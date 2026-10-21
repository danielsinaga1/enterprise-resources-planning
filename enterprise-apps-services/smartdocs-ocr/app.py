"""Service OCR SmartDocs — entrypoint FastAPI.

Perbedaan penting dari aplikasi SmartDocs lama:

* Tidak ada lapisan database. Dokumen hasil pindai dikembalikan sebagai JSON dan
  disimpan oleh modul Spring SmartDocs, sehingga hanya ada satu penulis ke
  db_smartdocs.
* Tidak ada login sendiri. Permintaan wajib datang lewat gateway IRMS yang sudah
  memvalidasi JWT dan menyuntikkan X-Gateway-Validated + X-User-Id — pola yang
  sama dengan service RAG.
* Tidak menyajikan frontend. Halaman HTML lama digantikan halaman React di
  enterprise-apps-ui.
"""
from __future__ import annotations

import logging
import os
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from routes import router

logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
logger = logging.getLogger(__name__)

BASE_DIR = Path(__file__).resolve().parent
UPLOAD_DIR = BASE_DIR / "uploads"
DOCUMENT_FILES_DIR = BASE_DIR / "document-files"

# Dibiarkan mati secara default. Menyalakannya berarti siapa pun yang bisa
# menjangkau port ini boleh memakai mesin OCR tanpa token — hanya masuk akal
# saat pengembangan lokal tanpa gateway.
TRUST_ALL = os.getenv("SMARTDOCS_OCR_ALLOW_UNAUTHENTICATED", "false").lower() == "true"

PUBLIC_PATHS = {"/health", "/docs", "/openapi.json", "/redoc"}


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Siapkan direktori kerja sebelum melayani permintaan."""
    UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    DOCUMENT_FILES_DIR.mkdir(parents=True, exist_ok=True)
    yield


app = FastAPI(
    title="SmartDocs OCR Service",
    version="1.0.0",
    description="Pemindaian invoice berbasis PaddleOCR untuk modul SmartDocs IRMS.",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=os.getenv("SMARTDOCS_OCR_ALLOWED_ORIGINS", "*").split(","),
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def gateway_gate(request: Request, call_next):
    """Hanya layani permintaan yang sudah divalidasi gateway IRMS.

    Service ini tidak memvalidasi JWT sendiri — gateway sudah melakukannya dan
    menandainya dengan X-Gateway-Validated. Karena itu port service TIDAK boleh
    terbuka ke jaringan luar; di server ia hanya dijangkau lewat gateway.
    """
    path = request.url.path
    if request.method == "OPTIONS" or path in PUBLIC_PATHS:
        return await call_next(request)

    if not TRUST_ALL and request.headers.get("X-Gateway-Validated") != "true":
        return JSONResponse(
            status_code=401,
            content={
                "success": False,
                "detail": "Permintaan harus melalui gateway IRMS yang terautentikasi.",
            },
        )

    return await call_next(request)


app.include_router(router, prefix="/api")


@app.get("/health")
def health_check() -> dict[str, object]:
    """Status kesehatan service; dipakai health check deploy dan container."""
    return {"status": "ok", "service": "smartdocs-ocr"}
