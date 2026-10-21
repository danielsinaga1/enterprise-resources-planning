import { useEffect, useMemo, useState } from 'react';
import { Card } from 'primereact/card';
import { Chart } from 'primereact/chart';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Message } from 'primereact/message';
import { ProgressSpinner } from 'primereact/progressspinner';
import { smartDocsService } from '../../services/smartDocsService';
import type { SmartDocsBucket, SmartDocsReports as ReportsData } from '../../types/smartdocs';
import { formatCurrency, formatNumber } from './smartDocsFormat';

/* ============================================================
   LAPORAN SMARTDOCS
   ============================================================ */

// Palet dipakai untuk diagram cacah; urutannya tetap supaya warna satu
// kategori tidak berubah-ubah tiap kali halaman dimuat.
const PALETTE = ['#4f46e5', '#0ea5e9', '#22c55e', '#f59e0b', '#ef4444', '#a855f7', '#14b8a6'];

const SmartDocsReports = () => {
  const [data, setData] = useState<ReportsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    smartDocsService
      .reports()
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch(() => {
        if (!cancelled) setError('Gagal memuat laporan SmartDocs.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const monthlyChart = useMemo(() => {
    const buckets = data?.expensePerMonth ?? [];
    return {
      data: {
        labels: buckets.map((b) => b.label),
        datasets: [
          {
            label: 'Nilai dokumen',
            data: buckets.map((b) => b.total ?? 0),
            backgroundColor: '#4f46e5',
            borderRadius: 6,
          },
        ],
      },
      options: {
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          y: {
            ticks: {
              callback: (value: unknown) => formatCurrency(Number(value)),
            },
          },
        },
      },
    };
  }, [data]);

  const statusChart = useMemo(() => {
    const buckets = data?.countByReviewStatus ?? [];
    return {
      data: {
        labels: buckets.map((b) => b.label),
        datasets: [
          {
            data: buckets.map((b) => b.count ?? 0),
            backgroundColor: buckets.map((_, i) => PALETTE[i % PALETTE.length]),
          },
        ],
      },
      options: {
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom' as const } },
      },
    };
  }, [data]);

  if (loading) {
    return (
      <div className="flex justify-content-center p-6">
        <ProgressSpinner style={{ width: 48, height: 48 }} />
      </div>
    );
  }

  if (error || !data) {
    return <Message severity="error" text={error ?? 'Laporan tidak tersedia.'} className="w-full" />;
  }

  return (
    <div className="flex flex-column gap-3">
      <div>
        <h2 className="m-0 text-900">Laporan SmartDocs</h2>
        <span className="text-sm text-500">Rekap nilai dokumen dan mutu pembacaan OCR</span>
      </div>

      <div className="grid">
        <div className="col-12 lg:col-8">
          <Card title="Nilai Dokumen per Bulan" className="h-full">
            {data.expensePerMonth.length === 0 ? (
              <span className="text-sm text-500">Belum ada data.</span>
            ) : (
              <Chart type="bar" data={monthlyChart.data} options={monthlyChart.options} style={{ height: 300 }} />
            )}
          </Card>
        </div>

        <div className="col-12 lg:col-4">
          <Card title="Status Tinjauan" className="h-full">
            {data.countByReviewStatus.length === 0 ? (
              <span className="text-sm text-500">Belum ada data.</span>
            ) : (
              <Chart type="doughnut" data={statusChart.data} options={statusChart.options} style={{ height: 300 }} />
            )}
          </Card>
        </div>
      </div>

      <div className="grid">
        <div className="col-12 lg:col-6">
          <Card title="Nilai per Divisi" className="h-full">
            <DataTable value={data.expenseByDivision} size="small" emptyMessage="Belum ada data.">
              <Column
                header="Divisi"
                body={(row: SmartDocsBucket) => <span className="text-sm text-900">{row.label}</span>}
              />
              <Column
                header="Nilai"
                body={(row: SmartDocsBucket) => (
                  <span className="text-sm font-semibold">{formatCurrency(row.total)}</span>
                )}
                style={{ width: 180 }}
              />
            </DataTable>
          </Card>
        </div>

        <div className="col-12 lg:col-6">
          <Card title="Jumlah Dokumen per Kategori" className="h-full">
            <DataTable value={data.countByCategory} size="small" emptyMessage="Belum ada data.">
              <Column
                header="Kategori"
                body={(row: SmartDocsBucket) => <span className="text-sm text-900">{row.label}</span>}
              />
              <Column
                header="Dokumen"
                body={(row: SmartDocsBucket) => (
                  <span className="text-sm font-semibold">{formatNumber(row.count)}</span>
                )}
                style={{ width: 140 }}
              />
            </DataTable>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default SmartDocsReports;
