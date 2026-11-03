/**
 * Email copy of notifications (GET/PUT /notifications/preferences). Shown on the Notifications page:
 * a switch when the administrator enabled email, otherwise a short note. Includes "send test email".
 */
import { useId } from 'react';
import { Mail, Send } from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import api from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';

interface Preferences {
  emailAvailable: boolean;
  emailEnabled: boolean;
  email: string | null;
}

const KEY = ['notification-preferences'];

/** Server error text ({ error }) or the fallback. */
const errorMessage = (err: unknown, fallback: string): string => {
  const e = err as { response?: { data?: { error?: unknown } } };
  return typeof e?.response?.data?.error === 'string' ? e.response.data.error : fallback;
};

export function NotificationEmailPreference() {
  const { t } = useTranslation('shell');
  const switchId = useId();
  const queryClient = useQueryClient();
  const prefs = useQuery({ queryKey: KEY, queryFn: async () => (await api.get<Preferences>('/notifications/preferences')).data });

  const toggle = useMutation({
    mutationFn: async (emailEnabled: boolean) => (await api.put<Preferences>('/notifications/preferences', { emailEnabled })).data,
    onSuccess: (data) => {
      queryClient.setQueryData(KEY, data);
      toast.success(data.emailEnabled ? t('notifications.email.turnedOn') : t('notifications.email.turnedOff'));
    },
    onError: (err) => toast.error(errorMessage(err, t('notifications.email.saveFailed'))),
  });

  const test = useMutation({
    mutationFn: () => api.post('/notifications/preferences/test-email', {}),
    onSuccess: () => toast.success(t('notifications.email.testSent', { email: prefs.data?.email ?? '' })),
    onError: (err) => toast.error(errorMessage(err, t('notifications.email.testFailed'))),
  });

  const data = prefs.data;
  if (!data) return null;
  const hasEmail = !!data.email && data.email.includes('@');

  return (
    <section
      aria-label={t('notifications.email.title')}
      className="flex flex-col gap-3 rounded-lg border border-border bg-card px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
    >
      <div className="flex min-w-0 items-start gap-3">
        <span className="mt-0.5 inline-flex size-8 shrink-0 items-center justify-center rounded-md bg-primary-subtle text-primary-subtle-foreground">
          <Mail className="size-4" aria-hidden />
        </span>
        <div className="min-w-0">
          {data.emailAvailable ? (
            <label htmlFor={switchId} className="text-sm font-medium text-foreground">
              {t('notifications.email.title')}
            </label>
          ) : (
            <p className="text-sm font-medium text-foreground">{t('notifications.email.title')}</p>
          )}
          <p className="text-xs text-muted-foreground">
            {!data.emailAvailable
              ? t('notifications.email.unavailable')
              : hasEmail
                ? t('notifications.email.hint', { email: data.email })
                : t('notifications.email.noAddress')}
          </p>
        </div>
      </div>
      {data.emailAvailable && hasEmail && (
        <div className="flex shrink-0 items-center gap-3">
          <Button variant="ghost" size="sm" onClick={() => test.mutate()} loading={test.isPending} disabled={!data.emailEnabled}>
            <Send aria-hidden />
            {t('notifications.email.sendTest')}
          </Button>
          <Switch
            id={switchId}
            checked={data.emailEnabled}
            disabled={toggle.isPending}
            onCheckedChange={(v: boolean) => toggle.mutate(v)}
          />
        </div>
      )}
    </section>
  );
}
