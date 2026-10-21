import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Card } from "primereact/card";
import { Button } from "primereact/button";
import { Tag } from "primereact/tag";
import { ProgressSpinner } from "primereact/progressspinner";
import { Toast } from "primereact/toast";
import { inventoryService } from "../../services/limsService";
import type { InventoryItemDto } from "../../types/lims";

const statusSeverity = (status: string): "success" | "warning" | "danger" | "info" | undefined => {
  switch (status) {
    case "Available": return "success";
    case "Borrowed": return "warning";
    case "Service": return "danger";
    default: return "info";
  }
};

const conditionSeverity = (c: string | null): "success" | "warning" | "danger" | undefined => {
  switch (c) {
    case "Baik": return "success";
    case "Rusak Ringan": return "warning";
    case "Rusak Berat": return "danger";
    default: return undefined;
  }
};

const fmtDate = (s: string | null) =>
  s ? new Date(s).toLocaleDateString("id-ID", { day: "2-digit", month: "long", year: "numeric" }) : "—";
const fmtRupiah = (v: number | null) => "Rp " + (v ?? 0).toLocaleString("id-ID");

const Row = ({ label, children }: { label: string; children: React.ReactNode }) => (
  <div className="col-12 md:col-6 flex flex-column gap-1 py-2">
    <span className="text-xs font-medium text-500 uppercase">{label}</span>
    <span className="text-sm text-800">{children}</span>
  </div>
);

const InventoryDetail: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams();
  const toast = useRef<Toast>(null);
  const [item, setItem] = useState<InventoryItemDto | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    inventoryService.get(Number(id))
      .then((res) => setItem(res.data))
      .catch(() => toast.current?.show({ severity: "error", summary: "Error", detail: "Gagal memuat detail alat", life: 3000 }))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return (
      <div className="flex align-items-center justify-content-center" style={{ minHeight: "60vh" }}>
        <ProgressSpinner strokeWidth="3" />
      </div>
    );
  }

  if (!item) {
    return (
      <div className="flex flex-column gap-3">
        <Toast ref={toast} />
        <p className="text-500">Data alat tidak ditemukan.</p>
        <Button label="Kembali" icon="pi pi-arrow-left" onClick={() => navigate("/dashboard/lims/inventory")} className="w-max" />
      </div>
    );
  }

  return (
    <div className="flex flex-column gap-4">
      <Toast ref={toast} />

      {/* Header */}
      <div className="flex align-items-center justify-content-between flex-wrap gap-3">
        <div className="flex align-items-center gap-3">
          <Button icon="pi pi-arrow-left" rounded text onClick={() => navigate("/dashboard/lims/inventory")} />
          <div>
            <h2 className="text-2xl font-bold text-800 m-0">{item.name}</h2>
            <p className="text-sm text-500 mt-1 mb-0">{item.itemCode}</p>
          </div>
        </div>
        <Button label="Edit" icon="pi pi-pencil" severity="warning" outlined onClick={() => navigate(`/dashboard/lims/inventory/${item.id}/edit`)} />
      </div>

      {/* Status chips */}
      <div className="flex flex-wrap gap-2">
        <Tag value={item.status} severity={statusSeverity(item.status)} />
        {item.condition && <Tag value={item.condition} severity={conditionSeverity(item.condition)} />}
        {item.lowStock && <Tag value="Stok Rendah" severity="danger" icon="pi pi-exclamation-triangle" />}
        {item.isActive === false && <Tag value="Nonaktif" severity="secondary" />}
      </div>

      <div className="grid">
        {/* General info */}
        <div className="col-12 lg:col-8">
          <Card title="Informasi Umum" className="shadow-1 border-round-xl h-full">
            <div className="grid">
              <Row label="Kategori">{item.category ?? "—"}</Row>
              <Row label="Lokasi">{item.location ?? "—"}</Row>
              <Row label="Jumlah Unit">{item.quantity}</Row>
              <Row label="Stok Minimum">{item.minStock}</Row>
              <Row label="Nilai Aset / Unit">{fmtRupiah(item.unitPrice)}</Row>
              <Row label="Total Nilai Aset">{fmtRupiah(item.totalValue)}</Row>
              <div className="col-12 flex flex-column gap-1 py-2">
                <span className="text-xs font-medium text-500 uppercase">Spesifikasi</span>
                <span className="text-sm text-800" style={{ whiteSpace: "pre-wrap" }}>{item.specification ?? "—"}</span>
              </div>
              <div className="col-12 flex flex-column gap-1 py-2">
                <span className="text-xs font-medium text-500 uppercase">Catatan</span>
                <span className="text-sm text-800" style={{ whiteSpace: "pre-wrap" }}>{item.notes ?? "—"}</span>
              </div>
            </div>
          </Card>
        </div>

        {/* Maintenance / borrow */}
        <div className="col-12 lg:col-4">
          <Card title="Kalibrasi & Servis" className="shadow-1 border-round-xl mb-3">
            <div className="grid">
              <Row label="Kalibrasi Terakhir">{fmtDate(item.lastCalibrationDate)}</Row>
              <Row label="Kalibrasi Berikutnya">{fmtDate(item.nextCalibrationDate)}</Row>
              <Row label="Servis Terakhir">{fmtDate(item.lastServiceDate)}</Row>
              <Row label="Servis Berikutnya">{fmtDate(item.nextServiceDate)}</Row>
            </div>
          </Card>

          {item.status === "Borrowed" && (
            <Card title="Peminjaman" className="shadow-1 border-round-xl">
              <div className="grid">
                <Row label="Dipinjam Oleh">{item.borrowedBy ?? "—"}</Row>
                <Row label="Tanggal Pinjam">{fmtDate(item.borrowedAt)}</Row>
              </div>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
};

export default InventoryDetail;
