import React, { useRef, useState } from 'react';
import { InputText } from 'primereact/inputtext';
import { Button } from 'primereact/button';

interface ChemicalFormulaInputProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
}

// Unicode subscript & superscript character maps
const SUBSCRIPT_CHARS: Record<string, string> = {
  '0': '₀', '1': '₁', '2': '₂', '3': '₃', '4': '₄',
  '5': '₅', '6': '₆', '7': '₇', '8': '₈', '9': '₉',
  '+': '₊', '-': '₋', '(': '₍', ')': '₎',
  'a': 'ₐ', 'e': 'ₑ', 'o': 'ₒ', 'x': 'ₓ', 'n': 'ₙ',
};

const SUPERSCRIPT_CHARS: Record<string, string> = {
  '0': '⁰', '1': '¹', '2': '²', '3': '³', '4': '⁴',
  '5': '⁵', '6': '⁶', '7': '⁷', '8': '⁸', '9': '⁹',
  '+': '⁺', '-': '⁻', '(': '⁽', ')': '⁾', 'n': 'ⁿ',
};

const SPECIAL_CHARS = [
  { label: 'µ', char: 'µ' },
  { label: '°', char: '°' },
  { label: '‰', char: '‰' },
  { label: '→', char: '→' },
  { label: '⇌', char: '⇌' },
  { label: '·', char: '·' },
  { label: 'Δ', char: 'Δ' },
];

const QUICK_FORMULAS = [
  { label: 'H₂O', char: 'H₂O' },
  { label: 'CO₂', char: 'CO₂' },
  { label: 'N₂', char: 'N₂' },
  { label: 'O₂', char: 'O₂' },
  { label: 'SO₂', char: 'SO₂' },
  { label: 'NO₂', char: 'NO₂' },
  { label: 'CH₄', char: 'CH₄' },
  { label: 'NH₃', char: 'NH₃' },
  { label: 'H₂S', char: 'H₂S' },
  { label: 'mg/L', char: 'mg/L' },
  { label: 'µg/L', char: 'µg/L' },
];

type PanelType = 'subscript' | 'superscript' | 'special' | 'quick' | null;

const ChemicalFormulaInput: React.FC<ChemicalFormulaInputProps> = ({
  value,
  onChange,
  placeholder = 'Masukkan formula...',
  className,
}) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const insertAtCursor = (text: string) => {
    const input = inputRef.current;
    if (!input) {
      onChange(value + text);
      return;
    }
    const start = input.selectionStart ?? value.length;
    const end = input.selectionEnd ?? value.length;
    const newValue = value.slice(0, start) + text + value.slice(end);
    onChange(newValue);
    // Restore cursor position after React re-render
    requestAnimationFrame(() => {
      input.focus();
      const pos = start + text.length;
      input.setSelectionRange(pos, pos);
    });
  };

  const [activePanel, setActivePanel] = useState<PanelType>(null);

  const handleToggle = (panel: PanelType) => {
    setActivePanel((prev) => (prev === panel ? null : panel));
  };

  return (
    <div className={className}>
      {/* Input field */}
      <InputText
        ref={inputRef}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full mb-2"
        style={{ fontSize: '1.1rem' }}
      />

      {/* Toolbar buttons */}
      <div className="flex flex-wrap gap-1 mb-1">
        <Button
          type="button"
          label="Subscript ₂"
          size="small"
          severity={activePanel === 'subscript' ? 'info' : 'secondary'}
          outlined={activePanel !== 'subscript'}
          onClick={() => handleToggle('subscript')}
          className="text-xs"
          style={{ padding: '0.25rem 0.5rem' }}
        />
        <Button
          type="button"
          label="Superscript ²"
          size="small"
          severity={activePanel === 'superscript' ? 'info' : 'secondary'}
          outlined={activePanel !== 'superscript'}
          onClick={() => handleToggle('superscript')}
          className="text-xs"
          style={{ padding: '0.25rem 0.5rem' }}
        />
        <Button
          type="button"
          label="Simbol"
          icon="pi pi-star"
          size="small"
          severity={activePanel === 'special' ? 'info' : 'secondary'}
          outlined={activePanel !== 'special'}
          onClick={() => handleToggle('special')}
          className="text-xs"
          style={{ padding: '0.25rem 0.5rem' }}
        />
        <Button
          type="button"
          label="Rumus Cepat"
          icon="pi pi-bolt"
          size="small"
          severity={activePanel === 'quick' ? 'info' : 'secondary'}
          outlined={activePanel !== 'quick'}
          onClick={() => handleToggle('quick')}
          className="text-xs"
          style={{ padding: '0.25rem 0.5rem' }}
        />
      </div>

      {/* Character panels */}
      {activePanel === 'subscript' && (
        <div className="flex flex-wrap gap-1 p-2 border-round surface-50 border-1 surface-border">
          {Object.entries(SUBSCRIPT_CHARS).map(([key, char]) => (
            <Button
              key={key}
              type="button"
              label={char}
              size="small"
              text
              onClick={() => insertAtCursor(char)}
              style={{ minWidth: '2.2rem', padding: '0.25rem', fontSize: '1rem' }}
            />
          ))}
        </div>
      )}

      {activePanel === 'superscript' && (
        <div className="flex flex-wrap gap-1 p-2 border-round surface-50 border-1 surface-border">
          {Object.entries(SUPERSCRIPT_CHARS).map(([key, char]) => (
            <Button
              key={key}
              type="button"
              label={char}
              size="small"
              text
              onClick={() => insertAtCursor(char)}
              style={{ minWidth: '2.2rem', padding: '0.25rem', fontSize: '1rem' }}
            />
          ))}
        </div>
      )}

      {activePanel === 'special' && (
        <div className="flex flex-wrap gap-1 p-2 border-round surface-50 border-1 surface-border">
          {SPECIAL_CHARS.map((s) => (
            <Button
              key={s.label}
              type="button"
              label={s.label}
              size="small"
              text
              onClick={() => insertAtCursor(s.char)}
              style={{ minWidth: '2.2rem', padding: '0.25rem', fontSize: '1rem' }}
            />
          ))}
        </div>
      )}

      {activePanel === 'quick' && (
        <div className="flex flex-wrap gap-1 p-2 border-round surface-50 border-1 surface-border">
          {QUICK_FORMULAS.map((f) => (
            <Button
              key={f.label}
              type="button"
              label={f.label}
              size="small"
              outlined
              severity="secondary"
              onClick={() => insertAtCursor(f.char)}
              style={{ padding: '0.25rem 0.5rem', fontSize: '0.85rem' }}
            />
          ))}
        </div>
      )}

      {/* Preview */}
      {value && (
        <div className="mt-2 p-2 border-round surface-100 border-1 surface-border">
          <span className="text-xs text-500">Preview: </span>
          <span style={{ fontSize: '1.1rem' }}>{value}</span>
        </div>
      )}
    </div>
  );
};

export default ChemicalFormulaInput;
