import { useEffect, useState, useRef, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Divider } from 'primereact/divider';
import { Toast } from 'primereact/toast';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { ProgressSpinner } from 'primereact/progressspinner';
import { safetyObservationService, userRoleService, observationTaskService, fileUploadService, authService, jsoReportService } from '../../services';
import { masterDivisionService, masterJsoLocationService, masterAreaService } from '../../services/masterDataService';
import { ApprovalBadge, ObservationTypeBadge } from '../../components/irms/StatusBadges';
import type { SafetyObservation, ObservationTask, JsoReport } from '../../types/irms';
import { Tag } from 'primereact/tag';
import { Dialog } from 'primereact/dialog';
import { InputText } from 'primereact/inputtext';
import { InputTextarea } from 'primereact/inputtextarea';
import { Dropdown } from 'primereact/dropdown';
import { Calendar } from 'primereact/calendar';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { toLocalDateTimeString } from '../../utils/date';

/** Foto disimpan sebagai JSON array nama file; kolom kosong/rusak = tanpa foto. */
const parsePhotoList = (json?: string): string[] => {
  if (!json) return [];
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

/* ============================================================
   SAFETY OBSERVATION DETAIL PAGE
   5-Stage Workflow:
   1. Pelaporan Awal    → open
   2. Tinjauan Teknis   → reviewed
   3. Analisis          → in_progress (with delegation)
   4. Tindakan          → action_taken
   5. Penyelesaian Akhir → closed
   ============================================================ */

// Workflow stage index from status
const STAGE_ORDER = ['open', 'reviewed', 'in_progress', 'action_taken', 'closed'] as const;
function getStageIndex(status: string, isJso?: boolean): number {
  if (status === 'rejected') return -1;
  if (status === 'returned') return 0; // Returned goes back to stage 0 (open/pelaporan)
  if (status === 'approved_partially') return 1; // JSO: QSHE review stage active
  if (isJso) {
    // JSO flow: stages shifted after approved_partially
    // reviewed → stage 2 (Superintendent assigns), in_progress → 3, action_taken → 4, closed → 5
    switch (status) {
      case 'reviewed': return 2;
      case 'in_progress': return 3;
      case 'action_taken': return 4;
      case 'closed': return 5; // beyond last stage (all complete)
      default: return 0;
    }
  }
  // NCR: original mapping
  const idx = STAGE_ORDER.indexOf(status as typeof STAGE_ORDER[number]);
  return idx >= 0 ? idx : 0;
}

const SafetyObservationDetail = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);

  const [obs, setObs] = useState<SafetyObservation | null>(null);
  const [loading, setLoading] = useState(true);
  const [userMap, setUserMap] = useState<Map<number, string>>(new Map());

  // Lightbox state — foto dibuka sebagai modal, bukan tab baru
  const [lightboxUrl, setLightboxUrl] = useState<string | null>(null);

  // Observation Task state
  const [tasks, setTasks] = useState<ObservationTask[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);
  const [jsoFindings, setJsoFindings] = useState<JsoReport[]>([]);
  const [showCreateTask, setShowCreateTask] = useState(false);
  const [taskForm, setTaskForm] = useState({ title: '', description: '', findings: '', correctiveAction: '', preventiveAction: '', assignedToId: null as number | null, priority: 'medium', dueDate: null as Date | null });
  const [userOptions, setUserOptions] = useState<{ label: string; value: number }[]>([]);
  // divisi tiap user — untuk membatasi kandidat subordinate ke divisi temuan JSO
  const [userDivisionMap, setUserDivisionMap] = useState<Map<number, number | null>>(new Map());
  // jabatan & divisi user yang sedang login — untuk gating aksi workflow
  const [myInfo, setMyInfo] = useState<{ jobTitle?: string | null; divisionId?: number | null }>({});
  // master id -> nama, untuk resolusi tampilan (divisi PIC temuan, lokasi JSO, area)
  const [divisionNames, setDivisionNames] = useState<Map<number, string>>(new Map());
  const [jsoLocationNames, setJsoLocationNames] = useState<Map<number, string>>(new Map());
  const [areaNames, setAreaNames] = useState<Map<number, string>>(new Map());

  // Workflow dialog state
  const [showWorkflowDialog, setShowWorkflowDialog] = useState(false);
  const [workflowAction, setWorkflowAction] = useState<string>('');
  const [wfSelectedUser, setWfSelectedUser] = useState<number | null>(null);
  const [wfCorrective, setWfCorrective] = useState('');
  const [wfReason, setWfReason] = useState('');
  const [wfComment, setWfComment] = useState('');
  const [wfWoNumber, setWfWoNumber] = useState('');

  // Dialog per-temuan: tugaskan PIC / submit tindak lanjut satu temuan
  const [findingDialog, setFindingDialog] = useState<{ mode: 'assign' | 'action'; finding: JsoReport } | null>(null);
  const [fdUser, setFdUser] = useState<number | null>(null);
  const [fdCorrective, setFdCorrective] = useState('');
  const [fdWoNumber, setFdWoNumber] = useState('');

  useEffect(() => {
    userRoleService.listUsers({ limit: 9999 }).then((resp) => {
      const users = resp.data ?? [];
      const map = new Map<number, string>();
      const divMap = new Map<number, number | null>();
      const opts: { label: string; value: number }[] = [];
      users.forEach((u) => {
        map.set(u.id, u.name);
        divMap.set(u.id, u.divisionId ?? null);
        opts.push({ label: u.name || `User #${u.id}`, value: u.id });
      });
      setUserMap(map);
      setUserDivisionMap(divMap);
      setUserOptions(opts);
      const meId = authService.getUser()?.id;
      const meRec = users.find((u) => u.id === meId);
      if (meRec) setMyInfo({ jobTitle: meRec.jobTitle, divisionId: meRec.divisionId });
    }).catch(console.error);

    // Master untuk resolusi nama di tampilan
    masterDivisionService.list({ limit: 9999 })
      .then((rows) => setDivisionNames(new Map(rows.map((d) => [d.id, d.name]))))
      .catch(console.error);
    masterJsoLocationService.list({ limit: 9999 })
      .then((rows) => setJsoLocationNames(new Map(rows.map((l) => [l.id, l.name]))))
      .catch(console.error);
    masterAreaService.list({ limit: 9999 })
      .then((rows) => setAreaNames(new Map(rows.map((a) => [a.id, a.name]))))
      .catch(console.error);
  }, []);

  const loadData = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const data = await safetyObservationService.detail(Number(id));
      setObs(data);
    } catch (err) {
      console.error('Failed to load safety observation', err);
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Gagal memuat detail observasi',
        life: 3000,
      });
    } finally {
      setLoading(false);
    }
  }, [id]);

  const loadTasks = useCallback(async () => {
    if (!id) return;
    setTasksLoading(true);
    try {
      const data = await observationTaskService.listByObservation(Number(id));
      setTasks(data);
    } catch {
      /* ignore */
    } finally {
      setTasksLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadData();
    loadTasks();
  }, [loadData, loadTasks]);

  useEffect(() => {
    if (!id || !obs?.isJso) return;
    jsoReportService.list(Number(id)).then(setJsoFindings).catch(() => setJsoFindings([]));
  }, [id, obs?.isJso]);

  const handleCreateTask = async () => {
    if (!id) return;
    try {
      const userData = authService.getUser();
      await observationTaskService.create({
        safetyObservationId: Number(id),
        title: taskForm.title,
        description: taskForm.description || undefined,
        findings: taskForm.findings || undefined,
        correctiveAction: taskForm.correctiveAction || undefined,
        preventiveAction: taskForm.preventiveAction || undefined,
        assignedToId: taskForm.assignedToId ?? undefined,
        assignedById: userData?.id,
        priority: taskForm.priority,
        dueDate: taskForm.dueDate ? toLocalDateTimeString(taskForm.dueDate) : undefined,
      });
      toast.current?.show({ severity: 'success', summary: 'Berhasil', detail: 'Tugas observasi berhasil dibuat', life: 3000 });
      setShowCreateTask(false);
      setTaskForm({ title: '', description: '', findings: '', correctiveAction: '', preventiveAction: '', assignedToId: null, priority: 'medium', dueDate: null });
      loadTasks();
    } catch {
      toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal membuat tugas', life: 3000 });
    }
  };

  // Workflow actions
  const openWorkflowDialog = (action: string) => {
    setWorkflowAction(action);
    setWfSelectedUser(null);
    setWfCorrective('');
    setWfReason('');
    setWfComment('');
    // Prefill nomor WO dari WO Avantis yang sudah diresolusi backend dari nomor WR
    // temuan. Sengaja TIDAK jatuh ke nomor WR bila WO-nya kosong: itulah yang dulu
    // membuat nomor WR tersimpan di kolom WO (temuan dengan wr_number = wr_wo_number).
    setWfWoNumber(
      action === 'submit-action'
        ? [...new Set(jsoFindings.map((f) => f.wrWoNumber).filter(Boolean))].join(', ')
        : ''
    );
    setShowWorkflowDialog(true);
  };

  const handleWorkflowSubmit = async () => {
    if (!id) return;
    try {
      const numId = Number(id);
      switch (workflowAction) {
        case 'review': {
          // Approve: reviewer = user yang sedang login; dept terkait sudah
          // ditentukan sejak pelaporan awal (designation_department_id).
          const reviewer = authService.getUser();
          if (!reviewer?.id) return;
          await safetyObservationService.review(numId, reviewer.id);
          break;
        }
        case 'assign-pic':
          if (!wfSelectedUser) return;
          await safetyObservationService.assignPic(numId, wfSelectedUser, authService.getUser()?.id);
          break;
        case 'submit-action': {
          // Pelaksana = user yang login (subordinate yang ditunjuk superiornya)
          const actor = authService.getUser();
          if (!actor?.id) return;
          await safetyObservationService.submitAction(numId, actor.id, wfCorrective, wfWoNumber.trim() || undefined);
          break;
        }
        case 'complete':
          {
            const userData = authService.getUser();
            await safetyObservationService.complete(numId, userData?.id);
          }
          break;
        case 'reject':
          await safetyObservationService.reject(numId, wfReason);
          break;
        case 'return-to-creator':
          await safetyObservationService.returnToCreator(numId, wfComment);
          break;
        case 'resubmit':
          await safetyObservationService.resubmit(numId);
          break;
        default:
          return;
      }
      toast.current?.show({ severity: 'success', summary: 'Berhasil', detail: 'Workflow berhasil diperbarui', life: 3000 });
      setShowWorkflowDialog(false);
      loadData();
    } catch {
      toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal memperbarui workflow', life: 3000 });
    }
  };

  // ── Aksi per-temuan ──
  const loadFindings = useCallback(() => {
    if (!id) return;
    jsoReportService.list(Number(id)).then(setJsoFindings).catch(() => setJsoFindings([]));
  }, [id]);

  const openFindingDialog = (mode: 'assign' | 'action', finding: JsoReport) => {
    setFdUser(null);
    setFdCorrective('');
    setFdWoNumber(mode === 'action' ? (finding.wrWoNumber ?? '') : '');
    setFindingDialog({ mode, finding });
  };

  const handleFindingSubmit = async () => {
    if (!id || !findingDialog?.finding?.id) return;
    const me = authService.getUser();
    try {
      if (findingDialog.mode === 'assign') {
        if (!fdUser) return;
        await jsoReportService.assignPic(Number(id), findingDialog.finding.id, fdUser, me?.id);
      } else {
        if (!me?.id || !fdCorrective.trim()) return;
        await jsoReportService.submitAction(Number(id), findingDialog.finding.id, me.id,
          fdCorrective, fdWoNumber.trim() || undefined);
      }
      toast.current?.show({ severity: 'success', summary: 'Berhasil', detail: 'Temuan berhasil diperbarui', life: 3000 });
      setFindingDialog(null);
      loadFindings();
      loadData();
      loadTasks();
    } catch {
      toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal memperbarui temuan', life: 3000 });
    }
  };

  const getWorkflowDialogTitle = () => {
    const titles: Record<string, string> = {
      'review': 'Tinjauan QSHE — Approve (Administrator Safety)',
      'assign-pic': 'Penugasan Subordinate (Superintendent Divisi Terkait)',
      'submit-action': 'Submit Tindak Lanjut Temuan (Subordinate)',
      'complete': 'Verifikasi & Penutupan (Administrator Safety)',
      'reject': 'Tolak Observasi',
      'return-to-creator': 'Revise — Kembalikan ke Pelaporan Awal',
      'resubmit': 'Ajukan Kembali Setelah Revisi',
    };
    return titles[workflowAction] || 'Workflow';
  };

  const isWorkflowSubmitDisabled = () => {
    if (workflowAction === 'assign-pic' && !wfSelectedUser) return true;
    if (workflowAction === 'submit-action' && !wfCorrective.trim()) return true;
    if (workflowAction === 'reject' && !wfReason.trim()) return true;
    if (workflowAction === 'return-to-creator' && !wfComment.trim()) return true;
    return false;
  };

  if (loading) {
    return (
      <div className="flex align-items-center justify-content-center" style={{ minHeight: '40vh' }}>
        <ProgressSpinner strokeWidth="3" />
      </div>
    );
  }

  if (!obs) {
    return (
      <div className="flex flex-column align-items-center justify-content-center gap-3" style={{ minHeight: '40vh' }}>
        <i className="pi pi-exclamation-circle text-5xl text-400" />
        <p className="text-500">Observasi tidak ditemukan</p>
        <Button label="Kembali" icon="pi pi-arrow-left" onClick={() => navigate('/dashboard/irms/safety-observations')} />
      </div>
    );
  }

  const formatDate = (date?: string) =>
    date
      ? new Date(date).toLocaleDateString('id-ID', {
          weekday: 'long',
          day: '2-digit',
          month: 'long',
          year: 'numeric',
        })
      : '—';

  // Kandidat penugasan/delegasi: untuk JSO dibatasi ke anggota divisi yang
  // dipilih pada temuan; fallback ke semua user bila divisi kosong/tak cocok.
  const findingDivisionIds = new Set(
    jsoFindings.map((f) => f.divisionId).filter((d): d is number => d != null)
  );
  const divisionFilteredOptions =
    obs.isJso && findingDivisionIds.size > 0
      ? userOptions.filter((o) => {
          const div = userDivisionMap.get(o.value);
          return div != null && findingDivisionIds.has(div);
        })
      : [];
  const isDivisionFiltered = divisionFilteredOptions.length > 0;
  const assignableUserOptions =
    isDivisionFiltered && workflowAction === 'assign-pic'
      ? divisionFilteredOptions
      : userOptions;

  // ── Gating aksi workflow per tahap (sesuai role/jabatan/divisi) ──
  const me = authService.getUser();
  const myRoles: string[] = me?.roles ?? [];
  const isSuperAdmin = myRoles.includes('SUPER_ADMIN');
  const isAdminSafety = isSuperAdmin || myRoles.includes('ADMINISTRATOR_SAFETY');
  const isSuperintendentTitle = (myInfo.jobTitle ?? '').toLowerCase().includes('superintendent');
  // JSO dengan temuan memakai aksi PER-TEMUAN (di kartu temuan);
  // aksi level-observasi hanya untuk NCR / JSO tanpa temuan.
  const perFindingMode = Boolean(obs.isJso) && jsoFindings.length > 0;
  const workflowPerms = {
    // Tinjauan QSHE: hanya Administrator Safety
    canReview: isAdminSafety,
    // Analisis & Penugasan Tim: Superintendent dari divisi pada temuan
    // (bila temuan tidak memuat divisi: superintendent mana pun / Admin Safety)
    canAssign:
      !perFindingMode &&
      (isSuperAdmin ||
        (findingDivisionIds.size > 0
          ? isSuperintendentTitle && myInfo.divisionId != null && findingDivisionIds.has(myInfo.divisionId)
          : isSuperintendentTitle || isAdminSafety)),
    // Tindak Lanjut: hanya subordinate yang ditunjuk
    canSubmitAction: !perFindingMode && (isSuperAdmin || (me?.id != null && me.id === obs.assignedToId)),
    // Verifikasi & Penutupan: hanya Administrator Safety
    canComplete: isAdminSafety,
    // Ajukan kembali setelah revise: hanya creator
    canResubmit: isSuperAdmin || (me?.id != null && me.id === obs.reporterId),
  };
  // Gating per-temuan
  const canAssignFinding = (f: JsoReport) =>
    isSuperAdmin ||
    (isSuperintendentTitle && myInfo.divisionId != null && f.divisionId != null && myInfo.divisionId === f.divisionId);
  const canActFinding = (f: JsoReport) =>
    isSuperAdmin || (me?.id != null && f.picId != null && me.id === f.picId);
  const findingAssignedCount = jsoFindings.filter((f) => f.picId != null).length;
  const findingDoneCount = jsoFindings.filter((f) => f.corrective && f.corrective.trim()).length;
  // Baris PIC per temuan untuk timeline (tampilkan semua PIC + status selesai)
  const findingRows = perFindingMode
    ? jsoFindings.map((f, i) => ({
        label: f.wrNumber ? `WR ${f.wrNumber}` : `Temuan #${i + 1}`,
        division: f.divisionId ? divisionNames.get(f.divisionId) : undefined,
        picName: f.picId ? (userMap.get(f.picId) ?? `User #${f.picId}`) : null,
        done: Boolean(f.corrective && f.corrective.trim()),
      }))
    : undefined;

  return (
    <div className="flex flex-column gap-4">
      <Toast ref={toast} />
      <ConfirmDialog />

      {/* Lightbox Dialog */}
      <Dialog
        visible={!!lightboxUrl}
        onHide={() => setLightboxUrl(null)}
        header="Foto"
        modal
        dismissableMask
        style={{ maxWidth: '90vw', maxHeight: '90vh' }}
        contentStyle={{ padding: 0 }}
      >
        {lightboxUrl && (
          <img
            src={lightboxUrl}
            alt="Foto"
            style={{ maxWidth: '85vw', maxHeight: '80vh', display: 'block', objectFit: 'contain' }}
          />
        )}
      </Dialog>

      {/* Header */}
      <div className="flex align-items-center justify-content-between flex-wrap gap-3">
        <div className="flex align-items-center gap-3">
          <Button
            icon="pi pi-arrow-left"
            rounded
            text
            severity="secondary"
            onClick={() => navigate('/dashboard/irms/safety-observations')}
          />
          <div>
            <div className="flex align-items-center gap-2 mb-1">
              <h2 className="text-2xl font-bold text-800 m-0">{obs.reportNumber}</h2>
              <ObservationTypeBadge isJso={obs.isJso} />
            </div>
            <p className="text-sm text-500 mt-0 mb-0">{obs.title}</p>
          </div>
        </div>
        <div className="flex gap-2">
          <Button
            label="Edit Observasi"
            icon="pi pi-pencil"
            onClick={() => navigate(`/dashboard/irms/safety-observations/${id}/edit`)}
            className="font-semibold border-round-xl shadow-2"
            style={{
              background: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
              border: 'none',
              padding: '0.75rem 2rem',
              fontSize: '0.925rem',
              letterSpacing: '0.01em',
              gap: '0.6rem',
            }}
          />
        </div>
      </div>

      <div className="grid">
        {/* Left: Details */}
        <div className="col-12 lg:col-8">
          <Card className="shadow-1 border-round-xl">
            <div className="flex flex-column gap-3">
              <div className="flex align-items-center gap-2">
                <ApprovalBadge status={obs.status} />
                <ObservationTypeBadge isJso={obs.isJso} />
              </div>

              <Divider className="my-2" />

              {obs.description && (
                <div>
                  <h4 className="text-sm font-semibold text-500 mt-0 mb-1">Deskripsi</h4>
                  <p className="text-sm text-700 line-height-3 m-0">{obs.description}</p>
                </div>
              )}

              {/* Foto level observasi. Pada JSO foto melekat pada tiap temuan —
                  yang tampil di sini hanya sisa observasi lama. */}
              {(() => {
                const photoList = parsePhotoList(obs.photos);
                if (photoList.length === 0) return null;
                return (
                  <div>
                    <h4 className="mt-0 mb-2 text-sm font-semibold text-500">
                      {obs.isJso ? 'Foto Observasi (lama)' : 'Foto'}
                    </h4>
                    <div className="flex flex-wrap gap-2">
                      {photoList.map((photo, idx) => (
                        <img
                          key={idx}
                          src={fileUploadService.getPhotoUrl(photo)}
                          alt={`Foto ${idx + 1}`}
                          className="border-round shadow-1"
                          style={{ width: '120px', height: '120px', objectFit: 'cover', cursor: 'pointer' }}
                          onClick={() => setLightboxUrl(fileUploadService.getPhotoUrl(photo))}
                        />
                      ))}
                    </div>
                  </div>
                );
              })()}

              <div className="grid mt-2">
                <div className="col-6 md:col-4">
                  <span className="text-xs text-500 block mb-1">Tanggal Observasi</span>
                  <span className="text-sm font-medium text-800">{formatDate(obs.observationDate ?? obs.observationDateJso)}</span>
                </div>
                <div className="col-6 md:col-4">
                  <span className="text-xs text-500 block mb-1">Lokasi</span>
                  <span className="text-sm font-medium text-800">
                    {obs.locationDetail
                      ?? (obs.jsoLocationId ? jsoLocationNames.get(obs.jsoLocationId) : undefined)
                      ?? '—'}
                  </span>
                </div>
                <div className="col-6 md:col-4">
                  <span className="text-xs text-500 block mb-1">Area</span>
                  <span className="text-sm font-medium text-800">
                    {obs.area?.name
                      ?? ([...new Set(jsoFindings.map((f) => (f.areaId ? areaNames.get(f.areaId) : undefined)).filter(Boolean))].join(', ') || '—')}
                  </span>
                </div>
                <div className="col-6 md:col-4">
                  <span className="text-xs text-500 block mb-1">Dept. Asal</span>
                  <span className="text-sm font-medium text-800">{obs.originDepartment?.name ?? '—'}</span>
                </div>
                <div className="col-6 md:col-4">
                  <span className="text-xs text-500 block mb-1">JSO Locations</span>
                  <span className="text-sm font-medium text-800">
                    {obs.jsoLocation?.name
                      ?? (obs.jsoLocationId ? jsoLocationNames.get(obs.jsoLocationId) : undefined)
                      ?? obs.designationDepartment?.name
                      ?? '—'}
                  </span>
                </div>
                {obs.woNumber && (
                  <div className="col-6 md:col-4">
                    <span className="text-xs text-500 block mb-1">No. WR</span>
                    <span className="text-sm font-medium text-800">{obs.woNumber}</span>
                  </div>
                )}
              </div>

              {/* JSO Team Members */}
              {obs.isJso && (
                <>
                  <Divider className="my-2" />
                  <h4 className="text-sm font-semibold text-500 m-0">Tim Observasi JSO</h4>
                  <div className="grid">
                    <div className="col-4">
                      <span className="text-xs text-500 block mb-1">Leader</span>
                      <span className="text-sm font-medium text-800">
                        {obs.leaderId ? (userMap.get(obs.leaderId) ?? `User #${obs.leaderId}`) : '—'}
                      </span>
                    </div>
                    <div className="col-4">
                      <span className="text-xs text-500 block mb-1">Member 1</span>
                      <span className="text-sm font-medium text-800">
                        {obs.member1Id ? (userMap.get(obs.member1Id) ?? `User #${obs.member1Id}`) : '—'}
                      </span>
                    </div>
                    <div className="col-4">
                      <span className="text-xs text-500 block mb-1">Member 2</span>
                      <span className="text-sm font-medium text-800">
                        {obs.member2Id ? (userMap.get(obs.member2Id) ?? `User #${obs.member2Id}`) : '—'}
                      </span>
                    </div>
                  </div>
                </>
              )}
            </div>
          </Card>

          {/* JSO Findings Section */}
          {obs.isJso && jsoFindings.length > 0 && (
            <Card className="shadow-1 border-round-xl mt-4">
              <h3 className="text-lg font-semibold text-800 mb-3">Temuan JSO</h3>
              <div className="flex flex-column gap-3">
                {jsoFindings.map((f, i) => (
                  <div key={f.id ?? i} className="p-3 border-round-lg border-1 surface-border">
                    <div className="grid">
                      <div className="col-12 md:col-4">
                        <span className="text-xs text-500 block mb-1">Divisi PIC</span>
                        <span className="text-sm font-medium text-800">
                          {f.divisionId ? (
                            <Tag
                              value={divisionNames.get(f.divisionId) ?? `Divisi #${f.divisionId}`}
                              severity="info"
                              icon="pi pi-sitemap"
                              className="text-xs"
                            />
                          ) : (
                            '—'
                          )}
                        </span>
                      </div>
                      {f.wrNumber && (
                        <div className="col-6 md:col-4">
                          <span className="text-xs text-500 block mb-1">No. WR</span>
                          <span className="text-sm font-medium text-800">{f.wrNumber}</span>
                        </div>
                      )}
                      {f.wrWoNumber && (
                        <div className="col-6 md:col-4">
                          <span className="text-xs text-500 block mb-1">No. WO</span>
                          <span className="text-sm font-medium text-800">{f.wrWoNumber}</span>
                        </div>
                      )}
                      {f.areaId && (
                        <div className="col-6 md:col-4">
                          <span className="text-xs text-500 block mb-1">Area</span>
                          <span className="text-sm font-medium text-800">{areaNames.get(f.areaId) ?? '—'}</span>
                        </div>
                      )}
                      <div className="col-12">
                        <span className="text-xs text-500 block mb-1">Temuan</span>
                        <span className="text-sm text-800">{f.findings || '—'}</span>
                      </div>
                      <div className="col-12 md:col-6">
                        <span className="text-xs text-500 block mb-1">Root Cause</span>
                        <span className="text-sm text-800">{f.rootCauseJso || '—'}</span>
                      </div>
                      <div className="col-12 md:col-6">
                        <span className="text-xs text-500 block mb-1">Potensi Bahaya</span>
                        <span className="text-sm text-800">{f.potentialHazard || '—'}</span>
                      </div>
                      {f.recommendation && (
                        <div className="col-12">
                          <span className="text-xs text-500 block mb-1">Rekomendasi</span>
                          <span className="text-sm text-800">{f.recommendation}</span>
                        </div>
                      )}
                      <div className="col-12 md:col-6">
                        <span className="text-xs text-500 block mb-1">PIC Temuan</span>
                        <span className="text-sm font-medium text-800">
                          {f.picId ? (userMap.get(f.picId) ?? `User #${f.picId}`) : 'Belum ditugaskan'}
                        </span>
                      </div>
                      {f.corrective && (
                        <div className="col-12">
                          <span className="text-xs text-500 block mb-1">Tindak Lanjut</span>
                          <span className="text-sm text-800">
                            <i className="pi pi-check-circle text-green-500 mr-1" />
                            {f.corrective}
                            {f.actionById && (
                              <span className="text-500"> — oleh {userMap.get(f.actionById) ?? `User #${f.actionById}`}</span>
                            )}
                          </span>
                        </div>
                      )}
                      {(() => {
                        const photoList = parsePhotoList(f.photos);
                        if (photoList.length === 0) return null;
                        return (
                          <div className="col-12">
                            <span className="block mb-1 text-xs text-500">Foto Temuan</span>
                            <div className="flex flex-wrap gap-2">
                              {photoList.map((photo, pi) => (
                                <img
                                  key={pi}
                                  src={fileUploadService.getPhotoUrl(photo)}
                                  alt={`Foto temuan ${i + 1} — ${pi + 1}`}
                                  className="border-round shadow-1"
                                  style={{ width: '96px', height: '96px', objectFit: 'cover', cursor: 'pointer' }}
                                  onClick={() => setLightboxUrl(fileUploadService.getPhotoUrl(photo))}
                                />
                              ))}
                            </div>
                          </div>
                        );
                      })()}
                    </div>
                    {/* Aksi per temuan */}
                    {!f.corrective && (
                      <div className="flex gap-2 justify-content-end mt-2">
                        {['reviewed', 'in_progress'].includes(obs.status) && canAssignFinding(f) && (
                          <Button
                            label={f.picId ? 'Ganti PIC' : 'Tugaskan PIC'}
                            icon="pi pi-user-plus"
                            size="small"
                            severity="info"
                            outlined={!!f.picId}
                            onClick={() => openFindingDialog('assign', f)}
                          />
                        )}
                        {obs.status === 'in_progress' && canActFinding(f) && (
                          <Button
                            label="Submit Tindak Lanjut"
                            icon="pi pi-check"
                            size="small"
                            severity="success"
                            onClick={() => openFindingDialog('action', f)}
                          />
                        )}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </Card>
          )}

          {/* Observation Tasks Section */}
          <Card className="shadow-1 border-round-xl mt-4">
            <div className="flex align-items-center justify-content-between mb-3">
              <h3 className="text-lg font-semibold text-800 m-0">Tugas Observasi</h3>
              <Button label="Buat Tugas" icon="pi pi-plus" size="small" rounded className="border-round-lg shadow-1 font-semibold" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} onClick={() => setShowCreateTask(true)} />
            </div>
            <DataTable
              value={tasks}
              loading={tasksLoading}
              size="small"
              stripedRows
              emptyMessage="Belum ada tugas observasi"
              className="border-round-lg"
            >
              <Column field="taskNumber" header="No. Tugas" className="font-medium" style={{ width: '140px' }} />
              <Column field="title" header="Judul" />
              <Column header="Ditugaskan Ke" body={(row: ObservationTask) => <span className="text-sm">{row.assignedToId ? (userMap.get(row.assignedToId) ?? `User #${row.assignedToId}`) : '—'}</span>} style={{ width: '140px' }} />
              <Column header="Prioritas" body={(row: ObservationTask) => {
                const pMap: Record<string, { severity: 'success' | 'info' | 'warning' | 'danger'; label: string }> = { low: { severity: 'success', label: 'Rendah' }, medium: { severity: 'info', label: 'Sedang' }, high: { severity: 'warning', label: 'Tinggi' }, critical: { severity: 'danger', label: 'Kritis' } };
                const p = pMap[row.priority] || { severity: 'info' as const, label: row.priority };
                return <Tag severity={p.severity} value={p.label} rounded />;
              }} style={{ width: '100px' }} />
              <Column header="Status" body={(row: ObservationTask) => {
                const sMap: Record<string, { severity: 'success' | 'info' | 'warning' | 'danger' | 'secondary'; label: string }> = { open: { severity: 'info', label: 'Terbuka' }, in_progress: { severity: 'warning', label: 'Proses' }, completed: { severity: 'success', label: 'Selesai' }, overdue: { severity: 'danger', label: 'Overdue' }, cancelled: { severity: 'secondary', label: 'Batal' } };
                const s = sMap[row.status] || { severity: 'info' as const, label: row.status };
                return <Tag severity={s.severity} value={s.label} rounded />;
              }} style={{ width: '100px' }} />
              <Column header="Aksi" body={(row: ObservationTask) => (
                <Button icon="pi pi-eye" rounded text severity="info" size="small" tooltip="Detail" tooltipOptions={{ position: 'top' }} onClick={() => navigate(`/dashboard/irms/observation-tasks/${row.id}`)} />
              )} style={{ width: '60px' }} />
            </DataTable>
          </Card>

          {/* Create Task Dialog */}
          <Dialog
            header="Buat Tugas Observasi"
            visible={showCreateTask}
            style={{ width: '600px' }}
            onHide={() => setShowCreateTask(false)}
            footer={
              <div>
                <Button label="Batal" icon="pi pi-times" severity="secondary" outlined className="border-round-lg shadow-1 font-semibold mr-2" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} onClick={() => setShowCreateTask(false)} />
                <Button label="Simpan" icon="pi pi-check" className="border-round-lg shadow-1 font-semibold" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} onClick={handleCreateTask} disabled={!taskForm.title.trim()} />
              </div>
            }
          >
            <div className="flex flex-column gap-3">
              <div className="flex flex-column gap-1">
                <label className="font-medium text-sm">Judul <span className="text-red-500">*</span></label>
                <InputText value={taskForm.title} onChange={(e) => setTaskForm({ ...taskForm, title: e.target.value })} placeholder="Judul tugas..." />
              </div>
              <div className="flex flex-column gap-1">
                <label className="font-medium text-sm">Deskripsi</label>
                <InputTextarea value={taskForm.description} onChange={(e) => setTaskForm({ ...taskForm, description: e.target.value })} rows={3} placeholder="Deskripsi tugas..." />
              </div>
              <div className="flex flex-column gap-1">
                <label className="font-medium text-sm">Temuan</label>
                <InputTextarea value={taskForm.findings} onChange={(e) => setTaskForm({ ...taskForm, findings: e.target.value })} rows={2} placeholder="Temuan yang ditemukan..." />
              </div>
              <div className="grid">
                <div className="col-6">
                  <div className="flex flex-column gap-1">
                    <label className="font-medium text-sm">Tindakan Korektif</label>
                    <InputTextarea value={taskForm.correctiveAction} onChange={(e) => setTaskForm({ ...taskForm, correctiveAction: e.target.value })} rows={2} placeholder="Tindakan korektif..." />
                  </div>
                </div>
                <div className="col-6">
                  <div className="flex flex-column gap-1">
                    <label className="font-medium text-sm">Tindakan Preventif</label>
                    <InputTextarea value={taskForm.preventiveAction} onChange={(e) => setTaskForm({ ...taskForm, preventiveAction: e.target.value })} rows={2} placeholder="Tindakan preventif..." />
                  </div>
                </div>
              </div>
              <div className="grid">
                <div className="col-6">
                  <div className="flex flex-column gap-1">
                    <label className="font-medium text-sm">Ditugaskan Ke</label>
                    <Dropdown value={taskForm.assignedToId} options={userOptions} onChange={(e) => setTaskForm({ ...taskForm, assignedToId: e.value })} placeholder="Pilih pengguna..." filter showClear />
                  </div>
                </div>
                <div className="col-3">
                  <div className="flex flex-column gap-1">
                    <label className="font-medium text-sm">Prioritas</label>
                    <Dropdown value={taskForm.priority} options={[{ label: 'Rendah', value: 'low' }, { label: 'Sedang', value: 'medium' }, { label: 'Tinggi', value: 'high' }, { label: 'Kritis', value: 'critical' }]} onChange={(e) => setTaskForm({ ...taskForm, priority: e.value })} />
                  </div>
                </div>
                <div className="col-3">
                  <div className="flex flex-column gap-1">
                    <label className="font-medium text-sm">Tenggat</label>
                    <Calendar value={taskForm.dueDate} onChange={(e) => setTaskForm({ ...taskForm, dueDate: e.value as Date | null })} dateFormat="dd/mm/yy" showIcon />
                  </div>
                </div>
              </div>
            </div>
          </Dialog>
        </div>

        {/* Right: 5-Stage Workflow */}
        <div className="col-12 lg:col-4">
          <Card title="Alur Persetujuan" className="shadow-1 border-round-xl mb-4">
            <WorkflowTimeline
              obs={obs}
              userMap={userMap}
              onAction={openWorkflowDialog}
              perms={workflowPerms}
              findingProgress={perFindingMode
                ? { assigned: findingAssignedCount, done: findingDoneCount, total: jsoFindings.length }
                : undefined}
              findingRows={findingRows}
            />
          </Card>

          {/* Timestamps */}
          <Card title="Informasi Waktu" className="shadow-1 border-round-xl">
            <div className="flex flex-column gap-2">
              <div className="flex justify-content-between">
                <span className="text-xs text-500">Dibuat</span>
                <span className="text-xs font-medium text-700">{formatDate(obs.createdAt)}</span>
              </div>
              <div className="flex justify-content-between">
                <span className="text-xs text-500">Terakhir diubah</span>
                <span className="text-xs font-medium text-700">{formatDate(obs.updatedAt)}</span>
              </div>
              {obs.assignedAt && (
                <div className="flex justify-content-between">
                  <span className="text-xs text-500">PIC Ditugaskan</span>
                  <span className="text-xs font-medium text-700">{formatDate(obs.assignedAt)}</span>
                </div>
              )}
              {obs.actionTakenAt && (
                <div className="flex justify-content-between">
                  <span className="text-xs text-500">Tindakan Diambil</span>
                  <span className="text-xs font-medium text-700">{formatDate(obs.actionTakenAt)}</span>
                </div>
              )}
              {obs.doneAt && (
                <div className="flex justify-content-between">
                  <span className="text-xs text-500">Diselesaikan</span>
                  <span className="text-xs font-medium text-700">{formatDate(obs.doneAt)}</span>
                </div>
              )}
            </div>
          </Card>
        </div>
      </div>

      {/* Workflow Action Dialog */}
      <Dialog
        header={getWorkflowDialogTitle()}
        visible={showWorkflowDialog}
        style={{ width: '500px' }}
        onHide={() => setShowWorkflowDialog(false)}
        footer={
          <div>
            <Button label="Batal" icon="pi pi-times" severity="secondary" outlined className="border-round-lg shadow-1 font-semibold mr-2" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} onClick={() => setShowWorkflowDialog(false)} />
            <Button
              label={workflowAction === 'reject' ? 'Tolak' : 'Konfirmasi'}
              icon={workflowAction === 'reject' ? 'pi pi-times' : 'pi pi-check'}
              severity={workflowAction === 'reject' ? 'danger' : undefined}
              className="border-round-lg shadow-1 font-semibold"
              style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }}
              onClick={handleWorkflowSubmit}
              disabled={isWorkflowSubmitDisabled()}
            />
          </div>
        }
      >
        <div className="flex flex-column gap-3">
          {/* User selection for assign-pic */}
          {workflowAction === 'assign-pic' && (
            <div className="flex flex-column gap-1">
              <label className="font-medium text-sm">
                Subordinate (Anggota Divisi Temuan)
                <span className="text-red-500"> *</span>
              </label>
              <Dropdown
                value={wfSelectedUser}
                options={assignableUserOptions}
                onChange={(e) => setWfSelectedUser(e.value)}
                placeholder="Pilih pengguna..."
                filter
                showClear
              />
              {isDivisionFiltered && (
                <small className="text-500">Kandidat dibatasi ke anggota divisi yang dipilih pada temuan JSO. Penugasan otomatis tercatat di Tugas Observasi.</small>
              )}
            </div>
          )}

          {/* Approve confirmation for QSHE review */}
          {workflowAction === 'review' && (
            <p className="text-sm text-700">
              Approve observasi ini? Laporan akan diteruskan ke Superintendent
              divisi terkait (sesuai divisi pada temuan) untuk analisis &amp;
              penugasan tim. Anda tercatat sebagai reviewer QSHE.
            </p>
          )}

          {/* Corrective action text + Nomor WO (dari WR temuan) */}
          {workflowAction === 'submit-action' && (
            <>
              <div className="flex flex-column gap-1">
                <label className="font-medium text-sm">Nomor WO</label>
                <InputText
                  value={wfWoNumber}
                  onChange={(e) => setWfWoNumber(e.target.value)}
                  placeholder="Nomor WO tindak lanjut..."
                />
                <small className="text-500">Terisi otomatis dari WR pada temuan (WR → WO Avantis); dapat disesuaikan bila perlu.</small>
              </div>
              <div className="flex flex-column gap-1">
                <label className="font-medium text-sm">Tindak Lanjut Temuan <span className="text-red-500">*</span></label>
                <InputTextarea value={wfCorrective} onChange={(e) => setWfCorrective(e.target.value)} rows={4} placeholder="Deskripsi tindak lanjut temuan dan close action yang telah dilakukan..." />
                <small className="text-500">Tindak lanjut dicatat atas nama Anda dan menutup Tugas Observasi terkait.</small>
              </div>
            </>
          )}

          {/* Rejection reason */}
          {workflowAction === 'reject' && (
            <div className="flex flex-column gap-1">
              <label className="font-medium text-sm">Alasan Penolakan <span className="text-red-500">*</span></label>
              <InputTextarea value={wfReason} onChange={(e) => setWfReason(e.target.value)} rows={3} placeholder="Alasan penolakan..." />
            </div>
          )}

          {/* Revise: return to Creator with a note */}
          {workflowAction === 'return-to-creator' && (
            <div className="flex flex-column gap-1">
              <label className="font-medium text-sm">Catatan Revisi untuk Creator <span className="text-red-500">*</span></label>
              <InputTextarea value={wfComment} onChange={(e) => setWfComment(e.target.value)} rows={3} placeholder="Jelaskan data yang perlu dilengkapi atau direvisi..." />
              <small className="text-500">Laporan dikembalikan ke tahap Pelaporan Awal; creator dapat merevisi lalu mengajukan kembali.</small>
            </div>
          )}

          {/* Resubmit confirmation */}
          {workflowAction === 'resubmit' && (
            <p className="text-sm text-700">Apakah Anda yakin data sudah lengkap dan ingin mengajukan kembali laporan ini untuk ditinjau?</p>
          )}

          {/* Complete confirmation */}
          {workflowAction === 'complete' && (
            <p className="text-sm text-700">Sebagai Administrator Safety, apakah Anda telah memverifikasi tindak lanjut temuan yang diinput oleh subordinate dan ingin menutup laporan ini? (CHECKLIST Closed)</p>
          )}
        </div>
      </Dialog>

      {/* Per-Finding Dialog: Tugaskan PIC / Submit Tindak Lanjut satu temuan */}
      <Dialog
        header={findingDialog?.mode === 'assign'
          ? 'Tugaskan PIC Temuan (Superintendent Divisi)'
          : 'Submit Tindak Lanjut Temuan (PIC)'}
        visible={!!findingDialog}
        style={{ width: '500px' }}
        onHide={() => setFindingDialog(null)}
        footer={
          <div>
            <Button label="Batal" icon="pi pi-times" severity="secondary" outlined className="mr-2" onClick={() => setFindingDialog(null)} />
            <Button
              label="Konfirmasi"
              icon="pi pi-check"
              onClick={handleFindingSubmit}
              disabled={findingDialog?.mode === 'assign' ? !fdUser : !fdCorrective.trim()}
            />
          </div>
        }
      >
        {findingDialog && (
          <div className="flex flex-column gap-3">
            <div className="p-2 border-round surface-100 text-sm text-700">
              <i className="pi pi-search mr-1" />
              {findingDialog.finding.findings || 'Temuan'}
              {findingDialog.finding.divisionId && (
                <span className="text-500"> — Divisi {divisionNames.get(findingDialog.finding.divisionId) ?? findingDialog.finding.divisionId}</span>
              )}
            </div>
            {findingDialog.mode === 'assign' ? (
              <div className="flex flex-column gap-1">
                <label className="font-medium text-sm">Subordinate (Anggota Divisi) <span className="text-red-500">*</span></label>
                <Dropdown
                  value={fdUser}
                  options={(() => {
                    const div = findingDialog.finding.divisionId;
                    const scoped = div != null
                      ? userOptions.filter((o) => userDivisionMap.get(o.value) === div)
                      : [];
                    return scoped.length > 0 ? scoped : userOptions;
                  })()}
                  onChange={(e) => setFdUser(e.value)}
                  placeholder="Pilih anggota divisi..."
                  filter
                  showClear
                />
                <small className="text-500">Penugasan otomatis tercatat sebagai Tugas Observasi dan PIC mendapat notifikasi.</small>
              </div>
            ) : (
              <>
                <div className="flex flex-column gap-1">
                  <label className="font-medium text-sm">Nomor WO</label>
                  <InputText value={fdWoNumber} onChange={(e) => setFdWoNumber(e.target.value)} placeholder="Nomor WO tindak lanjut..." />
                  <small className="text-500">Terisi otomatis dari WR temuan (WR → WO Avantis); dapat disesuaikan.</small>
                </div>
                <div className="flex flex-column gap-1">
                  <label className="font-medium text-sm">Tindak Lanjut Temuan <span className="text-red-500">*</span></label>
                  <InputTextarea value={fdCorrective} onChange={(e) => setFdCorrective(e.target.value)} rows={4} placeholder="Deskripsi tindak lanjut dan close action temuan ini..." />
                  <small className="text-500">Dicatat atas nama Anda; bila semua temuan selesai, laporan lanjut ke Verifikasi &amp; Penutupan.</small>
                </div>
              </>
            )}
          </div>
        )}
      </Dialog>
    </div>
  );
};

// ============================================================
// WORKFLOW TIMELINE COMPONENT (5-Stage)
// ============================================================
interface WorkflowPermissions {
  canReview: boolean;
  canAssign: boolean;
  canSubmitAction: boolean;
  canComplete: boolean;
  canResubmit: boolean;
}

interface WorkflowTimelineProps {
  obs: SafetyObservation;
  userMap: Map<number, string>;
  onAction: (action: string) => void;
  perms: WorkflowPermissions;
  /** Progres per-temuan (JSO): tahap 3 & 4 selesai bila semua temuan terpenuhi. */
  findingProgress?: { assigned: number; done: number; total: number };
  /** Daftar PIC per temuan (JSO) untuk ditampilkan di tahap Analisis & Tindak Lanjut. */
  findingRows?: { label: string; division?: string; picName: string | null; done: boolean }[];
}

const STAGE_CONFIGS = [
  { key: 'open',         label: 'Pelaporan Awal',             icon: 'pi pi-file',         roleLabel: 'Karyawan / User Creator' },
  { key: 'reviewed',     label: 'Tinjauan QSHE',              icon: 'pi pi-search',       roleLabel: 'Administrator Safety' },
  { key: 'in_progress',  label: 'Analisis & Penugasan Tim',   icon: 'pi pi-chart-bar',    roleLabel: 'Superintendent Divisi Terkait' },
  { key: 'action_taken', label: 'Tindak Lanjut Temuan',       icon: 'pi pi-wrench',       roleLabel: 'Subordinate (Bawahan yang Ditunjuk)' },
  { key: 'closed',       label: 'Verifikasi & Penutupan',     icon: 'pi pi-check-circle', roleLabel: 'Administrator Safety' },
];

function getStageStatus(stageIdx: number, currentIdx: number, isRejected: boolean, isReturned: boolean): 'completed' | 'active' | 'pending' | 'rejected' | 'returned' {
  if (isRejected) return stageIdx <= currentIdx ? 'rejected' : 'pending';
  if (isReturned && stageIdx === 0) return 'returned';
  if (isReturned) return 'pending';
  if (stageIdx < currentIdx) return 'completed';
  if (stageIdx === currentIdx) return 'active';
  return 'pending';
}

const STAGE_COLORS: Record<string, { bg: string; border: string; text: string; line: string }> = {
  completed: { bg: '#e8f5e9', border: '#4caf50', text: '#2e7d32', line: '#4caf50' },
  active:    { bg: '#e3f2fd', border: '#2196f3', text: '#1565c0', line: '#bdbdbd' },
  pending:   { bg: '#f5f5f5', border: '#bdbdbd', text: '#9e9e9e', line: '#bdbdbd' },
  rejected:  { bg: '#ffebee', border: '#f44336', text: '#c62828', line: '#f44336' },
  returned:  { bg: '#fff3e0', border: '#ff9800', text: '#e65100', line: '#ff9800' },
};

const WorkflowTimeline: React.FC<WorkflowTimelineProps> = ({ obs, userMap, onAction, perms, findingProgress, findingRows }) => {
  const isRejected = obs.status === 'rejected';
  const isReturned = obs.status === 'returned';
  const currentIdx = getStageIndex(obs.status, obs.isJso);

  const getUserName = (userId?: number) => {
    if (!userId) return null;
    return userMap.get(userId) ?? `User #${userId}`;
  };

  const formatDateTime = (dateStr?: string) => {
    if (!dateStr) return null;
    return new Date(dateStr).toLocaleDateString('id-ID', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  };

  // Get person & date for each stage
  const getStageInfo = (stageIdx: number) => {
    switch (stageIdx) {
      case 0: return { person: getUserName(obs.reporterId), date: formatDateTime(obs.createdAt) };
      case 1: return { person: getUserName(obs.reviewedById), date: null, deptTerkait: obs.designationDepartment?.name };
      case 2: {
        const person = getUserName(obs.assignedToId);
        const hasDelegation = !!obs.delegationNote;
        return { person, date: formatDateTime(obs.assignedAt), hasDelegation, delegationNote: obs.delegationNote };
      }
      case 3: {
        const person = getUserName(obs.actionById);
        return { person, date: formatDateTime(obs.actionTakenAt), corrective: obs.corrective };
      }
      case 4: return { person: null, date: formatDateTime(obs.doneAt) };
      default: return { person: null, date: null };
    }
  };

  // Which actions are available at current stage
  const getAvailableActions = (stageIdx: number): { action: string; label: string; icon: string; severity: 'success' | 'info' | 'warning' | 'danger' }[] => {
    if (isRejected || obs.status === 'closed') return [];

    // When returned, only the creator can resubmit (stage 0)
    if (isReturned) {
      if (stageIdx === 0 && perms.canResubmit) {
        return [
          { action: 'resubmit', label: 'Ajukan Kembali', icon: 'pi pi-replay', severity: 'success' },
        ];
      }
      return [];
    }

    if (stageIdx !== currentIdx) return [];

    const actions: { action: string; label: string; icon: string; severity: 'success' | 'info' | 'warning' | 'danger' }[] = [];

    // Use status-based logic to handle both NCR and JSO flows.
    // Setiap aksi digate sesuai peran tahapnya (Admin Safety / Superintendent
    // divisi temuan / subordinate yang ditunjuk).
    switch (obs.status) {
      case 'open':
      case 'approved_partially':
        // Tinjauan QSHE (NCR & JSO): hanya Administrator Safety
        if (perms.canReview) {
          actions.push({ action: 'review', label: 'Approve', icon: 'pi pi-check', severity: 'success' });
          actions.push({ action: 'return-to-creator', label: 'Revise', icon: 'pi pi-replay', severity: 'warning' });
        }
        break;
      case 'reviewed':
        // Hanya Superintendent dari divisi pada temuan yang menugaskan subordinate
        if (perms.canAssign) {
          actions.push({ action: 'assign-pic', label: 'Tugaskan Subordinate', icon: 'pi pi-user-plus', severity: 'success' });
        }
        break;
      case 'in_progress':
        // Hanya subordinate yang ditunjuk; delegasi sudah tidak ada di tahap ini
        if (perms.canSubmitAction) {
          actions.push({ action: 'submit-action', label: 'Submit Tindak Lanjut', icon: 'pi pi-check', severity: 'success' });
        }
        break;
      case 'action_taken':
        // Administrator Safety verifies and closes
        if (perms.canComplete) {
          actions.push({ action: 'complete', label: 'Verifikasi & Tutup (Admin Safety)', icon: 'pi pi-check-circle', severity: 'success' });
        }
        break;
    }
    return actions;
  };

  return (
    <div className="flex flex-column gap-0">
      {STAGE_CONFIGS.map((stage, idx) => {
        const stageStatus = getStageStatus(idx, currentIdx, isRejected, isReturned);
        const colors = STAGE_COLORS[stageStatus];
        const info = getStageInfo(idx);
        const actions = getAvailableActions(idx);
        const isLast = idx === STAGE_CONFIGS.length - 1;

        return (
          <div key={stage.key} className="flex gap-3" style={{ minHeight: '80px' }}>
            {/* Timeline line + circle */}
            <div className="flex flex-column align-items-center" style={{ width: '36px' }}>
              <div
                className="flex align-items-center justify-content-center border-circle"
                style={{
                  width: '36px', height: '36px',
                  backgroundColor: colors.bg,
                  border: `2px solid ${colors.border}`,
                  flexShrink: 0,
                }}
              >
                <i
                  className={stageStatus === 'completed' ? 'pi pi-check' : stage.icon}
                  style={{ fontSize: '0.9rem', color: colors.text }}
                />
              </div>
              {!isLast && (
                <div style={{ width: '2px', flex: 1, backgroundColor: colors.line, minHeight: '20px' }} />
              )}
            </div>

            {/* Content */}
            <div className="flex flex-column pb-3" style={{ flex: 1 }}>
              <div className="flex align-items-center gap-2 mb-1">
                <span className="text-sm font-bold" style={{ color: colors.text }}>{stage.label}</span>
                {stageStatus === 'completed' && <Tag value="Selesai" severity="success" className="text-xs" />}
                {stageStatus === 'active' && <Tag value="Berjalan" severity="info" className="text-xs" />}
                {stageStatus === 'pending' && <Tag value="Menunggu" severity="secondary" className="text-xs" />}
                {stageStatus === 'rejected' && <Tag value="Ditolak" severity="danger" className="text-xs" />}
                {stageStatus === 'returned' && <Tag value="Dikembalikan" severity="warning" className="text-xs" />}
              </div>

              <span className="text-xs text-500 mb-1">{stage.roleLabel}</span>

              {findingProgress && idx === 2 && (
                <span className="text-xs font-medium text-600 mb-1">
                  {findingProgress.assigned}/{findingProgress.total} temuan ditugaskan
                </span>
              )}
              {findingProgress && idx === 3 && (
                <span className="text-xs font-medium text-600 mb-1">
                  {findingProgress.done}/{findingProgress.total} temuan ditindaklanjuti
                </span>
              )}

              {/* Daftar PIC per temuan (mode per-temuan JSO): tampilkan semua PIC.
                  Tahap 2 = daftar penugasan; tahap 3 = daftar tindak lanjut + status. */}
              {findingRows && (idx === 2 || idx === 3) ? (
                <div className="flex flex-column gap-1 mt-1">
                  {findingRows.map((row, ri) => (
                    <div key={ri} className="flex align-items-center gap-2 text-sm">
                      {idx === 3 && (
                        <i
                          className={row.done ? 'pi pi-check-circle text-green-500' : 'pi pi-clock text-orange-400'}
                          style={{ fontSize: '0.85rem' }}
                        />
                      )}
                      <span className="font-medium text-800">
                        {row.picName ?? <span className="text-500 font-normal">Belum ditugaskan</span>}
                      </span>
                      <span className="text-xs text-500">
                        · {row.label}{row.division ? ` · ${row.division}` : ''}
                        {idx === 3 && (row.done ? ' · Selesai' : ' · Menunggu')}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <>
                  {info.person && (
                    <span className="text-sm font-medium text-800">{info.person}</span>
                  )}
                  {info.date && (
                    <span className="text-xs text-500">{info.date}</span>
                  )}
                </>
              )}

              {/* Designation department info for stage 1 (QSHE review) */}
              {idx === 1 && (info as { deptTerkait?: string }).deptTerkait && (
                <div className="mt-1 p-2 border-round" style={{ backgroundColor: '#e3f2fd', border: '1px solid #bbdefb' }}>
                  <span className="text-xs font-medium" style={{ color: '#1565c0' }}>
                    <i className="pi pi-building mr-1" style={{ fontSize: '0.7rem' }} />
                    Dept. Terkait: {(info as { deptTerkait?: string }).deptTerkait}
                  </span>
                </div>
              )}

              {/* Delegation info for stage 3 */}
              {idx === 2 && (info as { hasDelegation?: boolean; delegationNote?: string }).hasDelegation && (
                <div className="mt-1 p-2 border-round" style={{ backgroundColor: '#fff3e0', border: '1px solid #ffe0b2' }}>
                  <span className="text-xs font-medium" style={{ color: '#e65100' }}>
                    <i className="pi pi-share-alt mr-1" style={{ fontSize: '0.7rem' }} />
                    Didelegasikan
                  </span>
                  {(info as { delegationNote?: string }).delegationNote && (
                    <p className="text-xs mt-1 mb-0" style={{ color: '#bf360c' }}>{(info as { delegationNote?: string }).delegationNote}</p>
                  )}
                </div>
              )}

              {/* Corrective action for stage 4 */}
              {idx === 3 && (info as { corrective?: string }).corrective && (
                <div className="mt-1 p-2 border-round" style={{ backgroundColor: '#e8f5e9', border: '1px solid #c8e6c9' }}>
                  <span className="text-xs font-medium" style={{ color: '#2e7d32' }}>Tindak Lanjut Temuan:</span>
                  <p className="text-xs mt-1 mb-0" style={{ color: '#1b5e20' }}>{(info as { corrective?: string }).corrective}</p>
                </div>
              )}

              {/* Rejection reason */}
              {isRejected && obs.reason && idx === currentIdx && (
                <div className="mt-1 p-2 border-round" style={{ backgroundColor: '#ffebee', border: '1px solid #ffcdd2' }}>
                  <span className="text-xs font-medium" style={{ color: '#c62828' }}>Alasan Penolakan:</span>
                  <p className="text-xs mt-1 mb-0" style={{ color: '#b71c1c' }}>{obs.reason}</p>
                </div>
              )}

              {/* Return to creator comment */}
              {isReturned && obs.reason && idx === 0 && (
                <div className="mt-1 p-2 border-round" style={{ backgroundColor: '#fff3e0', border: '1px solid #ffe0b2' }}>
                  <span className="text-xs font-medium" style={{ color: '#e65100' }}>
                    <i className="pi pi-replay mr-1" style={{ fontSize: '0.7rem' }} />
                    Komentar QSHE:
                  </span>
                  <p className="text-xs mt-1 mb-0" style={{ color: '#bf360c' }}>{obs.reason}</p>
                </div>
              )}

              {/* Action buttons */}
              {actions.length > 0 && (
                <div className="flex gap-2 mt-2">
                  {actions.map((a) => (
                    <Button key={a.action} label={a.label} icon={a.icon} severity={a.severity} size="small" className="border-round-lg shadow-1 font-semibold" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.5rem 1rem' }} onClick={() => onAction(a.action)} />
                  ))}
                </div>
              )}
            </div>
          </div>
        );
      })}

      {/* Reject button — visible when not yet closed/rejected/returned */}
      {!isRejected && !isReturned && obs.status !== 'closed' && (
        <>
          <Divider className="my-2" />
          <Button label="Tolak Observasi" icon="pi pi-times" severity="danger" outlined size="small" className="w-full border-round-lg shadow-1 font-semibold" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} onClick={() => onAction('reject')} />
        </>
      )}
    </div>
  );
};

export default SafetyObservationDetail;
