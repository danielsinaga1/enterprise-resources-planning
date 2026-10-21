import { useState, useEffect, useCallback, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Card } from "primereact/card";
import { Button } from "primereact/button";
import { Tag } from "primereact/tag";
import { Toast } from "primereact/toast";
import { DataTable } from "primereact/datatable";
import { Column } from "primereact/column";
import { ConfirmDialog, confirmDialog } from "primereact/confirmdialog";
import { Dialog } from "primereact/dialog";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import { Calendar } from "primereact/calendar";
import { Divider } from "primereact/divider";
import { ProgressSpinner } from "primereact/progressspinner";
import {
  laboratoryReportService,
  analysisDetailService,
  labConfigService,
} from "../../services/limsService";
import { userRoleService } from "../../services/rbacService";
import type {
  LaboratoryReportDto,
  LaboratoryReportPayload,
  AnalysisDetailDto,
  AnalysisDetailPayload,
  LabParameter,
  LabTestType,
  LabMethod,
  LabStandard,
  LabUnit,
  LabDepartment,
  AuditLogDto,
  SpecStatus,
  LimitType,
} from "../../types/lims";
import { serverMessage } from "../../utils/apiError";
import { useAuth } from "../../context/AuthContext";
import { exportCoaPdf } from "../../utils/coaExport";
import { ChemText } from "../../utils/chemicalFormat";
import { toLocalISODate } from "../../utils/date";
import { useLimsPermissions } from "../../hooks/useLimsPermissions";
import ApprovalTimeline from "../../components/shared/ApprovalTimeline";
import type { StageAction, StageConfig, StageInfo } from "../../components/shared/ApprovalTimeline";

const statusSeverity = (status: string): "info" | "warning" | "success" | "danger" | "secondary" => {
  switch (status) {
    case "Open": return "info";
    case "Assigned": return "info";
    case "Reviewed": return "warning";
    case "Close": return "success";
    case "Rejected": return "danger";
    default: return "secondary";
  }
};

// Laboratory Report 4-stage workflow: Open → Assigned → Reviewed → Close
// Pelaporan Awal otomatis selesai saat laporan dibuat (tidak perlu persetujuan).
// Tahapan berikutnya digerakkan per peran lab (bukan hierarki atasan).
const LAB_STAGES: StageConfig[] = [
  { key: "open",     label: "Pelaporan Awal",           icon: "pi pi-file",         roleLabel: "Pelapor" },
  { key: "assigned", label: "Analisis & Penugasan Tim", icon: "pi pi-users",        roleLabel: "Reviewer Lab" },
  { key: "reviewed", label: "Tinjauan Teknis",          icon: "pi pi-search",       roleLabel: "Input: Sampler & Analis Lab · Finalisasi: Reviewer Lab" },
  { key: "closed",   label: "Penyelesaian Akhir",       icon: "pi pi-check-circle", roleLabel: "Manager Lab" },
];

function getLabStageIndex(status: string): number {
  switch (status) {
    case "Open": return 1;       // Stage 0 auto-completed, stage 1 (penugasan) active
    case "Assigned": return 2;   // Tim ditugaskan → Tinjauan Teknis active
    case "Reviewed": return 3;   // Ditinjau → Penyelesaian Akhir active
    case "Close": return 4;      // All stages completed
    case "Rejected": return -1;
    default: return 1;
  }
}

const SPEC_LABEL: Record<SpecStatus, string> = {
  PASS: "Sesuai",
  FAIL: "Di luar spesifikasi",
  NOT_EVALUATED: "Belum dinilai",
};

const SPEC_SEVERITY: Record<SpecStatus, "success" | "danger" | "secondary"> = {
  PASS: "success",
  FAIL: "danger",
  NOT_EVALUATED: "secondary",
};

/** Angka hasil/batas mengikuti gaya Indonesia, dengan desimal dari master standar bila ada. */
const formatNumber = (value: number | null | undefined, decimals?: number | null): string => {
  if (value === null || value === undefined) return "-";
  return value.toLocaleString("id-ID", {
    minimumFractionDigits: decimals ?? 0,
    maximumFractionDigits: decimals ?? 6,
  });
};

interface LimitShape {
  limitType?: LimitType | null;
  lowerLimit?: number | null;
  upperLimit?: number | null;
  decimalPlaces?: number | null;
  /** Teks standar apa adanya, dipakai bila batasnya kualitatif. */
  fallback?: string | null;
}

/** Ringkasan batas spesifikasi — satu sumber teks untuk tabel maupun form. */
const describeLimit = (s: LimitShape): string => {
  switch (s.limitType) {
    case "MAX":
      return `maks. ${formatNumber(s.upperLimit, s.decimalPlaces)}`;
    case "MIN":
      return `min. ${formatNumber(s.lowerLimit, s.decimalPlaces)}`;
    case "RANGE":
      return `${formatNumber(s.lowerLimit, s.decimalPlaces)} – ${formatNumber(s.upperLimit, s.decimalPlaces)}`;
    default:
      return s.fallback || "-";
  }
};

const formatSpecLimit = (d: AnalysisDetailDto): string => describeLimit({
  limitType: d.standardLimitType,
  lowerLimit: d.standardLowerLimit,
  upperLimit: d.standardUpperLimit,
  decimalPlaces: d.standardDecimalPlaces,
  fallback: d.standardMeasure,
});

const describeStandardLimit = (s: LabStandard): string => describeLimit({ ...s, fallback: s.measure });

const AUDIT_ACTION_LABEL: Record<AuditLogDto["action"], string> = {
  CREATE: "Dibuat",
  UPDATE: "Diubah",
  DELETE: "Dihapus",
  STATUS_CHANGE: "Status",
};

const AUDIT_FIELD_LABEL: Record<string, string> = {
  status: "Status",
  resultSampler: "Hasil Sampler",
  resultAnalysis: "Hasil Analisis",
  valueNumeric: "Nilai numerik",
  specStatus: "Status spesifikasi",
  description: "Deskripsi",
  standardId: "Standar",
  location: "Lokasi",
  notes: "Catatan",
  remark: "Keterangan",
  dateSampling: "Tanggal sampling",
  dateAnalysis: "Tanggal analisis",
  requestedBy: "Pemohon",
  samplerId: "Sampler",
  analystId: "Analis",
  departmentId: "Departemen",
};

const emptyDetail: AnalysisDetailPayload = {
  parameterId: undefined,
  testTypeId: undefined,
  methodId: undefined,
  standardId: undefined,
  unitId: undefined,
  resultSampler: "",
  resultAnalysis: "",
  description: "",
};

const LaboratoryReportDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { canReview, canApprove, canReject } = useLimsPermissions();
  const toast = useRef<Toast>(null);
  const [report, setReport] = useState<LaboratoryReportDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  // ── INLINE EDITING (SEMENTARA: semua bisa edit) ──
  const [isEditingMain, setIsEditingMain] = useState(false);
  const [mainSaving, setMainSaving] = useState(false);
  const [departments, setDepartments] = useState<LabDepartment[]>([]);
  // Kandidat Analis/Sampler dibatasi ke role ANALIS_LAB / SAMPLER_LAB
  const [analystOptions, setAnalystOptions] = useState<{ id: number; name: string }[]>([]);
  const [samplerOptions, setSamplerOptions] = useState<{ id: number; name: string }[]>([]);
  const [editMainForm, setEditMainForm] = useState<LaboratoryReportPayload>({
    dateSampling: "",
    location: "",
    notes: "",
    dateAnalysis: "",
    remark: "",
    requestedBy: "",
    departmentId: undefined,
    samplerId: undefined,
    analystId: undefined,
  });

  // ── Tahap 2: Analisis & Penugasan Tim (Reviewer Lab) ──
  const [assignDialog, setAssignDialog] = useState(false);
  const [assignAnalystId, setAssignAnalystId] = useState<number | null>(null);
  const [assignSamplerId, setAssignSamplerId] = useState<number | null>(null);
  const [assignSaving, setAssignSaving] = useState(false);

  // ── Penolakan: alasan wajib, jadi tidak cukup confirm dialog biasa ──
  const [rejectDialog, setRejectDialog] = useState(false);
  const [rejectReason, setRejectReason] = useState("");

  // ── Jejak audit ──
  const [auditTrail, setAuditTrail] = useState<AuditLogDto[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const [auditOpen, setAuditOpen] = useState(false);

  // Analysis detail dialog state
  const [detailDialog, setDetailDialog] = useState(false);
  const [editingDetail, setEditingDetail] = useState<AnalysisDetailDto | null>(null);
  const [detailForm, setDetailForm] = useState<AnalysisDetailPayload>({ ...emptyDetail });
  const [detailSaving, setDetailSaving] = useState(false);

  // Cascading dropdown data
  const [parameters, setParameters] = useState<LabParameter[]>([]);
  const [testTypes, setTestTypes] = useState<LabTestType[]>([]);
  const [methods, setMethods] = useState<LabMethod[]>([]);
  const [standards, setStandards] = useState<LabStandard[]>([]);
  const [units, setUnits] = useState<LabUnit[]>([]);

  const fetchReport = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const result = await laboratoryReportService.get(Number(id));
      setReport(result.data);
    } catch {
      setReport(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { fetchReport(); }, [fetchReport]);

  // Jejak audit hanya diambil saat panelnya dibuka — isinya bisa panjang dan
  // tidak dibutuhkan untuk pekerjaan harian analis.
  const fetchAuditTrail = useCallback(async () => {
    if (!id) return;
    setAuditLoading(true);
    try {
      const result = await laboratoryReportService.auditTrail(Number(id));
      setAuditTrail(result.data ?? []);
    } catch {
      setAuditTrail([]);
    } finally {
      setAuditLoading(false);
    }
  }, [id]);

  const toggleAudit = () => {
    const next = !auditOpen;
    setAuditOpen(next);
    if (next) void fetchAuditTrail();
  };

  // Load departments & kandidat Analis/Sampler Lab (dibatasi per role)
  useEffect(() => {
    labConfigService.getMasterData().then((res) => {
      const d = res.data as Record<string, unknown[]>;
      if (d?.departments) setDepartments(d.departments as LabDepartment[]);
    }).catch(() => {});
    userRoleService.getUsersDropdownByRoleCode("ANALIS_LAB").then(setAnalystOptions).catch(() => {});
    userRoleService.getUsersDropdownByRoleCode("SAMPLER_LAB").then(setSamplerOptions).catch(() => {});
  }, []);

  // Start inline editing: populate form from current report
  const startEditMain = () => {
    if (!report) return;
    setEditMainForm({
      dateSampling: report.dateSampling || "",
      location: report.location || "",
      notes: report.notes || "",
      dateAnalysis: report.dateAnalysis || "",
      remark: report.remark || "",
      requestedBy: report.requestedBy || "",
      departmentId: report.departmentId || undefined,
      samplerId: report.samplerId || undefined,
      analystId: report.analystId || undefined,
    });
    setIsEditingMain(true);
  };

  const cancelEditMain = () => setIsEditingMain(false);

  const saveMainReport = async () => {
    if (!report) return;
    if (!editMainForm.dateSampling || !editMainForm.location?.trim()) {
      toast.current?.show({ severity: "warn", summary: "Validasi", detail: "Tanggal sampling dan lokasi wajib diisi", life: 3000 });
      return;
    }
    setMainSaving(true);
    try {
      await laboratoryReportService.update(report.id, editMainForm);
      toast.current?.show({ severity: "success", summary: "Berhasil", detail: "Laporan berhasil diperbarui", life: 2000 });
      setIsEditingMain(false);
      await fetchReport();
    } catch {
      toast.current?.show({ severity: "error", summary: "Error", detail: "Gagal menyimpan perubahan", life: 3000 });
    } finally {
      setMainSaving(false);
    }
  };

  // Load master parameters on mount
  useEffect(() => {
    labConfigService.getMasterData().then((res) => {
      const d = res.data as Record<string, unknown[]>;
      if (d?.parameters) setParameters(d.parameters as LabParameter[]);
      if (d?.units) setUnits(d.units as LabUnit[]);
    }).catch(() => {});
  }, []);

  // Cascading: parameter → testTypes
  const loadDependencies = async (params: { parameterId?: number; testTypeId?: number; methodId?: number }) => {
    try {
      const res = await labConfigService.getDependencies(params);
      const d = res.data as Record<string, unknown[]>;
      if (params.parameterId && !params.testTypeId) {
        setTestTypes((d?.testTypes as LabTestType[]) || []);
        setMethods([]);
        setStandards([]);
      } else if (params.testTypeId && !params.methodId) {
        setMethods((d?.methods as LabMethod[]) || []);
        setStandards([]);
      } else if (params.methodId) {
        setStandards((d?.standards as LabStandard[]) || []);
      }
    } catch { /* ignore */ }
  };

  // ── Gating per peran lab untuk tiap tahap alur ──
  const myRoles: string[] = user?.roles ?? [];
  const hasRole = (...roles: string[]) => roles.some((r) => myRoles.includes(r));
  const isLabAdmin = hasRole("SUPER_ADMIN", "ADMIN_IT", "KEPALA_LAB");
  // Tahap 2 — penugasan tim oleh Reviewer Lab
  const canAssignTeam = isLabAdmin || hasRole("REVIEWER_LAB");
  // Tahap 3 — Sampler & Analis mengisi hasil, tetapi yang MENUTUP tahap
  // Tinjauan Teknis (dan menyerahkannya ke Manager Lab) adalah Reviewer Lab.
  const canDoReview = canReview && (isLabAdmin || hasRole("REVIEWER_LAB", "QUALITY_MANAGER"));
  // Tahap 4 — penyelesaian akhir oleh Manager Lab
  const canDoApprove = canApprove && (isLabAdmin || hasRole("MANAGER_LAB", "APPROVER_LAB", "QUALITY_MANAGER"));
  // Detail Analisis (Tambah/Edit/Hapus): hanya Analis/Sampler yang ditugaskan
  // pada tahap Analisis & Penugasan Tim, selama laporan belum ditutup/ditolak.
  const isAssignedTeamMember =
    !!user && !!report && (Number(user.id) === report.analystId || Number(user.id) === report.samplerId);
  const isReportFinalized = report?.status === "Close" || report?.status === "Rejected";
  const canManageAnalysisDetails = !isReportFinalized && (isLabAdmin || isAssignedTeamMember);
  // Tiap peran hanya mengisi kolom hasilnya sendiri; admin lab boleh keduanya.
  const canEditSamplerResult =
    !isReportFinalized && (isLabAdmin || (!!user && Number(user.id) === report?.samplerId));
  const canEditAnalysisResult =
    !isReportFinalized && (isLabAdmin || (!!user && Number(user.id) === report?.analystId));

  // Standar yang sedang dipilih di form detail — sumber teks batas spesifikasi
  // yang ditampilkan saat analis mengetik hasil.
  const selectedStandard = standards.find((s) => s.id === detailForm.standardId);

  const openAssignDialog = () => {
    setAssignAnalystId(report?.analystId ?? null);
    setAssignSamplerId(report?.samplerId ?? null);
    setAssignDialog(true);
  };

  const handleAssignTeam = async () => {
    if (!report || !assignAnalystId || !assignSamplerId) return;
    setAssignSaving(true);
    try {
      await laboratoryReportService.assignTeam(report.id, assignAnalystId, assignSamplerId);
      await fetchReport();
      setAssignDialog(false);
      toast.current?.show({ severity: "success", summary: "Berhasil", detail: "Analis & Sampler berhasil ditugaskan", life: 3000 });
    } catch {
      toast.current?.show({ severity: "error", summary: "Error", detail: "Gagal menugaskan tim", life: 3000 });
    } finally {
      setAssignSaving(false);
    }
  };

  const handleAction = async (action: "review" | "acknowledge" | "reject") => {
    if (!report || !user) return;

    // Penolakan punya dialognya sendiri: alasannya wajib dan ikut tersimpan di
    // jejak audit, jadi tidak bisa diwakili konfirmasi ya/tidak.
    if (action === "reject") {
      setRejectReason("");
      setRejectDialog(true);
      return;
    }
    // Ringkas kelengkapan hasil supaya Reviewer sadar bila masih ada yang kosong
    // sebelum menyerahkan laporan ke Manager Lab.
    const details = report.analysisDetails || [];
    const samplerDone = details.filter((d) => d.resultSampler?.trim()).length;
    const analysisDone = details.filter((d) => (d.resultAnalysis || d.result)?.trim()).length;
    const completeness = details.length
      ? `Kelengkapan hasil: Sampler ${samplerDone}/${details.length}, Analisis ${analysisDone}/${details.length}.`
      : "Laporan ini belum memiliki detail analisis.";
    const labels: Record<string, { msg: string; icon: string; accept: string }> = {
      review: {
        msg: `${completeness} Selesaikan Tinjauan Teknis dan serahkan ke Manager Lab untuk Penyelesaian Akhir?`,
        icon: "pi pi-check-circle",
        accept: "Ya, Selesaikan",
      },
      acknowledge: { msg: "Setujui dan tutup laporan ini?", icon: "pi pi-check-circle", accept: "Ya, Setujui" },
    };
    const l = labels[action];
    confirmDialog({
      message: l.msg,
      header: "Konfirmasi",
      icon: l.icon,
      acceptLabel: l.accept,
      rejectLabel: "Batal",
      accept: async () => {
        setActionLoading(true);
        try {
          if (action === "review") await laboratoryReportService.review(report.id, Number(user.id));
          else await laboratoryReportService.acknowledge(report.id, Number(user.id));
          await fetchReport();
          const actionLabel = action === "review" ? "direview" : "disetujui";
          toast.current?.show({ severity: "success", summary: "Berhasil", detail: `Laporan berhasil ${actionLabel}`, life: 3000 });
        } catch (err) {
          toast.current?.show({
            severity: "error",
            summary: "Error",
            detail: serverMessage(err) ?? `Gagal melakukan aksi ${action}`,
            life: 5000,
          });
        } finally {
          setActionLoading(false);
        }
      },
    });
  };

  const submitReject = async () => {
    if (!report) return;
    if (!rejectReason.trim()) {
      toast.current?.show({ severity: "warn", summary: "Validasi", detail: "Alasan penolakan wajib diisi", life: 3000 });
      return;
    }
    setActionLoading(true);
    try {
      await laboratoryReportService.reject(report.id, rejectReason.trim());
      setRejectDialog(false);
      await fetchReport();
      if (auditOpen) await fetchAuditTrail();
      toast.current?.show({ severity: "success", summary: "Berhasil", detail: "Laporan berhasil ditolak", life: 3000 });
    } catch (err) {
      toast.current?.show({
        severity: "error",
        summary: "Error",
        detail: serverMessage(err) ?? "Gagal menolak laporan",
        life: 5000,
      });
    } finally {
      setActionLoading(false);
    }
  };

  // === Analysis Detail CRUD ===
  const openNewDetail = () => {
    setEditingDetail(null);
    setDetailForm({ ...emptyDetail });
    setTestTypes([]);
    setMethods([]);
    setStandards([]);
    setDetailDialog(true);
  };

  const openEditDetail = (detail: AnalysisDetailDto) => {
    setEditingDetail(detail);
    setDetailForm({
      parameterId: detail.parameterId ?? undefined,
      testTypeId: detail.testTypeId ?? undefined,
      methodId: detail.methodId ?? undefined,
      standardId: detail.standardId ?? undefined,
      unitId: detail.unitId ?? undefined,
      // Data lama hanya punya `result` — tampilkan sebagai hasil analisis.
      resultSampler: detail.resultSampler || "",
      resultAnalysis: detail.resultAnalysis || detail.result || "",
      description: detail.description || "",
      changeReason: "",
    });
    setDetailDialog(true);
    // Load dependent dropdowns in parallel — they don't depend on each other's
    // resolved values, only on the IDs we already have on `detail`.
    void (async () => {
      const calls: Promise<{ data: unknown }>[] = [];
      if (detail.parameterId) calls.push(labConfigService.getDependencies({ parameterId: detail.parameterId }));
      if (detail.testTypeId)  calls.push(labConfigService.getDependencies({ testTypeId: detail.testTypeId }));
      if (detail.methodId)    calls.push(labConfigService.getDependencies({ methodId: detail.methodId }));
      const results = await Promise.allSettled(calls);
      results.forEach((r) => {
        if (r.status !== "fulfilled") return;
        const d = r.value.data as Record<string, unknown[]> | null;
        if (!d) return;
        if (d.testTypes) setTestTypes(d.testTypes as LabTestType[]);
        if (d.methods)   setMethods(d.methods as LabMethod[]);
        if (d.standards) setStandards(d.standards as LabStandard[]);
      });
    })();
  };

  const saveDetail = async () => {
    if (!report) return;
    setDetailSaving(true);
    try {
      if (editingDetail) {
        await analysisDetailService.update(report.id, editingDetail.id, detailForm);
        toast.current?.show({ severity: "success", summary: "Berhasil", detail: "Detail analisis diperbarui", life: 2000 });
      } else {
        await analysisDetailService.create(report.id, detailForm);
        toast.current?.show({ severity: "success", summary: "Berhasil", detail: "Detail analisis ditambahkan", life: 2000 });
      }
      setDetailDialog(false);
      await fetchReport();
      if (auditOpen) await fetchAuditTrail();
    } catch (err) {
      // Mis. "Alasan perubahan wajib diisi saat mengoreksi hasil yang sudah terisi."
      toast.current?.show({
        severity: "error",
        summary: "Error",
        detail: serverMessage(err) ?? "Gagal menyimpan detail analisis",
        life: 5000,
      });
    } finally {
      setDetailSaving(false);
    }
  };

  const confirmDeleteDetail = (detail: AnalysisDetailDto) => {
    if (!report) return;
    confirmDialog({
      message: `Hapus detail analisis "${detail.parameterName || "ini"}"?`,
      header: "Konfirmasi Hapus",
      icon: "pi pi-trash",
      acceptLabel: "Hapus",
      rejectLabel: "Batal",
      acceptClassName: "p-button-danger",
      accept: async () => {
        try {
          await analysisDetailService.delete(report.id, detail.id);
          toast.current?.show({ severity: "success", summary: "Berhasil", detail: "Detail analisis dihapus", life: 2000 });
          await fetchReport();
        } catch {
          toast.current?.show({ severity: "error", summary: "Error", detail: "Gagal menghapus detail analisis", life: 3000 });
        }
      },
    });
  };

  // === Render ===
  if (loading) {
    return (
      <div className="flex align-items-center justify-content-center" style={{ minHeight: "40vh" }}>
        <ProgressSpinner strokeWidth="3" />
      </div>
    );
  }

  if (!report) {
    return (
      <div className="flex flex-column gap-4">
        <h2 className="text-2xl font-bold text-800 m-0">Detail Laporan</h2>
        <Card className="shadow-1 border-round-xl">
          <p className="text-500">Laporan tidak ditemukan.</p>
          <Button label="Kembali" icon="pi pi-arrow-left" severity="secondary" outlined onClick={() => navigate("/dashboard/lims/laboratory-reports")} />
        </Card>
      </div>
    );
  }

  const InfoRow = ({ label, value }: { label: string; value: React.ReactNode }) => (
    <div className="flex flex-column gap-1">
      <span className="text-xs text-500 font-medium">{label}</span>
      <span className="text-sm text-800">{value || "-"}</span>
    </div>
  );

  const detailActionTemplate = (rowData: AnalysisDetailDto) => {
    if (!canManageAnalysisDetails) return null;
    return (
      <div className="flex gap-1">
        <Button icon="pi pi-pencil" rounded text severity="info" size="small" onClick={() => openEditDetail(rowData)} tooltip="Edit" tooltipOptions={{ position: "top" }} />
        <Button icon="pi pi-trash" rounded text severity="danger" size="small" onClick={() => confirmDeleteDetail(rowData)} tooltip="Hapus" tooltipOptions={{ position: "top" }} />
      </div>
    );
  };

  return (
    <div className="flex flex-column gap-4">
      <Toast ref={toast} />
      <ConfirmDialog />

      {/* Header */}
      <div className="flex align-items-center gap-3">
        <Button icon="pi pi-arrow-left" rounded text severity="secondary" onClick={() => navigate("/dashboard/lims/laboratory-reports")} />
        <div className="flex-1">
          <h2 className="text-2xl font-bold text-800 m-0">Detail Laporan Laboratorium</h2>
          <p className="text-sm text-500 mt-1 mb-0">Dashboard &gt; LIMS &gt; Laporan Laboratorium &gt; Detail</p>
        </div>
        <div className="flex gap-2">
          {/* SEMENTARA: tombol edit selalu tampil */}
          {isEditingMain ? (
            <>
              <Button
                label="Batal"
                icon="pi pi-times"
                severity="secondary"
                outlined
                onClick={cancelEditMain}
                className="font-semibold border-round-xl"
                style={{ gap: '0.6rem', padding: '0.6rem 1.25rem' }}
              />
              <Button
                label={mainSaving ? "Menyimpan..." : "Simpan Perubahan"}
                icon={mainSaving ? "pi pi-spin pi-spinner" : "pi pi-check"}
                loading={mainSaving}
                onClick={saveMainReport}
                className="font-semibold border-round-xl shadow-2"
                style={{
                  background: 'linear-gradient(135deg, #059669 0%, #065f46 100%)',
                  border: 'none',
                  gap: '0.6rem',
                  padding: '0.6rem 1.5rem',
                }}
              />
            </>
          ) : (
            <Button
              label="Edit Laporan"
              icon="pi pi-pencil"
              onClick={startEditMain}
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
          )}
        </div>
      </div>

      <div className="grid">
        {/* Main detail */}
        <div className="col-12 lg:col-8">
          <Card className="shadow-1 border-round-xl" title={
            <div className="flex align-items-center justify-content-between">
              <span>{report.noReport}</span>
              <Tag value={report.status} severity={statusSeverity(report.status)} />
            </div>
          }>
            {isEditingMain ? (
              /* ── MODE EDIT INLINE (SEMENTARA) ── */
              <div className="flex flex-column gap-3">
                <div className="grid">
                  <div className="col-12 md:col-6">
                    <label className="text-xs text-500 font-medium block mb-1">Tanggal Sampling <span className="text-red-500">*</span></label>
                    <Calendar
                      value={editMainForm.dateSampling ? new Date(editMainForm.dateSampling) : null}
                      onChange={(e) => {
                        const d = e.value as Date | null;
                        setEditMainForm({ ...editMainForm, dateSampling: d ? toLocalISODate(d) : "" });
                      }}
                      dateFormat="dd M yy"
                      showIcon
                      placeholder="Pilih tanggal sampling"
                      className="w-full"
                    />
                  </div>
                  <div className="col-12 md:col-6">
                    <label className="text-xs text-500 font-medium block mb-1">Tanggal Analisis</label>
                    <Calendar
                      value={editMainForm.dateAnalysis ? new Date(editMainForm.dateAnalysis) : null}
                      onChange={(e) => {
                        const d = e.value as Date | null;
                        setEditMainForm({ ...editMainForm, dateAnalysis: d ? toLocalISODate(d) : "" });
                      }}
                      dateFormat="dd M yy"
                      showIcon
                      placeholder="Pilih tanggal analisis"
                      className="w-full"
                    />
                  </div>
                  <div className="col-12 md:col-6">
                    <label className="text-xs text-500 font-medium block mb-1">Lokasi <span className="text-red-500">*</span></label>
                    <InputText
                      value={editMainForm.location}
                      onChange={(e) => setEditMainForm({ ...editMainForm, location: e.target.value })}
                      placeholder="Lokasi sampling"
                      className="w-full"
                    />
                  </div>
                  <div className="col-12 md:col-6">
                    <label className="text-xs text-500 font-medium block mb-1">Departemen</label>
                    <Dropdown
                      value={editMainForm.departmentId}
                      options={departments.map((d) => ({ label: d.name, value: d.id }))}
                      onChange={(e) => setEditMainForm({ ...editMainForm, departmentId: e.value })}
                      placeholder="Pilih departemen"
                      className="w-full"
                      showClear
                      filter
                    />
                  </div>
                  <div className="col-12 md:col-6">
                    <label className="text-xs text-500 font-medium block mb-1">Diminta Oleh</label>
                    <InputText
                      value={editMainForm.requestedBy || ""}
                      onChange={(e) => setEditMainForm({ ...editMainForm, requestedBy: e.target.value })}
                      placeholder="Nama pemohon"
                      className="w-full"
                    />
                  </div>
                  <div className="col-12 md:col-6">
                    <label className="text-xs text-500 font-medium block mb-1">Sampler (Pengambil Sampel)</label>
                    <Dropdown
                      value={editMainForm.samplerId}
                      options={samplerOptions.map((u) => ({ label: u.name, value: u.id }))}
                      onChange={(e) => setEditMainForm({ ...editMainForm, samplerId: e.value })}
                      placeholder="Pilih sampler"
                      className="w-full"
                      showClear
                      filter
                      filterPlaceholder="Cari nama..."
                    />
                  </div>
                  <div className="col-12 md:col-6">
                    <label className="text-xs text-500 font-medium block mb-1">Analis</label>
                    <Dropdown
                      value={editMainForm.analystId}
                      options={analystOptions.map((u) => ({ label: u.name, value: u.id }))}
                      onChange={(e) => setEditMainForm({ ...editMainForm, analystId: e.value })}
                      placeholder="Pilih analis"
                      className="w-full"
                      showClear
                      filter
                      filterPlaceholder="Cari nama..."
                    />
                  </div>
                  <div className="col-12">
                    <label className="text-xs text-500 font-medium block mb-1">Catatan</label>
                    <InputTextarea
                      value={editMainForm.notes || ""}
                      onChange={(e) => setEditMainForm({ ...editMainForm, notes: e.target.value })}
                      rows={3}
                      autoResize
                      placeholder="Catatan tambahan..."
                      className="w-full"
                    />
                  </div>
                  <div className="col-12">
                    <label className="text-xs text-500 font-medium block mb-1">Keterangan</label>
                    <InputTextarea
                      value={editMainForm.remark || ""}
                      onChange={(e) => setEditMainForm({ ...editMainForm, remark: e.target.value })}
                      rows={2}
                      autoResize
                      placeholder="Keterangan tambahan..."
                      className="w-full"
                    />
                  </div>
                </div>
              </div>
            ) : (
              /* ── MODE VIEW ── */
              <div className="grid">
                <div className="col-6"><InfoRow label="Tanggal Sampling" value={report.dateSampling} /></div>
                <div className="col-6"><InfoRow label="Lokasi" value={report.location} /></div>
                <div className="col-6"><InfoRow label="Departemen" value={report.departmentName} /></div>
                <div className="col-6"><InfoRow label="Tanggal Analisis" value={report.dateAnalysis} /></div>
                <div className="col-6"><InfoRow label="Diminta Oleh" value={report.requestedBy} /></div>
                <div className="col-6"><InfoRow label="Sampler" value={report.samplerName} /></div>
                <div className="col-6"><InfoRow label="Analis" value={report.analystName} /></div>
                <div className="col-6"><InfoRow label="Dibuat" value={report.createdAt} /></div>
                {report.reviewedAt && <div className="col-6"><InfoRow label="Direview" value={report.reviewedAt} /></div>}
                {report.acknowledgeAt && <div className="col-6"><InfoRow label="Disetujui" value={report.acknowledgeAt} /></div>}
                {report.rejectedAt && (
                  <div className="col-12">
                    <InfoRow
                      label="Ditolak"
                      value={`${report.rejectedByName ?? "-"} · ${new Date(report.rejectedAt).toLocaleString("id-ID")}`}
                    />
                  </div>
                )}
                {report.rejectionReason && (
                  <div className="col-12">
                    <InfoRow label="Alasan Penolakan" value={report.rejectionReason} />
                  </div>
                )}
                <div className="col-12"><InfoRow label="Catatan" value={report.notes ? <ChemText text={report.notes} /> : null} /></div>
                {report.remark && <div className="col-12"><InfoRow label="Keterangan" value={<ChemText text={report.remark} />} /></div>}
              </div>
            )}
          </Card>

          {/* Analysis Details */}
          <Card className="shadow-1 border-round-xl mt-4" title={
            <div className="flex align-items-center justify-content-between">
              <span>Detail Analisis</span>
              {canManageAnalysisDetails && (
                <Button label="Tambah" icon="pi pi-plus" size="small" onClick={openNewDetail} />
              )}
            </div>
          }>
            {!!report.outOfSpecCount && report.outOfSpecCount > 0 && (
              <div className="mb-3 p-3 border-round-lg surface-100 border-left-3 border-red-500 flex align-items-center gap-2">
                <i className="pi pi-exclamation-triangle text-red-600" />
                <span className="text-sm">
                  <b>{report.outOfSpecCount} hasil di luar spesifikasi</b> pada laporan ini.
                  Tiap temuan otomatis membuka NCR — nomornya tertera di kolom Spesifikasi.
                </span>
              </div>
            )}
            <DataTable
              value={report.analysisDetails || []}
              emptyMessage="Belum ada detail analisis"
              stripedRows
              size="small"
            >
              <Column header="No" body={(_: unknown, opts: { rowIndex: number }) => opts.rowIndex + 1} style={{ width: "3rem" }} />
              <Column field="parameterName" header="Parameter" body={(d: AnalysisDetailDto) => d.parameterName || d.parameterId || "-"} />
              <Column field="testTypeName" header="Jenis Uji" body={(d: AnalysisDetailDto) => d.testTypeName || d.testTypeId || "-"} />
              <Column field="methodName" header="Metode" body={(d: AnalysisDetailDto) => d.methodName || d.methodId || "-"} />
              <Column
                field="standardMeasure"
                header="Standar"
                body={(d: AnalysisDetailDto) => (d.standardId || d.standardMeasure) ? (
                  <div className="flex flex-column">
                    <span>{d.standardMeasure || d.standardId}</span>
                    {d.standardLimitType && d.standardLimitType !== "QUALITATIVE" && (
                      <small className="text-500">{formatSpecLimit(d)}</small>
                    )}
                  </div>
                ) : "-"}
              />
              <Column field="unitName" header="Satuan" body={(d: AnalysisDetailDto) => d.unitName || d.unitId || "-"} />
              <Column header="Hasil Sampler" body={(d: AnalysisDetailDto) => d.resultSampler || "-"} />
              {/* Data lama hanya punya `result` — tampilkan di kolom analisis. */}
              <Column header="Hasil Analisis" body={(d: AnalysisDetailDto) => d.resultAnalysis || d.result || "-"} />
              <Column
                header="Spesifikasi"
                body={(d: AnalysisDetailDto) => (
                  <div className="flex flex-column gap-1 align-items-start">
                    <Tag
                      value={SPEC_LABEL[d.specStatus ?? "NOT_EVALUATED"]}
                      severity={SPEC_SEVERITY[d.specStatus ?? "NOT_EVALUATED"]}
                      rounded
                    />
                    {d.ncrNumber && (
                      <Button
                        label={`NCR ${d.ncrNumber}`}
                        icon="pi pi-external-link"
                        iconPos="right"
                        link
                        size="small"
                        className="p-0 text-xs"
                        onClick={() => navigate(`/dashboard/irms/safety-observations/${d.ncrId}`)}
                      />
                    )}
                  </div>
                )}
                style={{ width: "12rem" }}
              />
              <Column field="description" header="Deskripsi" body={(d: AnalysisDetailDto) => d.description ? <ChemText text={d.description} /> : "-"} />
              <Column header="Aksi" body={detailActionTemplate} style={{ width: "6rem" }} />
            </DataTable>
          </Card>

          {/* Jejak Audit — siapa mengubah apa, kapan, dan kenapa */}
          <Card className="shadow-1 border-round-xl mt-4" title={
            <div className="flex align-items-center justify-content-between">
              <span>Jejak Audit</span>
              <Button
                label={auditOpen ? "Sembunyikan" : "Tampilkan"}
                icon={auditOpen ? "pi pi-chevron-up" : "pi pi-chevron-down"}
                size="small"
                text
                onClick={toggleAudit}
              />
            </div>
          }>
            {!auditOpen ? (
              <p className="text-sm text-500 m-0">
                Riwayat perubahan laporan dan detail analisisnya, termasuk alasan koreksi hasil.
              </p>
            ) : auditLoading ? (
              <div className="flex justify-content-center p-3"><ProgressSpinner strokeWidth="3" style={{ width: 40, height: 40 }} /></div>
            ) : (
              <DataTable value={auditTrail} emptyMessage="Belum ada jejak perubahan" stripedRows size="small" paginator rows={10}>
                <Column
                  header="Waktu"
                  body={(a: AuditLogDto) => new Date(a.createdAt).toLocaleString("id-ID")}
                  style={{ width: "12rem" }}
                />
                <Column header="Oleh" body={(a: AuditLogDto) => a.actorName || "-"} style={{ width: "12rem" }} />
                <Column
                  header="Aksi"
                  body={(a: AuditLogDto) => (
                    <Tag
                      value={AUDIT_ACTION_LABEL[a.action] ?? a.action}
                      severity={a.action === "DELETE" ? "danger" : a.action === "CREATE" ? "success" : "info"}
                      rounded
                    />
                  )}
                  style={{ width: "8rem" }}
                />
                <Column
                  header="Bagian"
                  body={(a: AuditLogDto) => {
                    const entity = a.entityType === "ANALYSIS_DETAIL" ? "Detail analisis" : "Laporan";
                    const field = a.fieldName ? (AUDIT_FIELD_LABEL[a.fieldName] ?? a.fieldName) : null;
                    return field ? `${entity} · ${field}` : entity;
                  }}
                />
                <Column
                  header="Perubahan"
                  body={(a: AuditLogDto) => {
                    if (a.action === "CREATE" || a.action === "DELETE") return a.oldValue || a.newValue || "-";
                    return (
                      <span>
                        <span className="text-500 line-through">{a.oldValue ?? "(kosong)"}</span>
                        <i className="pi pi-arrow-right mx-2 text-xs" />
                        <b>{a.newValue ?? "(kosong)"}</b>
                      </span>
                    );
                  }}
                />
                <Column header="Alasan" body={(a: AuditLogDto) => a.reason || "-"} />
              </DataTable>
            )}
          </Card>
        </div>

        {/* Sidebar - Approval Timeline */}
        <div className="col-12 lg:col-4">
          <Card title="Alur Persetujuan" className="shadow-1 border-round-xl">
            <ApprovalTimeline
              stages={LAB_STAGES}
              currentStageIndex={getLabStageIndex(report.status)}
              isRejected={report.status === "Rejected"}
              getStageInfo={(idx: number): StageInfo => {
                const fmtDate = (d?: string | null) => d ? new Date(d).toLocaleDateString("id-ID", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }) : null;
                switch (idx) {
                  case 0: return { person: report.reporterName ?? null, date: fmtDate(report.createdAt) };
                  case 1: return {
                    // Tim yang ditugaskan: Analis Lab & Sampler Lab
                    person: [report.analystName, report.samplerName].filter(Boolean).join(' · ') || null,
                    date: null,
                  };
                  case 2: {
                    // Tinjauan Teknis: tunjukkan kelengkapan input kedua peran
                    // supaya reviewer tahu apakah data sudah siap ditinjau.
                    const details = report.analysisDetails || [];
                    const samplerDone = details.filter((d) => d.resultSampler?.trim()).length;
                    const analysisDone = details.filter((d) => (d.resultAnalysis || d.result)?.trim()).length;
                    return {
                      person: report.reviewerName ?? null,
                      date: fmtDate(report.reviewedAt),
                      note: details.length
                        ? `Hasil Sampler ${samplerDone}/${details.length} · Hasil Analisis ${analysisDone}/${details.length}`
                        : "Belum ada detail analisis",
                      noteLabel: "Kelengkapan Hasil",
                      noteColor: details.length && samplerDone === details.length && analysisDone === details.length
                        ? "green"
                        : undefined,
                    };
                  }
                  case 3: return {
                    person: report.acknowledgerName ?? null,
                    date: fmtDate(report.acknowledgeAt),
                  };
                  default: return {};
                }
              }}
              getStageActions={(idx: number): StageAction[] => {
                const ci = getLabStageIndex(report.status);
                if (report.status === "Rejected" || report.status === "Close") return [];
                if (idx !== ci) return [];
                // Gating per peran lab (bukan hierarki atasan)
                switch (idx) {
                  case 1:
                    // Analisis & Penugasan Tim: Reviewer Lab menunjuk analis & sampler
                    return canAssignTeam ? [
                      { action: "assign-team", label: "Tugaskan Analis & Sampler", icon: "pi pi-users", severity: "success" },
                    ] : [];
                  case 2:
                    // Tinjauan Teknis ditutup oleh Reviewer Lab → diserahkan ke Manager Lab
                    return canDoReview ? [
                      { action: "review", label: "Selesaikan Tinjauan Teknis", icon: "pi pi-verified", severity: "success" },
                    ] : [];
                  case 3:
                    // Penyelesaian Akhir: Manager Lab
                    return canDoApprove ? [
                      { action: "acknowledge", label: "Setujui", icon: "pi pi-check-circle", severity: "success" },
                    ] : [];
                  default: return [];
                }
              }}
              onAction={(action: string) => {
                if (action === "assign-team") { openAssignDialog(); return; }
                handleAction(action as "review" | "acknowledge" | "reject");
              }}
              rejectButton={
                report.status !== "Close" && report.status !== "Rejected" && canReject
                  ? { label: "Tolak Laporan", action: "reject" }
                  : null
              }
            />
            {report.status === "Close" && (
              <div className="mt-3">
                <Button
                  label="Export Certificate of Analysis"
                  icon="pi pi-file-pdf"
                  severity="success"
                  onClick={() => { exportCoaPdf(report); }}
                  className="w-full border-round-lg shadow-1 font-semibold"
                  style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }}
                />
              </div>
            )}
          </Card>

          <Card title="Informasi" className="shadow-1 border-round-xl mt-4">
            <div className="flex flex-column gap-2">
              <div className="flex justify-content-between">
                <span className="text-sm text-500">Total Detail</span>
                <span className="text-sm font-bold text-800">{report.analysisDetails?.length || 0}</span>
              </div>
              <Divider className="my-1" />
              <div className="flex justify-content-between">
                <span className="text-sm text-500">Status</span>
                <Tag value={report.status} severity={statusSeverity(report.status)} />
              </div>
            </div>
          </Card>
        </div>
      </div>

      {/* Analysis Detail Add/Edit Dialog */}
      {/* Tahap 2 — Analisis & Penugasan Tim (Reviewer Lab) */}
      <Dialog
        header="Analisis & Penugasan Tim (Reviewer Lab)"
        visible={assignDialog}
        onHide={() => setAssignDialog(false)}
        style={{ width: "32rem" }}
        modal
        footer={
          <div className="flex justify-content-end gap-2">
            <Button label="Batal" icon="pi pi-times" severity="secondary" outlined onClick={() => setAssignDialog(false)} />
            <Button
              label={assignSaving ? "Menyimpan..." : "Tugaskan"}
              icon={assignSaving ? "pi pi-spin pi-spinner" : "pi pi-check"}
              disabled={assignSaving || !assignAnalystId || !assignSamplerId}
              onClick={handleAssignTeam}
            />
          </div>
        }
      >
        <div className="flex flex-column gap-3">
          <div className="flex flex-column gap-1">
            <label className="text-sm font-semibold text-700">
              Analis Lab <span className="text-red-500">*</span>
            </label>
            <Dropdown
              value={assignAnalystId}
              options={analystOptions.map((u) => ({ label: u.name, value: u.id }))}
              onChange={(e) => setAssignAnalystId(e.value)}
              placeholder="Pilih analis lab..."
              filter
              showClear
              emptyMessage="Belum ada user dengan role ANALIS_LAB"
            />
          </div>
          <div className="flex flex-column gap-1">
            <label className="text-sm font-semibold text-700">
              Sampler Lab <span className="text-red-500">*</span>
            </label>
            <Dropdown
              value={assignSamplerId}
              options={samplerOptions.map((u) => ({ label: u.name, value: u.id }))}
              onChange={(e) => setAssignSamplerId(e.value)}
              placeholder="Pilih sampler lab..."
              filter
              showClear
              emptyMessage="Belum ada user dengan role SAMPLER_LAB"
            />
          </div>
          <small className="text-500">
            Setelah ditugaskan, laporan lanjut ke tahap Tinjauan Teknis oleh Sampler Lab &amp; Reviewer Lab.
          </small>
        </div>
      </Dialog>

      <Dialog
        header={editingDetail ? "Edit Detail Analisis" : "Tambah Detail Analisis"}
        visible={detailDialog}
        onHide={() => setDetailDialog(false)}
        style={{ width: "40rem" }}
        modal
        footer={
          <div className="flex justify-content-end gap-2">
            <Button label="Batal" icon="pi pi-times" severity="secondary" outlined className="border-round-lg shadow-1 font-semibold" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} onClick={() => setDetailDialog(false)} />
            <Button label={detailSaving ? "Menyimpan..." : "Simpan"} icon={detailSaving ? "pi pi-spin pi-spinner" : "pi pi-check"} disabled={detailSaving} className="border-round-lg shadow-1 font-semibold" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} onClick={saveDetail} />
          </div>
        }
      >
        <div className="flex flex-column gap-4 pt-2">
          {/* Parameter */}
          <div>
            <label className="block text-sm font-medium text-700 mb-2">Parameter</label>
            <Dropdown
              value={detailForm.parameterId}
              options={parameters.map((p) => ({ label: p.name, value: p.id }))}
              onChange={(e) => {
                setDetailForm({ ...detailForm, parameterId: e.value, testTypeId: undefined, methodId: undefined, standardId: undefined });
                setTestTypes([]);
                setMethods([]);
                setStandards([]);
                if (e.value) loadDependencies({ parameterId: e.value });
              }}
              placeholder="Pilih Parameter"
              className="w-full"
              showClear
              filter
            />
          </div>

          {/* Test Type */}
          <div>
            <label className="block text-sm font-medium text-700 mb-2">Jenis Uji</label>
            <Dropdown
              value={detailForm.testTypeId}
              options={testTypes.map((t) => ({ label: t.name, value: t.id }))}
              onChange={(e) => {
                setDetailForm({ ...detailForm, testTypeId: e.value, methodId: undefined, standardId: undefined });
                setMethods([]);
                setStandards([]);
                if (e.value) loadDependencies({ testTypeId: e.value });
              }}
              placeholder="Pilih Jenis Uji"
              className="w-full"
              showClear
              filter
              disabled={!detailForm.parameterId}
            />
          </div>

          <div className="grid">
            {/* Method */}
            <div className="col-6">
              <label className="block text-sm font-medium text-700 mb-2">Metode</label>
              <Dropdown
                value={detailForm.methodId}
                options={methods.map((m) => ({ label: m.name, value: m.id }))}
                onChange={(e) => {
                  setDetailForm({ ...detailForm, methodId: e.value, standardId: undefined });
                  setStandards([]);
                  if (e.value) loadDependencies({ methodId: e.value });
                }}
                placeholder="Pilih Metode"
                className="w-full"
                showClear
                disabled={!detailForm.testTypeId}
              />
            </div>
            {/* Standard */}
            <div className="col-6">
              <label className="block text-sm font-medium text-700 mb-2">Standar</label>
              <Dropdown
                value={detailForm.standardId}
                options={standards.map((s) => ({ label: s.measure, value: s.id }))}
                onChange={(e) => setDetailForm({ ...detailForm, standardId: e.value })}
                placeholder="Pilih Standar"
                className="w-full"
                showClear
                disabled={!detailForm.methodId}
              />
            </div>
          </div>

          {/* Unit */}
          <div>
            <label className="block text-sm font-medium text-700 mb-2">Satuan</label>
            <Dropdown
              value={detailForm.unitId}
              options={units.map((u) => ({ label: u.name, value: u.id }))}
              onChange={(e) => setDetailForm({ ...detailForm, unitId: e.value })}
              placeholder="Pilih Satuan"
              className="w-full"
              showClear
              filter
            />
          </div>

          {/* Hasil dipisah per peran: Sampler dan Analis mengisi kolomnya sendiri.
              Field peran lain tetap terlihat (read-only) agar konteksnya jelas. */}
          <div className="grid">
            <div className="col-12 md:col-6">
              <label className="block text-sm font-medium text-700 mb-2">
                Hasil Sampler
                {!canEditSamplerResult && <span className="text-xs text-500 font-normal"> (Sampler Lab)</span>}
              </label>
              <InputText
                value={detailForm.resultSampler || ""}
                onChange={(e) => setDetailForm({ ...detailForm, resultSampler: e.target.value })}
                placeholder={canEditSamplerResult ? "Hasil pengambilan sampel" : "Diisi oleh Sampler Lab"}
                className="w-full"
                disabled={!canEditSamplerResult}
              />
            </div>
            <div className="col-12 md:col-6">
              <label className="block text-sm font-medium text-700 mb-2">
                Hasil Analisis
                {!canEditAnalysisResult && <span className="text-xs text-500 font-normal"> (Analis Lab)</span>}
              </label>
              <InputText
                value={detailForm.resultAnalysis || ""}
                onChange={(e) => setDetailForm({ ...detailForm, resultAnalysis: e.target.value })}
                placeholder={canEditAnalysisResult ? "Hasil pengujian laboratorium" : "Diisi oleh Analis Lab"}
                className="w-full"
                disabled={!canEditAnalysisResult}
              />
              {selectedStandard && selectedStandard.limitType && selectedStandard.limitType !== "QUALITATIVE" && (
                <small className="text-500 block mt-1">
                  Batas: {describeStandardLimit(selectedStandard)} · hasil di bawah batas deteksi ditulis
                  seperti "&lt;0,01"
                </small>
              )}
            </div>
          </div>

          {/* Koreksi hasil yang sudah terisi wajib menyebut alasannya — tersimpan
              di jejak audit, bukan di baris hasilnya. */}
          {editingDetail && (editingDetail.resultSampler?.trim() || editingDetail.resultAnalysis?.trim()) && (
            <div>
              <label className="block text-sm font-medium text-700 mb-2">Alasan Koreksi</label>
              <InputTextarea
                value={detailForm.changeReason || ""}
                onChange={(e) => setDetailForm({ ...detailForm, changeReason: e.target.value })}
                rows={2}
                autoResize
                placeholder="Mis. salah baca skala buret, sampel diuji ulang..."
                className="w-full"
              />
              <small className="text-500">Wajib diisi bila Anda mengubah hasil yang sudah terisi.</small>
            </div>
          )}

          {/* Description */}
          <div>
            <label className="block text-sm font-medium text-700 mb-2">Deskripsi</label>
            <InputTextarea
              value={detailForm.description || ""}
              onChange={(e) => setDetailForm({ ...detailForm, description: e.target.value })}
              rows={2}
              autoResize
              placeholder="Deskripsi tambahan..."
              className="w-full"
            />
          </div>
        </div>
      </Dialog>

      {/* Penolakan laporan — alasan wajib dan tersimpan di jejak audit */}
      <Dialog
        header="Tolak Laporan"
        visible={rejectDialog}
        onHide={() => setRejectDialog(false)}
        style={{ width: "32rem" }}
        breakpoints={{ "960px": "90vw" }}
        footer={
          <div className="flex justify-content-end gap-2">
            <Button label="Batal" icon="pi pi-times" severity="secondary" outlined onClick={() => setRejectDialog(false)} />
            <Button
              label="Tolak Laporan"
              icon="pi pi-times-circle"
              severity="danger"
              loading={actionLoading}
              disabled={!rejectReason.trim()}
              onClick={submitReject}
            />
          </div>
        }
      >
        <p className="text-sm text-600 mt-0">
          Laporan {report.noReport} akan berstatus <b>Ditolak</b>. Alasannya dikirim ke tim lab dan
          tercatat permanen di jejak audit.
        </p>
        <label className="block text-sm font-medium text-700 mb-2">
          Alasan Penolakan<span className="ml-1 text-red-500">*</span>
        </label>
        <InputTextarea
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
          rows={4}
          autoResize
          autoFocus
          maxLength={2000}
          placeholder="Mis. hasil pH belum diisi, metode yang dipakai tidak sesuai permintaan..."
          className="w-full"
        />
      </Dialog>
    </div>
  );
};

export default LaboratoryReportDetail;
