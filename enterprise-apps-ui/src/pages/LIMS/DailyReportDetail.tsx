import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Tag } from 'primereact/tag';
import { Divider } from 'primereact/divider';
import { Toast } from 'primereact/toast';
import { BreadCrumb } from 'primereact/breadcrumb';
import { ProgressSpinner } from 'primereact/progressspinner';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { dailyReportService } from '../../services/limsService';
import { useAuth } from '../../context/AuthContext';
import ApprovalTimeline from '../../components/shared/ApprovalTimeline';
import type { StageAction, StageConfig, StageInfo } from '../../components/shared/ApprovalTimeline';
import type { DailyReportDto } from '../../types/lims';

// Daily Report 3-stage workflow: Open → Reviewed → Close
const DAILY_STAGES: StageConfig[] = [
  { key: 'open',     label: 'Pelaporan Awal',     icon: 'pi pi-file',         roleLabel: 'Pelapor' },
  { key: 'reviewed', label: 'Tinjauan',            icon: 'pi pi-search',       roleLabel: 'Reviewer' },
  { key: 'closed',   label: 'Penyelesaian Akhir',  icon: 'pi pi-check-circle', roleLabel: 'Penutupan' },
];

function getDailyStageIndex(status?: string): number {
  switch (status) {
    case 'Open': return 0;
    case 'Reviewed': return 1;
    case 'Close': return 2;
    case 'Rejected': return -1;
    default: return 0;
  }
}

/* ============================================================
   DAILY REPORT DETAIL PAGE
   PrimeReact Verona Layout
   ============================================================ */
const DailyReportDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);
  const { user } = useAuth();
  const [report, setReport] = useState<DailyReportDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  // BreadCrumb
  const breadcrumbItems = [
    { label: 'LIMS', command: () => navigate('/dashboard') },
    { label: 'Laporan Harian', command: () => navigate('/dashboard/lims/daily-reports') },
    { label: 'Detail' },
  ];
  const breadcrumbHome = { icon: 'pi pi-home', command: () => navigate('/dashboard') };

  useEffect(() => {
    if (!id) return;
    (async () => {
      try {
        const res = await dailyReportService.get(Number(id));
        setReport(res.data);
      } catch {
        toast.current?.show({
          severity: 'error',
          summary: 'Error',
          detail: 'Gagal memuat detail laporan harian',
          life: 3000,
        });
      } finally {
        setLoading(false);
      }
    })();
  }, [id]);

  const reloadData = async () => {
    if (!id) return;
    try {
      const res = await dailyReportService.get(Number(id));
      setReport(res.data);
    } catch { /* ignore */ }
  };

  const handleWorkflowAction = (action: string) => {
    if (!report || !user) return;
    const labels: Record<string, { msg: string; accept: string }> = {
      review: { msg: 'Tandai laporan ini sebagai Reviewed?', accept: 'Ya, Review' },
      acknowledge: { msg: 'Setujui dan tutup laporan ini?', accept: 'Ya, Setujui' },
      reject: { msg: 'Apakah Anda yakin ingin menolak laporan ini?', accept: 'Ya, Tolak' },
    };
    const l = labels[action];
    if (!l) return;
    confirmDialog({
      message: l.msg,
      header: 'Konfirmasi',
      icon: action === 'reject' ? 'pi pi-exclamation-triangle' : 'pi pi-check-circle',
      acceptLabel: l.accept,
      rejectLabel: 'Batal',
      acceptClassName: action === 'reject' ? 'p-button-danger' : undefined,
      accept: async () => {
        setActionLoading(true);
        try {
          if (action === 'review') await dailyReportService.review(report.id, Number(user.id));
          else if (action === 'acknowledge') await dailyReportService.acknowledge(report.id, Number(user.id));
          else await dailyReportService.reject(report.id);
          await reloadData();
          const actionLabel = action === 'reject' ? 'ditolak' : action === 'review' ? 'direview' : 'disetujui';
          toast.current?.show({ severity: 'success', summary: 'Berhasil', detail: `Laporan berhasil ${actionLabel}`, life: 3000 });
        } catch {
          toast.current?.show({ severity: 'error', summary: 'Error', detail: `Gagal melakukan aksi ${action}`, life: 3000 });
        } finally {
          setActionLoading(false);
        }
      },
    });
  };

  const handleDelete = () => {
    if (!report) return;
    confirmDialog({
      message: `Hapus laporan harian "${report.name}"?`,
      header: 'Konfirmasi Hapus',
      icon: 'pi pi-exclamation-triangle',
      acceptClassName: 'p-button-danger',
      acceptLabel: 'Hapus',
      rejectLabel: 'Batal',
      accept: async () => {
        try {
          await dailyReportService.delete(report.id);
          toast.current?.show({
            severity: 'success',
            summary: 'Berhasil',
            detail: 'Laporan harian berhasil dihapus',
            life: 2000,
          });
          setTimeout(() => navigate('/dashboard/lims/daily-reports'), 800);
        } catch {
          toast.current?.show({
            severity: 'error',
            summary: 'Error',
            detail: 'Gagal menghapus laporan harian',
            life: 3000,
          });
        }
      },
    });
  };

  if (loading) {
    return (
      <div className="flex align-items-center justify-content-center" style={{ minHeight: '300px' }}>
        <ProgressSpinner style={{ width: '50px', height: '50px' }} />
      </div>
    );
  }

  if (!report) {
    return (
      <div className="flex flex-column align-items-center justify-content-center" style={{ minHeight: '300px' }}>
        <i className="pi pi-inbox text-4xl text-400 mb-3" />
        <p className="text-500 mb-3">Laporan harian tidak ditemukan.</p>
        <Button
          label="Kembali ke Daftar"
          icon="pi pi-arrow-left"
          severity="secondary"
          outlined
          onClick={() => navigate('/dashboard/lims/daily-reports')}
        />
      </div>
    );
  }

  const dueDateObj = report.dueDate ? new Date(report.dueDate) : null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const isPastDue = dueDateObj ? dueDateObj < today : false;

  const detailRows: { label: string; icon: string; value: React.ReactNode }[] = [
    {
      label: 'Nama Laporan',
      icon: 'pi pi-file',
      value: <span className="font-semibold text-800">{report.name}</span>,
    },
    {
      label: 'Tanggal Jatuh Tempo',
      icon: 'pi pi-calendar',
      value: dueDateObj ? (
        <Tag
          value={dueDateObj.toLocaleDateString('id-ID', { day: '2-digit', month: 'long', year: 'numeric' })}
          severity={isPastDue ? 'danger' : 'info'}
          icon={isPastDue ? 'pi pi-exclamation-circle' : 'pi pi-calendar'}
        />
      ) : (
        <span className="text-500">—</span>
      ),
    },
    {
      label: 'Lokasi',
      icon: 'pi pi-map-marker',
      value: report.location || <span className="text-500">—</span>,
    },
    {
      label: 'Parameter',
      icon: 'pi pi-sliders-h',
      value: report.parameterName || report.parameterId || <span className="text-500">—</span>,
    },
    {
      label: 'Unit',
      icon: 'pi pi-building',
      value: report.unitName || report.unitId || <span className="text-500">—</span>,
    },
    {
      label: 'Deskripsi',
      icon: 'pi pi-align-left',
      value: report.description || <span className="text-500">—</span>,
    },
    {
      label: 'Dibuat pada',
      icon: 'pi pi-clock',
      value: report.createdAt ? (
        <span className="text-sm">{new Date(report.createdAt).toLocaleString('id-ID')}</span>
      ) : (
        <span className="text-500">—</span>
      ),
    },
  ];

  return (
    <div className="flex gap-4 flex-column">
      <Toast ref={toast} />
      <ConfirmDialog />

      {/* BreadCrumb */}
      <BreadCrumb model={breadcrumbItems} home={breadcrumbHome} className="border-none p-0 mb-1" />

      {/* Header */}
      <div className="flex flex-wrap gap-3 align-items-center justify-content-between">
        <div>
          <h2 className="m-0 text-2xl font-bold text-800">Detail Laporan Harian</h2>
          <p className="mt-1 mb-0 text-sm text-500">{report.name}</p>
        </div>
        <div className="flex gap-2">
          <Button
            label="Kembali"
            icon="pi pi-arrow-left"
            severity="secondary"
            style={{ minWidth: '9rem', borderRadius: '10px', padding: '0.85rem 1.5rem', fontSize: '1rem' }}
            onClick={() => navigate('/dashboard/lims/daily-reports')}
          />
          <Button
            label="Edit"
            icon="pi pi-pencil"
            severity="warning"
            style={{ minWidth: '9rem', borderRadius: '10px', padding: '0.85rem 1.5rem', fontSize: '1rem' }}
            onClick={() => navigate(`/dashboard/lims/daily-reports/${id}/edit`)}
          />
          <Button
            label="Hapus"
            icon="pi pi-trash"
            severity="danger"
            style={{ minWidth: '9rem', borderRadius: '10px', padding: '0.85rem 1.5rem', fontSize: '1rem' }}
            onClick={handleDelete}
          />
        </div>
      </div>

      <div className="grid">
        {/* Left: Detail Card */}
        <div className="col-12 lg:col-8">
          <Card className="shadow-1 border-round-xl">
            <div className="grid">
              {detailRows.map((row, idx) => (
                <div key={idx} className="col-12 md:col-6 lg:col-4 mb-3">
                  <div className="flex align-items-start gap-3">
                    <div className="flex align-items-center justify-content-center border-round bg-primary-50 text-primary"
                      style={{ width: '2.5rem', height: '2.5rem', minWidth: '2.5rem' }}>
                      <i className={row.icon} />
                    </div>
                    <div>
                      <p className="text-500 text-xs font-medium mb-1 mt-0 uppercase">{row.label}</p>
                      <div className="text-800 text-sm">{row.value}</div>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {report.description && (
              <>
                <Divider />
                <div>
                  <h3 className="text-sm font-semibold text-700 mb-2">
                    <i className="pi pi-align-left mr-2" />
                    Deskripsi Lengkap
                  </h3>
                  <p className="text-800 line-height-3 m-0" style={{ whiteSpace: 'pre-wrap' }}>
                    {report.description}
                  </p>
                </div>
              </>
            )}
          </Card>
        </div>

        {/* Right: Approval Timeline */}
        <div className="col-12 lg:col-4">
          <Card title="Alur Persetujuan" className="shadow-1 border-round-xl mb-4">
            <ApprovalTimeline
              stages={DAILY_STAGES}
              currentStageIndex={getDailyStageIndex(report.status)}
              isRejected={report.status === 'Rejected'}
              getStageInfo={(idx: number): StageInfo => {
                const fmtDate = (d?: string) => d ? new Date(d).toLocaleDateString('id-ID', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : null;
                switch (idx) {
                  case 0: return { person: null, date: fmtDate(report.createdAt) };
                  case 1: return { person: report.reviewedByName ?? null, date: fmtDate(report.reviewedAt ?? undefined) };
                  case 2: return { person: report.acknowledgedByName ?? null, date: fmtDate(report.acknowledgedAt ?? undefined) };
                  default: return {};
                }
              }}
              getStageActions={(idx: number): StageAction[] => {
                const ci = getDailyStageIndex(report.status);
                if (report.status === 'Rejected' || report.status === 'Close') return [];
                if (idx !== ci) return [];
                switch (idx) {
                  case 0: return [
                    { action: 'review', label: 'Tinjau', icon: 'pi pi-search', severity: 'success' },
                  ];
                  case 1: return [
                    { action: 'acknowledge', label: 'Setujui', icon: 'pi pi-check-circle', severity: 'success' },
                  ];
                  default: return [];
                }
              }}
              onAction={handleWorkflowAction}
              rejectButton={
                report.status !== 'Close' && report.status !== 'Rejected'
                  ? { label: 'Tolak Laporan', action: 'reject' }
                  : null
              }
            />
          </Card>

          {/* Timestamps */}
          <Card title="Informasi Waktu" className="shadow-1 border-round-xl">
            <div className="flex flex-column gap-2">
              <div className="flex justify-content-between">
                <span className="text-xs text-500">Dibuat</span>
                <span className="text-xs font-medium text-700">
                  {report.createdAt ? new Date(report.createdAt).toLocaleString('id-ID') : '—'}
                </span>
              </div>
              <div className="flex justify-content-between">
                <span className="text-xs text-500">Terakhir diubah</span>
                <span className="text-xs font-medium text-700">
                  {report.updatedAt ? new Date(report.updatedAt).toLocaleString('id-ID') : '—'}
                </span>
              </div>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default DailyReportDetail;
