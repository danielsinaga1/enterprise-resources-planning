/**
 * Pengaturan → Asisten AI: status konfigurasi (model Hugging Face) dan basis pengetahuan RAG.
 * Daftar butuh izin lihat Pengaturan; unggah/hapus/proses ulang butuh izin ubah Pengaturan.
 */
import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BookOpen, RefreshCw, Sparkles, Trash2, Upload } from 'lucide-react';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import { can } from '@/lib/permissions';
import { formatDateTime } from '@/lib/format';
import { assistantApi, type KnowledgeDocument } from '@/lib/assistant';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ConfirmDialog, EmptyState, Field, LoadingState } from '@/components/shared';
import { SettingsCard } from '@/components/settings/SettingsCard';
import { apiError } from '@/components/fixed-assets/errors';

const DOCS_KEY = ['assistant', 'documents'];
const ACCEPT = '.pdf,.docx,.txt,.md,.markdown,.csv';
const STATUS_VARIANT = { processing: 'info', ready: 'success', failed: 'danger' } as const;

export function AssistantSettingsTab() {
  const { t } = useTranslation('assistant');
  const qc = useQueryClient();
  const canEdit = can('settings', 'edit');
  const fileRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState('');
  const [toDelete, setToDelete] = useState<KnowledgeDocument | null>(null);

  const status = useQuery({ queryKey: ['assistant', 'status'], queryFn: assistantApi.status });
  const docs = useQuery({
    queryKey: DOCS_KEY,
    queryFn: assistantApi.documents,
    // Embedding berjalan di latar belakang: muat ulang selama masih ada yang diproses.
    refetchInterval: (q) => (q.state.data?.documents.some((d) => d.status === 'processing') ? 3000 : false),
  });
  const configured = status.data?.configured ?? false;
  const refresh = () => qc.invalidateQueries({ queryKey: DOCS_KEY });

  const upload = useMutation({
    mutationFn: () => assistantApi.upload(file as File, title),
    onSuccess: () => {
      toast.success(t('settings.uploaded'));
      setFile(null);
      setTitle('');
      if (fileRef.current) fileRef.current.value = '';
      void refresh();
    },
    onError: (err) => toast.error(apiError(err, t('error'))),
  });
  const reindexGuide = useMutation({
    mutationFn: assistantApi.reindexGuide,
    onSuccess: () => {
      toast.success(t('settings.reindexGuideDone'));
      setTimeout(() => void refresh(), 1500);
    },
    onError: (err) => toast.error(apiError(err, t('error'))),
  });
  const reindex = useMutation({
    mutationFn: (id: string) => assistantApi.reindex(id),
    onSuccess: () => void refresh(),
    onError: (err) => toast.error(apiError(err, t('error'))),
  });
  const remove = useMutation({
    mutationFn: (id: string) => assistantApi.remove(id),
    onSuccess: () => {
      toast.success(t('settings.deleted'));
      void refresh();
    },
    onError: (err) => toast.error(apiError(err, t('error'))),
  });

  const stats = docs.data?.stats;
  const documents = docs.data?.documents ?? [];

  return (
    <div className="space-y-5">
      <SettingsCard
        title={(
          <span className="inline-flex items-center gap-2">
            <Sparkles className="size-4 text-primary" aria-hidden /> {t('settings.statusTitle')}
          </span>
        )}
        actions={status.data && (
          <Badge variant={configured ? 'success' : 'warning'}>{configured ? t('settings.active') : t('settings.inactive')}</Badge>
        )}
      >
        {status.isLoading ? <LoadingState /> : (
          <div className="space-y-3 text-sm">
            {!configured && <p className="rounded-md bg-warning-subtle px-3 py-2 text-warning-text">{t('settings.inactiveHelp')}</p>}
            <dl className="grid grid-cols-1 gap-x-6 gap-y-1 sm:grid-cols-[max-content_1fr]">
              <dt className="text-muted-foreground">{t('settings.provider')}</dt>
              <dd>{status.data?.provider === 'ollama' ? t('settings.providerOllama') : t('settings.providerHuggingFace')}</dd>
              <dt className="text-muted-foreground">{t('settings.chatModel')}</dt>
              <dd className="font-mono text-xs leading-5">{status.data?.chatModel}</dd>
              <dt className="text-muted-foreground">{t('settings.embeddingModel')}</dt>
              <dd className="font-mono text-xs leading-5">{status.data?.embeddingModel}</dd>
            </dl>
            <p className="text-xs text-muted-foreground">
              {status.data?.provider === 'ollama' ? t('settings.privacyLocal') : t('settings.privacy')}
            </p>
          </div>
        )}
      </SettingsCard>

      <SettingsCard
        title={(
          <span className="inline-flex items-center gap-2">
            <BookOpen className="size-4 text-primary" aria-hidden /> {t('settings.knowledgeTitle')}
          </span>
        )}
        description={t('settings.knowledgeHelp')}
        actions={canEdit && configured && (
          <Button variant="outline" size="sm" onClick={() => reindexGuide.mutate()} disabled={reindexGuide.isPending}>
            <RefreshCw className="size-3.5" aria-hidden /> {t('settings.reindexGuide')}
          </Button>
        )}
      >
        <div className="space-y-4">
          {!canEdit && <p className="text-xs text-muted-foreground">{t('settings.readOnly')}</p>}
          {canEdit && configured && (
            <form
              className="grid grid-cols-1 gap-3 rounded-md border border-border bg-muted/40 p-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end"
              onSubmit={(e) => { e.preventDefault(); if (file) upload.mutate(); }}
            >
              <Field label={t('settings.fileLabel')}>
                {(p) => (
                  <Input {...p} ref={fileRef} type="file" accept={ACCEPT} onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
                )}
              </Field>
              <Field label={t('settings.titleLabel')}>
                {(p) => <Input {...p} value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} />}
              </Field>
              <Button type="submit" disabled={!file || upload.isPending}>
                <Upload className="size-4" aria-hidden /> {upload.isPending ? t('settings.uploading') : t('settings.upload')}
              </Button>
            </form>
          )}

          {stats && (
            <p className="text-xs text-muted-foreground">
              {t('settings.stats', { ready: stats.ready, chunks: stats.chunks })}
            </p>
          )}
          {!!stats?.stale && <p className="rounded-md bg-warning-subtle px-3 py-2 text-xs text-warning-text">{t('settings.stale', { count: stats.stale })}</p>}

          {docs.isLoading ? <LoadingState /> : documents.length === 0 ? (
            <EmptyState title={t('settings.empty')} />
          ) : (
            <div className="overflow-x-auto rounded-md border border-border">
              <table className="w-full text-sm">
                <thead className="bg-muted/60 text-xs text-muted-foreground">
                  <tr>
                    <th className="px-3 py-2 text-left font-medium">{t('settings.columns.title')}</th>
                    <th className="px-3 py-2 text-left font-medium">{t('settings.columns.source')}</th>
                    <th className="px-3 py-2 text-left font-medium">{t('settings.columns.status')}</th>
                    <th className="px-3 py-2 text-right font-medium">{t('settings.columns.chunks')}</th>
                    <th className="px-3 py-2 text-left font-medium">{t('settings.columns.updated')}</th>
                    {canEdit && <th className="px-3 py-2" aria-label="aksi" />}
                  </tr>
                </thead>
                <tbody>
                  {documents.map((d) => (
                    <tr key={d.id} className="border-t border-border align-top">
                      <td className="px-3 py-2">
                        <p className="font-medium text-foreground">{d.title}</p>
                        {d.fileName && d.source === 'upload' && <p className="text-xs text-muted-foreground">{d.fileName}</p>}
                        {d.status === 'failed' && d.error && <p className="mt-0.5 text-xs text-danger-text">{d.error}</p>}
                      </td>
                      <td className="px-3 py-2 text-muted-foreground">{t(`settings.source.${d.source}`)}</td>
                      <td className="px-3 py-2">
                        <Badge variant={STATUS_VARIANT[d.status]}>{t(`settings.status.${d.status}`)}</Badge>
                      </td>
                      <td className="num px-3 py-2 text-right">{d.chunkCount}</td>
                      <td className="px-3 py-2 text-xs text-muted-foreground">{formatDateTime(d.updatedAt)}</td>
                      {canEdit && (
                        <td className="px-3 py-2">
                          {d.source === 'upload' && (
                            <div className="flex justify-end gap-1">
                              {configured && (
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => reindex.mutate(d.id)}
                                  disabled={d.status === 'processing' || reindex.isPending}
                                  title={t('settings.reindex')}
                                >
                                  <RefreshCw className="size-3.5" aria-hidden />
                                  <span className="sr-only">{t('settings.reindex')}</span>
                                </Button>
                              )}
                              <Button variant="ghost" size="sm" onClick={() => setToDelete(d)} title={t('settings.delete')}>
                                <Trash2 className="size-3.5 text-danger-text" aria-hidden />
                                <span className="sr-only">{t('settings.delete')}</span>
                              </Button>
                            </div>
                          )}
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </SettingsCard>

      <ConfirmDialog
        open={toDelete !== null}
        onOpenChange={(o) => !o && setToDelete(null)}
        title={t('settings.deleteConfirm', { title: toDelete?.title ?? '' })}
        tone="danger"
        confirmLabel={t('settings.delete')}
        loading={remove.isPending}
        onConfirm={async () => {
          if (toDelete) await remove.mutateAsync(toDelete.id);
          setToDelete(null);
        }}
      />
    </div>
  );
}
