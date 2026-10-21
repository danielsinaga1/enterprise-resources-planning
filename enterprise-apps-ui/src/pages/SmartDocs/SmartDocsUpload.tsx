import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { FileUpload, type FileUploadHandlerEvent } from 'primereact/fileupload';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Tag } from 'primereact/tag';
import { Toast } from 'primereact/toast';
import { Message } from 'primereact/message';
import { ProgressBar } from 'primereact/progressbar';
import { smartDocsOcrService, scanResultToPayload } from '../../services/smartDocsOcrService';
import { smartDocsService } from '../../services/smartDocsService';
import type { SaveSmartDocumentPayload, SmartDocsScanResult } from '../../types/smartdocs';
import { formatCurrency, reviewStatusLabel, reviewStatusSeverity } from './smartDocsFormat';

/* ============================================================
   UNGGAH & PINDAI — pintu masuk mesin OCR SmartDocs
   ============================================================
   Alurnya dua langkah dan memang disengaja: pindai dulu, periksa
   hasilnya, baru simpan. Mesin OCR tidak menulis apa pun ke
   database, sehingga tidak ada dokumen salah baca yang masuk
   tanpa sempat dilihat manusia.
   ============================================================ */

const SmartDocsUpload = () => {
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);
  const uploader = useRef<FileUpload>(null);

  const [scanning, setScanning] = useState(false);
  const [saving, setSaving] = useState(false);
  const [scan, setScan] = useState<SmartDocsScanResult | null>(null);
  const [payload, setPayload] = useState<SaveSmartDocumentPayload | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleScan = async (event: FileUploadHandlerEvent) => {
    const file = event.files?.[0];
    if (!file) return;

    setScanning(true);
    setError(null);
    setScan(null);
    setPayload(null);

    try {
      const result = await smartDocsOcrService.scan(file);
      setScan(result);
      setPayload(scanResultToPayload(result));
      toast.current?.show({
        severity: 'success',
        summary: 'Pemindaian selesai',
        detail: 'Periksa hasilnya sebelum disimpan.',
        life: 4000,
      });
    } catch {
      setError(
        'Pemindaian gagal. Pastikan service OCR berjalan dan berkasnya berupa gambar atau PDF yang terbaca.'
      );
    } finally {
      setScanning(false);
      uploader.current?.clear();
    }
  };

  const handleSave = async () => {
    if (!payload) return;
    setSaving(true);
    try {
      const saved = await smartDocsService.create(payload);
      toast.current?.show({
        severity: 'success',
        summary: 'Tersimpan',
        detail: 'Dokumen masuk ke daftar SmartDocs.',
        life: 3000,
      });
      navigate(`/dashboard/smartdocs/documents/${saved.id}`);
    } catch {
      toast.current?.show({
        severity: 'error',
        summary: 'Gagal menyimpan',
        detail: 'Anda mungkin tidak punya hak simpan dokumen.',
        life: 5000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex flex-column gap-3">
      <Toast ref={toast} />

      <div>
        <h2 className="m-0 text-900">Unggah &amp; Pindai Dokumen</h2>
        <span className="text-sm text-500">
          Invoice atau struk dipindai mesin OCR, lalu Anda periksa sebelum disimpan
        </span>
      </div>

      <Card>
        <FileUpload
          ref={uploader}
          name="file"
          accept="image/*,application/pdf"
          maxFileSize={20 * 1024 * 1024}
          customUpload
          uploadHandler={handleScan}
          chooseLabel="Pilih Berkas"
          uploadLabel="Pindai"
          cancelLabel="Batal"
          disabled={scanning}
          emptyTemplate={
            <p className="m-0 text-500 text-sm">
              Tarik berkas ke sini, atau pilih berkas lalu tekan Pindai. Format gambar
              (JPG, PNG, WEBP) atau PDF, maksimal 20 MB.
            </p>
          }
        />
        {scanning && (
          <div className="mt-3">
            <ProgressBar mode="indeterminate" style={{ height: 6 }} />
            <span className="text-xs text-500 mt-2 block">
              Sedang memindai. Deteksi layout dan pembacaan ulang sel bisa memakan
              waktu sampai beberapa menit untuk dokumen yang padat.
            </span>
          </div>
        )}
      </Card>

      {error && <Message severity="error" text={error} className="w-full justify-content-start" />}

      {payload && scan && (
        <>
          <Card title="Hasil Pembacaan">
            <div className="flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
              <Tag
                value={reviewStatusLabel(scan.review_status)}
                severity={reviewStatusSeverity(scan.review_status) ?? undefined}
              />
              <div className="flex gap-2">
                <Button
                  label="Buang Hasil"
                  icon="pi pi-times"
                  outlined
                  severity="secondary"
                  onClick={() => {
                    setScan(null);
                    setPayload(null);
                  }}
                />
                <Button
                  label="Simpan Dokumen"
                  icon="pi pi-save"
                  loading={saving}
                  onClick={handleSave}
                />
              </div>
            </div>

            <div className="grid">
              <div className="col-6 md:col-3">
                <div className="text-xs text-500">Vendor</div>
                <div className="text-sm font-medium text-900">{payload.vendorName}</div>
              </div>
              <div className="col-6 md:col-3">
                <div className="text-xs text-500">Nomor Invoice</div>
                <div className="text-sm font-medium text-900">{payload.noInvoice || '—'}</div>
              </div>
              <div className="col-6 md:col-3">
                <div className="text-xs text-500">Tanggal</div>
                <div className="text-sm font-medium text-900">{payload.invoiceDate || '—'}</div>
              </div>
              <div className="col-6 md:col-3">
                <div className="text-xs text-500">Total</div>
                <div className="text-sm font-bold text-900">
                  {formatCurrency(payload.grandTotal, payload.currency)}
                </div>
              </div>
            </div>
          </Card>

          <Card title={`Item Terbaca (${payload.items?.length ?? 0})`}>
            <DataTable
              value={payload.items ?? []}
              size="small"
              emptyMessage="Mesin tidak menemukan baris barang pada dokumen ini."
            >
              <Column header="Nama Barang" field="itemName" />
              <Column header="Satuan" field="unit" style={{ width: 100 }} />
              <Column header="Jumlah" field="quantity" style={{ width: 90 }} />
              <Column
                header="Harga"
                body={(row) => formatCurrency(row.price, payload.currency)}
                style={{ width: 140 }}
              />
              <Column
                header="Total"
                body={(row) => formatCurrency(row.totalPrice, payload.currency)}
                style={{ width: 150 }}
              />
            </DataTable>
            <span className="text-xs text-500 mt-2 block">
              Angka di atas adalah bacaan mesin. Setelah disimpan, betulkan lewat tombol
              Koreksi di halaman detail dokumen — bacaan asli mesin tetap tersimpan sebagai
              jejak audit.
            </span>
          </Card>
        </>
      )}
    </div>
  );
};

export default SmartDocsUpload;
