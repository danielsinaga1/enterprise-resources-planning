import { useEffect, useState, useRef, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { decodeReportId } from '../../utils/reportId';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Tag } from 'primereact/tag';
import { Divider } from 'primereact/divider';
import { Toast } from 'primereact/toast';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { Dialog } from 'primereact/dialog';
import { Dropdown } from 'primereact/dropdown';
import { InputTextarea } from 'primereact/inputtextarea';
import { Calendar } from 'primereact/calendar';
import { ProgressSpinner } from 'primereact/progressspinner';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { incidentReportService, userRoleService } from '../../services';
import { fileUploadService } from '../../services/fileUploadService';
import { weatherService, type WeatherData } from '../../services/integrationService';
import { useAuth } from '../../context/AuthContext';
import { ApprovalBadge, RiskLevelBadge } from '../../components/irms/StatusBadges';
import ApprovalTimeline from '../../components/shared/ApprovalTimeline';
import type { StageAction, StageConfig, StageInfo } from '../../components/shared/ApprovalTimeline';
import type { IncidentReport, InvestigationDetail } from '../../types/irms';

// Incident Report 6-stage workflow
const INCIDENT_STAGES: StageConfig[] = [
  { key: 'new',              label: 'Pelaporan Awal',           icon: 'pi pi-file',         roleLabel: 'Pelapor' },
  { key: 'superintendent',   label: 'Review Superintendent',    icon: 'pi pi-search',       roleLabel: 'Superintendent' },
  { key: 'manager',          label: 'Persetujuan Manager',      icon: 'pi pi-check-circle', roleLabel: 'Manager' },
  { key: 'assignment',       label: 'Penugasan Pelaksana',      icon: 'pi pi-user-plus',    roleLabel: 'Manajer Dept. Tujuan' },
  { key: 'in_progress',      label: 'Tindak Lanjut',            icon: 'pi pi-wrench',       roleLabel: 'Pelaksana' },
  { key: 'done',             label: 'Penyelesaian',             icon: 'pi pi-check-circle', roleLabel: 'Penutupan' },
];

/** Check if the status means the report is fully completed (new workflow 'Done' + legacy 'Close'/'Closed') */
function isCompletedStatus(status: string): boolean {
  return status === 'Done' || status === 'Close' || status === 'Closed';
}

function getIncidentStageIndex(status: string): number {
  switch (status) {
    case 'New': return 1;                        // Menunggu superintendent
    case 'Pending Manager Approval': return 2;   // Superintendent selesai, menunggu manager
    case 'Fully Approved': return 3;             // Manager selesai, menunggu penugasan
    case 'In Progress': return 4;                // Tindak lanjut sedang berjalan
    case 'Done':                                 // Semua selesai
    case 'Close':                                // Legacy status dari kpi_irms
    case 'Closed': return 6;                     // Past last stage (5) → semua stage completed
    case 'Partially Approved': return 1;         // Dikembalikan oleh superintendent
    default: return 1;
  }
}

/* ============================================================
   INCIDENT REPORT DETAIL PAGE
   Maps from kpi_irms IncidentReport show.blade.php
   Includes approval workflow buttons and investigation details
   ============================================================ */
const IncidentReportDetail = () => {
  const { id: routeId } = useParams<{ id: string }>();
  // URL membawa id ter-obfuscate; API tetap bicara id numerik.
  const id = decodeReportId(routeId);
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);
  const { user } = useAuth();

  const [report, setReport] = useState<IncidentReport | null>(null);
  const [investigations, setInvestigations] = useState<InvestigationDetail[]>([]);
  const [loading, setLoading] = useState(true);
  const [reporterName, setReporterName] = useState<string | undefined>();
  const [reviewerName, setReviewerName] = useState<string | undefined>();
  const [acknowledgerName, setAcknowledgerName] = useState<string | undefined>();
  const [managerName, setManagerName] = useState<string | undefined>();
  const [reporterParentId, setReporterParentId] = useState<number | undefined>();
  const [reporterParentName, setReporterParentName] = useState<string | undefined>();
  const [reviewerParentId, setReviewerParentId] = useState<number | undefined>();
  const [reviewerParentName, setReviewerParentName] = useState<string | undefined>();
  const [assignedToName, setAssignedToName] = useState<string | undefined>();
  const [actionByName, setActionByName] = useState<string | undefined>();

  // Assign-subordinate dialog state
  const [assignDialogVisible, setAssignDialogVisible] = useState(false);
  const [subordinates, setSubordinates] = useState<{ id: number; name: string; npk?: string }[]>([]);
  const [selectedSubordinateId, setSelectedSubordinateId] = useState<number | null>(null);
  const [assignNotes, setAssignNotes] = useState('');
  const [assignSubmitting, setAssignSubmitting] = useState(false);

  // Lightbox state
  const [lightboxUrl, setLightboxUrl] = useState<string | null>(null);

  // Weather enrichment (Open-Meteo historical API)
  const [weather, setWeather] = useState<WeatherData | null>(null);

  // Follow-up dialog state
  const [followUpDialogVisible, setFollowUpDialogVisible] = useState(false);
  const [followUpForm, setFollowUpForm] = useState({
    perbaikanAwal: '',
    corrective: '',
    preventive: '',
    followUpNotes: '',
    dateAction: null as Date | null,
    directCost: '',
    copq: '',
  });
  const [followUpSubmitting, setFollowUpSubmitting] = useState(false);
  const [followUpAttachmentFiles, setFollowUpAttachmentFiles] = useState<File[]>([]);
  const followUpFileInputRef = useRef<HTMLInputElement>(null);

  const loadData = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const detailData = await incidentReportService.detail(Number(id));
      setReport(detailData.report);
      setInvestigations(detailData.investigations ?? []);
      setReporterName(detailData.reporterName);
      setReviewerName(detailData.reviewerName);
      setAcknowledgerName(detailData.acknowledgerName);
      setManagerName(detailData.managerName);
      setReporterParentId(detailData.reporterParentId);
      setReporterParentName(detailData.reporterParentName);
      setReviewerParentId(detailData.reviewerParentId);
      setReviewerParentName(detailData.reviewerParentName);
      setAssignedToName(detailData.assignedToName);
      setActionByName(detailData.actionByName);
      // Fire weather fetch in parallel — failure is silent (weather is supplementary)
      weatherService.forIncident(Number(id)).then(setWeather).catch(() => {});
    } catch (err) {
      console.error('Failed to load incident report', err);
      // Drop the previously loaded report: leaving it on screen kept the action buttons
      // live for a report the server no longer has (deleted elsewhere), and every action
      // then failed with a confusing error.
      setReport(null);
      const status = (err as { response?: { status?: number } })?.response?.status;
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: status === 404
          ? 'Laporan tidak ditemukan — mungkin sudah dihapus.'
          : 'Gagal memuat detail laporan',
        life: 5000,
      });
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Load subordinates when current user is the designated manager
  useEffect(() => {
    if (report?.designatedManagerId && report.designatedManagerId === user?.id && report.status === 'Fully Approved') {
      userRoleService.getSubordinates(user.id).then(setSubordinates).catch(console.error);
    }
  }, [report, user]);

  // Handle assign subordinate
  const handleAssignSubordinate = async () => {
    if (!selectedSubordinateId || !user?.id) return;
    setAssignSubmitting(true);
    try {
      await incidentReportService.assignSubordinate(Number(id), user.id, selectedSubordinateId, assignNotes || undefined);
      toast.current?.show({
        severity: 'success',
        summary: 'Berhasil',
        detail: 'Bawahan berhasil ditugaskan untuk menangani insiden',
        life: 3000,
      });
      setAssignDialogVisible(false);
      setSelectedSubordinateId(null);
      setAssignNotes('');
      loadData();
    } catch (err) {
      // Surface what the server said. A bare "Gagal menugaskan bawahan" hid the real
      // reason — e.g. the report had been deleted in another session, so every attempt
      // came back 404 with no way for the user to tell why.
      const detail = (err as { response?: { data?: { errorMessage?: string } } })
        ?.response?.data?.errorMessage ?? 'Gagal menugaskan bawahan';
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail,
        life: 5000,
      });
    } finally {
      setAssignSubmitting(false);
    }
  };

  // Handle follow-up submission
  const handleFollowUpSubmit = async () => {
    if (!user?.id) return;
    setFollowUpSubmitting(true);
    try {
      // Upload attachment files first (if any)
      let uploadedAttachments: string[] = []; 
      if (followUpAttachmentFiles.length > 0) {
        uploadedAttachments = await fileUploadService.uploadPhotos(followUpAttachmentFiles);
      }

      await incidentReportService.submitFollowUp(Number(id), user.id, {
        perbaikanAwal: followUpForm.perbaikanAwal || undefined,
        corrective: followUpForm.corrective || undefined,
        preventive: followUpForm.preventive || undefined,
        followUpNotes: followUpForm.followUpNotes || undefined,
        dateAction: followUpForm.dateAction ? followUpForm.dateAction.toISOString() : undefined,
        directCost: followUpForm.directCost ? Number(followUpForm.directCost) : undefined,
        copq: followUpForm.copq ? Number(followUpForm.copq) : undefined,
        followUpAttachments: uploadedAttachments.length > 0 ? JSON.stringify(uploadedAttachments) : undefined,
      });
      toast.current?.show({
        severity: 'success',
        summary: 'Berhasil',
        detail: 'Tindak lanjut berhasil diselesaikan',
        life: 3000,
      });
      setFollowUpDialogVisible(false);
      setFollowUpForm({ perbaikanAwal: '', corrective: '', preventive: '', followUpNotes: '', dateAction: null, directCost: '', copq: '' });
      setFollowUpAttachmentFiles([]);
      loadData();
    } catch {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Gagal mengirim tindak lanjut',
        life: 3000,
      });
    } finally {
      setFollowUpSubmitting(false);
    }
  };

  // Approval actions
  const handleApproval = (
    action: 'approve' | 'reject',
    level: 'superintendent' | 'manager'
  ) => {
    const actionLabel = action === 'approve' ? 'menyetujui' : 'menolak';
    const levelLabel = level === 'superintendent' ? 'Superintendent' : 'Manager';

    confirmDialog({
      message: `Apakah Anda yakin ingin ${actionLabel} laporan ini sebagai ${levelLabel}?`,
      header: 'Konfirmasi',
      icon: action === 'approve' ? 'pi pi-check-circle' : 'pi pi-times-circle',
      acceptClassName: action === 'approve' ? 'p-button-success' : 'p-button-danger',
      acceptLabel: action === 'approve' ? 'Setujui' : 'Tolak',
      rejectLabel: 'Batal',
      accept: async () => {
        try {
          if (level === 'superintendent') {
            if (action === 'approve') await incidentReportService.approveBySuperintendent(Number(id), user?.id);
            else await incidentReportService.rejectBySuperintendent(Number(id), user?.id);
          } else {
            if (action === 'approve') await incidentReportService.approveByManager(Number(id), user?.id);
            else await incidentReportService.rejectByManager(Number(id), user?.id);
          }
          toast.current?.show({
            severity: 'success',
            summary: 'Berhasil',
            detail: `Laporan berhasil di-${actionLabel}`,
            life: 3000,
          });
          loadData();
        } catch {
          toast.current?.show({
            severity: 'error',
            summary: 'Error',
            detail: `Gagal ${actionLabel} laporan`,
            life: 3000,
          });
        }
      },
    });
  };

  if (loading) {
    return (
      <div className="flex align-items-center justify-content-center" style={{ minHeight: '40vh' }}>
        <ProgressSpinner strokeWidth="3" />
      </div>
    );
  }

  if (!report) {
    return (
      <div className="flex gap-3 flex-column align-items-center justify-content-center" style={{ minHeight: '40vh' }}>
        <i className="text-5xl pi pi-exclamation-circle text-400" />
        <p className="text-500">Laporan tidak ditemukan</p>
        <Button label="Kembali" icon="pi pi-arrow-left" onClick={() => navigate('/dashboard/irms/incident-reports')} />
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

  return (
    <div className="flex gap-4 flex-column">
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
      <div className="flex flex-wrap gap-3 align-items-center justify-content-between">
        <div className="flex gap-3 align-items-center">
          <Button
            icon="pi pi-arrow-left"
            rounded
            text
            severity="secondary"
            onClick={() => navigate('/dashboard/irms/incident-reports')}
          />
          <div>
            <h2 className="m-0 text-2xl font-bold text-800">{report.reportNumber}</h2>
            <p className="mt-1 mb-0 text-sm text-500">{report.title}</p>
          </div>
        </div>
        <div className="flex gap-2">
          <Button
            label="Edit"
            icon="pi pi-pencil"
            severity="warning"
            rounded
            className="px-5 py-3 text-base font-semibold shadow-2"
            onClick={() => navigate(`/dashboard/irms/incident-reports/${routeId}/edit`)}
          />
        </div>
      </div>

      <div className="grid">
        {/* Left: Details */}
        <div className="col-12 lg:col-8">
          {/* Main Info */}
          <Card className="mb-4 shadow-1 border-round-xl">
            <div className="flex gap-3 flex-column">
              <div className="flex gap-2 align-items-center">
                <ApprovalBadge status={report.status} />
                <RiskLevelBadge
                  riskLevel={report.riskLevel}
                  severity={report.severity}
                  probability={report.probability}
                />
                {report.investigationRequired && (
                  <Tag value="Butuh Investigasi" severity="warning" icon="pi pi-search" className="text-xs" />
                )}
              </div>

              <Divider className="my-2" />

              {/* Description */}
              {report.description && (
                <div>
                  <h4 className="mt-0 mb-1 text-sm font-semibold text-500">Deskripsi</h4>
                  <p className="m-0 text-sm text-700 line-height-3">{report.description}</p>
                </div>
              )}

              {/* Photos */}
              {report.photos && (() => {
                try {
                  const photoList: string[] = JSON.parse(report.photos);
                  if (photoList.length === 0) return null;
                  return (
                    <div>
                      <h4 className="mt-0 mb-2 text-sm font-semibold text-500">Foto</h4>
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
                } catch {
                  return null;
                }
              })()}

              {/* Info Grid */}
              <div className="grid mt-2">
                <div className="col-6 md:col-4">
                  <span className="block mb-1 text-xs text-500">Tanggal Insiden</span>
                  <span className="text-sm font-medium text-800">{formatDate(report.incidentDate)}</span>
                </div>
                <div className="col-6 md:col-4">
                  <span className="block mb-1 text-xs text-500">Waktu</span>
                  <span className="text-sm font-medium text-800">{report.incidentTime ?? '—'}</span>
                </div>
                <div className="col-6 md:col-4">
                  <span className="block mb-1 text-xs text-500">Lokasi</span>
                  <span className="text-sm font-medium text-800">{report.locationDetail ?? '—'}</span>
                </div>
                <div className="col-6 md:col-4">
                  <span className="block mb-1 text-xs text-500">Kategori</span>
                  <span className="text-sm font-medium text-800">{report.category?.name ?? '—'}</span>
                </div>
                <div className="col-6 md:col-4">
                  <span className="block mb-1 text-xs text-500">Klasifikasi</span>
                  <span className="text-sm font-medium text-800">{report.classification?.name ?? '—'}</span>
                </div>
                <div className="col-6 md:col-4">
                  <span className="block mb-1 text-xs text-500">Root Cause</span>
                  <span className="text-sm font-medium text-800">{report.rootCause?.name ?? '—'}</span>
                </div>
                <div className="col-6 md:col-4">
                  <span className="block mb-1 text-xs text-500">Area</span>
                  <span className="text-sm font-medium text-800">{report.area?.name ?? '—'}</span>
                </div>
                <div className="col-6 md:col-4">
                  <span className="block mb-1 text-xs text-500">Dept. Asal</span>
                  <span className="text-sm font-medium text-800">{report.originDepartment?.name ?? '—'}</span>
                </div>
                <div className="col-6 md:col-4">
                  <span className="block mb-1 text-xs text-500">Dept. Tujuan</span>
                  <span className="text-sm font-medium text-800">{report.designationDepartment?.name ?? '—'}</span>
                </div>
              </div>
            </div>
          </Card>

          {/* Weather at time of incident (Open-Meteo historical, only for past incidents ≥5 days) */}
          {weather && (
            <Card className="mb-4 shadow-1 border-round-xl">
              <div className="flex align-items-center gap-2 mb-3">
                <i className="pi pi-cloud" style={{ fontSize: '1.1rem', color: '#0ea5e9' }} />
                <span className="font-semibold text-800">Kondisi Cuaca Saat Kejadian</span>
                <span className="text-xs text-400 ml-1">(sumber: Open-Meteo, lokasi fasilitas)</span>
              </div>
              <div className="grid">
                <div className="col-12 md:col-6">
                  <div className="flex align-items-center gap-2 mb-2">
                    <i className="pi pi-sun" style={{ color: '#f59e0b' }} />
                    <span className="text-sm text-600">Kondisi</span>
                    <span className="text-sm font-medium text-800">{weather.description}</span>
                  </div>
                  {weather.temperatureCelsius != null && (
                    <div className="flex align-items-center gap-2 mb-2">
                      <i className="pi pi-chart-line" style={{ color: '#ef4444' }} />
                      <span className="text-sm text-600">Suhu</span>
                      <span className="text-sm font-medium text-800">{weather.temperatureCelsius}°C</span>
                    </div>
                  )}
                </div>
                <div className="col-12 md:col-6">
                  {weather.windSpeedKmh != null && (
                    <div className="flex align-items-center gap-2 mb-2">
                      <i className="pi pi-send" style={{ color: '#6366f1' }} />
                      <span className="text-sm text-600">Kecepatan Angin</span>
                      <span className="text-sm font-medium text-800">{weather.windSpeedKmh} km/h</span>
                    </div>
                  )}
                  {weather.precipitationMm != null && (
                    <div className="flex align-items-center gap-2 mb-2">
                      <i className="pi pi-cloud" style={{ color: '#3b82f6' }} />
                      <span className="text-sm text-600">Curah Hujan</span>
                      <span className="text-sm font-medium text-800">{weather.precipitationMm} mm</span>
                    </div>
                  )}
                </div>
              </div>
            </Card>
          )}

          {/* Follow-up Results — visible when In Progress or Done */}
          {(report.status === 'In Progress' || isCompletedStatus(report.status)) && (
            <Card title="Hasil Tindak Lanjut" className="mb-4 shadow-1 border-round-xl">
              <div className="flex gap-3 flex-column">
                {report.perbaikanAwal && (
                  <div>
                    <h4 className="mt-0 mb-1 text-sm font-semibold text-500">Perbaikan Awal / Tindakan Darurat</h4>
                    <p className="m-0 text-sm text-700 line-height-3">{report.perbaikanAwal}</p>
                  </div>
                )}
                {report.corrective && (
                  <div>
                    <h4 className="mt-0 mb-1 text-sm font-semibold text-500">Tindakan Perbaikan (Corrective Action)</h4>
                    <p className="m-0 text-sm text-700 line-height-3">{report.corrective}</p>
                  </div>
                )}
                {report.preventive && (
                  <div>
                    <h4 className="mt-0 mb-1 text-sm font-semibold text-500">Tindakan Pencegahan (Preventive Action)</h4>
                    <p className="m-0 text-sm text-700 line-height-3">{report.preventive}</p>
                  </div>
                )}
                {report.reason && (
                  <div>
                    <h4 className="mt-0 mb-1 text-sm font-semibold text-500">Alasan / Keterangan</h4>
                    <p className="m-0 text-sm text-700 line-height-3">{report.reason}</p>
                  </div>
                )}
                {report.followUpNotes && (
                  <div>
                    <h4 className="mt-0 mb-1 text-sm font-semibold text-500">Catatan Tindak Lanjut</h4>
                    <p className="m-0 text-sm text-700 line-height-3">{report.followUpNotes}</p>
                  </div>
                )}
                {!report.perbaikanAwal && !report.corrective && !report.preventive && !report.followUpNotes && !isCompletedStatus(report.status) && report.status === 'In Progress' && (
                  <p className="m-0 text-sm text-400 font-italic">Belum ada data tindak lanjut. Pelaksana belum mengisi laporan.</p>
                )}
                {/* Follow-up Attachments */}
                {report.followUpAttachments && (() => {
                  try {
                    const attachments: string[] = JSON.parse(report.followUpAttachments);
                    if (attachments.length === 0) return null;
                    return (
                      <div>
                        <h4 className="mt-0 mb-1 text-sm font-semibold text-500">Lampiran Pendukung</h4>
                        <div className="flex gap-1 flex-column">
                          {attachments.map((filename, idx) => (
                            <a
                              key={idx}
                              href={fileUploadService.getPhotoUrl(filename)}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="flex gap-2 p-2 no-underline transition-colors align-items-center surface-100 border-round text-700 hover:surface-200 transition-duration-150"
                            >
                              <i className="pi pi-file text-primary" />
                              <span className="flex-1 text-sm" style={{ wordBreak: 'break-all' }}>{filename}</span>
                              <i className="text-xs pi pi-external-link text-400" />
                            </a>
                          ))}
                        </div>
                      </div>
                    );
                  } catch { return null; }
                })()}
                {(report.dateAction || report.doneAt) && (
                  <div className="grid mt-1">
                    {report.dateAction && (
                      <div className="col-6">
                        <span className="block mb-1 text-xs text-500">Tanggal Tindakan</span>
                        <span className="text-sm font-medium text-800">{formatDate(report.dateAction)}</span>
                      </div>
                    )}
                    {report.doneAt && (
                      <div className="col-6">
                        <span className="block mb-1 text-xs text-500">Tanggal Selesai</span>
                        <span className="text-sm font-medium text-800">{formatDate(report.doneAt)}</span>
                      </div>
                    )}
                  </div>
                )}
                {(report.directCost != null || report.copq != null) && (
                  <div className="grid mt-1">
                    {report.directCost != null && (
                      <div className="col-6">
                        <span className="block mb-1 text-xs text-500">Biaya Langsung</span>
                        <span className="text-sm font-medium text-800">Rp {Number(report.directCost).toLocaleString('id-ID')}</span>
                      </div>
                    )}
                    {report.copq != null && (
                      <div className="col-6">
                        <span className="block mb-1 text-xs text-500">COPQ</span>
                        <span className="text-sm font-medium text-800">Rp {Number(report.copq).toLocaleString('id-ID')}</span>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </Card>
          )}

          {/* Investigation Details */}
          {report.investigationRequired && (
            <Card title="Detail Investigasi" className="shadow-1 border-round-xl">
              <DataTable
                value={investigations}
                size="small"
                stripedRows
                responsiveLayout="scroll"
                emptyMessage="Belum ada data investigasi"
                className="border-round-lg"
              >
                <Column field="finding" header="Temuan" />
                <Column field="recommendation" header="Rekomendasi" />
                <Column field="correctiveAction" header="Tindakan Korektif" />
                <Column field="preventiveAction" header="Tindakan Preventif" />
                <Column
                  field="status"
                  header="Status"
                  body={(row: InvestigationDetail) => <ApprovalBadge status={row.status} />}
                  style={{ width: '120px' }}
                />
              </DataTable>
            </Card>
          )}
        </div>

        {/* Right: Approval Timeline */}
        <div className="col-12 lg:col-4">
          <Card title="Alur Persetujuan" className="mb-4 shadow-1 border-round-xl">
            <ApprovalTimeline
              stages={INCIDENT_STAGES}
              currentStageIndex={getIncidentStageIndex(report.status)}
              isRejected={report.status === 'Partially Approved'}
              reason={report.revisionNotes}
              getStageInfo={(idx: number): StageInfo => {
                const fmtDate = (d?: string) => d ? new Date(d).toLocaleDateString('id-ID', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : null;
                switch (idx) {
                  case 0: return { person: reporterName ?? null, date: fmtDate(report.createdAt) };
                  case 1: return { person: reviewerName ?? reporterParentName ?? null, date: fmtDate(report.reviewedAt) };
                  case 2: return { person: acknowledgerName ?? reviewerParentName ?? managerName ?? null, date: fmtDate(report.acknowledgedAt ?? report.fullyApprovedAt) };
                  case 3: return { person: managerName ?? null, date: fmtDate(report.assignedAt) };
                  case 4: return { person: actionByName ?? null, date: fmtDate(report.inProgressAt), note: report.followUpNotes, noteLabel: 'Catatan Tindak Lanjut:', noteColor: 'green' };
                  case 5: return { person: null, date: fmtDate(report.doneAt ?? report.updatedAt) };
                  default: return {};
                }
              }}
              getStageActions={(idx: number): StageAction[] => {
                const ci = getIncidentStageIndex(report.status);
                if (isCompletedStatus(report.status)) return [];
                if (idx !== ci) return [];
                // Pelaporan Awal (stage 0) tidak perlu approval
                if (idx === 0) return [];
                // Superintendent review: hanya atasan langsung pelapor
                if (idx === 1 && report.status === 'New') {
                  if (!reporterParentId || reporterParentId !== user?.id) return [];
                  return [
                    { action: 'approve-superintendent', label: 'Setujui', icon: 'pi pi-check', severity: 'success' },
                    { action: 'reject-superintendent', label: 'Tolak', icon: 'pi pi-times', severity: 'danger' },
                  ];
                }
                // Manager approval: hanya atasan dari superintendent (reviewer)
                if (idx === 2 && report.status === 'Pending Manager Approval') {
                  if (!reviewerParentId || reviewerParentId !== user?.id) return [];
                  return [
                    { action: 'approve-manager', label: 'Setujui', icon: 'pi pi-check', severity: 'success' },
                    { action: 'reject-manager', label: 'Tolak', icon: 'pi pi-times', severity: 'danger' },
                  ];
                }
                // Assign subordinate: hanya designated manager dept tujuan
                if (idx === 3 && report.status === 'Fully Approved') {
                  if (!report.designatedManagerId || report.designatedManagerId !== user?.id) return [];
                  return [
                    { action: 'assign-subordinate', label: 'Tugaskan Pelaksana', icon: 'pi pi-user-plus', severity: 'info' },
                  ];
                }
                // Submit follow-up: hanya pelaksana yang ditugaskan (action_by)
                if (idx === 4 && report.status === 'In Progress') {
                  if (!report.actionById || report.actionById !== user?.id) return [];
                  return [
                    { action: 'submit-follow-up', label: 'Selesaikan Tindak Lanjut', icon: 'pi pi-check-circle', severity: 'success' },
                  ];
                }
                return [];
              }}
              onAction={(action: string) => {
                if (action === 'approve-superintendent') handleApproval('approve', 'superintendent');
                else if (action === 'reject-superintendent') handleApproval('reject', 'superintendent');
                else if (action === 'approve-manager') handleApproval('approve', 'manager');
                else if (action === 'reject-manager') handleApproval('reject', 'manager');
                else if (action === 'assign-subordinate') setAssignDialogVisible(true);
                else if (action === 'submit-follow-up') setFollowUpDialogVisible(true);
              }}
            />
          </Card>

          {/* Timestamps */}
          <Card title="Informasi Waktu" className="shadow-1 border-round-xl">
            <div className="flex gap-2 flex-column">
              <div className="flex justify-content-between">
                <span className="text-xs text-500">Dibuat</span>
                <span className="text-xs font-medium text-700">{formatDate(report.createdAt)}</span>
              </div>
              {report.reviewedAt && (
                <div className="flex justify-content-between">
                  <span className="text-xs text-500">Direview Superintendent</span>
                  <span className="text-xs font-medium text-700">{formatDate(report.reviewedAt)}</span>
                </div>
              )}
              {report.acknowledgedAt && (
                <div className="flex justify-content-between">
                  <span className="text-xs text-500">Disetujui Manager</span>
                  <span className="text-xs font-medium text-700">{formatDate(report.acknowledgedAt)}</span>
                </div>
              )}
              {report.assignedAt && (
                <div className="flex justify-content-between">
                  <span className="text-xs text-500">Ditugaskan</span>
                  <span className="text-xs font-medium text-700">{formatDate(report.assignedAt)}</span>
                </div>
              )}
              {report.dateAction && (
                <div className="flex justify-content-between">
                  <span className="text-xs text-500">Tanggal Tindakan</span>
                  <span className="text-xs font-medium text-700">{formatDate(report.dateAction)}</span>
                </div>
              )}
              {report.doneAt && (
                <div className="flex justify-content-between">
                  <span className="text-xs text-500">Selesai</span>
                  <span className="text-xs font-medium text-700">{formatDate(report.doneAt)}</span>
                </div>
              )}
              <div className="flex justify-content-between">
                <span className="text-xs text-500">Terakhir diubah</span>
                <span className="text-xs font-medium text-700">{formatDate(report.updatedAt)}</span>
              </div>
            </div>
          </Card>
        </div>
      </div>

      {/* Assign Subordinate Dialog */}
      <Dialog
        header="Tugaskan Bawahan"
        visible={assignDialogVisible}
        onHide={() => { setAssignDialogVisible(false); setSelectedSubordinateId(null); setAssignNotes(''); }}
        style={{ width: '450px' }}
        modal
        footer={
          <div className="flex gap-2 justify-content-end">
            <Button label="Batal" icon="pi pi-times" outlined severity="secondary" onClick={() => { setAssignDialogVisible(false); setSelectedSubordinateId(null); setAssignNotes(''); }} />
            <Button label="Tugaskan" icon="pi pi-check" severity="info" loading={assignSubmitting} disabled={!selectedSubordinateId} onClick={handleAssignSubordinate} />
          </div>
        }
      >
        <div className="flex gap-3 flex-column">
          <p className="m-0 text-sm text-600">
            Pilih bawahan yang akan ditugaskan untuk menangani insiden ini.
          </p>
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-700">Bawahan <span className="text-red-500">*</span></label>
            <Dropdown
              value={selectedSubordinateId}
              options={subordinates.map((s) => ({ label: `${s.name}${s.npk ? ` (${s.npk})` : ''}`, value: s.id }))}
              onChange={(e) => setSelectedSubordinateId(e.value)}
              placeholder="Pilih bawahan"
              filter
              showClear
              emptyMessage="Tidak ada bawahan ditemukan"
              className="w-full"
            />
          </div>
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-700">Catatan (opsional)</label>
            <InputTextarea
              value={assignNotes}
              onChange={(e) => setAssignNotes(e.target.value)}
              rows={3}
              placeholder="Tambahkan catatan untuk bawahan..."
              className="w-full"
            />
          </div>
        </div>
      </Dialog>

      {/* Follow-up Dialog */}
      <Dialog
        header="Selesaikan Tindak Lanjut"
        visible={followUpDialogVisible}
        onHide={() => { setFollowUpDialogVisible(false); setFollowUpForm({ perbaikanAwal: '', corrective: '', preventive: '', followUpNotes: '', dateAction: null, directCost: '', copq: '' }); setFollowUpAttachmentFiles([]); }}
        style={{ width: '550px' }}
        modal
        footer={
          <div className="flex gap-2 justify-content-end">
            <Button label="Batal" icon="pi pi-times" outlined severity="secondary" onClick={() => { setFollowUpDialogVisible(false); setFollowUpForm({ perbaikanAwal: '', corrective: '', preventive: '', followUpNotes: '', dateAction: null, directCost: '', copq: '' }); setFollowUpAttachmentFiles([]); }} />
            <Button label="Selesaikan" icon="pi pi-check" severity="success" loading={followUpSubmitting} disabled={!followUpForm.corrective && !followUpForm.perbaikanAwal} onClick={handleFollowUpSubmit} />
          </div>
        }
      >
        <div className="flex gap-3 flex-column">
          <p className="m-0 text-sm text-600">
            Isi laporan tindak lanjut yang telah dilakukan. Status laporan akan berubah menjadi <strong>Done</strong>.
          </p>
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-700">Perbaikan Awal / Tindakan Darurat</label>
            <InputTextarea
              value={followUpForm.perbaikanAwal}
              onChange={(e) => setFollowUpForm({ ...followUpForm, perbaikanAwal: e.target.value })}
              rows={3}
              placeholder="Tindakan darurat atau perbaikan awal yang dilakukan..."
              className="w-full"
            />
          </div>
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-700">Tindakan Perbaikan <span className="text-red-500">*</span></label>
            <InputTextarea
              value={followUpForm.corrective}
              onChange={(e) => setFollowUpForm({ ...followUpForm, corrective: e.target.value })}
              rows={3}
              placeholder="Tindakan perbaikan yang telah dilakukan..."
              className="w-full"
            />
          </div>
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-700">Tindakan Pencegahan</label>
            <InputTextarea
              value={followUpForm.preventive}
              onChange={(e) => setFollowUpForm({ ...followUpForm, preventive: e.target.value })}
              rows={3}
              placeholder="Langkah pencegahan agar insiden tidak terulang..."
              className="w-full"
            />
          </div>
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-700">Catatan Tindak Lanjut</label>
            <InputTextarea
              value={followUpForm.followUpNotes}
              onChange={(e) => setFollowUpForm({ ...followUpForm, followUpNotes: e.target.value })}
              rows={2}
              placeholder="Catatan tambahan..."
              className="w-full"
            />
          </div>
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-700">Tanggal Tindakan</label>
            <Calendar
              value={followUpForm.dateAction}
              onChange={(e) => setFollowUpForm({ ...followUpForm, dateAction: e.value as Date | null })}
              dateFormat="dd/mm/yy"
              showIcon
              placeholder="Pilih tanggal tindakan..."
              className="w-full"
            />
          </div>
          <div className="grid">
            <div className="col-6">
              <div className="flex gap-1 flex-column">
                <label className="text-sm font-semibold text-700">Biaya Langsung (Rp)</label>
                <input
                  type="number"
                  min="0"
                  value={followUpForm.directCost}
                  onChange={(e) => setFollowUpForm({ ...followUpForm, directCost: e.target.value })}
                  placeholder="0"
                  className="w-full p-inputtext p-component"
                />
              </div>
            </div>
            <div className="col-6">
              <div className="flex gap-1 flex-column">
                <label className="text-sm font-semibold text-700">COPQ (Rp)</label>
                <input
                  type="number"
                  min="0"
                  value={followUpForm.copq}
                  onChange={(e) => setFollowUpForm({ ...followUpForm, copq: e.target.value })}
                  placeholder="0"
                  className="w-full p-inputtext p-component"
                />
              </div>
            </div>
          </div>
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-700">Lampiran Pendukung <span className="font-normal text-400">(opsional)</span></label>
            <input
              ref={followUpFileInputRef}
              type="file"
              multiple
              accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx"
              onChange={(e) => {
                if (e.target.files) {
                  setFollowUpAttachmentFiles(prev => [...prev, ...Array.from(e.target.files!)]);
                }
              }}
              className="hidden"
            />
            <Button
              type="button"
              label="Pilih File"
              icon="pi pi-upload"
              outlined
              size="small"
              onClick={() => followUpFileInputRef.current?.click()}
            />
            {followUpAttachmentFiles.length > 0 && (
              <div className="flex gap-1 mt-1 flex-column">
                {followUpAttachmentFiles.map((file, idx) => (
                  <div key={idx} className="flex gap-2 p-2 align-items-center surface-100 border-round">
                    <i className="pi pi-file text-500" />
                    <span className="flex-1 text-sm text-700" style={{ wordBreak: 'break-all' }}>{file.name}</span>
                    <span className="text-xs text-400">{(file.size / 1024).toFixed(0)} KB</span>
                    <Button
                      icon="pi pi-times"
                      rounded
                      text
                      severity="danger"
                      size="small"
                      onClick={() => setFollowUpAttachmentFiles(prev => prev.filter((_, i) => i !== idx))}
                      className="p-0 w-2rem h-2rem"
                    />
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </Dialog>
    </div>
  );
};

export default IncidentReportDetail;
