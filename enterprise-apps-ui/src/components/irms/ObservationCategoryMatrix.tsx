import { useState } from 'react';

/* ============================================================
   OBSERVATION CATEGORY MATRIX
   Visual matrix table for selecting observation categories.
   Fully self-contained — no backend dependency.
   Based on "Item yang dilaporkan" reference table 2022:
   K3 | PS | LH | HS | HK | QA | OT

   IDs are fixed (1–7) and must match the root_causes table.
   Run the companion SQL migration to seed these categories:
     enterprise-apps-services/sql/seed_observation_categories.sql
   ============================================================ */

interface ObservationCategory {
  /** Fixed numeric ID — must match root_causes table row */
  id: number;
  code: string;
  name: string;
  color: string;
  bgColor: string;
  borderColor: string;
  icon: string;
  examples: string[];
}

const OBSERVATION_CATEGORIES: ObservationCategory[] = [
  {
    id: 1,
    code: 'K3',
    name: 'Keselamatan dan Kesehatan Kerja',
    color: '#b91c1c',
    bgColor: '#fef2f2',
    borderColor: '#fca5a5',
    icon: 'pi pi-shield',
    examples: [
      'Tidak memakai APD yang sesuai jenis dan lokasi pekerjaan',
      'Membawa HP di area produksi',
      'Melintasi safety line saat ada pekerjaan di atas',
      'Kondisi Kendaraan yang tidak laik',
      'Kontraktor bekerja tidak dilengkapi dengan JSA',
    ],
  },
  {
    id: 2,
    code: 'PS',
    name: 'Keselamatan Proses',
    color: '#c2410c',
    bgColor: '#fff7ed',
    borderColor: '#fdba74',
    icon: 'pi pi-cog',
    examples: [
      'Pekerjaan panas tidak ada APAR yang standby',
      'Pekerjaan melibatkan energi (listrik, fluida, dll) tidak menggunakan LOTO',
      'Perubahan proses/set point tidak dilengkapi dengan form MOC',
    ],
  },
  {
    id: 3,
    code: 'LH',
    name: 'Lingkungan Hidup',
    color: '#15803d',
    bgColor: '#f0fdf4',
    borderColor: '#86efac',
    icon: 'pi pi-globe',
    examples: [
      'Tumpahan bahan kimia di luar dike yang telah ditentukan',
      'Majun tidak dibuang di tempat sampah bertanda B3',
      'Sampah menumpuk melebihi kapasitas tempat sampah',
      'Kemasan sisa bahan kimia tidak diletakan di TPS B3',
    ],
  },
  {
    id: 4,
    code: 'HS',
    name: 'Hygiene Sanitasi',
    color: '#0369a1',
    bgColor: '#f0f9ff',
    borderColor: '#7dd3fc',
    icon: 'pi pi-heart',
    examples: [
      'Kondisi toilet yang kurang resik',
      'Kondisi pantry yang kurang terawat',
      'Tempat sampah di kantor yang tidak tertutup',
    ],
  },
  {
    id: 5,
    code: 'HK',
    name: 'Housekeeping',
    color: '#7e22ce',
    bgColor: '#faf5ff',
    borderColor: '#d8b4fe',
    icon: 'pi pi-home',
    examples: [
      'Kertas dan dokumen berserakan di lokasi kerja',
      'Genangan air di lantai kantor maupun pabrik',
      'Helm tidak diletakan sebagaimana mestinya',
      'Sisa pekerjaan yang tidak dirapikan kembali',
      'Kabel berserakan',
    ],
  },
  {
    id: 6,
    code: 'QA',
    name: 'Quality',
    color: '#b45309',
    bgColor: '#fffbeb',
    borderColor: '#fcd34d',
    icon: 'pi pi-check-square',
    examples: [
      'Sampling point dalam kondisi terkorosi',
      'Indikator di lapangan yang tidak akurat atau rusak',
    ],
  },
  {
    id: 7,
    code: 'OT',
    name: 'Others',
    color: '#475569',
    bgColor: '#f8fafc',
    borderColor: '#cbd5e1',
    icon: 'pi pi-ellipsis-h',
    examples: [
      'Lampu pecah',
      'Isolasi terbuka',
      'Gedung',
      'Shelter',
    ],
  },
];

interface Props {
  /** The currently selected category ID (matches root_causes.id) */
  selectedCategoryId?: number;
  /** Called with the category ID when user clicks a row, or 0 to clear */
  onSelect: (categoryId: number) => void;
}

const ObservationCategoryMatrix = ({ selectedCategoryId, onSelect }: Props) => {
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const selected = OBSERVATION_CATEGORIES.find((c) => c.id === selectedCategoryId);

  return (
    <div className="flex flex-column gap-2">
      {/* Header */}
      <div className="flex align-items-center gap-2 mb-1">
        <i className="pi pi-th-large text-indigo-500" />
        <span className="text-sm font-bold text-700">Pilih Kategori Observasi</span>
        {selectedCategoryId != null && selectedCategoryId > 0 && (
          <span
            className="text-xs px-2 py-1 border-round-lg font-semibold cursor-pointer"
            style={{ background: '#fee2e2', color: '#dc2626' }}
            onClick={() => onSelect(0)}
          >
            <i className="pi pi-times text-xs mr-1" />Reset
          </span>
        )}
      </div>

      {/* Matrix Table */}
      <div className="border-round-xl overflow-hidden shadow-1" style={{ border: '1px solid #e2e8f0' }}>
        {/* Table Header */}
        <div
          className="grid m-0 px-3 py-2"
          style={{ background: 'linear-gradient(90deg, #1e293b, #334155)', color: '#fff' }}
        >
          <div className="col-1 p-0 text-xs font-bold">No</div>
          <div className="col-3 p-0 text-xs font-bold">Kategori</div>
          <div className="col-1 p-0 text-xs font-bold text-center">Kode</div>
          <div className="col-7 p-0 text-xs font-bold">Contoh Item yang Dilaporkan</div>
        </div>

        {/* Rows */}
        {OBSERVATION_CATEGORIES.map((cat, idx) => {
          const isSelected = cat.id === selectedCategoryId;
          const isExpanded = expandedId === cat.id;

          return (
            <div
              key={cat.id}
              className="grid m-0 px-3 py-2 align-items-start cursor-pointer"
              style={{
                background: isSelected ? cat.bgColor : idx % 2 === 0 ? '#ffffff' : '#f8fafc',
                borderLeft: isSelected ? `4px solid ${cat.color}` : '4px solid transparent',
                borderBottom: '1px solid #f1f5f9',
                transition: 'background 0.15s',
              }}
              onClick={() => onSelect(cat.id)}
              onMouseEnter={(e) => {
                if (!isSelected) (e.currentTarget as HTMLDivElement).style.background = cat.bgColor;
              }}
              onMouseLeave={(e) => {
                if (!isSelected) (e.currentTarget as HTMLDivElement).style.background = idx % 2 === 0 ? '#ffffff' : '#f8fafc';
              }}
            >
              {/* No */}
              <div className="col-1 p-0 pt-1">
                <span className="text-sm font-semibold text-600">{idx + 1}</span>
              </div>

              {/* Kategori */}
              <div className="col-3 p-0 pt-1 flex align-items-center gap-1">
                {isSelected
                  ? <i className="pi pi-check-circle text-sm flex-shrink-0" style={{ color: cat.color }} />
                  : <i className={`${cat.icon} text-sm flex-shrink-0`} style={{ color: cat.color }} />
                }
                <span className="text-xs font-semibold" style={{ color: cat.color, lineHeight: 1.3 }}>
                  {cat.name}
                </span>
              </div>

              {/* Kode */}
              <div className="col-1 p-0 pt-1 flex justify-content-center">
                <span
                  className="text-xs font-bold px-2 py-1 border-round-lg text-center"
                  style={{
                    background: isSelected ? cat.color : cat.bgColor,
                    color: isSelected ? '#fff' : cat.color,
                    border: `1px solid ${cat.borderColor}`,
                    minWidth: 34,
                    display: 'inline-block',
                  }}
                >
                  {cat.code}
                </span>
              </div>

              {/* Contoh */}
              <div className="col-7 p-0 flex align-items-start justify-content-between gap-2">
                <div className="flex flex-column gap-1 flex-1">
                  {cat.examples.slice(0, isExpanded ? undefined : 2).map((ex, i) => (
                    <span key={i} className="text-xs text-600 flex align-items-start gap-1">
                      <span style={{ color: cat.borderColor, fontSize: 10, marginTop: 3 }}>●</span>
                      {ex}
                    </span>
                  ))}
                  {!isExpanded && cat.examples.length > 2 && (
                    <span
                      className="text-xs font-semibold"
                      style={{ color: cat.color }}
                      onClick={(e) => { e.stopPropagation(); setExpandedId(cat.id); }}
                    >
                      +{cat.examples.length - 2} contoh lainnya...
                    </span>
                  )}
                  {isExpanded && (
                    <span
                      className="text-xs font-semibold"
                      style={{ color: cat.color }}
                      onClick={(e) => { e.stopPropagation(); setExpandedId(null); }}
                    >
                      Tutup
                    </span>
                  )}
                </div>

                {/* Radio-style indicator */}
                <div
                  className="flex align-items-center justify-content-center flex-shrink-0 mt-1"
                  style={{
                    width: 22,
                    height: 22,
                    borderRadius: '50%',
                    border: isSelected ? `2px solid ${cat.color}` : '2px solid #d1d5db',
                    background: isSelected ? cat.color : 'transparent',
                    transition: 'all 0.2s',
                  }}
                >
                  {isSelected && <i className="pi pi-check" style={{ fontSize: 10, color: '#fff' }} />}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Selected summary */}
      {selected && (
        <div
          className="flex align-items-center gap-2 px-3 py-2 border-round-lg"
          style={{ background: selected.bgColor, border: `1px solid ${selected.borderColor}` }}
        >
          <i className={`${selected.icon} text-sm`} style={{ color: selected.color }} />
          <span className="text-xs font-bold" style={{ color: selected.color }}>
            Kategori terpilih: <span className="mr-1">{selected.code}</span>— {selected.name}
          </span>
        </div>
      )}
    </div>
  );
};

export default ObservationCategoryMatrix;
