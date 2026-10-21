import { useEffect, useState, useRef, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Divider } from 'primereact/divider';
import { Toast } from 'primereact/toast';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Tag } from 'primereact/tag';
import { Dialog } from 'primereact/dialog';
import { InputTextarea } from 'primereact/inputtextarea';
import { Calendar } from 'primereact/calendar';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { observationTaskService, userRoleService, authService } from '../../services';
import { toLocalDateTimeString } from '../../utils/date';
import type { ObservationTask } from '../../types/irms';

/* ============================================================
   OBSERVATION TASK DETAIL PAGE — Tugas Observasi Detail
   ============================================================ */
const ObservationTaskDetail = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);

  const [task, setTask] = useState<ObservationTask | null>(null);
  const [loading, setLoading] = useState(true);
  const [userMap, setUserMap] = useState<Map<number, string>>(new Map());

  // Complete / Verify dialog
  const [completeDialog, setCompleteDialog] = useState(false);
  const [verifyDialog, setVerifyDialog] = useState(false);
  const [completionNotes, setCompletionNotes] = useState('');
  const [verificationNotes, setVerificationNotes] = useState('');

  // Ubah tenggat
  const [dueDateDialog, setDueDateDialog] = useState(false);
  const [newDueDate, setNewDueDate] = useState<Date | null>(null);
  const [savingDueDate, setSavingDueDate] = useState(false);

  useEffect(() => {
    userRoleService.listUsers({ limit: 9999 }).then((resp) => {
      const users = resp.data ?? [];
      const map = new Map<number, string>();
      users.forEach((u) => map.set(u.id, u.name));
      setUserMap(map);
    }).catch(console.error);
  }, []);

  const loadData = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const data = await observationTaskService.detail(Number(id));
      setTask(data);
    } catch (err) {
      console.error('Failed to load observation task', err);
      toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal memuat detail tugas', life: 3000 });
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const getUserName = (uid?: number) => (uid ? (userMap.get(uid) ?? `User #${uid}`) : '—');

  const handleComplete = async () => {
    if (!task) return;
    try {
      const userData = authService.getUser();
      await observationTaskService.complete(task.id, { completionNotes, completedById: userData?.id });
      toast.current?.show({ severity: 'success', summary: 'Berhasil', detail: 'Tugas berhasil diselesaikan', life: 3000 });
      setCompleteDialog(false);
      loadData();
    } catch {
      toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal menyelesaikan tugas', life: 3000 });
    }
  };

  const handleVerify = async () => {
    if (!task) return;
    try {
      const userData = authService.getUser();
      await observationTaskService.verify(task.id, { verifiedById: userData?.id, verificationNotes });
      toast.current?.show({ severity: 'success', summary: 'Berhasil', detail: 'Tugas berhasil diverifikasi', life: 3000 });
      setVerifyDialog(false);
      loadData();
    } catch {
      toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal memverifikasi tugas', life: 3000 });
    }
  };

  const openDueDateDialog = () => {
    setNewDueDate(task?.dueDate ? new Date(task.dueDate) : null);
    setDueDateDialog(true);
  };

  const handleSaveDueDate = async () => {
    if (!task || !newDueDate) return;
    setSavingDueDate(true);
    try {
      await observationTaskService.update(task.id, { dueDate: toLocalDateTimeString(newDueDate) });
      toast.current?.show({ severity: 'success', summary: 'Berhasil', detail: 'Tenggat berhasil diperbarui', life: 3000 });
      setDueDateDialog(false);
      loadData();
    } catch {
      toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal memperbarui tenggat', life: 3000 });
    } finally {
      setSavingDueDate(false);
    }
  };

  const handleDelete = () => {
    if (!task) return;
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
          navigate('/dashboard/irms/observation-tasks');
        } catch {
          toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal menghapus tugas', life: 3000 });
        }
      },
    });
  };

  const formatDate = (d?: string) =>
    d ? new Date(d).toLocaleDateString('id-ID', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';

  const getStatusTag = (status: string) => {
    const map: Record<string, { severity: 'success' | 'info' | 'warning' | 'danger' | 'secondary'; label: string }> = {
      open: { severity: 'info', label: 'Terbuka' },
      in_progress: { severity: 'warning', label: 'Dalam Proses' },
      completed: { severity: 'success', label: 'Selesai' },
      overdue: { severity: 'danger', label: 'Overdue' },
      cancelled: { severity: 'secondary', label: 'Dibatalkan' },
    };
    const s = map[status] || { severity: 'info' as const, label: status };
    return <Tag severity={s.severity} value={s.label} rounded className="text-sm" />;
  };

  const getPriorityTag = (priority: string) => {
    const map: Record<string, { severity: 'success' | 'info' | 'warning' | 'danger'; label: string }> = {
      low: { severity: 'success', label: 'Rendah' },
      medium: { severity: 'info', label: 'Sedang' },
      high: { severity: 'warning', label: 'Tinggi' },
      critical: { severity: 'danger', label: 'Kritis' },
    };
    const p = map[priority] || { severity: 'info' as const, label: priority };
    return <Tag severity={p.severity} value={p.label} rounded className="text-sm" />;
  };

  if (loading) {
    return (
      <div className="flex align-items-center justify-content-center" style={{ minHeight: '400px' }}>
        <ProgressSpinner style={{ width: '50px', height: '50px' }} />
      </div>
    );
  }

  if (!task) {
    return (
      <div className="text-center py-8">
        <i className="pi pi-exclamation-circle text-4xl text-500 mb-3" />
        <p className="text-500">Tugas observasi tidak ditemukan</p>
        <Button label="Kembali" icon="pi pi-arrow-left" text onClick={() => navigate('/dashboard/irms/observation-tasks')} />
      </div>
    );
  }

  return (
    <div className="flex flex-column gap-4">
      <Toast ref={toast} />
      <ConfirmDialog />

      {/* Header */}
      <div className="flex align-items-center justify-content-between flex-wrap gap-3">
        <div className="flex align-items-center gap-3">
          <Button icon="pi pi-arrow-left" rounded text onClick={() => navigate('/dashboard/irms/observation-tasks')} />
          <div>
            <h2 className="text-2xl font-bold text-800 m-0">{task.taskNumber}</h2>
            <p className="text-sm text-500 mt-1 mb-0">{task.title}</p>
          </div>
        </div>
        <div className="flex gap-2">
          {(task.status === 'open' || task.status === 'in_progress') && (
            <Button label="Selesaikan" icon="pi pi-check" severity="success" size="small" onClick={() => { setCompletionNotes(''); setCompleteDialog(true); }} />
          )}
          {task.status === 'completed' && !task.verifiedAt && (
            <Button label="Verifikasi" icon="pi pi-verified" severity="warning" size="small" onClick={() => { setVerificationNotes(''); setVerifyDialog(true); }} />
          )}
          <Button label="Hapus" icon="pi pi-trash" severity="danger" size="small" outlined onClick={handleDelete} />
        </div>
      </div>

      <div className="grid">
        {/* Main Content */}
        <div className="col-12 lg:col-8">
          <Card className="shadow-1 border-round-xl">
            <div className="flex flex-column gap-3">
              <div className="flex gap-3 align-items-center">
                {getStatusTag(task.status)}
                {getPriorityTag(task.priority)}
              </div>

              <Divider className="my-1" />

              {/* Observation reference */}
              {task.safetyObservation && (
                <div className="surface-100 border-round p-3">
                  <span className="text-xs text-500 block mb-1">Laporan Observasi Terkait</span>
                  <Button
                    label={`${task.safetyObservation.reportNumber} — ${task.safetyObservation.title || ''}`}
                    className="p-button-text p-button-sm p-0"
                    onClick={() => navigate(`/dashboard/irms/safety-observations/${task.safetyObservation!.id}`)}
                  />
                </div>
              )}

              {/* Description */}
              {task.description && (
                <div>
                  <span className="text-xs text-500 block mb-1">Deskripsi</span>
                  <p className="text-sm text-800 m-0 white-space-pre-line">{task.description}</p>
                </div>
              )}

              {/* Findings */}
              {task.findings && (
                <div>
                  <span className="text-xs text-500 block mb-1">Temuan</span>
                  <p className="text-sm text-800 m-0 white-space-pre-line">{task.findings}</p>
                </div>
              )}

              <div className="grid">
                {/* Corrective Action */}
                {task.correctiveAction && (
                  <div className="col-6">
                    <span className="text-xs text-500 block mb-1">Tindakan Korektif</span>
                    <p className="text-sm text-800 m-0 white-space-pre-line">{task.correctiveAction}</p>
                  </div>
                )}

                {/* Preventive Action */}
                {task.preventiveAction && (
                  <div className="col-6">
                    <span className="text-xs text-500 block mb-1">Tindakan Preventif</span>
                    <p className="text-sm text-800 m-0 white-space-pre-line">{task.preventiveAction}</p>
                  </div>
                )}
              </div>

              {/* Completion */}
              {task.completionNotes && (
                <>
                  <Divider className="my-1" />
                  <div>
                    <span className="text-xs text-500 block mb-1">Catatan Penyelesaian</span>
                    <p className="text-sm text-800 m-0 white-space-pre-line">{task.completionNotes}</p>
                    <span className="text-xs text-500 mt-1 block">Oleh: {getUserName(task.completedById)} — {formatDate(task.completedAt)}</span>
                  </div>
                </>
              )}

              {/* Verification */}
              {task.verifiedAt && (
                <div>
                  <span className="text-xs text-500 block mb-1">Catatan Verifikasi</span>
                  <p className="text-sm text-800 m-0 white-space-pre-line">{task.verificationNotes || '—'}</p>
                  <span className="text-xs text-500 mt-1 block">Oleh: {getUserName(task.verifiedById)} — {formatDate(task.verifiedAt)}</span>
                </div>
              )}
            </div>
          </Card>
        </div>

        {/* Right: Info */}
        <div className="col-12 lg:col-4">
          <Card title="Informasi Tugas" className="shadow-1 border-round-xl mb-4">
            <div className="flex flex-column gap-3">
              <div className="flex justify-content-between">
                <span className="text-xs text-500">Ditugaskan Ke</span>
                <span className="text-xs font-medium text-700">{getUserName(task.assignedToId)}</span>
              </div>
              <div className="flex justify-content-between">
                <span className="text-xs text-500">Ditugaskan Oleh</span>
                <span className="text-xs font-medium text-700">{getUserName(task.assignedById)}</span>
              </div>
              <Divider className="my-1" />
              <div className="flex justify-content-between align-items-center">
                <span className="text-xs text-500">Tenggat Waktu</span>
                <span className="flex align-items-center gap-1">
                  <span className="text-xs font-medium text-700">{formatDate(task.dueDate)}</span>
                  {/* Tenggat menentukan kapan pengingat H-3/Hari-H/H+3 berbunyi, jadi
                      harus bisa dikoreksi tanpa lewat API. Tugas yang sudah selesai
                      atau dibatalkan tidak lagi punya tenggat yang berarti. */}
                  {task.status !== 'completed' && task.status !== 'cancelled' && (
                    <Button
                      icon="pi pi-pencil"
                      text
                      rounded
                      severity="secondary"
                      className="p-1"
                      style={{ width: '1.5rem', height: '1.5rem' }}
                      tooltip="Ubah tenggat"
                      tooltipOptions={{ position: 'left' }}
                      onClick={openDueDateDialog}
                    />
                  )}
                </span>
              </div>
              <div className="flex justify-content-between">
                <span className="text-xs text-500">Dibuat</span>
                <span className="text-xs font-medium text-700">{formatDate(task.createdAt)}</span>
              </div>
              <div className="flex justify-content-between">
                <span className="text-xs text-500">Terakhir diubah</span>
                <span className="text-xs font-medium text-700">{formatDate(task.updatedAt)}</span>
              </div>
            </div>
          </Card>
        </div>
      </div>

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
        <p className="mb-3">Selesaikan tugas <strong>{task.taskNumber}</strong>?</p>
        <div className="flex flex-column gap-2">
          <label className="font-medium text-sm">Catatan Penyelesaian</label>
          <InputTextarea value={completionNotes} onChange={(e) => setCompletionNotes(e.target.value)} rows={4} placeholder="Jelaskan tindakan yang telah dilakukan..." />
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
        <p className="mb-3">Verifikasi penyelesaian tugas <strong>{task.taskNumber}</strong>?</p>
        <div className="flex flex-column gap-2">
          <label className="font-medium text-sm">Catatan Verifikasi</label>
          <InputTextarea value={verificationNotes} onChange={(e) => setVerificationNotes(e.target.value)} rows={4} placeholder="Catatan verifikasi..." />
        </div>
      </Dialog>

      {/* Ubah Tenggat */}
      <Dialog
        header="Ubah Tenggat"
        visible={dueDateDialog}
        style={{ width: '420px' }}
        onHide={() => setDueDateDialog(false)}
        footer={
          <div>
            <Button label="Batal" icon="pi pi-times" severity="secondary" outlined className="border-round-lg shadow-1 font-semibold mr-2" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} onClick={() => setDueDateDialog(false)} />
            <Button label="Simpan" icon="pi pi-check" className="border-round-lg shadow-1 font-semibold" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} disabled={!newDueDate || savingDueDate} loading={savingDueDate} onClick={handleSaveDueDate} />
          </div>
        }
      >
        <p className="mb-3 text-sm">Tenggat tugas <strong>{task.taskNumber}</strong>.</p>
        <div className="flex flex-column gap-2">
          <label className="font-medium text-sm">Tenggat Baru</label>
          <Calendar
            value={newDueDate}
            onChange={(e) => setNewDueDate(e.value as Date | null)}
            dateFormat="dd/mm/yy"
            showTime
            hourFormat="24"
            showIcon
          />
          <small className="text-500">
            Pengingat dikirim 3 hari sebelum tenggat, pada hari tenggat, dan 3 hari setelahnya.
            Menggeser tenggat ke depan membuat tugas yang tadinya terlambat kembali terbuka.
          </small>
        </div>
      </Dialog>
    </div>
  );
};

export default ObservationTaskDetail;
