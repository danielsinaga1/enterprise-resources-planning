import { useState, useCallback, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { DataTable, type DataTablePageEvent, type DataTableSortEvent } from "primereact/datatable";
import { Column } from "primereact/column";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { Dropdown } from "primereact/dropdown";
import { Card } from "primereact/card";
import { Tag } from "primereact/tag";
import { ConfirmDialog, confirmDialog } from "primereact/confirmdialog";
import { Toast } from "primereact/toast";
import { KpiStatCard } from "../../components/irms/KpiStatCard";
import { inventoryService } from "../../services/limsService";
import type { InventoryItemDto, InventoryStats } from "../../types/lims";

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

const statusOptions = [
  { label: "Semua Status", value: null },
  { label: "Available", value: "Available" },
  { label: "Borrowed", value: "Borrowed" },
  { label: "Service", value: "Service" },
];

const formatRupiah = (v: number) =>
  "Rp " + (Number.isFinite(v) ? v : 0).toLocaleString("id-ID");

const emptyStats: InventoryStats = {
  totalItems: 0, totalUnits: 0, totalValue: 0, lowStockCount: 0,
  calibrationCount: 0, borrowedCount: 0, serviceCount: 0,
};

const InventoryList: React.FC = () => {
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);

  const [data, setData] = useState<InventoryItemDto[]>([]);
  const [totalRecords, setTotalRecords] = useState(0);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState(10);
  const [sortField, setSortField] = useState<string>("name");
  const [sortOrder, setSortOrder] = useState<1 | -1 | 0>(1);

  const [searchText, setSearchText] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<string | null>(null);
  const [categoryFilter, setCategoryFilter] = useState<string | null>(null);
  const [categories, setCategories] = useState<{ label: string; value: string | null }[]>([
    { label: "Semua Kategori", value: null },
  ]);
  const [stats, setStats] = useState<InventoryStats>(emptyStats);

  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(searchText.trim()), 300);
    return () => clearTimeout(t);
  }, [searchText]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const sortDir = sortOrder === 1 ? "asc" : "desc";
      const result = await inventoryService.list({
        page, size: rows, sortBy: sortField, sortDir,
        status: statusFilter, category: categoryFilter, search: debouncedSearch,
      });
      setData(result.data || []);
      setTotalRecords(result.countData ?? 0);
    } catch {
      toast.current?.show({ severity: "error", summary: "Error", detail: "Gagal memuat data inventaris", life: 3000 });
      setData([]);
    } finally {
      setLoading(false);
    }
  }, [page, rows, sortField, sortOrder, statusFilter, categoryFilter, debouncedSearch]);

  useEffect(() => { loadData(); }, [loadData]);
  useEffect(() => { setPage(0); }, [statusFilter, categoryFilter, debouncedSearch]);

  const loadStats = useCallback(async () => {
    try {
      const res = await inventoryService.getDashboardStats();
      if (res.data?.stats) setStats(res.data.stats);
    } catch { /* optional */ }
  }, []);
  useEffect(() => { loadStats(); }, [loadStats]);

  useEffect(() => {
    inventoryService.categories()
      .then((res) => {
        const cats = (res.data || []).map((c) => ({ label: c, value: c as string | null }));
        setCategories([{ label: "Semua Kategori", value: null }, ...cats]);
      })
      .catch(() => { /* optional */ });
  }, []);

  const onPage = (e: DataTablePageEvent) => { setPage(e.page ?? 0); setRows(e.rows); };
  const onSort = (e: DataTableSortEvent) => {
    setSortField(e.sortField as string);
    setSortOrder(e.sortOrder as 1 | -1 | 0);
    setPage(0);
  };

  const handleDelete = (item: InventoryItemDto) => {
    confirmDialog({
      message: `Hapus alat "${item.name}" (${item.itemCode})?`,
      header: "Konfirmasi Hapus",
      icon: "pi pi-exclamation-triangle",
      acceptClassName: "p-button-danger",
      acceptLabel: "Hapus",
      rejectLabel: "Batal",
      accept: async () => {
        try {
          await inventoryService.delete(item.id);
          toast.current?.show({ severity: "success", summary: "Berhasil", detail: "Alat berhasil dihapus", life: 3000 });
          loadData();
          loadStats();
        } catch {
          toast.current?.show({ severity: "error", summary: "Error", detail: "Gagal menghapus alat", life: 3000 });
        }
      },
    });
  };

  const kpiCards = [
    { label: "Jenis Alat", value: stats.totalItems, icon: "pi pi-box", color: "#6366f1", bg: "#eef2ff" },
    { label: "Total Unit", value: stats.totalUnits, icon: "pi pi-database", color: "#06b6d4", bg: "#ecfeff" },
    { label: "Nilai Aset", value: formatRupiah(stats.totalValue), icon: "pi pi-wallet", color: "#22c55e", bg: "#f0fdf4" },
    { label: "Stok Rendah", value: stats.lowStockCount, icon: "pi pi-exclamation-triangle", color: "#f97316", bg: "#fff7ed" },
    { label: "Perlu Kalibrasi", value: stats.calibrationCount, icon: "pi pi-calendar-clock", color: "#8b5cf6", bg: "#f5f3ff" },
    { label: "Dipinjam", value: stats.borrowedCount, icon: "pi pi-arrow-right-arrow-left", color: "#f59e0b", bg: "#fffbeb" },
    { label: "Perlu Servis", value: stats.serviceCount, icon: "pi pi-wrench", color: "#ef4444", bg: "#fef2f2" },
  ];

  const statusBody = (row: InventoryItemDto) => (
    <Tag value={row.status} severity={statusSeverity(row.status)} className="text-xs" />
  );

  const conditionBody = (row: InventoryItemDto) =>
    row.condition
      ? <Tag value={row.condition} severity={conditionSeverity(row.condition)} className="text-xs" />
      : <span className="text-500">—</span>;

  const stockBody = (row: InventoryItemDto) => (
    <span className={row.lowStock ? "text-orange-600 font-semibold" : ""}>
      {row.quantity}
      {row.lowStock && <i className="pi pi-exclamation-triangle text-xs ml-1" title="Stok rendah" />}
      <span className="text-500 text-xs"> / min {row.minStock}</span>
    </span>
  );

  const valueBody = (row: InventoryItemDto) => (
    <span className="text-sm">{formatRupiah(row.totalValue)}</span>
  );

  const calibBody = (row: InventoryItemDto) =>
    row.nextCalibrationDate
      ? <span className="text-sm">{new Date(row.nextCalibrationDate).toLocaleDateString("id-ID", { day: "2-digit", month: "short", year: "numeric" })}</span>
      : <span className="text-500">—</span>;

  const actionBody = (row: InventoryItemDto) => (
    <div className="flex gap-1">
      <Button icon="pi pi-eye" rounded text severity="info" size="small" tooltip="Detail" tooltipOptions={{ position: "top" }}
        onClick={() => navigate(`${row.id}`)} />
      <Button icon="pi pi-pencil" rounded text severity="warning" size="small" tooltip="Edit" tooltipOptions={{ position: "top" }}
        onClick={() => navigate(`${row.id}/edit`)} />
      <Button icon="pi pi-trash" rounded text severity="danger" size="small" tooltip="Hapus" tooltipOptions={{ position: "top" }}
        onClick={() => handleDelete(row)} />
    </div>
  );

  return (
    <div className="flex flex-column gap-4">
      <Toast ref={toast} />
      <ConfirmDialog />

      {/* Header */}
      <div className="flex align-items-center justify-content-between flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold text-800 m-0">Inventaris Alat</h2>
          <p className="text-sm text-500 mt-1 mb-0">Kelola alat & instrumen laboratorium</p>
        </div>
        <div className="flex gap-2 flex-wrap">
          <Button label="Dashboard" icon="pi pi-chart-bar" severity="secondary" outlined
            className="border-round-lg" onClick={() => navigate("/dashboard/lims/inventory/dashboard")} />
          <Button label="Tambah Alat" icon="pi pi-plus"
            className="border-round-lg shadow-1 font-semibold"
            onClick={() => navigate("create")} />
        </div>
      </div>

      {/* KPI cards */}
      <div className="grid">
        {kpiCards.map((s) => (
          <div key={s.label} className="col-6 md:col-4 lg:col-3 xl:col-2">
            <KpiStatCard label={s.label} value={loading ? "..." : s.value} icon={s.icon} color={s.color} bg={s.bg} />
          </div>
        ))}
      </div>

      {/* Filters */}
      <Card className="shadow-1 border-round-xl">
        <div className="flex flex-wrap gap-3 align-items-end">
          <div className="flex flex-column gap-1">
            <label className="text-xs font-medium text-500">Pencarian</label>
            <span className="p-input-icon-left">
              <i className="pi pi-search" />
              <InputText value={searchText} onChange={(e) => setSearchText(e.target.value)} placeholder="Cari kode / nama / lokasi..." className="w-20rem" />
            </span>
          </div>
          <div className="flex flex-column gap-1">
            <label className="text-xs font-medium text-500">Status</label>
            <Dropdown value={statusFilter} options={statusOptions} onChange={(e) => setStatusFilter(e.value)} className="w-12rem" />
          </div>
          <div className="flex flex-column gap-1">
            <label className="text-xs font-medium text-500">Kategori</label>
            <Dropdown value={categoryFilter} options={categories} onChange={(e) => setCategoryFilter(e.value)} className="w-12rem" />
          </div>
          <Button label="Reset" icon="pi pi-filter-slash" severity="secondary" outlined size="small"
            onClick={() => { setSearchText(""); setStatusFilter(null); setCategoryFilter(null); }} />
        </div>
      </Card>

      {/* Table */}
      <Card className="shadow-1 border-round-xl">
        <DataTable
          value={data}
          lazy
          paginator
          first={page * rows}
          rows={rows}
          totalRecords={totalRecords}
          onPage={onPage}
          onSort={onSort}
          sortField={sortField}
          sortOrder={sortOrder}
          loading={loading}
          size="small"
          stripedRows
          responsiveLayout="scroll"
          emptyMessage="Tidak ada data alat"
          rowsPerPageOptions={[5, 10, 25, 50]}
          paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
          className="border-round-lg"
        >
          <Column field="itemCode" header="Kode" sortable className="font-medium" style={{ width: "140px" }} />
          <Column field="name" header="Nama Alat" sortable />
          <Column field="category" header="Kategori" sortable body={(r: InventoryItemDto) => r.category ?? "—"} style={{ width: "130px" }} />
          <Column field="location" header="Lokasi" body={(r: InventoryItemDto) => r.location ?? "—"} style={{ width: "140px" }} />
          <Column header="Stok" body={stockBody} sortable sortField="quantity" style={{ width: "130px" }} />
          <Column header="Kondisi" body={conditionBody} style={{ width: "120px" }} />
          <Column header="Nilai Aset" body={valueBody} sortField="unitPrice" sortable style={{ width: "140px" }} />
          <Column header="Kalibrasi Berikutnya" body={calibBody} sortable sortField="nextCalibrationDate" style={{ width: "150px" }} />
          <Column header="Status" body={statusBody} style={{ width: "120px" }} />
          <Column header="Aksi" body={actionBody} style={{ width: "140px" }} frozen alignFrozen="right" />
        </DataTable>
      </Card>
    </div>
  );
};

export default InventoryList;
