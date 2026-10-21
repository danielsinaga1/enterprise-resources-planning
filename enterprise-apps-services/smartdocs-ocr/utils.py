"""Utility helpers for file handling and input normalization."""
from __future__ import annotations

import mimetypes
import re
import shutil
from pathlib import Path
from typing import Any
from uuid import uuid4

from fastapi import UploadFile

BASE_DIR = Path(__file__).resolve().parent
UPLOAD_DIR = BASE_DIR / "uploads"
DOCUMENT_FILES_DIR = BASE_DIR / "document-files"
ALLOWED_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".tif", ".tiff", ".pdf"}
MAX_UPLOAD_BYTES = 25 * 1024 * 1024


def clean_filename(filename: str) -> str:
    """Return a safe, normalized filename for storage and processing."""
    return Path(filename).name.replace(" ", "_")


def save_upload_file(upload: UploadFile) -> Path:
    """Save a temporary uploaded file to the backend uploads directory."""
    UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    file_name = clean_filename(upload.filename or "upload.bin")
    suffix = Path(file_name).suffix.lower()
    if suffix not in ALLOWED_SUFFIXES:
        raise ValueError("Format file tidak didukung")
    file_path = UPLOAD_DIR / f"{Path(file_name).stem[:80]}-{uuid4().hex[:10]}{suffix}"
    total_bytes = 0
    with file_path.open("wb") as destination:
        while True:
            chunk = upload.file.read(1024 * 1024)
            if not chunk:
                break
            total_bytes += len(chunk)
            if total_bytes > MAX_UPLOAD_BYTES:
                destination.close()
                file_path.unlink(missing_ok=True)
                raise ValueError("Ukuran file melebihi batas 25 MB")
            destination.write(chunk)
    return file_path


def archive_document_file(file_path: Path, original_filename: str) -> dict[str, str]:
    """Copy a validated OCR source into permanent document storage."""
    source = Path(file_path).resolve()
    uploads_root = UPLOAD_DIR.resolve()
    if source.parent != uploads_root or not source.is_file():
        raise ValueError("File sumber OCR tidak valid")

    safe_name = clean_filename(original_filename or source.name)
    suffix = source.suffix.lower()
    if suffix not in ALLOWED_SUFFIXES:
        raise ValueError("Format file sumber tidak didukung")

    DOCUMENT_FILES_DIR.mkdir(parents=True, exist_ok=True)
    safe_stem = re.sub(r"[^A-Za-z0-9._-]+", "-", Path(safe_name).stem).strip("-._")
    archive_name = f"{(safe_stem or 'document')[:80]}-{uuid4().hex[:12]}{suffix}"
    destination = (DOCUMENT_FILES_DIR / archive_name).resolve()
    if destination.parent != DOCUMENT_FILES_DIR.resolve():
        raise ValueError("Lokasi arsip dokumen tidak valid")
    shutil.copy2(source, destination)
    mime_type = mimetypes.guess_type(safe_name)[0] or (
        "application/pdf" if suffix == ".pdf" else "application/octet-stream"
    )
    return {
        "source_filename": safe_name,
        "source_file_path": archive_name,
        "source_file_url": f"/document-files/{archive_name}",
        "source_mime_type": mime_type,
    }
