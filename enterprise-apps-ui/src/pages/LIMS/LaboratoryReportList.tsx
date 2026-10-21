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
import { laboratoryReportService } from "../../services/limsService";
import ExportButtons from "../../components/common/ExportButtons";
import type { ExportColumn } from "../../utils/exportUtils";
import type { LaboratoryReportDto, LabReportStatus } from "../../types/lims";
import { useLimsPermissions } from "../../hooks/useLimsPermissions";

const statusSeverity = (status: string): "info" | "warning" | "success" | "danger" | undefined => {
  switch (status) {
    case "Open": return "info";
    case "Assigned": return "info";
    case "Reviewed": return "warning";
    case "Close": return "success";
    case "Rejected": return "danger";
    default: return undefined;
  }
};

const statusOptions = [
  { label: "Semua Status", value: null },
  { label: "Open", value: "Open" },
  { label: "Assigned", value: "Assigned" },
  { label: "Reviewed", value: "Reviewed" },
  { label: "Close", value: "Close" },
  { label: "Rejected", value: "Rejected" },
];

const LaboratoryReportList: React.FC = () => {
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);
  const { canCreateReport, canEditReport, canDeleteReport } = useLimsPermissions();

  const [data, setData] = useState<LaboratoryReportDto[]>([]);
  const [totalRecords, setTotalRecords] = useState(0);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState(10);
  const [sortField, setSortField] = useState<string>("createdAt");
  const [sortOrder, setSortOrder] = useState<1 | -1 | 0>(-1);

  const [searchText, setSearchText] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<LabReportStatus | null>(null);
  const [statsCounts, setStatsCounts] = useState<{ total: number; open: number; reviewed: number; closed: number }>({
    total: 0, open: 0, reviewed: 0, closed: 0,
  });

  // Debounce search input to avoid hammering the API on every keystroke.
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(searchText.trim()), 300);
    return () => clearTimeout(t);
  }, [searchText]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const sortDir = sortOrder === 1 ? "asc" : "desc";
      const result = await laboratoryReportService.list(page, rows, sortField, sortDir, {
        status: statusFilter,
        search: debouncedSearch,
      });
      setData(result.data || []);
      setTotalRecords(result.countData ?? 0);
    } catch {
      toast.current?.show({ severity: "error", summary: "Error", detail: "LIMS API belum tersedia", life: 3000 });
      setData([]);
    } finally {
      setLoading(false);
    }
  }, [page, rows, sortField, sortOrder, statusFilter, debouncedSearch]);

  useEffect(() => { loadData(); }, [loadData]);

  // Reset to page 0 whenever the filter changes — otherwise user lands on an empty page.
  useEffect(() => {
    setPage(0);
  }, [statusFilter, debouncedSearch]);

  // Server-side aggregate counts (independent of pagination).
  const loadStats = useCallback(async () => {
    try {
      const res = await laboratoryReportService.getDashboardStats();
      const s = res.data?.stats;
      if (s) {
        setStatsCounts({
          total: Number(s.totalLabReports ?? 0),
          open: Number(s.open ?? 0),
          reviewed: Number(s.reviewed ?? 0),
          closed: Number(s.closed ?? 0),
        });
      }
    } catch { /* dashboard endpoint optional */ }
  }, []);
  useEffect(() => { loadStats(); }, [loadStats]);

  const onPage = (e: DataTablePageEvent) => { setPage(e.page ?? 0); setRows(e.rows); };
  const onSort = (e: DataTableSortEvent) => { setSortField(e.sortField as string); setSortOrder(e.sortOrder as 1 | -1 | 0); setPage(0); };

  const handleDelete = (report: LaboratoryReportDto) => {
    confirmDialog({
      message: `Hapus laporan "${report.noReport}"?`,
      header: "Konfirmasi Hapus",
      icon: "pi pi-exclamation-triangle",
      acceptClassName: "p-button-danger",
      acceptLabel: "Hapus",
      rejectLabel: "Batal",
      accept: async () => {
        try {
          await laboratoryReportService.delete(report.id);
          toast.current?.show({ severity: "success", summary: "Berhasil", detail: "Laporan berhasil dihapus", life: 3000 });
          loadData();
        } catch {
          toast.current?.show({ severity: "error", summary: "Error", detail: "Gagal menghapus laporan", life: 3000 });
        }
      },
    });
  };

  // Server-side filter is applied via the loadData query — render server result directly.
  const filteredData = data;

  // Export configuration
  const exportColumns: ExportColumn[] = [
    { header: 'No. Laporan', field: 'noReport' },
    { header: 'Tgl Sampling', field: 'dateSampling', formatter: (v) => v ? new Date(v as string).toLocaleDateString('id-ID') : '-' },
    { header: 'Lokasi', field: 'location', formatter: (v) => (v as string) ?? '-' },
    { header: 'Departemen', field: 'departmentName', formatter: (v) => (v as string) ?? '-' },
    { header: 'Tgl Analisis', field: 'dateAnalysis', formatter: (v) => v ? new Date(v as string).toLocaleDateString('id-ID') : '-' },
    { header: 'Status', field: 'status' },
  ];

  const fetchExportData = async (): Promise<Record<string, unknown>[]> => {
    const sortDir = sortOrder === 1 ? 'asc' : 'desc';
    const result = await laboratoryReportService.list(0, 10000, sortField, sortDir);
    return (result.data || []) as unknown as Record<string, unknown>[];
  };

  // Summary stats reflect *all* reports, not just the current page.
  const stats = statsCounts;

  // Column body templates
  const statusBodyTemplate = (row: LaboratoryReportDto) => (
    <div className="flex align-items-center gap-1 flex-wrap">
      <Tag value={row.status} severity={statusSeverity(row.status)} className="text-xs" />
      {/* Hasil di luar spesifikasi harus terlihat dari daftar — kalau hanya
          muncul di halaman detail, laporan OOS mudah lolos tanpa ditinjau. */}
      {!!row.outOfSpecCount && row.outOfSpecCount > 0 && (
        <Tag
          value={`${row.outOfSpecCount} OOS`}
          severity="danger"
          icon="pi pi-exclamation-triangle"
          className="text-xs"
          data-pr-tooltip="Hasil di luar spesifikasi"
        />
      )}
    </div>
  );

  const dateBodyTemplate = (field: "dateSampling" | "dateAnalysis") => (row: LaboratoryReportDto) => {
    const val = row[field];
    if (!val) return <span className="text-500">—</span>;
    return <span className="text-sm">{new Date(val).toLocaleDateString("id-ID", { day: "2-digit", month: "short", year: "numeric" })}</span>;
  };

  const actionBodyTemplate = (row: LaboratoryReportDto) => (
    <div className="flex gap-1">
      <Button icon="pi pi-eye" rounded text severity="info" size="small" tooltip="Detail" tooltipOptions={{ position: "top" }}
        onClick={() => navigate(`${row.id}`)} />
      {canEditReport && (
        <Button icon="pi pi-pencil" rounded text severity="warning" size="small" tooltip="Edit" tooltipOptions={{ position: "top" }}
          onClick={() => navigate(`${row.id}/edit`)} />
      )}
      {canDeleteReport && (
        <Button icon="pi pi-trash" rounded text severity="danger" size="small" tooltip="Hapus" tooltipOptions={{ position: "top" }}
          onClick={() => handleDelete(row)} />
      )}
    </div>
  );

  return (
    <div className="flex flex-column gap-4">
      <Toast ref={toast} />
      <ConfirmDialog />

      {/* Header */}
      <div className="flex align-items-center justify-content-between flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold text-800 m-0">Laporan Laboratorium</h2>
          <p className="text-sm text-500 mt-1 mb-0">Kelola semua laporan analisis laboratorium</p>
        </div>
        <div className="flex gap-2">
          <ExportButtons
            permissionCode="LAB_REPORT_MANAGE"
            columns={exportColumns}
            fetchData={fetchExportData}
            fileName="Laporan_Laboratorium"
            title="Laporan Laboratorium"
            toastRef={toast}
          />
          {canCreateReport && (
            <Button label="Buat Laporan" icon="pi pi-plus" severity="info" outlined className="border-round-lg shadow-1 font-semibold" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem', border: '2px solid #3B82F6' }} onClick={() => navigate("create")} />
          )}
        </div>
      </div>

      {/* Summary Stats */}
      <div className="grid">
        {[
          { label: "Total", value: stats.total, icon: "pi pi-file", bg: "bg-blue-50", color: "text-blue-600" },
          { label: "Open", value: stats.open, icon: "pi pi-clock", bg: "bg-orange-50", color: "text-orange-600" },
          { label: "Reviewed", value: stats.reviewed, icon: "pi pi-check-circle", bg: "bg-green-50", color: "text-green-600" },
          { label: "Closed", value: stats.closed, icon: "pi pi-lock", bg: "bg-purple-50", color: "text-purple-600" },
        ].map((s) => (
          <div key={s.label} className="col-6 lg:col-3">
            <Card className="shadow-1 border-round-xl">
              <div className="flex align-items-center gap-3">
                <div className={`flex align-items-center justify-content-center border-round-lg ${s.bg}`} style={{ width: 44, height: 44 }}>
                  <i className={`${s.icon} text-xl ${s.color}`} />
                </div>
                <div>
                  <p className="text-2xl font-bold text-800 m-0">{loading ? "..." : s.value}</p>
                  <p className="text-xs font-medium text-500 mt-1 mb-0 uppercase">{s.label}</p>
                </div>
              </div>
            </Card>
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
              <InputText value={searchText} onChange={(e) => setSearchText(e.target.value)} placeholder="Cari no. laporan / lokasi..." className="w-20rem" />
            </span>
          </div>
          <div className="flex flex-column gap-1">
            <label className="text-xs font-medium text-500">Status</label>
            <Dropdown value={statusFilter} options={statusOptions} onChange={(e) => setStatusFilter(e.value)} className="w-12rem" />
          </div>
          <Button label="Reset" icon="pi pi-filter-slash" severity="secondary" outlined size="small" onClick={() => { setSearchText(""); setStatusFilter(null); }} />
        </div>
      </Card>

      {/* DataTable */}
      <Card className="shadow-1 border-round-xl">
        <DataTable
          value={filteredData}
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
          emptyMessage="Tidak ada data laporan"
          rowsPerPageOptions={[5, 10, 25, 50]}
          paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
          className="border-round-lg"
        >
          <Column field="noReport" header="No. Laporan" sortable className="font-medium" style={{ width: "160px" }} />
          <Column header="Tgl. Sampling" body={dateBodyTemplate("dateSampling")} sortable sortField="dateSampling" style={{ width: "130px" }} />
          <Column field="location" header="Lokasi" sortable />
          <Column field="departmentName" header="Departemen" body={(row: LaboratoryReportDto) => row.departmentName ?? "—"} style={{ width: "150px" }} />
          <Column header="Tgl. Analisis" body={dateBodyTemplate("dateAnalysis")} sortable sortField="dateAnalysis" style={{ width: "130px" }} />
          <Column header="Status" body={statusBodyTemplate} style={{ width: "120px" }} />
          <Column header="Aksi" body={actionBodyTemplate} style={{ width: "140px" }} frozen alignFrozen="right" />
        </DataTable>
      </Card>
    </div>
  );
};

export default LaboratoryReportList;
