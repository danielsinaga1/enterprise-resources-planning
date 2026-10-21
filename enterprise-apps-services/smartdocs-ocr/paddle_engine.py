"""PaddleOCR PP-StructureV3 adapter for layout-aware invoice extraction.

The module is deliberately optional.  SmartDocs keeps its existing OCR path as
a fallback when PaddleOCR is unavailable or a model cannot be loaded.
"""

from __future__ import annotations

import logging
import os
import re
import sys
import threading
from pathlib import Path
from typing import Any

import numpy as np

from parser import semantic_role

MODEL_CACHE = Path(
    os.environ.get(
        "SMARTDOCS_MODEL_HOME",
        str(Path(__file__).resolve().parent / ".model-cache"),
    )
)
os.environ.setdefault("PADDLE_PDX_CACHE_HOME", str(MODEL_CACHE))
os.environ.setdefault("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True")
# PaddleX pulls in Hugging Face utilities which otherwise probe PyTorch during
# import.  On Windows that loads Torch and Paddle DLLs into the same process and
# can fail with WinError 127.  The SmartDocs pipeline is Paddle-only; EasyOCR
# remains an independent fallback in ocr.py.
os.environ.setdefault("USE_TORCH", "0")
VENDORED_DEPENDENCIES = Path(__file__).resolve().parent / ".vendor-paddle"
if VENDORED_DEPENDENCIES.exists():
    # Append (rather than prepend) so the application's headless cv2 remains
    # authoritative while package metadata for the locked contrib wheel is visible.
    sys.path.append(str(VENDORED_DEPENDENCIES))

try:
    from bs4 import BeautifulSoup
except ImportError:  # pragma: no cover - covered by the PaddleOCR extra
    BeautifulSoup = None

try:
    from paddleocr import PPStructureV3, PaddleOCR
except Exception:  # pragma: no cover - fallback also covers cache/import failures
    PPStructureV3 = None
    PaddleOCR = None


LOGGER = logging.getLogger(__name__)
_PIPELINE = None
_CELL_OCR = None
_PAGE_OCR = None
_CELL_OCR_ERROR: str | None = None
_PAGE_OCR_ERROR: str | None = None
_PIPELINE_ERROR: str | None = None
_PIPELINE_LOCK = threading.Lock()
_PREDICT_LOCK = threading.Lock()
_CELL_OCR_LOCK = threading.Lock()
_PAGE_OCR_LOCK = threading.Lock()


def is_available() -> bool:
    return PPStructureV3 is not None and PaddleOCR is not None and BeautifulSoup is not None


def pipeline_error() -> str | None:
    return _PIPELINE_ERROR


def _get_pipeline():
    global _PIPELINE, _PIPELINE_ERROR
    if _PIPELINE is not None:
        return _PIPELINE
    if not is_available():
        return None

    with _PIPELINE_LOCK:
        if _PIPELINE is not None:
            return _PIPELINE
        try:
            _PIPELINE = PPStructureV3(
                lang="en",
                device="cpu",
                use_doc_orientation_classify=True,
                use_doc_unwarping=True,
                use_textline_orientation=True,
                use_table_recognition=True,
                use_formula_recognition=False,
                use_chart_recognition=False,
                use_seal_recognition=False,
                use_region_detection=True,
                text_rec_score_thresh=0.30,
            )
            _PIPELINE_ERROR = None
        except Exception as exc:  # keep the application usable offline
            _PIPELINE_ERROR = str(exc)
            LOGGER.exception("PP-StructureV3 could not be initialized")
            return None
    return _PIPELINE


def _get_cell_ocr():
    global _CELL_OCR, _CELL_OCR_ERROR
    if _CELL_OCR is not None:
        return _CELL_OCR
    if _CELL_OCR_ERROR is not None:
        return None
    if PaddleOCR is None:
        return None
    with _CELL_OCR_LOCK:
        if _CELL_OCR is None:
            try:
                _CELL_OCR = PaddleOCR(
                    device="cpu",
                    text_detection_model_name="PP-OCRv5_server_det",
                    text_recognition_model_name="PP-OCRv5_server_rec",
                    textline_orientation_model_name="PP-LCNet_x1_0_textline_ori",
                    use_doc_orientation_classify=False,
                    use_doc_unwarping=False,
                    use_textline_orientation=True,
                    text_rec_score_thresh=0.20,
                )
                _CELL_OCR_ERROR = None
            except Exception as exc:
                _CELL_OCR_ERROR = str(exc)
                LOGGER.exception("PaddleOCR cell reader could not be initialized")
                return None
    return _CELL_OCR


def _get_page_ocr():
    """Fast full-page OCR; the larger server model is reserved for weak cells."""
    global _PAGE_OCR, _PAGE_OCR_ERROR
    if _PAGE_OCR is not None:
        return _PAGE_OCR
    if _PAGE_OCR_ERROR is not None or PaddleOCR is None:
        return None
    with _PAGE_OCR_LOCK:
        if _PAGE_OCR is None:
            try:
                _PAGE_OCR = PaddleOCR(
                    device="cpu",
                    text_detection_model_name="PP-OCRv5_mobile_det",
                    text_recognition_model_name="PP-OCRv5_mobile_rec",
                    textline_orientation_model_name="PP-LCNet_x1_0_textline_ori",
                    use_doc_orientation_classify=False,
                    use_doc_unwarping=False,
                    use_textline_orientation=True,
                    text_rec_score_thresh=0.20,
                )
                _PAGE_OCR_ERROR = None
            except Exception as exc:
                _PAGE_OCR_ERROR = str(exc)
                LOGGER.exception("PaddleOCR page reader could not be initialized")
                return None
    return _PAGE_OCR


def _plain(value: Any) -> Any:
    if isinstance(value, np.ndarray):
        return value.tolist()
    if isinstance(value, dict):
        return {key: _plain(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_plain(item) for item in value]
    if isinstance(value, np.generic):
        return value.item()
    return value


def _result_payload(result: Any) -> dict[str, Any]:
    raw = getattr(result, "json", result)
    if callable(raw):
        raw = raw()
    raw = _plain(raw)
    if isinstance(raw, dict) and isinstance(raw.get("res"), dict):
        return raw["res"]
    return raw if isinstance(raw, dict) else {}


def recognize_cell_variants(images: list[np.ndarray]) -> dict[str, Any] | None:
    """OCR several crops with PaddleOCR and return the highest-confidence reading."""
    pipeline = _get_cell_ocr()
    if pipeline is None:
        return None
    candidates: list[dict[str, Any]] = []
    for variant_index, image in enumerate(images):
        try:
            with _CELL_OCR_LOCK:
                outputs = list(pipeline.predict(input=image))
        except Exception:
            LOGGER.exception("PaddleOCR cell retry failed")
            continue
        for output in outputs:
            payload = _result_payload(output)
            texts = [str(value).strip() for value in payload.get("rec_texts", [])]
            scores = [float(value) for value in payload.get("rec_scores", [])]
            valid = [
                (text, scores[index] if index < len(scores) else 0.0)
                for index, text in enumerate(texts)
                if text
            ]
            if not valid:
                continue
            text = " ".join(value for value, _ in valid)
            confidence = sum(score for _, score in valid) / len(valid)
            if confidence > 1:
                confidence /= 100
            candidates.append(
                {
                    "text": text,
                    "confidence": round(confidence * 100, 2),
                    "variant_index": variant_index,
                }
            )
    if not candidates:
        return None
    selected = max(
        candidates,
        key=lambda candidate: (
            candidate["confidence"],
            len(re.sub(r"\s+", "", candidate["text"])),
        ),
    )
    return {**selected, "candidates": candidates}


def recognize_page(image: np.ndarray, page: int = 1) -> list[dict[str, Any]]:
    """Run the PaddleOCR text pipeline and preserve word geometry."""
    pipeline = _get_page_ocr()
    if pipeline is None:
        return []
    try:
        with _PAGE_OCR_LOCK:
            outputs = list(pipeline.predict(input=image))
    except Exception:
        LOGGER.exception("PaddleOCR page recovery failed")
        return []
    height, width = image.shape[:2]
    tokens: list[dict[str, Any]] = []
    for output in outputs:
        payload = _result_payload(output)
        tokens.extend(
            _tokens_from_ocr(
                payload,
                page,
                width,
                height,
                engine="paddleocr_v5_mobile",
            )
        )
    return tokens


def recognize_page_accurate(image: np.ndarray, page: int = 1) -> list[dict[str, Any]]:
    """Run the server model for small/weak sources where character detail is scarce."""
    pipeline = _get_cell_ocr()
    if pipeline is None:
        return []
    try:
        with _CELL_OCR_LOCK:
            outputs = list(pipeline.predict(input=image))
    except Exception:
        LOGGER.exception("PaddleOCR accurate page recovery failed")
        return []
    height, width = image.shape[:2]
    tokens: list[dict[str, Any]] = []
    for output in outputs:
        payload = _result_payload(output)
        tokens.extend(
            _tokens_from_ocr(
                payload,
                page,
                width,
                height,
                engine="paddleocr_v5_server",
            )
        )
    return tokens


def _as_bbox(value: Any) -> list[int] | None:
    if value is None:
        return None
    array = np.asarray(value, dtype=float)
    if not array.size:
        return None
    if array.ndim == 1 and array.size >= 4:
        x1, y1, x2, y2 = array[:4]
    else:
        points = array.reshape(-1, 2)
        x1, y1 = points.min(axis=0)
        x2, y2 = points.max(axis=0)
    return [int(round(x1)), int(round(y1)), int(round(x2)), int(round(y2))]


def _tokens_from_ocr(
    ocr: dict[str, Any],
    page: int,
    page_width: int,
    page_height: int,
    engine: str = "pp_structure_v3",
) -> list[dict[str, Any]]:
    texts = ocr.get("rec_texts") or []
    scores = ocr.get("rec_scores") or []
    polygons = ocr.get("rec_polys") or ocr.get("dt_polys") or []
    boxes = ocr.get("rec_boxes") or []
    tokens: list[dict[str, Any]] = []
    for index, text in enumerate(texts):
        text = str(text or "").strip()
        if not text:
            continue
        geometry = polygons[index] if index < len(polygons) else (
            boxes[index] if index < len(boxes) else None
        )
        bbox = _as_bbox(geometry)
        if bbox is None:
            continue
        score = float(scores[index]) if index < len(scores) else 0.0
        confidence = score * 100 if score <= 1 else score
        x1, y1, x2, y2 = bbox
        tokens.append(
            {
                "text": text,
                "confidence": round(max(0.0, min(100.0, confidence)), 2),
                "bbox": bbox,
                "x0": x1,
                "x1": x2,
                "y0": y1,
                "y1": y2,
                "cx": (x1 + x2) / 2,
                "cy": (y1 + y2) / 2,
                "center_x": (x1 + x2) / 2,
                "center_y": (y1 + y2) / 2,
                "width": max(1, x2 - x1),
                "height": max(1, y2 - y1),
                "page": page,
                "page_width": page_width,
                "page_height": page_height,
                "engine": engine,
            }
        )
    return tokens


def _cell_confidence(bbox: list[int] | None, tokens: list[dict[str, Any]]) -> float:
    if not bbox:
        return 0.0
    x1, y1, x2, y2 = bbox
    matched = [
        token["confidence"]
        for token in tokens
        if x1 <= token["center_x"] <= x2 and y1 <= token["center_y"] <= y2
    ]
    return round(sum(matched) / len(matched), 2) if matched else 0.0


def _safe_span(value: Any) -> int:
    try:
        return max(1, int(value))
    except (TypeError, ValueError):
        return 1


def _parse_table_html(
    html: str,
    cell_boxes: list[Any],
    tokens: list[dict[str, Any]],
    page: int,
    table_index: int,
) -> dict[str, Any] | None:
    if not html or BeautifulSoup is None:
        return None
    soup = BeautifulSoup(html, "html.parser")
    all_nodes = soup.find_all("tr")
    if not all_nodes:
        return None

    thead = soup.find("thead")
    tbody = soup.find("tbody")
    if thead:
        header_nodes = thead.find_all("tr")
        body_nodes = tbody.find_all("tr") if tbody else all_nodes[len(header_nodes) :]
    else:
        section_rows = tbody.find_all("tr") if tbody else all_nodes
        explicit_headers = []
        for row in section_rows:
            if row.find("th") is None:
                break
            explicit_headers.append(row)
        header_nodes = explicit_headers or section_rows[:1]
        body_nodes = section_rows[len(header_nodes) :]
    ordered_nodes = list(header_nodes) + list(body_nodes)

    occupied: set[tuple[int, int]] = set()
    parsed_rows: list[list[dict[str, Any]]] = []
    flat_index = 0
    column_count = 0

    for row_index, row_node in enumerate(ordered_nodes):
        parsed_cells: list[dict[str, Any]] = []
        column_index = 0
        for node in row_node.find_all(["th", "td"], recursive=False):
            while (row_index, column_index) in occupied:
                column_index += 1
            colspan = _safe_span(node.get("colspan"))
            rowspan = _safe_span(node.get("rowspan"))
            bbox = _as_bbox(cell_boxes[flat_index]) if flat_index < len(cell_boxes) else None
            text = node.get_text(" ", strip=True)
            cell = {
                "text": text,
                "value": text,
                "confidence": _cell_confidence(bbox, tokens),
                "column_index": column_index,
                "colspan": colspan,
                "rowspan": rowspan,
                "bbox": bbox,
            }
            parsed_cells.append(cell)
            for row_offset in range(rowspan):
                for column_offset in range(colspan):
                    occupied.add((row_index + row_offset, column_index + column_offset))
            column_index += colspan
            flat_index += 1
        parsed_rows.append(parsed_cells)
        occupied_columns = [
            col + 1 for row, col in occupied if row == row_index
        ]
        column_count = max(column_count, column_index, *occupied_columns, 0)

    header_count = len(header_nodes)
    header_rows = parsed_rows[:header_count]
    data_rows = parsed_rows[header_count:]

    columns: list[dict[str, Any]] = []
    for column_index in range(column_count):
        candidates = [
            cell
            for row in header_rows
            for cell in row
            if cell["column_index"] <= column_index
            < cell["column_index"] + cell["colspan"]
        ]
        leaf = candidates[-1] if candidates else {}
        columns.append(
            {
                "id": f"column_{column_index + 1}",
                "name": str(leaf.get("text") or ""),
                "original_name": str(leaf.get("text") or ""),
                "confidence": float(leaf.get("confidence") or 0),
                "index": column_index,
                "column_index": column_index,
                "semantic_role": semantic_role(str(leaf.get("text") or "")),
            }
        )

    return {
        "table_index": table_index,
        "page": page,
        "title": f"Tabel {table_index + 1}",
        "columns": columns,
        "header_rows": header_rows,
        "rows": [
            {"index": row_index, "cells": row}
            for row_index, row in enumerate(data_rows)
        ],
        "column_count": column_count,
        "row_count": len(data_rows),
        "detection_method": "pp_structure_v3",
        "preserve_original_structure": True,
        "has_merged_cells": any(
            cell["colspan"] > 1 or cell["rowspan"] > 1
            for row in parsed_rows
            for cell in row
        ),
        "bbox": (
            {
                "x0": min(cell["bbox"][0] for row in parsed_rows for cell in row if cell.get("bbox")),
                "y0": min(cell["bbox"][1] for row in parsed_rows for cell in row if cell.get("bbox")),
                "x1": max(cell["bbox"][2] for row in parsed_rows for cell in row if cell.get("bbox")),
                "y1": max(cell["bbox"][3] for row in parsed_rows for cell in row if cell.get("bbox")),
            }
            if any(cell.get("bbox") for row in parsed_rows for cell in row)
            else None
        ),
    }


def _tables_from_result(
    table_results: list[Any], tokens: list[dict[str, Any]], page: int
) -> list[dict[str, Any]]:
    tables: list[dict[str, Any]] = []
    for table_index, raw_table in enumerate(table_results or []):
        payload = _result_payload(raw_table)
        table = _parse_table_html(
            str(payload.get("pred_html") or ""),
            payload.get("cell_box_list") or [],
            tokens,
            page,
            table_index,
        )
        if table:
            tables.append(table)
    return tables


def process_page(image: np.ndarray, page: int = 1) -> dict[str, Any] | None:
    """Run PP-StructureV3 and return normalized OCR/layout/table data."""

    pipeline = _get_pipeline()
    if pipeline is None:
        return None

    try:
        with _PREDICT_LOCK:
            outputs = list(
                pipeline.predict(
                    input=image,
                    use_table_orientation_classify=True,
                    use_ocr_results_with_table_cells=True,
                    use_wired_table_cells_trans_to_html=True,
                    use_wireless_table_cells_trans_to_html=True,
                )
            )
    except Exception as exc:
        LOGGER.exception("PP-StructureV3 prediction failed")
        return {
            "tokens": [],
            "tables": [],
            "layout_regions": [],
            "error": str(exc),
        }

    tokens: list[dict[str, Any]] = []
    tables: list[dict[str, Any]] = []
    regions: list[dict[str, Any]] = []
    for output in outputs:
        payload = _result_payload(output)
        height, width = image.shape[:2]
        page_tokens = _tokens_from_ocr(
            payload.get("overall_ocr_res") or {}, page, width, height
        )
        tokens.extend(page_tokens)
        tables.extend(
            _tables_from_result(payload.get("table_res_list") or [], page_tokens, page)
        )
        for region in payload.get("parsing_res_list") or []:
            bbox = _as_bbox(region.get("block_bbox"))
            regions.append(
                {
                    "label": str(region.get("block_label") or "unknown"),
                    "content": str(region.get("block_content") or ""),
                    "bbox": bbox,
                    "order": region.get("block_order"),
                    "page": page,
                }
            )

    return {
        "tokens": tokens,
        "tables": tables,
        "layout_regions": regions,
        "engine": "PaddleOCR PP-StructureV3",
        "error": None,
    }
