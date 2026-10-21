import { useEffect, useState, useCallback } from 'react';
import { Card } from 'primereact/card';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Chart } from 'primereact/chart';
import { Tag } from 'primereact/tag';
import { ProgressSpinner } from 'primereact/progressspinner';
import { KpiStatCard } from '../../components/irms/KpiStatCard';
import { incidentReportService, safetyObservationService, laboratoryReportService } from '../../services';
import type { IncidentReport, ChartDataPoint } from '../../types/irms';
import type { LaboratoryReportDto, LabReportStatus } from '../../types/lims';
import { ApprovalBadge } from '../../components/irms/StatusBadges';

/* ============================================================
   CHART OPTIONS
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

const CHART_COLORS = [
  '#6366f1', '#14b8a6', '#f59e0b', '#ef4444', '#8b5cf6',
  '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#64748b',
];

/* ============================================================
   COMPONENT
   ============================================================ */
const DashboardHome = () => {
  const [loading, setLoading] = useState(true);
  // IRMS stats
  const [totalIncidents, setTotalIncidents] = useState(0);
  const [openIncidents, setOpenIncidents] = useState(0);
  const [closedIncidents, setClosedIncidents] = useState(0);
  const [totalObservations, setTotalObservations] = useState(0);
  const [jsoCount, setJsoCount] = useState(0);
  const [ncrCount, setNcrCount] = useState(0);
  const [overdueObs, setOverdueObs] = useState(0);
  // LIMS stats
  const [totalLabReports, setTotalLabReports] = useState(0);
  const [totalDailyReports, setTotalDailyReports] = useState(0);
  const [openLabReports, setOpenLabReports] = useState(0);
  const [closedLabReports, setClosedLabReports] = useState(0);
  // Chart data
  const [categoryData, setCategoryData] = useState<ChartDataPoint[]>([]);
  const [limsMonthData, setLimsMonthData] = useState<ChartDataPoint[]>([]);
  const [incidentMonthData, setIncidentMonthData] = useState<ChartDataPoint[]>([]);
  const [safetyMonthData, setSafetyMonthData] = useState<ChartDataPoint[]>([]);
  // Recent data
  const [recentIncidents, setRecentIncidents] = useState<IncidentReport[]>([]);
  const [recentLabReports, setRecentLabReports] = useState<LaboratoryReportDto[]>([]);

  const loadData = useCallback(async () => {
    setLoading(true);
    const results = await Promise.allSettled([
      incidentReportService.getDashboardStats(),
      safetyObservationService.getDashboardStats(),
      laboratoryReportService.getDashboardStats(),
      incidentReportService.list({ page: 1, limit: 5, sortBy: 'created_at', sortOrder: 'desc' }),
      laboratoryReportService.list(0, 5, 'createdAt', 'desc'),
    ]);

    // IRMS — Incident
    if (results[0].status === 'fulfilled') {
      const incidentDash = results[0].value;
      setTotalIncidents(incidentDash.stats?.total ?? 0);
      setOpenIncidents(incidentDash.stats?.open ?? 0);
      setClosedIncidents(incidentDash.stats?.closed ?? 0);
      setCategoryData(incidentDash.chartData?.byCategory ?? []);
      setIncidentMonthData(incidentDash.chartData?.byMonth ?? []);
    } else {
      console.error('Failed to load incident dashboard', results[0].reason);
    }

    // IRMS — Safety Observation
    if (results[1].status === 'fulfilled') {
      const safetyDash = results[1].value;
      setTotalObservations(safetyDash.stats?.total ?? 0);
      setJsoCount(safetyDash.stats?.jsoCount ?? 0);
      setNcrCount(safetyDash.stats?.ncrCount ?? 0);
      setOverdueObs(safetyDash.stats?.overdue ?? 0);
      setSafetyMonthData(safetyDash.chartData?.byMonth ?? []);
    } else {
      console.error('Failed to load safety dashboard', results[1].reason);
    }

    // LIMS
    if (results[2].status === 'fulfilled') {
      const limsDash = results[2].value;
      const ls = limsDash.data?.stats;
      setTotalLabReports(ls?.totalLabReports ?? 0);
      setTotalDailyReports(ls?.totalDailyReports ?? 0);
      setOpenLabReports(ls?.open ?? 0);
      setClosedLabReports(ls?.closed ?? 0);
      setLimsMonthData(limsDash.data?.chartData?.byMonth ?? []);
    } else {
      console.error('Failed to load LIMS dashboard', results[2].reason);
    }

    // Recent incidents
    if (results[3].status === 'fulfilled') {
      setRecentIncidents(results[3].value.data ?? []);
    }

    // Recent lab reports
    if (results[4].status === 'fulfilled') {
      const recentLabs = results[4].value;
      setRecentLabReports(Array.isArray(recentLabs.data) ? recentLabs.data : []);
    }

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

  /* ---- Chart datasets ---- */
  const categoryChartData = categoryData.length
    ? {
        labels: categoryData.map((c) => c.label),
        datasets: [
          {
            label: 'Insiden per Kategori',
            data: categoryData.map((c) => c.value),
            backgroundColor: CHART_COLORS.slice(0, categoryData.length),
            borderRadius: 8,
            barPercentage: 0.6,
          },
        ],
      }
    : null;

  const limsMonthChartData = limsMonthData.length
    ? {
        labels: limsMonthData.map((m) => m.label),
        datasets: [
          {
            label: 'Laporan Lab per Bulan',
            data: limsMonthData.map((m) => m.value),
            borderColor: '#14b8a6',
            backgroundColor: 'rgba(20,184,166,0.08)',
            fill: true,
            tension: 0.4,
            pointRadius: 3,
            pointHoverRadius: 6,
          },
        ],
      }
    : null;

  const incidentMonthChartData = incidentMonthData.length
    ? {
        labels: incidentMonthData.map((m) => m.label),
        datasets: [
          {
            label: 'Insiden per Bulan',
            data: incidentMonthData.map((m) => m.value),
            borderColor: '#6366f1',
            backgroundColor: 'rgba(99,102,241,0.08)',
            fill: true,
            tension: 0.4,
            pointRadius: 3,
            pointHoverRadius: 6,
          },
        ],
      }
    : null;

  // Combined monthly trend chart (incidents + observations + lab reports)
  const allMonths = new Set([
    ...incidentMonthData.map((m) => m.label),
    ...safetyMonthData.map((m) => m.label),
    ...limsMonthData.map((m) => m.label),
  ]);
  const monthLabels = Array.from(allMonths);
  const combinedMonthlyData = monthLabels.length
    ? {
        labels: monthLabels,
        datasets: [
          {
            label: 'Insiden',
            data: monthLabels.map((l) => incidentMonthData.find((m) => m.label === l)?.value ?? 0),
            borderColor: '#6366f1',
            backgroundColor: 'rgba(99,102,241,0.08)',
            fill: false,
            tension: 0.4,
            pointRadius: 3,
          },
          {
            label: 'Observasi',
            data: monthLabels.map((l) => safetyMonthData.find((m) => m.label === l)?.value ?? 0),
            borderColor: '#14b8a6',
            backgroundColor: 'rgba(20,184,166,0.08)',
            fill: false,
            tension: 0.4,
            pointRadius: 3,
          },
          {
            label: 'Laporan Lab',
            data: monthLabels.map((l) => limsMonthData.find((m) => m.label === l)?.value ?? 0),
            borderColor: '#f59e0b',
            backgroundColor: 'rgba(245,158,11,0.08)',
            fill: false,
            tension: 0.4,
            pointRadius: 3,
          },
        ],
      }
    : null;

  const jsoNcrData = {
    labels: ['JSO', 'NCR'],
    datasets: [
      {
        data: [jsoCount, ncrCount],
        backgroundColor: ['#6366f1', '#f59e0b'],
        hoverBackgroundColor: ['#4f46e5', '#d97706'],
      },
    ],
  };

  const statusBodyTemplate = (row: IncidentReport) => (
    <ApprovalBadge status={row.status} />
  );

  const dateBodyTemplate = (row: IncidentReport) => (
    <span className="text-sm text-700">
      {row.incidentDate
        ? new Date(row.incidentDate).toLocaleDateString('id-ID', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
          })
        : '—'}
    </span>
  );

  const labStatusSeverity = (status: LabReportStatus) => {
    const map: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
      Open: 'info', Reviewed: 'warning', Close: 'success', Rejected: 'danger',
    };
    return map[status] ?? 'info';
  };

  const chartOrEmpty = (dataset: unknown, type: 'bar' | 'line' | 'doughnut', opts: object) =>
    dataset ? (
      <Chart type={type} data={dataset as object} options={opts} className="h-full" />
    ) : (
      <div className="flex align-items-center justify-content-center h-full text-500">Belum ada data</div>
    );

  const noAxisOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom' as const, labels: { usePointStyle: true, font: { family: 'Inter', size: 12 } } } },
  };

  return (
    <div className="flex flex-column gap-4">
      {/* Page Header */}
      <div>
        <h2 className="text-2xl font-bold text-800 m-0">Dashboard</h2>
        <p className="text-sm text-500 mt-1 mb-0">Ringkasan IRMS &amp; LIMS — Data Real-time dari Database</p>
      </div>

      {/* ═══ IRMS KPI Cards ═══ */}
      <div className="grid">
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Total Insiden" value={totalIncidents} icon="pi pi-exclamation-triangle" color="#ef4444" bg="#fef2f2" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Insiden Terbuka" value={openIncidents} icon="pi pi-folder-open" color="#f59e0b" bg="#fffbeb" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Insiden Ditutup" value={closedIncidents} icon="pi pi-check-circle" color="#22c55e" bg="#f0fdf4" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Total Observasi" value={totalObservations} icon="pi pi-eye" color="#6366f1" bg="#eef2ff" />
        </div>
      </div>

      {/* ═══ LIMS + Safety KPI Cards ═══ */}
      <div className="grid">
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Laporan Lab" value={totalLabReports} icon="pi pi-file" color="#14b8a6" bg="#f0fdfa" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Lab Open / Closed" value={`${openLabReports} / ${closedLabReports}`} icon="pi pi-chart-bar" color="#8b5cf6" bg="#f5f3ff" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Laporan Harian" value={totalDailyReports} icon="pi pi-calendar" color="#06b6d4" bg="#ecfeff" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Observasi Overdue" value={overdueObs} icon="pi pi-clock" color="#ef4444" bg="#fef2f2" />
        </div>
      </div>

      {/* ═══ Combined Monthly Trends ═══ */}
      <div className="grid">
        <div className="col-12 lg:col-8">
          <Card title="Tren Bulanan (Insiden, Observasi, Lab)" className="shadow-1 border-round-xl h-full">
            <div style={{ height: '340px' }}>
              {chartOrEmpty(combinedMonthlyData, 'line', baseChartOptions)}
            </div>
          </Card>
        </div>
        <div className="col-12 lg:col-4">
          <Card title="JSO vs NCR" className="shadow-1 border-round-xl h-full">
            <div style={{ height: '340px' }}>
              <Chart type="doughnut" data={jsoNcrData} options={noAxisOptions} className="h-full" />
            </div>
          </Card>
        </div>
      </div>

      {/* ═══ Charts Row 2: Category + Lab Monthly ═══ */}
      <div className="grid">
        <div className="col-12 lg:col-6">
          <Card title="Insiden per Kategori" className="shadow-1 border-round-xl h-full">
            <div style={{ height: '320px' }}>
              {chartOrEmpty(categoryChartData, 'bar', { ...baseChartOptions, plugins: { ...baseChartOptions.plugins, legend: { display: false } } })}
            </div>
          </Card>
        </div>
        <div className="col-12 lg:col-6">
          <Card title="Tren Laporan Lab (Bulanan)" className="shadow-1 border-round-xl h-full">
            <div style={{ height: '320px' }}>
              {chartOrEmpty(limsMonthChartData, 'line', baseChartOptions)}
            </div>
          </Card>
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
          <Column header="Status" body={statusBodyTemplate} style={{ width: '120px' }} />
        </DataTable>
      </Card>

      {/* ═══ Recent Lab Reports Table ═══ */}
      <Card title="Laporan Lab Terbaru" className="shadow-1 border-round-xl">
        <DataTable
          value={recentLabReports}
          size="small"
          stripedRows
          responsiveLayout="scroll"
          emptyMessage="Belum ada laporan laboratorium"
          className="border-round-lg"
        >
          <Column field="noReport" header="No. Laporan" className="font-medium" style={{ width: '180px' }} />
          <Column field="location" header="Lokasi" />
          <Column
            header="Tgl. Sampling"
            body={(row: LaboratoryReportDto) => (
              <span className="text-sm text-700">
                {row.dateSampling
                  ? new Date(row.dateSampling).toLocaleDateString('id-ID', { day: '2-digit', month: 'short', year: 'numeric' })
                  : '—'}
              </span>
            )}
            style={{ width: '140px' }}
          />
          <Column header="Departemen" body={(row: LaboratoryReportDto) => row.departmentName ?? '—'} style={{ width: '140px' }} />
          <Column
            header="Status"
            body={(row: LaboratoryReportDto) => (
              <Tag value={row.status} severity={labStatusSeverity(row.status)} className="text-xs font-semibold" />
            )}
            style={{ width: '120px' }}
          />
        </DataTable>
      </Card>
    </div>
  );
};

export default DashboardHome;
