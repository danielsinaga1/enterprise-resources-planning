import { useEffect, useMemo, useState } from 'react';
import { Card } from 'primereact/card';
import { Chart } from 'primereact/chart';
import { Dropdown } from 'primereact/dropdown';
import { ProgressSpinner } from 'primereact/progressspinner';
import { safetyObservationService } from '../../services';
import type { FindingsDashboard as FindingsDashboardData } from '../../types/irms';

/* ============================================================
   DASHBOARD TEMUAN JSO
   ------------------------------------------------------------
   Empat panel mengikuti dashboard QSHE:
     1. Donut Open/Close + Index V
     2. Monthly Findings — garis pembanding antar tahun
     3. Findings Category — donut per kategori K3/PS/LH/...
     4. Follow Up Percentage — bar bertumpuk per bulan

   Semua angka dihitung per TEMUAN, bukan per laporan observasi.
   ============================================================ */

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'];

/** Warna kategori disamakan dengan ObservationCategoryMatrix agar terbaca sebagai hal yang sama. */
const CATEGORY_COLORS: Record<string, string> = {
  K3: '#b91c1c',
  PS: '#c2410c',
  LH: '#2563eb',
  HS: '#059669',
  HK: '#0891b2',
  QA: '#7c3aed',
  OT: '#475569',
};
const FALLBACK_COLORS = ['#6366f1', '#14b8a6', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#64748b'];

/** Warna garis per tahun; tahun tersorot memakai warna paling pekat. */
const YEAR_COLORS = ['#a5b4fc', '#5eead4', '#f59e0b'];

const categoryColor = (label: string, index: number): string => {
  const code = label.trim().slice(0, 2).toUpperCase();
  return CATEGORY_COLORS[code] ?? FALLBACK_COLORS[index % FALLBACK_COLORS.length];
};

/** Ringkas "K3 - Keselamatan dan Kesehatan Kerja" jadi "K3 - Keselamatan…" untuk legenda. */
const shortLabel = (label: string) => (label.length > 26 ? `${label.slice(0, 25)}…` : label);

interface FindingsDashboardProps {
  /** Tahun awal; kosong = tahun berjalan. */
  initialYear?: number;
}

const FindingsDashboard = ({ initialYear }: FindingsDashboardProps) => {
  const [data, setData] = useState<FindingsDashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [year, setYear] = useState<number | undefined>(initialYear);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    safetyObservationService
      .getFindingsDashboard(year)
      .then((d) => {
        if (cancelled) return;
        setData(d);
        // Server yang menentukan tahun default; disimpan agar dropdown tidak kosong.
        if (year === undefined) setYear(d.year);
      })
      .catch((err) => {
        console.error('Gagal memuat dashboard temuan', err);
        if (!cancelled) setData(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [year]);

  const stats = data?.stats;
  const total = stats?.total ?? 0;
  const pct = (n: number) => (total === 0 ? 0 : Math.round((n / total) * 100));

  /* ---- 1. Donut Open/Close ---- */
  const statusDonut = useMemo(() => {
    if (!stats || total === 0) return null;
    return {
      labels: ['Open', 'Close'],
      datasets: [
        {
          data: [stats.open, stats.closed],
          backgroundColor: ['#c0392b', '#2c7a94'],
          borderWidth: 0,
        },
      ],
    };
  }, [stats, total]);

  /* ---- 2. Monthly Findings ---- */
  const monthlyLine = useMemo(() => {
    if (!data?.byMonthPerYear.length) return null;
    return {
      labels: MONTHS,
      datasets: data.byMonthPerYear.map((series, i) => ({
        label: String(series.year),
        data: series.points,
        borderColor: YEAR_COLORS[i % YEAR_COLORS.length],
        backgroundColor: YEAR_COLORS[i % YEAR_COLORS.length],
        // Kurva halus meniru dashboard aslinya, tapi tidak berlebihan agar
        // puncak bulanan tidak terbaca melenceng dari bulannya.
        tension: 0.35,
        borderWidth: 2,
        pointRadius: 3,
        pointHoverRadius: 5,
        fill: false,
      })),
    };
  }, [data]);

  /* ---- 3. Findings Category ---- */
  const categoryDonut = useMemo(() => {
    if (!data?.byCategory.length) return null;
    return {
      labels: data.byCategory.map((c) => shortLabel(c.label)),
      datasets: [
        {
          data: data.byCategory.map((c) => c.value),
          backgroundColor: data.byCategory.map((c, i) => categoryColor(c.label, i)),
          borderWidth: 0,
        },
      ],
    };
  }, [data]);

  /* ---- 4. Follow Up Percentage ---- */
  const followUpBar = useMemo(() => {
    if (!data?.followUpByMonth.length) return null;
    return {
      labels: MONTHS,
      datasets: [
        {
          label: 'Ditindaklanjuti',
          data: data.followUpByMonth.map((p) => p.percentage),
          backgroundColor: '#2c7a94',
        },
        {
          label: 'Belum',
          data: data.followUpByMonth.map((p) => Math.round((100 - p.percentage) * 10) / 10),
          backgroundColor: '#c0392b',
        },
      ],
    };
  }, [data]);

  const donutOptions = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '62%',
    plugins: {
      legend: {
        position: 'bottom' as const,
        labels: { usePointStyle: true, boxWidth: 8, font: { family: 'Inter', size: 11 } },
      },
      tooltip: {
        callbacks: {
          label: (ctx: { label?: string; parsed: number }) =>
            `${ctx.label}: ${ctx.parsed} (${pct(ctx.parsed)}%)`,
        },
      },
    },
  };

  const lineOptions = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index' as const, intersect: false },
    plugins: {
      legend: {
        position: 'bottom' as const,
        labels: { usePointStyle: true, boxWidth: 8, font: { family: 'Inter', size: 11 } },
      },
    },
    scales: {
      x: { grid: { display: false }, ticks: { font: { family: 'Inter', size: 11 } } },
      y: { beginAtZero: true, grid: { color: '#f1f5f9' }, ticks: { precision: 0, font: { family: 'Inter', size: 11 } } },
    },
  };

  const followUpOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'bottom' as const,
        labels: { usePointStyle: true, boxWidth: 8, font: { family: 'Inter', size: 11 } },
      },
      tooltip: {
        callbacks: {
          label: (ctx: { dataset: { label?: string }; parsed: { y: number }; dataIndex: number }) => {
            const point = data?.followUpByMonth[ctx.dataIndex];
            const suffix = point ? ` (${point.closed}/${point.total} temuan)` : '';
            return `${ctx.dataset.label}: ${ctx.parsed.y}%${suffix}`;
          },
        },
      },
    },
    scales: {
      x: { stacked: true, grid: { display: false }, ticks: { font: { family: 'Inter', size: 11 } } },
      y: {
        stacked: true,
        min: 0,
        max: 100,
        grid: { color: '#f1f5f9' },
        ticks: { callback: (v: string | number) => `${v}%`, font: { family: 'Inter', size: 11 } },
      },
    },
  };

  const yearOptions = (data?.availableYears?.length ? data.availableYears : [year ?? new Date().getFullYear()])
    .map((y) => ({ label: String(y), value: y }));

  const emptyState = (text = 'Belum ada data') => (
    <div className="flex align-items-center justify-content-center h-full text-500 text-sm">{text}</div>
  );

  if (loading && !data) {
    return (
      <Card className="shadow-1 border-round-xl">
        <div className="flex align-items-center justify-content-center" style={{ minHeight: 240 }}>
          <ProgressSpinner style={{ width: 44, height: 44 }} />
        </div>
      </Card>
    );
  }

  return (
    <div className="flex flex-column gap-3">
      <div className="flex flex-wrap gap-3 align-items-center justify-content-between">
        <div>
          <h3 className="m-0 text-xl font-bold text-800">Dashboard Temuan</h3>
          <p className="mt-1 mb-0 text-sm text-500">
            Temuan JSO — status tindak lanjut, kategori, dan tren bulanan
          </p>
        </div>
        <Dropdown
          value={year}
          options={yearOptions}
          onChange={(e) => setYear(e.value)}
          className="w-8rem"
          disabled={loading}
        />
      </div>

      <div className="grid">
        {/* ── 1. Open vs Close ── */}
        <div className="col-12 lg:col-5">
          <Card className="h-full shadow-1 border-round-xl">
            <div className="flex align-items-start justify-content-between mb-2">
              <span className="text-3xl font-bold" style={{ color: '#2c7a94' }}>{data?.year ?? '—'}</span>
              <div className="text-right">
                <div className="text-xs text-500">Index V</div>
                <div className="text-lg font-semibold" style={{ color: '#b45309' }}>
                  {stats?.indexV != null ? stats.indexV.toFixed(4).replace('.', ',') : '—'}
                </div>
              </div>
            </div>

            <div className="relative" style={{ height: 230 }}>
              {statusDonut ? (
                <>
                  <Chart type="doughnut" data={statusDonut} options={donutOptions} className="h-full" />
                  {/* Total ditaruh di lubang donut — sama seperti dashboard acuan. */}
                  <div
                    className="absolute flex flex-column align-items-center justify-content-center"
                    style={{ top: 0, left: 0, right: 0, height: 'calc(100% - 42px)', pointerEvents: 'none' }}
                  >
                    <span className="text-4xl font-bold" style={{ color: '#2c7a94' }}>{total}</span>
                    <span className="text-xs text-500">temuan</span>
                  </div>
                </>
              ) : (
                emptyState('Belum ada temuan di tahun ini')
              )}
            </div>

            <div className="flex justify-content-around pt-2 border-top-1 surface-border">
              <div className="text-center">
                <div className="text-lg font-bold" style={{ color: '#c0392b' }}>{stats?.open ?? 0}</div>
                <div className="text-xs text-500">Open · {pct(stats?.open ?? 0)}%</div>
              </div>
              <div className="text-center">
                <div className="text-lg font-bold" style={{ color: '#2c7a94' }}>{stats?.closed ?? 0}</div>
                <div className="text-xs text-500">Close · {pct(stats?.closed ?? 0)}%</div>
              </div>
            </div>
          </Card>
        </div>

        {/* ── 2. Monthly Findings ── */}
        <div className="col-12 lg:col-7">
          <Card
            className="h-full shadow-1 border-round-xl"
            title={<span className="text-base font-semibold" style={{ color: '#2c7a94' }}>Monthly Findings</span>}
          >
            <div style={{ height: 268 }}>
              {monthlyLine ? (
                <Chart type="line" data={monthlyLine} options={lineOptions} className="h-full" />
              ) : (
                emptyState()
              )}
            </div>
          </Card>
        </div>

        {/* ── 3. Findings Category ── */}
        <div className="col-12 lg:col-5">
          <Card
            className="h-full shadow-1 border-round-xl"
            title={<span className="text-base font-semibold" style={{ color: '#2c7a94' }}>Findings Category</span>}
          >
            <div style={{ height: 292 }}>
              {categoryDonut ? (
                <Chart type="doughnut" data={categoryDonut} options={donutOptions} className="h-full" />
              ) : (
                emptyState()
              )}
            </div>
          </Card>
        </div>

        {/* ── 4. Follow Up Percentage ── */}
        <div className="col-12 lg:col-7">
          <Card
            className="h-full shadow-1 border-round-xl"
            title={<span className="text-base font-semibold" style={{ color: '#2c7a94' }}>Follow Up Percentage</span>}
          >
            <div style={{ height: 292 }}>
              {followUpBar ? (
                <Chart type="bar" data={followUpBar} options={followUpOptions} className="h-full" />
              ) : (
                emptyState()
              )}
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default FindingsDashboard;
