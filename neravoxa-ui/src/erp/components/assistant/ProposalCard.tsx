/** Kartu usulan input data dari asisten: belum tersimpan sampai pengguna menekan Buat. */
import { CheckCircle2, FilePlus2, Loader2, XCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { formatRupiah } from '@/lib/format';
import type { Proposal, ProposalState } from '@/lib/assistant';

const PREVIEW_ROWS = 6;
const MONEY_COLUMNS = new Set(['masuk', 'keluar', 'amount']);

interface Props {
  proposal: Proposal;
  state: ProposalState;
  onCreate: () => void;
  onDismiss: () => void;
  onOpen: (path: string) => void;
}

export function ProposalCard({ proposal, state, onCreate, onDismiss, onOpen }: Props) {
  const { t } = useTranslation('assistant');
  const rows = proposal.table?.rows ?? [];
  const busy = state.state === 'creating';
  return (
    <div className="rounded-lg border border-primary/30 bg-card shadow-[var(--shadow-1)]">
      <div className="flex items-start gap-2 border-b border-border px-3 py-2">
        <FilePlus2 className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden />
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-foreground">{proposal.title}</p>
          <p className="text-xs text-muted-foreground">{t(`proposal.kinds.${proposal.kind}`)}</p>
        </div>
      </div>
      <div className="space-y-2 px-3 py-2">
        <ul className="space-y-0.5 text-xs text-muted-foreground">
          {proposal.summary.map((line, i) => <li key={i}>{line}</li>)}
        </ul>
        {proposal.table && rows.length > 0 && (
          <div className="overflow-x-auto rounded-md border border-border">
            <table className="w-full text-xs">
              <thead className="bg-muted/60">
                <tr>
                  {proposal.table.columns.map((c) => (
                    <th key={c} className="px-2 py-1 text-left font-medium text-muted-foreground">{t(`proposal.columns.${c}`, c)}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.slice(0, PREVIEW_ROWS).map((r, i) => (
                  <tr key={i} className="border-t border-border">
                    {r.map((cell, j) => (
                      <td key={j} className={MONEY_COLUMNS.has(proposal.table!.columns[j]) ? 'num px-2 py-1 text-right' : 'px-2 py-1'}>
                        {cell == null || cell === '' ? '' : MONEY_COLUMNS.has(proposal.table!.columns[j]) ? formatRupiah(cell) : String(cell)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
            {rows.length > PREVIEW_ROWS && (
              <p className="border-t border-border px-2 py-1 text-xs text-muted-foreground">
                {t('proposal.moreRows', { count: rows.length - PREVIEW_ROWS })}
              </p>
            )}
          </div>
        )}
      </div>
      <div className="flex flex-wrap items-center justify-end gap-2 border-t border-border px-3 py-2">
        {state.state === 'created' ? (
          <>
            <span className="mr-auto inline-flex items-center gap-1 text-xs font-medium text-success-text">
              <CheckCircle2 className="size-3.5" aria-hidden /> {t('proposal.created')}
            </span>
            <Button size="sm" onClick={() => onOpen(state.link)}>{t('proposal.open')}</Button>
          </>
        ) : state.state === 'dismissed' ? (
          <span className="mr-auto text-xs text-muted-foreground">{t('proposal.dismissed')}</span>
        ) : (
          <>
            {state.state === 'failed' ? (
              <span className="mr-auto inline-flex items-start gap-1 text-xs text-danger-text">
                <XCircle className="mt-0.5 size-3.5 shrink-0" aria-hidden /> {state.error}
              </span>
            ) : (
              <span className="mr-auto text-xs text-muted-foreground">{t('proposal.notSaved')}</span>
            )}
            <Button size="sm" variant="ghost" onClick={onDismiss} disabled={busy}>{t('proposal.dismiss')}</Button>
            <Button size="sm" onClick={onCreate} disabled={busy}>
              {busy && <Loader2 className="size-3.5 animate-spin" aria-hidden />}
              {busy ? t('proposal.creating') : state.state === 'failed' ? t('proposal.retry') : t(`proposal.create.${proposal.kind}`)}
            </Button>
          </>
        )}
      </div>
    </div>
  );
}
