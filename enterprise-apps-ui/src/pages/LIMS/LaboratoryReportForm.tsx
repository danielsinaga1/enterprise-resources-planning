import { useState, useEffect, useCallback, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import { Dropdown } from "primereact/dropdown";
import { Button } from "primereact/button";
import { Toast } from "primereact/toast";
import { TabView, TabPanel } from "primereact/tabview";
import { ProgressSpinner } from "primereact/progressspinner";
import { laboratoryReportService, labConfigService } from "../../services/limsService";
import { authService } from "../../services";
import type { LaboratoryReportPayload, LabDepartment } from "../../types/lims";

const LaboratoryReportForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(isEdit);
  const [departments, setDepartments] = useState<LabDepartment[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  // Tanggal sampling & pemohon dicatat otomatis oleh backend saat create;
  // tanggal analisis terisi saat Tinjauan Teknis.
  const [form, setForm] = useState<LaboratoryReportPayload>({
    location: "",
    notes: "",
    remark: "",
    departmentId: undefined,
  });

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const configPromise = labConfigService.getMasterData().catch(() => null);
      if (isEdit && id) {
        const [reportRes, configRes] = await Promise.all([
          laboratoryReportService.get(Number(id)),
          configPromise,
        ]);
        const r = reportRes.data;
        setForm({
          location: r.location,
          notes: r.notes || "",
          remark: r.remark || "",
          departmentId: r.departmentId || undefined,
        });
        if (configRes) {
          const depts = (configRes.data as Record<string, LabDepartment[]>)?.departments;
          if (depts) setDepartments(depts);
        }
      } else {
        const configRes = await configPromise;
        if (configRes) {
          const depts = (configRes.data as Record<string, LabDepartment[]>)?.departments;
          if (depts) setDepartments(depts);
        }
      }
    } catch {
      if (isEdit) toast.current?.show({ severity: "error", summary: "Error", detail: "Gagal memuat data laporan", life: 3000 });
    } finally {
      setLoading(false);
    }
  }, [id, isEdit]);

  useEffect(() => { loadData(); }, [loadData]);

  const validate = (): boolean => {
    const err: Record<string, string> = {};
    if (!form.location?.trim()) err.location = "Lokasi wajib diisi";
    setErrors(err);
    return Object.keys(err).length === 0;
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setSubmitting(true);
    try {
      if (isEdit && id) {
        await laboratoryReportService.update(Number(id), form);
        toast.current?.show({ severity: "success", summary: "Berhasil", detail: "Laporan berhasil diperbarui", life: 2000 });
      } else {
        // Pelapor = pengguna yang membuat laporan; backend memakai ini untuk
        // mencatat pemohon (requestedBy) dan tanggal sampling otomatis.
        await laboratoryReportService.create({ ...form, reporterId: authService.getUser()?.id });
        toast.current?.show({ severity: "success", summary: "Berhasil", detail: "Laporan berhasil dibuat", life: 2000 });
      }
      setTimeout(() => navigate("/dashboard/lims/laboratory-reports"), 500);
    } catch {
      toast.current?.show({ severity: "error", summary: "Error", detail: `Gagal ${isEdit ? "memperbarui" : "membuat"} laporan`, life: 3000 });
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex align-items-center justify-content-center" style={{ minHeight: "40vh" }}>
        <ProgressSpinner strokeWidth="3" />
      </div>
    );
  }

  const departmentOptions = departments.map((d) => ({ label: d.name, value: d.id }));

  return (
    <div className="flex flex-column gap-4 p-3 md:p-4" style={{ maxWidth: 1100, margin: '0 auto' }}>
      <Toast ref={toast} />

      {/* ── Header ── */}
      <div className="border-round-2xl overflow-hidden shadow-3" style={{ background: 'linear-gradient(135deg, #065f46 0%, #059669 60%, #34d399 100%)' }}>
        <div className="flex align-items-center gap-3 p-4">
          <Button icon="pi pi-arrow-left" rounded text className="text-white" onClick={() => navigate("/dashboard/lims/laboratory-reports")} />
          <div className="flex-1">
            <h2 className="text-xl md:text-2xl font-bold text-white m-0">
              <i className={`pi ${isEdit ? 'pi-pencil' : 'pi-plus-circle'} mr-2`} />
              {isEdit ? 'Edit Laporan Laboratorium' : 'Buat Laporan Laboratorium'}
            </h2>
            <p className="text-sm mt-1 mb-0" style={{ color: 'rgba(255,255,255,.7)' }}>
              {isEdit ? 'Perbarui data laporan laboratorium' : 'Buat laporan laboratorium baru untuk analisis sampel'}
            </p>
          </div>
          <div className="hidden md:flex align-items-center gap-2 px-3 py-2 border-round-xl" style={{ background: 'rgba(255,255,255,.15)', backdropFilter: 'blur(8px)' }}>
            <i className="pi pi-file text-white" />
            <span className="text-sm font-semibold text-white">Lab Report</span>
          </div>
        </div>
      </div>

      {/* ── TabView Content ── */}
      <TabView className="shadow-2 border-round-xl overflow-hidden">
        {/* Tab 1: Informasi Sampling */}
        <TabPanel header="Informasi Sampling" leftIcon="pi pi-info-circle mr-2">
          <div className="flex flex-column gap-4 p-2">
            {/* Location & Department */}
            <div className="grid">
              <div className="col-12 md:col-6">
                <div className="flex flex-column gap-1">
                  <label className="text-sm font-semibold text-700">
                    <i className="pi pi-map-marker mr-1 text-red-500" />Lokasi <span className="text-red-500">*</span>
                  </label>
                  <InputText
                    value={form.location}
                    onChange={(e) => setForm({ ...form, location: e.target.value })}
                    placeholder="Masukkan lokasi sampling"
                    className={errors.location ? "p-invalid" : ""}
                  />
                  {errors.location && <small className="p-error">{errors.location}</small>}
                </div>
              </div>
              <div className="col-12 md:col-6">
                <div className="flex flex-column gap-1">
                  <label className="text-sm font-semibold text-700">
                    <i className="pi pi-building mr-1 text-purple-500" />Departemen
                  </label>
                  <Dropdown
                    value={form.departmentId}
                    options={departmentOptions}
                    onChange={(e) => setForm({ ...form, departmentId: e.value })}
                    placeholder="Pilih departemen"
                    showClear
                    filter
                  />
                </div>
              </div>
            </div>

            <small className="text-500">
              <i className="pi pi-info-circle mr-1" />
              Tanggal sampling dan pemohon tercatat otomatis dari Anda saat laporan dibuat;
              tanggal analisis terisi saat Tinjauan Teknis.
            </small>
          </div>
        </TabPanel>

        {/* Tab 2: Catatan & Keterangan */}
        <TabPanel header="Catatan & Keterangan" leftIcon="pi pi-align-left mr-2">
          <div className="flex flex-column gap-4 p-2">
            {/* Notes */}
            <div className="flex flex-column gap-1">
              <label className="text-sm font-semibold text-700">
                <i className="pi pi-file-edit mr-1 text-orange-500" />Catatan
              </label>
              <InputTextarea
                value={form.notes || ""}
                onChange={(e) => setForm({ ...form, notes: e.target.value })}
                rows={4}
                autoResize
                placeholder="Catatan tambahan..."
              />
            </div>

            {/* Remark */}
            <div className="flex flex-column gap-1">
              <label className="text-sm font-semibold text-700">
                <i className="pi pi-comment mr-1 text-cyan-500" />Keterangan
              </label>
              <InputTextarea
                value={form.remark || ""}
                onChange={(e) => setForm({ ...form, remark: e.target.value })}
                rows={3}
                autoResize
                placeholder="Keterangan tambahan..."
              />
            </div>

            {/* Info Box */}
            <div className="p-3 border-round-xl" style={{ background: 'linear-gradient(135deg, #ecfdf5, #d1fae5)', border: '1px solid #6ee7b7' }}>
              <p className="text-sm m-0" style={{ color: '#065f46' }}>
                <i className="pi pi-info-circle mr-1" />
                Nomor laporan akan digenerate otomatis oleh sistem. Detail analisis dapat ditambahkan setelah laporan dibuat.
              </p>
            </div>
          </div>
        </TabPanel>
      </TabView>

      {/* ── Sticky Actions ── */}
      <div className="flex justify-content-between align-items-center p-3 border-round-xl shadow-3 surface-card" style={{ position: 'sticky', bottom: 16, zIndex: 10 }}>
        <span className="text-xs text-500 hidden md:inline">
          <i className="pi pi-info-circle mr-1" />
          Lengkapi formulir laporan laboratorium
        </span>
        <div className="flex gap-2">
          <Button
            label="Batal"
            icon="pi pi-times"
            outlined
            severity="secondary"
            className="font-semibold border-round-xl shadow-1"
            style={{ gap: '0.6rem', padding: '0.75rem 1.75rem', fontSize: '0.925rem' }}
            onClick={() => navigate("/dashboard/lims/laboratory-reports")}
          />
          <Button
            label={isEdit ? 'Simpan Perubahan' : 'Buat Laporan'}
            icon={isEdit ? 'pi pi-check' : 'pi pi-plus'}
            loading={submitting}
            onClick={handleSubmit}
            className="font-semibold border-round-xl shadow-2"
            style={{
              background: isEdit
                ? 'linear-gradient(135deg, #059669 0%, #065f46 100%)'
                : 'linear-gradient(135deg, #16a34a 0%, #15803d 100%)',
              border: 'none',
              gap: '0.6rem',
              padding: '0.75rem 1.75rem',
              fontSize: '0.925rem',
            }}
          />
        </div>
      </div>
    </div>
  );
};

export default LaboratoryReportForm;
