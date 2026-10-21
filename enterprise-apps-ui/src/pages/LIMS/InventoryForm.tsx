import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Card } from "primereact/card";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import { InputNumber } from "primereact/inputnumber";
import { Dropdown } from "primereact/dropdown";
import { Calendar } from "primereact/calendar";
import { InputSwitch } from "primereact/inputswitch";
import { ProgressSpinner } from "primereact/progressspinner";
import { Toast } from "primereact/toast";
import { inventoryService } from "../../services/limsService";
import type { InventoryItemPayload } from "../../types/lims";

const statusOptions = ["Available", "Borrowed", "Service"].map((s) => ({ label: s, value: s }));
const conditionOptions = ["Baik", "Rusak Ringan", "Rusak Berat"].map((s) => ({ label: s, value: s }));

// ISO yyyy-MM-dd <-> Date helpers (backend uses LocalDate strings).
const toDate = (s: string | null | undefined): Date | null => (s ? new Date(s) : null);
const toIso = (d: Date | null | undefined): string | undefined => {
  if (!d) return undefined;
  const tz = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
  return tz.toISOString().slice(0, 10);
};

interface FormState {
  itemCode: string;
  name: string;
  category: string;
  specification: string;
  location: string;
  condition: string;
  quantity: number;
  minStock: number;
  unitPrice: number;
  status: string;
  lastCalibrationDate: Date | null;
  nextCalibrationDate: Date | null;
  lastServiceDate: Date | null;
  nextServiceDate: Date | null;
  borrowedBy: string;
  borrowedAt: Date | null;
  notes: string;
  isActive: boolean;
}

const emptyForm: FormState = {
  itemCode: "", name: "", category: "", specification: "", location: "",
  condition: "Baik", quantity: 0, minStock: 0, unitPrice: 0, status: "Available",
  lastCalibrationDate: null, nextCalibrationDate: null, lastServiceDate: null, nextServiceDate: null,
  borrowedBy: "", borrowedAt: null, notes: "", isActive: true,
};

const InventoryForm: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams();
  const isEdit = !!id;
  const toast = useRef<Toast>(null);

  const [form, setForm] = useState<FormState>(emptyForm);
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  useEffect(() => {
    if (!isEdit) return;
    inventoryService.get(Number(id))
      .then((res) => {
        const d = res.data;
        setForm({
          itemCode: d.itemCode ?? "",
          name: d.name ?? "",
          category: d.category ?? "",
          specification: d.specification ?? "",
          location: d.location ?? "",
          condition: d.condition ?? "Baik",
          quantity: d.quantity ?? 0,
          minStock: d.minStock ?? 0,
          unitPrice: d.unitPrice ?? 0,
          status: d.status ?? "Available",
          lastCalibrationDate: toDate(d.lastCalibrationDate),
          nextCalibrationDate: toDate(d.nextCalibrationDate),
          lastServiceDate: toDate(d.lastServiceDate),
          nextServiceDate: toDate(d.nextServiceDate),
          borrowedBy: d.borrowedBy ?? "",
          borrowedAt: toDate(d.borrowedAt),
          notes: d.notes ?? "",
          isActive: d.isActive ?? true,
        });
      })
      .catch(() => toast.current?.show({ severity: "error", summary: "Error", detail: "Gagal memuat data alat", life: 3000 }))
      .finally(() => setLoading(false));
  }, [id, isEdit]);

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const handleSubmit = async () => {
    setSubmitted(true);
    if (!form.name.trim()) {
      toast.current?.show({ severity: "warn", summary: "Validasi", detail: "Nama alat wajib diisi", life: 3000 });
      return;
    }
    const payload: InventoryItemPayload = {
      itemCode: form.itemCode.trim() || undefined,
      name: form.name.trim(),
      category: form.category.trim() || undefined,
      specification: form.specification.trim() || undefined,
      location: form.location.trim() || undefined,
      condition: form.condition,
      quantity: form.quantity,
      minStock: form.minStock,
      unitPrice: form.unitPrice,
      status: form.status,
      lastCalibrationDate: toIso(form.lastCalibrationDate),
      nextCalibrationDate: toIso(form.nextCalibrationDate),
      lastServiceDate: toIso(form.lastServiceDate),
      nextServiceDate: toIso(form.nextServiceDate),
      borrowedBy: form.status === "Borrowed" ? (form.borrowedBy.trim() || undefined) : undefined,
      borrowedAt: form.status === "Borrowed" ? toIso(form.borrowedAt) : undefined,
      notes: form.notes.trim() || undefined,
      isActive: form.isActive,
    };

    setSaving(true);
    try {
      if (isEdit) {
        await inventoryService.update(Number(id), payload);
        toast.current?.show({ severity: "success", summary: "Berhasil", detail: "Alat berhasil diperbarui", life: 2000 });
      } else {
        await inventoryService.create(payload);
        toast.current?.show({ severity: "success", summary: "Berhasil", detail: "Alat berhasil ditambahkan", life: 2000 });
      }
      setTimeout(() => navigate("/dashboard/lims/inventory"), 800);
    } catch {
      toast.current?.show({ severity: "error", summary: "Error", detail: "Gagal menyimpan data alat", life: 3000 });
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex align-items-center justify-content-center" style={{ minHeight: "60vh" }}>
        <ProgressSpinner strokeWidth="3" />
      </div>
    );
  }

  const fieldLabel = (text: string, required = false) => (
    <label className="text-sm font-medium text-700 mb-1 block">
      {text} {required && <span className="text-red-500">*</span>}
    </label>
  );

  return (
    <div className="flex flex-column gap-4">
      <Toast ref={toast} />

      <div className="flex align-items-center gap-3">
        <Button icon="pi pi-arrow-left" rounded text onClick={() => navigate("/dashboard/lims/inventory")} />
        <div>
          <h2 className="text-2xl font-bold text-800 m-0">{isEdit ? "Edit Alat" : "Tambah Alat"}</h2>
          <p className="text-sm text-500 mt-1 mb-0">{isEdit ? "Perbarui data alat laboratorium" : "Daftarkan alat laboratorium baru"}</p>
        </div>
      </div>

      <Card className="shadow-1 border-round-xl">
        <div className="grid formgrid p-fluid">
          <div className="field col-12 md:col-4">
            {fieldLabel("Kode Alat")}
            <InputText value={form.itemCode} onChange={(e) => set("itemCode", e.target.value)} placeholder="Kosongkan untuk auto-generate" />
          </div>
          <div className="field col-12 md:col-8">
            {fieldLabel("Nama Alat", true)}
            <InputText value={form.name} onChange={(e) => set("name", e.target.value)}
              className={submitted && !form.name.trim() ? "p-invalid" : ""} placeholder="misal: Spektrofotometer UV-Vis" />
          </div>

          <div className="field col-12 md:col-4">
            {fieldLabel("Kategori")}
            <InputText value={form.category} onChange={(e) => set("category", e.target.value)} placeholder="misal: Instrumen" />
          </div>
          <div className="field col-12 md:col-4">
            {fieldLabel("Lokasi")}
            <InputText value={form.location} onChange={(e) => set("location", e.target.value)} placeholder="misal: Lab Kimia A" />
          </div>
          <div className="field col-12 md:col-4">
            {fieldLabel("Kondisi")}
            <Dropdown value={form.condition} options={conditionOptions} onChange={(e) => set("condition", e.value)} />
          </div>

          <div className="field col-12">
            {fieldLabel("Spesifikasi")}
            <InputTextarea value={form.specification} onChange={(e) => set("specification", e.target.value)} rows={2} autoResize placeholder="Spesifikasi teknis alat" />
          </div>

          <div className="field col-12 md:col-4">
            {fieldLabel("Jumlah Unit (Stok)")}
            <InputNumber value={form.quantity} onValueChange={(e) => set("quantity", e.value ?? 0)} min={0} showButtons />
          </div>
          <div className="field col-12 md:col-4">
            {fieldLabel("Stok Minimum")}
            <InputNumber value={form.minStock} onValueChange={(e) => set("minStock", e.value ?? 0)} min={0} showButtons />
          </div>
          <div className="field col-12 md:col-4">
            {fieldLabel("Nilai Aset / Unit (Rp)")}
            <InputNumber value={form.unitPrice} onValueChange={(e) => set("unitPrice", e.value ?? 0)} min={0} mode="currency" currency="IDR" locale="id-ID" />
          </div>

          <div className="field col-12 md:col-4">
            {fieldLabel("Status")}
            <Dropdown value={form.status} options={statusOptions} onChange={(e) => set("status", e.value)} />
          </div>

          {form.status === "Borrowed" && (
            <>
              <div className="field col-12 md:col-4">
                {fieldLabel("Dipinjam Oleh")}
                <InputText value={form.borrowedBy} onChange={(e) => set("borrowedBy", e.target.value)} placeholder="Nama peminjam" />
              </div>
              <div className="field col-12 md:col-4">
                {fieldLabel("Tanggal Pinjam")}
                <Calendar value={form.borrowedAt} onChange={(e) => set("borrowedAt", (e.value as Date) ?? null)} dateFormat="dd M yy" showIcon />
              </div>
            </>
          )}

          <div className="field col-12 md:col-3">
            {fieldLabel("Kalibrasi Terakhir")}
            <Calendar value={form.lastCalibrationDate} onChange={(e) => set("lastCalibrationDate", (e.value as Date) ?? null)} dateFormat="dd M yy" showIcon />
          </div>
          <div className="field col-12 md:col-3">
            {fieldLabel("Kalibrasi Berikutnya")}
            <Calendar value={form.nextCalibrationDate} onChange={(e) => set("nextCalibrationDate", (e.value as Date) ?? null)} dateFormat="dd M yy" showIcon />
          </div>
          <div className="field col-12 md:col-3">
            {fieldLabel("Servis Terakhir")}
            <Calendar value={form.lastServiceDate} onChange={(e) => set("lastServiceDate", (e.value as Date) ?? null)} dateFormat="dd M yy" showIcon />
          </div>
          <div className="field col-12 md:col-3">
            {fieldLabel("Servis Berikutnya")}
            <Calendar value={form.nextServiceDate} onChange={(e) => set("nextServiceDate", (e.value as Date) ?? null)} dateFormat="dd M yy" showIcon />
          </div>

          <div className="field col-12">
            {fieldLabel("Catatan")}
            <InputTextarea value={form.notes} onChange={(e) => set("notes", e.target.value)} rows={2} autoResize />
          </div>

          <div className="field col-12 flex align-items-center gap-2">
            <InputSwitch checked={form.isActive} onChange={(e) => set("isActive", e.value)} />
            <span className="text-sm text-700">Aktif</span>
          </div>
        </div>

        <div className="flex justify-content-end gap-2 mt-3">
          <Button label="Batal" icon="pi pi-times" severity="secondary" outlined onClick={() => navigate("/dashboard/lims/inventory")} disabled={saving} />
          <Button label={isEdit ? "Simpan Perubahan" : "Simpan"} icon="pi pi-check" onClick={handleSubmit} loading={saving} />
        </div>
      </Card>
    </div>
  );
};

export default InventoryForm;
