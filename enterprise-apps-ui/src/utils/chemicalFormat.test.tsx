import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { ChemText, cleanTextForPdf, fixEncoding } from './chemicalFormat';

describe('fixEncoding', () => {
  it('memperbaiki mojibake UTF-8 → Latin-1', () => {
    // Muncul saat data lama diimpor dengan encoding salah; kalau tidak dibersihkan,
    // hasil ekspor PDF/Excel ikut membawa karakter rusak.
    expect(fixEncoding('Kadar ÔÇö tinggi')).toBe('Kadar — tinggi');
    expect(fixEncoding('CafÃ©')).toBe('Café');
  });

  it('membiarkan teks yang sudah bersih apa adanya', () => {
    expect(fixEncoding('Kadar normal')).toBe('Kadar normal');
  });

  it('cleanTextForPdf membersihkan encoding tanpa mengubah rumus kimia', () => {
    // jsPDF memakai font tanpa dukungan subscript Unicode, jadi PO4 harus tetap PO4.
    expect(cleanTextForPdf('PO4 ÔÇö batas')).toBe('PO4 — batas');
  });
});

describe('ChemText', () => {
  it('merender angka setelah unsur sebagai subscript', () => {
    const { container } = render(<ChemText text="H2SO4" />);

    expect(container.querySelectorAll('sub')).toHaveLength(2);
    // Teks yang bisa disalin harus tetap terbaca sebagai rumus aslinya.
    expect(container.textContent).toBe('H2SO4');
  });

  it('merender muatan ion sebagai superscript', () => {
    const { container } = render(<ChemText text="Cl-" />);

    expect(container.querySelectorAll('sup')).toHaveLength(1);
    expect(container.textContent).toBe('Cl-');
  });

  it('membiarkan teks biasa tanpa markup', () => {
    const { container } = render(<ChemText text="Hasil uji normal" />);

    expect(container.querySelectorAll('sub, sup')).toHaveLength(0);
    expect(container.textContent).toBe('Hasil uji normal');
  });

  it('memperbaiki mojibake sebelum merender', () => {
    const { container } = render(<ChemText text="SiO2 ÔÇö tinggi" />);

    expect(container.textContent).toBe('SiO2 — tinggi');
  });

  it('menangani teks kosong tanpa error', () => {
    const { container } = render(<ChemText text="" />);

    expect(container.textContent).toBe('');
  });
});
