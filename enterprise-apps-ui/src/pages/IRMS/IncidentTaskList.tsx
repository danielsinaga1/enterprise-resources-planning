import { useEffect, useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { DataTable, type DataTablePageEvent, type DataTableSortEvent } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { Dropdown } from 'primereact/dropdown';
import { Card } from 'primereact/card';
import { Toast } from 'primereact/toast';
import { Tag } from 'primereact/tag';
import { useTranslation } from 'react-i18next';
import { incidentReportService } from '../../services';
import { userPermissionService } from '../../services/rbacService';
import { encodeReportId } from '../../utils/reportId';
import { useAuth } from '../../context/AuthContext';
import { ApprovalBadge } from '../../components/irms/StatusBadges';
import type { IncidentReport } from '../../types/irms';

/* ============================================================
   INCIDENT TASK LIST — Tugas Laporan Insiden (Dept. Tujuan)
   Menampilkan daftar laporan insiden yang ditugaskan ke user
   sebagai manager departemen tujuan.

   Authorization Logic:
   - Status laporan harus 'Fully Approved' / 'In Progress' / 'Done'
   - Akses diatur lewat Permission Matrix (RBAC), bagian
     Tindak Lanjut: INCIDENT_FOLLOWUP_ASSIGN — satu permission
     untuk manager departemen tujuan: lihat Tugas Insiden
     sekaligus assign tugas ke member departemennya.
   - Data difilter ke designated_manager_id = user login
     (resolved via designated_manager_id auto-assignment)
   ============================================================ */

const IncidentTaskList = () => {
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);
  const { user } = useAuth();
  const { t } = useTranslation();

  // Authorization check via Permission Matrix (null = masih dicek)
  const [isAuthorized, setIsAuthorized] = useState<boolean | null>(null);

  useEffect(() => {
    if (!user?.id) return;
    if (user.roles?.includes('SUPER_ADMIN')) {
      setIsAuthorized(true);
      return;
    }
    userPermissionService
      .check(user.id, 'INCIDENT_FOLLOWUP_ASSIGN', 'read')
      .then(setIsAuthorized)
      .catch(() => setIsAuthorized(false));
  }, [user?.id, user?.roles]);

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
  const [statusFilter, setStatusFilter] = useState<string | null>(null);

  const statusOptions = [
    { label: t('incidentTask.allStatus'), value: null },
    { label: t('incidentTask.statusFullyApproved'), value: 'Fully Approved' },
    { label: t('incidentTask.statusInProgress'), value: 'In Progress' },
    { label: t('incidentTask.statusDone'), value: 'Done' },
  ];

  // Load data — filtered by designated_manager_id = current user
  // Backend auto-assigns designatedManagerId based on designation department's manager
  const loadData = useCallback(async () => {
    if (!user?.id || isAuthorized !== true) {
      setLoading(false);
      return;
    }
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
          designated_manager_id: user.id,
          status: statusFilter ?? undefined,
        }
      );
      // Client-side: only show Fully Approved / In Progress / Done
      const taskStatuses = ['Fully Approved', 'In Progress', 'Done'];
      const filtered = result.data.filter((r) => taskStatuses.includes(r.status));
      setData(filtered);
      setTotalRecords(result.total);
    } catch (err) {
      console.error('Failed to load incident tasks', err);
      toast.current?.show({
        severity: 'error',
        summary: t('common.error'),
        detail: t('incidentTask.loadError'),
        life: 3000,
      });
    } finally {
      setLoading(false);
    }
  }, [user?.id, isAuthorized, page, rows, sortField, sortOrder, statusFilter, t]);

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

  const priorityBodyTemplate = (row: IncidentReport) => {
    if (!row.riskLevel) return <span className="text-400">—</span>;
    const severityMap: Record<string, 'danger' | 'warning' | 'info' | 'success'> = {
      'Extreme': 'danger',
      'High': 'danger',
      'Medium': 'warning',
      'Low': 'info',
      'Minimal': 'success',
    };
    return <Tag value={row.riskLevel} severity={severityMap[row.riskLevel] ?? 'info'} className="text-xs" />;
  };

  const actionLabelTemplate = (row: IncidentReport) => {
    switch (row.status) {
      case 'Fully Approved':
        return (
          <Tag
            value={t('incidentTask.needsAssignment')}
            severity="warning"
            icon="pi pi-exclamation-triangle"
            className="text-xs"
          />
        );
      case 'In Progress':
        return (
          <Tag
            value={t('incidentTask.inProgress')}
            severity="info"
            icon="pi pi-spin pi-spinner"
            className="text-xs"
          />
        );
      case 'Done':
        return (
          <Tag
            value={t('incidentTask.completed')}
            severity="success"
            icon="pi pi-check"
            className="text-xs"
          />
        );
      default:
        return <Tag value={row.status} className="text-xs" />;
    }
  };

  const actionBodyTemplate = (row: IncidentReport) => (
    <div className="flex gap-1">
      <Button
        icon="pi pi-eye"
        rounded
        text
        severity="info"
        size="small"
        tooltip={t('incidentTask.viewDetail')}
        tooltipOptions={{ position: 'top' }}
        onClick={() => navigate(`/dashboard/irms/incident-reports/${encodeReportId(row.id)}`)}
      />
      {row.status === 'Fully Approved' && (
        <Button
          icon="pi pi-user-plus"
          rounded
          text
          severity="warning"
          size="small"
          tooltip={t('incidentTask.assignSubordinate')}
          tooltipOptions={{ position: 'top' }}
          onClick={() => navigate(`/dashboard/irms/incident-reports/${encodeReportId(row.id)}`)}
        />
      )}
    </div>
  );

  // Client-side text search on loaded page
  const filteredData = searchText
    ? data.filter(
        (r) =>
          r.reportNumber?.toLowerCase().includes(searchText.toLowerCase()) ||
          r.title?.toLowerCase().includes(searchText.toLowerCase())
      )
    : data;

  // Count by status for summary cards
  const countFullyApproved = data.filter((r) => r.status === 'Fully Approved').length;
  const countInProgress = data.filter((r) => r.status === 'In Progress').length;
  const countDone = data.filter((r) => r.status === 'Done').length;

  // ── Checking permission ──
  if (isAuthorized === null) {
    return (
      <div className="flex align-items-center justify-content-center" style={{ minHeight: '60vh' }}>
        <i className="pi pi-spin pi-spinner text-4xl text-400" />
      </div>
    );
  }

  // ── Unauthorized View ──
  if (!isAuthorized) {
    return (
      <div className="flex flex-column align-items-center justify-content-center gap-4" style={{ minHeight: '60vh' }}>
        <div className="text-center">
          <i className="pi pi-lock text-5xl text-red-400 mb-3" />
          <h2 className="text-xl font-bold text-800 m-0 mt-3">{t('incidentTask.unauthorizedTitle')}</h2>
          <p className="text-sm text-500 mt-2 mb-0" style={{ maxWidth: 480 }}>
            {t('incidentTask.unauthorizedMessage')}
          </p>
        </div>
        <Button
          label={t('incidentTask.unauthorizedBack')}
          icon="pi pi-arrow-left"
          severity="secondary"
          outlined
          onClick={() => navigate('/dashboard')}
        />
      </div>
    );
  }

  return (
    <div className="flex flex-column gap-4">
      <Toast ref={toast} />

      {/* Header */}
      <div className="flex align-items-center justify-content-between flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold text-800 m-0">
            <i className="pi pi-briefcase mr-2 text-blue-500" />
            {t('incidentTask.title')}
          </h2>
          <p className="text-sm text-500 mt-1 mb-0">
            {t('incidentTask.subtitle')}
          </p>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid">
        <div className="col-12 md:col-4">
          <Card className="shadow-1 border-round-xl border-left-3 border-orange-400">
            <div className="flex align-items-center gap-3">
              <div className="flex align-items-center justify-content-center border-round-lg bg-orange-50" style={{ width: 48, height: 48 }}>
                <i className="pi pi-exclamation-triangle text-orange-500 text-xl" />
              </div>
              <div>
                <p className="text-xs text-500 m-0 mb-1">{t('incidentTask.needsAssignment')}</p>
                <p className="text-2xl font-bold text-800 m-0">{countFullyApproved}</p>
              </div>
            </div>
          </Card>
        </div>
        <div className="col-12 md:col-4">
          <Card className="shadow-1 border-round-xl border-left-3 border-blue-400">
            <div className="flex align-items-center gap-3">
              <div className="flex align-items-center justify-content-center border-round-lg bg-blue-50" style={{ width: 48, height: 48 }}>
                <i className="pi pi-spin pi-cog text-blue-500 text-xl" />
              </div>
              <div>
                <p className="text-xs text-500 m-0 mb-1">{t('incidentTask.inProgress')}</p>
                <p className="text-2xl font-bold text-800 m-0">{countInProgress}</p>
              </div>
            </div>
          </Card>
        </div>
        <div className="col-12 md:col-4">
          <Card className="shadow-1 border-round-xl border-left-3 border-green-400">
            <div className="flex align-items-center gap-3">
              <div className="flex align-items-center justify-content-center border-round-lg bg-green-50" style={{ width: 48, height: 48 }}>
                <i className="pi pi-check-circle text-green-500 text-xl" />
              </div>
              <div>
                <p className="text-xs text-500 m-0 mb-1">{t('incidentTask.completed')}</p>
                <p className="text-2xl font-bold text-800 m-0">{countDone}</p>
              </div>
            </div>
          </Card>
        </div>
      </div>

      {/* Filters */}
      <Card className="shadow-1 border-round-xl">
        <div className="flex flex-wrap gap-3 align-items-end">
          <div className="flex flex-column gap-1">
            <label className="text-xs font-medium text-500">{t('incidentTask.search')}</label>
            <span className="p-input-icon-left">
              <i className="pi pi-search" />
              <InputText
                value={searchText}
                onChange={(e) => setSearchText(e.target.value)}
                placeholder={t('incidentTask.searchPlaceholder')}
                className="w-20rem"
              />
            </span>
          </div>
          <div className="flex flex-column gap-1">
            <label className="text-xs font-medium text-500">{t('incidentTask.statusFilter')}</label>
            <Dropdown
              value={statusFilter}
              options={statusOptions}
              onChange={(e) => {
                setStatusFilter(e.value);
                setPage(1);
              }}
              className="w-14rem"
            />
          </div>
          <Button
            label={t('common.reset')}
            icon="pi pi-filter-slash"
            severity="secondary"
            outlined
            size="small"
            onClick={() => {
              setSearchText('');
              setStatusFilter(null);
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
          emptyMessage={t('incidentTask.emptyMessage')}
          rowsPerPageOptions={[5, 10, 25, 50]}
          paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
          className="border-round-lg"
        >
          <Column
            field="reportNumber"
            header={t('incidentTask.reportNumber')}
            sortable
            className="font-medium"
            style={{ width: '160px' }}
          />
          <Column field="title" header={t('incidentTask.titleColumn')} sortable />
          <Column
            header={t('incidentTask.incidentDate')}
            body={dateBodyTemplate}
            sortable
            sortField="incidentDate"
            style={{ width: '130px' }}
          />
          <Column
            header={t('incidentTask.category')}
            body={(row: IncidentReport) => row.category?.name ?? '—'}
            style={{ width: '130px' }}
          />
          <Column
            header={t('incidentTask.originDept')}
            body={(row: IncidentReport) => row.originDepartment?.name ?? '—'}
            style={{ width: '150px' }}
          />
          <Column
            header={t('incidentTask.risk')}
            body={priorityBodyTemplate}
            style={{ width: '100px' }}
          />
          <Column
            header={t('incidentTask.status')}
            body={statusBodyTemplate}
            style={{ width: '150px' }}
          />
          <Column
            header={t('incidentTask.action')}
            body={actionLabelTemplate}
            style={{ width: '160px' }}
          />
          <Column
            header={t('incidentTask.actions')}
            body={actionBodyTemplate}
            style={{ width: '110px' }}
            frozen
            alignFrozen="right"
          />
        </DataTable>
      </Card>
    </div>
  );
};

export default IncidentTaskList;
