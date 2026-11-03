/**
 * Record an hour-meter (operating hours) reading for a machine, with the latest readings below.
 * The latest reading drives hour-interval schedules (server: ErpGaMeterReadingService / MaintenanceDue).
 */
import { useState } from 'react';
import { Trash2 } from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import api from '@/lib/api';
import { formatDate, formatNumber } from '@/lib/format';
import { Button } from '@/components/ui/button';
import { NumberInput } from '@/components/ui/number-input';
import { Textarea } from '@/components/ui/textarea';
import { DateInput, Field } from '@/components/shared';
import { FormModal } from './form-modal';
import { apiErrorMessage } from './wo-meta';
import { useTranslation } from 'react-i18next';

interface MeterReading {
  id: string;
  readingAt: string;
  hours: number;
  notes: string | null;
  workOrderCode: string | null;
  createdByName: string | null;
}

export function MeterReadingModal({ assetId, assetLabel, onClose }: { assetId: string; assetLabel: string; onClose: () => void }) {
  const { t } = useTranslation('eam');
  const queryClient = useQueryClient();
  const [form, setForm] = useState({ hours: '', readingAt: '', notes: '' });
  const [submitted, setSubmitted] = useState(false);
  const set = (patch: Partial<typeof form>) => setForm((f) => ({ ...f, ...patch }));
  const hoursError = form.hours.trim() === '' ? t('meter.hoursRequired') : undefined;

  const readings = useQuery({
    queryKey: ['ga-meter-readings', assetId],
    queryFn: async () => (await api.get<MeterReading[]>('/ga/maintenance/meter-readings', { params: { assetId } })).data ?? [],
  });
  const latest = readings.data?.[0];

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['ga-meter-readings', assetId] });
    queryClient.invalidateQueries({ queryKey: ['ga-schedules'] });
  };

  const save = useMutation({
    mutationFn: () =>
      api.post('/ga/maintenance/meter-readings', {
        assetId,
        hours: Number(form.hours),
        readingAt: form.readingAt || undefined,
        notes: form.notes || null,
      }),
    onSuccess: () => {
      toast.success(t('meter.saved'));
      refresh();
      onClose();
    },
    onError: (err) => toast.error(apiErrorMessage(err, t('meter.saveFailed'))),
  });

  const remove = useMutation({
    mutationFn: (id: string) => api.delete(`/ga/maintenance/meter-readings/${id}`),
    onSuccess: () => {
      toast.success(t('meter.deleted'));
      refresh();
    },
    onError: (err) => toast.error(apiErrorMessage(err, t('meter.deleteFailed'))),
  });

  return (
    <FormModal
      title={t('meter.title')}
      description={assetLabel}
      onClose={onClose}
      onSubmit={() => {
        setSubmitted(true);
        if (!hoursError) save.mutate();
      }}
      submitLabel={t('shared.save')}
      submitting={save.isPending}
    >
      <p className="text-sm text-muted-foreground">
        {latest
          ? t('meter.last', { hours: formatNumber(latest.hours), date: formatDate(latest.readingAt) })
          : readings.isLoading
            ? '…'
            : t('meter.none')}
      </p>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Field label={t('meter.hours')} required error={submitted ? hoursError : undefined}>
          <NumberInput min={0} step={1} value={form.hours} onChange={(e) => set({ hours: e.target.value })} autoFocus />
        </Field>
        <Field label={t('meter.readingAt')} hint={t('meter.readingAtHint')}>
          {(control) => <DateInput id={control.id} value={form.readingAt} onChange={(v) => set({ readingAt: v })} />}
        </Field>
      </div>
      <Field label={t('shared.notes')}>
        <Textarea rows={2} value={form.notes} onChange={(e) => set({ notes: e.target.value })} />
      </Field>

      <section aria-label={t('meter.history')} className="space-y-2">
        <h3 className="text-sm font-medium text-foreground">{t('meter.history')}</h3>
        {readings.error ? (
          <p role="alert" className="text-sm text-danger-text">
            {t('meter.loadFailed')}
          </p>
        ) : (
          <ul className="divide-y divide-border rounded-md border border-border">
            {(readings.data ?? []).slice(0, 10).map((r) => (
              <li key={r.id} className="flex items-center gap-3 px-3 py-2 text-sm">
                <span className="num w-24 shrink-0 font-medium text-foreground">{formatNumber(r.hours)} h</span>
                <span className="min-w-0 flex-1 text-muted-foreground">
                  <span className="whitespace-nowrap">{formatDate(r.readingAt)}</span>
                  {r.workOrderCode && <> · {t('meter.fromWo', { code: r.workOrderCode })}</>}
                  {r.createdByName && <> · {t('meter.by', { name: r.createdByName })}</>}
                  {r.notes && <span className="block truncate">{r.notes}</span>}
                </span>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  className="shrink-0 text-danger-text hover:bg-danger-subtle"
                  aria-label={t('meter.delete', { hours: formatNumber(r.hours) })}
                  disabled={remove.isPending}
                  onClick={() => remove.mutate(r.id)}
                >
                  <Trash2 aria-hidden />
                </Button>
              </li>
            ))}
            {readings.data?.length === 0 && <li className="px-3 py-2 text-sm text-muted-foreground">{t('meter.none')}</li>}
          </ul>
        )}
      </section>
    </FormModal>
  );
}
