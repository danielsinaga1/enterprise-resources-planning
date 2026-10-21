"""Endpoint pemindaian dokumen SmartDocs.

Service ini murni pemindai: berkas masuk, hasil baca keluar. Tidak ada endpoint
simpan/daftar/laporan di sini — semuanya dilayani modul Spring SmartDocs yang
menjadi satu-satunya penulis db_smartdocs. Pemisahan ini disengaja: dua penulis
ke satu tabel adalah sumber data tidak konsisten yang paling sulit dilacak.
"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Any, Dict

from fastapi import APIRouter, File, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import FileResponse, JSONResponse

from ocr import process_document
from utils import DOCUMENT_FILES_DIR, archive_document_file, clean_filename, save_upload_file

logger = logging.getLogger(__name__)

router = APIRouter()

# Batas jumlah berkas per permintaan batch. Tiap berkas menjalankan pipeline
# PaddleOCR penuh, jadi batch besar mengunci worker sangat lama.
MAX_BATCH_FILES = 50


@router.post("/scan")
async def scan_document(file: UploadFile = File(...)) -> Dict[str, Any]:
    """Pindai satu berkas dan kembalikan hasil bacanya secara terstruktur."""
    save_path: Path | None = None
    try:
        if not file.filename:
            raise ValueError("Tidak ada berkas yang dipilih")

        save_path = save_upload_file(file)
        cleaned_name = clean_filename(file.filename)

        result = await run_in_threadpool(process_document, save_path, cleaned_name)
        ocr_data = result.get("data", {}) if isinstance(result, dict) else result

        # Berkas sumber diarsipkan supaya hasil baca selalu bisa dicocokkan
        # kembali dengan dokumen aslinya saat ditinjau manusia.
        source_archive = await run_in_threadpool(
            archive_document_file, save_path, file.filename
        )
        ocr_data.update(source_archive)

        return {"success": True, "data": ocr_data}

    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - jalur pertahanan
        logger.exception("Pemindaian gagal untuk %s", file.filename)
        raise HTTPException(
            status_code=500, detail=f"Pemindaian OCR gagal: {exc}"
        ) from exc
    finally:
        if save_path is not None:
            save_path.unlink(missing_ok=True)


@router.post("/batch-scan")
async def batch_scan_documents(files: list[UploadFile] = File(...)) -> JSONResponse:
    """Pindai banyak berkas sekaligus.

    Satu berkas gagal tidak menggagalkan seluruh batch: kegagalannya dicatat per
    berkas agar pengguna tahu persis mana yang perlu diulang, bukan kehilangan
    seluruh hasil karena satu gambar rusak.
    """
    if not files:
        raise HTTPException(status_code=400, detail="Tidak ada berkas yang dikirim")
    if len(files) > MAX_BATCH_FILES:
        raise HTTPException(
            status_code=400,
            detail=f"Maksimal {MAX_BATCH_FILES} berkas per permintaan",
        )

    results: list[dict[str, Any]] = []
    failures: list[dict[str, str]] = []

    for file in files:
        save_path: Path | None = None
        try:
            if not file.filename:
                failures.append({"filename": "", "error": "Nama berkas kosong"})
                continue

            save_path = save_upload_file(file)
            cleaned_name = clean_filename(file.filename)
            result = await run_in_threadpool(process_document, save_path, cleaned_name)
            ocr_data = result.get("data", {}) if isinstance(result, dict) else result
            source_archive = await run_in_threadpool(
                archive_document_file, save_path, file.filename
            )
            ocr_data.update(source_archive)
            results.append({"filename": file.filename, "data": ocr_data})
        except Exception as exc:  # pragma: no cover - jalur pertahanan
            logger.exception("Pemindaian batch gagal untuk %s", file.filename)
            failures.append({"filename": file.filename or "", "error": str(exc)})
        finally:
            if save_path is not None:
                save_path.unlink(missing_ok=True)

    return JSONResponse(
        status_code=200,
        content={
            "success": len(failures) == 0,
            "scanned": len(results),
            "failed": len(failures),
            "data": results,
            "failures": failures,
        },
    )


@router.get("/files/{filename}")
async def get_document_file(filename: str) -> FileResponse:
    """Sajikan berkas sumber yang diarsipkan saat pemindaian.

    Peninjau perlu membandingkan angka hasil baca dengan dokumen aslinya —
    tanpa ini, panel keyakinan OCR menyuruh sesuatu yang tidak bisa dilakukan.

    Berkas disajikan lewat endpoint terautentikasi, bukan direktori statis
    publik: isinya invoice dengan nama vendor dan nominal, jadi tidak boleh
    bisa ditebak URL-nya oleh siapa saja.
    """
    # Nama berkas dinormalkan ke basename lalu path hasilnya dipastikan benar-benar
    # berada di dalam direktori arsip. Dua lapis, karena satu lapis saja mudah
    # terlewat saat kode disunting kemudian.
    safe_name = Path(filename).name
    if not safe_name or safe_name in {".", ".."}:
        raise HTTPException(status_code=400, detail="Nama berkas tidak sah")

    target = (DOCUMENT_FILES_DIR / safe_name).resolve()
    if not target.is_relative_to(DOCUMENT_FILES_DIR.resolve()):
        raise HTTPException(status_code=400, detail="Nama berkas tidak sah")
    if not target.is_file():
        raise HTTPException(status_code=404, detail="Berkas sumber tidak ditemukan")

    return FileResponse(target, filename=safe_name)
