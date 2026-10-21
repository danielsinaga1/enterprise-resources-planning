import { useEffect, useState, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { Card } from 'primereact/card';
import { Dialog } from 'primereact/dialog';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { Toast } from 'primereact/toast';
import { InputNumber } from 'primereact/inputnumber';
import { ColorPicker } from 'primereact/colorpicker';
import { Dropdown } from 'primereact/dropdown';
import {
  masterAreaService,
  masterCategoryService,
  masterClassificationService,
  masterRootCauseService,
  masterRegionService,
  masterOriginDeptService,
  masterDesignationDeptService,
  masterJsoLeaderService,
  masterJsoMemberService,
  masterJobTitleService,
  masterJsoLocationService,
  masterDivisionService,
  masterLimsParameterService,
  masterLimsTestTypeService,
  masterLimsMethodService,
  masterLimsStandardService,
  masterLimsUnitService,
  masterLimsDepartmentService,
  masterLimsResultService,
  masterLimsCompoundService,
} from '../../../services';
import ExportButtons from '../../../components/common/ExportButtons';
import ChemicalFormulaInput from '../../../components/common/ChemicalFormulaInput';
import { serverMessage } from '../../../utils/apiError';
import type { ExportColumn } from '../../../utils/exportUtils';
import { pubchemService, type PubChemData } from '../../../services/integrationService';
import { ProgressSpinner } from 'primereact/progressspinner';

/* ============================================================
   CONFIGURATION — each master data type
   ============================================================ */
interface FieldDef {
  key: string;
  label: string;
  type: 'text' | 'number' | 'decimal' | 'color' | 'textarea' | 'select' | 'formula';
  required?: boolean;
  /** Keterangan singkat di bawah input — dipakai untuk aturan yang tidak terbaca dari label. */
  hint?: string;
  /** For 'select' type: async function to load dropdown options */
  loadOptions?: () => Promise<{ label: string; value: unknown }[]>;
  /** For 'select' type: field from row data to display in DataTable instead of the raw value */
  displayKey?: string;
}

interface MasterConfig {
  key: string;
  label: string;
  icon: string;
  path: string;
  fields: FieldDef[];
  service: {
    list: () => Promise<unknown[]>;
    create: (p: Record<string, unknown>) => Promise<unknown>;
    update: (id: number, p: Record<string, unknown>) => Promise<unknown>;
    remove: (id: number) => Promise<void>;
  };
}

const CONFIGS: MasterConfig[] = [
  {
    key: 'areas',
    label: 'Area',
    icon: 'pi pi-map-marker',
    path: 'areas',
    fields: [{ key: 'name', label: 'Nama Area', type: 'text', required: true }],
    service: masterAreaService as MasterConfig['service'],
  },
  {
    key: 'categories',
    label: 'Kategori Insiden',
    icon: 'pi pi-tag',
    path: 'categories',
    fields: [
      { key: 'name', label: 'Nama Kategori', type: 'text', required: true },
      { key: 'description', label: 'Deskripsi', type: 'textarea' },
    ],
    service: masterCategoryService as MasterConfig['service'],
  },
  {
    key: 'classifications',
    label: 'Klasifikasi Insiden',
    icon: 'pi pi-sort-amount-up',
    path: 'classifications',
    fields: [
      { key: 'name', label: 'Nama Klasifikasi', type: 'text', required: true },
      { key: 'level', label: 'Level', type: 'number', required: true },
      { key: 'color', label: 'Warna', type: 'color' },
    ],
    service: masterClassificationService as MasterConfig['service'],
  },
  {
    key: 'root-causes',
    label: 'Akar Masalah',
    icon: 'pi pi-sitemap',
    path: 'root-causes',
    fields: [{ key: 'name', label: 'Nama Akar Masalah', type: 'text', required: true }],
    service: masterRootCauseService as MasterConfig['service'],
  },
  {
    key: 'regions',
    label: 'Region',
    icon: 'pi pi-globe',
    path: 'regions',
    fields: [{ key: 'name', label: 'Nama Region', type: 'text', required: true }],
    service: masterRegionService as MasterConfig['service'],
  },
  {
    key: 'origin-departments',
    label: 'Departemen Asal',
    icon: 'pi pi-building',
    path: 'origin-departments',
    fields: [
      { key: 'name', label: 'Nama Departemen', type: 'text', required: true },
      { key: 'code', label: 'Kode', type: 'text' },
    ],
    service: masterOriginDeptService as MasterConfig['service'],
  },
  {
    key: 'designation-departments',
    label: 'Departemen Tujuan',
    icon: 'pi pi-send',
    path: 'designation-departments',
    fields: [
      { key: 'name', label: 'Nama Departemen', type: 'text', required: true },
      { key: 'code', label: 'Kode', type: 'text' },
    ],
    service: masterDesignationDeptService as MasterConfig['service'],
  },
  {
    key: 'jso-leaders',
    label: 'Leader JSO',
    icon: 'pi pi-star',
    path: 'jso-leaders',
    fields: [
      { key: 'name', label: 'Nama Leader', type: 'text', required: true },
      { key: 'npp', label: 'NPP', type: 'text' },
      { key: 'department', label: 'Departemen', type: 'text' },
      { key: 'position', label: 'Jabatan', type: 'text' },
    ],
    service: masterJsoLeaderService as MasterConfig['service'],
  },
  {
    key: 'jso-members',
    label: 'Member JSO',
    icon: 'pi pi-users',
    path: 'jso-members',
    fields: [
      { key: 'name', label: 'Nama Member', type: 'text', required: true },
      { key: 'npp', label: 'NPP', type: 'text' },
      { key: 'department', label: 'Departemen', type: 'text' },
      { key: 'position', label: 'Jabatan', type: 'text' },
    ],
    service: masterJsoMemberService as MasterConfig['service'],
  },
  {
    key: 'job-titles',
    label: 'Job Title',
    icon: 'pi pi-id-card',
    path: 'job-titles',
    fields: [
      { key: 'name', label: 'Nama Job Title', type: 'text', required: true },
    ],
    service: masterJobTitleService as MasterConfig['service'],
  },
  {
    key: 'jso-locations',
    label: 'Lokasi JSO',
    icon: 'pi pi-map',
    path: 'jso-locations',
    fields: [
      { key: 'name', label: 'Nama Lokasi', type: 'text', required: true },
    ],
    service: masterJsoLocationService as MasterConfig['service'],
  },
  {
    key: 'divisions',
    label: 'Divisi',
    icon: 'pi pi-sitemap',
    path: 'divisions',
    fields: [
      { key: 'name', label: 'Nama Divisi', type: 'text', required: true },
      { key: 'code', label: 'Kode', type: 'text' },
    ],
    service: masterDivisionService as MasterConfig['service'],
  },
  // --- LIMS Master Data ---
  {
    key: 'lims-parameters',
    label: 'Parameter Uji',
    icon: 'pi pi-filter',
    path: 'lims-parameters',
    fields: [{ key: 'name', label: 'Nama Parameter', type: 'text', required: true }],
    service: masterLimsParameterService as MasterConfig['service'],
  },
  {
    key: 'lims-test-types',
    label: 'Jenis Uji',
    icon: 'pi pi-list',
    path: 'lims-test-types',
    fields: [
      { key: 'name', label: 'Nama Jenis Uji', type: 'formula', required: true },
      {
        key: 'parameterId',
        label: 'Parameter',
        type: 'select',
        required: true,
        displayKey: 'parameterName',
        loadOptions: async () => {
          const items = await masterLimsParameterService.list();
          return items.map((p) => ({ label: p.name, value: p.id }));
        },
      },
    ],
    service: masterLimsTestTypeService as MasterConfig['service'],
  },
  {
    key: 'lims-methods',
    label: 'Metode',
    icon: 'pi pi-cog',
    path: 'lims-methods',
    fields: [{ key: 'name', label: 'Nama Metode', type: 'formula', required: true }],
    service: masterLimsMethodService as MasterConfig['service'],
  },
  {
    key: 'lims-standards',
    label: 'Standar',
    icon: 'pi pi-check-square',
    path: 'lims-standards',
    fields: [
      { key: 'measure', label: 'Ukuran Standar', type: 'formula', required: true },
      {
        key: 'limitType',
        label: 'Tipe Batas',
        type: 'select',
        required: true,
        hint: 'Menentukan cara hasil uji dinilai. Kualitatif tidak dinilai otomatis.',
        loadOptions: async () => [
          { label: 'Kualitatif (tidak dinilai otomatis)', value: 'QUALITATIVE' },
          { label: 'Rentang (batas bawah s/d batas atas)', value: 'RANGE' },
          { label: 'Maksimum (hasil ≤ batas atas)', value: 'MAX' },
          { label: 'Minimum (hasil ≥ batas bawah)', value: 'MIN' },
        ],
      },
      { key: 'lowerLimit', label: 'Batas Bawah', type: 'decimal', hint: 'Wajib untuk tipe Rentang dan Minimum.' },
      { key: 'upperLimit', label: 'Batas Atas', type: 'decimal', hint: 'Wajib untuk tipe Rentang dan Maksimum.' },
      { key: 'targetValue', label: 'Nilai Target', type: 'decimal', hint: 'Hanya acuan tampilan & tren; tidak memengaruhi lulus/tidak.' },
      { key: 'decimalPlaces', label: 'Jumlah Desimal', type: 'number', hint: 'Untuk penyajian hasil (0-6).' },
      {
        key: 'methodId',
        label: 'Metode',
        type: 'select',
        required: true,
        displayKey: 'methodName',
        loadOptions: async () => {
          const items = await masterLimsMethodService.list();
          return items.map((m) => ({ label: m.name, value: m.id }));
        },
      },
    ],
    service: masterLimsStandardService as MasterConfig['service'],
  },
  {
    key: 'lims-units',
    label: 'Satuan',
    icon: 'pi pi-calculator',
    path: 'lims-units',
    fields: [{ key: 'name', label: 'Nama Satuan', type: 'formula', required: true }],
    service: masterLimsUnitService as MasterConfig['service'],
  },
  {
    key: 'lims-departments',
    label: 'Dept. Lab',
    icon: 'pi pi-building',
    path: 'lims-departments',
    fields: [{ key: 'name', label: 'Nama Departemen', type: 'text', required: true }],
    service: masterLimsDepartmentService as MasterConfig['service'],
  },
  {
    key: 'lims-results',
    label: 'Hasil Lab',
    icon: 'pi pi-chart-bar',
    path: 'lims-results',
    fields: [{ key: 'name', label: 'Nama Hasil', type: 'text', required: true }],
    service: masterLimsResultService as MasterConfig['service'],
  },
  {
    key: 'lims-compounds',
    label: 'Senyawa Kimia',
    icon: 'pi pi-slack',
    path: 'lims-compounds',
    fields: [
      { key: 'name', label: 'Nama Senyawa', type: 'formula', required: true },
      { key: 'formula', label: 'Rumus Kimia', type: 'formula', required: true },
      { key: 'casNumber', label: 'CAS Number', type: 'text' },
      {
        key: 'category',
        label: 'Kategori',
        type: 'select',
        loadOptions: async () => [
          { label: 'Gas', value: 'Gas' },
          { label: 'Liquid', value: 'Liquid' },
          { label: 'Solid', value: 'Solid' },
          { label: 'Ion', value: 'Ion' },
          { label: 'Organic', value: 'Organic' },
        ],
      },
      { key: 'description', label: 'Deskripsi', type: 'textarea' },
    ],
    service: masterLimsCompoundService as MasterConfig['service'],
  },
];

/* ============================================================
   COMPONENT
   ============================================================ */
const MasterDataPage = () => {
  const { type } = useParams<{ type: string }>();
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);

  const config = CONFIGS.find((c) => c.path === type) ?? CONFIGS[0];

  // Data
  const [data, setData] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchText, setSearchText] = useState('');

  // CRUD dialog
  const [dialogVisible, setDialogVisible] = useState(false);
  const [editingItem, setEditingItem] = useState<Record<string, unknown> | null>(null);
  const [formData, setFormData] = useState<Record<string, unknown>>({});
  const [saving, setSaving] = useState(false);
  const [selectOptions, setSelectOptions] = useState<Record<string, { label: string; value: unknown }[]>>({});

  // PubChem enrichment dialog (Senyawa Kimia only)
  const [pubchemVisible, setPubchemVisible] = useState(false);
  const [pubchemLoading, setPubchemLoading] = useState(false);
  const [pubchemData, setPubchemData] = useState<PubChemData | null>(null);
  const [pubchemItem, setPubchemItem] = useState<Record<string, unknown> | null>(null);

  const openPubChem = async (item: Record<string, unknown>) => {
    setPubchemItem(item);
    setPubchemData(null);
    setPubchemVisible(true);
    setPubchemLoading(true);
    try {
      const data = await pubchemService.enrich(item.id as number);
      setPubchemData(data);
    } catch {
      // data stays null — dialog shows "tidak ditemukan"
    } finally {
      setPubchemLoading(false);
    }
  };

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await config.service.list();
      setData(result as Record<string, unknown>[]);
    } catch {
      toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal memuat data', life: 3000 });
    } finally {
      setLoading(false);
    }
  }, [config]);

  useEffect(() => {
    loadData();
    setSearchText('');
  }, [loadData]);

  // Navigate to default if no type
  useEffect(() => {
    if (!type) navigate('/dashboard/settings/master-data/areas', { replace: true });
  }, [type, navigate]);

  const loadSelectOptions = async () => {
    const selectFields = config.fields.filter((f) => f.type === 'select' && f.loadOptions);
    if (selectFields.length === 0) return;
    const opts: Record<string, { label: string; value: unknown }[]> = {};
    await Promise.all(
      selectFields.map(async (f) => {
        opts[f.key] = await f.loadOptions!();
      })
    );
    setSelectOptions(opts);
  };

  const openCreate = () => {
    setEditingItem(null);
    const initial: Record<string, unknown> = {};
    config.fields.forEach((f) => {
      // Desimal dibiarkan kosong: 0 adalah batas yang sah, jadi tidak boleh
      // terisi sendiri tanpa disadari.
      if (f.type === 'number') initial[f.key] = 0;
      else if (f.type === 'decimal') initial[f.key] = null;
      else initial[f.key] = '';
    });
    setFormData(initial);
    loadSelectOptions();
    setDialogVisible(true);
  };

  const openEdit = (item: Record<string, unknown>) => {
    setEditingItem(item);
    const initial: Record<string, unknown> = {};
    config.fields.forEach((f) => (initial[f.key] = item[f.key] ?? ''));
    setFormData(initial);
    loadSelectOptions();
    setDialogVisible(true);
  };

  const handleSave = async () => {
    // Validate required
    for (const field of config.fields) {
      if (field.required && !formData[field.key]) {
        toast.current?.show({ severity: 'warn', summary: 'Validasi', detail: `${field.label} wajib diisi`, life: 3000 });
        return;
      }
    }

    setSaving(true);
    try {
      if (editingItem) {
        await config.service.update(editingItem.id as number, formData);
        toast.current?.show({ severity: 'success', summary: 'Berhasil', detail: 'Data berhasil diperbarui', life: 3000 });
      } else {
        await config.service.create(formData);
        toast.current?.show({ severity: 'success', summary: 'Berhasil', detail: 'Data berhasil ditambahkan', life: 3000 });
      }
      setDialogVisible(false);
      loadData();
    } catch (err) {
      // Validasi batas spesifikasi ditolak backend dengan pesan yang spesifik —
      // menampilkan "Gagal menyimpan data" saja membuatnya mustahil diperbaiki.
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: serverMessage(err) ?? 'Gagal menyimpan data',
        life: 5000,
      });
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = (item: Record<string, unknown>) => {
    confirmDialog({
      message: `Hapus "${item.name}"?`,
      header: 'Konfirmasi Hapus',
      icon: 'pi pi-exclamation-triangle',
      acceptClassName: 'p-button-danger',
      acceptLabel: 'Hapus',
      rejectLabel: 'Batal',
      accept: async () => {
        try {
          await config.service.remove(item.id as number);
          toast.current?.show({ severity: 'success', summary: 'Berhasil', detail: 'Data berhasil dihapus', life: 3000 });
          loadData();
        } catch {
          toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal menghapus data', life: 3000 });
        }
      },
    });
  };

  // Filter
  const filtered = searchText
    ? data.filter((d) => String(d.name ?? '').toLowerCase().includes(searchText.toLowerCase()))
    : data;

  // Export configuration (dynamic from config fields)
  const exportColumns: ExportColumn[] = config.fields
    .filter((f) => f.type !== 'color')
    .map((f) => ({
      header: f.label,
      field: f.displayKey ?? f.key,
      formatter: (v: unknown) => (v != null ? String(v) : '-'),
    }));

  const fetchExportData = async (): Promise<Record<string, unknown>[]> => {
    const result = await config.service.list();
    return result as Record<string, unknown>[];
  };

  const actionBody = (row: Record<string, unknown>) => (
    <div className="flex gap-1">
      {config.key === 'lims-compounds' && (
        <Button icon="pi pi-search-plus" rounded text severity="info" size="small" tooltip="Data PubChem" tooltipOptions={{ position: 'top' }} onClick={() => openPubChem(row)} />
      )}
      <Button icon="pi pi-pencil" rounded text severity="warning" size="small" tooltip="Edit" tooltipOptions={{ position: 'top' }} onClick={() => openEdit(row)} />
      <Button icon="pi pi-trash" rounded text severity="danger" size="small" tooltip="Hapus" tooltipOptions={{ position: 'top' }} onClick={() => handleDelete(row)} />
    </div>
  );

  const renderField = (field: FieldDef) => {
    const val = formData[field.key];
    switch (field.type) {
      case 'number':
        return (
          <InputNumber
            value={val as number}
            onValueChange={(e) => setFormData({ ...formData, [field.key]: e.value })}
            className="w-full"
          />
        );
      case 'decimal':
        // Batas spesifikasi kerap berupa pecahan kecil (mis. 0,005 mg/L), jadi
        // desimal harus boleh sampai 6 angka dan boleh dikosongkan.
        return (
          <InputNumber
            value={(val === '' || val === undefined) ? null : (val as number)}
            onValueChange={(e) => setFormData({ ...formData, [field.key]: e.value ?? null })}
            minFractionDigits={0}
            maxFractionDigits={6}
            useGrouping={false}
            className="w-full"
          />
        );
      case 'color':
        return (
          <div className="flex gap-2 align-items-center">
            <ColorPicker
              value={String(val ?? '').replace('#', '')}
              onChange={(e) => setFormData({ ...formData, [field.key]: '#' + (e.value ?? '') })}
            />
            <InputText
              value={String(val ?? '')}
              onChange={(e) => setFormData({ ...formData, [field.key]: e.target.value })}
              placeholder="#FF0000"
              className="w-8rem"
            />
          </div>
        );
      case 'select':
        return (
          <Dropdown
            value={val}
            options={selectOptions[field.key] ?? []}
            onChange={(e) => setFormData({ ...formData, [field.key]: e.value })}
            placeholder={`Pilih ${field.label}`}
            className="w-full"
            filter
          />
        );
      case 'textarea':
        return (
          <textarea
            value={String(val ?? '')}
            onChange={(e) => setFormData({ ...formData, [field.key]: e.target.value })}
            className="w-full p-inputtext p-component"
            rows={3}
          />
        );
      case 'formula':
        return (
          <ChemicalFormulaInput
            value={String(val ?? '')}
            onChange={(v) => setFormData({ ...formData, [field.key]: v })}
            placeholder={`Masukkan ${field.label}...`}
          />
        );
      default:
        return (
          <InputText
            value={String(val ?? '')}
            onChange={(e) => setFormData({ ...formData, [field.key]: e.target.value })}
            className="w-full"
          />
        );
    }
  };

  const dialogFooter = (
    <div className="flex gap-2 justify-content-end">
      <Button label="Batal" icon="pi pi-times" severity="secondary" outlined onClick={() => setDialogVisible(false)} disabled={saving} />
      <Button label="Simpan" icon="pi pi-check" onClick={handleSave} loading={saving} />
    </div>
  );

  return (
    <div className="flex gap-4 flex-column">
      <Toast ref={toast} />
      <ConfirmDialog />

      {/* Header */}
      <div className="flex flex-wrap gap-3 align-items-center justify-content-between">
        <div>
          <h2 className="m-0 text-2xl font-bold text-800">Master Data</h2>
          <p className="mt-1 mb-0 text-sm text-500">Kelola data referensi sistem IRMS</p>
        </div>
      </div>

      <div className="flex gap-4" style={{ minHeight: '600px' }}>
        {/* Sidebar Navigation */}
        <Card className="shadow-1 border-round-xl" style={{ width: '260px', flexShrink: 0 }}>
          <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {CONFIGS.map((c) => {
              const active = c.path === config.path;
              return (
                <li key={c.key}>
                  <button
                    onClick={() => navigate(`/dashboard/settings/master-data/${c.path}`)}
                    style={{
                      width: '100%',
                      border: 'none',
                      cursor: 'pointer',
                      borderRadius: '8px',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '10px',
                      padding: '10px 12px',
                      marginBottom: '4px',
                      fontSize: '0.875rem',
                      fontWeight: active ? 600 : 400,
                      color: active ? '#4338ca' : '#64748b',
                      background: active ? '#eef2ff' : 'transparent',
                      transition: 'all 0.2s',
                    }}
                    onMouseEnter={(e) => {
                      if (!active) e.currentTarget.style.background = '#f1f5f9';
                    }}
                    onMouseLeave={(e) => {
                      if (!active) e.currentTarget.style.background = 'transparent';
                    }}
                  >
                    <i className={c.icon} style={{ fontSize: '1rem' }} />
                    <span>{c.label}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        </Card>

        {/* Main Content */}
        <div className="flex-1">
          <Card className="shadow-1 border-round-xl">
            {/* Toolbar */}
            <div className="flex flex-wrap gap-3 mb-4 align-items-center justify-content-between">
              <div className="flex gap-2 align-items-center">
                <i className={config.icon} style={{ fontSize: '1.25rem', color: '#4338ca' }} />
                <h3 className="m-0 text-xl font-semibold text-800">{config.label}</h3>
                <span className="text-sm text-500">({filtered.length} data)</span>
              </div>
              <div className="flex gap-2">
                <span className="p-input-icon-left">
                  <i className="pi pi-search" />
                  <InputText
                    value={searchText}
                    onChange={(e) => setSearchText(e.target.value)}
                    placeholder="Cari..."
                    className="w-14rem"
                  />
                </span>
                <ExportButtons
                  permissionCode="SETTINGS_MANAGE"
                  columns={exportColumns}
                  fetchData={fetchExportData}
                  fileName={`Master_${config.label.replace(/\s+/g, '_')}`}
                  title={`Master Data — ${config.label}`}
                  toastRef={toast}
                />
                <Button label="Tambah" icon="pi pi-plus" size="small" severity="info" outlined className="border-round-lg font-semibold" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.5rem 1rem', border: '2px solid #3B82F6' }} onClick={openCreate} />
              </div>
            </div>

            {/* Table */}
            <DataTable
              value={filtered}
              loading={loading}
              size="small"
              stripedRows
              responsiveLayout="scroll"
              emptyMessage="Tidak ada data"
              paginator
              rows={10}
              rowsPerPageOptions={[5, 10, 25]}
            >
              <Column field="id" header="ID" sortable style={{ width: '70px' }} />
              {config.fields.map((f) => {
                if (f.type === 'color') {
                  return (
                    <Column
                      key={f.key}
                      field={f.key}
                      header={f.label}
                      sortable
                      body={(row) => (
                        <div className="flex gap-2 align-items-center">
                          <span
                            style={{
                              display: 'inline-block',
                              width: '20px',
                              height: '20px',
                              borderRadius: '4px',
                              background: String(row[f.key] ?? '#ccc'),
                              border: '1px solid #e2e8f0',
                            }}
                          />
                          <span className="text-sm">{row[f.key]}</span>
                        </div>
                      )}
                      style={{ width: '140px' }}
                    />
                  );
                }
                if (f.type === 'select' && f.displayKey) {
                  return (
                    <Column key={f.key} field={f.displayKey} header={f.label} sortable />
                  );
                }
                return (
                  <Column key={f.key} field={f.key} header={f.label} sortable />
                );
              })}
              <Column header="Aksi" body={actionBody} style={{ width: '120px' }} frozen alignFrozen="right" />
            </DataTable>
          </Card>
        </div>
      </div>

      {/* PubChem Enrichment Dialog */}
      <Dialog
        header={`Data PubChem — ${String(pubchemItem?.name ?? '')}`}
        visible={pubchemVisible}
        style={{ width: '560px' }}
        onHide={() => setPubchemVisible(false)}
        modal
      >
        {pubchemLoading ? (
          <div className="flex justify-content-center py-5">
            <ProgressSpinner strokeWidth="3" style={{ width: '40px', height: '40px' }} />
          </div>
        ) : !pubchemData ? (
          <div className="flex flex-column align-items-center gap-2 py-5 text-500">
            <i className="pi pi-info-circle" style={{ fontSize: '2rem' }} />
            <span className="text-sm">Senyawa tidak ditemukan di PubChem atau CAS number belum diisi.</span>
          </div>
        ) : (
          <div className="flex flex-column gap-3 pt-2">
            {/* Identity */}
            <div className="surface-50 border-round p-3">
              <p className="text-xs font-semibold text-500 mt-0 mb-2 uppercase tracking-wide">Identitas</p>
              <div className="grid">
                {pubchemData.cid && <><div className="col-5 text-sm text-600">PubChem CID</div><div className="col-7 text-sm font-medium">{pubchemData.cid}</div></>}
                {pubchemData.iupacName && <><div className="col-5 text-sm text-600">Nama IUPAC</div><div className="col-7 text-sm font-medium" style={{ wordBreak: 'break-all' }}>{pubchemData.iupacName}</div></>}
                {pubchemData.molecularFormula && <><div className="col-5 text-sm text-600">Rumus Molekul</div><div className="col-7 text-sm font-medium">{pubchemData.molecularFormula}</div></>}
                {pubchemData.molecularWeight != null && <><div className="col-5 text-sm text-600">Berat Molekul</div><div className="col-7 text-sm font-medium">{pubchemData.molecularWeight} g/mol</div></>}
                {pubchemData.inchiKey && <><div className="col-5 text-sm text-600">InChIKey</div><div className="col-7 text-sm font-medium" style={{ wordBreak: 'break-all', fontSize: '0.75rem' }}>{pubchemData.inchiKey}</div></>}
              </div>
            </div>

            {/* GHS Hazard */}
            {(pubchemData.ghsPictograms?.length || pubchemData.ghsSignalWord || pubchemData.hazardStatements?.length) && (
              <div className="border-round p-3" style={{ background: '#fff7ed', border: '1px solid #fed7aa' }}>
                <p className="text-xs font-semibold mt-0 mb-2 uppercase" style={{ color: '#ea580c' }}>
                  <i className="pi pi-exclamation-triangle mr-1" />
                  Klasifikasi GHS / Bahaya
                </p>
                {pubchemData.ghsSignalWord && (
                  <span className="inline-block mb-2 px-2 py-1 border-round text-xs font-bold"
                    style={{ background: pubchemData.ghsSignalWord === 'Danger' ? '#fef2f2' : '#fefce8', color: pubchemData.ghsSignalWord === 'Danger' ? '#dc2626' : '#ca8a04', border: `1px solid ${pubchemData.ghsSignalWord === 'Danger' ? '#fca5a5' : '#fde047'}` }}>
                    {pubchemData.ghsSignalWord}
                  </span>
                )}
                {pubchemData.ghsPictograms && pubchemData.ghsPictograms.length > 0 && (
                  <div className="flex flex-wrap gap-1 mb-2">
                    {pubchemData.ghsPictograms.map((p) => (
                      <span key={p} className="px-2 py-1 border-round text-xs" style={{ background: '#fee2e2', color: '#991b1b', border: '1px solid #fca5a5' }}>{p}</span>
                    ))}
                  </div>
                )}
                {pubchemData.hazardStatements && pubchemData.hazardStatements.length > 0 && (
                  <ul className="m-0 pl-4" style={{ fontSize: '0.8rem', color: '#7c2d12' }}>
                    {pubchemData.hazardStatements.slice(0, 8).map((s, i) => <li key={i}>{s}</li>)}
                    {pubchemData.hazardStatements.length > 8 && <li className="text-500">+{pubchemData.hazardStatements.length - 8} lainnya...</li>}
                  </ul>
                )}
              </div>
            )}

            <a href={`https://pubchem.ncbi.nlm.nih.gov/compound/${pubchemData.cid}`} target="_blank" rel="noopener noreferrer"
              className="text-sm" style={{ color: '#4338ca' }}>
              <i className="pi pi-external-link mr-1" />
              Lihat di PubChem
            </a>
          </div>
        )}
      </Dialog>

      {/* Create/Edit Dialog */}
      <Dialog
        header={editingItem ? `Edit ${config.label}` : `Tambah ${config.label}`}
        visible={dialogVisible}
        style={{ width: '560px' }}
        onHide={() => setDialogVisible(false)}
        footer={dialogFooter}
        modal
      >
        <div className="flex gap-3 pt-2 flex-column">
          {config.fields.map((field) => (
            <div key={field.key} className="flex gap-1 flex-column">
              <label className="text-sm font-medium text-700">
                {field.label}
                {field.required && <span className="ml-1 text-red-500">*</span>}
              </label>
              {renderField(field)}
              {field.hint && <small className="text-500">{field.hint}</small>}
            </div>
          ))}
        </div>
      </Dialog>
    </div>
  );
};

export default MasterDataPage;
