import { useEffect, useState } from 'react';
import { Dialog } from 'primereact/dialog';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { InputNumber } from 'primereact/inputnumber';
import { Message } from 'primereact/message';
import type {
  SaveSmartDocumentPayload,
  SmartDocumentDetail,
  SmartDocumentItem,
} from '../../types/smartdocs';

/* ============================================================
   KOREKSI DOKUMEN SMARTDOCS
   ============================================================
   Mesin OCR salah baca adalah hal biasa, jadi manusia harus bisa
   membetulkannya. Yang TIDAK boleh hilang saat dibetulkan adalah
   bacaan asli mesin: blob confidence/validation/struktur dikirim
   kembali apa adanya, sehingga nilai terkoreksi dan bacaan mesin
   tetap bisa dibandingkan setelahnya.
   ============================================================ */

interface Props {
  doc: SmartDocumentDetail;
  visible: boolean;
  onHide: () => void;
  onSave: (payload: SaveSmartDocumentPayload) => Promise<void>;
}

type DraftItem = SmartDocumentItem & { _key: number };

const SmartDocumentEditDialog = ({ doc, visible, onHide, onSave }: Props) => {
  const [form, setForm] = useState(doc);
  const [items, setItems] = useState<DraftItem[]>([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Form disiapkan ulang tiap dialog dibuka supaya koreksi yang dibatalkan
  // tidak menempel ke pembukaan berikutnya.
  useEffect(() => {
    if (!visible) return;
    setForm(doc);
    setItems(doc.items.map((item, i) => ({ ...item, _key: i })));
    setError(null);
  }, [visible, doc]);

  const setField = <K extends keyof SmartDocumentDetail>(key: K, value: SmartDocumentDetail[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const setItem = (key: number, patch: Partial<SmartDocumentItem>) =>
    setItems((prev) => prev.map((it) => (it._key === key ? { ...it, ...patch } : it)));

  const addItem = () =>
    setItems((prev) => [
      ...prev,
      { _key: Date.now(), itemName: '', unit: '', quantity: 1, price: 0, totalPrice: 0 },
    ]);

  const removeItem = (key: number) => setItems((prev) => prev.filter((it) => it._key !== key));

  const itemsSubtotal = items.reduce((sum, it) => sum + (it.totalPrice ?? 0), 0);

  const handleSave = async () => {
    if (!form.vendorName?.trim()) {
      setError('Nama vendor wajib diisi.');
      return;
    }
    if (items.some((it) => !it.itemName.trim())) {
      setError('Masih ada baris barang tanpa nama.');
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await onSave({
        vendorName: form.vendorName,
        customerName: form.customerName,
        activityName: form.activityName,
        invoiceNumber: form.invoiceNumber,
        noInvoice: form.noInvoice,
        invoiceDate: form.invoiceDate,
        dueDate: form.dueDate,
        division: form.division,
        category: form.category,
        paymentMethod: form.paymentMethod,
        description: form.description,
        note: form.note,
        currency: form.currency,
        subtotal: form.subtotal,
        tax: form.tax,
        discount: form.discount,
        grandTotal: form.grandTotal,
        address: form.address,
        city: form.city,
        phone: form.phone,
        sourceFilename: form.sourceFilename,
        sourceFilePath: form.sourceFilePath,
        sourceMimeType: form.sourceMimeType,
        // Jejak mesin dikirim kembali utuh — koreksi manusia tidak boleh
        // menghapus dasar pembandingnya.
        confidenceJson: form.confidenceJson,
        validationJson: form.validationJson,
        documentStructureJson: form.documentStructureJson,
        items: items.map(({ _key, ...item }) => item),
      });
      onHide();
    } catch {
      setError('Gagal menyimpan koreksi. Anda mungkin tidak punya hak ubah dokumen.');
    } finally {
      setSaving(false);
    }
  };

  const text = (label: string, key: keyof SmartDocumentDetail, cols = 'col-12 md:col-4') => (
    <div className={cols}>
      <label className="block text-xs text-500 mb-1">{label}</label>
      <InputText
        className="w-full"
        value={(form[key] as string) ?? ''}
        onChange={(e) => setField(key, e.target.value as SmartDocumentDetail[typeof key])}
      />
    </div>
  );

  const money = (label: string, key: 'subtotal' | 'tax' | 'discount' | 'grandTotal') => (
    <div className="col-6 md:col-3">
      <label className="block text-xs text-500 mb-1">{label}</label>
      <InputNumber
        className="w-full"
        inputClassName="w-full"
        value={form[key] ?? 0}
        onValueChange={(e) => setField(key, (e.value ?? 0) as never)}
        mode="decimal"
        minFractionDigits={0}
        maxFractionDigits={2}
      />
    </div>
  );

  return (
    <Dialog
      header="Koreksi Dokumen"
      visible={visible}
      onHide={onHide}
      style={{ width: 'min(1000px, 95vw)' }}
      breakpoints={{ '960px': '95vw' }}
      footer={
        <div className="flex justify-content-end gap-2">
          <Button label="Batal" icon="pi pi-times" outlined severity="secondary" onClick={onHide} />
          <Button label="Simpan Koreksi" icon="pi pi-check" loading={saving} onClick={handleSave} />
        </div>
      }
    >
      <Message
        severity="info"
        className="w-full justify-content-start mb-3"
        text="Bacaan asli mesin OCR tetap tersimpan sebagai jejak audit; koreksi Anda hanya mengubah nilai yang dipakai laporan."
      />

      {error && <Message severity="error" className="w-full justify-content-start mb-3" text={error} />}

      <div className="grid">
        {text('Vendor', 'vendorName')}
        {text('Pelanggan', 'customerName')}
        {text('Nomor Invoice', 'noInvoice')}
        {text('Tanggal Invoice', 'invoiceDate')}
        {text('Jatuh Tempo', 'dueDate')}
        {text('Mata Uang', 'currency')}
        {text('Divisi', 'division')}
        {text('Kategori', 'category')}
        {text('Metode Pembayaran', 'paymentMethod')}
        {text('Alamat', 'address', 'col-12 md:col-8')}
        {text('Kota', 'city')}
        {text('Telepon', 'phone')}
        {text('Catatan', 'note', 'col-12 md:col-8')}
      </div>

      <div className="grid mt-2">
        {money('Subtotal', 'subtotal')}
        {money('Diskon', 'discount')}
        {money('Pajak', 'tax')}
        {money('Total', 'grandTotal')}
      </div>

      <div className="flex align-items-center justify-content-between mt-4 mb-2">
        <span className="font-semibold text-900">Item Dokumen</span>
        <Button label="Tambah Baris" icon="pi pi-plus" size="small" outlined onClick={addItem} />
      </div>

      {items.length === 0 ? (
        <span className="text-sm text-500">Belum ada baris barang.</span>
      ) : (
        <div className="flex flex-column gap-2">
          {items.map((item) => (
            <div key={item._key} className="grid align-items-end">
              <div className="col-12 md:col-4">
                <label className="block text-xs text-500 mb-1">Nama Barang</label>
                <InputText
                  className="w-full"
                  value={item.itemName}
                  onChange={(e) => setItem(item._key, { itemName: e.target.value })}
                />
              </div>
              <div className="col-4 md:col-2">
                <label className="block text-xs text-500 mb-1">Satuan</label>
                <InputText
                  className="w-full"
                  value={item.unit ?? ''}
                  onChange={(e) => setItem(item._key, { unit: e.target.value })}
                />
              </div>
              <div className="col-4 md:col-1">
                <label className="block text-xs text-500 mb-1">Jumlah</label>
                <InputNumber
                  className="w-full"
                  inputClassName="w-full"
                  value={item.quantity ?? 0}
                  onValueChange={(e) => setItem(item._key, { quantity: e.value ?? 0 })}
                />
              </div>
              <div className="col-4 md:col-2">
                <label className="block text-xs text-500 mb-1">Harga</label>
                <InputNumber
                  className="w-full"
                  inputClassName="w-full"
                  value={item.price ?? 0}
                  onValueChange={(e) => setItem(item._key, { price: e.value ?? 0 })}
                  mode="decimal"
                  maxFractionDigits={2}
                />
              </div>
              <div className="col-8 md:col-2">
                <label className="block text-xs text-500 mb-1">Total</label>
                <InputNumber
                  className="w-full"
                  inputClassName="w-full"
                  value={item.totalPrice ?? 0}
                  onValueChange={(e) => setItem(item._key, { totalPrice: e.value ?? 0 })}
                  mode="decimal"
                  maxFractionDigits={2}
                />
              </div>
              <div className="col-4 md:col-1">
                <Button
                  icon="pi pi-trash"
                  severity="danger"
                  text
                  onClick={() => removeItem(item._key)}
                  tooltip="Hapus baris"
                />
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Pengingat, bukan pemaksaan: subtotal boleh berbeda dari jumlah item
          (mis. struk yang barisnya tidak terbaca semua), tapi selisihnya harus
          disadari, bukan lolos diam-diam. */}
      {items.length > 0 && Math.abs(itemsSubtotal - (form.subtotal ?? 0)) > 0.01 && (
        <Message
          severity="warn"
          className="w-full justify-content-start mt-3"
          text={`Jumlah total item (${itemsSubtotal.toLocaleString('id-ID')}) berbeda dari subtotal yang diisi (${(form.subtotal ?? 0).toLocaleString('id-ID')}).`}
        />
      )}
    </Dialog>
  );
};

export default SmartDocumentEditDialog;
