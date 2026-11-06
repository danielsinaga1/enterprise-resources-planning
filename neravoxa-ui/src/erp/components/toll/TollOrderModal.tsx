/**
 * Buat / ubah Order Jasa Maklon. Tarif, ongkos minimum, dan dasar tarif boleh dikosongkan:
 * server mengisinya dari tarif khusus pelanggan → tarif item jasa (pratinjau lewat /toll/orders/defaults).
 */
import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Factory } from 'lucide-react';
import { toast } from 'sonner';
import api from '@/lib/api';
import { usePartyPicker } from '@/hooks/usePartyPicker';
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
  BY_PRODUCT_OWNERS,
  PRICING_BASES,
  TOLL_BASE,
  errorMessage,
  num,
  type ByProductOwner,
  type PricingBasis,
  type TollOrderRow,
} from './common';

interface Props {
  open: boolean;
  onClose: () => void;
  /** Ubah order ini; kosong = order baru. */
  order?: TollOrderRow | null;
  onSaved?: (order: TollOrderRow) => void;
}

const today = () => new Date().toISOString().split('T')[0];
const numOrEmpty = (v: unknown) => (v === null || v === undefined || v === '' ? '' : String(num(v as string)));

export default function TollOrderModal({ open, onClose, order, onSaved }: Props) {
  const { t } = useTranslation('toll');
  const queryClient = useQueryClient();
  const [date, setDate] = useState(today());
  const [partyId, setPartyId] = useState('');
  const [serviceItemId, setServiceItemId] = useState('');
  const [pricingBasis, setPricingBasis] = useState<PricingBasis | ''>('');
  const [rate, setRate] = useState('');
  const [minimumCharge, setMinimumCharge] = useState('');
  const [taxPct, setTaxPct] = useState('0');
  const [owner, setOwner] = useState<ByProductOwner>('Customer');
  const [plannedQty, setPlannedQty] = useState('');
  const [expectedYieldPct, setExpectedYieldPct] = useState('');
  const [storageRate, setStorageRate] = useState('0');
  const [storageFreeDays, setStorageFreeDays] = useState('0');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setDate(order?.date ? order.date.slice(0, 10) : today());
    setPartyId(order?.partyId ?? '');
    setServiceItemId(order?.serviceItemId ?? '');
    setPricingBasis(order?.pricingBasis ?? '');
    setRate(order ? numOrEmpty(order.rate) : '');
    setMinimumCharge(order ? numOrEmpty(order.minimumCharge) : '');
    setTaxPct(order ? numOrEmpty(order.taxPct) || '0' : '0');
    setOwner(order?.byProductOwner ?? 'Customer');
    setPlannedQty(order ? numOrEmpty(order.plannedQty) : '');
    setExpectedYieldPct(order ? numOrEmpty(order.expectedYieldPct) : '');
    setStorageRate(order ? numOrEmpty(order.storageRate) || '0' : '0');
    setStorageFreeDays(order ? String(order.storageFreeDays ?? 0) : '0');
    setNotes(order?.notes ?? '');
    setError('');
  }, [open, order]);

  const partyPicker = usePartyPicker({ type: 'Customer', selectedId: partyId || null, enabled: open, activeOnly: true });

  const { data: serviceItems } = useQuery({
    queryKey: ['service-items', 'toll-picker'],
    queryFn: async () => {
      const res = await api.get('/service-items', { params: { limit: 500 } });
      const all = (res.data.data ?? res.data) as Array<{ id: string; code: string; name: string; isActive: boolean }>;
      return all.filter((s) => s.isActive);
    },
    enabled: open,
  });

  const { data: defaults } = useQuery({
    queryKey: ['toll-orders', 'defaults', partyId, serviceItemId],
    queryFn: async () =>
      (await api.get<{ pricingBasis: PricingBasis; rate: number | null; minimumCharge: number | null; source: string }>(
        `${TOLL_BASE}/orders/defaults`, { params: { partyId: partyId || undefined, serviceItemId: serviceItemId || undefined } },
      )).data,
    enabled: open && !!serviceItemId,
  });

  const mutation = useMutation({
    mutationFn: async (body: Record<string, unknown>) =>
      order ? (await api.put(`${TOLL_BASE}/orders/${order.id}`, body)).data : (await api.post(`${TOLL_BASE}/orders`, body)).data,
    onSuccess: (saved: TollOrderRow) => {
      toast.success(t('orders.toast.saved'));
      queryClient.invalidateQueries({ queryKey: ['toll-orders'] });
      onSaved?.(saved);
      onClose();
    },
    onError: (err) => setError(errorMessage(err, t('form.saveFailed'))),
  });

  const optional = (v: string) => (v.trim() === '' ? null : Number(v));
  const canSubmit = !!partyId && !!date && !mutation.isPending;

  const submit = () =>
    mutation.mutate({
      date,
      partyId,
      serviceItemId: serviceItemId || null,
      pricingBasis: pricingBasis || null,
      rate: optional(rate),
      minimumCharge: optional(minimumCharge),
      taxPct: optional(taxPct) ?? 0,
      byProductOwner: owner,
      plannedQty: optional(plannedQty),
      expectedYieldPct: optional(expectedYieldPct),
      storageRate: optional(storageRate) ?? 0,
      storageFreeDays: optional(storageFreeDays) ?? 0,
      notes: notes.trim() || null,
    });

  const effectiveBasis = pricingBasis || defaults?.pricingBasis || 'InputQty';

  return (
    <Dialog open={open} onOpenChange={(o) => !o && !mutation.isPending && onClose()}>
      <DialogContent size="lg" className={dialogShellClass}>
        <form noValidate className="flex min-h-0 flex-1 flex-col" onSubmit={(e) => { e.preventDefault(); if (canSubmit) submit(); }}>
          <OverlayHeader>
            <DialogTitle className="flex items-center gap-2">
              <Factory className="size-5 text-primary" aria-hidden />
              {order ? t('form.titleEdit', { number: order.orderNumber }) : t('form.titleCreate')}
            </DialogTitle>
            <DialogDescription>{t('form.description')}</DialogDescription>
          </OverlayHeader>

          <OverlayBody className="gap-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label={t('form.date')} required>
                <DateInput value={date} onChange={setDate} />
              </Field>
              <Field label={t('form.byProductOwner')}>
                {(control) => (
                  <Select value={owner} onValueChange={(v) => setOwner(v as ByProductOwner)}>
                    <SelectTrigger {...control}><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {BY_PRODUCT_OWNERS.map((o) => <SelectItem key={o} value={o}>{t(`owner.${o}`)}</SelectItem>)}
                    </SelectContent>
                  </Select>
                )}
              </Field>
            </div>

            <ComboboxField
              label={t('form.party')}
              required
              {...partyPicker.pickerProps}
              value={partyId}
              onChange={setPartyId}
              placeholder={t('form.partyPlaceholder')}
            />

            <Field label={t('form.serviceItem')}>
              {(control) => (
                <Select value={serviceItemId || NONE_VALUE} onValueChange={(v) => setServiceItemId(v === NONE_VALUE ? '' : v)}>
                  <SelectTrigger {...control}><SelectValue placeholder={t('form.serviceItemPlaceholder')} /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NONE_VALUE}>{t('form.noServiceItem')}</SelectItem>
                    {(serviceItems ?? []).map((s) => <SelectItem key={s.id} value={s.id}>{s.code} · {s.name}</SelectItem>)}
                  </SelectContent>
                </Select>
              )}
            </Field>

            <div className="grid gap-4 sm:grid-cols-3">
              <Field label={t('form.pricingBasis')}>
                {(control) => (
                  <Select value={effectiveBasis} onValueChange={(v) => setPricingBasis(v as PricingBasis)}>
                    <SelectTrigger {...control}><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {PRICING_BASES.map((b) => <SelectItem key={b} value={b}>{t(`basis.${b}`)}</SelectItem>)}
                    </SelectContent>
                  </Select>
                )}
              </Field>
              <Field
                label={t('form.rate')}
                hint={defaults ? t('form.rateHint', { source: t(`form.rateSource.${defaults.source}`) }) : undefined}
              >
                <NumberInput min={0} step="any" value={rate} onChange={(e) => setRate(e.target.value)}
                  placeholder={defaults?.rate != null ? String(defaults.rate) : '0'} />
              </Field>
              <Field label={t('form.minimumCharge')} hint={t('form.minimumHint')}>
                <NumberInput min={0} step="any" value={minimumCharge} onChange={(e) => setMinimumCharge(e.target.value)}
                  placeholder={defaults?.minimumCharge != null ? String(defaults.minimumCharge) : '0'} />
              </Field>
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              <Field label={t('form.taxPct')}>
                <NumberInput min={0} max={100} step="any" value={taxPct} onChange={(e) => setTaxPct(e.target.value)} />
              </Field>
              <Field label={t('form.plannedQty')}>
                <NumberInput min={0} step="any" value={plannedQty} onChange={(e) => setPlannedQty(e.target.value)} />
              </Field>
              <Field label={t('form.expectedYieldPct')}>
                <NumberInput min={0} max={100} step="any" value={expectedYieldPct} onChange={(e) => setExpectedYieldPct(e.target.value)} />
              </Field>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <Field label={t('form.storageRate')}>
                <NumberInput min={0} step="any" value={storageRate} onChange={(e) => setStorageRate(e.target.value)} />
              </Field>
              <Field label={t('form.storageFreeDays')}>
                <NumberInput min={0} step={1} value={storageFreeDays} onChange={(e) => setStorageFreeDays(e.target.value)} />
              </Field>
            </div>

            <Field label={t('form.notes')}>
              <Input value={notes} onChange={(e) => setNotes(e.target.value)} />
            </Field>

            <FormError message={error} />
          </OverlayBody>

          <OverlayFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={mutation.isPending}>{t('form.cancel')}</Button>
            <Button type="submit" disabled={!canSubmit}>{mutation.isPending ? t('form.saving') : t('form.save')}</Button>
          </OverlayFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
