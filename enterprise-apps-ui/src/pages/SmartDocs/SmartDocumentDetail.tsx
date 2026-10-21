import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Card } from 'primereact/card';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Tag } from 'primereact/tag';
import { Button } from 'primereact/button';
import { Message } from 'primereact/message';
import { ProgressSpinner } from 'primereact/progressspinner';
import { ProgressBar } from 'primereact/progressbar';
import { Accordion, AccordionTab } from 'primereact/accordion';
import { Toast } from 'primereact/toast';
import SmartDocumentEditDialog from './SmartDocumentEditDialog';
import SmartDocumentSourceDialog from './SmartDocumentSourceDialog';
import { smartDocsService } from '../../services/smartDocsService';
import {
  parseSmartDocsJson,
  type SmartDocsConfidence,
  type SmartDocsValidation,
  type SmartDocumentDetail as DetailData,
  type SmartDocumentItem,
  type SaveSmartDocumentPayload,
} from '../../types/smartdocs';
import {
  formatCurrency,
  formatDateTime,
  formatInvoiceDate,
  reviewStatusLabel,
  reviewStatusSeverity,
  toConfidencePercent,
} from './smartDocsFormat';

/* ============================================================
   DETAIL DOKUMEN SMARTDOCS
   Menampilkan hasil baca mesin OCR apa adanya: nilai, skor
   keyakinan per field, pemeriksaan aritmetika, dan teks mentah.
   ============================================================ */

/** Label Indonesia untuk field header hasil OCR. */
const FIELD_LABELS: Record<string, string> = {
  vendor: 'Vendor',
  customer: 'Pelanggan',
  invoice_number: 'Nomor Invoice',
  invoice_date: 'Tanggal Invoice',
  address: 'Alamat',
  city: 'Kota',
  phone: 'Telepon',
};

const Row = ({ label, value }: { label: string; value?: string | null }) => (
  <div className="flex flex-column gap-1">
    <span className="text-xs text-500">{label}</span>
    <span className="text-sm text-900 font-medium">{value?.trim() ? value : '—'}</span>
  </div>
);

/** Warna bar keyakinan: rendah berarti perlu mata manusia. */
const confidenceColor = (percent: number): string =>
  percent >= 90 ? '#22c55e' : percent >= 70 ? '#f59e0b' : '#ef4444';

const SmartDocumentDetail = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [doc, setDoc] = useState<DetailData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [viewingSource, setViewingSource] = useState(false);
  const toast = useRef<Toast>(null);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    setLoading(true);
    smartDocsService
      .detail(Number(id))
      .then((result) => {
        if (!cancelled) setDoc(result);
      })
      .catch(() => {
        if (!cancelled) setError('Dokumen tidak ditemukan atau gagal dimuat.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  const confidence = useMemo(
    () => parseSmartDocsJson<SmartDocsConfidence>(doc?.confidenceJson) ?? {},
    [doc?.confidenceJson]
  );

  const validation = useMemo(
    () => parseSmartDocsJson<SmartDocsValidation>(doc?.validationJson) ?? {},
    [doc?.validationJson]
  );

  /** Teks mentah OCR disimpan di dalam struktur dokumen, bukan kolom sendiri. */
  const rawText = useMemo(() => {
    const structure = parseSmartDocsJson<{ raw_text?: string }>(doc?.documentStructureJson);
    return structure?.raw_text ?? '';
  }, [doc?.documentStructureJson]);

  // Setelah koreksi tersimpan, detail dimuat ulang dari server — bukan
  // ditambal di klien — supaya yang tampil benar-benar yang tersimpan,
  // termasuk status tinjauan dan total efektif yang dihitung backend.
  const handleSaveCorrection = async (payload: SaveSmartDocumentPayload) => {
    const updated = await smartDocsService.update(Number(id), payload);
    setDoc(updated);
    toast.current?.show({
      severity: 'success',
      summary: 'Koreksi tersimpan',
      detail: 'Nilai dokumen diperbarui.',
      life: 3000,
    });
  };

  if (loading) {
    return (
      <div className="flex justify-content-center p-6">
        <ProgressSpinner style={{ width: 48, height: 48 }} />
      </div>
    );
  }

  if (error || !doc) {
    return (
      <div className="flex flex-column gap-3">
        <Button label="Kembali" icon="pi pi-arrow-left" text onClick={() => navigate('/dashboard/smartdocs/documents')} />
        <Message severity="error" text={error ?? 'Dokumen tidak tersedia.'} className="w-full" />
      </div>
    );
  }

  const confidenceEntries = Object.entries(confidence)
    .map(([key, value]) => ({ key, percent: toConfidencePercent(value) }))
    .filter((entry): entry is { key: string; percent: number } => entry.percent !== null);

  return (
    <div className="flex flex-column gap-3">
      <Toast ref={toast} />
      <SmartDocumentEditDialog
        doc={doc}
        visible={editing}
        onHide={() => setEditing(false)}
        onSave={handleSaveCorrection}
      />
      <SmartDocumentSourceDialog
        filename={doc.sourceFilePath}
        displayName={doc.sourceFilename}
        mimeType={doc.sourceMimeType}
        visible={viewingSource}
        onHide={() => setViewingSource(false)}
      />

      <div className="flex flex-wrap align-items-center justify-content-between gap-2">
        <div className="flex align-items-center gap-2">
          <Button icon="pi pi-arrow-left" text onClick={() => navigate('/dashboard/smartdocs/documents')} />
          <div>
            <h2 className="m-0 text-900">{doc.vendorName}</h2>
            <span className="text-sm text-500">
              {doc.noInvoice || doc.invoiceNumber || 'Tanpa nomor'} · dipindai {formatDateTime(doc.createdAt)}
            </span>
          </div>
        </div>
        <div className="flex align-items-center gap-2">
          <Tag
            value={reviewStatusLabel(doc.reviewStatus)}
            severity={reviewStatusSeverity(doc.reviewStatus) ?? undefined}
          />
          <Button
            label="Berkas Sumber"
            icon="pi pi-image"
            outlined
            severity="secondary"
            disabled={!doc.sourceFilePath}
            tooltip={doc.sourceFilePath ? undefined : "Dokumen ini tidak punya berkas sumber terarsip"}
            onClick={() => setViewingSource(true)}
          />
          <Button label="Koreksi" icon="pi pi-pencil" outlined onClick={() => setEditing(true)} />
        </div>
      </div>

      {/* Peringatan field kritis yang tidak terbaca mesin */}
      {validation.missing_critical && validation.missing_critical.length > 0 && (
        <Message
          severity="warn"
          className="w-full justify-content-start"
          text={`Field berikut tidak terbaca OCR: ${validation.missing_critical
            .map((f) => FIELD_LABELS[f] ?? f)
            .join(', ')}. Periksa berkas sumbernya sebelum dipakai.`}
        />
      )}

      <div className="grid">
        {/* Identitas dokumen */}
        <div className="col-12 lg:col-8">
          <Card title="Informasi Dokumen" className="h-full">
            <div className="grid">
              <div className="col-6 md:col-4"><Row label="Vendor" value={doc.vendorName} /></div>
              <div className="col-6 md:col-4"><Row label="Pelanggan" value={doc.customerName} /></div>
              <div className="col-6 md:col-4"><Row label="Nomor Invoice" value={doc.noInvoice || doc.invoiceNumber} /></div>
              <div className="col-6 md:col-4"><Row label="Tanggal Invoice" value={formatInvoiceDate(doc.invoiceDate)} /></div>
              <div className="col-6 md:col-4"><Row label="Jatuh Tempo" value={doc.dueDate} /></div>
              <div className="col-6 md:col-4"><Row label="Divisi" value={doc.division} /></div>
              <div className="col-6 md:col-4"><Row label="Kategori" value={doc.category} /></div>
              <div className="col-6 md:col-4"><Row label="Metode Pembayaran" value={doc.paymentMethod} /></div>
              <div className="col-6 md:col-4"><Row label="Telepon" value={doc.phone} /></div>
              <div className="col-12 md:col-8"><Row label="Alamat" value={[doc.address, doc.city].filter(Boolean).join(', ')} /></div>
              <div className="col-12 md:col-4"><Row label="Berkas Sumber" value={doc.sourceFilename} /></div>
              {doc.description?.trim() && (
                <div className="col-12"><Row label="Keterangan" value={doc.description} /></div>
              )}
              {doc.note?.trim() && <div className="col-12"><Row label="Catatan" value={doc.note} /></div>}
            </div>
          </Card>
        </div>

        {/* Ringkasan nilai + pemeriksaan aritmetika */}
        <div className="col-12 lg:col-4">
          <Card title="Ringkasan Nilai" className="h-full">
            <div className="flex flex-column gap-2">
              <div className="flex justify-content-between text-sm">
                <span className="text-500">Subtotal</span>
                <span className="text-900">{formatCurrency(doc.subtotal, doc.currency)}</span>
              </div>
              <div className="flex justify-content-between text-sm">
                <span className="text-500">Diskon</span>
                <span className="text-900">- {formatCurrency(doc.discount, doc.currency)}</span>
              </div>
              <div className="flex justify-content-between text-sm">
                <span className="text-500">Pajak</span>
                <span className="text-900">{formatCurrency(doc.tax, doc.currency)}</span>
              </div>
              <hr className="my-2 border-top-1 surface-border" />
              <div className="flex justify-content-between">
                <span className="font-semibold text-900">Total</span>
                <span className="font-bold text-xl text-900">{formatCurrency(doc.grandTotal, doc.currency)}</span>
              </div>

              {/* Selisih hitung mesin vs angka yang terbaca di dokumen */}
              {typeof validation.grand_total_difference === 'number' && validation.grand_total_difference !== 0 && (
                <Message
                  severity="warn"
                  className="mt-2 w-full justify-content-start"
                  text={`Total hasil hitung (${formatCurrency(validation.calculated_grand_total, doc.currency)}) berbeda ${formatCurrency(validation.grand_total_difference, doc.currency)} dari total yang terbaca di dokumen (${formatCurrency(validation.ocr_grand_total, doc.currency)}).`}
                />
              )}
            </div>
          </Card>
        </div>
      </div>

      {/* Baris barang */}
      <Card title={`Item Dokumen (${doc.items.length})`}>
        <DataTable value={doc.items} size="small" emptyMessage="Mesin OCR tidak menemukan baris barang.">
          <Column
            header="Nama Barang"
            body={(row: SmartDocumentItem) => <span className="text-sm text-900">{row.itemName}</span>}
          />
          <Column
            header="Satuan"
            body={(row: SmartDocumentItem) => <span className="text-sm">{row.unit?.trim() || '—'}</span>}
            style={{ width: 100 }}
          />
          <Column
            header="Jumlah"
            body={(row: SmartDocumentItem) => <span className="text-sm">{row.quantity ?? '—'}</span>}
            style={{ width: 90 }}
          />
          <Column
            header="Harga"
            body={(row: SmartDocumentItem) => (
              <span className="text-sm">{formatCurrency(row.price, doc.currency)}</span>
            )}
            style={{ width: 140 }}
          />
          <Column
            header="Total"
            body={(row: SmartDocumentItem) => (
              <span className="text-sm font-semibold">{formatCurrency(row.totalPrice, doc.currency)}</span>
            )}
            style={{ width: 150 }}
          />
          <Column
            header="Periksa"
            body={(row: SmartDocumentItem) => {
              // Tiap baris membawa hasil cek jumlah x harga = total dari mesin.
              const check = parseSmartDocsJson<{ is_valid?: boolean; difference?: number }>(row.validationJson);
              if (!check) return <span className="text-xs text-500">—</span>;
              return check.is_valid ? (
                <Tag value="Cocok" severity="success" />
              ) : (
                <Tag
                  value={`Selisih ${formatCurrency(check.difference, doc.currency)}`}
                  severity="warning"
                />
              );
            }}
            style={{ width: 170 }}
          />
        </DataTable>
      </Card>

      {/* Jejak mesin OCR */}
      <div className="grid">
        <div className="col-12 lg:col-5">
          <Card title="Keyakinan Pembacaan OCR" className="h-full">
            {confidenceEntries.length === 0 ? (
              <span className="text-sm text-500">Tidak ada skor keyakinan pada dokumen ini.</span>
            ) : (
              <div className="flex flex-column gap-3">
                {confidenceEntries.map(({ key, percent }) => (
                  <div key={key} className="flex flex-column gap-1">
                    <div className="flex justify-content-between text-xs">
                      <span className="text-700">{FIELD_LABELS[key] ?? key}</span>
                      <span className="font-semibold" style={{ color: confidenceColor(percent) }}>
                        {percent.toFixed(1)}%
                      </span>
                    </div>
                    <ProgressBar
                      value={percent}
                      showValue={false}
                      style={{ height: 6 }}
                      color={confidenceColor(percent)}
                    />
                  </div>
                ))}
                <span className="text-xs text-500">
                  Skor di bawah 70% sebaiknya dicocokkan langsung dengan berkas aslinya
                  lewat tombol Berkas Sumber di atas.
                </span>
              </div>
            )}
          </Card>
        </div>

        <div className="col-12 lg:col-7">
          <Card title="Hasil Pemeriksaan" className="h-full">
            <div className="grid">
              <div className="col-6 md:col-4"><Row label="Status" value={reviewStatusLabel(doc.reviewStatus)} /></div>
              <div className="col-6 md:col-4"><Row label="Jumlah Item Terbaca" value={String(validation.item_count ?? doc.items.length)} /></div>
              <div className="col-6 md:col-4"><Row label="Mode Perhitungan" value={validation.formula_mode} /></div>
              <div className="col-6 md:col-4"><Row label="Subtotal Item" value={formatCurrency(validation.items_subtotal, doc.currency)} /></div>
              <div className="col-6 md:col-4"><Row label="Subtotal Terbaca" value={formatCurrency(validation.ocr_subtotal, doc.currency)} /></div>
              <div className="col-6 md:col-4"><Row label="Total Terbaca" value={formatCurrency(validation.ocr_grand_total, doc.currency)} /></div>
            </div>

            {validation.review_cells && validation.review_cells.length > 0 && (
              <div className="mt-3">
                <span className="text-xs text-500">
                  {validation.review_cells.length} sel ditandai mesin untuk ditinjau ulang.
                </span>
              </div>
            )}
          </Card>
        </div>
      </div>

      {/* Teks mentah — rujukan terakhir saat angka hasil baca diragukan */}
      {rawText && (
        <Accordion>
          <AccordionTab header="Teks Mentah Hasil OCR">
            <pre
              className="text-xs text-700 m-0"
              style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', maxHeight: 320, overflowY: 'auto' }}
            >
              {rawText}
            </pre>
          </AccordionTab>
        </Accordion>
      )}
    </div>
  );
};

export default SmartDocumentDetail;
