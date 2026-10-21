import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import type { LaboratoryReportDto, SpecStatus } from '../types/lims';
import { cleanTextForPdf } from './chemicalFormat';
import kopReport from '../assets/KopReport.png';
import { userRoleService } from '../services/rbacService';

// Keterangan status spesifikasi pada tabel hasil. Sertifikat harus menyatakan
// kesimpulannya, bukan hanya angkanya — "Belum dinilai" sengaja eksplisit agar
// hasil yang tidak terevaluasi tidak terbaca seolah-olah lolos.
const SPEC_LABEL_PDF: Record<SpecStatus, string> = {
  PASS: 'Sesuai',
  FAIL: 'Di luar spesifikasi',
  NOT_EVALUATED: 'Belum dinilai',
};

// Brand colours
const PRIMARY: [number, number, number] = [15, 76, 129]; // dark teal-blue
const ACCENT: [number, number, number] = [0, 137, 123];  // teal
const LIGHT_BG: [number, number, number] = [245, 248, 250];
const DARK: [number, number, number] = [33, 37, 41];

// Height of the kop surat (letterhead) in mm on A4 (210 mm wide)
const KOP_H = 35;

// Reserved space at the bottom of a page for the footer accent bar.
const FOOTER_RESERVED_MM = 14;

/**
 * Make sure `needed` mm of vertical space is available below `y`.
 * If not, advance to a new page (re-rendering the kop surat) and return the
 * new top-of-content y. Otherwise return `y` unchanged.
 */
function ensureSpace(
  doc: jsPDF,
  y: number,
  needed: number,
  ph: number,
  kopBase64: string,
  primary: [number, number, number],
): number {
  if (y + needed <= ph - FOOTER_RESERVED_MM) return y;
  doc.addPage();
  const pw = doc.internal.pageSize.getWidth();
  doc.addImage(kopBase64, 'PNG', 0, 0, pw, KOP_H);
  doc.setDrawColor(...primary);
  doc.setLineWidth(0.6);
  doc.line(0, KOP_H + 1, pw, KOP_H + 1);
  return KOP_H + 10;
}

function fmtDate(v: string | null | undefined): string {
  if (!v) return '-';
  return new Date(v).toLocaleDateString('id-ID', { day: 'numeric', month: 'long', year: 'numeric' });
}

async function loadImageAsBase64(url: string): Promise<string> {
  const res = await fetch(url);
  const blob = await res.blob();
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => resolve(reader.result as string);
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}

async function findNameByJobTitle(jobTitle: string): Promise<string | null> {
  // Server-side lookup; returns just the matching user's name. Avoids paging
  // through the entire user table on every COA export.
  return userRoleService.findUserNameByJobTitle(jobTitle);
}

/**
 * Generate and download a Certificate of Analysis (COA) PDF
 * from a closed laboratory report.
 */
export async function exportCoaPdf(report: LaboratoryReportDto): Promise<void> {
  const [kopBase64, labSuperintendentName] = await Promise.all([
    loadImageAsBase64(kopReport),
    findNameByJobTitle('Laboratory Superintendent'),
  ]);

  const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
  const pw = doc.internal.pageSize.getWidth();
  const ph = doc.internal.pageSize.getHeight();
  const m = 18; // margin
  const cw = pw - m * 2; // content width

  // ════════════════════════════════════════════════════
  // KOP SURAT (LETTERHEAD)
  // ════════════════════════════════════════════════════
  doc.addImage(kopBase64, 'PNG', 0, 0, pw, KOP_H);

  // Separator line below kop surat
  doc.setDrawColor(...PRIMARY);
  doc.setLineWidth(0.6);
  doc.line(0, KOP_H + 1, pw, KOP_H + 1);
  doc.setLineWidth(0.2);
  doc.setDrawColor(...ACCENT);
  doc.line(0, KOP_H + 2.2, pw, KOP_H + 2.2);

  // ════════════════════════════════════════════════════
  // TITLE
  // ════════════════════════════════════════════════════
  doc.setFontSize(16);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(...PRIMARY);
  doc.text('CERTIFICATE OF ANALYSIS', pw / 2, KOP_H + 12, { align: 'center' });

  doc.setFontSize(9);
  doc.setFont('helvetica', 'normal');
  doc.setTextColor(120, 120, 120);
  

  // Decorative line
  doc.setDrawColor(...ACCENT);
  doc.setLineWidth(0.8);
  doc.line(m, KOP_H + 22, pw - m, KOP_H + 22);
  doc.setLineWidth(0.3);
  doc.setDrawColor(200, 200, 200);
  doc.line(m, KOP_H + 23.2, pw - m, KOP_H + 23.2);

  // ════════════════════════════════════════════════════
  // REPORT INFO — two-column layout
  // ════════════════════════════════════════════════════
  let y = KOP_H + 31;
  const col1x = m;
  const col2x = pw / 2 + 4;
  const labelW = 38;

  const leftInfo: [string, string][] = [
    ['No. Laporan', report.noReport || '-'],
    ['Tgl. Sampling', fmtDate(report.dateSampling)],
    ['Tgl. Analisis', fmtDate(report.dateAnalysis)],
    ['Diminta Oleh', report.requestedBy ? cleanTextForPdf(report.requestedBy) : '-'],
  ];
  const rightInfo: [string, string][] = [
    ['Lokasi', report.location || '-'],
    ['Departemen', report.departmentName || '-'],
    ['Pelapor', report.reporterName || '-'],
    ['Status', report.status || '-'],
  ];

  doc.setFontSize(9);
  doc.setTextColor(...DARK);
  const rowH = 6.5;
  for (let i = 0; i < Math.max(leftInfo.length, rightInfo.length); i++) {
    if (leftInfo[i]) {
      doc.setFont('helvetica', 'bold');
      doc.text(leftInfo[i][0], col1x, y);
      doc.setFont('helvetica', 'normal');
      doc.text(`: ${leftInfo[i][1]}`, col1x + labelW, y);
    }
    if (rightInfo[i]) {
      doc.setFont('helvetica', 'bold');
      doc.text(rightInfo[i][0], col2x, y);
      doc.setFont('helvetica', 'normal');
      doc.text(`: ${rightInfo[i][1]}`, col2x + labelW, y);
    }
    y += rowH;
  }

  if (report.notes) {
    y += 1;
    doc.setFont('helvetica', 'bold');
    doc.text('Catatan', col1x, y);
    doc.setFont('helvetica', 'normal');
    const noteLines = doc.splitTextToSize(`: ${cleanTextForPdf(report.notes)}`, cw - labelW);
    doc.text(noteLines, col1x + labelW, y);
    y += noteLines.length * 4.5;
  }

  y += 6;

  // ════════════════════════════════════════════════════
  // ANALYSIS RESULTS TABLE
  // ════════════════════════════════════════════════════
  doc.setFontSize(11);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(...PRIMARY);
  doc.text('Hasil Analisis', m, y);
  doc.setTextColor(...DARK);
  y += 2;

  const details = report.analysisDetails || [];
  const head = [['No', 'Parameter', 'Jenis Uji', 'Metode', 'Standar', 'Satuan', 'Hasil', 'Ket.']];
  const body = details.map((d, i) => [
    String(i + 1),
    d.parameterName || '-',
    d.testTypeName || '-',
    d.methodName || '-',
    d.standardMeasure || '-',
    d.unitName || '-',
    // Hasil baru disimpan di resultAnalysis; `result` hanya ada pada data lama.
    // Sebelumnya hanya `result` yang dicetak, sehingga laporan baru keluar kosong.
    d.resultAnalysis || d.result || '-',
    SPEC_LABEL_PDF[d.specStatus ?? 'NOT_EVALUATED'],
  ]);

  if (body.length === 0) {
    body.push(['-', 'Tidak ada data analisis', '', '', '', '', '', '']);
  }

  autoTable(doc, {
    startY: y,
    head,
    body,
    styles: { fontSize: 8, cellPadding: 2.5, textColor: DARK, lineColor: [220, 220, 220], lineWidth: 0.2 },
    headStyles: { fillColor: PRIMARY, textColor: [255, 255, 255], fontStyle: 'bold', halign: 'center' },
    alternateRowStyles: { fillColor: LIGHT_BG },
    columnStyles: {
      0: { halign: 'center', cellWidth: 10 },
      6: { halign: 'center', fontStyle: 'bold' },
      7: { halign: 'center', cellWidth: 22 },
    },
    margin: { left: m, right: m },
    theme: 'grid',
  });

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const tableEnd = (doc as any).lastAutoTable?.finalY ?? y + 20;

  // ════════════════════════════════════════════════════
  // REMARK (if any)
  // ════════════════════════════════════════════════════
  y = tableEnd + 8;
  if (report.remark) {
    doc.setFontSize(9);
    const remarkLines = doc.splitTextToSize(cleanTextForPdf(report.remark), cw);
    // Reserve "Keterangan:" header (5mm) + rendered lines (~4mm each).
    y = ensureSpace(doc, y, 5 + remarkLines.length * 4, ph, kopBase64, PRIMARY);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...PRIMARY);
    doc.text('Keterangan:', m, y);
    doc.setFont('helvetica', 'italic');
    doc.setTextColor(80, 80, 80);
    doc.text(remarkLines, m, y + 5);
    y += 5 + remarkLines.length * 4;
  }

  y += 10;

  // ════════════════════════════════════════════════════
  // SIGNATURE SECTION — Laboratory Superintendent only
  // (city + date, "Acknowledgement", signature space,
  //  underlined name, job title — mengikuti format COA lama)
  // ════════════════════════════════════════════════════
  // ~50mm total reserved (date line + label + 24mm sign space + name + title).
  y = ensureSpace(doc, y, 50, ph, kopBase64, PRIMARY);

  const sigName =
    report.reviewerName || labSuperintendentName || '.......................';
  const sigDate = fmtDate(report.reviewedAt || report.acknowledgeAt) !== '-'
    ? fmtDate(report.reviewedAt || report.acknowledgeAt)
    : fmtDate(new Date().toISOString());

  doc.setFontSize(9);
  doc.setTextColor(...DARK);
  doc.setFont('helvetica', 'normal');
  doc.text(`Bontang, ${sigDate}`, m, y);
  doc.text('Acknowledgement', m, y + 5);

  // Ruang tanda tangan, lalu nama dengan garis bawah
  const nameY = y + 29;
  doc.setFont('helvetica', 'bold');
  doc.text(sigName, m, nameY);
  const nameW = doc.getTextWidth(sigName);
  doc.setDrawColor(...DARK);
  doc.setLineWidth(0.3);
  doc.line(m, nameY + 1.3, m + nameW, nameY + 1.3);

  doc.setFont('helvetica', 'normal');
  doc.text('Laboratory Superintendent', m, nameY + 6);

  y = nameY + 12;

  // ════════════════════════════════════════════════════
  // FOOTER
  // ════════════════════════════════════════════════════
  // Bottom accent bar
  doc.setFillColor(...PRIMARY);
  doc.rect(0, ph - 12, pw, 12, 'F');

  doc.setFontSize(7);
  doc.setFont('helvetica', 'normal');
  doc.setTextColor(255, 255, 255);

  const footLeft: string[] = [];
  if (report.reviewedAt) footLeft.push(`Direview: ${fmtDate(report.reviewedAt)}`);
  if (report.acknowledgeAt) footLeft.push(`Disetujui: ${fmtDate(report.acknowledgeAt)}`);
  doc.text(footLeft.join('   |   '), m, ph - 5);

  // Nama perusahaan di tengah footer
  doc.setFont('helvetica', 'bold');
  doc.text('PT Kaltim Parna Industri', pw / 2, ph - 5, { align: 'center' });
  doc.setFont('helvetica', 'normal');

  doc.text(
    `Dicetak: ${new Date().toLocaleDateString('id-ID', { day: 'numeric', month: 'long', year: 'numeric' })}`,
    pw - m,
    ph - 5,
    { align: 'right' }
  );

  // ════════════════════════════════════════════════════
  // SAVE
  // ════════════════════════════════════════════════════
  const safeName = (report.noReport || String(report.id)).replace(/\//g, '-');
  doc.save(`COA_${safeName}.pdf`);
}
