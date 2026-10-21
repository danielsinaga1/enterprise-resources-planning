import { useEffect, useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { DataTable, type DataTablePageEvent, type DataTableSortEvent } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { Card } from 'primereact/card';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { Toast } from 'primereact/toast';
import { Tag } from 'primereact/tag';
import { BreadCrumb } from 'primereact/breadcrumb';
import { Tooltip } from 'primereact/tooltip';
import { Dialog } from 'primereact/dialog';
import { ProgressSpinner } from 'primereact/progressspinner';
import { SelectButton } from 'primereact/selectbutton';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import interactionPlugin from '@fullcalendar/interaction';
import listPlugin from '@fullcalendar/list';
import type { EventClickArg, DatesSetArg } from '@fullcalendar/core';
import { dailyReportService } from '../../services/limsService';
import ExportButtons from '../../components/common/ExportButtons';
import type { ExportColumn } from '../../utils/exportUtils';
import type { DailyReportDto } from '../../types/lims';
import { useLimsPermissions } from '../../hooks/useLimsPermissions';

/* ============================================================
   DAILY REPORT LIST PAGE
   PrimeReact Verona Layout – DataTable with filters & actions
   ============================================================ */
const DailyReportList: React.FC = () => {
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);
  const { canCreateReport, canEditReport, canDeleteReport } = useLimsPermissions();

  // Pagination & sorting
  const [data, setData] = useState<DailyReportDto[]>([]);
  const [totalRecords, setTotalRecords] = useState(0);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState(10);
  const [sortField, setSortField] = useState<string>('dueDate');
  const [sortOrder, setSortOrder] = useState<1 | -1 | 0>(-1);

  // Filters
  const [searchText, setSearchText] = useState('');

  // View mode: table or calendar
  const [viewMode, setViewMode] = useState<'table' | 'calendar'>('table');
  const calendarRef = useRef<FullCalendar>(null);
  const [calendarData, setCalendarData] = useState<DailyReportDto[]>([]);
  const [calendarLoading, setCalendarLoading] = useState(false);
  const [calendarRange, setCalendarRange] = useState<{ start: string; end: string } | null>(null);
  const [detailVisible, setDetailVisible] = useState(false);
  const [detailReport, setDetailReport] = useState<DailyReportDto | null>(null);

  const viewOptions = [
    { label: 'Tabel', value: 'table', icon: 'pi pi-table' },
    { label: 'Kalender', value: 'calendar', icon: 'pi pi-calendar' },
  ];

  // BreadCrumb
  const breadcrumbItems = [
    { label: 'LIMS', command: () => navigate('/dashboard') },
    { label: 'Laporan Harian' },
  ];
  const breadcrumbHome = { icon: 'pi pi-home', command: () => navigate('/dashboard') };

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const sortDir = sortOrder === 1 ? 'asc' : 'desc';
      const result = await dailyReportService.list(page - 1, rows, sortField, sortDir);
      setData(result.data || []);
      setTotalRecords((result.totalPage ?? 1) * rows);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { errorMessage?: string } } })?.response?.data?.errorMessage
        || 'LIMS API belum tersedia / unavailable';
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: msg,
        life: 4000,
      });
      setData([]);
    } finally {
      setLoading(false);
    }
  }, [page, rows, sortField, sortOrder]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Calendar data loading
  const loadCalendarData = useCallback(async (startDate: string, endDate: string) => {
    setCalendarLoading(true);
    try {
      const result = await dailyReportService.calendar(startDate, endDate);
      setCalendarData(result.data || []);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { errorMessage?: string } } })?.response?.data?.errorMessage
        || 'Gagal memuat data kalender';
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: msg,
        life: 4000,
      });
      setCalendarData([]);
    } finally {
      setCalendarLoading(false);
    }
  }, []);

  const handleDatesSet = useCallback(
    (arg: DatesSetArg) => {
      const start = arg.startStr.slice(0, 10);
      const end = arg.endStr.slice(0, 10);
      setCalendarRange({ start, end });
      loadCalendarData(start, end);
    },
    [loadCalendarData]
  );

  const calendarEvents = calendarData.map((report) => {
    const d = new Date(report.dueDate);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const isPast = d < today;
    return {
      id: String(report.id),
      title: report.name,
      start: report.dueDate,
      backgroundColor: isPast ? '#ef4444' : '#3b82f6',
      borderColor: isPast ? '#ef4444' : '#3b82f6',
      extendedProps: { report },
    };
  });

  const handleEventClick = (clickInfo: EventClickArg) => {
    const report = clickInfo.event.extendedProps.report as DailyReportDto;
    setDetailReport(report);
    setDetailVisible(true);
  };

  // Pagination handlers
  const onPage = (e: DataTablePageEvent) => {
    setPage((e.page ?? 0) + 1);
    setRows(e.rows);
  };

  const onSort = (e: DataTableSortEvent) => {
    setSortField(e.sortField as string);
    setSortOrder(e.sortOrder as 1 | -1 | 0);
    setPage(1);
  };

  // Delete
  const handleDelete = (report: DailyReportDto) => {
    confirmDialog({
      message: `Hapus laporan harian "${report.name}"?`,
      header: 'Konfirmasi Hapus',
      icon: 'pi pi-exclamation-triangle',
      acceptClassName: 'p-button-danger',
      acceptLabel: 'Hapus',
      rejectLabel: 'Batal',
      accept: async () => {
        try {
          await dailyReportService.delete(report.id);
          toast.current?.show({
            severity: 'success',
            summary: 'Berhasil',
            detail: 'Laporan harian berhasil dihapus',
            life: 3000,
          });
          loadData();
        } catch {
          toast.current?.show({
            severity: 'error',
            summary: 'Error',
            detail: 'Gagal menghapus laporan harian',
            life: 3000,
          });
        }
      },
    });
  };

  // Client-side search filter
  const filteredData = searchText
    ? data.filter(
        (r) =>
          r.name?.toLowerCase().includes(searchText.toLowerCase()) ||
          r.location?.toLowerCase().includes(searchText.toLowerCase()) ||
          r.unitName?.toLowerCase().includes(searchText.toLowerCase()) ||
          r.parameterName?.toLowerCase().includes(searchText.toLowerCase())
      )
    : data;

  // Column templates
  const dateBodyTemplate = (row: DailyReportDto) => {
    if (!row.dueDate) return <span className="text-500">—</span>;
    const d = new Date(row.dueDate);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const isPast = d < today;
    return (
      <Tag
        value={d.toLocaleDateString('id-ID', { day: '2-digit', month: 'short', year: 'numeric' })}
        severity={isPast ? 'danger' : 'info'}
        icon={isPast ? 'pi pi-exclamation-circle' : 'pi pi-calendar'}
        className="text-xs"
      />
    );
  };

  const locationBodyTemplate = (row: DailyReportDto) => (
    <span className="text-sm">{row.location || '—'}</span>
  );

  const parameterBodyTemplate = (row: DailyReportDto) => (
    <span className="text-sm">{row.parameterName || '—'}</span>
  );

  const unitBodyTemplate = (row: DailyReportDto) => (
    <span className="text-sm">{row.unitName || '—'}</span>
  );

  const descriptionBodyTemplate = (row: DailyReportDto) => {
    const desc = row.description || '—';
    const truncated = desc.length > 50 ? desc.substring(0, 50) + '...' : desc;
    return (
      <>
        <span className={`description-tooltip-${row.id} text-sm`}>{truncated}</span>
        {desc.length > 50 && (
          <Tooltip target={`.description-tooltip-${row.id}`} content={desc} position="top" />
        )}
      </>
    );
  };

  const actionBodyTemplate = (row: DailyReportDto) => (
    <div className="flex gap-1">
      <Button
        icon="pi pi-eye"
        rounded
        text
        severity="info"
        size="small"
        tooltip="Lihat Detail"
        tooltipOptions={{ position: 'top' }}
        onClick={() => navigate(`${row.id}`)}
      />
      {canEditReport && (
        <Button
          icon="pi pi-pencil"
          rounded
          text
          severity="warning"
          size="small"
          tooltip="Edit"
          tooltipOptions={{ position: 'top' }}
          onClick={() => navigate(`${row.id}/edit`)}
        />
      )}
      {canDeleteReport && (
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
      )}
    </div>
  );

  // Export CSV
  const dt = useRef<DataTable<DailyReportDto[]>>(null);
  const exportCSV = () => dt.current?.exportCSV();

  // Export configuration
  const exportColumns: ExportColumn[] = [
    { header: 'Nama', field: 'name' },
    { header: 'Deskripsi', field: 'description', formatter: (v) => (v as string) ?? '-' },
    { header: 'Tenggat', field: 'dueDate', formatter: (v) => v ? new Date(v as string).toLocaleDateString('id-ID') : '-' },
    { header: 'Lokasi', field: 'location', formatter: (v) => (v as string) ?? '-' },
    { header: 'Unit', field: 'unitName', formatter: (v) => (v as string) ?? '-' },
    { header: 'Parameter', field: 'parameterName', formatter: (v) => (v as string) ?? '-' },
  ];

  const fetchExportData = async (): Promise<Record<string, unknown>[]> => {
    const sortDir = sortOrder === 1 ? 'asc' : 'desc';
    const result = await dailyReportService.list(0, 10000, sortField, sortDir);
    return (result.data || []) as unknown as Record<string, unknown>[];
  };

  return (
    <div className="flex gap-4 flex-column">
      <Toast ref={toast} />
      <ConfirmDialog />

      {/* BreadCrumb */}
      <BreadCrumb model={breadcrumbItems} home={breadcrumbHome} className="border-none p-0 mb-1" />

      {/* Header */}
      <div className="flex flex-wrap gap-3 align-items-center justify-content-between">
        <div>
          <h2 className="m-0 text-2xl font-bold text-800">Laporan Harian</h2>
          <p className="mt-1 mb-0 text-sm text-500">Kelola laporan harian laboratorium</p>
        </div>
        <div className="flex gap-2 align-items-center">
          <SelectButton
            value={viewMode}
            onChange={(e) => e.value && setViewMode(e.value)}
            options={viewOptions}
            optionLabel="label"
            optionValue="value"
            itemTemplate={(option) => (
              <span className="flex align-items-center gap-2">
                <i className={option.icon} />
                <span>{option.label}</span>
              </span>
            )}
            pt={{
              button: { style: { padding: '0.85rem 1.5rem', fontSize: '1rem', borderRadius: '10px' } },
            }}
          />
          <ExportButtons
            permissionCode="DAILY_REPORT_MANAGE"
            columns={exportColumns}
            fetchData={fetchExportData}
            fileName="Laporan_Harian"
            title="Laporan Harian"
            toastRef={toast}
          />
          {canCreateReport && (
            <Button
              label="Tambah Laporan"
              icon="pi pi-plus"
              severity="info"
              outlined
              className="border-round-lg shadow-1 font-semibold"
              style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.85rem 1.5rem', fontSize: '1rem', border: '2px solid #3B82F6' }}
              onClick={() => navigate('create')}
            />
          )}
        </div>
      </div>

      {/* Table View */}
      {viewMode === 'table' && (
        <>
      {/* Filters */}
      <Card className="shadow-1 border-round-xl">
        <div className="flex flex-wrap gap-3 align-items-end">
          <div className="flex gap-1 flex-column">
            <label className="text-xs font-medium text-500">Pencarian</label>
            <span className="p-input-icon-left">
              <i className="pi pi-search" />
              <InputText
                value={searchText}
                onChange={(e) => setSearchText(e.target.value)}
                placeholder="Cari nama, lokasi, parameter..."
                className="w-20rem"
              />
            </span>
          </div>
          <Button
            label="Reset"
            icon="pi pi-filter-slash"
            severity="secondary"
            outlined
            size="small"
            onClick={() => {
              setSearchText('');
              setPage(1);
            }}
          />
          <Button
            icon="pi pi-refresh"
            severity="secondary"
            outlined
            size="small"
            tooltip="Refresh data"
            tooltipOptions={{ position: 'top' }}
            onClick={() => loadData()}
          />
        </div>
      </Card>

      {/* DataTable */}
      <Card className="shadow-1 border-round-xl">
        <DataTable
          ref={dt}
          value={filteredData}
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
          emptyMessage="Tidak ada data laporan harian"
          rowsPerPageOptions={[5, 10, 25, 50]}
          paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
          className="border-round-lg"
          rowHover
        >
          <Column
            field="name"
            header="Nama Laporan"
            sortable
            className="font-medium"
            style={{ minWidth: '200px' }}
          />
          <Column
            header="Tanggal Jatuh Tempo"
            body={dateBodyTemplate}
            sortable
            sortField="dueDate"
            style={{ width: '170px' }}
          />
          <Column
            header="Lokasi"
            body={locationBodyTemplate}
            style={{ width: '150px' }}
          />
          <Column
            header="Parameter"
            body={parameterBodyTemplate}
            style={{ width: '140px' }}
          />
          <Column
            header="Unit"
            body={unitBodyTemplate}
            style={{ width: '120px' }}
          />
          <Column
            header="Deskripsi"
            body={descriptionBodyTemplate}
            style={{ minWidth: '180px' }}
          />
          <Column
            header="Aksi"
            body={actionBodyTemplate}
            style={{ width: '140px' }}
            frozen
            alignFrozen="right"
          />
        </DataTable>
      </Card>
        </>
      )}

      {/* Calendar View */}
      {viewMode === 'calendar' && (
        <Card className="shadow-1 border-round-xl">
          <div className="relative">
            {calendarLoading && (
              <div className="absolute top-0 left-0 w-full h-full flex align-items-center justify-content-center z-5"
                style={{ backgroundColor: 'rgba(255,255,255,0.6)' }}
              >
                <ProgressSpinner style={{ width: '40px', height: '40px' }} />
              </div>
            )}
            <FullCalendar
              ref={calendarRef}
              plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin, listPlugin]}
              initialView="dayGridMonth"
              headerToolbar={{
                left: 'prev,next today',
                center: 'title',
                right: 'dayGridMonth,timeGridWeek,listWeek',
              }}
              locale="id"
              events={calendarEvents}
              eventClick={handleEventClick}
              datesSet={handleDatesSet}
              height="auto"
              dayMaxEvents={3}
              eventDisplay="block"
              buttonText={{
                today: 'Hari Ini',
                month: 'Bulan',
                week: 'Minggu',
                list: 'Daftar',
              }}
            />
          </div>

          {/* Legend */}
          <div className="flex gap-4 mt-3 flex-wrap">
            <div className="flex align-items-center gap-2">
              <div className="border-circle" style={{ width: '12px', height: '12px', backgroundColor: '#3b82f6' }} />
              <span className="text-sm text-600">Aktif / Mendatang</span>
            </div>
            <div className="flex align-items-center gap-2">
              <div className="border-circle" style={{ width: '12px', height: '12px', backgroundColor: '#ef4444' }} />
              <span className="text-sm text-600">Sudah Lewat</span>
            </div>
          </div>
        </Card>
      )}

      {/* Detail Dialog (Calendar) */}
      <Dialog
        header="Detail Laporan Harian"
        visible={detailVisible}
        style={{ width: '500px' }}
        modal
        onHide={() => { setDetailVisible(false); setDetailReport(null); }}
        footer={
          <div className="flex justify-content-end gap-2">
            {canDeleteReport && (
              <Button
                label="Hapus"
                icon="pi pi-trash"
                severity="danger"
                outlined
                onClick={() => {
                  if (!detailReport) return;
                  setDetailVisible(false);
                  handleDelete(detailReport);
                }}
              />
            )}
            <Button
              label="Lihat Detail"
              icon="pi pi-eye"
              severity="info"
              onClick={() => {
                if (detailReport) navigate(`${detailReport.id}`);
              }}
            />
            {canEditReport && (
              <Button
                label="Edit"
                icon="pi pi-pencil"
                onClick={() => {
                  if (detailReport) navigate(`${detailReport.id}/edit`);
                }}
              />
            )}
          </div>
        }
      >
        {detailReport && (
          <div className="flex flex-column gap-3">
            <div>
              <label className="text-sm font-semibold text-500">Nama Laporan</label>
              <p className="m-0 text-lg font-medium">{detailReport.name}</p>
            </div>
            {detailReport.description && (
              <div>
                <label className="text-sm font-semibold text-500">Deskripsi</label>
                <p className="m-0">{detailReport.description}</p>
              </div>
            )}
            <div className="grid">
              <div className="col-6">
                <label className="text-sm font-semibold text-500">Tanggal Jatuh Tempo</label>
                <p className="m-0">
                  {new Date(detailReport.dueDate).toLocaleDateString('id-ID', {
                    day: '2-digit',
                    month: 'long',
                    year: 'numeric',
                  })}
                </p>
              </div>
              <div className="col-6">
                <label className="text-sm font-semibold text-500">Lokasi</label>
                <p className="m-0">{detailReport.location || '—'}</p>
              </div>
            </div>
            <div className="grid">
              <div className="col-6">
                <label className="text-sm font-semibold text-500">Unit</label>
                <p className="m-0">{detailReport.unitName || '—'}</p>
              </div>
              <div className="col-6">
                <label className="text-sm font-semibold text-500">Parameter</label>
                <p className="m-0">{detailReport.parameterName || '—'}</p>
              </div>
            </div>
          </div>
        )}
      </Dialog>
    </div>
  );
};

export default DailyReportList;
