import { useEffect, useState, useCallback } from 'react';
import { Card } from 'primereact/card';
import { Chart } from 'primereact/chart';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Button } from 'primereact/button';
import { Message } from 'primereact/message';
import { KpiStatCard } from '../../components/irms/KpiStatCard';
import FindingsDashboard from '../../components/irms/FindingsDashboard';
import { ApprovalBadge } from '../../components/irms/StatusBadges';
import { incidentReportService, safetyObservationService } from '../../services';
import IncidentMatrixTable from '../../components/irms/IncidentMatrixTable';
import type {
  IncidentDashboardStats,
  IncidentChartData,
  SafetyDashboardStats,
  SafetyChartData,
  ChartDataPoint,
  IncidentReport,
} from '../../types/irms';

/* ============================================================
   CHART OPTIONS FACTORY
   ============================================================ */
const baseChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      position: 'bottom' as const,
      labels: { usePointStyle: true, font: { family: 'Inter', size: 12 } },
    },
  },
  scales: {
    x: { grid: { display: false }, ticks: { font: { family: 'Inter', size: 11 } } },
    y: { grid: { color: '#f1f5f9' }, ticks: { font: { family: 'Inter', size: 11 } } },
  },
};

const noAxisOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { position: 'bottom' as const, labels: { usePointStyle: true, font: { family: 'Inter', size: 12 } } },
  },
};

const CHART_COLORS = [
  '#6366f1', '#14b8a6', '#f59e0b', '#ef4444', '#8b5cf6',
  '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#64748b',
];

/* ============================================================
   IRMS DASHBOARD HOME
   ============================================================ */
const IrmsDashboardHome = () => {
  const [loading, setLoading] = useState(true);
  const [incidentStats, setIncidentStats] = useState<IncidentDashboardStats | null>(null);
  const [incidentCharts, setIncidentCharts] = useState<IncidentChartData | null>(null);
  const [safetyStats, setSafetyStats] = useState<SafetyDashboardStats | null>(null);
  const [safetyCharts, setSafetyCharts] = useState<SafetyChartData | null>(null);
  const [recentIncidents, setRecentIncidents] = useState<IncidentReport[]>([]);
  const [loadErrors, setLoadErrors] = useState<{ incident: boolean; safety: boolean; recent: boolean }>({
    incident: false, safety: false, recent: false,
  });

  const loadData = useCallback(async () => {
    setLoading(true);
    const results = await Promise.allSettled([
      incidentReportService.getDashboardStats(),
      safetyObservationService.getDashboardStats(),
      incidentReportService.list({ page: 1, limit: 5, sortBy: 'created_at', sortOrder: 'desc' }),
    ]);

    const errs = { incident: false, safety: false, recent: false };

    if (results[0].status === 'fulfilled') {
      setIncidentStats(results[0].value.stats);
      setIncidentCharts(results[0].value.chartData);
    } else {
      errs.incident = true;
      console.error('Failed to load incident dashboard', results[0].reason);
    }

    if (results[1].status === 'fulfilled') {
      setSafetyStats(results[1].value.stats);
      setSafetyCharts(results[1].value.chartData);
    } else {
      errs.safety = true;
      console.error('Failed to load safety dashboard', results[1].reason);
    }

    if (results[2].status === 'fulfilled') {
      setRecentIncidents(results[2].value.data);
    } else {
      errs.recent = true;
      console.error('Failed to load recent incidents', results[2].reason);
    }

    setLoadErrors(errs);
    setLoading(false);
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  if (loading) {
    return (
      <div className="flex align-items-center justify-content-center" style={{ minHeight: '60vh' }}>
        <ProgressSpinner strokeWidth="3" />
      </div>
    );
  }

  /* ---- Helper to build chart from ChartDataPoint[] ---- */
  const makeBarDataset = (points: ChartDataPoint[] | undefined, label: string) =>
    points?.length
      ? {
          labels: points.map((c) => c.label),
          datasets: [{
            label,
            data: points.map((c) => c.value),
            backgroundColor: CHART_COLORS.slice(0, points.length),
            borderRadius: 8,
            barPercentage: 0.6,
          }],
        }
      : null;

  const makeLineDataset = (points: ChartDataPoint[] | undefined, label: string, color: string) =>
    points?.length
      ? {
          labels: points.map((m) => m.label),
          datasets: [{
            label,
            data: points.map((m) => m.value),
            borderColor: color,
            backgroundColor: `${color}14`,
            fill: true,
            tension: 0.4,
            pointRadius: 3,
            pointHoverRadius: 6,
          }],
        }
      : null;

  /* ---- Build chart datasets from real API data ---- */
  const categoryChartData = makeBarDataset(incidentCharts?.byCategory, 'Insiden per Kategori');
  const deptChartData = makeBarDataset(incidentCharts?.byOriginDepartment, 'Insiden per Dept. Asal');
  const monthlyIncidentData = makeLineDataset(incidentCharts?.byMonth, 'Insiden per Bulan', '#6366f1');
  const monthlySafetyData = makeLineDataset(safetyCharts?.byMonth, 'Observasi per Bulan', '#14b8a6');

  const safetyPieData = safetyStats
    ? {
        labels: ['JSO', 'NCR'],
        datasets: [{
          data: [safetyStats.jsoCount, safetyStats.ncrCount],
          backgroundColor: ['#6366f1', '#f59e0b'],
          hoverBackgroundColor: ['#4f46e5', '#d97706'],
        }],
      }
    : null;

  const statusPieData = incidentCharts?.byStatus?.length
    ? {
        labels: incidentCharts.byStatus.map((s) => s.label),
        datasets: [{
          data: incidentCharts.byStatus.map((s) => s.value),
          backgroundColor: CHART_COLORS.slice(0, incidentCharts.byStatus.length),
        }],
      }
    : null;

  /* ---- Template helpers ---- */
  const statusBodyTemplate = (row: IncidentReport) => <ApprovalBadge status={row.status} />;

  const dateBodyTemplate = (row: IncidentReport) => (
    <span className="text-sm text-700">
      {row.incidentDate
        ? new Date(row.incidentDate).toLocaleDateString('id-ID', { day: '2-digit', month: 'short', year: 'numeric' })
        : '—'}
    </span>
  );

  const chartOrEmpty = (dataset: unknown, type: 'bar' | 'line' | 'doughnut' | 'pie', opts: object) =>
    dataset ? (
      <Chart type={type} data={dataset as object} options={opts} className="h-full" />
    ) : (
      <div className="flex align-items-center justify-content-center h-full text-500">Belum ada data</div>
    );

  return (
    <div className="flex flex-column gap-4">
      {/* Page Header */}
      <div>
        <h2 className="text-2xl font-bold text-800 m-0">Dashboard IRMS</h2>
        <p className="text-sm text-500 mt-1 mb-0">
          Incident Reporting & Management System — Ringkasan KPI
        </p>
      </div>

      {/* Surface partial-load failures so users can distinguish "no data" from "fetch failed". */}
      {(loadErrors.incident || loadErrors.safety || loadErrors.recent) && (
        <div className="flex align-items-center justify-content-between gap-3 p-3 border-round-xl"
             style={{ background: '#fef2f2', border: '1px solid #fecaca' }}>
          <Message
            severity="warn"
            text={
              loadErrors.incident && loadErrors.safety && loadErrors.recent
                ? 'Gagal memuat data dashboard. Periksa koneksi atau coba lagi.'
                : 'Sebagian data dashboard gagal dimuat. Angka di bawah mungkin tidak lengkap.'
            }
          />
          <Button label="Coba lagi" icon="pi pi-refresh" size="small" outlined onClick={loadData} />
        </div>
      )}

      {/* ═══ Incident KPI Cards (Row 1) ═══ */}
      <div className="grid">
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Total Insiden" value={incidentStats?.total ?? 0} icon="pi pi-exclamation-triangle" color="#6366f1" bg="#eef2ff" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Insiden Terbuka" value={incidentStats?.open ?? 0} icon="pi pi-folder-open" color="#f59e0b" bg="#fffbeb" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Insiden Ditutup" value={incidentStats?.closed ?? 0} icon="pi pi-lock" color="#22c55e" bg="#f0fdf4" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Selesai (Done)" value={incidentStats?.done ?? 0} icon="pi pi-check-circle" color="#14b8a6" bg="#f0fdfa" />
        </div>
      </div>

      {/* ═══ Incident Workflow Cards (Row 2) ═══ */}
      <div className="grid">
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Baru (New)" value={incidentStats?.new ?? 0} icon="pi pi-plus-circle" color="#3b82f6" bg="#eff6ff" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Partially Approved" value={incidentStats?.partiallyApproved ?? 0} icon="pi pi-minus-circle" color="#8b5cf6" bg="#f5f3ff" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Pending Manager" value={incidentStats?.pendingManager ?? 0} icon="pi pi-hourglass" color="#f97316" bg="#fff7ed" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Fully Approved" value={incidentStats?.fullyApproved ?? 0} icon="pi pi-verified" color="#14b8a6" bg="#f0fdfa" />
        </div>
      </div>

      {/* ═══ Safety Observation KPI Cards (Row 3) ═══ */}
      <div className="grid">
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Total Observasi" value={safetyStats?.total ?? 0} icon="pi pi-eye" color="#14b8a6" bg="#f0fdfa" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Laporan JSO" value={safetyStats?.jsoCount ?? 0} icon="pi pi-users" color="#8b5cf6" bg="#f5f3ff" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Laporan NCR" value={safetyStats?.ncrCount ?? 0} icon="pi pi-file" color="#f97316" bg="#fff7ed" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Overdue" value={safetyStats?.overdue ?? 0} icon="pi pi-clock" color="#ef4444" bg="#fef2f2" />
        </div>
      </div>

      {/* ═══ Dashboard Temuan (Open/Close, kategori, tren, tindak lanjut) ═══ */}
      <FindingsDashboard />

      {/* ═══ Charts Row 1: Monthly Observation Trend ═══ */}
      <div className="grid">
        <div className="col-12">
          <Card title="Tren Observasi per Bulan" className="shadow-1 border-round-xl h-full">
            <div style={{ height: '320px' }}>
              {chartOrEmpty(monthlySafetyData, 'line', baseChartOptions)}
            </div>
          </Card>
        </div>
      </div>

      {/* ═══ Charts Row 2: Category, Department, Pie ═══ */}
      <div className="grid">
        <div className="col-12 lg:col-4">
          <Card title="Insiden per Kategori" className="shadow-1 border-round-xl h-full">
            <div style={{ height: '320px' }}>
              {chartOrEmpty(categoryChartData, 'bar', { ...baseChartOptions, plugins: { ...baseChartOptions.plugins, legend: { display: false } } })}
            </div>
          </Card>
        </div>
        <div className="col-12 lg:col-4">
          <Card title="Insiden per Departemen" className="shadow-1 border-round-xl h-full">
            <div style={{ height: '320px' }}>
              {chartOrEmpty(deptChartData, 'bar', { ...baseChartOptions, plugins: { ...baseChartOptions.plugins, legend: { display: false } } })}
            </div>
          </Card>
        </div>
        <div className="col-12 lg:col-4">
          <Card title="JSO vs NCR" className="shadow-1 border-round-xl h-full">
            <div style={{ height: '320px' }}>
              {chartOrEmpty(safetyPieData, 'doughnut', noAxisOptions)}
            </div>
          </Card>
        </div>
      </div>

      {/* ═══ Charts Row 3: Status Distribution ═══ */}
      <div className="grid">
        <div className="col-12 lg:col-4">
          <Card title="Distribusi Status Insiden" className="shadow-1 border-round-xl h-full">
            <div style={{ height: '320px' }}>
              {chartOrEmpty(statusPieData, 'doughnut', noAxisOptions)}
            </div>
          </Card>
        </div>
        <div className="col-12 lg:col-8">
          <IncidentMatrixTable defaultCollapsed={true} />
        </div>
      </div>

      {/* ═══ Recent Incidents Table ═══ */}
      <Card title="Insiden Terbaru" className="shadow-1 border-round-xl">
        <DataTable
          value={recentIncidents}
          size="small"
          stripedRows
          responsiveLayout="scroll"
          emptyMessage="Belum ada laporan insiden"
          className="border-round-lg"
        >
          <Column field="reportNumber" header="No. Laporan" className="font-medium" style={{ width: '160px' }} />
          <Column field="title" header="Judul" />
          <Column header="Tanggal" body={dateBodyTemplate} style={{ width: '140px' }} />
          <Column header="Departemen" body={(row: IncidentReport) => row.originDepartment?.name ?? '—'} style={{ width: '140px' }} />
          <Column header="Kategori" body={(row: IncidentReport) => row.category?.name ?? '—'} style={{ width: '140px' }} />
          <Column header="Status" body={statusBodyTemplate} style={{ width: '120px' }} />
        </DataTable>
      </Card>

      {/* ═══ Tren Insiden per Bulan (Bottom) ═══ */}
      <Card title="Tren Insiden per Bulan" className="shadow-1 border-round-xl">
        <div style={{ height: '320px' }}>
          {chartOrEmpty(monthlyIncidentData, 'line', baseChartOptions)}
        </div>
      </Card>
    </div>
  );
};

export default IrmsDashboardHome;
