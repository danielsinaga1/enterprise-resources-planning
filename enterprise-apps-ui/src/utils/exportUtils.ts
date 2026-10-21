import * as XLSX from 'xlsx';
import { saveAs } from 'file-saver';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';

export interface ExportColumn {
  header: string;
  field: string;
  /** Optional formatter for display */
  formatter?: (value: unknown, row: Record<string, unknown>) => string;
}

/**
 * Export data to Excel (.xlsx)
 */
export function exportToExcel(
  data: Record<string, unknown>[],
  columns: ExportColumn[],
  fileName: string,
  sheetName = 'Data'
): void {
  const rows = data.map((row) =>
    columns.reduce((acc, col) => {
      const raw = getNestedValue(row, col.field);
      acc[col.header] = col.formatter ? col.formatter(raw, row) : (raw ?? '');
      return acc;
    }, {} as Record<string, unknown>)
  );

  const ws = XLSX.utils.json_to_sheet(rows);

  // Auto-size columns
  const colWidths = columns.map((col) => {
    const maxLen = Math.max(
      col.header.length,
      ...rows.map((r) => String(r[col.header] ?? '').length)
    );
    return { wch: Math.min(maxLen + 2, 50) };
  });
  ws['!cols'] = colWidths;

  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, sheetName);

  const buf = XLSX.write(wb, { bookType: 'xlsx', type: 'array' });
  const blob = new Blob([buf], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  });
  saveAs(blob, `${fileName}.xlsx`);
}

/**
 * Export data to PDF
 */
export function exportToPdf(
  data: Record<string, unknown>[],
  columns: ExportColumn[],
  fileName: string,
  title?: string
): void {
  const doc = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' });

  // Title
  if (title) {
    doc.setFontSize(14);
    doc.text(title, 14, 15);
    doc.setFontSize(10);
    doc.text(`Diekspor: ${new Date().toLocaleDateString('id-ID')}`, 14, 22);
  }

  const head = [columns.map((c) => c.header)];
  const body = data.map((row) =>
    columns.map((col) => {
      const raw = getNestedValue(row, col.field);
      return col.formatter ? col.formatter(raw, row) : String(raw ?? '-');
    })
  );

  autoTable(doc, {
    startY: title ? 27 : 14,
    head,
    body,
    styles: { fontSize: 8, cellPadding: 2 },
    headStyles: { fillColor: [67, 56, 202], textColor: 255, fontStyle: 'bold' },
    alternateRowStyles: { fillColor: [245, 247, 255] },
    margin: { left: 14, right: 14 },
  });

  doc.save(`${fileName}.pdf`);
}

/** Get nested object value by dot-notation field path */
function getNestedValue(obj: Record<string, unknown>, path: string): unknown {
  return path.split('.').reduce((acc: unknown, key: string) => {
    if (acc && typeof acc === 'object') return (acc as Record<string, unknown>)[key];
    return undefined;
  }, obj);
}

/* ============================================================
   EKSPOR BERKELOMPOK  (laporan per tipe + chart ringkasan)
   ------------------------------------------------------------
   Satu tabel gabungan menyamarkan bahwa JSO dan NCR punya kolom yang
   berbeda: kolom tim JSO selalu kosong di baris NCR, dan sebaliknya.
   Di sini tiap kelompok membawa definisi kolomnya sendiri lalu keluar
   sebagai sheet terpisah (Excel) atau bagian berjudul sendiri (PDF).
   ============================================================ */

export interface ExportGroup {
  /** Nama sheet Excel / judul bagian PDF, mis. "JSO". */
  name: string;
  columns: ExportColumn[];
  rows: Record<string, unknown>[];
}

export interface ChartSpec {
  title: string;
  points: { label: string; value: number }[];
}

export interface SummaryItem {
  label: string;
  value: string | number;
}

const CHART_COLOR: [number, number, number] = [67, 56, 202];
const INK: [number, number, number] = [55, 65, 81];
const MUTED: [number, number, number] = [120, 128, 145];

const buildRow = (row: Record<string, unknown>, columns: ExportColumn[]) =>
  columns.reduce((acc, col) => {
    const raw = getNestedValue(row, col.field);
    acc[col.header] = col.formatter ? col.formatter(raw, row) : (raw ?? '');
    return acc;
  }, {} as Record<string, unknown>);

/** Satu sheet per kelompok, plus sheet "Ringkasan" berisi angka kuncinya. */
export function exportGroupedToExcel(
  groups: ExportGroup[],
  fileName: string,
  summary?: SummaryItem[],
): void {
  const wb = XLSX.utils.book_new();

  if (summary?.length) {
    const ws = XLSX.utils.json_to_sheet(
      summary.map((s) => ({ Keterangan: s.label, Nilai: s.value })),
    );
    ws['!cols'] = [{ wch: 32 }, { wch: 16 }];
    XLSX.utils.book_append_sheet(wb, ws, 'Ringkasan');
  }

  for (const group of groups) {
    const rows = group.rows.map((r) => buildRow(r, group.columns));
    // Sheet kosong tetap dibuat agar pembaca tahu kelompoknya memang nihil,
    // bukan terlewat saat ekspor.
    const ws = XLSX.utils.json_to_sheet(
      rows.length ? rows : [{ Keterangan: 'Tidak ada data' }],
    );
    if (rows.length) {
      ws['!cols'] = group.columns.map((col) => ({
        wch: Math.min(
          Math.max(col.header.length, ...rows.map((r) => String(r[col.header] ?? '').length)) + 2,
          50,
        ),
      }));
    }
    // Nama sheet Excel maksimal 31 karakter dan tidak boleh memuat : \ / ? * [ ]
    const safeName = group.name.replace(/[:\\/?*[\]]/g, '-').slice(0, 31);
    XLSX.utils.book_append_sheet(wb, ws, safeName);
  }

  const buf = XLSX.write(wb, { bookType: 'xlsx', type: 'array' });
  saveAs(
    new Blob([buf], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    }),
    `${fileName}.xlsx`,
  );
}

/**
 * Bar chart sederhana digambar langsung dengan primitif jsPDF.
 *
 * Sengaja tidak memotret elemen chart di layar (html2canvas): grafik yang
 * diekspor harus mengikuti seluruh data terpilih, sedangkan yang tampil di
 * layar hanya sebagian — dan hasil potretnya buram saat dicetak. Menggambar
 * vektor juga membuat ekspor tetap jalan walau chart tidak sedang dirender.
 */
function drawBarChart(
  doc: jsPDF,
  spec: ChartSpec,
  x: number,
  y: number,
  w: number,
  h: number,
): void {
  doc.setFontSize(9);
  doc.setTextColor(INK[0], INK[1], INK[2]);
  doc.text(spec.title, x, y);

  const plotTop = y + 4;
  const plotBottom = y + h - 8;
  const plotHeight = plotBottom - plotTop;

  doc.setDrawColor(210, 214, 224);
  doc.setLineWidth(0.2);
  doc.line(x, plotBottom, x + w, plotBottom);

  const points = spec.points.slice(0, 12);
  if (!points.length) {
    doc.setFontSize(8);
    doc.setTextColor(MUTED[0], MUTED[1], MUTED[2]);
    doc.text('Tidak ada data', x, plotTop + plotHeight / 2);
    return;
  }

  const max = Math.max(...points.map((p) => p.value), 1);
  const slot = w / points.length;
  const barW = Math.min(slot * 0.6, 14);

  points.forEach((p, i) => {
    const barH = (p.value / max) * plotHeight;
    const bx = x + slot * i + (slot - barW) / 2;
    const by = plotBottom - barH;

    doc.setFillColor(CHART_COLOR[0], CHART_COLOR[1], CHART_COLOR[2]);
    doc.rect(bx, by, barW, barH, 'F');

    doc.setFontSize(6.5);
    doc.setTextColor(INK[0], INK[1], INK[2]);
    doc.text(String(p.value), bx + barW / 2, by - 1.2, { align: 'center' });

    doc.setTextColor(MUTED[0], MUTED[1], MUTED[2]);
    // Label dipotong, bukan dikecilkan lagi — di bawah 6pt tidak terbaca saat dicetak.
    const label = p.label.length > 9 ? `${p.label.slice(0, 8)}.` : p.label;
    doc.text(label, bx + barW / 2, plotBottom + 3.5, { align: 'center' });
  });
}

/**
 * PDF berisi halaman ringkasan (angka kunci + chart), lalu satu bagian per
 * kelompok dengan kolomnya masing-masing.
 */
export function exportGroupedToPdf(
  groups: ExportGroup[],
  fileName: string,
  title: string,
  options?: {
    charts?: ChartSpec[];
    summary?: SummaryItem[];
    subtitle?: string;
  },
): void {
  const doc = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' });
  const pageW = doc.internal.pageSize.getWidth();
  const margin = 14;

  doc.setFontSize(15);
  doc.setTextColor(INK[0], INK[1], INK[2]);
  doc.text(title, margin, 15);

  doc.setFontSize(9);
  doc.setTextColor(MUTED[0], MUTED[1], MUTED[2]);
  doc.text(
    `Diekspor: ${new Date().toLocaleDateString('id-ID', { day: '2-digit', month: 'long', year: 'numeric' })}`,
    margin,
    21,
  );
  if (options?.subtitle) doc.text(options.subtitle, margin, 26);

  let cursor = options?.subtitle ? 34 : 30;

  // ---- Angka kunci ----
  const summary = options?.summary ?? [];
  if (summary.length) {
    const boxW = (pageW - margin * 2) / summary.length;
    summary.forEach((item, i) => {
      const bx = margin + boxW * i;
      doc.setFillColor(245, 247, 255);
      doc.roundedRect(bx, cursor, boxW - 3, 16, 2, 2, 'F');
      doc.setFontSize(7.5);
      doc.setTextColor(MUTED[0], MUTED[1], MUTED[2]);
      doc.text(item.label, bx + 4, cursor + 6);
      doc.setFontSize(12);
      doc.setTextColor(CHART_COLOR[0], CHART_COLOR[1], CHART_COLOR[2]);
      doc.text(String(item.value), bx + 4, cursor + 13);
    });
    cursor += 24;
  }

  // ---- Chart ----
  const charts = options?.charts ?? [];
  if (charts.length) {
    const chartW = (pageW - margin * 2) / charts.length - 6;
    charts.forEach((spec, i) => {
      drawBarChart(doc, spec, margin + (chartW + 6) * i, cursor, chartW, 48);
    });
    cursor += 56;
  }

  // ---- Tabel per kelompok ----
  groups.forEach((group, idx) => {
    // Kelompok pertama boleh menyambung halaman ringkasan bila masih muat.
    if (idx > 0 || cursor > 150) {
      doc.addPage();
      cursor = 18;
    }

    doc.setFontSize(12);
    doc.setTextColor(INK[0], INK[1], INK[2]);
    doc.text(`${group.name} (${group.rows.length})`, margin, cursor);

    if (!group.rows.length) {
      doc.setFontSize(9);
      doc.setTextColor(MUTED[0], MUTED[1], MUTED[2]);
      doc.text('Tidak ada data untuk kelompok ini.', margin, cursor + 7);
      cursor += 16;
      return;
    }

    autoTable(doc, {
      startY: cursor + 4,
      head: [group.columns.map((c) => c.header)],
      body: group.rows.map((row) =>
        group.columns.map((col) => {
          const raw = getNestedValue(row, col.field);
          return col.formatter ? col.formatter(raw, row) : String(raw ?? '-');
        }),
      ),
      styles: { fontSize: 8, cellPadding: 2 },
      headStyles: { fillColor: CHART_COLOR, textColor: 255, fontStyle: 'bold' },
      alternateRowStyles: { fillColor: [245, 247, 255] },
      margin: { left: margin, right: margin },
    });

    cursor = (doc as unknown as { lastAutoTable: { finalY: number } }).lastAutoTable.finalY + 12;
  });

  doc.save(`${fileName}.pdf`);
}
