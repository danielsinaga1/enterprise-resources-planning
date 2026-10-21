import { useEffect, useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { DataTable, type DataTablePageEvent, type DataTableSortEvent } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { Dropdown } from 'primereact/dropdown';
import { Card } from 'primereact/card';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { Toast } from 'primereact/toast';
import { incidentReportService, incidentLookupService, authService } from '../../services';
import { encodeReportId } from '../../utils/reportId';
import { ApprovalBadge } from '../../components/irms/StatusBadges';
import ExportButtons from '../../components/common/ExportButtons';
import type { ExportColumn } from '../../utils/exportUtils';
import type { IncidentReport, IncidentLookups } from '../../types/irms';

/* ============================================================
   INCIDENT REPORT LIST PAGE
   Maps from kpi_irms IncidentReportController@index + Blade
   Server-side pagination via PrimeReact DataTable lazy mode
   ============================================================ */
const IncidentReportList = () => {
  const navigate = useNavigate();
  const isSuperAdmin = authService.getUser()?.roles?.includes('SUPER_ADMIN') ?? false;
  const toast = useRef<Toast>(null);

  // Pagination & sort state
  const [data, setData] = useState<IncidentReport[]>([]);
  const [totalRecords, setTotalRecords] = useState(0);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState(10);
  const [sortField, setSortField] = useState<string>('createdAt');
  const [sortOrder, setSortOrder] = useState<1 | -1 | 0>(-1);

  // Filters
  const [searchText, setSearchText] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [statusFilter, setStatusFilter] = useState<string | null>(null);
  const [categoryFilter, setCategoryFilter] = useState<number | null>(null);
  const [yearFilter, setYearFilter] = useState<number | null>(null);

  // Lookups for dropdown filters
  const [lookups, setLookups] = useState<IncidentLookups | null>(null);
  const [yearOptions, setYearOptions] = useState<{ label: string; value: number | null }[]>([]);

  const statusOptions = [
    { label: 'Semua Status', value: null },
    { label: 'Open', value: 'Open' },
    { label: 'In Progress', value: 'In Progress' },
    { label: 'Close', value: 'Close' },
  ];

  // Debounce search input (400ms)
  useEffect(() => {
    if (debounceTimer.current) clearTimeout(debounceTimer.current);
    debounceTimer.current = setTimeout(() => {
      setDebouncedSearch(searchText);
      setPage(1);
    }, 400);
    return () => { if (debounceTimer.current) clearTimeout(debounceTimer.current); };
  }, [searchText]);

  // Load lookups on mount
  useEffect(() => {
    incidentLookupService.getAll().then(setLookups).catch(console.error);
    incidentLookupService.getYears().then((years) => {
      setYearOptions([
        { label: 'Semua Tahun', value: null },
        ...years.map((y) => ({ label: String(y), value: y })),
      ]);
    }).catch(console.error);
  }, []);

  // Load data
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await incidentReportService.list(
        {
          page,
          limit: rows,
          sortBy: sortField,
          sortOrder: sortOrder === 1 ? 'asc' : 'desc',
        },
        {
          status: statusFilter ?? undefined,
          category_id: categoryFilter ?? undefined,
          year: yearFilter ?? undefined,
          search: debouncedSearch || undefined,
        }
      );
      setData(result.data);
      setTotalRecords(result.total);
    } catch (err) {
      console.error('Failed to load incident reports', err);
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Gagal memuat data laporan insiden',
        life: 3000,
      });
    } finally {
      setLoading(false);
    }
  }, [page, rows, sortField, sortOrder, statusFilter, categoryFilter, yearFilter, debouncedSearch]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Handlers
  const onPage = (e: DataTablePageEvent) => {
    setPage((e.page ?? 0) + 1);
    setRows(e.rows);
  };

  const onSort = (e: DataTableSortEvent) => {
    setSortField(e.sortField as string);
    setSortOrder(e.sortOrder as 1 | -1 | 0);
    setPage(1);
  };

  const handleDelete = (report: IncidentReport) => {
    confirmDialog({
      message: `Hapus laporan "${report.reportNumber}"?`,
      header: 'Konfirmasi Hapus',
      icon: 'pi pi-exclamation-triangle',
      acceptClassName: 'p-button-danger',
      acceptLabel: 'Hapus',
      rejectLabel: 'Batal',
      accept: async () => {
        try {
          await incidentReportService.delete(report.id);
          toast.current?.show({
            severity: 'success',
            summary: 'Berhasil',
            detail: 'Laporan insiden berhasil dihapus',
            life: 3000,
          });
          loadData();
        } catch {
          toast.current?.show({
            severity: 'error',
            summary: 'Error',
            detail: 'Gagal menghapus laporan insiden',
            life: 3000,
          });
        }
      },
    });
  };

  // Column body templates
  const statusBodyTemplate = (row: IncidentReport) => <ApprovalBadge status={row.status} />;

  const dateBodyTemplate = (row: IncidentReport) => (
    <span className="text-sm">
      {row.incidentDate
        ? new Date(row.incidentDate).toLocaleDateString('id-ID', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
          })
        : '—'}
    </span>
  );

  const actionBodyTemplate = (row: IncidentReport) => (
    <div className="flex gap-1">
      <Button
        icon="pi pi-eye"
        rounded
        text
        severity="info"
        size="small"
        tooltip="Lihat Detail"
        tooltipOptions={{ position: 'top' }}
        onClick={() => navigate(`/dashboard/irms/incident-reports/${encodeReportId(row.id)}`)}
      />
      <Button
        icon="pi pi-pencil"
        rounded
        text
        severity="warning"
        size="small"
        tooltip="Edit"
        tooltipOptions={{ position: 'top' }}
        onClick={() => navigate(`/dashboard/irms/incident-reports/${encodeReportId(row.id)}/edit`)}
      />
      {/* Hapus: SUPER_ADMIN saja. Backend menolak yang lain (@PreAuthorize), ini hanya
          menyembunyikan tombolnya supaya tidak menawarkan aksi yang pasti gagal. */}
      {isSuperAdmin && (
        <Button
          icon="pi pi-trash"
          rounded
          text
          severity="danger"
          size="small"
          tooltip="Hapus"
          tooltipOptions={{ position: 'top' }}
          onClick={() => handleDelete(row)}
        />
      )}
    </div>
  );

  // Data is now filtered server-side via the search parameter
  const filteredData = data;

  const categoryOptions = lookups
    ? [
        { label: 'Semua Kategori', value: null },
        ...lookups.categories.map((c) => ({ label: c.name, value: c.id })),
      ]
    : [];

  // Export configuration
  const exportColumns: ExportColumn[] = [
    { header: 'No. Laporan', field: 'reportNumber' },
    { header: 'Judul', field: 'title' },
    { header: 'Tanggal Insiden', field: 'incidentDate', formatter: (v) => v ? new Date(v as string).toLocaleDateString('id-ID') : '-' },
    { header: 'Kategori', field: 'category.name', formatter: (v) => (v as string) ?? '-' },
    { header: 'Departemen', field: 'originDepartment.name', formatter: (v) => (v as string) ?? '-' },
    { header: 'Nama Pelapor', field: 'reporterName', formatter: (v) => (v as string) ?? '-' },
    { header: 'Status', field: 'status' },
  ];

  const fetchExportData = async (): Promise<Record<string, unknown>[]> => {
    const result = await incidentReportService.list({ page: 1, limit: 10000, sortBy: sortField, sortOrder: sortOrder === 1 ? 'asc' : 'desc' }, { status: statusFilter ?? undefined, category_id: categoryFilter ?? undefined, year: yearFilter ?? undefined });
    return result.data as unknown as Record<string, unknown>[];
  };

  return (
    <div className="flex flex-column gap-4">
      <Toast ref={toast} />
      <ConfirmDialog />

      {/* Header */}
      <div className="flex align-items-center justify-content-between flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold text-800 m-0">Laporan Insiden</h2>
          <p className="text-sm text-500 mt-1 mb-0">Kelola semua laporan insiden</p>
        </div>
        <div className="flex gap-2">
          <ExportButtons
            permissionCode="INCIDENT_REPORT_MANAGE"
            columns={exportColumns}
            fetchData={fetchExportData}
            fileName="Laporan_Insiden"
            title="Laporan Insiden"
            toastRef={toast}
          />
          <Button
            label="Buat Laporan"
            icon="pi pi-plus"
            severity="info"
            outlined
            className="border-round-lg shadow-1 font-semibold"
            style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem', border: '2px solid #3B82F6' }}
            onClick={() => navigate('/dashboard/irms/incident-reports/create')}
          />
        </div>
      </div>

      {/* Filters */}
      <Card className="shadow-1 border-round-xl">
        <div className="flex flex-wrap gap-3 align-items-end">
          <div className="flex flex-column gap-1">
            <label className="text-xs font-medium text-500">Pencarian</label>
            <span className="p-input-icon-left">
              <i className="pi pi-search" />
              <InputText
                value={searchText}
                onChange={(e) => setSearchText(e.target.value)}
                placeholder="Cari no. laporan / judul..."
                className="w-20rem"
              />
            </span>
          </div>
          <div className="flex flex-column gap-1">
            <label className="text-xs font-medium text-500">Status</label>
            <Dropdown
              value={statusFilter}
              options={statusOptions}
              onChange={(e) => {
                setStatusFilter(e.value);
                setPage(1);
              }}
              className="w-12rem"
            />
          </div>
          <div className="flex flex-column gap-1">
            <label className="text-xs font-medium text-500">Kategori</label>
            <Dropdown
              value={categoryFilter}
              options={categoryOptions}
              onChange={(e) => {
                setCategoryFilter(e.value);
                setPage(1);
              }}
              className="w-12rem"
              loading={!lookups}
            />
          </div>
          <div className="flex flex-column gap-1">
            <label className="text-xs font-medium text-500">Tahun</label>
            <Dropdown
              value={yearFilter}
              options={yearOptions}
              onChange={(e) => {
                setYearFilter(e.value);
                setPage(1);
              }}
              className="w-10rem"
              loading={yearOptions.length === 0}
            />
          </div>
          <Button
            label="Reset"
            icon="pi pi-filter-slash"
            severity="secondary"
            outlined
            size="small"
            onClick={() => {
              setSearchText('');
              setStatusFilter(null);
              setCategoryFilter(null);
              setYearFilter(null);
              setPage(1);
            }}
          />
        </div>
      </Card>

      {/* DataTable */}
      <Card className="shadow-1 border-round-xl">
        <DataTable
          value={filteredData}
          lazy
          paginator
          first={(page - 1) * rows}
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
          emptyMessage="Tidak ada data laporan insiden"
          rowsPerPageOptions={[5, 10, 25, 50]}
          paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
          className="border-round-lg"
        >
          <Column
            field="reportNumber"
            header="No. Laporan"
            sortable
            className="font-medium"
            style={{ width: '160px' }}
          />
          <Column field="title" header="Judul" sortable />
          <Column header="Tanggal" body={dateBodyTemplate} sortable sortField="incidentDate" style={{ width: '130px' }} />
          <Column
            header="Kategori"
            body={(row: IncidentReport) => row.category?.name ?? '—'}
            style={{ width: '130px' }}
          />
          <Column
            header="Departemen"
            body={(row: IncidentReport) => row.originDepartment?.name ?? '—'}
            style={{ width: '150px' }}
          />
          <Column
            header="Nama Pelapor"
            body={(row: IncidentReport) => row.reporterName ?? '—'}
            style={{ width: '160px' }}
          />
          <Column header="Status" body={statusBodyTemplate} style={{ width: '160px' }} />
          <Column
            header="Result"
            body={(row: IncidentReport) => row.result?.name ? <ApprovalBadge status={row.result.name} /> : '—'}
            style={{ width: '160px' }}
          />
          <Column header="Aksi" body={actionBodyTemplate} style={{ width: '140px' }} frozen alignFrozen="right" />
        </DataTable>
      </Card>
    </div>
  );
};

export default IncidentReportList;
