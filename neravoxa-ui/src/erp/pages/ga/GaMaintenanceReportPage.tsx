/**
 * Laporan Biaya Pemeliharaan (/ga/maintenance/report) — maintenance cost per asset from
 * GET /ga/maintenance/cost-report: KPIs, monthly cost trend (labor / supplier / parts),
 * top assets and a per-asset table. Period lives in the URL like the fleet report;
 * Excel export of the filtered rows. Series colors are a validated 3-slot palette
 * (light & dark checked with the dataviz validator) in fixed order.
 */
import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { AlarmClock, ClipboardCheck, Download, Search, Timer, Wallet } from 'lucide-react';
import { Link } from '@/lib/router';
import { toast } from 'sonner';
import api from '@/lib/api';
import { exportToExcel } from '@/lib/exportExcel';
import { formatDate, formatNumber, formatPercent, formatRupiah } from '@/lib/format';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { DataTable, PageHeader, StatCard } from '@/components/shared';
import { ChartTooltipBox, Panel, PanelState } from '@/components/inventory/dashboard-kit';
import { CHART_AXIS, CHART_GRID_STROKE } from '@/components/inventory/dashboard-hooks';
import { ReportPeriodToolbar } from '@/components/ga/vehicle/ReportPeriodToolbar';
import { useReportPeriod } from '@/components/ga/vehicle/useReportPeriod';
import { monthLabel } from '@/components/ga/vehicle/fleet-report-model';
import { UnitHeader } from '@/components/ga/vehicle/vehicle-ui';
import { FLUSH_TABLE } from '@/components/ga/vehicle/ui-classes';
import { useChartAnimation } from '@/contexts/motion-context';
import { useTheme } from '@/contexts/ThemeContext';
import { useTranslation } from 'react-i18next';

type Money = number | string;
type CostKey = 'labor' | 'external' | 'parts';

interface CostCounts {
  labor: Money;
  external: Money;
  parts: Money;
  total: Money;
  workOrders: number;
  preventive: number;
  corrective: number;
  late: number;
}

interface AssetCost extends CostCounts {
  assetId: string;
  code: string;
  name: string;
  category: string | null;
  status: string;
  lastCompletedAt: string;
}

interface CostReport {
  period: { from: string; to: string; months: number };
  totals: CostCounts & {
    laborHours: Money;
    assetCount: number;
    lateRate: Money;
    preventiveRate: Money;
    avgResolutionHours: Money | null;
    costPerMonth: Money;
    openOverdue: number;
  };
  monthlyTrend: (CostCounts & { month: string })[];
  assets: AssetCost[];
}

const KEYS: CostKey[] = ['labor', 'external', 'parts'];
/** Fixed order; validated (CVD + normal-vision separation, lightness, chroma, contrast) per mode. */
const COLORS: Record<'light' | 'dark', Record<CostKey, string>> = {
  light: { labor: '#0D9488', external: '#2563EB', parts: '#A86F0A' },
  dark: { labor: '#0D9488', external: '#3B82F6', parts: '#D97706' },
};
const num = (v: Money | null | undefined) => (v == null ? 0 : Number(v) || 0);
const money = (v: Money) => formatRupiah(num(v), { symbol: false });
const compact = (v: number) => formatRupiah(v, { compact: true, symbol: false });

export function GaMaintenanceReportPage() {
  const { t } = useTranslation('eam');
  const animate = useChartAnimation();
  const { resolvedTheme } = useTheme();
  const color = COLORS[resolvedTheme === 'dark' ? 'dark' : 'light'];
  const { period, setPreset, setRange } = useReportPeriod();
  const [search, setSearch] = useState('');
  const label = (k: CostKey) => t(`maintenanceReport.cost.${k}`);

  const query = useQuery<CostReport>({
    queryKey: ['ga-maintenance-cost-report', period.startDate, period.endDate],
    queryFn: async () => (await api.get('/ga/maintenance/cost-report', { params: { from: period.startDate, to: period.endDate } })).data,
    placeholderData: keepPreviousData,
  });
  const data = query.data;
  const loading = !data && query.isLoading;

  const rows = useMemo(() => {
    const list = data?.assets ?? [];
    const q = search.trim().toLowerCase();
    return q ? list.filter((a) => [a.code, a.name, a.category].some((f) => f?.toLowerCase().includes(q))) : list;
  }, [data, search]);

  const trend = useMemo(
    () => (data?.monthlyTrend ?? []).map((m) => ({ label: monthLabel(m.month), labor: num(m.labor), external: num(m.external), parts: num(m.parts), total: num(m.total), workOrders: m.workOrders })),
    [data],
  );
  const hasTrend = trend.some((m) => m.total > 0);
  const total = num(data?.totals.total);
  const share = (k: CostKey) => (total > 0 ? num(data?.totals[k]) / total : 0);
  const top = (data?.assets ?? []).slice(0, 8);

  const handleExport = async () => {
    if (!rows.length) {
      toast.error(t('maintenanceReport.noExportData'));
      return;
    }
    try {
      await exportToExcel(
        rows.map((a) => ({
          [t('maintenanceReport.export.code')]: a.code,
          [t('maintenanceReport.export.name')]: a.name,
          [t('maintenanceReport.export.category')]: a.category ?? '',
          [t('maintenanceReport.export.workOrders')]: a.workOrders,
          [t('maintenanceReport.export.preventive')]: a.preventive,
          [t('maintenanceReport.export.corrective')]: a.corrective,
          [t('maintenanceReport.export.late')]: a.late,
          [label('labor')]: num(a.labor),
          [label('external')]: num(a.external),
          [label('parts')]: num(a.parts),
          [t('maintenanceReport.export.total')]: num(a.total),
          [t('maintenanceReport.export.lastCompleted')]: formatDate(a.lastCompletedAt),
        })),
        `${t('maintenanceReport.filePrefix')}-${period.startDate}_${period.endDate}`,
      );
      toast.success(t('maintenanceReport.exported'));
    } catch {
      toast.error(t('maintenanceReport.exportFailed'));
    }
  };

  const columns = useMemo<ColumnDef<AssetCost>[]>(
    () => [
      {
        id: 'asset',
        header: t('maintenanceReport.asset'),
        meta: { cellClassName: 'min-w-44' },
        cell: ({ row }) => {
          const a = row.original;
          return (
            <div className="min-w-0">
              <Link to={`/ga/assets?asset=${encodeURIComponent(a.assetId)}`} className="truncate text-sm font-medium text-primary-hover underline-offset-2 hover:underline">
                {a.name}
              </Link>
              <p className="num truncate text-xs text-muted-foreground">{[a.code, a.category].filter(Boolean).join(' · ')}</p>
            </div>
          );
        },
      },
      {
        id: 'workOrders',
        header: t('maintenanceReport.workOrders'),
        meta: { align: 'right' },
        cell: ({ row }) => (
          <div className="text-right">
            <span className="num text-foreground">{formatNumber(row.original.workOrders)}</span>
            <p className="text-xs text-muted-foreground">
              {t('maintenanceReport.mix', { preventive: row.original.preventive, corrective: row.original.corrective })}
            </p>
          </div>
        ),
      },
      {
        id: 'late',
        header: t('maintenanceReport.late'),
        meta: { align: 'right', hideBelow: 'sm' },
        cell: ({ row }) =>
          row.original.late > 0 ? (
            <span className="num font-medium text-warning-text">{formatNumber(row.original.late)}</span>
          ) : (
            <span className="text-muted-foreground">—</span>
          ),
      },
      ...KEYS.map<ColumnDef<AssetCost>>((k) => ({
        id: k,
        header: () => <UnitHeader label={label(k)} />,
        meta: { align: 'right', hideBelow: k === 'labor' ? 'md' : 'lg' },
        cell: ({ row }) => (num(row.original[k]) ? money(row.original[k]) : <span className="text-muted-foreground">—</span>),
      })),
      {
        id: 'total',
        header: () => <UnitHeader label={t('maintenanceReport.total')} />,
        meta: { align: 'right' },
        cell: ({ row }) => <span className="font-semibold text-foreground">{money(row.original.total)}</span>,
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <div className="space-y-5 pb-8">
      <PageHeader
        title={t('maintenanceReport.title')}
        description={t('maintenanceReport.description')}
        breadcrumbs={[{ label: t('maintenanceReport.maintenance'), href: '/ga/maintenance' }, { label: t('maintenanceReport.title') }]}
        actions={
          <Button variant="outline" onClick={handleExport} disabled={!rows.length}>
            <Download aria-hidden />
            {t('maintenanceReport.exportExcel')}
          </Button>
        }
      >
        <ReportPeriodToolbar period={period} onPresetChange={setPreset} onRangeChange={setRange} />
      </PageHeader>

      {query.isError && !data ? (
        <PanelState error={query.error} onRetry={() => query.refetch()}>
          {null}
        </PanelState>
      ) : (
        <>
          <section aria-label={t('maintenanceReport.summaryLabel')} className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatCard
              label={t('maintenanceReport.totalCost')}
              icon={Wallet}
              loading={loading}
              value={data ? formatRupiah(total, { compact: true }) : '—'}
              valueTitle={data ? formatRupiah(total) : undefined}
              hint={data ? t('maintenanceReport.perMonthHint', { amount: formatRupiah(num(data.totals.costPerMonth), { compact: true }) }) : undefined}
            />
            <StatCard
              label={t('maintenanceReport.completed')}
              icon={ClipboardCheck}
              loading={loading}
              value={data ? formatNumber(data.totals.workOrders) : '—'}
              hint={data ? t('maintenanceReport.completedHint', { share: formatPercent(num(data.totals.preventiveRate)), assets: data.totals.assetCount }) : undefined}
            />
            <StatCard
              label={t('maintenanceReport.lateRate')}
              icon={AlarmClock}
              loading={loading}
              value={data && data.totals.workOrders ? formatPercent(num(data.totals.lateRate)) : '—'}
              hint={data ? t('maintenanceReport.lateHint', { late: data.totals.late, total: data.totals.workOrders, open: data.totals.openOverdue }) : undefined}
            />
            <StatCard
              label={t('maintenanceReport.resolution')}
              icon={Timer}
              loading={loading}
              value={data?.totals.avgResolutionHours != null ? t('maintenanceReport.hours', { n: formatNumber(num(data.totals.avgResolutionHours)) }) : '—'}
              hint={data ? t('maintenanceReport.resolutionHint', { hours: formatNumber(num(data.totals.laborHours)) }) : undefined}
            />
          </section>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-5">
            <Panel
              className="lg:col-span-3"
              title={t('maintenanceReport.trend')}
              description={data ? t('maintenanceReport.periodDescription', { count: data.period.months, from: formatDate(data.period.from), to: formatDate(data.period.to) }) : undefined}
            >
              <PanelState loading={loading} skeleton="chart" empty={!hasTrend} emptyTitle={t('maintenanceReport.noCost')}>
                <figure className="m-0">
                  <div className="h-64" aria-hidden>
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart accessibilityLayer={false} data={trend} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
                        <CartesianGrid vertical={false} stroke={CHART_GRID_STROKE} strokeDasharray="3 3" />
                        <XAxis dataKey="label" {...CHART_AXIS} interval="preserveStartEnd" minTickGap={8} />
                        <YAxis {...CHART_AXIS} width={52} tickFormatter={(v: number) => compact(v)} />
                        <Tooltip
                          cursor={{ fill: 'var(--accent)' }}
                          content={({ active, payload }) => {
                            const m = active ? (payload?.[0]?.payload as (typeof trend)[number] | undefined) : undefined;
                            if (!m) return null;
                            return (
                              <ChartTooltipBox
                                title={m.label}
                                rows={[
                                  ...KEYS.filter((k) => m[k] > 0).map((k) => ({ label: label(k), value: formatRupiah(m[k]), color: color[k] })),
                                  { label: t('maintenanceReport.total'), value: formatRupiah(m.total) },
                                  { label: t('maintenanceReport.workOrders'), value: formatNumber(m.workOrders) },
                                ]}
                              />
                            );
                          }}
                        />
                        {KEYS.map((k, i) => (
                          <Bar
                            key={k}
                            isAnimationActive={animate}
                            dataKey={k}
                            stackId="cost"
                            fill={color[k]}
                            stroke="var(--card)"
                            strokeWidth={2}
                            maxBarSize={28}
                            radius={i === KEYS.length - 1 ? [4, 4, 0, 0] : 0}
                          />
                        ))}
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                  <figcaption className="mt-3 grid grid-cols-1 gap-1.5 text-xs sm:grid-cols-3">
                    {KEYS.map((k) => (
                      <span key={k} className="flex items-center gap-1.5 text-muted-foreground">
                        <span className="size-2.5 shrink-0 rounded-sm" style={{ background: color[k] }} aria-hidden />
                        <span className="text-foreground">{label(k)}</span>
                        <span className="num">
                          {formatRupiah(num(data?.totals[k]), { compact: true })} · {formatPercent(share(k))}
                        </span>
                      </span>
                    ))}
                    <span className="sr-only">
                      {t('maintenanceReport.trendCaption', { list: trend.map((m) => `${m.label} ${formatRupiah(m.total)}`).join('; ') })}
                    </span>
                  </figcaption>
                </figure>
              </PanelState>
            </Panel>

            <Panel className="lg:col-span-2" title={t('maintenanceReport.topTitle')} description={t('maintenanceReport.topDescription')}>
              <PanelState loading={loading} empty={!top.length} emptyTitle={t('maintenanceReport.noCost')}>
                <ol className="space-y-2.5">
                  {top.map((a, i) => {
                    const max = num(top[0]?.total) || 1;
                    return (
                      <li key={a.assetId} className="flex items-center gap-3">
                        <span className="num w-5 text-right text-xs font-semibold text-muted-foreground">{i + 1}</span>
                        <div className="min-w-0 flex-1">
                          <div className="mb-1 flex items-center justify-between gap-2 text-sm">
                            <Link to={`/ga/assets?asset=${encodeURIComponent(a.assetId)}`} className="min-w-0 truncate font-medium text-foreground hover:underline">
                              {a.name}
                            </Link>
                            <span className="num shrink-0 text-muted-foreground">{formatRupiah(num(a.total), { compact: true })}</span>
                          </div>
                          <div className="flex h-1.5 gap-0.5 overflow-hidden rounded-sm bg-muted" aria-hidden>
                            {KEYS.map((k) =>
                              num(a[k]) > 0 ? <div key={k} className="h-full" style={{ width: `${(num(a[k]) / max) * 100}%`, background: color[k] }} /> : null,
                            )}
                          </div>
                        </div>
                      </li>
                    );
                  })}
                </ol>
              </PanelState>
            </Panel>
          </div>

          <Panel
            title={t('maintenanceReport.detailTitle')}
            description={data ? t('maintenanceReport.detailDescription', { shown: formatNumber(rows.length), total: formatNumber(data.assets.length) }) : undefined}
            bodyClassName="p-0"
            action={
              <div className="relative w-full sm:w-64">
                <label htmlFor="maintenance-report-q" className="sr-only">
                  {t('maintenanceReport.search')}
                </label>
                <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
                <Input id="maintenance-report-q" type="search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder={t('maintenanceReport.searchPlaceholder')} className="pl-9" />
              </div>
            }
          >
            <DataTable
              label={t('maintenanceReport.tableLabel')}
              itemLabel={t('maintenanceReport.itemLabel')}
              columns={columns}
              data={rows}
              getRowId={(r) => r.assetId}
              loading={loading}
              fetching={query.isFetching && query.isPlaceholderData}
              filtered={!!search.trim()}
              onClearFilters={() => setSearch('')}
              stickyHeader={false}
              pageSize={25}
              className={FLUSH_TABLE}
              empty={{ title: t('maintenanceReport.emptyTitle'), description: t('maintenanceReport.emptyDescription') }}
            />
            {rows.length > 0 && (
              <p className="flex flex-wrap items-center justify-end gap-x-4 gap-y-1 border-t border-border px-4 py-2.5 text-sm">
                <span className="text-muted-foreground">{t('maintenanceReport.footerTotal', { count: rows.length, n: formatNumber(rows.length) })}</span>
                <span className="num font-semibold text-foreground">{formatRupiah(rows.reduce((s, a) => s + num(a.total), 0))}</span>
              </p>
            )}
          </Panel>
        </>
      )}
    </div>
  );
}

export default GaMaintenanceReportPage;
