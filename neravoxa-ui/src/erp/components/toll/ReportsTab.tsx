/** Laporan Jasa Maklon: rendemen per pelanggan & order, dan biaya simpan titipan per periode. */
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import api from '@/lib/api';
import { formatRupiah } from '@/lib/format';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { DateInput, Field } from '@/components/shared';
import { TOLL_BASE, num, qty, type Num } from './common';

interface YieldFigures {
  inputQty: Num; mainOutputQty: Num; byProductQty: Num; shrinkQty: Num; yieldPct: Num; byProductPct: Num; shrinkPct: Num; yieldVariancePct?: Num;
}
interface YieldReport {
  orders: Array<YieldFigures & { tollOrderId: string; orderNumber: string; partyName: string; runs: Num; expectedYieldPct: Num }>;
  parties: Array<YieldFigures & { partyId: string; partyName: string; runs: number }>;
  totals: YieldFigures & { runs: number };
}
interface StorageReport {
  startDate: string;
  endDate: string;
  data: Array<{ tollOrderId: string; orderNumber: string; partyName: string; storageRate: Num; storageFreeDays: number; qtyDays: Num; chargeableDays: number; fee: Num }>;
  totals: { qtyDays: Num; fee: Num };
}

const monthStart = () => { const d = new Date(); return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().split('T')[0]; };
const today = () => new Date().toISOString().split('T')[0];
const pct = (v: Num) => (v === null || v === undefined ? '—' : `${qty(v)}%`);
const th = 'px-2 py-1.5 text-xs font-medium text-muted-foreground';
const td = 'num px-2 py-1.5 text-right';

export default function ReportsTab() {
  const { t } = useTranslation('toll');
  const [startDate, setStartDate] = useState(monthStart());
  const [endDate, setEndDate] = useState(today());
  const params = { startDate: startDate || undefined, endDate: endDate || undefined };

  const yieldQ = useQuery({
    queryKey: ['toll-reports', 'yield', params],
    queryFn: async () => (await api.get<YieldReport>(`${TOLL_BASE}/reports/yield`, { params })).data,
  });
  const storageQ = useQuery({
    queryKey: ['toll-reports', 'storage', params],
    queryFn: async () => (await api.get<StorageReport>(`${TOLL_BASE}/reports/storage`, { params })).data,
  });

  const yieldCells = (r: YieldFigures) => (
    <>
      <td className={td}>{qty(r.inputQty)}</td>
      <td className={td}>{qty(r.mainOutputQty)}</td>
      <td className={td}>{qty(r.byProductQty)}</td>
      <td className={td}>{qty(r.shrinkQty)} <span className="text-xs text-muted-foreground">({pct(r.shrinkPct)})</span></td>
      <td className={`${td} font-semibold`}>{pct(r.yieldPct)}</td>
    </>
  );

  return (
    <div className="space-y-4">
      <div className="grid max-w-md grid-cols-2 gap-3">
        <Field label={t('reports.period')}><DateInput value={startDate} onChange={setStartDate} /></Field>
        <Field label="—" hideLabel><DateInput value={endDate} onChange={setEndDate} /></Field>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('reports.yieldTitle')}</CardTitle>
          <p className="text-sm text-muted-foreground">{t('reports.yieldDescription')}</p>
        </CardHeader>
        <CardContent className="space-y-4 overflow-x-auto">
          {!yieldQ.data || yieldQ.data.orders.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t('reports.empty')}</p>
          ) : (
            <>
              <table className="w-full text-sm">
                <caption className="pb-1 text-left text-xs font-semibold text-foreground">{t('reports.byParty')}</caption>
                <thead><tr>
                  <th className={`${th} text-left`}>{t('reports.cols.party')}</th>
                  <th className={`${th} text-right`}>{t('reports.cols.runs')}</th>
                  <th className={`${th} text-right`}>{t('reports.cols.input')}</th>
                  <th className={`${th} text-right`}>{t('reports.cols.output')}</th>
                  <th className={`${th} text-right`}>{t('reports.cols.byProduct')}</th>
                  <th className={`${th} text-right`}>{t('reports.cols.shrink')}</th>
                  <th className={`${th} text-right`}>{t('reports.cols.yield')}</th>
                </tr></thead>
                <tbody>
                  {yieldQ.data.parties.map((p) => (
                    <tr key={p.partyId} className="border-t border-border">
                      <td className="px-2 py-1.5">{p.partyName}</td>
                      <td className={td}>{p.runs}</td>
                      {yieldCells(p)}
                    </tr>
                  ))}
                  <tr className="border-t border-border bg-muted/40 font-semibold">
                    <td className="px-2 py-1.5">{t('reports.total')}</td>
                    <td className={td}>{yieldQ.data.totals.runs}</td>
                    {yieldCells(yieldQ.data.totals)}
                  </tr>
                </tbody>
              </table>
              <table className="w-full text-sm">
                <caption className="pb-1 text-left text-xs font-semibold text-foreground">{t('reports.byOrder')}</caption>
                <thead><tr>
                  <th className={`${th} text-left`}>{t('reports.cols.order')}</th>
                  <th className={`${th} text-right`}>{t('reports.cols.input')}</th>
                  <th className={`${th} text-right`}>{t('reports.cols.output')}</th>
                  <th className={`${th} text-right`}>{t('reports.cols.byProduct')}</th>
                  <th className={`${th} text-right`}>{t('reports.cols.shrink')}</th>
                  <th className={`${th} text-right`}>{t('reports.cols.yield')}</th>
                  <th className={`${th} text-right`}>{t('reports.cols.variance')}</th>
                </tr></thead>
                <tbody>
                  {yieldQ.data.orders.map((o) => (
                    <tr key={o.tollOrderId} className="border-t border-border">
                      <td className="px-2 py-1.5">{o.orderNumber}<span className="block text-xs text-muted-foreground">{o.partyName}</span></td>
                      {yieldCells(o)}
                      <td className={`${td} ${num(o.yieldVariancePct) < 0 ? 'text-danger-text' : ''}`}>
                        {o.yieldVariancePct == null ? '—' : pct(o.yieldVariancePct)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('reports.storageTitle')}</CardTitle>
          <p className="text-sm text-muted-foreground">{t('reports.storageDescription')}</p>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          {!storageQ.data || storageQ.data.data.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t('reports.empty')}</p>
          ) : (
            <table className="w-full text-sm">
              <thead><tr>
                <th className={`${th} text-left`}>{t('reports.cols.order')}</th>
                <th className={`${th} text-right`}>{t('reports.cols.rate')}</th>
                <th className={`${th} text-right`}>{t('reports.cols.freeDays')}</th>
                <th className={`${th} text-right`}>{t('reports.cols.days')}</th>
                <th className={`${th} text-right`}>{t('reports.cols.qtyDays')}</th>
                <th className={`${th} text-right`}>{t('reports.cols.fee')}</th>
              </tr></thead>
              <tbody>
                {storageQ.data.data.map((r) => (
                  <tr key={r.tollOrderId} className="border-t border-border">
                    <td className="px-2 py-1.5">{r.orderNumber}<span className="block text-xs text-muted-foreground">{r.partyName}</span></td>
                    <td className={td}>{formatRupiah(num(r.storageRate), { decimals: 2 })}</td>
                    <td className={td}>{r.storageFreeDays}</td>
                    <td className={td}>{r.chargeableDays}</td>
                    <td className={td}>{qty(r.qtyDays)}</td>
                    <td className={`${td} font-semibold`}>{formatRupiah(num(r.fee))}</td>
                  </tr>
                ))}
                <tr className="border-t border-border bg-muted/40 font-semibold">
                  <td className="px-2 py-1.5" colSpan={4}>{t('reports.total')}</td>
                  <td className={td}>{qty(storageQ.data.totals.qtyDays)}</td>
                  <td className={td}>{formatRupiah(num(storageQ.data.totals.fee))}</td>
                </tr>
              </tbody>
            </table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
