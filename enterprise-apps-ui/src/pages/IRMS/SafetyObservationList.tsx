import { useEffect, useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { DataTable, type DataTablePageEvent, type DataTableSortEvent } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { Dropdown } from 'primereact/dropdown';
import { InputText } from 'primereact/inputtext';
import { Card } from 'primereact/card';
import { TabView, TabPanel } from 'primereact/tabview';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { Toast } from 'primereact/toast';
import { safetyObservationService, userRoleService } from '../../services';
import { ApprovalBadge, ObservationTypeBadge } from '../../components/irms/StatusBadges';
import ExportButtons, { type GroupedExportPayload } from '../../components/common/ExportButtons';
import type { ExportColumn, ExportGroup, ChartSpec, SummaryItem } from '../../utils/exportUtils';
import type { SafetyObservation, SafetyObservationFilters } from '../../types/irms';

/* ============================================================
   SAFETY OBSERVATION LIST PAGE (NCR + JSO)
   Maps from kpi_irms SafetyObservationController@index
   Tab-based filter: All / NCR / JSO
   ============================================================ */
/** Label status yang dipakai dropdown filter sekaligus ekspor. */
const STATUS_LABELS: Record<string, string> = {
  open: 'Terbuka',
  reviewed: 'Ditinjau',
  in_progress: 'Dalam Proses',
  action_taken: 'Tindakan Diambil',
  closed: 'Ditutup',
  returned: 'Dikembalikan',
  overdue: 'Overdue',
  rejected: 'Ditolak',
};

const SafetyObservationList = () => {
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);

  // Tab state
  const [activeTab, setActiveTab] = useState(0);
  const typeMap: Array<SafetyObservationFilters['type']> = ['all', 'ncr', 'jso'];
  // Kolom tim hanya relevan di tab JSO — NCR tidak punya leader/member sama sekali,
  // jadi tab Semua & NCR tetap memakai Dept. Asal seperti sebelumnya.
  const isJsoTab = activeTab === 2;

  // Pagination
  const [data, setData] = useState<SafetyObservation[]>([]);
  const [totalRecords, setTotalRecords] = useState(0);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState(10);
  const [sortField, setSortField] = useState<string>('createdAt');
  const [sortOrder, setSortOrder] = useState<1 | -1 | 0>(-1);

  // id -> nama, untuk menampilkan leader & member JSO (API list hanya mengirim id-nya)
  const [userMap, setUserMap] = useState<Map<number, string>>(new Map());

  // Filters
  const [searchText, setSearchText] = useState('');
  // Nilai yang benar-benar dikirim ke server. Ditahan sejenak supaya mengetik
  // "SO-2506-001" tidak menembakkan 11 request berturut-turut.
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string | null>(null);
  const [yearFilter, setYearFilter] = useState<number | null>(null);

  const currentYear = new Date().getFullYear();
  const yearOptions = [
    { label: 'Semua Tahun', value: null },
    ...Array.from({ length: currentYear - 2019 + 1 }, (_, i) => currentYear - i).map((y) => ({ label: String(y), value: y })),
  ];

  const statusOptions = [
    { label: 'Semua Status', value: null as string | null },
    ...Object.entries(STATUS_LABELS).map(([value, label]) => ({ label, value })),
  ];

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await safetyObservationService.list(
        {
          page,
          limit: rows,
          sortBy: sortField,
          sortOrder: sortOrder === 1 ? 'asc' : 'desc',
        },
        {
          type: typeMap[activeTab],
          status: statusFilter ?? undefined,
          year: yearFilter ?? undefined,
          search: searchQuery || undefined,
        }
      );
      setData(result.data);
      setTotalRecords(result.total);
    } catch (err) {
      console.error('Failed to load safety observations', err);
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Gagal memuat data observasi keselamatan',
        life: 3000,
      });
    } finally {
      setLoading(false);
    }
  }, [page, rows, sortField, sortOrder, activeTab, statusFilter, yearFilter, searchQuery]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    const t = setTimeout(() => {
      setSearchQuery(searchText.trim());
      setPage(1); // hasil pencarian selalu mulai dari halaman pertama
    }, 350);
    return () => clearTimeout(t);
  }, [searchText]);

  useEffect(() => {
    userRoleService
      .listUsers({ limit: 9999 })
      .then((resp) => setUserMap(new Map((resp.data ?? []).map((u) => [u.id, u.name]))))
      .catch(console.error);
  }, []);

  const onPage = (e: DataTablePageEvent) => {
    setPage((e.page ?? 0) + 1);
    setRows(e.rows);
  };

  const onSort = (e: DataTableSortEvent) => {
    setSortField(e.sortField as string);
    setSortOrder(e.sortOrder as 1 | -1 | 0);
    setPage(1);
  };

  const onTabChange = (idx: number) => {
    setActiveTab(idx);
    setPage(1);
  };

  const handleDelete = (obs: SafetyObservation) => {
    confirmDialog({
      message: `Hapus observasi "${obs.reportNumber}"?`,
      header: 'Konfirmasi Hapus',
      icon: 'pi pi-exclamation-triangle',
      acceptClassName: 'p-button-danger',
      acceptLabel: 'Hapus',
      rejectLabel: 'Batal',
      accept: async () => {
        try {
          await safetyObservationService.delete(obs.id);
          toast.current?.show({
            severity: 'success',
            summary: 'Berhasil',
            detail: 'Observasi berhasil dihapus',
            life: 3000,
          });
          loadData();
        } catch {
          toast.current?.show({
            severity: 'error',
            summary: 'Error',
            detail: 'Gagal menghapus observasi',
            life: 3000,
          });
        }
      },
    });
  };

  // Templates
  const typeBodyTemplate = (row: SafetyObservation) => <ObservationTypeBadge isJso={row.isJso} />;
  const statusBodyTemplate = (row: SafetyObservation) => <ApprovalBadge status={row.status} />;

  const dateBodyTemplate = (row: SafetyObservation) => {
    const dateVal = row.observationDateDisplay ?? row.observationDate;
    return (
      <span className="text-sm">
        {dateVal
          ? new Date(dateVal).toLocaleDateString('id-ID', {
              day: '2-digit',
              month: 'short',
              year: 'numeric',
            })
          : '—'}
      </span>
    );
  };

  const userName = (id?: number) => (id ? userMap.get(id) ?? `User #${id}` : null);

  const jsoTeamBodyTemplate = (row: SafetyObservation) => {
    const leader = userName(row.leaderId);
    const members = [userName(row.member1Id), userName(row.member2Id)].filter(Boolean);
    if (!leader && members.length === 0) return <span className="text-sm text-500">—</span>;
    return (
      <div className="flex flex-column gap-1 text-sm">
        {leader && (
          <span>
            <span className="text-500">Leader: </span>
            <span className="font-medium">{leader}</span>
          </span>
        )}
        {members.length > 0 && (
          <span className="text-600">
            <span className="text-500">Member: </span>
            {members.join(', ')}
          </span>
        )}
      </div>
    );
  };

  const actionBodyTemplate = (row: SafetyObservation) => (
    <div className="flex gap-1">
      <Button
        icon="pi pi-eye"
        rounded
        text
        severity="info"
        size="small"
        tooltip="Lihat Detail"
        tooltipOptions={{ position: 'top' }}
        onClick={() => navigate(`/dashboard/irms/safety-observations/${row.id}`)}
      />
      <Button
        icon="pi pi-pencil"
        rounded
        text
        severity="warning"
        size="small"
        tooltip="Edit"
        tooltipOptions={{ position: 'top' }}
        onClick={() => navigate(`/dashboard/irms/safety-observations/${row.id}/edit`)}
      />
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
    </div>
  );

  // Tidak ada penyaringan di sisi klien: memfilter `data` berarti hanya menyaring
  // 10 baris halaman yang sedang tampil, sehingga nomor laporan yang berada di
  // halaman lain tampak "tidak ditemukan". Pencarian dikerjakan server.

  // ── Konfigurasi ekspor ──
  // JSO dan NCR keluar sebagai kelompok terpisah karena kolomnya memang beda:
  // JSO punya leader/member, NCR punya dept. asal. Digabung dalam satu tabel,
  // separuh kolom selalu kosong dan laporannya jadi sulit dibaca.
  const BASE_COLUMNS: ExportColumn[] = [
    { header: 'No. Laporan', field: 'reportNumber' },
    { header: 'Judul', field: 'title' },
    {
      header: 'Tanggal',
      field: 'observationDateDisplay',
      formatter: (v, row) => {
        // Tanggal JSO ada di kolomnya sendiri; pakai nilai efektif seperti di tabel.
        const raw = (v as string) ?? (row.observationDate as string) ?? (row.observationDateJso as string);
        return raw ? new Date(raw).toLocaleDateString('id-ID') : '-';
      },
    },
    { header: 'Status', field: 'status', formatter: (v) => STATUS_LABELS[v as string] ?? ((v as string) || '-') },
  ];

  const JSO_COLUMNS: ExportColumn[] = [
    ...BASE_COLUMNS,
    { header: 'Leader JSO', field: 'leaderId', formatter: (v) => userName(v as number | undefined) ?? '-' },
    {
      header: 'Member JSO',
      field: 'member1Id',
      formatter: (_v, row) =>
        [userName(row.member1Id as number | undefined), userName(row.member2Id as number | undefined)]
          .filter(Boolean)
          .join(', ') || '-',
    },
  ];

  const NCR_COLUMNS: ExportColumn[] = [
    ...BASE_COLUMNS,
    { header: 'Dept. Asal', field: 'originDepartment.name', formatter: (v) => (v as string) ?? '-' },
  ];

  /** Tanggal efektif satu observasi (JSO menyimpannya di kolom terpisah). */
  const effectiveDate = (r: SafetyObservation): Date | null => {
    const raw = r.observationDateDisplay ?? r.observationDate ?? r.observationDateJso;
    if (!raw) return null;
    const d = new Date(raw);
    return Number.isNaN(d.getTime()) ? null : d;
  };

  const fetchExportGroups = async (): Promise<GroupedExportPayload> => {
    // Ekspor mengikuti filter yang sedang aktif, tapi selalu mengambil kedua tipe
    // supaya laporannya utuh; pemisahannya dilakukan di sini.
    const result = await safetyObservationService.list(
      { page: 1, limit: 10000, sortBy: sortField, sortOrder: sortOrder === 1 ? 'asc' : 'desc' },
      {
        type: typeMap[activeTab],
        status: statusFilter ?? undefined,
        year: yearFilter ?? undefined,
        search: searchQuery || undefined,
      },
    );
    const all = result.data;
    const jso = all.filter((r) => r.isJso);
    const ncr = all.filter((r) => !r.isJso);

    const groups: ExportGroup[] = [];
    // Tab JSO/NCR hanya memuat satu tipe — tidak perlu bagian kosong di sana.
    if (activeTab !== 1) groups.push({ name: 'Laporan JSO', columns: JSO_COLUMNS, rows: jso as unknown as Record<string, unknown>[] });
    if (activeTab !== 2) groups.push({ name: 'Laporan NCR', columns: NCR_COLUMNS, rows: ncr as unknown as Record<string, unknown>[] });

    // ---- Chart ----
    const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'];
    const monthCounts = new Array(12).fill(0) as number[];
    for (const r of all) {
      const d = effectiveDate(r);
      if (d) monthCounts[d.getMonth()] += 1;
    }

    const statusCounts = new Map<string, number>();
    for (const r of all) {
      const key = STATUS_LABELS[r.status] ?? r.status ?? '-';
      statusCounts.set(key, (statusCounts.get(key) ?? 0) + 1);
    }

    const charts: ChartSpec[] = [
      { title: 'Observasi per Bulan', points: MONTHS.map((m, i) => ({ label: m, value: monthCounts[i] })) },
      {
        title: 'Observasi per Status',
        points: [...statusCounts.entries()]
          .sort((a, b) => b[1] - a[1])
          .map(([label, value]) => ({ label, value })),
      },
    ];

    const summary: SummaryItem[] = [
      { label: 'Total Observasi', value: all.length },
      { label: 'JSO', value: jso.length },
      { label: 'NCR', value: ncr.length },
      { label: 'Ditutup', value: all.filter((r) => r.status === 'closed').length },
      { label: 'Belum Ditutup', value: all.filter((r) => r.status !== 'closed').length },
    ];

    const filterNotes = [
      yearFilter ? `Tahun ${yearFilter}` : 'Semua tahun',
      statusFilter ? `Status ${STATUS_LABELS[statusFilter] ?? statusFilter}` : 'Semua status',
      searchQuery ? `Pencarian "${searchQuery}"` : null,
    ].filter(Boolean);

    return { groups, charts, summary, subtitle: `Filter: ${filterNotes.join(' · ')}` };
  };

  // Shared DataTable
  const renderTable = () => (
    <DataTable
      value={data}
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
      emptyMessage="Tidak ada data observasi keselamatan"
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
      <Column header="Tipe" body={typeBodyTemplate} style={{ width: '80px' }} />
      <Column field="title" header="Judul" sortable />
      <Column header="Tanggal" body={dateBodyTemplate} sortable sortField="observationDate" style={{ width: '130px' }} />
      {isJsoTab ? (
        <Column header="Tim JSO" body={jsoTeamBodyTemplate} style={{ width: '230px' }} />
      ) : (
        <Column
          header="Dept. Asal"
          body={(row: SafetyObservation) => row.originDepartment?.name ?? '—'}
          style={{ width: '150px' }}
        />
      )}
      <Column header="Status" body={statusBodyTemplate} style={{ width: '120px' }} />
      <Column header="Aksi" body={actionBodyTemplate} style={{ width: '140px' }} frozen alignFrozen="right" />
    </DataTable>
  );

  return (
    <div className="flex gap-4 flex-column">
      <Toast ref={toast} />
      <ConfirmDialog />

      {/* Header */}
      <div className="flex flex-wrap gap-3 align-items-center justify-content-between">
        <div>
          <h2 className="m-0 text-2xl font-bold text-800">Observasi Keselamatan</h2>
          <p className="mt-1 mb-0 text-sm text-500">Kelola laporan NCR & JSO</p>
        </div>
        <div className="flex gap-2">
          <ExportButtons
            permissionCode="SAFETY_OBSERVATION_MANAGE"
            fetchGroups={fetchExportGroups}
            fileName="Observasi_Keselamatan"
            title="Observasi Keselamatan"
            toastRef={toast}
          />
          <Button
            label="Buat Observasi"
            icon="pi pi-plus"
            severity="info"
            outlined
            className="border-round-lg shadow-1 font-semibold"
            style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem', border: '2px solid #3B82F6' }}
            onClick={() => navigate('/dashboard/irms/safety-observations/create')}
          />
        </div>
      </div>

      {/* Filters */}
      <Card className="shadow-1 border-round-xl">
        <div className="flex flex-wrap gap-3 align-items-end">
          <div className="flex gap-1 flex-column">
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
          <div className="flex gap-1 flex-column">
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
          <div className="flex gap-1 flex-column">
            <label className="text-xs font-medium text-500">Tahun</label>
            <Dropdown
              value={yearFilter}
              options={yearOptions}
              onChange={(e) => {
                setYearFilter(e.value);
                setPage(1);
              }}
              className="w-9rem"
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
              setYearFilter(null);
              setPage(1);
            }}
          />
        </div>
      </Card>

      {/* Tabbed DataTable */}
      <Card className="shadow-1 border-round-xl">
        <TabView activeIndex={activeTab} onTabChange={(e) => onTabChange(e.index)}>
          <TabPanel header="Semua" leftIcon="pi pi-list mr-2">
            {renderTable()}
          </TabPanel>
          <TabPanel header="NCR" leftIcon="pi pi-exclamation-triangle mr-2">
            {renderTable()}
          </TabPanel>
          <TabPanel header="JSO" leftIcon="pi pi-eye mr-2">
            {renderTable()}
          </TabPanel>
        </TabView>
      </Card>
    </div>
  );
};

export default SafetyObservationList;
