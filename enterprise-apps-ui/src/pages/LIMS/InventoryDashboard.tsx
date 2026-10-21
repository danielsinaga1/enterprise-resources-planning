import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card } from 'primereact/card';
import { Chart } from 'primereact/chart';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { Tag } from 'primereact/tag';
import { ProgressSpinner } from 'primereact/progressspinner';
import { KpiStatCard } from '../../components/irms/KpiStatCard';
import { inventoryService } from '../../services';
import type { InventoryItemDto, InventoryStats } from '../../types/lims';

/* ============================================================
   CHART CONFIG (matches IRMS/LIMS dashboards)
   ============================================================ */
const baseChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { position: 'bottom' as const, labels: { usePointStyle: true, font: { family: 'Inter', size: 12 } } },
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

const STATUS_COLORS: Record<string, string> = {
  Available: '#22c55e',
  Borrowed: '#f59e0b',
  Service: '#ef4444',
};

const emptyStats: InventoryStats = {
  totalItems: 0, totalUnits: 0, totalValue: 0, lowStockCount: 0,
  calibrationCount: 0, borrowedCount: 0, serviceCount: 0,
};

interface ChartItem { label: string; value: number; }

const fmtRupiah = (v: number) => 'Rp ' + (Number.isFinite(v) ? v : 0).toLocaleString('id-ID');
const fmtDate = (s: string | null) =>
  s ? new Date(s).toLocaleDateString('id-ID', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

const conditionSeverity = (c: string | null): 'success' | 'warning' | 'danger' | undefined => {
  switch (c) {
    case 'Baik': return 'success';
    case 'Rusak Ringan': return 'warning';
    case 'Rusak Berat': return 'danger';
    default: return undefined;
  }
};

const InventoryDashboard = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState<InventoryStats>(emptyStats);
  const [byCategory, setByCategory] = useState<ChartItem[]>([]);
  const [byStatus, setByStatus] = useState<ChartItem[]>([]);
  const [items, setItems] = useState<InventoryItemDto[]>([]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [dash, list] = await Promise.all([
        inventoryService.getDashboardStats(),
        inventoryService.list({ page: 0, size: 500, sortBy: 'name', sortDir: 'asc' }),
      ]);
      if (dash.data?.stats) setStats(dash.data.stats);
      setByCategory(dash.data?.chartData?.byCategory ?? []);
      setByStatus(dash.data?.chartData?.byStatus ?? []);
      setItems(Array.isArray(list.data) ? list.data : []);
    } catch (err) {
      console.error('Failed to load inventory dashboard', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  if (loading) {
    return (
      <div className="flex align-items-center justify-content-center" style={{ minHeight: '60vh' }}>
        <ProgressSpinner strokeWidth="3" />
      </div>
    );
  }

  /* ---- Derived alert lists ---- */
  const lowStockItems = items.filter((i) => i.lowStock);
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const in30 = new Date(today); in30.setDate(in30.getDate() + 30);
  const calibrationDueItems = items
    .filter((i) => i.nextCalibrationDate && new Date(i.nextCalibrationDate) >= today && new Date(i.nextCalibrationDate) <= in30)
    .sort((a, b) => new Date(a.nextCalibrationDate!).getTime() - new Date(b.nextCalibrationDate!).getTime());

  /* ---- Chart datasets ---- */
  const categoryDataset = byCategory.length
    ? {
        labels: byCategory.map((c) => c.label),
        datasets: [{
          label: 'Jumlah Alat',
          data: byCategory.map((c) => c.value),
          backgroundColor: CHART_COLORS.slice(0, byCategory.length),
          borderRadius: 8,
          barPercentage: 0.6,
        }],
      }
    : null;

  const statusDataset = byStatus.length
    ? {
        labels: byStatus.map((s) => s.label),
        datasets: [{
          data: byStatus.map((s) => s.value),
          backgroundColor: byStatus.map((s, i) => STATUS_COLORS[s.label] ?? CHART_COLORS[i % CHART_COLORS.length]),
        }],
      }
    : null;

  const chartOrEmpty = (dataset: unknown, type: 'bar' | 'doughnut', opts: object) =>
    dataset ? (
      <Chart type={type} data={dataset as object} options={opts} className="h-full" />
    ) : (
      <div className="flex align-items-center justify-content-center h-full text-500">Belum ada data</div>
    );

  /* ---- Table body templates ---- */
  const stockBody = (row: InventoryItemDto) => (
    <span className="text-orange-600 font-semibold">
      {row.quantity} <span className="text-500 text-xs font-normal">/ min {row.minStock}</span>
    </span>
  );
  const calibBody = (row: InventoryItemDto) => <span className="text-sm">{fmtDate(row.nextCalibrationDate)}</span>;
  const conditionBody = (row: InventoryItemDto) =>
    row.condition ? <Tag value={row.condition} severity={conditionSeverity(row.condition)} className="text-xs" /> : '—';

  return (
    <div className="flex flex-column gap-4">
      {/* Header + actions */}
      <div className="flex align-items-center justify-content-between flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold text-800 m-0">Dashboard Inventaris</h2>
          <p className="text-sm text-500 mt-1 mb-0">Ringkasan stok, aset, kalibrasi & kondisi alat laboratorium</p>
        </div>
        <div className="flex gap-2 flex-wrap">
          <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined onClick={loadData}
            className="border-round-lg" />
          <Button label="Daftar Alat" icon="pi pi-list" severity="info" outlined onClick={() => navigate('/dashboard/lims/inventory')}
            className="border-round-lg" />
          <Button label="Tambah Alat" icon="pi pi-plus" onClick={() => navigate('/dashboard/lims/inventory/create')}
            className="border-round-lg shadow-1 font-semibold" />
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid">
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Jenis Alat" value={stats.totalItems} icon="pi pi-box" color="#6366f1" bg="#eef2ff" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Total Unit" value={stats.totalUnits} icon="pi pi-database" color="#06b6d4" bg="#ecfeff" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Total Nilai Aset" value={fmtRupiah(stats.totalValue)} icon="pi pi-wallet" color="#22c55e" bg="#f0fdf4" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Stok Rendah" value={stats.lowStockCount} icon="pi pi-exclamation-triangle" color="#f97316" bg="#fff7ed" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Perlu Kalibrasi (30 hari)" value={stats.calibrationCount} icon="pi pi-calendar-clock" color="#8b5cf6" bg="#f5f3ff" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Sedang Dipinjam" value={stats.borrowedCount} icon="pi pi-arrow-right-arrow-left" color="#f59e0b" bg="#fffbeb" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Perlu Servis" value={stats.serviceCount} icon="pi pi-wrench" color="#ef4444" bg="#fef2f2" />
        </div>
        <div className="col-12 md:col-6 xl:col-3">
          <KpiStatCard label="Alat Tersedia" value={stats.totalItems - stats.borrowedCount - stats.serviceCount} icon="pi pi-check-circle" color="#14b8a6" bg="#f0fdfa" />
        </div>
      </div>

      {/* Charts */}
      <div className="grid">
        <div className="col-12 lg:col-8">
          <Card title="Alat per Kategori" className="shadow-1 border-round-xl h-full">
            <div style={{ height: '320px' }}>
              {chartOrEmpty(categoryDataset, 'bar', { ...baseChartOptions, plugins: { ...baseChartOptions.plugins, legend: { display: false } } })}
            </div>
          </Card>
        </div>
        <div className="col-12 lg:col-4">
          <Card title="Distribusi Status" className="shadow-1 border-round-xl h-full">
            <div style={{ height: '320px' }}>
              {chartOrEmpty(statusDataset, 'doughnut', noAxisOptions)}
            </div>
          </Card>
        </div>
      </div>

      {/* Alert tables */}
      <div className="grid">
        <div className="col-12 lg:col-6">
          <Card className="shadow-1 border-round-xl h-full">
            <div className="flex align-items-center gap-2 mb-3">
              <i className="pi pi-exclamation-triangle text-orange-500" />
              <span className="font-bold text-800">Stok Rendah</span>
              <Tag value={lowStockItems.length} severity="warning" className="ml-auto" />
            </div>
            <DataTable value={lowStockItems} size="small" stripedRows responsiveLayout="scroll"
              emptyMessage="Tidak ada alat stok rendah ✅" className="border-round-lg"
              paginator={lowStockItems.length > 5} rows={5}>
              <Column field="itemCode" header="Kode" className="font-medium" style={{ width: '130px' }} />
              <Column field="name" header="Nama Alat" />
              <Column header="Stok" body={stockBody} style={{ width: '120px' }} />
            </DataTable>
          </Card>
        </div>
        <div className="col-12 lg:col-6">
          <Card className="shadow-1 border-round-xl h-full">
            <div className="flex align-items-center gap-2 mb-3">
              <i className="pi pi-calendar-clock text-purple-500" />
              <span className="font-bold text-800">Perlu Kalibrasi (≤ 30 hari)</span>
              <Tag value={calibrationDueItems.length} severity="info" className="ml-auto" />
            </div>
            <DataTable value={calibrationDueItems} size="small" stripedRows responsiveLayout="scroll"
              emptyMessage="Tidak ada kalibrasi jatuh tempo ✅" className="border-round-lg"
              paginator={calibrationDueItems.length > 5} rows={5}>
              <Column field="itemCode" header="Kode" className="font-medium" style={{ width: '130px' }} />
              <Column field="name" header="Nama Alat" />
              <Column header="Kondisi" body={conditionBody} style={{ width: '120px' }} />
              <Column header="Jatuh Tempo" body={calibBody} style={{ width: '120px' }} />
            </DataTable>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default InventoryDashboard;
