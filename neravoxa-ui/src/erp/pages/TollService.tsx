/**
 * Jasa Maklon & Stok Titipan (/toll-service); tab di ?tab= (orders | stock | returns | reports).
 * Order Jasa Maklon mengikat STT titipan → produksi maklon → surat jalan pengembalian → invoice.
 */
import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useTranslation } from 'react-i18next';
import { CheckCircle2, Eye, Factory, Pencil, RotateCcw, Truck, XCircle } from 'lucide-react';
import { toast } from 'sonner';
import api from '@/lib/api';
import { can, cap } from '@/lib/permissions';
import { formatDate, formatRupiah } from '@/lib/format';
import { useListState } from '@/hooks/useListState';
import { usePartyPicker } from '@/hooks/usePartyPicker';
import { useTableDensity } from '@/hooks/useTableDensity';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  ComboboxFilter,
  ConfirmDialog,
  DataTable,
  DocumentLink,
  FilterBar,
  PageHeader,
  RowActions,
  StatusBadge,
  listTableProps,
  type Paginated,
} from '@/components/shared';
import TollOrderModal from '@/components/toll/TollOrderModal';
import TollOrderDetailSheet from '@/components/toll/TollOrderDetailSheet';
import CustomerReturnModal from '@/components/toll/CustomerReturnModal';
import StockTab from '@/components/toll/StockTab';
import ReturnsTab from '@/components/toll/ReturnsTab';
import ReportsTab from '@/components/toll/ReportsTab';
import { ORDER_STATUSES, TOLL_BASE, errorMessage, num, qty, type TollOrderDetail, type TollOrderRow } from '@/components/toll/common';

const TABS = ['orders', 'stock', 'returns', 'reports'] as const;
type Tab = (typeof TABS)[number];
const FILTER_KEYS = ['status', 'partyId', 'startDate', 'endDate'] as const;
const FILTER_CLASS = 'w-[calc(50%-0.25rem)] sm:w-56';

export default function TollService() {
  const { t } = useTranslation('toll');
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const rawTab = searchParams.get('tab') as Tab | null;
  const tab: Tab = rawTab && TABS.includes(rawTab) ? rawTab : 'orders';
  const changeTab = (value: string) => setSearchParams(value === 'orders' ? {} : { tab: value });

  const canEdit = can('toll_service', 'edit');
  const canCancel = canEdit && cap('doc.cancel');
  const canInvoice = canEdit && can('sales_invoice', 'edit');

  const list = useListState({ filters: FILTER_KEYS, defaultSort: { id: 'date', desc: true } });
  const [density, setDensity] = useTableDensity();
  const [modal, setModal] = useState<{ open: boolean; order: TollOrderRow | null }>({ open: false, order: null });
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [returnFrom, setReturnFrom] = useState<{ open: boolean; order: TollOrderDetail | null }>({ open: false, order: null });
  const [confirm, setConfirm] = useState<{ kind: 'complete' | 'cancel'; order: TollOrderRow; balance?: number } | null>(null);

  const ordersQuery = useQuery({
    queryKey: ['toll-orders', 'list', list.params],
    queryFn: async () => (await api.get<Paginated<TollOrderRow>>(`${TOLL_BASE}/orders`, { params: list.params })).data,
    placeholderData: keepPreviousData,
    enabled: tab === 'orders',
  });
  const partyPicker = usePartyPicker({ type: 'Customer', selectedId: list.filters.partyId || null });

  const action = useMutation({
    mutationFn: ({ id, op }: { id: string; op: 'complete' | 'reopen' | 'cancel' }) => api.post(`${TOLL_BASE}/orders/${id}/${op}`),
    onSuccess: (_res, { op }) => {
      toast.success(t(`orders.toast.${op === 'complete' ? 'completed' : op === 'reopen' ? 'reopened' : 'cancelled'}`));
      queryClient.invalidateQueries({ queryKey: ['toll-orders'] });
    },
    onError: (err) => toast.error(errorMessage(err, t('orders.toast.failed'))),
  });

  const columns = useMemo<ColumnDef<TollOrderRow>[]>(
    () => [
      {
        id: 'number',
        header: t('orders.columns.number'),
        enableSorting: true,
        sortDescFirst: true,
        cell: ({ row }) => (
          <div className="flex flex-col items-start gap-0.5">
            <DocumentLink onClick={() => setSelectedId(row.original.id)}>{row.original.orderNumber}</DocumentLink>
            <span className="max-w-40 truncate text-xs text-muted-foreground sm:hidden">{row.original.partyName}</span>
          </div>
        ),
      },
      { id: 'date', header: t('orders.columns.date'), enableSorting: true, sortDescFirst: true,
        meta: { hideBelow: 'md', cellClassName: 'num whitespace-nowrap text-muted-foreground' }, cell: ({ row }) => formatDate(row.original.date) },
      {
        id: 'customer',
        header: t('orders.columns.party'),
        enableSorting: true,
        meta: { hideBelow: 'sm', cellClassName: 'min-w-40 max-w-72' },
        cell: ({ row }) => (
          <div className="min-w-0">
            <p className="truncate font-medium">{row.original.partyName}</p>
            <p className="truncate text-xs text-muted-foreground">{row.original.serviceItemName ?? ''}</p>
          </div>
        ),
      },
      {
        id: 'tariff',
        header: t('orders.columns.tariff'),
        meta: { hideBelow: 'lg' },
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-sm">
            {formatRupiah(num(row.original.rate))} <span className="text-xs text-muted-foreground">{t(`basisShort.${row.original.pricingBasis}`)}</span>
          </span>
        ),
      },
      { id: 'received', header: t('orders.columns.received'), meta: { align: 'right', hideBelow: 'md' }, cell: ({ row }) => <span className="num">{qty(row.original.receivedQty)}</span> },
      { id: 'processed', header: t('orders.columns.processed'), meta: { align: 'right', hideBelow: 'md' }, cell: ({ row }) => <span className="num">{qty(row.original.processedQty)}</span> },
      { id: 'balance', header: t('orders.columns.balance'), meta: { align: 'right' }, cell: ({ row }) => <span className="num font-semibold">{qty(row.original.balanceQty)}</span> },
      { id: 'billed', header: t('orders.columns.billed'), meta: { align: 'right', hideBelow: 'lg' }, cell: ({ row }) => <span className="num">{formatRupiah(num(row.original.billedAmount), { compact: true })}</span> },
      { id: 'status', header: t('orders.columns.status'), meta: { hideBelow: 'sm' }, cell: ({ row }) => <StatusBadge status={row.original.status} /> },
      {
        id: 'actions',
        header: () => <span className="sr-only">{t('orders.columns.actions')}</span>,
        meta: { align: 'right', headerClassName: 'w-12', cellClassName: 'w-12 py-0' },
        cell: ({ row }) => {
          const o = row.original;
          const editable = o.status === 'Open' || o.status === 'InProgress';
          return (
            <RowActions
              label={t('orders.rowActions', { number: o.orderNumber })}
              actions={[
                { label: t('orders.actions.view'), icon: Eye, onSelect: () => setSelectedId(o.id) },
                { label: t('orders.actions.edit'), icon: Pencil, hidden: !canEdit || !editable, onSelect: () => setModal({ open: true, order: o }) },
                { label: t('orders.actions.complete'), icon: CheckCircle2, hidden: !canEdit || !editable,
                  onSelect: () => setConfirm({ kind: 'complete', order: o, balance: num(o.balanceQty) }) },
                { label: t('orders.actions.reopen'), icon: RotateCcw, hidden: !canEdit || o.status !== 'Completed',
                  onSelect: () => action.mutate({ id: o.id, op: 'reopen' }) },
                { label: t('orders.actions.cancel'), icon: XCircle, tone: 'danger', hidden: !canCancel || o.status === 'Cancelled',
                  onSelect: () => setConfirm({ kind: 'cancel', order: o }) },
              ]}
            />
          );
        },
      },
    ],
    [canEdit, canCancel, t, action],
  );

  const headerAction =
    tab === 'orders' && canEdit ? (
      <Button onClick={() => setModal({ open: true, order: null })}><Factory aria-hidden />{t('orders.create')}</Button>
    ) : tab === 'returns' && canEdit ? (
      <Button onClick={() => setReturnFrom({ open: true, order: null })}><Truck aria-hidden />{t('returns.create')}</Button>
    ) : undefined;

  return (
    <Tabs value={tab} onValueChange={changeTab} activationMode="manual" className="pb-8">
      <PageHeader title={t('page.title')} description={t(`page.description.${tab}`)} actions={headerAction}>
        <TabsList variant="underline" aria-label={t('page.tabsLabel')}>
          {TABS.map((k) => <TabsTrigger key={k} value={k}>{t(`page.tabs.${k}`)}</TabsTrigger>)}
        </TabsList>
      </PageHeader>

      <TabsContent value="orders" className="mt-0 space-y-3">
        <FilterBar
          list={list}
          searchLabel={t('orders.searchLabel')}
          searchPlaceholder={t('orders.searchPlaceholder')}
          status={{ options: ORDER_STATUSES.map((s) => ({ value: s, label: t(`status.${s}`) })) }}
          dateRange={{ label: t('orders.dateRange') }}
        >
          <ComboboxFilter
            label={t('orders.party')}
            allLabel={t('orders.allParties')}
            searchPlaceholder={t('orders.partySearch')}
            value={list.filters.partyId}
            onChange={(v) => list.setFilter('partyId', v)}
            {...partyPicker.pickerProps}
            className={FILTER_CLASS}
          />
        </FilterBar>
        <DataTable
          {...listTableProps(list, ordersQuery.data?.total)}
          label={t('orders.tableLabel')}
          itemLabel={t('orders.itemLabel')}
          columns={columns}
          data={ordersQuery.data?.data}
          getRowId={(r) => r.id}
          loading={ordersQuery.isLoading}
          fetching={ordersQuery.isFetching && ordersQuery.isPlaceholderData}
          error={ordersQuery.error}
          onRetry={() => ordersQuery.refetch()}
          onRowClick={(r) => setSelectedId(r.id)}
          rowClassName={(r) => (r.status === 'Cancelled' ? 'text-muted-foreground' : undefined)}
          density={density}
          onDensityChange={setDensity}
          empty={{
            title: t('orders.empty.title'),
            description: t('orders.empty.description'),
            action: canEdit ? (
              <Button size="sm" onClick={() => setModal({ open: true, order: null })}><Factory aria-hidden />{t('orders.empty.action')}</Button>
            ) : undefined,
          }}
        />
      </TabsContent>

      <TabsContent value="stock" className="mt-0">
        {tab === 'stock' && <StockTab canEdit={canEdit} canCancel={canCancel} />}
      </TabsContent>
      <TabsContent value="returns" className="mt-0">
        {tab === 'returns' && <ReturnsTab canCancel={canCancel} />}
      </TabsContent>
      <TabsContent value="reports" className="mt-0">
        {tab === 'reports' && <ReportsTab />}
      </TabsContent>

      <TollOrderModal
        open={modal.open}
        order={modal.order}
        onClose={() => setModal({ open: false, order: null })}
        onSaved={(o) => !modal.order && setSelectedId(o.id)}
      />
      <TollOrderDetailSheet
        orderId={selectedId}
        onClose={() => setSelectedId(null)}
        canEdit={canEdit}
        canCancel={canCancel}
        canInvoice={canInvoice}
        onEdit={(o) => setModal({ open: true, order: o })}
        onComplete={(o) => setConfirm({ kind: 'complete', order: o, balance: o.stock.reduce((s, r) => s + num(r.balanceQty), 0) })}
        onReopen={(o) => action.mutate({ id: o.id, op: 'reopen' })}
        onCancel={(o) => setConfirm({ kind: 'cancel', order: o })}
        onReturn={(o) => setReturnFrom({ open: true, order: o })}
      />
      <CustomerReturnModal open={returnFrom.open} fromOrder={returnFrom.order} onClose={() => setReturnFrom({ open: false, order: null })} />

      <ConfirmDialog
        open={confirm !== null}
        onOpenChange={(o) => !o && setConfirm(null)}
        title={confirm?.kind === 'complete' ? t('orders.completeDialog.title') : t('orders.cancelDialog.title')}
        description={
          confirm?.kind === 'complete'
            ? t('orders.completeDialog.description', { number: confirm.order.orderNumber, balance: qty(confirm.balance ?? 0) })
            : t('orders.cancelDialog.description', { number: confirm?.order.orderNumber ?? '' })
        }
        confirmLabel={confirm?.kind === 'complete' ? t('orders.completeDialog.confirm') : t('orders.cancelDialog.confirm')}
        tone={confirm?.kind === 'cancel' ? 'danger' : undefined}
        loading={action.isPending}
        onConfirm={async () => {
          if (!confirm) return;
          await action.mutateAsync({ id: confirm.order.id, op: confirm.kind });
          setConfirm(null);
        }}
      />
    </Tabs>
  );
}
