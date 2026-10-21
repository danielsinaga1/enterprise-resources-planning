import { useEffect, useState, useCallback, useRef } from 'react';
import { DataTable, type DataTablePageEvent, type DataTableSortEvent } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { InputTextarea } from 'primereact/inputtextarea';
import { InputNumber } from 'primereact/inputnumber';
import { Calendar } from 'primereact/calendar';
import { Dropdown } from 'primereact/dropdown';
import { Checkbox } from 'primereact/checkbox';
import { Card } from 'primereact/card';
import { Dialog } from 'primereact/dialog';
import { Toast } from 'primereact/toast';
import { Tag } from 'primereact/tag';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { purchaseRequisitionService } from '../../services';
import type {
  PurchaseRequisition,
  PurchaseRequisitionSummary,
  PrStatusUpdate,
} from '../../types/irms';

/* ============================================================
   MONITORING PURCHASE REQUISITION
   Menggantikan file Excel "PR Outstanding.xlsx" milik tim IT.

   Satu baris tabel = satu BARIS PR (nomor PR + line), bukan satu
   dokumen PR — satu PR bisa punya belasan line dengan status
   berbeda-beda, persis seperti di Excel aslinya.

   Kolom "Umur" tidak disimpan di database: backend menghitungnya
   dari (hari ini - tanggal PR to Logistic) supaya tidak basi.
   ============================================================ */

/** Ambang usia PR untuk pewarnaan kolom Umur, dalam hari. */
const AGING_WARNING = 30;
const AGING_DANGER = 90;

const emptyForm = {
  prNo: '',
  prLine: 1,
  prLineDescription: '',
  prCreatedDate: null as Date | null,
  prApprovalDate: null as Date | null,
  prToLogisticDate: null as Date | null,
  buyerPr: '',
  isRenewal: false,
  status: '',
};

/** Tanggal → 'yyyy-MM-dd' tanpa pergeseran zona waktu (toISOString memakai UTC). */
const toIsoDate = (d: Date | null): string | undefined => {
  if (!d) return undefined;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};

const formatDate = (value?: string | null): string => {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('id-ID', { day: '2-digit', month: 'short', year: 'numeric' });
};

const PurchaseRequisitionList = () => {
  const toast = useRef<Toast>(null);

  // Data & paginasi
  const [data, setData] = useState<PurchaseRequisition[]>([]);
  const [summary, setSummary] = useState<PurchaseRequisitionSummary | null>(null);
  const [totalRecords, setTotalRecords] = useState(0);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState(10);
  const [sortField, setSortField] = useState<string>('prCreatedDate');
  const [sortOrder, setSortOrder] = useState<1 | -1 | 0>(-1);

  // Filter
  const [keyword, setKeyword] = useState('');
  const [buyerFilter, setBuyerFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [renewalFilter, setRenewalFilter] = useState<boolean | null>(null);

  // Dialog form (tambah/ubah)
  const [formDialog, setFormDialog] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState({ ...emptyForm });
  const [saving, setSaving] = useState(false);

  // Dialog riwayat status
  const [historyDialog, setHistoryDialog] = useState(false);
  const [historyRow, setHistoryRow] = useState<PurchaseRequisition | null>(null);
  const [history, setHistory] = useState<PrStatusUpdate[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [newStatus, setNewStatus] = useState('');
  const [newStatusDate, setNewStatusDate] = useState<Date | null>(new Date());
  const [newStatusNotes, setNewStatusNotes] = useState('');

  const renewalOptions = [
    { label: 'Semua jenis', value: null },
    { label: 'Perpanjangan (Renewal)', value: true },
    { label: 'Non-Renewal', value: false },
  ];

  const notifyError = (detail: string) =>
    toast.current?.show({ severity: 'error', summary: 'Gagal', detail, life: 4000 });

  const notifySuccess = (detail: string) =>
    toast.current?.show({ severity: 'success', summary: 'Berhasil', detail, life: 3000 });

  // ── Muat data ──────────────────────────────────────────────
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await purchaseRequisitionService.list(
        {
          page,
          limit: rows,
          sortBy: sortField,
          sortOrder: sortOrder === 1 ? 'asc' : 'desc',
        },
        {
          keyword: keyword || undefined,
          buyer: buyerFilter || undefined,
          status: statusFilter || undefined,
          'is-renewal': renewalFilter ?? undefined,
        }
      );
      setData(result.data);
      setTotalRecords(result.total);
    } catch (err) {
      console.error('Gagal memuat data PR', err);
      notifyError('Tidak bisa memuat data Purchase Requisition.');
    } finally {
      setLoading(false);
    }
  }, [page, rows, sortField, sortOrder, keyword, buyerFilter, statusFilter, renewalFilter]);

  const loadSummary = useCallback(async () => {
    try {
      setSummary(await purchaseRequisitionService.summary());
    } catch (err) {
      // Ringkasan bersifat pelengkap — kegagalannya tidak boleh mengosongkan tabel.
      console.error('Gagal memuat ringkasan PR', err);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    loadSummary();
  }, [loadSummary]);

  // ── Handler tabel ──────────────────────────────────────────
  const onPage = (e: DataTablePageEvent) => {
    setPage((e.page ?? 0) + 1);
    setRows(e.rows);
  };

  const onSort = (e: DataTableSortEvent) => {
    setSortField(e.sortField as string);
    setSortOrder(e.sortOrder as 1 | -1 | 0);
    setPage(1);
  };

  const resetFilters = () => {
    setKeyword('');
    setBuyerFilter('');
    setStatusFilter('');
    setRenewalFilter(null);
    setPage(1);
  };

  // ── Form tambah / ubah ─────────────────────────────────────
  const openCreate = () => {
    setEditingId(null);
    setForm({ ...emptyForm });
    setFormDialog(true);
  };

  const openEdit = (row: PurchaseRequisition) => {
    setEditingId(row.id);
    setForm({
      prNo: row.prNo,
      prLine: row.prLine,
      prLineDescription: row.prLineDescription ?? '',
      prCreatedDate: row.prCreatedDate ? new Date(row.prCreatedDate) : null,
      prApprovalDate: row.prApprovalDate ? new Date(row.prApprovalDate) : null,
      prToLogisticDate: row.prToLogisticDate ? new Date(row.prToLogisticDate) : null,
      buyerPr: row.buyerPr ?? '',
      isRenewal: row.isRenewal ?? false,
      status: '',
    });
    setFormDialog(true);
  };

  const handleSave = async () => {
    if (!form.prNo.trim()) {
      notifyError('Nomor PR wajib diisi.');
      return;
    }
    if (!form.prLine || form.prLine < 1) {
      notifyError('Line PR minimal 1.');
      return;
    }

    setSaving(true);
    try {
      const base = {
        pr_no: form.prNo.trim(),
        pr_line: form.prLine,
        pr_line_description: form.prLineDescription || undefined,
        pr_created_date: toIsoDate(form.prCreatedDate),
        pr_approval_date: toIsoDate(form.prApprovalDate),
        pr_to_logistic_date: toIsoDate(form.prToLogisticDate),
        buyer_pr: form.buyerPr || undefined,
        is_renewal: form.isRenewal,
      };

      if (editingId) {
        await purchaseRequisitionService.update(editingId, base);
        notifySuccess('Baris PR berhasil diperbarui.');
      } else {
        await purchaseRequisitionService.create({
          ...base,
          status: form.status || undefined,
        });
        notifySuccess('Baris PR berhasil ditambahkan.');
      }

      setFormDialog(false);
      loadData();
      loadSummary();
    } catch (err) {
      console.error('Gagal menyimpan PR', err);
      notifyError(
        editingId ? 'Tidak bisa memperbarui baris PR.' : 'Tidak bisa menambah baris PR. Pastikan nomor PR + line belum terdaftar.'
      );
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = (row: PurchaseRequisition) => {
    confirmDialog({
      message: `Hapus baris PR ${row.prNo} line ${row.prLine}?`,
      header: 'Konfirmasi Hapus',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Hapus',
      rejectLabel: 'Batal',
      acceptClassName: 'p-button-danger',
      accept: async () => {
        try {
          await purchaseRequisitionService.delete(row.id);
          notifySuccess('Baris PR berhasil dihapus.');
          loadData();
          loadSummary();
        } catch (err) {
          console.error('Gagal menghapus PR', err);
          notifyError('Tidak bisa menghapus baris PR.');
        }
      },
    });
  };

  // ── Riwayat status ─────────────────────────────────────────
  const openHistory = async (row: PurchaseRequisition) => {
    setHistoryRow(row);
    setHistoryDialog(true);
    setNewStatus('');
    setNewStatusDate(new Date());
    setNewStatusNotes('');
    setHistoryLoading(true);
    try {
      setHistory(await purchaseRequisitionService.statusHistory(row.id));
    } catch (err) {
      console.error('Gagal memuat riwayat status', err);
      notifyError('Tidak bisa memuat riwayat status.');
      setHistory([]);
    } finally {
      setHistoryLoading(false);
    }
  };

  const handleAddStatus = async () => {
    if (!historyRow) return;
    if (!newStatus.trim()) {
      notifyError('Status wajib diisi.');
      return;
    }
    try {
      await purchaseRequisitionService.addStatusUpdate(historyRow.id, {
        status: newStatus.trim(),
        update_date: toIsoDate(newStatusDate),
        notes: newStatusNotes || undefined,
      });
      notifySuccess('Status berhasil dicatat.');
      setNewStatus('');
      setNewStatusNotes('');
      setHistory(await purchaseRequisitionService.statusHistory(historyRow.id));
      loadData();
    } catch (err) {
      console.error('Gagal mencatat status', err);
      notifyError('Tidak bisa mencatat status.');
    }
  };

  const handleDeleteStatus = async (entry: PrStatusUpdate) => {
    if (!historyRow) return;
    try {
      await purchaseRequisitionService.deleteStatusUpdate(historyRow.id, entry.id);
      notifySuccess('Riwayat status dihapus.');
      setHistory(await purchaseRequisitionService.statusHistory(historyRow.id));
      loadData();
    } catch (err) {
      console.error('Gagal menghapus riwayat status', err);
      notifyError('Tidak bisa menghapus riwayat status.');
    }
  };

  // ── Template kolom ─────────────────────────────────────────
  const descriptionTemplate = (row: PurchaseRequisition) => (
    <span
      className="text-sm block"
      style={{ maxWidth: 380, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
      title={row.prLineDescription ?? ''}
    >
      {row.prLineDescription || '—'}
    </span>
  );

  const daysTemplate = (row: PurchaseRequisition) => {
    if (row.days === null || row.days === undefined) {
      return <span className="text-400">—</span>;
    }
    const severity =
      row.days >= AGING_DANGER ? 'danger' : row.days >= AGING_WARNING ? 'warning' : 'info';
    return <Tag value={`${row.days} hari`} severity={severity} className="text-xs" />;
  };

  const statusTemplate = (row: PurchaseRequisition) => {
    if (!row.currentStatus) return <span className="text-400">Belum ada update</span>;
    return (
      <div className="flex flex-column gap-1">
        <span className="text-sm font-medium">{row.currentStatus}</span>
        <span className="text-xs text-500">{formatDate(row.currentStatusDate)}</span>
      </div>
    );
  };

  const renewalTemplate = (row: PurchaseRequisition) =>
    row.isRenewal ? (
      <Tag value="Renewal" severity="success" className="text-xs" />
    ) : (
      <Tag value="Non-Renewal" severity="secondary" className="text-xs" />
    );

  const actionTemplate = (row: PurchaseRequisition) => (
    <div className="flex gap-1">
      <Button
        icon="pi pi-history"
        rounded
        text
        severity="info"
        size="small"
        tooltip="Riwayat status"
        tooltipOptions={{ position: 'top' }}
        onClick={() => openHistory(row)}
      />
      <Button
        icon="pi pi-pencil"
        rounded
        text
        severity="warning"
        size="small"
        tooltip="Ubah"
        tooltipOptions={{ position: 'top' }}
        onClick={() => openEdit(row)}
      />
      <Button
        icon="pi pi-trash"
        rounded
        text
        severity="danger"
        size="small"
        tooltip="Hapus"
        tooltipOptions={{ position: 'top' }}
        onClick={() => handleDelete(row)}
      />
    </div>
  );

  // ── Kartu ringkasan ────────────────────────────────────────
  const summaryCard = (
    label: string,
    value: number | undefined,
    icon: string,
    color: string
  ) => (
    <div className="col-12 sm:col-6 lg:col-3">
      <Card className={`shadow-1 border-round-xl border-left-3 border-${color}-400`}>
        <div className="flex align-items-center gap-3">
          <div
            className={`flex align-items-center justify-content-center border-round-lg bg-${color}-50`}
            style={{ width: 48, height: 48 }}
          >
            <i className={`${icon} text-${color}-500 text-xl`} />
          </div>
          <div>
            <p className="text-xs text-500 m-0 mb-1">{label}</p>
            <p className="text-2xl font-bold text-800 m-0">{value ?? '—'}</p>
          </div>
        </div>
      </Card>
    </div>
  );

  return (
    <div className="flex flex-column gap-4">
      <Toast ref={toast} />
      <ConfirmDialog />

      {/* Header */}
      <div className="flex align-items-center justify-content-between flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold text-800 m-0">
            <i className="pi pi-shopping-cart mr-2 text-blue-500" />
            Monitoring Purchase Requisition
          </h2>
          <p className="text-sm text-500 mt-1 mb-0">
            Pemantauan PR pengadaan IT beserta riwayat status tiap barisnya
          </p>
        </div>
        <Button
          label="Tambah Baris PR"
          icon="pi pi-plus"
          className="border-round-lg"
          onClick={openCreate}
        />
      </div>

      {/* Ringkasan */}
      <div className="grid">
        {summaryCard('Jumlah PR', summary?.totalPr, 'pi pi-file', 'blue')}
        {summaryCard('Jumlah Line PR', summary?.totalLine, 'pi pi-list', 'cyan')}
        {summaryCard('PR Renewal', summary?.totalPrRenewal, 'pi pi-refresh', 'green')}
        {summaryCard('PR Non-Renewal', summary?.totalPrNonRenewal, 'pi pi-box', 'orange')}
      </div>

      {/* Filter */}
      <Card className="shadow-1 border-round-xl">
        <div className="flex flex-wrap gap-3 align-items-end">
          <div className="flex flex-column gap-1">
            <label className="text-xs font-medium text-500">Pencarian</label>
            <span className="p-input-icon-left">
              <i className="pi pi-search" />
              <InputText
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && setPage(1)}
                placeholder="Nomor PR atau deskripsi"
                className="w-18rem"
              />
            </span>
          </div>
          <div className="flex flex-column gap-1">
            <label className="text-xs font-medium text-500">Buyer</label>
            <InputText
              value={buyerFilter}
              onChange={(e) => setBuyerFilter(e.target.value)}
              placeholder="Nama buyer"
              className="w-12rem"
            />
          </div>
          <div className="flex flex-column gap-1">
            <label className="text-xs font-medium text-500">Status</label>
            <InputText
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              placeholder="mis. TTC, PO"
              className="w-12rem"
            />
          </div>
          <div className="flex flex-column gap-1">
            <label className="text-xs font-medium text-500">Jenis</label>
            <Dropdown
              value={renewalFilter}
              options={renewalOptions}
              onChange={(e) => {
                setRenewalFilter(e.value);
                setPage(1);
              }}
              className="w-14rem"
            />
          </div>
          <Button
            label="Terapkan"
            icon="pi pi-filter"
            size="small"
            className="border-round-lg"
            onClick={() => setPage(1)}
          />
          <Button
            label="Reset"
            icon="pi pi-filter-slash"
            severity="secondary"
            outlined
            size="small"
            className="border-round-lg"
            onClick={resetFilters}
          />
        </div>
      </Card>

      {/* Tabel */}
      <Card className="shadow-1 border-round-xl">
        <DataTable
          value={data}
          lazy
          paginator
          first={(page - 1) * rows}
          rows={rows}
          totalRecords={totalRecords}
          onPage={onPage}
          onSort={onSort}
          sortField={sortField}
          sortOrder={sortOrder}
          loading={loading}
          size="small"
          stripedRows
          responsiveLayout="scroll"
          emptyMessage="Belum ada data Purchase Requisition."
          rowsPerPageOptions={[10, 25, 50, 100]}
          paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
          className="border-round-lg"
        >
          <Column field="prNo" header="No. PR" sortable className="font-medium" style={{ width: '100px' }} />
          <Column field="prLine" header="Line" sortable style={{ width: '80px' }} />
          <Column header="Deskripsi" body={descriptionTemplate} />
          <Column
            header="Dibuat"
            body={(row: PurchaseRequisition) => (
              <span className="text-sm">{formatDate(row.prCreatedDate)}</span>
            )}
            sortable
            sortField="prCreatedDate"
            style={{ width: '120px' }}
          />
          <Column
            header="Ke Logistic"
            body={(row: PurchaseRequisition) => (
              <span className="text-sm">{formatDate(row.prToLogisticDate)}</span>
            )}
            sortable
            sortField="prToLogisticDate"
            style={{ width: '120px' }}
          />
          <Column header="Umur" body={daysTemplate} style={{ width: '100px' }} />
          <Column
            field="buyerPr"
            header="Buyer"
            sortable
            body={(row: PurchaseRequisition) => (
              <span className="text-sm">{row.buyerPr || '—'}</span>
            )}
            style={{ width: '150px' }}
          />
          <Column header="Jenis" body={renewalTemplate} style={{ width: '120px' }} />
          <Column header="Status Terakhir" body={statusTemplate} style={{ width: '200px' }} />
          <Column
            header="Aksi"
            body={actionTemplate}
            style={{ width: '140px' }}
            frozen
            alignFrozen="right"
          />
        </DataTable>
      </Card>

      {/* Dialog tambah / ubah */}
      <Dialog
        header={editingId ? 'Ubah Baris PR' : 'Tambah Baris PR'}
        visible={formDialog}
        style={{ width: '640px' }}
        breakpoints={{ '960px': '90vw' }}
        onHide={() => setFormDialog(false)}
        modal
        footer={
          <div>
            <Button
              label="Batal"
              icon="pi pi-times"
              severity="secondary"
              outlined
              onClick={() => setFormDialog(false)}
            />
            <Button label="Simpan" icon="pi pi-check" loading={saving} onClick={handleSave} />
          </div>
        }
      >
        <div className="grid formgrid p-fluid pt-2">
          <div className="field col-12 md:col-6">
            <label className="text-sm font-medium">Nomor PR *</label>
            <InputText
              value={form.prNo}
              onChange={(e) => setForm({ ...form, prNo: e.target.value })}
              placeholder="mis. 43021"
            />
          </div>
          <div className="field col-12 md:col-6">
            <label className="text-sm font-medium">Line PR *</label>
            <InputNumber
              value={form.prLine}
              onValueChange={(e) => setForm({ ...form, prLine: e.value ?? 1 })}
              min={1}
              showButtons
            />
          </div>
          <div className="field col-12">
            <label className="text-sm font-medium">Deskripsi</label>
            <InputTextarea
              value={form.prLineDescription}
              onChange={(e) => setForm({ ...form, prLineDescription: e.target.value })}
              rows={4}
              autoResize
            />
          </div>
          <div className="field col-12 md:col-4">
            <label className="text-sm font-medium">Tanggal Dibuat</label>
            <Calendar
              value={form.prCreatedDate}
              onChange={(e) => setForm({ ...form, prCreatedDate: (e.value as Date) ?? null })}
              dateFormat="dd/mm/yy"
              showIcon
            />
          </div>
          <div className="field col-12 md:col-4">
            <label className="text-sm font-medium">Tanggal Approval</label>
            <Calendar
              value={form.prApprovalDate}
              onChange={(e) => setForm({ ...form, prApprovalDate: (e.value as Date) ?? null })}
              dateFormat="dd/mm/yy"
              showIcon
            />
          </div>
          <div className="field col-12 md:col-4">
            <label className="text-sm font-medium">Ke Logistic</label>
            <Calendar
              value={form.prToLogisticDate}
              onChange={(e) => setForm({ ...form, prToLogisticDate: (e.value as Date) ?? null })}
              dateFormat="dd/mm/yy"
              showIcon
            />
          </div>
          <div className="field col-12 md:col-6">
            <label className="text-sm font-medium">Buyer</label>
            <InputText
              value={form.buyerPr}
              onChange={(e) => setForm({ ...form, buyerPr: e.target.value })}
            />
          </div>
          {!editingId && (
            <div className="field col-12 md:col-6">
              <label className="text-sm font-medium">Status Awal</label>
              <InputText
                value={form.status}
                onChange={(e) => setForm({ ...form, status: e.target.value })}
                placeholder="mis. Prepare RFQ (opsional)"
              />
            </div>
          )}
          <div className="field col-12 flex align-items-center gap-2 mt-2">
            <Checkbox
              inputId="isRenewal"
              checked={form.isRenewal}
              onChange={(e) => setForm({ ...form, isRenewal: e.checked ?? false })}
            />
            <label htmlFor="isRenewal" className="text-sm">
              PR perpanjangan lisensi / support (Renewal)
            </label>
          </div>
        </div>
      </Dialog>

      {/* Dialog riwayat status */}
      <Dialog
        header={
          historyRow
            ? `Riwayat Status — PR ${historyRow.prNo} line ${historyRow.prLine}`
            : 'Riwayat Status'
        }
        visible={historyDialog}
        style={{ width: '720px' }}
        breakpoints={{ '960px': '90vw' }}
        onHide={() => setHistoryDialog(false)}
        modal
      >
        <div className="flex flex-column gap-4 pt-2">
          {/* Form tambah status */}
          <Card className="surface-50 border-round-lg shadow-none">
            <div className="grid formgrid p-fluid">
              <div className="field col-12 md:col-6">
                <label className="text-sm font-medium">Status Baru *</label>
                <InputText
                  value={newStatus}
                  onChange={(e) => setNewStatus(e.target.value)}
                  placeholder="mis. TTC to Buyer, PO 37056"
                />
              </div>
              <div className="field col-12 md:col-6">
                <label className="text-sm font-medium">Tanggal Update</label>
                <Calendar
                  value={newStatusDate}
                  onChange={(e) => setNewStatusDate((e.value as Date) ?? null)}
                  dateFormat="dd/mm/yy"
                  showIcon
                />
              </div>
              <div className="field col-12">
                <label className="text-sm font-medium">Catatan</label>
                <InputTextarea
                  value={newStatusNotes}
                  onChange={(e) => setNewStatusNotes(e.target.value)}
                  rows={2}
                  autoResize
                />
              </div>
              <div className="col-12">
                <Button
                  label="Catat Status"
                  icon="pi pi-plus"
                  size="small"
                  className="border-round-lg"
                  onClick={handleAddStatus}
                />
                <small className="block text-500 mt-2">
                  Status pada tanggal yang sama akan menimpa entri sebelumnya.
                </small>
              </div>
            </div>
          </Card>

          {/* Daftar riwayat */}
          <DataTable
            value={history}
            loading={historyLoading}
            size="small"
            stripedRows
            emptyMessage="Belum ada riwayat status."
            className="border-round-lg"
          >
            <Column
              header="Tanggal"
              body={(row: PrStatusUpdate) => (
                <span className="text-sm">{formatDate(row.updateDate)}</span>
              )}
              style={{ width: '130px' }}
            />
            <Column field="status" header="Status" className="text-sm" />
            <Column
              header="Catatan"
              body={(row: PrStatusUpdate) => (
                <span className="text-sm">{row.notes || '—'}</span>
              )}
            />
            <Column
              header="Oleh"
              body={(row: PrStatusUpdate) => (
                <span className="text-xs text-500">{row.createdBy || '—'}</span>
              )}
              style={{ width: '120px' }}
            />
            <Column
              body={(row: PrStatusUpdate) => (
                <Button
                  icon="pi pi-trash"
                  rounded
                  text
                  severity="danger"
                  size="small"
                  tooltip="Hapus entri"
                  tooltipOptions={{ position: 'top' }}
                  onClick={() => handleDeleteStatus(row)}
                />
              )}
              style={{ width: '60px' }}
            />
          </DataTable>
        </div>
      </Dialog>
    </div>
  );
};

export default PurchaseRequisitionList;
