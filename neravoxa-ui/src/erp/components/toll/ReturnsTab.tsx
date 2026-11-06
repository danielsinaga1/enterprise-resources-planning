/** Daftar Surat Jalan Pengembalian Titipan: cetak ulang & batal (hak doc.cancel). */
import { useMemo, useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useTranslation } from 'react-i18next';
import { FileDown, XCircle } from 'lucide-react';
import { toast } from 'sonner';
import api from '@/lib/api';
import { formatDate } from '@/lib/format';
import { statusOptions } from '@/lib/status';
import { useListState } from '@/hooks/useListState';
import { useCompanyPDF } from '@/lib/pdf/useCompanyPDF';
import { CustomerReturnPDF } from '@/lib/pdf/TollPDF';
import PDFDownloadButton from '@/components/PDFDownloadButton';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { ConfirmDialog, DataTable, FilterBar, RowActions, StatusBadge, listTableProps, type Paginated } from '@/components/shared';
import { OverlayBody, OverlayFooter, OverlayHeader } from '../sales/dialog-parts-b';
import { dialogShellClass } from '../sales/dialog-classes-b';
import { TOLL_BASE, errorMessage, num, type CustomerReturnRow } from './common';

const FILTER_KEYS = ['status', 'startDate', 'endDate'] as const;
const STATUS_FILTER = statusOptions(['Submitted', 'Cancelled']);

export default function ReturnsTab({ canCancel }: { canCancel: boolean }) {
  const { t } = useTranslation('toll');
  const queryClient = useQueryClient();
  const list = useListState({ filters: FILTER_KEYS, defaultSort: { id: 'date', desc: true } });
  const [printId, setPrintId] = useState<string | null>(null);
  const [cancelTarget, setCancelTarget] = useState<CustomerReturnRow | null>(null);

  const query = useQuery({
    queryKey: ['toll-returns', 'list', list.params],
    queryFn: async () => (await api.get<Paginated<CustomerReturnRow>>(`${TOLL_BASE}/returns`, { params: list.params })).data,
    placeholderData: keepPreviousData,
  });

  const cancelMutation = useMutation({
    mutationFn: (id: string) => api.post(`${TOLL_BASE}/returns/${id}/cancel`),
    onSuccess: () => {
      toast.success(t('returns.toast.cancelled'));
      queryClient.invalidateQueries({ queryKey: ['toll-returns'] });
      queryClient.invalidateQueries({ queryKey: ['toll-orders'] });
      queryClient.invalidateQueries({ queryKey: ['toll-stock'] });
    },
    onError: (err) => toast.error(errorMessage(err, t('returns.toast.failed'))),
  });

  const columns = useMemo<ColumnDef<CustomerReturnRow>[]>(
    () => [
      { id: 'number', header: t('returns.columns.number'), cell: ({ row }) => <span className="font-medium">{row.original.returnNumber}</span> },
      { id: 'date', header: t('returns.columns.date'), meta: { hideBelow: 'md', cellClassName: 'num whitespace-nowrap text-muted-foreground' },
        cell: ({ row }) => formatDate(row.original.date) },
      { id: 'party', header: t('returns.columns.party'), cell: ({ row }) => row.original.partyName },
      { id: 'order', header: t('returns.columns.order'), meta: { hideBelow: 'md' }, cell: ({ row }) => row.original.tollOrderNumber ?? '—' },
      { id: 'items', header: t('returns.columns.items'), meta: { hideBelow: 'sm', cellClassName: 'max-w-72 truncate' },
        cell: ({ row }) => <span title={row.original.summary ?? ''}>{row.original.summary ?? '—'}</span> },
      { id: 'vehicle', header: t('returns.columns.vehicle'), meta: { hideBelow: 'lg' }, cell: ({ row }) => row.original.nomorMobil ?? '—' },
      { id: 'status', header: t('returns.columns.status'), cell: ({ row }) => <StatusBadge status={row.original.status} /> },
      {
        id: 'actions',
        header: () => <span className="sr-only">{t('returns.columns.actions')}</span>,
        meta: { align: 'right', headerClassName: 'w-12', cellClassName: 'w-12 py-0' },
        cell: ({ row }) => (
          <RowActions
            label={row.original.returnNumber}
            actions={[
              { label: t('returns.actions.print'), icon: FileDown, onSelect: () => setPrintId(row.original.id) },
              { label: t('returns.actions.cancel'), icon: XCircle, tone: 'danger',
                hidden: !canCancel || row.original.status === 'Cancelled', onSelect: () => setCancelTarget(row.original) },
            ]}
          />
        ),
      },
    ],
    [canCancel, t],
  );

  return (
    <div className="space-y-3">
      <FilterBar
        list={list}
        searchLabel={t('returns.searchLabel')}
        searchPlaceholder={t('returns.searchPlaceholder')}
        status={{ options: STATUS_FILTER }}
        dateRange={{ label: t('returns.columns.date') }}
      />
      <DataTable
        {...listTableProps(list, query.data?.total)}
        label={t('returns.tableLabel')}
        itemLabel={t('returns.itemLabel')}
        columns={columns}
        data={query.data?.data}
        getRowId={(r) => r.id}
        loading={query.isLoading}
        fetching={query.isFetching && query.isPlaceholderData}
        error={query.error}
        onRetry={() => query.refetch()}
        onRowClick={(r) => setPrintId(r.id)}
        rowClassName={(r) => (r.status === 'Cancelled' ? 'text-muted-foreground' : undefined)}
        empty={{ title: t('returns.empty.title'), description: t('returns.empty.description') }}
      />
      {printId && <ReturnPrintDialog id={printId} onClose={() => setPrintId(null)} />}
      <ConfirmDialog
        open={cancelTarget !== null}
        onOpenChange={(o) => !o && setCancelTarget(null)}
        title={t('returns.cancelDialog.title')}
        description={t('returns.cancelDialog.description', { number: cancelTarget?.returnNumber ?? '' })}
        confirmLabel={t('returns.cancelDialog.confirm')}
        tone="danger"
        loading={cancelMutation.isPending}
        onConfirm={async () => {
          if (!cancelTarget) return;
          await cancelMutation.mutateAsync(cancelTarget.id);
          setCancelTarget(null);
        }}
      />
    </div>
  );
}

function ReturnPrintDialog({ id, onClose }: { id: string; onClose: () => void }) {
  const { t } = useTranslation('toll');
  const company = useCompanyPDF();
  const { data } = useQuery({
    queryKey: ['toll-returns', 'detail', id],
    queryFn: async () => (await api.get<CustomerReturnRow>(`${TOLL_BASE}/returns/${id}`)).data,
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent size="md" className={dialogShellClass}>
        <OverlayHeader>
          <DialogTitle className="flex items-center gap-2">{data?.returnNumber ?? '…'} {data && <StatusBadge status={data.status} />}</DialogTitle>
          <DialogDescription>{data ? `${data.partyName} · ${formatDate(data.date)}${data.tollOrderNumber ? ` · ${data.tollOrderNumber}` : ''}` : ''}</DialogDescription>
        </OverlayHeader>
        <OverlayBody className="gap-2">
          <ul className="divide-y divide-border rounded-md border border-border text-sm">
            {(data?.items ?? []).map((i) => (
              <li key={i.id} className="flex justify-between gap-3 px-3 py-2">
                <span>{i.itemName} <span className="text-xs text-muted-foreground">· {i.warehouseName}</span></span>
                <span className="num">{num(i.quantity).toLocaleString('id-ID')} {i.unit}</span>
              </li>
            ))}
          </ul>
        </OverlayBody>
        <OverlayFooter>
          {data && (
            <PDFDownloadButton
              fileName={`${data.returnNumber}.pdf`}
              label={t('returns.actions.print')}
              document={
                <CustomerReturnPDF
                  returnNumber={data.returnNumber}
                  date={data.date}
                  party={{ name: data.partyName, address: data.partyAddress }}
                  tollOrderNumber={data.tollOrderNumber}
                  nomorMobil={data.nomorMobil}
                  driverName={data.driverName}
                  recipientName={data.recipientName}
                  notes={data.notes}
                  items={(data.items ?? []).map((i) => ({ code: i.itemCode, name: i.itemName, qty: num(i.quantity), unit: i.unit, description: i.warehouseName }))}
                  company={company}
                />
              }
            />
          )}
          <Button onClick={onClose}>{t('detail.close')}</Button>
        </OverlayFooter>
      </DialogContent>
    </Dialog>
  );
}
