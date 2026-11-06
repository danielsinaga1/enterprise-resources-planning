/**
 * Surat Jalan Pengembalian Titipan (POST /toll/returns). Saldo titipan pelanggan per item × gudang dipakai
 * sebagai panduan (dan bisa mengisi baris otomatis dari sisa order). Setelah tersimpan, PDF siap diunduh.
 */
import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Plus, Trash2, Truck } from 'lucide-react';
import { toast } from 'sonner';
import api from '@/lib/api';
import { usePartyPicker } from '@/hooks/usePartyPicker';
import { useCompanyPDF } from '@/lib/pdf/useCompanyPDF';
import { CustomerReturnPDF } from '@/lib/pdf/TollPDF';
import PDFDownloadButton from '@/components/PDFDownloadButton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { NumberInput } from '@/components/ui/number-input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import { DateInput, Field } from '@/components/shared';
import { ComboboxField } from '../overlays/ComboboxField';
import { FormError, OverlayBody, OverlayFooter, OverlayHeader } from '../sales/dialog-parts-b';
import { dialogShellClass, NONE_VALUE } from '../sales/dialog-classes-b';
import {
  TOLL_BASE,
  errorMessage,
  num,
  qty,
  useInventoryItemOptions,
  useTollOrderOptions,
  useWarehouseOptions,
  type CustomerReturnRow,
  type TollOrderDetail,
} from './common';

interface Line {
  key: string;
  itemId: string;
  warehouseId: string;
  quantity: string;
}

interface Props {
  open: boolean;
  onClose: () => void;
  /** Prefill dari detail order (pelanggan, order, baris = sisa titipan per gudang). */
  fromOrder?: TollOrderDetail | null;
}

const today = () => new Date().toISOString().split('T')[0];
const newLine = (warehouseId = ''): Line => ({ key: crypto.randomUUID(), itemId: '', warehouseId, quantity: '' });

export default function CustomerReturnModal({ open, onClose, fromOrder }: Props) {
  const { t } = useTranslation('toll');
  const queryClient = useQueryClient();
  const company = useCompanyPDF();
  const [date, setDate] = useState(today());
  const [partyId, setPartyId] = useState('');
  const [tollOrderId, setTollOrderId] = useState('');
  const [nomorMobil, setNomorMobil] = useState('');
  const [driverName, setDriverName] = useState('');
  const [recipientName, setRecipientName] = useState('');
  const [notes, setNotes] = useState('');
  const [lines, setLines] = useState<Line[]>([newLine()]);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState<CustomerReturnRow | null>(null);

  const { data: items } = useInventoryItemOptions(open);
  const { data: warehouses } = useWarehouseOptions(open);
  const partyPicker = usePartyPicker({ type: 'Customer', selectedId: partyId || null, enabled: open });
  const { data: orderOptions } = useTollOrderOptions(partyId, 'RETURN', open && !!partyId);

  const { data: balances } = useQuery({
    queryKey: ['toll-stock', 'balances', partyId],
    queryFn: async () =>
      (await api.get<{ data: Array<{ itemId: string; warehouseId: string; quantity: number }> }>(`${TOLL_BASE}/stock/balances`, {
        params: { partyId },
      })).data.data,
    enabled: open && !!partyId,
  });
  const available = useMemo(() => {
    const m = new Map<string, number>();
    (balances ?? []).forEach((b) => m.set(`${b.itemId}|${b.warehouseId}`, num(b.quantity)));
    return m;
  }, [balances]);

  useEffect(() => {
    if (!open) return;
    setDate(today());
    setPartyId(fromOrder?.partyId ?? '');
    setTollOrderId(fromOrder?.id ?? '');
    setNomorMobil('');
    setDriverName('');
    setRecipientName('');
    setNotes('');
    setError('');
    setSaved(null);
    const prefill = (fromOrder?.stockByWarehouse ?? [])
      .filter((s) => num(s.balanceQty) > 0)
      .map((s) => ({ key: crypto.randomUUID(), itemId: s.itemId, warehouseId: s.warehouseId, quantity: String(num(s.balanceQty)) }));
    setLines(prefill.length ? prefill : [newLine()]);
  }, [open, fromOrder]);

  const defaultWarehouse = warehouses?.find((w) => w.isDefault)?.value ?? '';
  const update = (key: string, patch: Partial<Line>) => setLines((prev) => prev.map((l) => (l.key === key ? { ...l, ...patch } : l)));

  const mutation = useMutation({
    mutationFn: async (body: Record<string, unknown>) => (await api.post<CustomerReturnRow>(`${TOLL_BASE}/returns`, body)).data,
    onSuccess: (row) => {
      toast.success(t('returns.toast.saved', { number: row.returnNumber }));
      queryClient.invalidateQueries({ queryKey: ['toll-returns'] });
      queryClient.invalidateQueries({ queryKey: ['toll-orders'] });
      queryClient.invalidateQueries({ queryKey: ['toll-stock'] });
      setSaved(row);
    },
    onError: (err) => setError(errorMessage(err, t('returnForm.saveFailed'))),
  });

  const validLines = lines.filter((l) => l.itemId && (l.warehouseId || defaultWarehouse) && Number(l.quantity) > 0);
  const canSubmit = !!partyId && validLines.length > 0 && !mutation.isPending;

  const submit = () =>
    mutation.mutate({
      date,
      partyId,
      tollOrderId: tollOrderId || null,
      nomorMobil: nomorMobil || null,
      driverName: driverName || null,
      recipientName: recipientName || null,
      notes: notes || null,
      items: validLines.map((l) => ({ itemId: l.itemId, warehouseId: l.warehouseId || defaultWarehouse, quantity: Number(l.quantity) })),
    });

  const pdfDoc = saved ? (
    <CustomerReturnPDF
      returnNumber={saved.returnNumber}
      date={saved.date}
      party={{ name: saved.partyName, address: saved.partyAddress }}
      tollOrderNumber={saved.tollOrderNumber}
      nomorMobil={saved.nomorMobil}
      driverName={saved.driverName}
      recipientName={saved.recipientName}
      notes={saved.notes}
      items={(saved.items ?? []).map((i) => ({ code: i.itemCode, name: i.itemName, qty: num(i.quantity), unit: i.unit, description: i.warehouseName }))}
      company={company}
    />
  ) : null;

  return (
    <Dialog open={open} onOpenChange={(o) => !o && !mutation.isPending && onClose()}>
      <DialogContent size="lg" className={dialogShellClass}>
        <OverlayHeader>
          <DialogTitle className="flex items-center gap-2"><Truck className="size-5 text-primary" aria-hidden />{t('returnForm.title')}</DialogTitle>
          <DialogDescription>{t('returnForm.description')}</DialogDescription>
        </OverlayHeader>

        {saved ? (
          <>
            <OverlayBody className="gap-3">
              <p className="text-sm">{t('returns.toast.saved', { number: saved.returnNumber })}</p>
            </OverlayBody>
            <OverlayFooter>
              {pdfDoc && <PDFDownloadButton document={pdfDoc} fileName={`${saved.returnNumber}.pdf`} label={t('returns.actions.print')} />}
              <Button onClick={onClose}>{t('detail.close')}</Button>
            </OverlayFooter>
          </>
        ) : (
          <form noValidate className="flex min-h-0 flex-1 flex-col" onSubmit={(e) => { e.preventDefault(); if (canSubmit) submit(); }}>
            <OverlayBody className="gap-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label={t('returnForm.date')} required><DateInput value={date} onChange={setDate} /></Field>
                <Field label={t('returnForm.order')}>
                  {(control) => (
                    <Select value={tollOrderId || NONE_VALUE} onValueChange={(v) => setTollOrderId(v === NONE_VALUE ? '' : v)} disabled={!partyId}>
                      <SelectTrigger {...control}><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NONE_VALUE}>{t('returnForm.noOrder')}</SelectItem>
                        {(orderOptions ?? []).map((o) => <SelectItem key={o.id} value={o.id}>{o.orderNumber}</SelectItem>)}
                      </SelectContent>
                    </Select>
                  )}
                </Field>
              </div>
              <ComboboxField
                label={t('returnForm.party')}
                required
                {...partyPicker.pickerProps}
                value={partyId}
                onChange={(v) => { setPartyId(v); setTollOrderId(''); }}
                placeholder={t('form.partyPlaceholder')}
              />
              <div className="grid gap-4 sm:grid-cols-3">
                <Field label={t('returnForm.vehicle')}><Input value={nomorMobil} onChange={(e) => setNomorMobil(e.target.value)} /></Field>
                <Field label={t('returnForm.driver')}><Input value={driverName} onChange={(e) => setDriverName(e.target.value)} /></Field>
                <Field label={t('returnForm.recipient')}><Input value={recipientName} onChange={(e) => setRecipientName(e.target.value)} /></Field>
              </div>

              <div className="space-y-2">
                <p className="text-sm font-medium">{t('returnForm.items')}</p>
                {lines.map((l, idx) => {
                  const wh = l.warehouseId || defaultWarehouse;
                  const avail = l.itemId && wh ? available.get(`${l.itemId}|${wh}`) ?? 0 : null;
                  return (
                    <div key={l.key} className="grid gap-2 rounded-md border border-border p-2 sm:grid-cols-[1fr_12rem_8rem_auto] sm:items-end">
                      <ComboboxField
                        label={t('returnForm.item')}
                        options={items ?? []}
                        value={l.itemId}
                        onChange={(v) => update(l.key, { itemId: v })}
                        placeholder="…"
                      />
                      <Field label={t('returnForm.warehouse')}>
                        {(control) => (
                          <Select value={wh} onValueChange={(v) => update(l.key, { warehouseId: v })}>
                            <SelectTrigger {...control}><SelectValue /></SelectTrigger>
                            <SelectContent>
                              {(warehouses ?? []).map((w) => <SelectItem key={w.value} value={w.value}>{w.label}</SelectItem>)}
                            </SelectContent>
                          </Select>
                        )}
                      </Field>
                      <Field label={t('returnForm.quantity')} hint={avail !== null ? t('returnForm.available', { qty: qty(avail) }) : undefined}>
                        <NumberInput min={0} step="any" value={l.quantity} onChange={(e) => update(l.key, { quantity: e.target.value })}
                          aria-invalid={avail !== null && Number(l.quantity) > avail ? true : undefined} />
                      </Field>
                      <Button type="button" variant="ghost" size="icon" aria-label={t('returnForm.removeRow', { n: idx + 1 })}
                        onClick={() => setLines((prev) => (prev.length > 1 ? prev.filter((x) => x.key !== l.key) : prev))}>
                        <Trash2 aria-hidden />
                      </Button>
                    </div>
                  );
                })}
                <Button type="button" variant="outline" size="sm" onClick={() => setLines((prev) => [...prev, newLine(defaultWarehouse)])}>
                  <Plus aria-hidden />{t('returnForm.addRow')}
                </Button>
              </div>

              <Field label={t('returnForm.notes')}><Input value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
              <FormError message={error} />
            </OverlayBody>
            <OverlayFooter>
              <Button type="button" variant="outline" onClick={onClose} disabled={mutation.isPending}>{t('returnForm.cancel')}</Button>
              <Button type="submit" disabled={!canSubmit}>{mutation.isPending ? t('returnForm.saving') : t('returnForm.save')}</Button>
            </OverlayFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
