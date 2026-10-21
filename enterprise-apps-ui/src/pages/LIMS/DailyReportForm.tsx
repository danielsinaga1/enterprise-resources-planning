import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { InputText } from 'primereact/inputtext';
import { InputTextarea } from 'primereact/inputtextarea';
import { Dropdown } from 'primereact/dropdown';
import { Calendar } from 'primereact/calendar';
import { Button } from 'primereact/button';
import { Toast } from 'primereact/toast';
import { TabView, TabPanel } from 'primereact/tabview';
import { ProgressSpinner } from 'primereact/progressspinner';
import { dailyReportService, labConfigService } from '../../services/limsService';
import { toLocalISODate } from '../../utils/date';
import type { DailyReportPayload, LabParameter, LabUnit } from '../../types/lims';

/* ============================================================
   DAILY REPORT FORM (Create / Edit)
   Modern layout matching Safety Observation / Incident Report
   ============================================================ */
const DailyReportForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);

  const [loading, setLoading] = useState(false);
  const [fetching, setFetching] = useState(isEdit);
  const [parameters, setParameters] = useState<LabParameter[]>([]);
  const [units, setUnits] = useState<LabUnit[]>([]);
  const [form, setForm] = useState<DailyReportPayload>({
    name: '',
    dueDate: toLocalISODate(new Date()),
    description: '',
    location: '',
    unitId: undefined,
    parameterId: undefined,
  });

  const loadData = useCallback(async () => {
    setFetching(true);
    try {
      const configPromise = labConfigService.getMasterData().catch(() => null);

      if (isEdit && id) {
        const [reportRes, configRes] = await Promise.all([
          dailyReportService.get(Number(id)),
          configPromise,
        ]);
        const r = reportRes.data;
        setForm({
          name: r.name,
          dueDate: r.dueDate,
          description: r.description || '',
          location: r.location || '',
          unitId: r.unitId || undefined,
          parameterId: r.parameterId || undefined,
        });
        if (configRes) {
          const data = configRes.data as Record<string, unknown[]>;
          if (data?.parameters) setParameters(data.parameters as LabParameter[]);
          if (data?.units) setUnits(data.units as LabUnit[]);
        }
      } else {
        const configRes = await configPromise;
        if (configRes) {
          const data = configRes.data as Record<string, unknown[]>;
          if (data?.parameters) setParameters(data.parameters as LabParameter[]);
          if (data?.units) setUnits(data.units as LabUnit[]);
        }
      }
    } catch {
      if (isEdit) {
        toast.current?.show({
          severity: 'error',
          summary: 'Error',
          detail: 'Gagal memuat data laporan',
          life: 3000,
        });
      }
    } finally {
      setFetching(false);
    }
  }, [id, isEdit]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const updateField = (field: string, value: unknown) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const handleSubmit = async () => {
    if (!form.name || !form.dueDate) {
      toast.current?.show({
        severity: 'warn',
        summary: 'Validasi',
        detail: 'Nama dan tanggal jatuh tempo wajib diisi',
        life: 3000,
      });
      return;
    }
    setLoading(true);
    try {
      if (isEdit && id) {
        await dailyReportService.update(Number(id), form);
        toast.current?.show({
          severity: 'success',
          summary: 'Berhasil',
          detail: 'Laporan harian berhasil diperbarui',
          life: 2000,
        });
      } else {
        await dailyReportService.create(form);
        toast.current?.show({
          severity: 'success',
          summary: 'Berhasil',
          detail: 'Laporan harian berhasil dibuat',
          life: 2000,
        });
      }
      setTimeout(() => navigate('/dashboard/lims/daily-reports'), 800);
    } catch {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: `Gagal ${isEdit ? 'memperbarui' : 'membuat'} laporan`,
        life: 3000,
      });
    } finally {
      setLoading(false);
    }
  };

  if (fetching) {
    return (
      <div className="flex align-items-center justify-content-center" style={{ minHeight: '40vh' }}>
        <ProgressSpinner strokeWidth="3" />
      </div>
    );
  }

  const parameterOptions = parameters.map((p) => ({ label: p.name, value: p.id }));
  const unitOptions = units.map((u) => ({ label: u.name, value: u.id }));

  return (
    <div className="flex flex-column gap-4 p-3 md:p-4" style={{ maxWidth: 1100, margin: '0 auto' }}>
      <Toast ref={toast} />

      {/* ── Header ── */}
      <div className="border-round-2xl overflow-hidden shadow-3" style={{ background: 'linear-gradient(135deg, #1e3a5f 0%, #2563eb 60%, #3b82f6 100%)' }}>
        <div className="flex align-items-center gap-3 p-4">
          <Button icon="pi pi-arrow-left" rounded text className="text-white" onClick={() => navigate('/dashboard/lims/daily-reports')} />
          <div className="flex-1">
            <h2 className="text-xl md:text-2xl font-bold text-white m-0">
              <i className={`pi ${isEdit ? 'pi-pencil' : 'pi-plus-circle'} mr-2`} />
              {isEdit ? 'Edit Laporan Harian' : 'Buat Laporan Harian'}
            </h2>
            <p className="text-sm mt-1 mb-0" style={{ color: 'rgba(255,255,255,.7)' }}>
              {isEdit ? 'Perbarui data laporan harian' : 'Buat laporan harian baru untuk pencatatan aktivitas laboratorium'}
            </p>
          </div>
          <div className="hidden md:flex align-items-center gap-2 px-3 py-2 border-round-xl" style={{ background: 'rgba(255,255,255,.15)', backdropFilter: 'blur(8px)' }}>
            <i className="pi pi-calendar text-white" />
            <span className="text-sm font-semibold text-white">Daily Report</span>
          </div>
        </div>
      </div>

      {/* ── TabView Content ── */}
      <TabView className="shadow-2 border-round-xl overflow-hidden">
        {/* Tab 1: Informasi Dasar */}
        <TabPanel header="Informasi Dasar" leftIcon="pi pi-info-circle mr-2">
          <div className="flex flex-column gap-4 p-2">
            {/* Name */}
            <div className="flex flex-column gap-1">
              <label htmlFor="name" className="text-sm font-semibold text-700">
                <i className="pi pi-bookmark mr-1 text-blue-500" />Nama Laporan <span className="text-red-500">*</span>
              </label>
              <InputText
                id="name"
                value={form.name}
                onChange={(e) => updateField('name', e.target.value)}
                placeholder="Masukkan nama laporan"
              />
            </div>

            {/* Due Date & Location */}
            <div className="grid">
              <div className="col-12 md:col-6">
                <div className="flex flex-column gap-1">
                  <label className="text-sm font-semibold text-700">
                    <i className="pi pi-calendar mr-1 text-orange-500" />Tanggal Jatuh Tempo <span className="text-red-500">*</span>
                  </label>
                  <Calendar
                    value={form.dueDate ? new Date(form.dueDate) : undefined}
                    onChange={(e) => {
                      const val = e.value;
                      if (val instanceof Date) {
                        updateField('dueDate', toLocalISODate(val));
                      }
                    }}
                    dateFormat="dd M yy"
                    showIcon
                    placeholder="Pilih tanggal"
                  />
                </div>
              </div>
              <div className="col-12 md:col-6">
                <div className="flex flex-column gap-1">
                  <label className="text-sm font-semibold text-700">
                    <i className="pi pi-map-marker mr-1 text-red-500" />Lokasi
                  </label>
                  <InputText
                    value={form.location || ''}
                    onChange={(e) => updateField('location', e.target.value)}
                    placeholder="Masukkan lokasi"
                  />
                </div>
              </div>
            </div>

            {/* Description */}
            <div className="flex flex-column gap-1">
              <label className="text-sm font-semibold text-700">
                <i className="pi pi-align-left mr-1 text-blue-500" />Deskripsi
              </label>
              <InputTextarea
                value={form.description || ''}
                onChange={(e) => updateField('description', e.target.value)}
                rows={4}
                autoResize
                placeholder="Masukkan deskripsi laporan"
              />
            </div>
          </div>
        </TabPanel>

        {/* Tab 2: Parameter & Unit */}
        <TabPanel header="Parameter & Unit" leftIcon="pi pi-sliders-h mr-2">
          <div className="flex flex-column gap-4 p-2">
            <div className="p-3 border-round-xl" style={{ background: 'linear-gradient(135deg, #eef2ff, #e0e7ff)', border: '1px solid #a5b4fc' }}>
              <p className="text-sm m-0" style={{ color: '#4338ca' }}>
                <i className="pi pi-info-circle mr-1" />
                Pilih parameter dan unit yang terkait dengan laporan harian ini.
              </p>
            </div>

            <div className="grid">
              {/* Parameter */}
              <div className="col-12 md:col-6">
                <div className="flex flex-column gap-1">
                  <label className="text-sm font-semibold text-700">
                    <i className="pi pi-chart-bar mr-1 text-purple-500" />Parameter
                  </label>
                  {parameterOptions.length > 0 ? (
                    <Dropdown
                      value={form.parameterId}
                      options={parameterOptions}
                      onChange={(e) => updateField('parameterId', e.value)}
                      placeholder="Pilih parameter"
                      showClear
                      filter
                    />
                  ) : (
                    <InputText
                      value={form.parameterId?.toString() || ''}
                      onChange={(e) => updateField('parameterId', e.target.value ? Number(e.target.value) : undefined)}
                      placeholder="ID Parameter"
                      keyfilter="int"
                    />
                  )}
                </div>
              </div>

              {/* Unit */}
              <div className="col-12 md:col-6">
                <div className="flex flex-column gap-1">
                  <label className="text-sm font-semibold text-700">
                    <i className="pi pi-box mr-1 text-teal-500" />Unit
                  </label>
                  {unitOptions.length > 0 ? (
                    <Dropdown
                      value={form.unitId}
                      options={unitOptions}
                      onChange={(e) => updateField('unitId', e.value)}
                      placeholder="Pilih unit"
                      showClear
                      filter
                    />
                  ) : (
                    <InputText
                      value={form.unitId?.toString() || ''}
                      onChange={(e) => updateField('unitId', e.target.value ? Number(e.target.value) : undefined)}
                      placeholder="ID Unit"
                      keyfilter="int"
                    />
                  )}
                </div>
              </div>
            </div>
          </div>
        </TabPanel>
      </TabView>

      {/* ── Sticky Actions ── */}
      <div className="flex justify-content-between align-items-center p-3 border-round-xl shadow-3 surface-card" style={{ position: 'sticky', bottom: 16, zIndex: 10 }}>
        <span className="text-xs text-500 hidden md:inline">
          <i className="pi pi-info-circle mr-1" />
          Lengkapi formulir laporan harian
        </span>
        <div className="flex gap-2">
          <Button label="Batal" icon="pi pi-times" outlined severity="secondary" className="border-round-lg shadow-1 font-semibold" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} onClick={() => navigate('/dashboard/lims/daily-reports')} />
          <Button
            label={isEdit ? 'Simpan Perubahan' : 'Buat Laporan'}
            icon={isEdit ? 'pi pi-check' : 'pi pi-plus'}
            loading={loading}
            onClick={handleSubmit}
            className="border-round-lg shadow-1 font-semibold"
            style={{ background: 'linear-gradient(90deg, #2563eb, #4f46e5)', border: 'none', gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }}
          />
        </div>
      </div>
    </div>
  );
};

export default DailyReportForm;
