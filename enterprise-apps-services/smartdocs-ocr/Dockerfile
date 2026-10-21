# Service OCR SmartDocs (PaddleOCR).
#
# Dijalankan sebagai container tersendiri seperti service RAG, bukan di dalam
# JVM: PaddleOCR menarik paddlepaddle, OpenCV, dan beberapa model inference
# berukuran gigabyte yang tidak ada urusannya dengan backend Spring.
FROM python:3.11-slim

# Pustaka sistem yang dibutuhkan saat runtime, bukan kenyamanan:
#   libgomp1      - paddlepaddle menautnya. Tanpa ini impor paddle gagal
#                   ("libgomp.so.1: cannot open shared object file") dan ocr.py
#                   diam-diam jatuh ke fallback EasyOCR yang tidak dipasang,
#                   sehingga pemindaian gagal dengan pesan yang menyesatkan.
#   libgl1        - impor cv2 gagal tanpa ini ("libGL.so.1").
#   libglib2.0-0  - dibutuhkan OpenCV.
#   poppler-utils - pdf2image memanggil pdftoppm untuk memindai PDF.
RUN apt-get update && apt-get install -y --no-install-recommends \
        libgomp1 \
        libgl1 \
        libglib2.0-0 \
        poppler-utils \
        && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Dependensi dipasang lebih dulu agar layer-nya ter-cache; paddlepaddle besar
# dan mengunduhnya ulang tiap build sangat mahal.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Model diunduh saat pemindaian pertama dan disimpan di sini. Pasang volume ke
# direktori ini di produksi, jika tidak setiap container baru mengunduh ulang
# beberapa gigabyte model.
ENV PADDLE_PDX_MODEL_SOURCE=huggingface \
    PADDLE_HOME=/app/.model-cache \
    LOG_LEVEL=INFO

RUN mkdir -p /app/uploads /app/document-files /app/.model-cache

EXPOSE 8001

# Satu worker: tiap proses memuat model OCR ke memori, jadi menambah worker
# menggandakan pemakaian RAM alih-alih mempercepat.
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8001", "--workers", "1"]
