import api from "./api";
import type {
  SaveSmartDocumentPayload,
  SmartDocsScanItem,
  SmartDocsScanResult,
} from "../types/smartdocs";

// ============================================================
// SMARTDOCS OCR SERVICE
// Container Python terpisah (PaddleOCR), lewat gateway.
// ============================================================
// Service OCR hanya memindai dan TIDAK menyimpan apa pun. Hasilnya diperiksa
// pengguna lebih dulu, lalu dikirim ke modul Spring untuk disimpan — itulah
// sebabnya scan() dan simpan dokumen adalah dua panggilan terpisah.
const PREFIX = "/api/smartdocs-ocr";

// Pemindaian satu invoice menjalankan deteksi layout, OCR, dan percobaan ulang
// per sel; jauh lebih lama dari permintaan API biasa.
const SCAN_TIMEOUT_MS = 180_000;

interface ScanResponse {
  success: boolean;
  data: SmartDocsScanResult;
}

interface BatchScanResponse {
  success: boolean;
  scanned: number;
  failed: number;
  data: Array<{ filename: string; data: SmartDocsScanResult }>;
  failures: Array<{ filename: string; error: string }>;
}

const num = (...values: Array<number | undefined>): number => {
  for (const value of values) {
    if (typeof value === "number" && Number.isFinite(value)) return value;
  }
  return 0;
};

const str = (...values: Array<string | undefined>): string => {
  for (const value of values) {
    if (typeof value === "string" && value.trim()) return value;
  }
  return "";
};

/**
 * Ubah hasil pindai menjadi payload simpan milik modul Spring.
 *
 * Mesin OCR memakai snake_case dan sebagian nama kolom Indonesia, sementara API
 * penyimpanan memakai camelCase — pemetaan dikumpulkan di satu tempat ini supaya
 * perubahan bentuk keluaran mesin tidak menyebar ke halaman-halaman UI.
 *
 * Blok diagnostik mesin (field terdeteksi, tabel, ringkasan, teks mentah)
 * dibungkus ke documentStructureJson: itulah yang membuat dokumen tetap bisa
 * ditelusuri balik ke bacaan aslinya setelah dikoreksi manusia.
 */
export function scanResultToPayload(scan: SmartDocsScanResult): SaveSmartDocumentPayload {
  const items = (scan.items ?? []).map((item: SmartDocsScanItem) => ({
    itemName: str(item.nama_barang, item.item) || "(tanpa nama)",
    unit: str(item.satuan, item.unit),
    quantity: Math.round(num(item.jumlah, item.qty)) || 1,
    price: num(item.harga_value, item.price),
    totalPrice: num(item.total_value, item.total),
    confidenceJson: item.confidence ? JSON.stringify(item.confidence) : undefined,
    validationJson: item.validation ? JSON.stringify(item.validation) : undefined,
  }));

  const structure = {
    document_fields: scan.document_fields ?? [],
    tables: scan.tables ?? [],
    invoice: scan.invoice ?? {},
    summary: scan.summary ?? {},
    summary_details: scan.summary_details ?? {},
    review_status: scan.review_status ?? "",
    raw_text: scan.raw_text ?? "",
  };

  const invoiceNumber = str(scan.no_invoice, scan.invoice_number);

  return {
    vendorName: str(scan.vendor) || "(vendor tidak terbaca)",
    customerName: str(scan.customer),
    activityName: str(scan.activity_name) || "OCR Processed Document",
    invoiceNumber,
    noInvoice: invoiceNumber,
    invoiceDate: str(scan.invoice_date),
    dueDate: str(scan.due_date),
    division: str(scan.division) || "General",
    category: str(scan.category) || "Invoice",
    paymentMethod: str(scan.payment_method),
    description: str(scan.description),
    note: str(scan.note),
    currency: str(scan.currency) || "IDR",
    subtotal: num(scan.subtotal),
    tax: num(scan.tax),
    discount: num(scan.discount),
    grandTotal: num(scan.grand_total),
    address: str(scan.address),
    city: str(scan.city),
    phone: str(scan.phone),
    sourceFilename: str(scan.source_filename),
    sourceFilePath: str(scan.source_file_path),
    sourceMimeType: str(scan.source_mime_type),
    confidenceJson: scan.confidence ? JSON.stringify(scan.confidence) : undefined,
    validationJson: scan.validation ? JSON.stringify(scan.validation) : undefined,
    documentStructureJson: JSON.stringify(structure),
    items,
  };
}

export const smartDocsOcrService = {
  /** Pindai satu berkas. Hasilnya belum tersimpan. */
  async scan(file: File): Promise<SmartDocsScanResult> {
    const form = new FormData();
    form.append("file", file);
    const { data } = await api.post<ScanResponse>(`${PREFIX}/scan`, form, {
      timeoutMs: SCAN_TIMEOUT_MS,
    });
    return data.data;
  },

  /** Pindai beberapa berkas sekaligus; kegagalan dilaporkan per berkas. */
  async batchScan(files: File[]): Promise<BatchScanResponse> {
    const form = new FormData();
    files.forEach((file) => form.append("files", file));
    const { data } = await api.post<BatchScanResponse>(`${PREFIX}/batch-scan`, form, {
      timeoutMs: SCAN_TIMEOUT_MS,
    });
    return data;
  },
};

export default smartDocsOcrService;

/**
 * Ambil berkas sumber yang diarsipkan saat pemindaian.
 *
 * Dipanggil langsung dengan fetch, bukan lewat klien `api`, karena yang
 * dibutuhkan adalah blob mentah — bukan JSON. Token disertakan manual sebab
 * berkas ini tidak disajikan sebagai direktori publik: isinya invoice dengan
 * nama vendor dan nominal, jadi URL-nya tidak boleh bisa ditebak siapa saja.
 */
export async function fetchSourceFile(filename: string): Promise<Blob> {
  const base = import.meta.env.VITE_API_URL || "";
  const token = localStorage.getItem("access_token");
  const res = await fetch(
    `${base}${PREFIX}/files/${encodeURIComponent(filename)}`,
    { headers: token ? { Authorization: `Bearer ${token}` } : {} }
  );
  if (!res.ok) {
    throw new Error(
      res.status === 404
        ? "Berkas sumber tidak ditemukan di arsip."
        : `Gagal mengambil berkas sumber (HTTP ${res.status}).`
    );
  }
  return res.blob();
}
