// ============================================================
// SMARTDOCS — dokumen hasil OCR (db_smartdocs)
// ============================================================
// Bentuknya mengikuti DTO backend id.enterpriseapps.smartdocs.dto.*.
//
// Field *Json dibiarkan string: isinya struktur bebas dari mesin OCR yang
// bentuknya masih berubah. Halaman detail mengurainya saat perlu lewat
// parseJson(), jadi perubahan di mesin OCR tidak memaksa perubahan tipe di sini.

/** Satu baris pada daftar dokumen. */
export interface SmartDocumentSummary {
  id: number;
  vendorName: string;
  customerName?: string;
  activityName?: string;
  /** Nomor tampilan: noInvoice bila ada, jika tidak invoiceNumber. */
  invoiceNumber?: string;
  invoiceDate?: string;
  division?: string;
  category?: string;
  paymentMethod?: string;
  currency?: string;
  subtotal?: number;
  tax?: number;
  discount?: number;
  grandTotal: number;
  /** Hasil validasi OCR: "Valid", "Valid dengan catatan", "Perlu ditinjau". */
  reviewStatus?: string;
  sourceFilename?: string;
  sourceFilePath?: string;
  sourceMimeType?: string;
  itemCount?: number;
  createdAt?: string;
}

/** Baris barang pada dokumen. */
export interface SmartDocumentItem {
  id?: number;
  itemName: string;
  quantity?: number;
  price?: number;
  totalPrice?: number;
  unit?: string;
  confidenceJson?: string;
  validationJson?: string;
}

/** Dokumen lengkap beserta item dan jejak OCR-nya. */
export interface SmartDocumentDetail {
  id: number;
  vendorName: string;
  customerName?: string;
  activityName?: string;
  invoiceNumber?: string;
  noInvoice?: string;
  invoiceDate?: string;
  dueDate?: string;
  division?: string;
  category?: string;
  paymentMethod?: string;
  description?: string;
  currency?: string;
  subtotal?: number;
  tax?: number;
  discount?: number;
  grandTotal: number;
  address?: string;
  city?: string;
  phone?: string;
  note?: string;
  sourceFilename?: string;
  sourceFilePath?: string;
  sourceMimeType?: string;
  createdAt?: string;
  reviewStatus?: string;
  confidenceJson?: string;
  validationJson?: string;
  documentStructureJson?: string;
  items: SmartDocumentItem[];
}

export interface SaveSmartDocumentPayload {
  vendorName: string;
  customerName?: string;
  activityName?: string;
  invoiceNumber?: string;
  noInvoice?: string;
  invoiceDate?: string;
  dueDate?: string;
  division?: string;
  category?: string;
  paymentMethod?: string;
  description?: string;
  currency?: string;
  subtotal?: number;
  tax?: number;
  discount?: number;
  grandTotal?: number;
  address?: string;
  city?: string;
  phone?: string;
  note?: string;
  sourceFilename?: string;
  sourceFilePath?: string;
  sourceMimeType?: string;
  confidenceJson?: string;
  validationJson?: string;
  documentStructureJson?: string;
  items?: SmartDocumentItem[];
}

export interface SmartDocsActivity {
  id: number;
  action: string;
  username?: string;
  documentId?: number;
  timestamp: string;
}

export interface SmartDocsDashboard {
  totalDocuments: number;
  totalExpenses: number;
  /** Dokumen yang status validasinya belum "Valid" — antrean tinjauan manusia. */
  needsReview: number;
  recentDocuments: SmartDocumentSummary[];
  recentActivity: SmartDocsActivity[];
}

/** Satu titik data laporan; total untuk nominal, count untuk cacah dokumen. */
export interface SmartDocsBucket {
  label: string;
  total?: number;
  count?: number;
}

export interface SmartDocsReports {
  expensePerMonth: SmartDocsBucket[];
  expensePerDay: SmartDocsBucket[];
  expenseByDivision: SmartDocsBucket[];
  countByCategory: SmartDocsBucket[];
  countByReviewStatus: SmartDocsBucket[];
}

export interface SmartDocumentFilters {
  search?: string;
  division?: string;
  category?: string;
}

/* ---------------------- Pembacaan blob OCR ---------------------- */

/** Skor keyakinan OCR per field header, mis. { vendor: 98.76 }. */
export type SmartDocsConfidence = Record<string, number>;

/** Ringkasan hasil validasi aritmetika dan kelengkapan dokumen. */
export interface SmartDocsValidation {
  is_valid?: boolean;
  status?: string;
  missing_critical?: string[];
  item_count?: number;
  items_subtotal?: number;
  ocr_subtotal?: number;
  subtotal_difference?: number;
  calculated_grand_total?: number;
  ocr_grand_total?: number;
  grand_total_difference?: number;
  formula_mode?: string;
  review_cells?: Array<{ row?: number; field?: string; reason?: string; confidence?: number }>;
}

/** Uraikan blob JSON OCR dengan aman; data OCR kerap tidak lengkap. */
export function parseSmartDocsJson<T>(raw?: string | null): T | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

/* ---------------------- Hasil pindai mesin OCR ---------------------- */
// Bentuk respons service OCR memakai snake_case dan sebagian nama Indonesia
// (nama_barang, jumlah, harga) karena mengikuti kolom invoice yang dibacanya.
// Tipe ini sengaja longgar: mesin OCR menambah field diagnostik dari waktu ke
// waktu, dan halaman unggah hanya memakai sebagian kecil di antaranya.

export interface SmartDocsScanItem {
  item?: string;
  nama_barang?: string;
  satuan?: string;
  unit?: string;
  jumlah?: number;
  qty?: number;
  harga_value?: number;
  price?: number;
  total_value?: number;
  total?: number;
  confidence?: Record<string, number>;
  validation?: Record<string, unknown>;
}

export interface SmartDocsScanResult {
  vendor?: string;
  customer?: string;
  address?: string;
  city?: string;
  phone?: string;
  no_invoice?: string;
  invoice_number?: string;
  invoice_date?: string;
  due_date?: string;
  currency?: string;
  tax?: number;
  discount?: number;
  subtotal?: number;
  grand_total?: number;
  activity_name?: string;
  division?: string;
  category?: string;
  payment_method?: string;
  description?: string;
  note?: string;
  items?: SmartDocsScanItem[];
  invoice?: Record<string, unknown>;
  summary?: Record<string, unknown>;
  summary_details?: Record<string, unknown>;
  document_fields?: Array<Record<string, unknown>>;
  tables?: Array<Record<string, unknown>>;
  confidence?: Record<string, number>;
  validation?: Record<string, unknown>;
  review_status?: string;
  raw_text?: string;
  source_filename?: string;
  source_file_path?: string;
  source_mime_type?: string;
}
