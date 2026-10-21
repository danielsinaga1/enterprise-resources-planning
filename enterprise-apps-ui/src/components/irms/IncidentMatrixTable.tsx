import { useState } from 'react';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';

/* ============================================================
   INCIDENT MATRIX TABLE
   Severity × Impact-category reference matrix
   ============================================================ */

interface MatrixRow {
  severity: string;
  severityColor: string;
  manusia: string;
  asset: string[];
  lingkungan: string;
  reputasi: string[];
  securityFisik: string;
  securitySiber: string;
}

const MATRIX_DATA: MatrixRow[] = [
  {
    severity: 'Insignificant',
    severityColor: '#22c55e',
    manusia: 'First aid case (tanpa tindakan medis)',
    asset: ['Pabrik beroperasi normal dengan kondisi gangguan tidak berarti'],
    lingkungan: 'Pencemaran di lingkungan kerja',
    reputasi: ['Reaksi di internal kawasan industri KIE'],
    securityFisik: 'Masuk tanpa izin, tidak ada kehilangan',
    securitySiber: 'Penggunaan hardware yang tidak terdaftar tanpa kehilangan informasi perusahaan',
  },
  {
    severity: 'Minor',
    severityColor: '#eab308',
    manusia: 'First aid case (ada tindakan medis)',
    asset: ['Pabrik beroperasi normal dengan gangguan yang menyebabkan perbaikan di tempat'],
    lingkungan: 'Pencemaran di lingkungan perusahaan',
    reputasi: ['Liputan oleh media lokal (Bontang Post)'],
    securityFisik: 'Masuk tanpa izin, tidak ada kehilangan, dan mengganggu kenyamanan',
    securitySiber: 'System down kurang dari 2 jam',
  },
  {
    severity: 'Moderate',
    severityColor: '#f97316',
    manusia: 'Medical treatment case',
    asset: [
      'Pabrik beroperasi tidak normal dan perlu perbaikan dengan menurunkan rate produksi',
      'Kerugian < USD 50.000',
    ],
    lingkungan: 'Pencemaran ke masyarakat',
    reputasi: [
      'Liputan oleh media regional (Kaltim Post)',
      'Aduan kerusakan oleh masyarakat',
    ],
    securityFisik: 'Masuk tanpa izin, ada kehilangan 1 barang',
    securitySiber: 'System down 2 - 8 jam, Kehilangan data perusahaan secara permanen',
  },
  {
    severity: 'Major',
    severityColor: '#ef4444',
    manusia: 'Lost workday case atau Cacat Permanen',
    asset: [
      'Pabrik shutdown dengan kerusakan yang harus di perbaiki sampai dengan 5 hari',
      'Kerugian USD 50.000 - 100.000',
    ],
    lingkungan: 'Pencemaran ke masyarakat dan menimbulkan protes masyarakat',
    reputasi: [
      'Liputan oleh media Nasional',
      'Tuntutan hukum oleh masyarakat/LSM',
      'PROPER Nasional MERAH',
    ],
    securityFisik: 'Masuk tanpa izin, ada kehilangan lebih dari 1 barang',
    securitySiber: 'System diretas, tanpa diketahui, dan sewaktu-waktu dapat dikendalikan dari luar',
  },
  {
    severity: 'Catastrophic',
    severityColor: '#991b1b',
    manusia: 'Kematian (1 atau lebih orang)',
    asset: [
      'Pabrik shutdown dengan memerlukan perbaikan lebih dari 5 hari',
      'Kerugian > USD 100.000',
    ],
    lingkungan: 'Pencemaran lingkungan yang menimbulkan tuntutan hukum',
    reputasi: [
      'Liputan oleh media Internasional',
      'Investigasi dari Pemerintah/Kepolisian',
    ],
    securityFisik: 'Ancaman bom, pembajakan pabrik, sabotase',
    securitySiber: 'Kelumpuhan infrastruktur IT',
  },
];

const listBodyTemplate = (items: string[]) => {
  if (items.length <= 1) return <span>{items[0]}</span>;
  return (
    <ul className="pl-3 m-0" style={{ listStyleType: 'disc' }}>
      {items.map((item, i) => (
        <li key={i} className="mb-1">{item}</li>
      ))}
    </ul>
  );
};

interface IncidentMatrixTableProps {
  /** Start collapsed (default true) */
  defaultCollapsed?: boolean;
}

const IncidentMatrixTable: React.FC<IncidentMatrixTableProps> = ({ defaultCollapsed = true }) => {
  const [collapsed, setCollapsed] = useState(defaultCollapsed);

  const severityBodyTemplate = (row: MatrixRow) => (
    <span
      className="text-sm font-bold"
      style={{ color: row.severityColor }}
    >
      {row.severity}
    </span>
  );

  const header = (
    <div className="flex flex-wrap align-items-center justify-content-between">
      <div className="flex gap-2 align-items-center">
        <i className="pi pi-table text-primary" />
        <span className="text-lg font-bold">Table Matrix Incident Report</span>
      </div>
      <Button
        icon={collapsed ? 'pi pi-chevron-down' : 'pi pi-chevron-up'}
        rounded
        text
        size="small"
        onClick={() => setCollapsed(!collapsed)}
        tooltip={collapsed ? 'Tampilkan tabel' : 'Sembunyikan tabel'}
        tooltipOptions={{ position: 'left' }}
      />
    </div>
  );

  return (
    <Card className="shadow-1 border-round-xl">
      {header}
      {!collapsed && (
        <div className="mt-3">
          <DataTable
            value={MATRIX_DATA}
            size="small"
            stripedRows
            responsiveLayout="scroll"
            className="text-sm border-round-lg"
            rowClassName={() => 'text-sm'}
          >
            <Column
              header=""
              body={severityBodyTemplate}
              style={{ width: '120px', whiteSpace: 'nowrap' }}
              frozen
            />
            <Column
              header="Manusia"
              field="manusia"
              style={{ minWidth: '160px' }}
            />
            <Column
              header="Asset/Produksi"
              body={(row: MatrixRow) => listBodyTemplate(row.asset)}
              style={{ minWidth: '220px' }}
            />
            <Column
              header="Lingkungan"
              field="lingkungan"
              style={{ minWidth: '180px' }}
            />
            <Column
              header="Reputasi"
              body={(row: MatrixRow) => listBodyTemplate(row.reputasi)}
              style={{ minWidth: '200px' }}
            />
            <Column
              header="Security Fisik"
              field="securityFisik"
              style={{ minWidth: '180px' }}
            />
            <Column
              header="Security Siber"
              field="securitySiber"
              style={{ minWidth: '200px' }}
            />
          </DataTable>
          <div className="p-2 mt-2 border-round" style={{ background: '#f8fafc' }}>
            <p className="m-0 text-xs text-500">
              <i className="mr-1 pi pi-info-circle" />
              Gunakan tabel di atas sebagai acuan dalam menentukan tingkat keparahan (severity) insiden berdasarkan dampak pada aspek Manusia, Asset/Produksi, Lingkungan, Reputasi, Security Fisik, dan Security Siber.
            </p>
          </div>
        </div>
      )}
    </Card>
  );
};

export default IncidentMatrixTable;
