/** Tarif jasa khusus per pelanggan (dipakai sebagai default Order Jasa Maklon). */
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Plus, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import api from '@/lib/api';
import { can } from '@/lib/permissions';
import { formatRupiah } from '@/lib/format';
import { usePartyPicker } from '@/hooks/usePartyPicker';
import { Button } from '@/components/ui/button';
import { NumberInput } from '@/components/ui/number-input';
import { ComboboxField } from '../overlays/ComboboxField';
import { TOLL_BASE, errorMessage, num } from './common';

interface RateRow {
  id: string;
  partyId: string;
  partyName: string;
  rate: number | string;
  minimumCharge: number | string | null;
}

export default function ServicePartyRates({ serviceItemId }: { serviceItemId: string }) {
  const { t } = useTranslation('toll');
  const queryClient = useQueryClient();
  const canEdit = can('toll_service', 'edit');
  const [partyId, setPartyId] = useState('');
  const [rate, setRate] = useState('');
  const [minimum, setMinimum] = useState('');
  const partyPicker = usePartyPicker({ type: 'Customer', selectedId: partyId || null, activeOnly: true });

  const { data: rates } = useQuery({
    queryKey: ['toll-rates', serviceItemId],
    queryFn: async () => (await api.get<RateRow[]>(`${TOLL_BASE}/rates`, { params: { serviceItemId } })).data,
  });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['toll-rates', serviceItemId] });

  const save = useMutation({
    mutationFn: () => api.post(`${TOLL_BASE}/rates`, {
      serviceItemId, partyId, rate: Number(rate), minimumCharge: minimum.trim() === '' ? null : Number(minimum),
    }),
    onSuccess: () => {
      toast.success(t('rates.saved'));
      setPartyId('');
      setRate('');
      setMinimum('');
      invalidate();
    },
    onError: (err) => toast.error(errorMessage(err, t('rates.failed'))),
  });
  const remove = useMutation({
    mutationFn: (id: string) => api.delete(`${TOLL_BASE}/rates/${id}`),
    onSuccess: invalidate,
    onError: (err) => toast.error(errorMessage(err, t('rates.failed'))),
  });

  return (
    <section className="space-y-2 rounded-lg border border-border p-3">
      <div>
        <h3 className="text-sm font-semibold">{t('rates.title')}</h3>
        <p className="text-xs text-muted-foreground">{t('rates.description')}</p>
      </div>
      {(rates ?? []).length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('rates.empty')}</p>
      ) : (
        <ul className="divide-y divide-border text-sm">
          {(rates ?? []).map((r) => (
            <li key={r.id} className="flex items-center justify-between gap-2 py-1.5">
              <span className="min-w-0 truncate">{r.partyName}</span>
              <span className="flex items-center gap-2">
                <span className="num">{formatRupiah(num(r.rate))}</span>
                {r.minimumCharge != null && (
                  <span className="text-xs text-muted-foreground">{t('rates.minShort', { amount: formatRupiah(num(r.minimumCharge)) })}</span>
                )}
                {canEdit && (
                  <Button type="button" variant="ghost" size="icon-sm" aria-label={t('rates.delete', { name: r.partyName })}
                    onClick={() => remove.mutate(r.id)}>
                    <Trash2 aria-hidden />
                  </Button>
                )}
              </span>
            </li>
          ))}
        </ul>
      )}
      {canEdit && (
        <div className="grid gap-2 sm:grid-cols-[1fr_8rem_8rem_auto] sm:items-end">
          <ComboboxField label={t('rates.party')} {...partyPicker.pickerProps} value={partyId} onChange={setPartyId} placeholder="…" />
          <label className="grid gap-1 text-xs text-muted-foreground">
            {t('rates.rate')}
            <NumberInput min={0} step="any" value={rate} onChange={(e) => setRate(e.target.value)} />
          </label>
          <label className="grid gap-1 text-xs text-muted-foreground">
            {t('rates.minimum')}
            <NumberInput min={0} step="any" value={minimum} onChange={(e) => setMinimum(e.target.value)} />
          </label>
          <Button type="button" variant="outline" disabled={!partyId || rate === '' || save.isPending} onClick={() => save.mutate()}>
            <Plus aria-hidden />{t('rates.add')}
          </Button>
        </div>
      )}
    </section>
  );
}
