import { useEffect, useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { DataTable, type DataTablePageEvent, type DataTableSortEvent } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { Dropdown } from 'primereact/dropdown';
import { InputText } from 'primereact/inputtext';
import { Card } from 'primereact/card';
import { Dialog } from 'primereact/dialog';
import { InputTextarea } from 'primereact/inputtextarea';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { Toast } from 'primereact/toast';
import { Tag } from 'primereact/tag';
import { observationTaskService } from '../../services';
import { userRoleService, authService } from '../../services';
import ExportButtons from '../../components/common/ExportButtons';
import type { ExportColumn } from '../../utils/exportUtils';
import type { ObservationTask, ObservationTaskStats } from '../../types/irms';

/* ============================================================
   OBSERVATION TASK LIST PAGE — Tugas Observasi
   ============================================================ */
const ObservationTaskList = () => {
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);

  // Pagination
  const [data, setData] = useState<ObservationTask[]>([]);
  const [totalRecords, setTotalRecords] = useState(0);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState(10);
  const [sortField, setSortField] = useState<string>('createdAt');
  const [sortOrder, setSortOrder] = useState<1 | -1 | 0>(-1);

  // Filters
  const [searchText, setSearchText] = useState('');
  const [statusFilter, setStatusFilter] = useState<string | null>(null);

  // Stats
  const [stats, setStats] = useState<ObservationTaskStats | null>(null);

  // Complete / Verify dialog
  const [completeDialog, setCompleteDialog] = useState(false);
  const [verifyDialog, setVerifyDialog] = useState(false);
  const [selectedTask, setSelectedTask] = useState<ObservationTask | null>(null);
  const [completionNotes, setCompletionNotes] = useState('');
  const [verificationNotes, setVerificationNotes] = useState('');

  // User map
  const [userMap, setUserMap] = useState<Record<number, string>>({});

  const statusOptions = [
    { label: 'Semua Status', value: null },
    { label: 'Terbuka', value: 'open' },
    { label: 'Dalam Proses', value: 'in_progress' },
    { label: 'Selesai', value: 'completed' },
    { label: 'Overdue', value: 'overdue' },
    { label: 'Dibatalkan', value: 'cancelled' },
  ];

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await observationTaskService.list(
        {
          page,
          limit: rows,
          sortBy: sortField,
          sortOrder: sortOrder === 1 ? 'asc' : 'desc',
        },
        { status: statusFilter ?? undefined }
      );
      setData(result.data);
      setTotalRecords(result.total);
    } catch (err) {
      console.error('Failed to load observation tasks', err);
      toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal memuat data tugas observasi', life: 3000 });
    } finally {
      setLoading(false);
    }
  }, [page, rows, sortField, sortOrder, statusFilter]);

  const loadStats = useCallback(async () => {
    try {
      const s = await observationTaskService.getStats();
      setStats(s);
    } catch { /* ignore */ }
  }, []);

  const loadUsers = useCallback(async () => {
    try {
      const res = await userRoleService.listUsers({ limit: 9999 });
      const map: Record<number, string> = {};
      const users = Array.isArray(res) ? res : (res as { data?: unknown[] }).data ? (res as { data: { id: number; fullName?: string; username?: string }[] }).data : [];
      (users as { id: number; fullName?: string; username?: string }[]).forEach((u) => {
        map[u.id] = u.fullName || u.username || `User #${u.id}`;
      });
      setUserMap(map);
    } catch { /* ignore */ }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    loadStats();
    loadUsers();
  }, [loadStats, loadUsers]);

  const onPage = (e: DataTablePageEvent) => {
    setPage((e.page ?? 0) + 1);
    setRows(e.rows);
  };

  const onSort = (e: DataTableSortEvent) => {
    setSortField(e.sortField as string);
    setSortOrder(e.sortOrder as 1 | -1 | 0);
    setPage(1);
  };

  // Complete task
  const openCompleteDialog = (task: ObservationTask) => {
    setSelectedTask(task);
    setCompletionNotes('');
    setCompleteDialog(true);
  };

  const handleComplete = async () => {
    if (!selectedTask) return;
    try {
      const userData = authService.getUser();
      await observationTaskService.complete(selectedTask.id, {
        completionNotes,
        completedById: userData?.id,
      });
      toast.current?.show({ severity: 'success', summary: 'Berhasil', detail: 'Tugas berhasil diselesaikan', life: 3000 });
      setCompleteDialog(false);
      loadData();
      loadStats();
    } catch {
      toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal menyelesaikan tugas', life: 3000 });
    }
  };

  // Verify task
  const openVerifyDialog = (task: ObservationTask) => {
    setSelectedTask(task);
    setVerificationNotes('');
    setVerifyDialog(true);
  };

  const handleVerify = async () => {
    if (!selectedTask) return;
    try {
      const userData = authService.getUser();
      await observationTaskService.verify(selectedTask.id, {
        verifiedById: userData?.id,
        verificationNotes,
      });
      toast.current?.show({ severity: 'success', summary: 'Berhasil', detail: 'Tugas berhasil diverifikasi', life: 3000 });
      setVerifyDialog(false);
      loadData();
      loadStats();
    } catch {
      toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal memverifikasi tugas', life: 3000 });
    }
  };

  // Delete
  const handleDelete = (task: ObservationTask) => {
    confirmDialog({
      message: `Hapus tugas "${task.taskNumber}"?`,
      header: 'Konfirmasi Hapus',
      icon: 'pi pi-exclamation-triangle',
      acceptClassName: 'p-button-danger',
      acceptLabel: 'Hapus',
      rejectLabel: 'Batal',
      accept: async () => {
        try {
          await observationTaskService.delete(task.id);
          toast.current?.show({ severity: 'success', summary: 'Berhasil', detail: 'Tugas berhasil dihapus', life: 3000 });
          loadData();
          loadStats();
        } catch {
          toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal menghapus tugas', life: 3000 });
        }
      },
    });
  };

  // Helpers
  const getUserName = (id?: number) => (id ? userMap[id] || `User #${id}` : '—');

  const getStatusTag = (status: string) => {
    const map: Record<string, { severity: 'success' | 'info' | 'warning' | 'danger' | 'secondary'; label: string }> = {
      open: { severity: 'info', label: 'Terbuka' },
      in_progress: { severity: 'warning', label: 'Dalam Proses' },
      completed: { severity: 'success', label: 'Selesai' },
      overdue: { severity: 'danger', label: 'Overdue' },
      cancelled: { severity: 'secondary', label: 'Dibatalkan' },
    };
    const s = map[status] || { severity: 'info' as const, label: status };
    return <Tag severity={s.severity} value={s.label} rounded />;
  };

  const getPriorityTag = (priority: string) => {
    const map: Record<string, { severity: 'success' | 'info' | 'warning' | 'danger'; label: string }> = {
      low: { severity: 'success', label: 'Rendah' },
      medium: { severity: 'info', label: 'Sedang' },
      high: { severity: 'warning', label: 'Tinggi' },
      critical: { severity: 'danger', label: 'Kritis' },
    };
    const p = map[priority] || { severity: 'info' as const, label: priority };
    return <Tag severity={p.severity} value={p.label} rounded />;
  };

  const formatDate = (d?: string) =>
    d ? new Date(d).toLocaleDateString('id-ID', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

  // Templates
  const statusBodyTemplate = (row: ObservationTask) => getStatusTag(row.status);
  const priorityBodyTemplate = (row: ObservationTask) => getPriorityTag(row.priority);
  const dueDateTemplate = (row: ObservationTask) => <span className="text-sm">{formatDate(row.dueDate)}</span>;
  const assignedTemplate = (row: ObservationTask) => <span className="text-sm">{getUserName(row.assignedToId)}</span>;
  const observationTemplate = (row: ObservationTask) => (
    <Button
      label={row.safetyObservation?.reportNumber || '—'}
      className="p-button-text p-button-sm p-0"
      onClick={() => row.safetyObservation && navigate(`/dashboard/irms/safety-observations/${row.safetyObservation.id}`)}
    />
  );

  const actionBodyTemplate = (row: ObservationTask) => (
    <div className="flex gap-1">
      <Button
        icon="pi pi-eye"
        rounded text severity="info" size="small"
        tooltip="Lihat Detail" tooltipOptions={{ position: 'top' }}
        onClick={() => navigate(`/dashboard/irms/observation-tasks/${row.id}`)}
      />
      {(row.status === 'open' || row.status === 'in_progress') && (
        <Button
          icon="pi pi-check"
          rounded text severity="success" size="small"
          tooltip="Selesaikan" tooltipOptions={{ position: 'top' }}
          onClick={() => openCompleteDialog(row)}
        />
      )}
      {row.status === 'completed' && !row.verifiedAt && (
        <Button
          icon="pi pi-verified"
          rounded text severity="warning" size="small"
          tooltip="Verifikasi" tooltipOptions={{ position: 'top' }}
          onClick={() => openVerifyDialog(row)}
        />
      )}
      <Button
        icon="pi pi-trash"
        rounded text severity="danger" size="small"
        tooltip="Hapus" tooltipOptions={{ position: 'top' }}
        onClick={() => handleDelete(row)}
      />
    </div>
  );

  const filteredData = searchText
    ? data.filter(
        (r) =>
          r.taskNumber?.toLowerCase().includes(searchText.toLowerCase()) ||
          r.title?.toLowerCase().includes(searchText.toLowerCase()) ||
          r.safetyObservation?.reportNumber?.toLowerCase().includes(searchText.toLowerCase())
      )
    : data;

  // Export configuration
  const exportColumns: ExportColumn[] = [
    { header: 'No. Tugas', field: 'taskNumber' },
    { header: 'Judul', field: 'title' },
    { header: 'Prioritas', field: 'priority' },
    { header: 'Tenggat', field: 'dueDate', formatter: (v) => v ? new Date(v as string).toLocaleDateString('id-ID') : '-' },
    { header: 'No. Observasi', field: 'safetyObservation.reportNumber', formatter: (v) => (v as string) ?? '-' },
    { header: 'Status', field: 'status' },
  ];

  const fetchExportData = async (): Promise<Record<string, unknown>[]> => {
    const result = await observationTaskService.list(
      { page: 1, limit: 10000, sortBy: sortField, sortOrder: sortOrder === 1 ? 'asc' : 'desc' },
      { status: statusFilter ?? undefined }
    );
    return result.data as unknown as Record<string, unknown>[];
  };

  return (
    <div className="flex flex-column gap-4">
      <Toast ref={toast} />
      <ConfirmDialog />

      {/* Header */}
      <div className="flex align-items-center justify-content-between flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold text-800 m-0">Tugas Observasi</h2>
          <p className="text-sm text-500 mt-1 mb-0">Kelola tugas tindak lanjut laporan JSO & NCR</p>
        </div>
        <ExportButtons
          permissionCode="SAFETY_OBSERVATION_MANAGE"
          columns={exportColumns}
          fetchData={fetchExportData}
          fileName="Tugas_Observasi"
          title="Tugas Observasi"
          toastRef={toast}
        />
      </div>

      {/* Stats Cards */}
      {stats && (
        <div className="grid">
          <div className="col-6 md:col-3 lg:col-2">
            <Card className="shadow-1 border-round-xl text-center">
              <div className="text-2xl font-bold text-primary">{stats.total}</div>
              <div className="text-xs text-500 mt-1">Total</div>
            </Card>
          </div>
          <div className="col-6 md:col-3 lg:col-2">
            <Card className="shadow-1 border-round-xl text-center">
              <div className="text-2xl font-bold" style={{ color: '#2196F3' }}>{stats.open}</div>
              <div className="text-xs text-500 mt-1">Terbuka</div>
            </Card>
          </div>
          <div className="col-6 md:col-3 lg:col-2">
            <Card className="shadow-1 border-round-xl text-center">
              <div className="text-2xl font-bold" style={{ color: '#FFC107' }}>{stats.inProgress}</div>
              <div className="text-xs text-500 mt-1">Dalam Proses</div>
            </Card>
          </div>
          <div className="col-6 md:col-3 lg:col-2">
            <Card className="shadow-1 border-round-xl text-center">
              <div className="text-2xl font-bold" style={{ color: '#4CAF50' }}>{stats.completed}</div>
              <div className="text-xs text-500 mt-1">Selesai</div>
            </Card>
          </div>
          <div className="col-6 md:col-3 lg:col-2">
            <Card className="shadow-1 border-round-xl text-center">
              <div className="text-2xl font-bold" style={{ color: '#F44336' }}>{stats.overdue}</div>
              <div className="text-xs text-500 mt-1">Overdue</div>
            </Card>
          </div>
        </div>
      )}

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
                placeholder="No. Tugas / Judul / No. Observasi"
                style={{ width: '280px' }}
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
              placeholder="Semua Status"
              style={{ width: '180px' }}
            />
          </div>
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
          emptyMessage="Tidak ada data tugas observasi"
          rowsPerPageOptions={[5, 10, 25, 50]}
          paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
          className="border-round-lg"
        >
          <Column field="taskNumber" header="No. Tugas" sortable className="font-medium" style={{ width: '150px' }} />
          <Column header="Observasi" body={observationTemplate} style={{ width: '160px' }} />
          <Column field="title" header="Judul" sortable />
          <Column header="Ditugaskan Ke" body={assignedTemplate} style={{ width: '150px' }} />
          <Column header="Prioritas" body={priorityBodyTemplate} style={{ width: '100px' }} />
          <Column header="Tenggat" body={dueDateTemplate} sortable sortField="dueDate" style={{ width: '120px' }} />
          <Column header="Status" body={statusBodyTemplate} style={{ width: '120px' }} />
          <Column header="Aksi" body={actionBodyTemplate} style={{ width: '160px' }} frozen alignFrozen="right" />
        </DataTable>
      </Card>

      {/* Complete Dialog */}
      <Dialog
        header="Selesaikan Tugas"
        visible={completeDialog}
        style={{ width: '450px' }}
        onHide={() => setCompleteDialog(false)}
        footer={
          <div>
            <Button label="Batal" icon="pi pi-times" severity="secondary" outlined className="border-round-lg shadow-1 font-semibold mr-2" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} onClick={() => setCompleteDialog(false)} />
            <Button label="Selesaikan" icon="pi pi-check" severity="success" className="border-round-lg shadow-1 font-semibold" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} onClick={handleComplete} />
          </div>
        }
      >
        <p className="mb-3">Selesaikan tugas <strong>{selectedTask?.taskNumber}</strong>?</p>
        <div className="flex flex-column gap-2">
          <label className="font-medium text-sm">Catatan Penyelesaian</label>
          <InputTextarea
            value={completionNotes}
            onChange={(e) => setCompletionNotes(e.target.value)}
            rows={4}
            placeholder="Jelaskan tindakan yang telah dilakukan..."
          />
        </div>
      </Dialog>

      {/* Verify Dialog */}
      <Dialog
        header="Verifikasi Tugas"
        visible={verifyDialog}
        style={{ width: '450px' }}
        onHide={() => setVerifyDialog(false)}
        footer={
          <div>
            <Button label="Batal" icon="pi pi-times" severity="secondary" outlined className="border-round-lg shadow-1 font-semibold mr-2" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} onClick={() => setVerifyDialog(false)} />
            <Button label="Verifikasi" icon="pi pi-verified" severity="warning" className="border-round-lg shadow-1 font-semibold" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} onClick={handleVerify} />
          </div>
        }
      >
        <p className="mb-3">Verifikasi penyelesaian tugas <strong>{selectedTask?.taskNumber}</strong>?</p>
        <div className="flex flex-column gap-2">
          <label className="font-medium text-sm">Catatan Verifikasi</label>
          <InputTextarea
            value={verificationNotes}
            onChange={(e) => setVerificationNotes(e.target.value)}
            rows={4}
            placeholder="Catatan verifikasi..."
          />
        </div>
      </Dialog>
    </div>
  );
};

export default ObservationTaskList;
