/**
 * Panel Asisten AI (pojok kanan bawah) di seluruh halaman ERP.
 *
 * - Saran "langkah berikutnya" untuk halaman aktif (berbasis aturan, jalan tanpa model AI).
 * - Chat RAG: jawaban dari panduan + dokumen perusahaan (dengan sumber), data ERP lewat tool
 *   sesuai hak akses, dan kartu usulan input data yang baru disimpan setelah pengguna menekan Buat.
 * Riwayat percakapan disimpan per tab (sessionStorage) dan dikirim ulang tiap giliran.
 */
import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { ArrowRight, BookOpen, ChevronDown, Loader2, RotateCcw, SendHorizontal, Sparkles, X } from 'lucide-react';
import { useLocation, useNavigate } from '@/lib/router';
import { accessibleRoutes, matchRoute } from '@/app/routes';
import { can, getStoredUser } from '@/lib/permissions';
import { formatRupiah } from '@/lib/format';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import {
  assistantApi,
  clearConversation,
  executeProposal,
  isKnownProposal,
  loadConversation,
  newId,
  saveConversation,
  type ChatMessage,
  type Proposal,
  type ProposalState,
  type Suggestion,
} from '@/lib/assistant';
import { AssistantMarkdown } from './AssistantMarkdown';
import { ProposalCard } from './ProposalCard';

const EXAMPLES = ['today', 'howInvoice', 'overdue', 'cashDraft'] as const;

function errorText(err: unknown, fallback: string): string {
  const e = err as { response?: { data?: { error?: string } }; message?: string };
  return e?.response?.data?.error ?? e?.message ?? fallback;
}

export function AssistantWidget() {
  const { t } = useTranslation('assistant');
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const route = location.pathname || '/';
  const routeEntry = matchRoute(route);
  const pageTitle = routeEntry?.label ?? '';

  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>(loadConversation);
  const [input, setInput] = useState('');
  const [pending, setPending] = useState(false);
  const [expandedSources, setExpandedSources] = useState<string | null>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const status = useQuery({ queryKey: ['assistant', 'status'], queryFn: assistantApi.status, staleTime: 5 * 60_000 });
  const suggestions = useQuery({
    queryKey: ['assistant', 'suggestions', route],
    queryFn: () => assistantApi.suggestions(route),
    enabled: open,
    staleTime: 60_000,
  });
  const configured = status.data?.configured ?? false;

  const pages = useMemo(
    () => accessibleRoutes((m) => can(m))
      .filter((e) => !e.path.includes(':'))
      .map((e) => ({ path: e.path, label: e.label })),
    // Label mengikuti bahasa; cukup dihitung ulang saat panel dibuka.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [open],
  );

  useEffect(() => saveConversation(messages), [messages]);
  useEffect(() => {
    if (open) endRef.current?.scrollIntoView({ block: 'end' });
  }, [messages, pending, open]);
  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  const goTo = (path: string) => {
    navigate(path);
    if (window.matchMedia('(max-width: 639px)').matches) setOpen(false);
  };

  const send = async (raw: string) => {
    const text = raw.trim();
    if (!text || pending || !configured) return;
    const userMsg: ChatMessage = { id: newId(), role: 'user', content: text };
    const next = [...messages, userMsg];
    setMessages(next);
    setInput('');
    setPending(true);
    try {
      const history = next.filter((m) => !m.error).map((m) => ({ role: m.role, content: m.content }));
      const res = await assistantApi.chat(history, { route, pageTitle, pages });
      const proposals = res.proposals.filter(isKnownProposal);
      const states: Record<string, ProposalState> = {};
      proposals.forEach((p) => { states[p.id] = { state: 'pending' }; });
      setMessages((m) => [...m, {
        id: newId(), role: 'assistant', content: res.reply, sources: res.sources, proposals, proposalStates: states,
      }]);
      queryClient.setQueryData(['assistant', 'suggestions', route], res.suggestions);
    } catch (err) {
      setMessages((m) => [...m, { id: newId(), role: 'assistant', content: errorText(err, t('error')), error: true }]);
    } finally {
      setPending(false);
    }
  };

  const setProposalState = (messageId: string, proposalId: string, state: ProposalState) => {
    setMessages((all) => all.map((m) => (m.id === messageId
      ? { ...m, proposalStates: { ...(m.proposalStates ?? {}), [proposalId]: state } }
      : m)));
  };

  const createFromProposal = async (messageId: string, p: Proposal) => {
    setProposalState(messageId, p.id, { state: 'creating' });
    try {
      const link = await executeProposal(p);
      setProposalState(messageId, p.id, { state: 'created', link });
      void queryClient.invalidateQueries({ queryKey: ['assistant', 'suggestions'] });
    } catch (err) {
      setProposalState(messageId, p.id, { state: 'failed', error: errorText(err, t('error')) });
    }
  };

  const reset = () => {
    clearConversation();
    setMessages([]);
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      void send(input);
    }
  };

  const items = suggestions.data ?? [];
  const firstName = (getStoredUser()?.fullName ?? '').split(' ')[0];

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="fixed bottom-5 right-5 z-[45] inline-flex items-center gap-2 rounded-full bg-primary px-4 py-3 text-sm font-medium text-primary-foreground shadow-[var(--shadow-3,0_10px_30px_rgba(0,0,0,0.18))] transition-colors hover:bg-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        aria-label={t('launcher')}
      >
        <Sparkles className="size-4" aria-hidden />
        <span className="hidden sm:inline">{t('launcher')}</span>
      </button>
    );
  }

  return (
    <section
      role="dialog"
      aria-modal="false"
      aria-label={t('title')}
      onKeyDown={(e) => { if (e.key === 'Escape') setOpen(false); }}
      className="fixed inset-x-2 bottom-2 top-16 z-[45] flex flex-col overflow-hidden rounded-xl border border-border bg-card text-foreground shadow-[var(--shadow-3,0_20px_50px_rgba(0,0,0,0.25))] sm:inset-x-auto sm:bottom-5 sm:right-5 sm:top-auto sm:h-[min(680px,calc(100vh-2.5rem))] sm:w-[420px]"
    >
      <header className="flex items-center gap-2 border-b border-border bg-primary px-3 py-2.5 text-primary-foreground">
        <Sparkles className="size-4 shrink-0" aria-hidden />
        <div className="min-w-0 flex-1">
          <h2 className="font-heading text-sm font-semibold leading-tight text-primary-foreground">{t('title')}</h2>
          <p className="truncate text-[11px] opacity-80">{pageTitle ? t('onPage', { page: pageTitle }) : t('subtitle')}</p>
        </div>
        {messages.length > 0 && (
          <button type="button" onClick={reset} className="rounded-md p-1.5 hover:bg-white/15" title={t('clear')} aria-label={t('clear')}>
            <RotateCcw className="size-4" aria-hidden />
          </button>
        )}
        <button type="button" onClick={() => setOpen(false)} className="rounded-md p-1.5 hover:bg-white/15" title={t('close')} aria-label={t('close')}>
          <X className="size-4" aria-hidden />
        </button>
      </header>

      <div className="flex-1 space-y-3 overflow-y-auto px-3 py-3" aria-live="polite">
        {!status.isLoading && !configured && (
          <div className="rounded-md border border-warning/40 bg-warning-subtle px-3 py-2 text-xs text-warning-text">{t('notConfigured')}</div>
        )}

        {messages.length === 0 && (
          <div className="space-y-3">
            <p className="text-sm text-muted-foreground">{t('greeting', { name: firstName || t('there') })}</p>
            <NextSteps items={items} loading={suggestions.isLoading} onOpen={goTo} />
            {configured && (
              <div>
                <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t('examples')}</p>
                <div className="flex flex-wrap gap-1.5">
                  {EXAMPLES.map((key) => (
                    <button
                      key={key}
                      type="button"
                      onClick={() => (key === 'cashDraft' ? setInput(t(`example.${key}`)) : void send(t(`example.${key}`)))}
                      className="rounded-full border border-border bg-background px-2.5 py-1 text-left text-xs text-foreground hover:bg-accent"
                    >
                      {t(`example.${key}`)}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {messages.map((m) => (
          <div key={m.id} className={cn('flex', m.role === 'user' ? 'justify-end' : 'justify-start')}>
            {m.role === 'user' ? (
              <div className="max-w-[85%] whitespace-pre-wrap break-words rounded-2xl rounded-br-sm bg-primary px-3 py-2 text-sm text-primary-foreground">
                {m.content}
              </div>
            ) : (
              <div className="w-full max-w-[95%] space-y-2">
                <div
                  className={cn(
                    'rounded-2xl rounded-bl-sm px-3 py-2',
                    m.error ? 'border border-danger/40 bg-danger-subtle text-danger-text' : 'bg-muted text-foreground',
                  )}
                >
                  {m.error ? <p className="text-sm">{m.content}</p> : (
                    <AssistantMarkdown text={m.content} onNavigate={goTo} citations={new Set((m.sources ?? []).map((s) => s.n))} />
                  )}
                </div>
                {!!m.sources?.length && (
                  <div>
                    <button
                      type="button"
                      onClick={() => setExpandedSources(expandedSources === m.id ? null : m.id)}
                      className="inline-flex items-center gap-1 text-xs font-medium text-muted-foreground hover:text-foreground"
                      aria-expanded={expandedSources === m.id}
                    >
                      <BookOpen className="size-3.5" aria-hidden />
                      {t('sources', { count: m.sources.length })}
                      <ChevronDown className={cn('size-3.5 transition-transform', expandedSources === m.id && 'rotate-180')} aria-hidden />
                    </button>
                    {expandedSources === m.id && (
                      <ul className="mt-1.5 space-y-1.5">
                        {m.sources.map((s) => (
                          <li key={s.n} className="rounded-md border border-border bg-background px-2 py-1.5 text-xs">
                            <p className="font-medium text-foreground">
                              <span className="mr-1 rounded bg-primary-subtle px-1 font-semibold text-primary-subtle-foreground">{s.n}</span>
                              {s.title}{s.heading ? ` — ${s.heading}` : ''}
                              <span className="ml-1 text-muted-foreground">({t(s.source === 'guide' ? 'sourceGuide' : 'sourceUpload')})</span>
                            </p>
                            <p className="mt-0.5 line-clamp-3 text-muted-foreground">{s.excerpt}</p>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                )}
                {m.proposals?.map((p) => (
                  <ProposalCard
                    key={p.id}
                    proposal={p}
                    state={m.proposalStates?.[p.id] ?? { state: 'pending' }}
                    onCreate={() => void createFromProposal(m.id, p)}
                    onDismiss={() => setProposalState(m.id, p.id, { state: 'dismissed' })}
                    onOpen={goTo}
                  />
                ))}
              </div>
            )}
          </div>
        ))}

        {pending && (
          <div className="inline-flex items-center gap-2 rounded-2xl rounded-bl-sm bg-muted px-3 py-2 text-sm text-muted-foreground">
            <Loader2 className="size-4 animate-spin" aria-hidden /> {t('thinking')}
          </div>
        )}
        {messages.length > 0 && items.length > 0 && !pending && (
          <details className="rounded-md border border-border bg-background px-3 py-2">
            <summary className="cursor-pointer text-xs font-semibold text-muted-foreground">{t('nextStepsCount', { count: items.length })}</summary>
            <div className="mt-2"><NextSteps items={items} loading={false} onOpen={goTo} compact /></div>
          </details>
        )}
        <div ref={endRef} />
      </div>

      <form
        className="flex items-end gap-2 border-t border-border bg-background px-3 py-2.5"
        onSubmit={(e) => { e.preventDefault(); void send(input); }}
      >
        <textarea
          ref={inputRef}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKeyDown}
          rows={Math.min(5, Math.max(1, input.split('\n').length))}
          maxLength={4000}
          disabled={!configured}
          placeholder={configured ? t('placeholder') : t('placeholderDisabled')}
          aria-label={t('placeholder')}
          className="max-h-40 min-h-9 flex-1 resize-none rounded-md border border-border bg-card px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-60"
        />
        <Button type="submit" size="icon" disabled={!configured || pending || !input.trim()} aria-label={t('send')}>
          {pending ? <Loader2 className="size-4 animate-spin" aria-hidden /> : <SendHorizontal className="size-4" aria-hidden />}
        </Button>
      </form>
    </section>
  );
}

function NextSteps({ items, loading, onOpen, compact = false }: {
  items: Suggestion[];
  loading: boolean;
  onOpen: (path: string) => void;
  compact?: boolean;
}) {
  const { t } = useTranslation('assistant');
  if (loading) {
    return <p className="text-xs text-muted-foreground">{t('loadingNextSteps')}</p>;
  }
  return (
    <div>
      {!compact && <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t('nextSteps')}</p>}
      {items.length === 0 ? (
        <p className="text-xs text-muted-foreground">{t('noNextSteps')}</p>
      ) : (
        <ul className="space-y-1.5">
          {items.map((s) => (
            <li key={s.key}>
              <button
                type="button"
                onClick={() => onOpen(s.link)}
                className="group flex w-full items-start gap-2 rounded-md border border-border bg-background px-2.5 py-2 text-left text-xs hover:border-primary/40 hover:bg-primary-subtle"
              >
                <span className="mt-0.5 inline-flex min-w-5 justify-center rounded bg-primary-subtle px-1 font-semibold text-primary-subtle-foreground">
                  {s.count}
                </span>
                <span className="flex-1 text-foreground">
                  {t(`suggestions.${s.key}`, { count: s.count, amount: s.amount != null ? formatRupiah(s.amount) : '', defaultValue: s.title })}
                </span>
                <ArrowRight className="mt-0.5 size-3.5 shrink-0 text-muted-foreground group-hover:text-primary" aria-hidden />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
