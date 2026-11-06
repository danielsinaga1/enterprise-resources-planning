/**
 * Stok titipan pelanggan: saldo per pelanggan × item × gudang + umur lot tertua (FIFO), kartu stok titipan
 * (saldo berjalan), dan pindah gudang. Terpisah total dari stok/nilai persediaan perusahaan.
 */
import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useTranslation } from 'react-i18next';
import { ArrowLeftRight, Clock, History, Package, Users, XCircle } from 'lucide-react';
import { toast } from 'sonner';
import api from '@/lib/api';
import { formatDate } from '@/lib/format';
import { usePartyPicker } from '@/hooks/usePartyPicker';
import { Button } from '@/components/ui/button';
import { NumberInput } from '@/components/ui/number-input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import { ComboboxFilter, DataTable, DateInput, Field, RowActions, StatCard } from '@/components/shared';
import { FormError, OverlayBody, OverlayFooter, OverlayHeader } from '../sales/dialog-parts-b';
import { dialogShellClass } from '../sales/dialog-classes-b';
import { TOLL_BASE, errorMessage, num, qty, useWarehouseOptions } from './common';

interface BalanceRow {
  partyId: string;
  partyName: string;
  itemId: string;
  itemCode: string;
  itemName: string;
  unit: string;
  warehouseId: string;
  warehouseName: string;
  quantity: number;
  oldestLotDate: string | null;
  ageDays: number | null;
  ageBucket: string | null;
}

interface CardRow {
  id: string;
  date: string;
  direction: 'In' | 'Out';
  quantity: number;
  sourceType: string;
  sourceId: string;
  sourceNumber: string | null;
  tollOrderNumber: string | null;
  warehouseName: string;
  notes: string | null;
  balance: number;
}

interface CardData {
  partyName: string;
  itemName: string;
  unit: string;
  opening: number;
  rows: CardRow[];
  totalIn: number;
  totalOut: number;
  closing: number;
}

const FILTER_CLASS = 'w-[calc(50%-0.25rem)] sm:w-56';

export default function StockTab({ canEdit, canCancel }: { canEdit: boolean; canCancel: boolean }) {
  const { t } = useTranslation('toll');
  const [partyId, setPartyId] = useState('');
  const [warehouseId, setWarehouseId] = useState('');
  const [cardTarget, setCardTarget] = useState<BalanceRow | null>(null);
  const [transferTarget, setTransferTarget] = useState<BalanceRow | null>(null);

  const partyPicker = usePartyPicker({ type: 'Customer', selectedId: partyId || null });
  const { data: warehouses } = useWarehouseOptions();

  const query = useQuery({
    queryKey: ['toll-stock', 'balances', partyId, warehouseId],
    queryFn: async () =>
      (await api.get<{ data: BalanceRow[]; summary: { rows: number; parties: number; totalQty: number; over30Days: number } }>(
        `${TOLL_BASE}/stock/balances`, { params: { partyId: partyId || undefined, warehouseId: warehouseId || undefined } },
      )).data,
  });

  const columns = useMemo<ColumnDef<BalanceRow>[]>(
    () => [
      { id: 'party', header: t('stock.columns.party'), cell: ({ row }) => <span className="font-medium">{row.original.partyName}</span> },
      {
        id: 'item',
        header: t('stock.columns.item'),
        cell: ({ row }) => (
          <div className="min-w-0">
            <p className="truncate">{row.original.itemName}</p>
            <p className="text-xs text-muted-foreground">{row.original.itemCode}</p>
          </div>
        ),
      },
      { id: 'warehouse', header: t('stock.columns.warehouse'), meta: { hideBelow: 'sm' }, cell: ({ row }) => row.original.warehouseName },
      {
        id: 'quantity',
        header: t('stock.columns.quantity'),
        meta: { align: 'right' },
        cell: ({ row }) => <span className="num font-semibold">{qty(row.original.quantity)} {row.original.unit}</span>,
      },
      {
        id: 'age',
        header: t('stock.columns.age'),
        meta: { align: 'right', hideBelow: 'md' },
        cell: ({ row }) =>
          row.original.ageDays == null ? '—' : (
            <span className={row.original.ageDays > 30 ? 'text-warning-text font-medium' : 'text-muted-foreground'}
              title={row.original.oldestLotDate ? formatDate(row.original.oldestLotDate) : undefined}>
              {t('stock.ageDays', { count: row.original.ageDays })}
            </span>
          ),
      },
      {
        id: 'actions',
        header: () => <span className="sr-only">{t('stock.columns.actions')}</span>,
        meta: { align: 'right', headerClassName: 'w-12', cellClassName: 'w-12 py-0' },
        cell: ({ row }) => (
          <RowActions
            label={row.original.itemName}
            actions={[
              { label: t('stock.actions.card'), icon: History, onSelect: () => setCardTarget(row.original) },
              { label: t('stock.actions.transfer'), icon: ArrowLeftRight, hidden: !canEdit || num(row.original.quantity) <= 0,
                onSelect: () => setTransferTarget(row.original) },
            ]}
          />
        ),
      },
    ],
    [canEdit, t],
  );

  const s = query.data?.summary;
  return (
    <div className="space-y-4">
      <section className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label={t('stock.summary.rows')} icon={Package} loading={!s} value={s ? qty(s.rows) : '—'} />
        <StatCard label={t('stock.summary.parties')} icon={Users} loading={!s} value={s ? qty(s.parties) : '—'} />
        <StatCard label={t('stock.summary.total')} icon={Package} loading={!s} value={s ? qty(s.totalQty) : '—'} />
        <StatCard label={t('stock.summary.aging')} icon={Clock} loading={!s} value={s ? qty(s.over30Days) : '—'} />
      </section>
      <div className="flex flex-wrap gap-2">
        <ComboboxFilter
          label={t('stock.party')}
          allLabel={t('stock.allParties')}
          searchPlaceholder={t('stock.partySearch')}
          value={partyId}
          onChange={(v) => setPartyId(v ?? '')}
          {...partyPicker.pickerProps}
          className={FILTER_CLASS}
        />
        <ComboboxFilter
          label={t('stock.warehouse')}
          allLabel={t('stock.allWarehouses')}
          searchPlaceholder={t('stock.warehouse')}
          value={warehouseId}
          onChange={(v) => setWarehouseId(v ?? '')}
          options={(warehouses ?? []).map((w) => ({ value: w.value, label: w.label }))}
          className={FILTER_CLASS}
        />
      </div>
      <DataTable
        label={t('stock.tableLabel')}
        itemLabel={t('stock.columns.item')}
        columns={columns}
        data={query.data?.data}
        getRowId={(r) => `${r.partyId}|${r.itemId}|${r.warehouseId}`}
        loading={query.isLoading}
        error={query.error}
        onRetry={() => query.refetch()}
        onRowClick={(r) => setCardTarget(r)}
        empty={{ title: t('stock.empty.title'), description: t('stock.empty.description') }}
      />

      {cardTarget && <StockCardDialog target={cardTarget} canCancel={canCancel} onClose={() => setCardTarget(null)} />}
      {transferTarget && <TransferDialog target={transferTarget} onClose={() => setTransferTarget(null)} />}
    </div>
  );
}

function StockCardDialog({ target, canCancel, onClose }: { target: BalanceRow; canCancel: boolean; onClose: () => void }) {
  const { t } = useTranslation('toll');
  const queryClient = useQueryClient();
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const { data } = useQuery({
    queryKey: ['toll-stock', 'card', target.partyId, target.itemId, startDate, endDate],
    queryFn: async () =>
      (await api.get<CardData>(`${TOLL_BASE}/stock/card`, {
        params: { partyId: target.partyId, itemId: target.itemId, startDate: startDate || undefined, endDate: endDate || undefined },
      })).data,
  });
  const cancelTransfer = useMutation({
    mutationFn: (id: string) => api.post(`${TOLL_BASE}/stock/transfer/${id}/cancel`),
    onSuccess: () => {
      toast.success(t('stock.transferCancelled'));
      queryClient.invalidateQueries({ queryKey: ['toll-stock'] });
    },
    onError: (err) => toast.error(errorMessage(err, t('stock.transferFailed'))),
  });

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent size="lg" className={dialogShellClass}>
        <OverlayHeader>
          <DialogTitle>{t('stock.cardTitle')}</DialogTitle>
          <DialogDescription>{t('stock.cardSubtitle', { item: target.itemName, party: target.partyName })}</DialogDescription>
        </OverlayHeader>
        <OverlayBody className="gap-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label={t('reports.period')}><DateInput value={startDate} onChange={setStartDate} /></Field>
            <Field label="—" hideLabel><DateInput value={endDate} onChange={setEndDate} /></Field>
          </div>
          <div className="overflow-x-auto rounded-md border border-border">
            <table className="w-full text-sm">
              <thead className="bg-muted/50 text-xs text-muted-foreground">
                <tr>
                  <th className="px-2 py-1.5 text-left">{t('stock.cardCols.date')}</th>
                  <th className="px-2 py-1.5 text-left">{t('stock.cardCols.doc')}</th>
                  <th className="px-2 py-1.5 text-left">{t('stock.cardCols.warehouse')}</th>
                  <th className="px-2 py-1.5 text-right">{t('stock.cardCols.in')}</th>
                  <th className="px-2 py-1.5 text-right">{t('stock.cardCols.out')}</th>
                  <th className="px-2 py-1.5 text-right">{t('stock.cardCols.balance')}</th>
                </tr>
              </thead>
              <tbody>
                <tr className="border-t border-border bg-muted/20">
                  <td className="px-2 py-1.5" colSpan={5}>{t('stock.opening')}</td>
                  <td className="num px-2 py-1.5 text-right">{qty(data?.opening)}</td>
                </tr>
                {(data?.rows ?? []).map((r) => (
                  <tr key={r.id} className="border-t border-border">
                    <td className="num px-2 py-1.5 whitespace-nowrap">{formatDate(r.date)}</td>
                    <td className="px-2 py-1.5">
                      <span className="font-medium">{r.sourceNumber ?? '—'}</span>
                      <span className="block text-xs text-muted-foreground">
                        {t(`stock.source.${r.sourceType}`, { defaultValue: r.sourceType })}{r.tollOrderNumber ? ` · ${r.tollOrderNumber}` : ''}
                      </span>
                      {r.sourceType === 'Transfer' && canCancel && (
                        <Button variant="link" size="sm" className="text-xs text-danger-text" onClick={() => cancelTransfer.mutate(r.sourceId)}>
                          <XCircle aria-hidden />{t('stock.cancelTransfer')}
                        </Button>
                      )}
                    </td>
                    <td className="px-2 py-1.5">{r.warehouseName}</td>
                    <td className="num px-2 py-1.5 text-right">{r.direction === 'In' ? qty(r.quantity) : ''}</td>
                    <td className="num px-2 py-1.5 text-right">{r.direction === 'Out' ? qty(r.quantity) : ''}</td>
                    <td className="num px-2 py-1.5 text-right">{qty(r.balance)}</td>
                  </tr>
                ))}
                <tr className="border-t border-border bg-muted/40 font-semibold">
                  <td className="px-2 py-1.5" colSpan={3}>{t('stock.closing')}</td>
                  <td className="num px-2 py-1.5 text-right">{qty(data?.totalIn)}</td>
                  <td className="num px-2 py-1.5 text-right">{qty(data?.totalOut)}</td>
                  <td className="num px-2 py-1.5 text-right">{qty(data?.closing)} {data?.unit ?? ''}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </OverlayBody>
        <OverlayFooter>
          <Button onClick={onClose}>{t('detail.close')}</Button>
        </OverlayFooter>
      </DialogContent>
    </Dialog>
  );
}

function TransferDialog({ target, onClose }: { target: BalanceRow; onClose: () => void }) {
  const { t } = useTranslation('toll');
  const queryClient = useQueryClient();
  const { data: warehouses } = useWarehouseOptions();
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
  const [toWarehouseId, setToWarehouseId] = useState('');
  const [quantity, setQuantity] = useState(String(num(target.quantity)));
  const [error, setError] = useState('');
  const mutation = useMutation({
    mutationFn: () => api.post(`${TOLL_BASE}/stock/transfer`, {
      date, partyId: target.partyId, itemId: target.itemId, fromWarehouseId: target.warehouseId, toWarehouseId, quantity: Number(quantity),
    }),
    onSuccess: () => {
      toast.success(t('stock.transferDone'));
      queryClient.invalidateQueries({ queryKey: ['toll-stock'] });
      queryClient.invalidateQueries({ queryKey: ['toll-orders'] });
      onClose();
    },
    onError: (err) => setError(errorMessage(err, t('stock.transferFailed'))),
  });
  const targets = (warehouses ?? []).filter((w) => w.value !== target.warehouseId);

  return (
    <Dialog open onOpenChange={(o) => !o && !mutation.isPending && onClose()}>
      <DialogContent size="sm" className={dialogShellClass}>
        <OverlayHeader>
          <DialogTitle>{t('stock.transferTitle')}</DialogTitle>
          <DialogDescription>{t('stock.cardSubtitle', { item: target.itemName, party: target.partyName })}</DialogDescription>
        </OverlayHeader>
        <OverlayBody className="gap-4">
          <Field label={t('returnForm.date')}><DateInput value={date} onChange={setDate} /></Field>
          <p className="text-sm text-muted-foreground">{t('stock.transferFrom')}: <span className="text-foreground">{target.warehouseName}</span></p>
          <Field label={t('stock.transferTo')} required>
            {(control) => (
              <Select value={toWarehouseId} onValueChange={setToWarehouseId}>
                <SelectTrigger {...control}><SelectValue placeholder="…" /></SelectTrigger>
                <SelectContent>{targets.map((w) => <SelectItem key={w.value} value={w.value}>{w.label}</SelectItem>)}</SelectContent>
              </Select>
            )}
          </Field>
          <Field label={t('stock.transferQty')} hint={t('returnForm.available', { qty: qty(target.quantity) })} required>
            <NumberInput min={0} step="any" value={quantity} onChange={(e) => setQuantity(e.target.value)} />
          </Field>
          <FormError message={error} />
        </OverlayBody>
        <OverlayFooter>
          <Button variant="outline" onClick={onClose} disabled={mutation.isPending}>{t('form.cancel')}</Button>
          <Button onClick={() => mutation.mutate()} disabled={!toWarehouseId || Number(quantity) <= 0 || mutation.isPending}>
            {t('stock.transferSave')}
          </Button>
        </OverlayFooter>
      </DialogContent>
    </Dialog>
  );
}
