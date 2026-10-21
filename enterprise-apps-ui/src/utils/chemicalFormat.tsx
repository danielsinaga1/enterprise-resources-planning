import React from 'react';

/**
 * Fix common UTF-8 → Latin-1 mojibake (garbled encoding).
 */
const MOJIBAKE: [string, string][] = [
  ['ÔÇö', '\u2014'],  // — em dash
  ['ÔÇô', '\u2013'],  // – en dash
  ['ÔÇ£', '\u201C'],  // " left double quote
  ['ÔÇØ', '\u201D'],  // " right double quote
  ['ÔÇÿ', '\u2019'],  // ' right single quote
  ['ÔÇÖ', '\u2018'],  // ' left single quote
  ['ÔÇ¬', '\u2026'],  // … ellipsis
  ['Ã©', 'é'],
  ['Ã¨', 'è'],
  ['Ã¶', 'ö'],
  ['Ã¼', 'ü'],
  ['Ã¤', 'ä'],
];

export function fixEncoding(text: string): string {
  let r = text;
  for (const [bad, good] of MOJIBAKE) {
    r = r.split(bad).join(good);
  }
  return r;
}

/**
 * Fix encoding for plain-text contexts (PDF export).
 * Chemical formulas stay as-is (PO4, SiO2) since jsPDF default
 * fonts don't support Unicode subscript characters.
 */
export function cleanTextForPdf(text: string): string {
  return fixEncoding(text);
}

/**
 * React component that renders text with:
 * - Fixed mojibake encoding
 * - Chemical subscripts (e.g. PO4 → PO₄, SiO2 → SiO₂, H2SO4 → H₂SO₄)
 * - Charge superscripts (e.g. Cl- → Cl⁻, Fe3+ → Fe³⁺, SO4^2- → SO₄²⁻)
 */
export function ChemText({ text }: { text: string }): React.ReactElement {
  const fixed = fixEncoding(text);

  // Tokenise by chemical-formula-like patterns:
  //  Group A – element + digits (subscript):  [A-Z][a-z]?\d+
  //  Group B – trailing charge:               \d*[+\-]  (after letter or digit)
  // We combine into one regex that captures in order.
  const TOKEN =
    /([A-Z][a-z]?)(\d+)(\d*[+\-](?=[\s,;)\].]|$))?|([A-Za-z\d])(\d*[+\-])(?=[\s,;)\].]|$)/g;

  const parts: React.ReactNode[] = [];
  let lastIdx = 0;
  let key = 0;
  let m: RegExpExecArray | null;

  while ((m = TOKEN.exec(fixed)) !== null) {
    // text before match
    if (m.index > lastIdx) {
      parts.push(fixed.slice(lastIdx, m.index));
    }

    if (m[1] !== undefined) {
      // Group A matched: element + subscript digits (+ optional charge)
      parts.push(
        <React.Fragment key={key++}>
          {m[1]}
          <sub>{m[2]}</sub>
          {m[3] && <sup>{m[3]}</sup>}
        </React.Fragment>,
      );
    } else if (m[4] !== undefined) {
      // Group B matched: standalone charge after a letter/digit (e.g. Cl-, Na+)
      parts.push(
        <React.Fragment key={key++}>
          {m[4]}
          <sup>{m[5]}</sup>
        </React.Fragment>,
      );
    }
    lastIdx = TOKEN.lastIndex;
  }

  // remaining text
  if (lastIdx < fixed.length) {
    parts.push(fixed.slice(lastIdx));
  }

  return <>{parts}</>;
}
