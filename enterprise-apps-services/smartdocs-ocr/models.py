"""Model respons OCR SmartDocs.

Hanya model hasil pemindaian yang tersisa di sini. Model login, pengguna, dan
penyimpanan dokumen milik aplikasi SmartDocs lama sengaja tidak ikut: autentikasi
ditangani gateway IRMS, dan penyimpanan dokumen ditangani modul Spring
(id.enterpriseapps.smartdocs) yang menjadi satu-satunya penulis db_smartdocs.
"""
from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


class OCRItem(BaseModel):
    """Represents an extracted invoice item."""

    item: str = Field(default="")
    description: str = Field(default="")
    qty: float = Field(default=0.0)
    unit: str = Field(default="")
    price: float = Field(default=0.0)
    total: float = Field(default=0.0)
    confidence: dict[str, float] = Field(default_factory=dict)
    validation: dict[str, Any] = Field(default_factory=dict)
    nama_barang: str = Field(default="")
    satuan: str = Field(default="")
    jumlah: float = Field(default=0.0)
    jumlah_raw: str = Field(default="")
    harga_raw: str = Field(default="")
    harga_normalized: str = Field(default="")
    harga_value: float = Field(default=0.0)
    total_raw: str = Field(default="")
    total_normalized: str = Field(default="")
    total_value: float = Field(default=0.0)
    raw_cells: dict[str, Any] = Field(default_factory=dict)

    model_config = {"extra": "allow"}


class OCRResponse(BaseModel):
    """Structured OCR extraction payload returned to the frontend."""

    vendor: str = Field(default="")
    customer: str = Field(default="")
    address: str = Field(default="")
    city: str = Field(default="")
    phone: str = Field(default="")
    invoice_number: str = Field(default="", alias="no_invoice")
    invoice_date: str = Field(default="")
    due_date: str = Field(default="")
    currency: str = Field(default="IDR")
    tax: float = Field(default=0.0)
    discount: float = Field(default=0.0)
    subtotal: float = Field(default=0.0)
    activity_name: str = Field(default="")
    division: str = Field(default="")
    category: str = Field(default="")
    payment_method: str = Field(default="")
    description: str = Field(default="")
    note: str = Field(default="")
    grand_total: float = Field(default=0.0)
    items: list[OCRItem] = Field(default_factory=list)
    invoice: dict[str, Any] = Field(default_factory=dict)
    summary: dict[str, Any] = Field(default_factory=dict)
    summary_details: dict[str, Any] = Field(default_factory=dict)
    document_fields: list[dict[str, Any]] = Field(default_factory=list)
    tables: list[dict[str, Any]] = Field(default_factory=list)
    confidence: dict[str, float] = Field(default_factory=dict)
    validation: dict[str, Any] = Field(default_factory=dict)
    source_filename: str = Field(default="")
    source_file_path: str = Field(default="")
    source_file_url: str = Field(default="")
    source_mime_type: str = Field(default="")
    page_count: int = Field(default=1)
    review_status: str = Field(default="")
    raw_text: str = Field(default="")
    ocr_text: str = Field(default="")
    preprocessing: list[dict[str, Any]] = Field(default_factory=list)
    layout_regions: list[dict[str, Any]] = Field(default_factory=list)
    document_regions: list[dict[str, Any]] = Field(default_factory=list)
    ocr_engines: list[str] = Field(default_factory=list)
    paddleocr_available: bool = Field(default=False)
    paddleocr_error: str | None = Field(default=None)

    model_config = {
        "populate_by_name": True,
        "extra": "allow",
    }

