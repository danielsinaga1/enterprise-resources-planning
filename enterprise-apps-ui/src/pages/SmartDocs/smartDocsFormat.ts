// Format tampilan bersama halaman-halaman SmartDocs.
//
// Ditaruh di modul sendiri, bukan diulang di tiap halaman, supaya angka rupiah
// dan status tinjauan tampil sama di dashboard, daftar, detail, dan laporan.

/** Rupiah tanpa desimal — nilai invoice selalu bulat dalam praktiknya. */
export const formatCurrency = (value?: number | null, currency = 'IDR'): string => {
  const amount = typeof value === 'number' && Number.isFinite(value) ? value : 0;
  try {
    return new Intl.NumberFormat('id-ID', {
      style: 'currency',
      currency: currency || 'IDR',
      maximumFractionDigits: 0,
    }).format(amount);
  } catch {
    // Mata uang hasil OCR kadang bukan kode ISO yang sah; jangan sampai
    // satu dokumen aneh menjatuhkan seluruh tabel.
    return `${currency} ${new Intl.NumberFormat('id-ID', { maximumFractionDigits: 0 }).format(amount)}`;
  }
};

/** Angka biasa dengan pemisah ribuan. */
export const formatNumber = (value?: number | null): string =>
  new Intl.NumberFormat('id-ID').format(
    typeof value === 'number' && Number.isFinite(value) ? value : 0
  );

export const formatDateTime = (value?: string | null): string => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('id-ID', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
};

/**
 * Tanggal invoice ditampilkan apa adanya.
 *
 * Isinya teks mentah hasil OCR dan sering bukan tanggal ("1 CreamBruille 14").
 * Memformatnya sebagai tanggal akan memunculkan "Invalid Date" persis pada
 * dokumen yang paling perlu ditinjau manusia.
 */
export const formatInvoiceDate = (value?: string | null): string =>
  value && value.trim() ? value : '—';

type Severity = 'success' | 'info' | 'warning' | 'danger' | null;

/** Warna tag status tinjauan hasil validasi OCR. */
export const reviewStatusSeverity = (status?: string | null): Severity => {
  if (!status) return null;
  const s = status.toLowerCase();
  if (s === 'valid') return 'success';
  if (s.includes('catatan')) return 'info';
  if (s.includes('tinjau')) return 'warning';
  return null;
};

export const reviewStatusLabel = (status?: string | null): string =>
  status && status.trim() ? status : 'Tanpa status';

/** Ubah skor keyakinan OCR (0–1 atau 0–100) menjadi persen. */
export const toConfidencePercent = (value?: number | null): number | null => {
  if (typeof value !== 'number' || !Number.isFinite(value)) return null;
  const percent = value <= 1 ? value * 100 : value;
  return Math.max(0, Math.min(100, percent));
};
