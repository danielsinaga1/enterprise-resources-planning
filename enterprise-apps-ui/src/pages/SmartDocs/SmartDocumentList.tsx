import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { DataTable, type DataTablePageEvent, type DataTableSortEvent } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { Tag } from 'primereact/tag';
import { Toast } from 'primereact/toast';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { smartDocsService } from '../../services/smartDocsService';
import type { SmartDocumentSummary } from '../../types/smartdocs';
import {
  formatCurrency,
  formatDateTime,
  formatInvoiceDate,
  reviewStatusLabel,
  reviewStatusSeverity,
} from './smartDocsFormat';

/* ============================================================
   DAFTAR DOKUMEN SMARTDOCS
   ============================================================ */
const SmartDocumentList = () => {
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);

  const [rows, setRows] = useState<SmartDocumentSummary[]>([]);
  const [totalRecords, setTotalRecords] = useState(0);
  const [loading, setLoading] = useState(true);

  // Paginasi modul ini 0-based, mengikuti controller SmartDocs.
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [sortField, setSortField] = useState('createdAt');
  const [sortOrder, setSortOrder] = useState<1 | -1 | 0>(-1);

  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    smartDocsService
      .list(
        { page, size, sortBy: sortField, sortDir: sortOrder === 1 ? 'asc' : 'desc' },
        { search }
      )
      .then((result) => {
        setRows(result.data);
        setTotalRecords(result.total ?? 0);
      })
      .catch(() => {
        toast.current?.show({
          severity: 'error',
          summary: 'Gagal memuat',
          detail: 'Daftar dokumen tidak dapat dimuat.',
          life: 4000,
        });
      })
      .finally(() => setLoading(false));
  }, [page, size, sortField, sortOrder, search]);

  useEffect(() => {
    load();
  }, [load]);

  const onPage = (e: DataTablePageEvent) => {
    setPage(e.page ?? 0);
    setSize(e.rows);
  };

  const onSort = (e: DataTableSortEvent) => {
    setSortField(e.sortField);
    setSortOrder(e.sortOrder ?? -1);
    setPage(0);
  };

  const applySearch = () => {
    setSearch(searchInput.trim());
    setPage(0);
  };

  const confirmDelete = (row: SmartDocumentSummary) => {
    confirmDialog({
      message: `Hapus dokumen ${row.invoiceNumber || row.vendorName}? Item dan jejak OCR-nya ikut terhapus.`,
      header: 'Konfirmasi Hapus',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Hapus',
      rejectLabel: 'Batal',
      acceptClassName: 'p-button-danger',
      accept: async () => {
        try {
          await smartDocsService.remove(row.id);
          toast.current?.show({ severity: 'success', summary: 'Berhasil', detail: 'Dokumen dihapus.', life: 3000 });
          load();
        } catch {
          // Hapus dibatasi peran administratif; pesannya dibuat jelas supaya
          // pengguna tahu ini soal hak akses, bukan galat sistem.
          toast.current?.show({
            severity: 'error',
            summary: 'Gagal menghapus',
            detail: 'Anda mungkin tidak punya hak hapus dokumen.',
            life: 5000,
          });
        }
      },
    });
  };

  return (
    <div className="flex flex-column gap-3">
      <Toast ref={toast} />
      <ConfirmDialog />

      <div className="flex flex-wrap align-items-center justify-content-between gap-2">
        <div>
          <h2 className="m-0 text-900">Daftar Dokumen</h2>
          <span className="text-sm text-500">Invoice dan struk hasil pemindaian OCR</span>
        </div>
        <div className="flex gap-2">
          <span className="p-input-icon-left">
            <i className="pi pi-search" />
            <InputText
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && applySearch()}
              placeholder="Cari vendor, nomor, pelanggan..."
              style={{ minWidth: 260 }}
            />
          </span>
          <Button icon="pi pi-search" label="Cari" onClick={applySearch} />
          <Button icon="pi pi-refresh" outlined onClick={load} tooltip="Muat ulang" />
        </div>
      </div>

      <DataTable
        value={rows}
        lazy
        loading={loading}
        paginator
        first={page * size}
        rows={size}
        totalRecords={totalRecords}
        rowsPerPageOptions={[10, 25, 50]}
        onPage={onPage}
        onSort={onSort}
        sortField={sortField}
        sortOrder={sortOrder}
        dataKey="id"
        size="small"
        emptyMessage="Tidak ada dokumen yang cocok."
        rowClassName={() => 'cursor-pointer'}
        onRowClick={(e) => navigate(`/dashboard/smartdocs/documents/${(e.data as SmartDocumentSummary).id}`)}
      >
        <Column
          field="vendorName"
          header="Vendor"
          sortable
          body={(row: SmartDocumentSummary) => (
            <div className="flex flex-column">
              <span className="font-medium text-900">{row.vendorName}</span>
              <span className="text-xs text-500">{row.invoiceNumber || 'Tanpa nomor'}</span>
            </div>
          )}
        />
        <Column
          field="customerName"
          header="Pelanggan"
          sortable
          body={(row: SmartDocumentSummary) => (
            <span className="text-sm">{row.customerName?.trim() || '—'}</span>
          )}
          style={{ width: 160 }}
        />
        <Column
          field="invoiceDate"
          header="Tanggal Invoice"
          sortable
          body={(row: SmartDocumentSummary) => (
            <span className="text-sm">{formatInvoiceDate(row.invoiceDate)}</span>
          )}
          style={{ width: 150 }}
        />
        <Column
          field="category"
          header="Kategori"
          sortable
          body={(row: SmartDocumentSummary) => (
            <span className="text-sm">{row.category?.trim() || '—'}</span>
          )}
          style={{ width: 130 }}
        />
        <Column
          field="grandTotal"
          header="Nilai"
          sortable
          body={(row: SmartDocumentSummary) => (
            <span className="font-semibold">{formatCurrency(row.grandTotal, row.currency)}</span>
          )}
          style={{ width: 150 }}
        />
        <Column
          header="Item"
          body={(row: SmartDocumentSummary) => (
            <span className="text-sm">{row.itemCount ?? '—'}</span>
          )}
          style={{ width: 80 }}
        />
        <Column
          header="Status Tinjauan"
          body={(row: SmartDocumentSummary) => (
            <Tag
              value={reviewStatusLabel(row.reviewStatus)}
              severity={reviewStatusSeverity(row.reviewStatus) ?? undefined}
            />
          )}
          style={{ width: 170 }}
        />
        <Column
          field="createdAt"
          header="Dipindai"
          sortable
          body={(row: SmartDocumentSummary) => (
            <span className="text-xs text-500">{formatDateTime(row.createdAt)}</span>
          )}
          style={{ width: 160 }}
        />
        <Column
          header=""
          body={(row: SmartDocumentSummary) => (
            <Button
              icon="pi pi-trash"
              severity="danger"
              text
              size="small"
              tooltip="Hapus dokumen"
              onClick={(e) => {
                e.stopPropagation();
                confirmDelete(row);
              }}
            />
          )}
          style={{ width: 60 }}
        />
      </DataTable>
    </div>
  );
};

export default SmartDocumentList;
