/**
 * Markdown ringkas untuk jawaban asisten: paragraf, daftar (•/1.), judul, tabel pipa sederhana,
 * **tebal**, `kode`, tautan, dan sitasi [n]. Tidak pernah merender HTML mentah.
 *
 * Tautan internal (/path) hanya bisa diklik bila cocok dengan rute ERP yang boleh dibuka
 * pengguna; tautan lain ditampilkan sebagai teks biasa atau dibuka di tab baru (http/https).
 */
import { Fragment, type ReactNode } from 'react';
import { matchRoute } from '@/app/routes';
import { can } from '@/lib/permissions';
import { cn } from '@/lib/utils';

interface Props {
  text: string;
  onNavigate: (path: string) => void;
  /** Sitasi [n] yang punya sumber; nomor lain dibiarkan sebagai teks. */
  citations?: Set<number>;
}

type Block =
  | { kind: 'p'; lines: string[] }
  | { kind: 'ul' | 'ol'; items: string[] }
  | { kind: 'h'; text: string }
  | { kind: 'table'; rows: string[][] };

function parseBlocks(text: string): Block[] {
  const blocks: Block[] = [];
  const lines = text.replace(/\r\n/g, '\n').split('\n');
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    const t = line.trim();
    if (!t) {
      i++;
      continue;
    }
    if (/^#{1,4}\s+/.test(t)) {
      blocks.push({ kind: 'h', text: t.replace(/^#{1,4}\s+/, '') });
      i++;
      continue;
    }
    if (t.startsWith('|')) {
      const rows: string[][] = [];
      while (i < lines.length && lines[i].trim().startsWith('|')) {
        const cells = lines[i].trim().replace(/^\||\|$/g, '').split('|').map((c) => c.trim());
        if (!cells.every((c) => /^:?-{2,}:?$/.test(c))) rows.push(cells);
        i++;
      }
      blocks.push({ kind: 'table', rows });
      continue;
    }
    const bullet = /^[-*•]\s+/;
    const numbered = /^\d+[.)]\s+/;
    if (bullet.test(t) || numbered.test(t)) {
      const kind = bullet.test(t) ? 'ul' : 'ol';
      const re = kind === 'ul' ? bullet : numbered;
      const items: string[] = [];
      while (i < lines.length && re.test(lines[i].trim())) {
        let item = lines[i].trim().replace(re, '');
        i++;
        // Baris lanjutan yang menjorok ikut ke butir sebelumnya.
        while (i < lines.length && /^\s{2,}\S/.test(lines[i]) && !re.test(lines[i].trim())) {
          item += ` ${lines[i].trim()}`;
          i++;
        }
        items.push(item);
      }
      blocks.push({ kind, items });
      continue;
    }
    const para: string[] = [];
    while (i < lines.length && lines[i].trim() && !/^(#{1,4}\s|\||[-*•]\s|\d+[.)]\s)/.test(lines[i].trim())) {
      para.push(lines[i].trim());
      i++;
    }
    blocks.push({ kind: 'p', lines: para });
  }
  return blocks;
}

const INLINE = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)\s]+\)|\[\d{1,2}\])/g;

function linkAllowed(href: string): boolean {
  if (!href.startsWith('/') || href.startsWith('//')) return false;
  const entry = matchRoute(href);
  return !!entry && (!entry.module || can(entry.module));
}

function Inline({ text, onNavigate, citations }: Props) {
  const parts = text.split(INLINE).filter((p) => p !== '');
  return (
    <>
      {parts.map((part, idx) => {
        if (part.startsWith('**') && part.endsWith('**')) {
          return <strong key={idx} className="font-semibold text-foreground">{part.slice(2, -2)}</strong>;
        }
        if (part.startsWith('`') && part.endsWith('`')) {
          return <code key={idx} className="rounded bg-muted px-1 py-0.5 font-mono text-[0.85em]">{part.slice(1, -1)}</code>;
        }
        const cite = /^\[(\d{1,2})\]$/.exec(part);
        if (cite) {
          const n = Number(cite[1]);
          return citations?.has(n) ? (
            <sup key={idx} className="mx-0.5 rounded bg-primary-subtle px-1 text-[0.7em] font-semibold text-primary-subtle-foreground">
              {n}
            </sup>
          ) : (
            <Fragment key={idx}>{part}</Fragment>
          );
        }
        const link = /^\[([^\]]+)\]\(([^)\s]+)\)$/.exec(part);
        if (link) {
          const [, label, href] = link;
          if (linkAllowed(href)) {
            return (
              <button
                key={idx}
                type="button"
                onClick={() => onNavigate(href)}
                className="font-medium text-primary underline underline-offset-2 hover:text-primary-hover"
              >
                {label}
              </button>
            );
          }
          if (/^https?:\/\//i.test(href)) {
            return (
              <a key={idx} href={href} target="_blank" rel="noopener noreferrer" className="text-primary underline underline-offset-2">
                {label}
              </a>
            );
          }
          return <Fragment key={idx}>{label}</Fragment>;
        }
        return <Fragment key={idx}>{part}</Fragment>;
      })}
    </>
  );
}

export function AssistantMarkdown({ text, onNavigate, citations }: Props) {
  const blocks = parseBlocks(text);
  const inline = (s: string): ReactNode => <Inline text={s} onNavigate={onNavigate} citations={citations} />;
  return (
    <div className="space-y-2 break-words text-sm leading-relaxed">
      {blocks.map((b, idx) => {
        switch (b.kind) {
          case 'h':
            return <p key={idx} className="font-heading font-semibold text-foreground">{inline(b.text)}</p>;
          case 'ul':
          case 'ol': {
            const List = b.kind === 'ul' ? 'ul' : 'ol';
            return (
              <List key={idx} className={cn('space-y-1 pl-5', b.kind === 'ul' ? 'list-disc' : 'list-decimal')}>
                {b.items.map((item, j) => <li key={j}>{inline(item)}</li>)}
              </List>
            );
          }
          case 'table': {
            const [head, ...rows] = b.rows;
            return (
              <div key={idx} className="overflow-x-auto rounded-md border border-border">
                <table className="w-full text-xs">
                  {head && (
                    <thead className="bg-muted/60">
                      <tr>{head.map((c, j) => <th key={j} className="px-2 py-1.5 text-left font-semibold">{inline(c)}</th>)}</tr>
                    </thead>
                  )}
                  <tbody>
                    {rows.map((r, j) => (
                      <tr key={j} className="border-t border-border">
                        {r.map((c, k) => <td key={k} className="px-2 py-1.5 align-top">{inline(c)}</td>)}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            );
          }
          default:
            return <p key={idx}>{b.lines.map((l, j) => <Fragment key={j}>{j > 0 && <br />}{inline(l)}</Fragment>)}</p>;
        }
      })}
    </div>
  );
}
