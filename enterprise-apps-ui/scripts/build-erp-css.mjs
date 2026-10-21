#!/usr/bin/env node
/**
 * Build gaya modul ERP: Tailwind CSS v4 → CSS ber-scope `.erp-root`.
 *
 * Aplikasi ini memakai Tailwind 3 + PrimeFlex secara global, sedangkan halaman ERP (port klien
 * ENTARIS) ditulis untuk Tailwind 4 dengan token tema sendiri. Agar keduanya hidup berdampingan:
 *   1. src/erp/styles/erp.tw.css dikompilasi Tailwind v4 CLI (paket alias `tailwindcss4`),
 *      memindai hanya src/erp (lihat `source('../')`).
 *   2. Hasilnya diproses PostCSS: blok `@layer` dibuka (urutan tetap) dan setiap selector diberi
 *      prefix `.erp-root` — preflight, utilitas, dan token tema hanya berlaku di dalam wrapper ERP
 *      (dan portal dialog/popover yang diarahkan ke wrapper itu), tidak bocor ke IRMS/LIMS.
 *      Spesifisitas +1 kelas juga membuat utilitas ERP menang atas PrimeFlex (`.grid`, `.w-4`, `.p-4`).
 *
 * Pemakaian: `node scripts/build-erp-css.mjs` (otomatis lewat predev/prebuild). `--watch` untuk dev.
 */
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, watch, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import postcss from 'postcss';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const INPUT = join(ROOT, 'src/erp/styles/erp.tw.css');
const OUTPUT = join(ROOT, 'src/erp/styles/erp.generated.css');
const SCOPE = '.erp-root';
const require = createRequire(import.meta.url);

function tailwindCli() {
  const pkg = require.resolve('@tailwindcss/cli/package.json');
  const meta = JSON.parse(readFileSync(pkg, 'utf8'));
  const bin = typeof meta.bin === 'string' ? meta.bin : meta.bin.tailwindcss;
  return join(dirname(pkg), bin);
}

function compileTailwind() {
  const dir = mkdtempSync(join(tmpdir(), 'erp-css-'));
  const out = join(dir, 'raw.css');
  try {
    execFileSync(process.execPath, [tailwindCli(), '-i', INPUT, '-o', out, '--minify'], {
      cwd: ROOT,
      stdio: ['ignore', 'ignore', 'inherit'],
    });
    return readFileSync(out, 'utf8');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

/** Satu selector → bentuk ber-scope. */
function scopeSelector(sel) {
  const s = sel.trim();
  if (!s) return s;
  if (s === ':root' || s === ':host' || s === 'html' || s === 'body' || s === ':root,:host') return SCOPE;
  // Tema: .dark/.light dipasang ThemeContext di <html> → token berlaku untuk wrapper di bawahnya.
  if (s === '.dark') return `.dark ${SCOPE},${SCOPE} .dark,${SCOPE}.dark`;
  if (s === '.light') return `.light ${SCOPE},${SCOPE} .light,${SCOPE}.light`;
  // :root[data-motion=…] .x — atribut tetap di <html>, sisanya di dalam wrapper.
  const rootAttr = /^:root((?:\[[^\]]+\]|\.[\w-]+)+)\s+(.*)$/.exec(s);
  if (rootAttr) return `:root${rootAttr[1]} ${SCOPE} ${rootAttr[2]}`;
  if (/^:root((?:\[[^\]]+\])+)$/.test(s)) return `${s} ${SCOPE}`;
  if (s.startsWith('html ') || s.startsWith('body ')) return `${SCOPE} ${s.slice(5)}`;
  // Universal (preflight): sertakan wrapper itu sendiri.
  if (s === '*') return `${SCOPE},${SCOPE} *`;
  if (s.startsWith('::')) return `${SCOPE}${s},${SCOPE} ${s}`;
  return `${SCOPE} ${s}`;
}

const scopePlugin = {
  postcssPlugin: 'erp-scope',
  Once(root) {
    // 1) Buka @layer (urutan tetap: properties, theme, base, components, utilities).
    root.walkAtRules('layer', (at) => {
      if (!at.nodes) at.remove();
      else at.replaceWith(at.nodes);
    });
    // 2) Prefix selector, kecuali isi @keyframes.
    root.walkRules((rule) => {
      let p = rule.parent;
      while (p) {
        if (p.type === 'atrule' && /keyframes$/i.test(p.name)) return;
        p = p.parent;
      }
      rule.selectors = [...new Set(rule.selectors.flatMap((sel) => scopeSelector(sel).split(',')))];
    });
  },
};

async function build() {
  const raw = compileTailwind();
  const result = await postcss([scopePlugin]).process(raw, { from: undefined });
  const banner = '/* Dibangkitkan scripts/build-erp-css.mjs dari src/erp/styles/erp.tw.css — jangan diedit manual. */\n';
  writeFileSync(OUTPUT, banner + result.css);
  console.log(`[erp-css] ${OUTPUT.replace(ROOT, '.')} (${(result.css.length / 1024).toFixed(0)} KB)`);
}

await build();

if (process.argv.includes('--watch')) {
  let timer = null;
  const dir = join(ROOT, 'src/erp');
  console.log('[erp-css] memantau perubahan src/erp …');
  watch(dir, { recursive: true }, (_event, file) => {
    if (!file || file.endsWith('erp.generated.css')) return;
    clearTimeout(timer);
    timer = setTimeout(() => build().catch((e) => console.error('[erp-css]', e.message)), 300);
  });
}

