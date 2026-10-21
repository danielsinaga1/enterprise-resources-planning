"""Layout-aware OCR pipeline for invoice images and multi-page PDFs."""
from __future__ import annotations

import math
import os
import re
import threading
from pathlib import Path
from difflib import SequenceMatcher
from typing import Any

import cv2  # type: ignore
import numpy as np  # type: ignore
from PIL import Image, ImageOps

from parser import (
    build_invoice_contract,
    normalize_numeric_field,
    parse_invoice,
    resolve_table_roles,
    semantic_role,
)

try:
    from paddle_engine import is_available as paddle_is_available
    from paddle_engine import pipeline_error as paddle_pipeline_error
    from paddle_engine import process_page as paddle_process_page
    from paddle_engine import recognize_cell_variants as paddle_recognize_cells
    from paddle_engine import recognize_page as paddle_recognize_page
    from paddle_engine import recognize_page_accurate as paddle_recognize_page_accurate
except ImportError:  # pragma: no cover
    paddle_is_available = lambda: False
    paddle_pipeline_error = lambda: "PaddleOCR is not installed"
    paddle_process_page = lambda *_args, **_kwargs: None
    paddle_recognize_cells = lambda *_args, **_kwargs: None
    paddle_recognize_page = lambda *_args, **_kwargs: []
    paddle_recognize_page_accurate = lambda *_args, **_kwargs: []

try:
    import easyocr  # type: ignore
except ImportError:  # pragma: no cover
    easyocr = None

try:
    from pdf2image import convert_from_path  # type: ignore
except ImportError:  # pragma: no cover
    convert_from_path = None

EASYOCR_READER = None
READER_LOCK = threading.Lock()
SUPPORTED_IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".tif", ".tiff"}


def get_ocr_reader():
    """Create one shared EasyOCR reader; model initialization is expensive."""
    global EASYOCR_READER
    if EASYOCR_READER is None:
        if easyocr is None:
            raise RuntimeError("EasyOCR is not installed")
        with READER_LOCK:
            if EASYOCR_READER is None:
                try:
                    EASYOCR_READER = easyocr.Reader(["id", "en"], gpu=False, verbose=False)
                except Exception:
                    EASYOCR_READER = easyocr.Reader(["en"], gpu=False, verbose=False)
    return EASYOCR_READER


def _order_points(points: np.ndarray) -> np.ndarray:
    rectangle = np.zeros((4, 2), dtype="float32")
    sums = points.sum(axis=1)
    differences = np.diff(points, axis=1).reshape(-1)
    rectangle[0] = points[np.argmin(sums)]
    rectangle[2] = points[np.argmax(sums)]
    rectangle[1] = points[np.argmin(differences)]
    rectangle[3] = points[np.argmax(differences)]
    return rectangle


def _perspective_correct(image: np.ndarray) -> np.ndarray:
    """Flatten a photographed page when a reliable four-corner contour exists."""
    height, width = image.shape[:2]
    scale = min(1.0, 1200.0 / max(height, width))
    small = cv2.resize(image, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
    gray = cv2.cvtColor(small, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(gray, 50, 150)
    edges = cv2.morphologyEx(edges, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8), iterations=2)
    contours, _ = cv2.findContours(edges, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    page_area = float(small.shape[0] * small.shape[1])
    for contour in sorted(contours, key=cv2.contourArea, reverse=True)[:8]:
        perimeter = cv2.arcLength(contour, True)
        polygon = cv2.approxPolyDP(contour, 0.02 * perimeter, True)
        if len(polygon) != 4 or cv2.contourArea(polygon) < page_area * 0.35:
            continue
        points = polygon.reshape(4, 2).astype("float32") / scale
        rect = _order_points(points)
        tl, tr, br, bl = rect
        target_width = int(max(np.linalg.norm(br - bl), np.linalg.norm(tr - tl)))
        target_height = int(max(np.linalg.norm(tr - br), np.linalg.norm(tl - bl)))
        if target_width < 400 or target_height < 400:
            break
        destination = np.array(
            [[0, 0], [target_width - 1, 0], [target_width - 1, target_height - 1], [0, target_height - 1]],
            dtype="float32",
        )
        matrix = cv2.getPerspectiveTransform(rect, destination)
        return cv2.warpPerspective(image, matrix, (target_width, target_height), borderValue=(255, 255, 255))
    return image


def _deskew(image: np.ndarray) -> tuple[np.ndarray, float]:
    """Correct small scanner/camera skew without rotating legitimate layouts."""
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    binary = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)[1]
    coordinates = np.column_stack(np.where(binary > 0))
    if coordinates.shape[0] < 100:
        return image, 0.0
    angle = cv2.minAreaRect(coordinates[:, ::-1].astype(np.float32))[-1]
    angle = -(90 + angle) if angle < -45 else -angle
    if abs(angle) < 0.15 or abs(angle) > 12:
        return image, 0.0
    height, width = image.shape[:2]
    matrix = cv2.getRotationMatrix2D((width / 2, height / 2), angle, 1.0)
    corrected = cv2.warpAffine(
        image, matrix, (width, height), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_CONSTANT,
        borderValue=(255, 255, 255),
    )
    return corrected, round(angle, 2)


def preprocess_image(image: Image.Image) -> tuple[np.ndarray, dict[str, Any]]:
    """Apply page correction, shadow cleaning, denoise, CLAHE, resize and sharpening."""
    image = ImageOps.exif_transpose(image).convert("RGB")
    bgr = cv2.cvtColor(np.asarray(image), cv2.COLOR_RGB2BGR)
    bgr = _perspective_correct(bgr)
    auto_rotation = 0
    # Aspect ratio alone cannot distinguish a legitimate landscape invoice
    # from a sideways phone photo. Orientation candidates are evaluated from
    # OCR quality later; keep the source geometry intact at this stage.
    bgr, skew_angle = _deskew(bgr)

    height, width = bgr.shape[:2]
    # PP-Structure performs its own resize. Keeping this geometry unchanged
    # prevents table-cell coordinates from drifting after aggressive upscaling.
    structure_image = bgr.copy()
    scale = min(2.75, max(1.0, 1650.0 / max(width, 1)))
    if scale > 1.02:
        bgr = cv2.resize(bgr, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)

    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    # Estimate illumination and divide it out: effective for folds and phone shadows.
    background = cv2.morphologyEx(gray, cv2.MORPH_CLOSE, np.ones((31, 31), np.uint8))
    clean = cv2.divide(gray, np.maximum(background, 1), scale=255)
    clean = cv2.fastNlMeansDenoising(clean, None, 7, 7, 21)
    clean = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(clean)
    sharpened = cv2.addWeighted(clean, 1.55, cv2.GaussianBlur(clean, (0, 0), 1.1), -0.55, 0)

    # Keep grayscale for OCR. Adaptive threshold is generated as a second candidate,
    # because hard thresholding can erase thin decimal separators.
    threshold = cv2.adaptiveThreshold(
        sharpened, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 35, 12
    )
    metrics = {
        "original_size": [width, height],
        "processed_size": [int(sharpened.shape[1]), int(sharpened.shape[0])],
        "deskew_angle": skew_angle,
        "auto_rotation": auto_rotation,
        "contrast": round(float(sharpened.std()), 2),
        "sharpness": round(float(cv2.Laplacian(sharpened, cv2.CV_64F).var()), 2),
        "operations": [
            "exif_auto_rotate", "perspective_correction", "auto_deskew", "shadow_removal",
            "noise_removal", "background_cleaning", "contrast_enhancement", "resize_resolution",
            "sharpen_image", "adaptive_threshold", "edge_detection",
        ],
    }
    return sharpened, {
        **metrics,
        "_threshold": threshold,
        "_structure_image": structure_image,
    }


def _tokens_from_result(result: list[Any], page: int, width: int, height: int) -> list[dict[str, Any]]:
    tokens: list[dict[str, Any]] = []
    for bbox, text, confidence in result:
        value = str(text).strip()
        if not value or float(confidence) < 0.08:
            continue
        xs = [float(point[0]) for point in bbox]
        ys = [float(point[1]) for point in bbox]
        tokens.append({
            "text": value,
            "confidence": round(float(confidence) * 100, 2),
            "x0": min(xs), "x1": max(xs), "y0": min(ys), "y1": max(ys),
            "cx": (min(xs) + max(xs)) / 2, "cy": (min(ys) + max(ys)) / 2,
            "page": page, "page_width": width, "page_height": height,
        })
    return tokens


def _ocr_candidate(
    reader: Any,
    image: np.ndarray,
    page: int,
    *,
    decoder: str = "greedy",
) -> list[dict[str, Any]]:
    height, width = image.shape[:2]
    with READER_LOCK:
        result = reader.readtext(
            image, detail=1, paragraph=False, decoder=decoder, beamWidth=5,
            batch_size=1, workers=0, rotation_info=None,
            text_threshold=0.62, low_text=0.32, link_threshold=0.35,
            canvas_size=2300, mag_ratio=1.0,
        )
    return _tokens_from_result(result, page, width, height)


def _detect_grid_tables(image: np.ndarray, tokens: list[dict[str, Any]], page: int) -> list[dict[str, Any]]:
    """Recover exact row/column geometry from visible invoice table borders."""
    height, width = image.shape[:2]
    binary = cv2.adaptiveThreshold(
        image, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY_INV, 31, 10
    )
    horizontal = cv2.morphologyEx(
        binary, cv2.MORPH_OPEN,
        cv2.getStructuringElement(cv2.MORPH_RECT, (max(20, width // 30), 1)),
    )
    vertical = cv2.morphologyEx(
        binary, cv2.MORPH_OPEN,
        cv2.getStructuringElement(cv2.MORPH_RECT, (1, max(20, height // 30))),
    )
    grid = cv2.add(horizontal, vertical)
    projected_groups: list[list[list[tuple[int, int, int, int]]]] = []
    external_contours, _ = cv2.findContours(
        grid, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
    )

    def grouped_positions(mask: np.ndarray) -> list[int]:
        indices = np.flatnonzero(mask)
        if not len(indices):
            return []
        groups = [[int(indices[0])]]
        for value in indices[1:]:
            value = int(value)
            if value - groups[-1][-1] <= 3:
                groups[-1].append(value)
            else:
                groups.append([value])
        return [int(round(sum(group) / len(group))) for group in groups]

    for contour in sorted(external_contours, key=cv2.contourArea, reverse=True)[:8]:
        table_x, table_y, table_width, table_height = cv2.boundingRect(contour)
        if (
            table_width < width * 0.25
            or table_height < height * 0.06
            or table_width * table_height < width * height * 0.025
        ):
            continue
        vertical_roi = vertical[
            table_y : table_y + table_height,
            table_x : table_x + table_width,
        ]
        horizontal_roi = horizontal[
            table_y : table_y + table_height,
            table_x : table_x + table_width,
        ]
        x_positions = grouped_positions(
            np.count_nonzero(vertical_roi, axis=0) >= table_height * 0.42
        )
        y_positions = grouped_positions(
            np.count_nonzero(horizontal_roi, axis=1) >= table_width * 0.42
        )
        if len(x_positions) < 3 or len(y_positions) < 3:
            continue
        rows = []
        for row_index in range(len(y_positions) - 1):
            cells = []
            for column_index in range(len(x_positions) - 1):
                x1 = table_x + x_positions[column_index]
                y1 = table_y + y_positions[row_index]
                x2 = table_x + x_positions[column_index + 1]
                y2 = table_y + y_positions[row_index + 1]
                if x2 - x1 > 20 and y2 - y1 > 12:
                    cells.append((x1, y1, x2 - x1, y2 - y1))
            if len(cells) >= 2:
                rows.append(cells)
        if len(rows) >= 2:
            projected_groups.append(rows)
            break

    contours, _ = cv2.findContours(grid, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    rectangles: list[tuple[int, int, int, int]] = []
    for contour in contours:
        x, y, cell_width, cell_height = cv2.boundingRect(contour)
        if not (35 < cell_width < width * 0.82 and 14 < cell_height < height * 0.3):
            continue
        if cell_width * cell_height < 900:
            continue
        candidate = (x, y, cell_width, cell_height)
        if any(
            abs(x - other[0]) < 4 and abs(y - other[1]) < 4
            and abs(cell_width - other[2]) < 6 and abs(cell_height - other[3]) < 6
            for other in rectangles
        ):
            continue
        rectangles.append(candidate)

    row_groups: list[list[tuple[int, int, int, int]]] = []
    for rectangle in sorted(rectangles, key=lambda value: (value[1], value[0])):
        match = next(
            (
                row for row in reversed(row_groups[-8:])
                if abs(rectangle[1] - sum(cell[1] for cell in row) / len(row))
                <= max(5, rectangle[3] * 0.22)
            ),
            None,
        )
        if match is None:
            row_groups.append([rectangle])
        else:
            match.append(rectangle)
    row_groups = [sorted(row, key=lambda value: value[0]) for row in row_groups if len(row) >= 2]

    table_groups: list[list[list[tuple[int, int, int, int]]]] = projected_groups
    if not table_groups:
        for row in row_groups:
            if not table_groups:
                table_groups.append([row])
                continue
            previous = table_groups[-1][-1]
            same_columns = len(row) == len(previous) and all(
                abs(current[0] - old[0]) <= max(8, old[2] * 0.12)
                for current, old in zip(row, previous)
            )
            previous_bottom = max(cell[1] + cell[3] for cell in previous)
            close_vertically = min(cell[1] for cell in row) - previous_bottom <= 14
            if same_columns and close_vertically:
                table_groups[-1].append(row)
            else:
                table_groups.append([row])

    tables: list[dict[str, Any]] = []
    for group in table_groups:
        if len(group) < 2 or len(group[0]) < 2:
            continue
        rendered_rows: list[list[dict[str, Any]]] = []
        for row in group:
            cells = []
            for column_index, (x, y, cell_width, cell_height) in enumerate(row):
                cell_tokens = [
                    token for token in tokens
                    if x - 3 <= token["cx"] <= x + cell_width + 3
                    and y - 3 <= token["cy"] <= y + cell_height + 3
                ]
                cell_tokens.sort(key=lambda token: (token["cy"], token["x0"]))
                unique_tokens: list[dict[str, Any]] = []
                for token in cell_tokens:
                    token_key = re.sub(
                        r"\s+", "", str(token.get("text") or "").lower()
                    )
                    duplicate_index = next(
                        (
                            index
                            for index, existing in enumerate(unique_tokens)
                            if token_key
                            == re.sub(
                                r"\s+",
                                "",
                                str(existing.get("text") or "").lower(),
                            )
                            and abs(token["cx"] - existing["cx"])
                            <= max(8, (token["x1"] - token["x0"]) * 0.35)
                            and abs(token["cy"] - existing["cy"])
                            <= max(6, (token["y1"] - token["y0"]) * 0.45)
                        ),
                        None,
                    )
                    if duplicate_index is None:
                        unique_tokens.append(token)
                    elif token["confidence"] > unique_tokens[duplicate_index]["confidence"]:
                        unique_tokens[duplicate_index] = token
                cell_tokens = sorted(
                    unique_tokens, key=lambda token: (token["cy"], token["x0"])
                )
                value = " ".join(token["text"] for token in cell_tokens).strip()
                confidence = (
                    round(sum(token["confidence"] for token in cell_tokens) / len(cell_tokens), 2)
                    if cell_tokens else 0.0
                )
                engines = [str(token.get("engine") or "") for token in cell_tokens]
                cells.append({
                    "text": value,
                    "confidence": confidence,
                    "engine": max(set(engines), key=engines.count) if engines else "",
                    "column_index": column_index,
                    "colspan": 1,
                    "rowspan": 1,
                    "bbox": [x, y, x + cell_width, y + cell_height],
                })
            rendered_rows.append(cells)
        is_projected_grid = any(group is projected for projected in projected_groups)
        header_position = 0 if is_projected_grid else next(
            (index for index, row in enumerate(rendered_rows) if sum(bool(cell["text"]) for cell in row) >= 2),
            None,
        )
        if header_position is None:
            # Geometry is still reliable. Empty header/data cells will be OCRed
            # regionally after this structure has been returned.
            header_position = 0
        header_end = header_position
        for candidate_index in range(header_position + 1, len(rendered_rows)):
            candidate = rendered_rows[candidate_index]
            non_empty = [cell for cell in candidate if cell["text"]]
            numeric_cells = [cell for cell in non_empty if any(char.isdigit() for char in cell["text"])]
            if numeric_cells or len(non_empty) < 2:
                break
            header_end = candidate_index
        header_rows = rendered_rows[header_position:header_end + 1]
        header_cells = []
        for column_index in range(len(header_rows[0])):
            parts = [
                row[column_index]["text"] for row in header_rows
                if column_index < len(row) and row[column_index]["text"]
            ]
            confidences = [
                row[column_index]["confidence"] for row in header_rows
                if column_index < len(row) and row[column_index]["text"]
            ]
            header_cells.append({
                "text": "\n".join(dict.fromkeys(parts)),
                "confidence": round(sum(confidences) / len(confidences), 2) if confidences else 0.0,
                "column_index": column_index,
                "colspan": 1,
                "rowspan": 1,
                "bbox": header_rows[-1][column_index].get("bbox")
                if column_index < len(header_rows[-1])
                else None,
            })
        columns = [{
            "id": f"column_{index + 1}",
            "name": cell["text"],
            "index": index,
            "semantic_role": semantic_role(cell["text"]),
            "confidence": cell["confidence"],
        } for index, cell in enumerate(header_cells)]
        data_rows = []
        for cells in rendered_rows[header_end + 1:]:
            data_rows.append({"index": len(data_rows), "cells": cells})
        if not data_rows:
            continue
        all_cells = [cell for row in group for cell in row]
        tables.append({
            "page": page,
            "detection_method": "grid",
            "columns": columns,
            "header_rows": [header_cells],
            "rows": data_rows,
            "column_count": len(columns),
            "row_count": len(data_rows),
            "bbox": {
                "x0": min(cell[0] for cell in all_cells),
                "y0": min(cell[1] for cell in all_cells),
                "x1": max(cell[0] + cell[2] for cell in all_cells),
                "y1": max(cell[1] + cell[3] for cell in all_cells),
            },
        })
    return tables


def _refresh_grid_columns(table: dict[str, Any]) -> None:
    header_rows = table.get("header_rows") or []
    for column_index, column in enumerate(table.get("columns") or []):
        cells = [
            row[column_index]
            for row in header_rows
            if column_index < len(row) and str(row[column_index].get("text") or "").strip()
        ]
        if not cells:
            continue
        names = [str(cell["text"]).strip() for cell in cells]
        column["name"] = "\n".join(dict.fromkeys(names))
        column["confidence"] = round(
            sum(float(cell.get("confidence") or 0) for cell in cells) / len(cells),
            2,
        )
        column["semantic_role"] = semantic_role(column["name"])


def _candidate_score(tokens: list[dict[str, Any]]) -> float:
    if not tokens:
        return 0.0
    useful = [token for token in tokens if any(char.isalnum() for char in token["text"])]
    return sum(math.sqrt(max(len(token["text"]), 1)) * token["confidence"] for token in useful)


def _invoice_candidate_quality(tokens: list[dict[str, Any]]) -> tuple[float, dict[str, Any]]:
    """Score OCR output by invoice semantics, not token confidence alone."""
    if not tokens:
        return 0.0, {}
    raw_text = _tokens_to_text(tokens)
    try:
        parsed = parse_invoice(tokens, raw_text, "quality-check")
    except Exception:
        return _candidate_score(tokens) * 0.01, {}
    validation = parsed.get("validation") or {}
    items = list(parsed.get("items") or [])
    valid_rows = len(items) - len(validation.get("invalid_rows") or [])
    score = (
        (1200.0 if validation.get("valid") else 0.0)
        + valid_rows * 180.0
        + len(items) * 45.0
        + (180.0 if float(parsed.get("grand_total") or 0) > 0 else 0.0)
        + (80.0 if str(parsed.get("vendor") or "").strip(" -") else 0.0)
        + (70.0 if str(parsed.get("invoice_number") or "").strip(" -") else 0.0)
        + (50.0 if str(parsed.get("invoice_date") or "").strip(" -") else 0.0)
        + min(160.0, _candidate_score(tokens) * 0.015)
    )
    return score, parsed


def _structure_result_score(result: dict[str, Any] | None) -> float:
    if not result:
        return 0.0
    tokens = result.get("tokens") or []
    confidence = (
        sum(float(token.get("confidence") or 0) for token in tokens) / len(tokens)
        if tokens
        else 0.0
    )
    table_bonus = 25.0 if result.get("tables") else 0.0
    return confidence + min(len(tokens), 100) * 0.08 + table_bonus


def _merge_candidates(primary: list[dict[str, Any]], threshold: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Fuse OCR passes: grayscale preserves names, threshold often improves digits."""
    if not primary:
        return threshold
    if not threshold:
        return primary
    merged: list[dict[str, Any]] = []
    used_primary: set[int] = set()
    for hard_token in threshold:
        best_index = None
        best_distance = float("inf")
        for index, soft_token in enumerate(primary):
            if index in used_primary:
                continue
            avg_width = max(hard_token["x1"] - hard_token["x0"], soft_token["x1"] - soft_token["x0"], 12)
            avg_height = max(hard_token["y1"] - hard_token["y0"], soft_token["y1"] - soft_token["y0"], 8)
            dx = abs(hard_token["cx"] - soft_token["cx"]) / avg_width
            dy = abs(hard_token["cy"] - soft_token["cy"]) / avg_height
            distance = dx + dy
            if dx < 0.55 and dy < 0.65 and distance < best_distance:
                best_index, best_distance = index, distance
        if best_index is None:
            merged.append(hard_token)
            continue
        used_primary.add(best_index)
        soft_token = primary[best_index]
        hard_is_numeric = bool(any(char.isdigit() for char in hard_token["text"]))
        if hard_is_numeric:
            selected = hard_token if hard_token["confidence"] >= soft_token["confidence"] - 2 else soft_token
        else:
            selected = soft_token if soft_token["confidence"] >= hard_token["confidence"] - 8 else hard_token
        merged.append(selected.copy())
    for index, token in enumerate(primary):
        if index not in used_primary and token["confidence"] >= 45:
            merged.append(token)
    return merged


def _tokens_to_text(tokens: list[dict[str, Any]]) -> str:
    lines: list[list[dict[str, Any]]] = []
    for token in sorted(tokens, key=lambda item: (item["page"], item["cy"], item["x0"])):
        if not lines or token["page"] != lines[-1][0]["page"]:
            lines.append([token])
            continue
        line = lines[-1]
        avg_height = sum(item["y1"] - item["y0"] for item in line) / len(line)
        avg_y = sum(item["cy"] for item in line) / len(line)
        if abs(token["cy"] - avg_y) <= max(8.0, avg_height * 0.55):
            line.append(token)
        else:
            lines.append([token])
    return "\n".join("  ".join(item["text"] for item in sorted(line, key=lambda value: value["x0"])) for line in lines)


def _classify_document_regions(
    regions: list[dict[str, Any]], tables: list[dict[str, Any]]
) -> list[dict[str, Any]]:
    classified: list[dict[str, Any]] = []
    summary_terms = ("subtotal", "sub total", "diskon", "discount", "pajak", "ppn", "grand total", "total keseluruhan")
    customer_terms = ("customer", "pelanggan", "bill to", "ship to", "kepada")
    for region in regions:
        label = str(region.get("label") or "").lower()
        content = str(region.get("content") or "")
        content_key = content.lower()
        if "table" in label:
            region_type = "item_table"
        elif "footer" in label or "seal" in label or "signature" in label:
            region_type = "footer"
        elif any(term in content_key for term in summary_terms):
            region_type = "payment_summary"
        elif any(term in content_key for term in customer_terms):
            region_type = "vendor_customer"
        elif label in {"title", "header", "doc_title"}:
            region_type = "header"
        else:
            region_type = "body"
        classified.append({**region, "region_type": region_type})
    for table in tables:
        if not any(
            region.get("region_type") == "item_table"
            and int(region.get("page", 1)) == int(table.get("page", 1))
            for region in classified
        ):
            classified.append(
                {
                    "region_type": "item_table",
                    "label": "table",
                    "content": "",
                    "bbox": table.get("bbox"),
                    "page": table.get("page", 1),
                }
            )
    return classified


def _vendor_from_layout(regions: list[dict[str, Any]]) -> tuple[str, float]:
    candidates: list[str] = []
    for region in regions:
        if str(region.get("label") or "").lower() in {"footer", "table"}:
            continue
        for line in str(region.get("content") or "").splitlines():
            value = re.sub(r"\s+", " ", line).strip(" :-")
            if re.search(
                r"\b(?:PT|CV|UD|Toko|Store|Ltd|LLC|Inc)\b",
                value,
                re.I,
            ) and not re.search(r"\b(?:customer|pelanggan|bill to)\b", value, re.I):
                candidates.append(value)
    if not candidates:
        return "", 0.0
    selected = min(candidates, key=len)
    return selected[:150], 92.0


def _select_cell_ocr_candidate(
    candidates: list[dict[str, Any]], role: str
) -> dict[str, Any] | None:
    """Choose a stable cell reading across preprocessing variants.

    Confidence alone is unreliable on tiny scans. For descriptions, prefer
    the candidate most similar to the rest of the ensemble and penalize OCR
    noise such as digits embedded inside words. Numeric alternatives remain
    available to the arithmetic reconciler through ``ocr_candidates``.
    """
    usable = [candidate for candidate in candidates if str(candidate.get("text") or "").strip()]
    if not usable:
        return None
    if role != "description" or len(usable) == 1:
        return max(
            usable,
            key=lambda candidate: (
                float(candidate.get("confidence") or 0),
                len(str(candidate.get("text") or "")),
            ),
        )

    keys = [
        re.sub(r"[^a-z0-9]+", "", str(candidate.get("text") or "").lower())
        for candidate in usable
    ]
    scored = []
    for index, candidate in enumerate(usable):
        text = str(candidate.get("text") or "").strip()
        support = sum(
            SequenceMatcher(None, keys[index], other_key).ratio()
            for other_key in keys
            if other_key
        )
        embedded_noise = len(
            re.findall(r"[A-Za-z]+\d+[A-Za-z]*|\d+[A-Za-z]{2,}", text)
        )
        symbol_noise = len(re.findall(r"[^\w\s./&()\-]", text))
        score = (
            support * 18.0
            + float(candidate.get("confidence") or 0) * 0.05
            - embedded_noise * 9.0
            - symbol_noise * 3.0
        )
        scored.append((score, candidate))
    return max(scored, key=lambda item: item[0])[1]


def _retry_low_confidence_cells(
    image: np.ndarray,
    threshold: np.ndarray,
    tables: list[dict[str, Any]],
    page: int,
) -> int:
    """Retry weak or arithmetically inconsistent cells without rerunning the page."""
    retry_cells: list[dict[str, Any]] = []
    for table in tables:
        if table.get("detection_method") == "grid":
            for header_row in table.get("header_rows") or []:
                for cell in header_row:
                    if (
                        cell.get("bbox")
                        and float(cell.get("confidence") or 0) < 88
                        and cell not in retry_cells
                    ):
                        cell["_retry_reason"] = "grid_header_low_confidence"
                        retry_cells.append(cell)
        role_columns = resolve_table_roles(table)
        for row in table.get("rows", []):
            cells = row.get("cells") or []
            row_text = " ".join(str(cell.get("text") or "") for cell in cells).strip()
            row_key = re.sub(r"[^a-z0-9]+", " ", row_text.lower()).strip()
            row_is_summary_or_footer = bool(
                re.match(
                    r"^(?:sub\s*total|subtotal|diskon|discount|pajak|ppn|vat|"
                    r"grand\s*total|total\s+(?:pembayaran|keseluruhan)|"
                    r"pembayaran|payment|pelunasan|transfer|rekening|account|"
                    r"metode\s+pembayaran|konfirmasi\s+pembayaran|sisa\s+tagihan|"
                    r"balance\s+due|catatan|notes|pesan|hormat\s+kami|signature|"
                    r"manager|finance)\b",
                    row_key,
                )
            )

            def at(role: str) -> dict[str, Any] | None:
                target = role_columns.get(role)
                if target is None:
                    return None
                for fallback_index, cell in enumerate(cells):
                    start = int(cell.get("column_index", fallback_index))
                    if start <= target < start + max(1, int(cell.get("colspan", 1))):
                        return cell
                return None

            important = [
                (role, at(role))
                for role in ("description", "unit", "qty", "price", "subtotal")
            ]
            if row_is_summary_or_footer:
                important = []
            elif table.get("detection_method") == "grid":
                important = []
                for index, cell in enumerate(cells):
                    role = next(
                        (
                            candidate_role
                            for candidate_role, column_index in role_columns.items()
                            if column_index
                            == int(cell.get("column_index", index))
                        ),
                        "",
                    )
                    if row_text and role:
                        important.append((role, cell))
            for role, cell in important:
                text = str((cell or {}).get("text") or "")
                if role == "unit" and not text.strip():
                    continue
                is_paddle_page_cell = str(
                    (cell or {}).get("engine") or ""
                ).startswith("paddleocr_v5_")
                suspicious_description = (
                    role == "description"
                    and bool(
                        re.search(r"[A-Za-z]{3,}\d{2,}\b|[A-Za-z]\d[A-Za-z0-9]\b", text)
                    )
                )
                if (
                    cell
                    and cell.get("bbox")
                    and (
                        float(cell.get("confidence") or 0)
                        < (
                            80
                            if is_paddle_page_cell
                            and role in {"qty", "price", "subtotal"}
                            else 92
                            if is_paddle_page_cell and role == "description"
                            else 78
                            if is_paddle_page_cell
                            else 94
                            if role in {"qty", "price", "subtotal"}
                            else 86
                        )
                        or suspicious_description
                    )
                    and cell not in retry_cells
                ):
                    cell["_retry_role"] = role
                    cell["_retry_reason"] = (
                        "ambiguous_alphanumeric_description"
                        if suspicious_description
                        else "low_confidence"
                    )
                    retry_cells.append(cell)

            qty_cell, price_cell, total_cell = at("qty"), at("price"), at("subtotal")
            if (
                not row_is_summary_or_footer
                and qty_cell
                and price_cell
                and total_cell
            ):
                qty = normalize_numeric_field(qty_cell.get("text"), monetary=False)["numeric_value"]
                price = normalize_numeric_field(price_cell.get("text"), monetary=True)["numeric_value"]
                total = normalize_numeric_field(total_cell.get("text"), monetary=True)["numeric_value"]
                current_invalid = (
                    qty
                    and price
                    and total
                    and abs(qty * price - total) > max(1, total * 0.005)
                )
                swapped_is_valid = (
                    qty
                    and price
                    and total
                    and abs(qty * total - price) <= max(1, price * 0.005)
                )
                if current_invalid and not swapped_is_valid:
                    for cell in (qty_cell, price_cell, total_cell):
                        if cell.get("bbox") and cell not in retry_cells:
                            cell["_retry_reason"] = "arithmetic_mismatch"
                            retry_cells.append(cell)

    if not retry_cells:
        return 0

    retried = 0
    height, width = image.shape[:2]
    # One bad page must not monopolize a batch.  The page-level OCR and parser
    # still retain every cell; server-model retries are capped to the most
    # important low-confidence regions.
    retry_cells.sort(
        key=lambda cell: (
            0 if not str(cell.get("text") or "").strip() else 1,
            float(cell.get("confidence") or 0),
        )
    )
    for cell in retry_cells[:24]:
        x1, y1, x2, y2 = [int(value) for value in cell["bbox"]]
        pad_x = max(3, int((x2 - x1) * 0.04))
        pad_y = max(3, int((y2 - y1) * 0.12))
        x1, x2 = max(0, x1 - pad_x), min(width, x2 + pad_x)
        y1, y2 = max(0, y1 - pad_y), min(height, y2 + pad_y)
        if x2 - x1 < 8 or y2 - y1 < 8:
            continue

        crop = image[y1:y2, x1:x2]
        hard_crop = threshold[y1:y2, x1:x2]
        if not crop.size:
            continue
        denoised = cv2.fastNlMeansDenoising(crop, None, 6, 7, 21)
        contrast = cv2.createCLAHE(clipLimit=2.2, tileGridSize=(4, 4)).apply(denoised)
        sharpened = cv2.addWeighted(
            contrast, 1.7, cv2.GaussianBlur(contrast, (0, 0), 1.0), -0.7, 0
        )
        variants = []
        for source in (crop, sharpened):
            resized = cv2.resize(
                source, None, fx=2.0, fy=2.0, interpolation=cv2.INTER_CUBIC
            )
            variants.append(
                cv2.cvtColor(resized, cv2.COLOR_GRAY2BGR)
                if resized.ndim == 2
                else resized
            )
        # A local Otsu pass preserves thin characters better than a threshold
        # calculated for the entire page, especially on low-resolution scans.
        local_base = cv2.resize(
            crop, None, fx=2.0, fy=2.0, interpolation=cv2.INTER_CUBIC
        )
        local_otsu = cv2.threshold(
            local_base, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU
        )[1]
        variants.append(
            cv2.cvtColor(local_otsu, cv2.COLOR_GRAY2BGR)
            if local_otsu.ndim == 2
            else local_otsu
        )
        paddle_result = paddle_recognize_cells(variants)
        text = str((paddle_result or {}).get("text") or "").strip()
        confidence = float((paddle_result or {}).get("confidence") or 0)
        candidate_audit = list((paddle_result or {}).get("candidates") or [])

        if not text:
            try:
                reader = get_ocr_reader()
                easy_outputs = []
                for variant_index, variant in enumerate(variants):
                    tokens = _ocr_candidate(reader, variant, page)
                    if not tokens:
                        continue
                    rendered = " ".join(
                        token["text"]
                        for token in sorted(
                            tokens, key=lambda item: (item["cy"], item["x0"])
                        )
                    ).strip()
                    easy_outputs.append(
                        {
                            "text": rendered,
                            "confidence": round(
                                sum(token["confidence"] for token in tokens)
                                / len(tokens),
                                2,
                            ),
                            "engine": "easyocr_cell_ensemble",
                            "variant_index": variant_index,
                        }
                    )
            except Exception:
                easy_outputs = []
            if easy_outputs:
                candidate_audit.extend(easy_outputs)
                selected_easy = _select_cell_ocr_candidate(
                    easy_outputs, str(cell.get("_retry_role") or "")
                )
                if selected_easy:
                    text = str(selected_easy["text"])
                    confidence = float(selected_easy["confidence"])

        original_text = str(cell.get("text") or "")
        original_confidence = float(cell.get("confidence") or 0)
        cell["ocr_candidates"] = [
            {
                "text": original_text,
                "confidence": original_confidence,
                "engine": "pp_structure_v3",
            },
            *candidate_audit,
        ]
        safe_to_replace = confidence >= max(80.0, original_confidence + 5.0)
        safe_blank_recovery = not original_text and confidence >= 65.0
        ambiguity_recovery = False
        if cell.get("_retry_reason") == "ambiguous_alphanumeric_description":
            original_key = re.sub(r"\s+", "", original_text)
            candidate_key = re.sub(r"\s+", "", text)
            ambiguity_recovery = (
                confidence >= max(80.0, original_confidence - 3.0)
                and candidate_key != original_key
                and not re.search(r"[A-Za-z]{3,}\d{2,}\b", text)
            )
        low_resolution_description_recovery = False
        if cell.get("_retry_role") == "description" and original_confidence < 70:
            original_noise = len(
                re.findall(
                    r"[A-Za-z]+\d+[A-Za-z]*|\d+[A-Za-z]{2,}|[^\w\s./&()\-]",
                    original_text,
                )
            )
            candidate_noise = len(
                re.findall(
                    r"[A-Za-z]+\d+[A-Za-z]*|\d+[A-Za-z]{2,}|[^\w\s./&()\-]",
                    text,
                )
            )
            low_resolution_description_recovery = (
                confidence >= 28
                and len(re.findall(r"[A-Za-z]", text)) >= 3
                and candidate_noise <= original_noise
            )
        if text and (
            safe_to_replace
            or safe_blank_recovery
            or ambiguity_recovery
            or low_resolution_description_recovery
        ):
            cell["original_text"] = original_text
            cell["text"] = text
            cell["value"] = text
            cell["confidence"] = confidence
            cell["retried"] = True
            cell["retry_reason"] = cell.pop("_retry_reason", "low_confidence")
            retried += 1
        else:
            cell.pop("_retry_reason", None)
        cell.pop("_retry_role", None)
    return retried


def _load_pages(path: Path) -> list[Image.Image]:
    if path.suffix.lower() == ".pdf":
        if convert_from_path is None:
            raise RuntimeError("pdf2image is required for PDF OCR")
        try:
            return [page.convert("RGB") for page in convert_from_path(path, dpi=300, fmt="png", thread_count=1)]
        except Exception as exc:
            raise RuntimeError(
                "PDF tidak dapat dirender. Pastikan Poppler tersedia pada server."
            ) from exc
    if path.suffix.lower() not in SUPPORTED_IMAGE_SUFFIXES:
        raise ValueError("Format tidak didukung. Gunakan JPG, JPEG, PNG, WEBP, TIFF, atau PDF.")
    with Image.open(path) as source:
        return [ImageOps.exif_transpose(source).convert("RGB")]


def process_document(file_path: Path | str, filename: str) -> dict[str, Any]:
    """OCR every page and return structured, confidence-scored, validated invoice data."""
    path = Path(file_path)
    pages = _load_pages(path)
    all_tokens: list[dict[str, Any]] = []
    page_metrics: list[dict[str, Any]] = []
    detected_tables: list[dict[str, Any]] = []
    layout_regions: list[dict[str, Any]] = []
    engines_used: set[str] = set()
    retry_assets: dict[int, tuple[np.ndarray, np.ndarray]] = {}

    for page_number, page in enumerate(pages, start=1):
        processed, metrics = preprocess_image(page)
        threshold = metrics.pop("_threshold")
        structure_input = metrics.pop("_structure_image")
        page_retry_gray = processed
        page_retry_threshold = threshold
        # PaddleOCR performs its own resize/normalization.  Feeding it the
        # heavily enlarged EasyOCR preprocessing result can merge adjacent
        # rows on small invoices, so preserve the EXIF-corrected source here.
        base_structure_input = cv2.cvtColor(
            np.asarray(page), cv2.COLOR_RGB2BGR
        )
        structure_result = None
        grid_candidates: list[dict[str, Any]] = []
        grid_retried = False
        recovered_tokens: list[dict[str, Any]] = []
        use_full_structure = os.environ.get(
            "SMARTDOCS_ENABLE_PP_STRUCTURE", "false"
        ).strip().lower() in {"1", "true", "yes", "on"}
        if paddle_is_available() and use_full_structure:
            structure_result = paddle_process_page(structure_input, page_number)
            original_structure_result = structure_result
            structure_tokens = (structure_result or {}).get("tokens") or []
            mean_confidence = (
                sum(token["confidence"] for token in structure_tokens)
                / len(structure_tokens)
                if structure_tokens
                else 0
            )
            if mean_confidence < 82 or not (structure_result or {}).get("tables"):
                structure_gray_candidate = cv2.cvtColor(
                    structure_input, cv2.COLOR_BGR2GRAY
                )
                structure_contrast = cv2.createCLAHE(
                    clipLimit=2.0, tileGridSize=(8, 8)
                ).apply(structure_gray_candidate)
                enhanced_structure = cv2.cvtColor(
                    structure_contrast, cv2.COLOR_GRAY2BGR
                )
                enhanced_result = paddle_process_page(
                    enhanced_structure, page_number
                )
                if _structure_result_score(enhanced_result) > _structure_result_score(
                    structure_result
                ):
                    structure_result = enhanced_result
                    structure_result["tokens"] = _merge_candidates(
                        (original_structure_result or {}).get("tokens") or [],
                        (enhanced_result or {}).get("tokens") or [],
                    )
                    original_regions = (
                        (original_structure_result or {}).get("layout_regions") or []
                    )
                    enhanced_regions = (
                        (enhanced_result or {}).get("layout_regions") or []
                    )
                    structure_result["layout_regions"] = [
                        *original_regions,
                        *[
                            region
                            for region in enhanced_regions
                            if region not in original_regions
                        ],
                    ]
                    structure_input = enhanced_structure
                    metrics["structure_variant"] = "contrast_enhanced"
                else:
                    metrics["structure_variant"] = "geometry_corrected_original"
            else:
                metrics["structure_variant"] = "geometry_corrected_original"

        # The mobile PP-OCRv5 reader is the default page engine.  It preserves
        # token geometry for both ruled and borderless invoices, while avoiding
        # the cost of a complete PP-Structure pass on every file.  Deployments
        # that need the heavier document-layout pipeline can opt in through the
        # environment variable above.
        if paddle_is_available() and not structure_result:
            source_height, source_width = base_structure_input.shape[:2]
            scarce_character_detail = (
                source_width * source_height < 900_000
                or min(source_width, source_height) < 650
            )
            page_tokens = (
                paddle_recognize_page_accurate(
                    base_structure_input, page_number
                )
                if scarce_character_detail
                else paddle_recognize_page(base_structure_input, page_number)
            )
            if page_tokens:
                page_model = (
                    "server" if scarce_character_detail else "mobile"
                )
                structure_result = {
                    "tokens": page_tokens,
                    "tables": [],
                    "layout_regions": [],
                    "engine": f"PaddleOCR PP-OCRv5 {page_model}",
                }
                metrics["structure_variant"] = f"pp_ocr_v5_{page_model}"

        selected = (structure_result or {}).get("tokens") or []
        page_tables = (structure_result or {}).get("tables") or []
        if selected:
            selected_engine = str(
                (structure_result or {}).get("engine")
                or "PaddleOCR PP-StructureV3"
            )
            engines_used.add(selected_engine)
            metrics["selected_variant"] = (
                "pp_ocr_v5_page"
                if selected_engine.startswith("PaddleOCR PP-OCRv5")
                else "pp_structure_v3_layout"
            )
            layout_regions.extend((structure_result or {}).get("layout_regions") or [])
            if selected_engine == "PaddleOCR PP-StructureV3":
                recovered_tokens = paddle_recognize_page(
                    base_structure_input, page_number
                )
                if recovered_tokens:
                    selected = _merge_candidates(selected, recovered_tokens)
                    metrics["page_ocr_geometry_recovered"] = True
            structure_gray = cv2.cvtColor(structure_input, cv2.COLOR_BGR2GRAY)
            structure_threshold = cv2.adaptiveThreshold(
                structure_gray,
                255,
                cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                cv2.THRESH_BINARY,
                35,
                12,
            )
            page_retry_gray = structure_gray
            page_retry_threshold = structure_threshold
            grid_candidates = _detect_grid_tables(
                cv2.cvtColor(base_structure_input, cv2.COLOR_BGR2GRAY),
                recovered_tokens or selected,
                page_number,
            )
            metrics["grid_candidates"] = len(grid_candidates)
            retry_tables = grid_candidates or page_tables
            retries = _retry_low_confidence_cells(
                structure_gray, structure_threshold, retry_tables, page_number
            )
            metrics["low_confidence_regions_retried"] = retries
            if grid_candidates:
                grid_retried = True
                for grid_table in grid_candidates:
                    _refresh_grid_columns(grid_table)
        else:
            reader = get_ocr_reader()
            primary_tokens = _ocr_candidate(reader, processed, page_number)
            selected = primary_tokens
            primary_quality, primary_parsed = _invoice_candidate_quality(
                primary_tokens
            )
            primary_validation = primary_parsed.get("validation") or {}
            needs_ensemble = (
                metrics["contrast"] < 48
                or _candidate_score(primary_tokens) < 1200
                or not primary_validation.get("valid")
            )
            if needs_ensemble:
                threshold_tokens = _ocr_candidate(reader, threshold, page_number)
                merged_tokens = _merge_candidates(
                    primary_tokens, threshold_tokens
                )
                merged_quality, _ = _invoice_candidate_quality(merged_tokens)
                if merged_quality >= primary_quality:
                    selected = merged_tokens
                    primary_quality = merged_quality
                metrics["selected_variant"] = "grayscale_threshold_ensemble"
            else:
                metrics["selected_variant"] = "enhanced_grayscale"

            # Only very weak pages pay the cost of orientation alternatives.
            # A legitimate landscape invoice remains untouched when its OCR
            # semantics are stronger than either rotated candidate.
            if primary_quality < 420:
                orientation_candidates = []
                for angle, rotation_code in (
                    (90, cv2.ROTATE_90_CLOCKWISE),
                    (270, cv2.ROTATE_90_COUNTERCLOCKWISE),
                ):
                    rotated = cv2.rotate(processed, rotation_code)
                    rotated_tokens = _ocr_candidate(
                        reader, rotated, page_number
                    )
                    quality, _ = _invoice_candidate_quality(rotated_tokens)
                    orientation_candidates.append(
                        (quality, angle, rotated, rotated_tokens)
                    )
                best_quality, angle, rotated, rotated_tokens = max(
                    orientation_candidates, key=lambda candidate: candidate[0]
                )
                if best_quality > primary_quality * 1.18:
                    processed = rotated
                    threshold = cv2.adaptiveThreshold(
                        processed,
                        255,
                        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                        cv2.THRESH_BINARY,
                        35,
                        12,
                    )
                    page_retry_gray = processed
                    page_retry_threshold = threshold
                    selected = rotated_tokens
                    metrics["auto_rotation"] = angle
                    metrics["processed_size"] = [
                        int(processed.shape[1]), int(processed.shape[0])
                    ]
                    metrics["selected_variant"] = (
                        "ocr_scored_orientation"
                    )
            engines_used.add("EasyOCR fallback")

        all_tokens.extend(selected)
        grid_image = (
            cv2.cvtColor(base_structure_input, cv2.COLOR_BGR2GRAY)
            if structure_result and selected
            else processed
        )
        if not grid_candidates:
            grid_candidates = _detect_grid_tables(
                grid_image, selected, page_number
            )
            metrics["grid_candidates"] = len(grid_candidates)
        if (
            grid_candidates
            and structure_result
            and selected
            and not grid_retried
        ):
            metrics["low_confidence_regions_retried"] = (
                int(metrics.get("low_confidence_regions_retried", 0))
                + _retry_low_confidence_cells(
                    structure_gray,
                    structure_threshold,
                    grid_candidates,
                    page_number,
                )
            )
            for grid_table in grid_candidates:
                _refresh_grid_columns(grid_table)
        detected_tables.extend(page_tables)
        detected_tables.extend(grid_candidates)
        page_metrics.append(metrics)
        retry_assets[page_number] = (
            page_retry_gray,
            page_retry_threshold,
        )

    raw_text = _tokens_to_text(all_tokens)
    parsed = parse_invoice(all_tokens, raw_text, filename)
    # parse_invoice also builds a position-based table when an invoice has no
    # visible grid or PP-Structure does not classify the region as a table.
    # Previously that fallback stayed inside parsed["tables"] and was never
    # passed to the canonical mapper, producing "0 baris" even though the OCR
    # text and column headers had been read successfully.
    structure_table_count = len(detected_tables)
    borderless_tables = list(parsed.get("tables") or [])
    for page_number, (retry_gray, retry_threshold) in retry_assets.items():
        page_borderless = [
            table
            for table in borderless_tables
            if int(table.get("page", 1)) == page_number
        ]
        if not page_borderless:
            continue
        if str(
            page_metrics[page_number - 1].get("structure_variant") or ""
        ).startswith("pp_ocr_v5_"):
            page_metrics[page_number - 1]["borderless_cells_retried"] = 0
            continue
        retried = _retry_low_confidence_cells(
            retry_gray,
            retry_threshold,
            page_borderless,
            page_number,
        )
        page_metrics[page_number - 1]["borderless_cells_retried"] = retried
    detected_tables.extend(borderless_tables)
    layout_vendor, layout_vendor_confidence = _vendor_from_layout(layout_regions)
    parsed_vendor = str(parsed.get("vendor") or "")
    if layout_vendor and (
        not parsed_vendor
        or re.search(
            r"\b(?:customer|pelanggan|bill to|subtotal|diskon|discount|total|bank|payment)\b",
            parsed_vendor,
            re.I,
        )
    ):
        parsed["vendor"] = layout_vendor
        parsed.setdefault("confidence", {})["vendor"] = layout_vendor_confidence
    parsed = build_invoice_contract(parsed, detected_tables, all_tokens, raw_text)
    document_regions = _classify_document_regions(layout_regions, detected_tables)
    parsed.update({
        "ocr_text": raw_text,
        "page_count": len(pages),
        "preprocessing": page_metrics,
        "source_filename": filename,
        "layout_regions": layout_regions,
        "document_regions": document_regions,
        "table_detection": {
            "structure_or_grid_tables": structure_table_count,
            "borderless_layout_tables": len(borderless_tables),
            "candidate_tables": len(detected_tables),
        },
        "ocr_engines": sorted(engines_used),
        "paddleocr_available": paddle_is_available(),
        "paddleocr_error": paddle_pipeline_error(),
    })
    return {"success": True, "data": parsed} 
