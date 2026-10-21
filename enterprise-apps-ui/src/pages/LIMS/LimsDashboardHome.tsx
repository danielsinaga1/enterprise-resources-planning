import { useEffect, useState, useCallback } from 'react';
import { Card } from 'primereact/card';
import { Chart } from 'primereact/chart';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Tag } from 'primereact/tag';
import { KpiStatCard } from '../../components/irms/KpiStatCard';
import { laboratoryReportService } from '../../services';
import type { LaboratoryReportDto, LabReportStatus } from '../../types/lims';

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
   TYPES
   ============================================================ */
interface LimsStats {
  totalLabReports: number;
  open: number;
  reviewed: number;
  closed: number;
  rejected: number;
  totalDailyReports: number;
  /** Jumlah hasil (bukan laporan) yang di luar spesifikasi. */
  outOfSpec: number;
}

interface ChartItem {
  label: string;
  value: number;
}

interface LimsChartData {
  byDepartment: ChartItem[];
  byStatus: ChartItem[];
  byMonth: ChartItem[];
}

/* ============================================================
   STATUS HELPERS
   ============================================================ */
const statusSeverity = (status: LabReportStatus) => {
  const map: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    Open: 'info',
    Reviewed: 'warning',
    Close: 'success',
    Rejected: 'danger',
  };
  return map[status] ?? 'info';
};

/* ============================================================
   LIMS DASHBOARD HOME
   ============================================================ */
const LimsDashboardHome = () => {
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState<LimsStats | null>(null);
  const [chartData, setChartData] = useState<LimsChartData | null>(null);
  const [recentReports, setRecentReports] = useState<LaboratoryReportDto[]>([]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [dashRes, recentRes] = await Promise.all([
        laboratoryReportService.getDashboardStats(),
        laboratoryReportService.list(0, 5, 'createdAt', 'desc'),
      ]);
      setStats(dashRes.data.stats);
      setChartData(dashRes.data.chartData);
      setRecentReports(Array.isArray(recentRes.data) ? recentRes.data : []);
    } catch (err) {
      console.error('Failed to load LIMS dashboard data', err);
    } finally {
      setLoading(false);
    }
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

  /* ---- Build chart datasets ---- */
  const deptChartDataset = chartData
    ? {
        labels: chartData.byDepartment.map((d) => d.label),
        datasets: [
          {
            label: 'Laporan per Departemen',
            data: chartData.byDepartment.map((d) => d.value),
            backgroundColor: CHART_COLORS.slice(0, chartData.byDepartment.length),
            borderRadius: 8,
            barPercentage: 0.6,
          },
        ],
      }
    : null;

  const statusChartDataset = chartData
    ? {
        labels: chartData.byStatus.map((s) => s.label),
        datasets: [
          {
            data: chartData.byStatus.map((s) => s.value),
            backgroundColor: ['#6366f1', '#f59e0b', '#22c55e', '#ef4444'].slice(
              0,
              chartData.byStatus.length
            ),
            hoverBackgroundColor: ['#4f46e5', '#d97706', '#16a34a', '#dc2626'].slice(
              0,
              chartData.byStatus.length
            ),
          },
        ],
      }
    : null;

  const monthlyChartDataset = chartData
    ? {
        labels: chartData.byMonth.map((m) => m.label),
        datasets: [
          {
            label: 'Laporan per Bulan',
            data: chartData.byMonth.map((m) => m.value),
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

  /* ---- Template helpers ---- */
  const statusBodyTemplate = (row: LaboratoryReportDto) => (
    <Tag value={row.status} severity={statusSeverity(row.status)} className="text-xs font-semibold" />
  );

  const dateBodyTemplate = (row: LaboratoryReportDto) => (
    <span className="text-sm text-700">
      {row.dateSampling
        ? new Date(row.dateSampling).toLocaleDateString('id-ID', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
          })
        : '—'}
    </span>
  );

  return (
    <div className="flex flex-column gap-4">
      {/* Page Header */}
      <div>
        <h2 className="text-2xl font-bold text-800 m-0">Dashboard LIMS</h2>
        <p className="text-sm text-500 mt-1 mb-0">
          Laboratory Information Management System — Ringkasan KPI
        </p>
      </div>

      {/* KPI Cards Row 1 — Lab Reports */}
      <div className="grid">
        <div className="col-12 md:col-6 xl:col-2">
          <KpiStatCard
            label="Total Laporan Lab"
            value={stats?.totalLabReports ?? 0}
            icon="pi pi-file"
            color="#6366f1"
            bg="#eef2ff"
          />
        </div>
        <div className="col-12 md:col-6 xl:col-2">
          <KpiStatCard
            label="Open"
            value={stats?.open ?? 0}
            icon="pi pi-circle"
            color="#3b82f6"
            bg="#eff6ff"
          />
        </div>
        <div className="col-12 md:col-6 xl:col-2">
          <KpiStatCard
            label="Reviewed"
            value={stats?.reviewed ?? 0}
            icon="pi pi-eye"
            color="#f59e0b"
            bg="#fffbeb"
          />
        </div>
        <div className="col-12 md:col-6 xl:col-2">
          <KpiStatCard
            label="Closed"
            value={stats?.closed ?? 0}
            icon="pi pi-check-circle"
            color="#22c55e"
            bg="#f0fdf4"
          />
        </div>
        <div className="col-12 md:col-6 xl:col-2">
          <KpiStatCard
            label="Rejected"
            value={stats?.rejected ?? 0}
            icon="pi pi-times-circle"
            color="#ef4444"
            bg="#fef2f2"
          />
        </div>
        <div className="col-12 md:col-6 xl:col-2">
          <KpiStatCard
            label="Hasil di Luar Spesifikasi"
            value={stats?.outOfSpec ?? 0}
            icon="pi pi-exclamation-triangle"
            color="#dc2626"
            bg="#fef2f2"
          />
        </div>
        <div className="col-12 md:col-6 xl:col-2">
          <KpiStatCard
            label="Laporan Harian"
            value={stats?.totalDailyReports ?? 0}
            icon="pi pi-calendar"
            color="#14b8a6"
            bg="#f0fdfa"
          />
        </div>
      </div>

      {/* Charts Row */}
      <div className="grid">
        <div className="col-12 lg:col-5">
          <Card title="Laporan per Bulan" className="shadow-1 border-round-xl h-full">
            <div style={{ height: '320px' }}>
              {monthlyChartDataset ? (
                <Chart type="line" data={monthlyChartDataset} options={baseChartOptions} className="h-full" />
              ) : (
                <div className="flex align-items-center justify-content-center h-full text-500">
                  Belum ada data
                </div>
              )}
            </div>
          </Card>
        </div>
        <div className="col-12 lg:col-4">
          <Card title="Laporan per Departemen" className="shadow-1 border-round-xl h-full">
            <div style={{ height: '320px' }}>
              {deptChartDataset ? (
                <Chart
                  type="bar"
                  data={deptChartDataset}
                  options={{
                    ...baseChartOptions,
                    plugins: { ...baseChartOptions.plugins, legend: { display: false } },
                  }}
                  className="h-full"
                />
              ) : (
                <div className="flex align-items-center justify-content-center h-full text-500">
                  Belum ada data
                </div>
              )}
            </div>
          </Card>
        </div>
        <div className="col-12 lg:col-3">
          <Card title="Status Distribusi" className="shadow-1 border-round-xl h-full">
            <div style={{ height: '320px' }}>
              {statusChartDataset ? (
                <Chart
                  type="doughnut"
                  data={statusChartDataset}
                  options={{
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                      legend: {
                        position: 'bottom',
                        labels: { usePointStyle: true, font: { family: 'Inter', size: 12 } },
                      },
                    },
                  }}
                  className="h-full"
                />
              ) : (
                <div className="flex align-items-center justify-content-center h-full text-500">
                  Belum ada data
                </div>
              )}
            </div>
          </Card>
        </div>
      </div>

      {/* Quick Links */}
      <div className="grid">
        <div className="col-12 md:col-4">
          <Card className="shadow-1 border-round-xl cursor-pointer hover:shadow-3 transition-all transition-duration-200"
                onClick={() => window.location.href = '/dashboard/lims/laboratory-reports'}>
            <div className="flex align-items-center gap-3">
              <i className="pi pi-file text-3xl" style={{ color: '#6366f1' }} />
              <div>
                <div className="font-bold text-800">Laporan Laboratorium</div>
                <div className="text-sm text-500">Lihat & kelola laporan lab</div>
              </div>
            </div>
          </Card>
        </div>
        <div className="col-12 md:col-4">
          <Card className="shadow-1 border-round-xl cursor-pointer hover:shadow-3 transition-all transition-duration-200"
                onClick={() => window.location.href = '/dashboard/lims/daily-reports'}>
            <div className="flex align-items-center gap-3">
              <i className="pi pi-calendar text-3xl" style={{ color: '#14b8a6' }} />
              <div>
                <div className="font-bold text-800">Laporan Harian</div>
                <div className="text-sm text-500">Lihat & kelola laporan harian</div>
              </div>
            </div>
          </Card>
        </div>
        <div className="col-12 md:col-4">
          <Card className="shadow-1 border-round-xl cursor-pointer hover:shadow-3 transition-all transition-duration-200"
                onClick={() => window.location.href = '/dashboard/lims/permission-matrix'}>
            <div className="flex align-items-center gap-3">
              <i className="pi pi-shield text-3xl" style={{ color: '#f59e0b' }} />
              <div>
                <div className="font-bold text-800">Permission Matrix</div>
                <div className="text-sm text-500">Kelola hak akses LIMS per role</div>
              </div>
            </div>
          </Card>
        </div>
        <div className="col-12 md:col-4">
          <Card className="shadow-1 border-round-xl cursor-pointer hover:shadow-3 transition-all transition-duration-200"
                onClick={() => window.location.href = '/dashboard/lims/inventory/dashboard'}>
            <div className="flex align-items-center gap-3">
              <i className="pi pi-box text-3xl" style={{ color: '#06b6d4' }} />
              <div>
                <div className="font-bold text-800">Inventaris Alat</div>
                <div className="text-sm text-500">Dashboard stok, kalibrasi & aset alat lab</div>
              </div>
            </div>
          </Card>
        </div>
      </div>

      {/* Recent Lab Reports Table */}
      <Card title="Laporan Laboratorium Terbaru" className="shadow-1 border-round-xl">
        <DataTable
          value={recentReports}
          size="small"
          stripedRows
          responsiveLayout="scroll"
          emptyMessage="Belum ada laporan laboratorium"
          className="border-round-lg"
        >
          <Column field="noReport" header="No. Laporan" className="font-medium" style={{ width: '180px' }} />
          <Column field="location" header="Lokasi" />
          <Column header="Tgl. Sampling" body={dateBodyTemplate} style={{ width: '140px' }} />
          <Column
            header="Departemen"
            body={(row: LaboratoryReportDto) => row.departmentName ?? '—'}
            style={{ width: '140px' }}
          />
          <Column header="Status" body={statusBodyTemplate} style={{ width: '120px' }} />
        </DataTable>
      </Card>
    </div>
  );
};

export default LimsDashboardHome;
