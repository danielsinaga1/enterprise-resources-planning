import { useCallback, useEffect, useState } from 'react';
import { DataTable, type DataTablePageEvent } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { smartDocsService } from '../../services/smartDocsService';
import type { SmartDocsActivity } from '../../types/smartdocs';
import { formatDateTime } from './smartDocsFormat';

/* ============================================================
   LOG AKTIVITAS SMARTDOCS
   ============================================================ */
const SmartDocsActivityLog = () => {
  const [rows, setRows] = useState<SmartDocsActivity[]>([]);
  const [totalRecords, setTotalRecords] = useState(0);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  const load = useCallback(() => {
    setLoading(true);
    smartDocsService
      .activity({ page, size })
      .then((result) => {
        setRows(result.data);
        setTotalRecords(result.total ?? 0);
      })
      .finally(() => setLoading(false));
  }, [page, size]);

  useEffect(() => {
    load();
  }, [load]);

  const onPage = (e: DataTablePageEvent) => {
    setPage(e.page ?? 0);
    setSize(e.rows);
  };

  return (
    <div className="flex flex-column gap-3">
      <div className="flex flex-wrap align-items-center justify-content-between gap-2">
        <div>
          <h2 className="m-0 text-900">Log Aktivitas</h2>
          <span className="text-sm text-500">Jejak unggah, simpan, ubah, dan hapus dokumen</span>
        </div>
        <Button icon="pi pi-refresh" label="Muat ulang" outlined onClick={load} />
      </div>

      <DataTable
        value={rows}
        lazy
        loading={loading}
        paginator
        first={page * size}
        rows={size}
        totalRecords={totalRecords}
        rowsPerPageOptions={[20, 50, 100]}
        onPage={onPage}
        dataKey="id"
        size="small"
        emptyMessage="Belum ada aktivitas tercatat."
      >
        <Column
          field="timestamp"
          header="Waktu"
          body={(row: SmartDocsActivity) => (
            <span className="text-sm">{formatDateTime(row.timestamp)}</span>
          )}
          style={{ width: 190 }}
        />
        <Column
          field="username"
          header="Pengguna"
          body={(row: SmartDocsActivity) => (
            <span className="text-sm">{row.username?.trim() || 'System'}</span>
          )}
          style={{ width: 220 }}
        />
        <Column
          field="action"
          header="Aktivitas"
          body={(row: SmartDocsActivity) => <span className="text-sm text-900">{row.action}</span>}
        />
        <Column
          field="documentId"
          header="Dokumen"
          body={(row: SmartDocsActivity) => (
            <span className="text-xs text-500">{row.documentId ? `#${row.documentId}` : '—'}</span>
          )}
          style={{ width: 110 }}
        />
      </DataTable>
    </div>
  );
};

export default SmartDocsActivityLog;
