/**
 * Detail Order Jasa Maklon (GET /toll/orders/:id): stok titipan order, hasil proses & rendemen, tagihan +
 * usulan baris invoice, dokumen tertaut, cetak Berita Acara Hasil Proses. Aksi tulis dipegang halaman.
 */
import { useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { CheckCircle2, FilePlus2, Pencil, RotateCcw, Truck, XCircle } from 'lucide-react';
import api from '@/lib/api';
import { formatDate, formatRupiah } from '@/lib/format';
import { useCompanyPDF } from '@/lib/pdf/useCompanyPDF';
import { TollProcessReportPDF } from '@/lib/pdf/TollPDF';
import PDFDownloadButton from '@/components/PDFDownloadButton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import { Sheet, SheetBody, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import { DateInput, Field, StatusBadge } from '@/components/shared';
import { FormError, OverlayBody, OverlayFooter, OverlayHeader } from '../sales/dialog-parts-b';
import { dialogShellClass } from '../sales/dialog-classes-b';
import { TOLL_BASE, errorMessage, num, qty, type TollOrderDetail } from './common';

interface Props {
  orderId: string | null;
  onClose: () => void;
  canEdit: boolean;
  canCancel: boolean;
  canInvoice: boolean;
  onEdit: (order: TollOrderDetail) => void;
  onComplete: (order: TollOrderDetail) => void;
  onReopen: (order: TollOrderDetail) => void;
  onCancel: (order: TollOrderDetail) => void;
  onReturn: (order: TollOrderDetail) => void;
}

const money = (v: unknown) => formatRupiah(num(v as number), { symbol: true });
const pct = (v: unknown) => (v === null || v === undefined ? '—' : `${qty(v as number)}%`);

function Detail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 break-words text-sm text-foreground">{children}</dd>
    </div>
  );
}

function Section({ title, children, action }: { title: string; children: ReactNode; action?: ReactNode }) {
  return (
    <section className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-foreground">{title}</h3>
        {action}
      </div>
      {children}
    </section>
  );
}

export default function TollOrderDetailSheet({
  orderId, onClose, canEdit, canCancel, canInvoice, onEdit, onComplete, onReopen, onCancel, onReturn,
}: Props) {
  const { t } = useTranslation('toll');
  const company = useCompanyPDF();
  const [invoiceOpen, setInvoiceOpen] = useState(false);

  const { data: order, isLoading } = useQuery({
    queryKey: ['toll-orders', 'detail', orderId],
    queryFn: async () => (await api.get<TollOrderDetail>(`${TOLL_BASE}/orders/${orderId}`)).data,
    enabled: !!orderId,
  });

  const active = order && order.status !== 'Cancelled';
  const editable = order && (order.status === 'Open' || order.status === 'InProgress');
  const billing = order?.billing;
  const prod = order?.production;
  const balanceTotal = (order?.stock ?? []).reduce((s, r) => s + num(r.balanceQty), 0);

  const pdfDoc = order ? (
    <TollProcessReportPDF
      orderNumber={order.orderNumber}
      date={order.date}
      party={{ name: order.partyName }}
      serviceName={order.serviceItemName}
      status={t(`status.${order.status}`)}
      stock={order.stock}
      production={order.production}
      runs={order.runs}
      factoryByProducts={order.factoryByProducts}
      company={company}
    />
  ) : null;

  return (
    <>
      <Sheet open={!!orderId} onOpenChange={(o) => !o && onClose()}>
        <SheetContent size="lg">
          <SheetHeader>
            <SheetTitle className="flex flex-wrap items-center gap-2">
              {order ? t('detail.title', { number: order.orderNumber }) : '…'}
              {order && <StatusBadge status={order.status} />}
            </SheetTitle>
            <SheetDescription>{order ? `${order.partyName} · ${formatDate(order.date)}` : ''}</SheetDescription>
          </SheetHeader>

          <SheetBody className="space-y-6">
            {isLoading || !order ? (
              <p className="text-sm text-muted-foreground">…</p>
            ) : (
              <>
                <dl className="grid grid-cols-2 gap-x-4 gap-y-3 sm:grid-cols-3">
                  <Detail label={t('detail.service')}>{order.serviceItemName ?? '—'}</Detail>
                  <Detail label={t('detail.tariff')}>
                    {order.pricingBasis === 'Flat' ? money(order.rate) : `${money(order.rate)} ${t(`basisShort.${order.pricingBasis}`)}`}
                  </Detail>
                  <Detail label={t('detail.minimum')}>{num(order.minimumCharge) > 0 ? money(order.minimumCharge) : '—'}</Detail>
                  <Detail label={t('detail.owner')}>{t(`owner.${order.byProductOwner}`)}</Detail>
                  <Detail label={t('detail.storage')}>
                    {num(order.storageRate) > 0
                      ? t('detail.storageValue', { rate: qty(order.storageRate), days: order.storageFreeDays })
                      : '—'}
                  </Detail>
                  {order.notes && <Detail label={t('form.notes')}>{order.notes}</Detail>}
                </dl>

                <Section title={t('detail.stockTitle')}>
                  {order.stock.length === 0 ? (
                    <p className="text-sm text-muted-foreground">{t('detail.noStock')}</p>
                  ) : (
                    <div className="overflow-x-auto rounded-md border border-border">
                      <table className="w-full text-sm">
                        <thead className="bg-muted/50 text-xs text-muted-foreground">
                          <tr>
                            <th className="px-2 py-1.5 text-left">{t('detail.stockCols.item')}</th>
                            {(['received', 'processed', 'produced', 'returned', 'balance'] as const).map((k) => (
                              <th key={k} className="px-2 py-1.5 text-right">{t(`detail.stockCols.${k}`)}</th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {order.stock.map((s) => (
                            <tr key={s.itemId} className="border-t border-border">
                              <td className="px-2 py-1.5">{s.itemName} <span className="text-xs text-muted-foreground">({s.unit})</span></td>
                              <td className="num px-2 py-1.5 text-right">{qty(s.receivedQty)}</td>
                              <td className="num px-2 py-1.5 text-right">{qty(s.processedQty)}</td>
                              <td className="num px-2 py-1.5 text-right">{qty(s.producedQty)}</td>
                              <td className="num px-2 py-1.5 text-right">{qty(s.returnedQty)}</td>
                              <td className="num px-2 py-1.5 text-right font-semibold">{qty(s.balanceQty)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                  {order.stockByWarehouse.length > 0 && (
                    <p className="text-xs text-muted-foreground">
                      {t('detail.byWarehouse')}: {order.stockByWarehouse.map((w) => `${w.itemName} ${qty(w.balanceQty)} ${w.unit} @ ${w.warehouseName}`).join(' · ')}
                    </p>
                  )}
                </Section>

                {prod && (
                  <Section title={t('detail.productionTitle')} action={<span className="text-xs text-muted-foreground">{t('detail.runs', { count: prod.runs })}</span>}>
                    <dl className="grid grid-cols-3 gap-3 sm:grid-cols-5">
                      <Detail label={t('detail.input')}>{qty(prod.inputQty)} {prod.inputUnit ?? ''}</Detail>
                      <Detail label={t('detail.mainOutput')}>{qty(prod.mainOutputQty)} {prod.outputUnit ?? ''}</Detail>
                      <Detail label={t('detail.byProduct')}>{qty(prod.byProductQty)}</Detail>
                      <Detail label={t('detail.shrink')}>{qty(prod.shrinkQty)} ({pct(prod.shrinkPct)})</Detail>
                      <Detail label={t('detail.yield')}>
                        <span className={num(prod.yieldVariancePct) < 0 ? 'text-danger-text' : undefined}>{pct(prod.yieldPct)}</span>
                        {prod.expectedYieldPct != null && (
                          <span className="block text-xs text-muted-foreground">{t('detail.target', { pct: pct(prod.expectedYieldPct) })}</span>
                        )}
                      </Detail>
                    </dl>
                    {order.factoryByProducts.length > 0 && (
                      <p className="text-xs text-muted-foreground">
                        {t('detail.factoryByProducts')}: {order.factoryByProducts.map((b) => `${b.itemName} ${qty(b.quantity)} ${b.unit} (${money(b.value)})`).join(', ')}
                      </p>
                    )}
                  </Section>
                )}

                {billing && (
                  <Section
                    title={t('detail.billingTitle')}
                    action={canInvoice && active && billing.lines.length > 0 ? (
                      <Button size="sm" onClick={() => setInvoiceOpen(true)}>
                        <FilePlus2 aria-hidden />
                        {t('orders.actions.invoice')}
                      </Button>
                    ) : undefined}
                  >
                    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                      <Detail label={t('detail.basisQty')}>{order.pricingBasis === 'Flat' ? t('basis.Flat') : qty(billing.basisQty)}</Detail>
                      <Detail label={t('detail.serviceAmount')}>{money(billing.serviceAmount)}</Detail>
                      <Detail label={t('detail.billed')}>{money(billing.billedTotal)}</Detail>
                      <Detail label={t('detail.unbilled')}><span className="font-semibold">{money(billing.unbilledTotal)}</span></Detail>
                    </dl>
                    {num(billing.minimumCharge) > 0 && !billing.minimumApplies && (
                      <p className="text-xs text-muted-foreground">{t('detail.minimumPending')}</p>
                    )}
                    {num(billing.storage.rate) > 0 && (
                      <p className="text-xs text-muted-foreground">
                        {t('detail.storage')}: {t('detail.storageLine', { qtyDays: qty(billing.storage.qtyDays), until: formatDate(billing.storage.until), fee: money(billing.storage.fee) })}
                      </p>
                    )}
                    {billing.lines.length === 0 ? (
                      <p className="text-sm text-muted-foreground">{t('detail.nothingToBill')}</p>
                    ) : (
                      <ul className="divide-y divide-border rounded-md border border-border text-sm">
                        {billing.lines.map((l) => (
                          <li key={l.chargeType} className="flex items-start justify-between gap-3 px-3 py-2">
                            <div className="min-w-0">
                              <p className="font-medium">{t(`chargeType.${l.chargeType}`)}</p>
                              <p className="truncate text-xs text-muted-foreground">{l.description}</p>
                            </div>
                            <span className="num shrink-0">{money(l.amount)}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </Section>
                )}

                <Section title={t('detail.documents')}>
                  <dl className="grid gap-3 sm:grid-cols-2">
                    <Detail label={t('detail.receipts')}>
                      {order.receipts.length ? order.receipts.map((r) => `${r.sttNumber}${r.status === 'Cancelled' ? ' ✕' : ''} (${qty(r.quantity)})`).join(', ') : t('detail.none')}
                    </Detail>
                    <Detail label={t('detail.runsDocs')}>
                      {order.runs.length ? order.runs.map((r) => `${r.runNumber}${r.isCancelled ? ' ✕' : ''}`).join(', ') : t('detail.none')}
                    </Detail>
                    <Detail label={t('detail.returnsDocs')}>
                      {order.returns.length ? order.returns.map((r) => `${r.returnNumber}${r.status === 'Cancelled' ? ' ✕' : ''} (${qty(r.quantity)})`).join(', ') : t('detail.none')}
                    </Detail>
                    <Detail label={t('detail.invoices')}>
                      {order.invoices.length ? order.invoices.map((v) => `${v.invoiceNumber}${v.status === 'Cancelled' ? ' ✕' : ''} (${money(v.tollAmount)})`).join(', ') : t('detail.none')}
                    </Detail>
                  </dl>
                </Section>
              </>
            )}
          </SheetBody>

          {order && (
            <SheetFooter className="flex-wrap gap-2">
              {pdfDoc && <PDFDownloadButton document={pdfDoc} fileName={`BA-${order.orderNumber}.pdf`} label={t('detail.printReport')} />}
              {canEdit && active && balanceTotal > 0 && (
                <Button variant="outline" onClick={() => onReturn(order)}><Truck aria-hidden />{t('detail.newReturn')}</Button>
              )}
              {canEdit && editable && (
                <Button variant="outline" onClick={() => onEdit(order)}><Pencil aria-hidden />{t('orders.actions.edit')}</Button>
              )}
              {canEdit && editable && (
                <Button variant="outline" onClick={() => onComplete(order)}><CheckCircle2 aria-hidden />{t('orders.actions.complete')}</Button>
              )}
              {canEdit && order.status === 'Completed' && (
                <Button variant="outline" onClick={() => onReopen(order)}><RotateCcw aria-hidden />{t('orders.actions.reopen')}</Button>
              )}
              {canCancel && order.status !== 'Cancelled' && (
                <Button variant="outline" className="text-danger-text" onClick={() => onCancel(order)}><XCircle aria-hidden />{t('orders.actions.cancel')}</Button>
              )}
            </SheetFooter>
          )}
        </SheetContent>
      </Sheet>

      {order && invoiceOpen && (
        <TollInvoiceDialog order={order} onClose={() => setInvoiceOpen(false)} />
      )}
    </>
  );
}

function TollInvoiceDialog({ order, onClose }: { order: TollOrderDetail; onClose: () => void }) {
  const { t } = useTranslation('toll');
  const queryClient = useQueryClient();
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
  const [dueDate, setDueDate] = useState('');
  const [notes, setNotes] = useState('');
  const [includeStorage, setIncludeStorage] = useState(true);
  const [includeMinimum, setIncludeMinimum] = useState(true);
  const [error, setError] = useState('');

  const lines = order.billing.lines.filter(
    (l) => (l.chargeType !== 'Storage' || includeStorage) && (l.chargeType !== 'Minimum' || includeMinimum),
  );
  const total = lines.reduce((s, l) => s + num(l.amount), 0);

  const mutation = useMutation({
    mutationFn: async () =>
      (await api.post(`${TOLL_BASE}/orders/${order.id}/invoice`, { date, dueDate: dueDate || null, notes: notes || null, includeStorage, includeMinimum })).data,
    onSuccess: (inv: { invoiceNumber: string }) => {
      toast.success(t('orders.toast.invoiced', { number: inv.invoiceNumber }));
      queryClient.invalidateQueries({ queryKey: ['toll-orders'] });
      queryClient.invalidateQueries({ queryKey: ['sales-invoices'] });
      onClose();
    },
    onError: (err) => setError(errorMessage(err, t('orders.toast.failed'))),
  });

  const hasStorage = order.billing.lines.some((l) => l.chargeType === 'Storage');
  const hasMinimum = order.billing.lines.some((l) => l.chargeType === 'Minimum');

  return (
    <Dialog open onOpenChange={(o) => !o && !mutation.isPending && onClose()}>
      <DialogContent size="md" className={dialogShellClass}>
        <OverlayHeader>
          <DialogTitle>{t('invoice.title', { number: order.orderNumber })}</DialogTitle>
          <DialogDescription>{t('invoice.description')}</DialogDescription>
        </OverlayHeader>
        <OverlayBody className="gap-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t('invoice.date')} required><DateInput value={date} onChange={setDate} /></Field>
            <Field label={t('invoice.dueDate')}><DateInput value={dueDate} onChange={setDueDate} /></Field>
          </div>
          <Field label={t('invoice.notes')}><Input value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
          {hasStorage && (
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={includeStorage} onCheckedChange={(v) => setIncludeStorage(v === true)} />
              {t('invoice.includeStorage')}
            </label>
          )}
          {hasMinimum && (
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={includeMinimum} onCheckedChange={(v) => setIncludeMinimum(v === true)} />
              {t('invoice.includeMinimum')}
            </label>
          )}
          <ul className="divide-y divide-border rounded-md border border-border text-sm">
            {lines.map((l) => (
              <li key={l.chargeType} className="flex justify-between gap-3 px-3 py-2">
                <span>{t(`chargeType.${l.chargeType}`)} <span className="text-xs text-muted-foreground">· {qty(l.quantity)} {l.unit}</span></span>
                <span className="num">{money(l.amount)}</span>
              </li>
            ))}
            <li className="flex justify-between gap-3 bg-muted/40 px-3 py-2 font-semibold">
              <span>{t('invoice.total')}</span>
              <span className="num">{money(total)}</span>
            </li>
          </ul>
          <FormError message={error} />
        </OverlayBody>
        <OverlayFooter>
          <Button variant="outline" onClick={onClose} disabled={mutation.isPending}>{t('form.cancel')}</Button>
          <Button onClick={() => mutation.mutate()} disabled={mutation.isPending || lines.length === 0 || !date}>
            {mutation.isPending ? t('invoice.submitting') : t('invoice.submit')}
          </Button>
        </OverlayFooter>
      </DialogContent>
    </Dialog>
  );
}
