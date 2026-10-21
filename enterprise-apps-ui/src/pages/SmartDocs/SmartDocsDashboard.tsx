import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card } from 'primereact/card';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Tag } from 'primereact/tag';
import { Button } from 'primereact/button';
import { Message } from 'primereact/message';
import { ProgressSpinner } from 'primereact/progressspinner';
import { smartDocsService } from '../../services/smartDocsService';
import type { SmartDocsDashboard as DashboardData, SmartDocumentSummary } from '../../types/smartdocs';
import {
  formatCurrency,
  formatDateTime,
  formatInvoiceDate,
  formatNumber,
  reviewStatusLabel,
  reviewStatusSeverity,
} from './smartDocsFormat';

/* ============================================================
   SMARTDOCS DASHBOARD — ringkasan dokumen hasil OCR
   ============================================================ */

interface StatCardProps {
  icon: string;
  label: string;
  value: string;
  color: string;
  hint?: string;
}

const StatCard = ({ icon, label, value, color, hint }: StatCardProps) => (
  <div className="surface-card border-round-xl shadow-1 p-3 h-full">
    <div className="flex align-items-center gap-3">
      <span
        className="flex align-items-center justify-content-center border-circle"
        style={{ width: 44, height: 44, background: `${color}1a`, color }}
      >
        <i className={icon} style={{ fontSize: '1.25rem' }} />
      </span>
      <div className="flex-1">
        <div className="text-xs text-500 font-medium">{label}</div>
        <div className="text-xl font-bold text-900">{value}</div>
        {hint && <div className="text-xs text-500 mt-1">{hint}</div>}
      </div>
    </div>
  </div>
);

const SmartDocsDashboard = () => {
  const navigate = useNavigate();
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    smartDocsService
      .dashboard()
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch(() => {
        if (!cancelled) setError('Gagal memuat ringkasan SmartDocs. Coba muat ulang halaman.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return (
      <div className="flex justify-content-center p-6">
        <ProgressSpinner style={{ width: 48, height: 48 }} />
      </div>
    );
  }

  if (error || !data) {
    return <Message severity="error" text={error ?? 'Data tidak tersedia.'} className="w-full" />;
  }

  const reviewShare =
    data.totalDocuments > 0 ? Math.round((data.needsReview / data.totalDocuments) * 100) : 0;

  return (
    <div className="flex flex-column gap-3">
      <div className="flex flex-wrap align-items-center justify-content-between gap-2">
        <div>
          <h2 className="m-0 text-900">SmartDocs</h2>
          <span className="text-sm text-500">Dokumen invoice hasil pemindaian OCR</span>
        </div>
        <div className="flex gap-2">
          <Button
            label="Daftar Dokumen"
            icon="pi pi-file"
            outlined
            onClick={() => navigate('/dashboard/smartdocs/documents')}
          />
          <Button
            label="Laporan"
            icon="pi pi-chart-bar"
            outlined
            onClick={() => navigate('/dashboard/smartdocs/reports')}
          />
        </div>
      </div>

      <div className="grid">
        <div className="col-12 md:col-4">
          <StatCard
            icon="pi pi-file"
            label="Total Dokumen"
            value={formatNumber(data.totalDocuments)}
            color="#4f46e5"
          />
        </div>
        <div className="col-12 md:col-4">
          <StatCard
            icon="pi pi-wallet"
            label="Total Nilai Dokumen"
            value={formatCurrency(data.totalExpenses)}
            color="#0ea5e9"
          />
        </div>
        <div className="col-12 md:col-4">
          <StatCard
            icon="pi pi-exclamation-triangle"
            label="Perlu Ditinjau"
            value={formatNumber(data.needsReview)}
            color="#f59e0b"
            hint={`${reviewShare}% dokumen belum berstatus Valid`}
          />
        </div>
      </div>

      <div className="grid">
        <div className="col-12 lg:col-7">
          <Card title="Dokumen Terbaru" className="h-full">
            <DataTable
              value={data.recentDocuments}
              size="small"
              emptyMessage="Belum ada dokumen."
              rowClassName={() => 'cursor-pointer'}
              onRowClick={(e) =>
                navigate(`/dashboard/smartdocs/documents/${(e.data as SmartDocumentSummary).id}`)
              }
            >
              <Column
                header="Vendor"
                body={(row: SmartDocumentSummary) => (
                  <div className="flex flex-column">
                    <span className="font-medium text-900">{row.vendorName}</span>
                    <span className="text-xs text-500">{row.invoiceNumber || '—'}</span>
                  </div>
                )}
              />
              <Column
                header="Tanggal"
                body={(row: SmartDocumentSummary) => (
                  <span className="text-sm">{formatInvoiceDate(row.invoiceDate)}</span>
                )}
                style={{ width: 140 }}
              />
              <Column
                header="Nilai"
                body={(row: SmartDocumentSummary) => (
                  <span className="font-semibold">{formatCurrency(row.grandTotal, row.currency)}</span>
                )}
                style={{ width: 150 }}
              />
              <Column
                header="Status"
                body={(row: SmartDocumentSummary) => (
                  <Tag
                    value={reviewStatusLabel(row.reviewStatus)}
                    severity={reviewStatusSeverity(row.reviewStatus) ?? undefined}
                  />
                )}
                style={{ width: 160 }}
              />
            </DataTable>
          </Card>
        </div>

        <div className="col-12 lg:col-5">
          <Card title="Aktivitas Terbaru" className="h-full">
            {data.recentActivity.length === 0 ? (
              <span className="text-sm text-500">Belum ada aktivitas.</span>
            ) : (
              <ul className="list-none p-0 m-0 flex flex-column gap-3">
                {data.recentActivity.map((item) => (
                  <li key={item.id} className="flex gap-2">
                    <i className="pi pi-circle-fill text-primary mt-1" style={{ fontSize: '0.5rem' }} />
                    <div className="flex-1">
                      <div className="text-sm text-900">{item.action}</div>
                      <div className="text-xs text-500">
                        {item.username || 'System'} · {formatDateTime(item.timestamp)}
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
            <div className="mt-3">
              <Button
                label="Lihat semua aktivitas"
                icon="pi pi-history"
                text
                size="small"
                onClick={() => navigate('/dashboard/smartdocs/activity')}
              />
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default SmartDocsDashboard;
