import { useEffect, useMemo, useState, useRef, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { decodeReportId } from '../../utils/reportId';
import { InputText } from 'primereact/inputtext';
import { InputTextarea } from 'primereact/inputtextarea';
import { Dropdown } from 'primereact/dropdown';
import { Calendar } from 'primereact/calendar';
import { Button } from 'primereact/button';
import { Toast } from 'primereact/toast';
import { TabView } from 'primereact/tabview';
import { TabPanel } from 'primereact/tabview';
import { ProgressSpinner } from 'primereact/progressspinner';
import { incidentReportService, incidentLookupService, fileUploadService, userRoleService } from '../../services';
import { useAuth } from '../../context/AuthContext';
import { toLocalISODateOrUndefined } from '../../utils/date';
import IncidentMatrixTable from '../../components/irms/IncidentMatrixTable';
import type {
  AddIncidentReportPayload,
  UpdateIncidentReportPayload,
  IncidentLookups,
} from '../../types/irms';

/* ============================================================
   INCIDENT REPORT FORM (Create / Edit)
   Maps from kpi_irms IncidentReport create.blade.php / edit.blade.php
   Uses PrimeReact form components with real-time validation
   ============================================================ */

const IncidentReportForm = () => {
  const { id: routeId } = useParams<{ id: string }>();
  const id = decodeReportId(routeId);
  const isEdit = !!id;
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);
  const { user } = useAuth();

  // Form state
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [incidentDate, setIncidentDate] = useState<Date | null>(null);
  const [incidentTime, setIncidentTime] = useState<string>('');
  const [incidentTimeError, setIncidentTimeError] = useState<string>('');
  const [locationDetail, setLocationDetail] = useState('');
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [classificationId, setClassificationId] = useState<number | null>(null);
  const [rootCauseId, setRootCauseId] = useState<number | null>(null);
  const [areaId, setAreaId] = useState<number | null>(null);
  const [regionId, setRegionId] = useState<number | null>(null);
  const [originDeptId, setOriginDeptId] = useState<number | null>(null);
  const [designationDeptId, setDesignationDeptId] = useState<number | null>(null);
  const [reportStatus, setReportStatus] = useState<string>('New');

  // Approval flow user fields (edit only)
  const [reviewerId, setReviewerId] = useState<number | null>(null);
  const [acknowledgerId, setAcknowledgerId] = useState<number | null>(null);
  const [designatedManagerId, setDesignatedManagerId] = useState<number | null>(null);
  const [actionById, setActionById] = useState<number | null>(null);
  const [assignedToId, setAssignedToId] = useState<number | null>(null);
  const [usersList, setUsersList] = useState<{ id: number; name: string; npk?: string }[]>([]);

  // Status options mirroring backend workflow
  const STATUS_OPTIONS = [
    { label: 'New — Pelaporan Awal', value: 'New' },
    { label: 'Partially Approved — Dikembalikan', value: 'Partially Approved' },
    { label: 'Pending Manager Approval — Review Superintendent', value: 'Pending Manager Approval' },
    { label: 'Fully Approved — Persetujuan Manager', value: 'Fully Approved' },
    { label: 'In Progress — Tindak Lanjut', value: 'In Progress' },
    { label: 'Done — Selesai', value: 'Done' },
  ];

  // Auto-set origin department from logged-in user
  useEffect(() => {
    if (!isEdit && user?.departmentId) {
      setOriginDeptId(user.departmentId);
    }
  }, [isEdit, user]);

  const [lookups, setLookups] = useState<IncidentLookups | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(isEdit);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [photoFiles, setPhotoFiles] = useState<File[]>([]);
  const [existingPhotos, setExistingPhotos] = useState<string[]>([]);
  const [uploading, setUploading] = useState(false);

  // Stable object URLs per-file. Without this, every re-render minted a fresh
  // URL — leaking memory across long form-edit sessions, especially on phones.
  const photoPreviewUrls = useMemo(
    () => photoFiles.map((f) => URL.createObjectURL(f)),
    [photoFiles],
  );
  useEffect(() => {
    return () => {
      photoPreviewUrls.forEach((u) => URL.revokeObjectURL(u));
    };
  }, [photoPreviewUrls]);

  // Ref for description textarea (used by formatting toolbar)
  const descriptionRef = useRef<HTMLTextAreaElement>(null);

  /**
   * Insert text at current caret position of the description textarea.
   * Supports three modes:
   *  - 'inline'  : insert raw text at caret (for symbols).
   *  - 'line'    : prepend prefix to the start of current line (or every selected line).
   *  - 'ordered' : prepend 1. 2. 3. ... to each selected line.
   */
  const insertIntoDescription = (
    payload: string,
    mode: 'inline' | 'line' | 'ordered' = 'inline',
  ) => {
    const el = descriptionRef.current;
    if (!el) {
      setDescription((prev) => prev + payload);
      return;
    }
    const value = el.value;
    const start = el.selectionStart ?? value.length;
    const end = el.selectionEnd ?? value.length;

    if (mode === 'inline') {
      const next = value.slice(0, start) + payload + value.slice(end);
      setDescription(next);
      requestAnimationFrame(() => {
        el.focus();
        const pos = start + payload.length;
        el.setSelectionRange(pos, pos);
      });
      return;
    }

    // Line-based modes: operate on full lines covering the selection
    const lineStart = value.lastIndexOf('\n', start - 1) + 1;
    const lineEndIdx = value.indexOf('\n', end);
    const blockEnd = lineEndIdx === -1 ? value.length : lineEndIdx;
    const block = value.slice(lineStart, blockEnd);
    const lines = block.length === 0 ? [''] : block.split('\n');

    let newBlock: string;
    if (mode === 'ordered') {
      newBlock = lines
        .map((ln, i) => `${i + 1}. ${ln.replace(/^\s*(\d+\.\s+|[-•●▪►✓✗★→])\s*/, '')}`)
        .join('\n');
    } else {
      newBlock = lines
        .map((ln) => `${payload}${ln.replace(/^\s*(\d+\.\s+|[-•●▪►✓✗★→])\s*/, '')}`)
        .join('\n');
    }

    const next = value.slice(0, lineStart) + newBlock + value.slice(blockEnd);
    setDescription(next);
    requestAnimationFrame(() => {
      el.focus();
      const pos = lineStart + newBlock.length;
      el.setSelectionRange(pos, pos);
    });
  };

  // Available quick-insert symbols for the description toolbar
  const DESC_SYMBOLS: { label: string; value: string; title: string }[] = [
    { label: '•', value: '• ', title: 'Bullet' },
    { label: '●', value: '● ', title: 'Solid bullet' },
    { label: '▪', value: '▪ ', title: 'Square bullet' },
    { label: '►', value: '► ', title: 'Arrow right' },
    { label: '→', value: '→ ', title: 'Arrow' },
    { label: '✓', value: '✓ ', title: 'Check' },
    { label: '✗', value: '✗ ', title: 'Cross' },
    { label: '★', value: '★ ', title: 'Star' },
    { label: '°', value: '°', title: 'Degree' },
    { label: '±', value: '±', title: 'Plus-minus' },
    { label: '≈', value: '≈', title: 'Approximately' },
  ];

  // Load lookups + users
  useEffect(() => {
    incidentLookupService.getAll().then(setLookups).catch(console.error);
    userRoleService.getUsersDropdown().then(setUsersList).catch(console.error);
  }, []);

  // Load existing data for edit
  const loadReport = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const data = await incidentReportService.detail(Number(id));
      const report = data.report;
      setTitle(report.title ?? '');
      setDescription(report.description ?? '');
      setIncidentDate(report.incidentDate ? new Date(report.incidentDate) : null);
      setIncidentTime(report.incidentTime ?? '');
      setLocationDetail(report.locationDetail ?? '');
      setCategoryId(report.category?.id ?? null);
      setClassificationId(report.classification?.id ?? null);
      setRootCauseId(report.rootCause?.id ?? null);
      setAreaId(report.area?.id ?? null);
      setRegionId(report.region?.id ?? null);
      setOriginDeptId(report.originDepartment?.id ?? null);
      setDesignationDeptId(report.designationDepartment?.id ?? null);
      setReportStatus(report.status ?? 'New');
      // Alur Persetujuan: prioritaskan nilai tersimpan, fallback ke parent-derived
      // Superintendent = reviewerId yang tersimpan, fallback ke atasan langsung pelapor
      setReviewerId(report.reviewerId ?? data.reporterParentId ?? null);
      // Manager = acknowledgerId yang tersimpan, fallback ke atasan dari superintendent
      setAcknowledgerId(report.acknowledgerId ?? data.reviewerParentId ?? null);
      setDesignatedManagerId(report.designatedManagerId ?? null);
      setActionById(report.actionById ?? null);
      setAssignedToId(report.assignedToId ?? null);

      // Load existing photos
      if (report.photos) {
        try {
          setExistingPhotos(JSON.parse(report.photos));
        } catch { setExistingPhotos([]); }
      }

    } catch {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Gagal memuat data laporan',
        life: 3000,
      });
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadReport();
  }, [loadReport]);

  // Validation
  const validate = (): boolean => {
    const err: Record<string, string> = {};
    if (!title.trim()) err.title = 'Judul wajib diisi';
    else if (title.trim().length < 5) err.title = 'Judul minimal 5 karakter';
    // Time is optional, but if filled it must be a complete HH:mm value.
    if (incidentTime && incidentTime.length !== 5) {
      err.incidentTime = 'Format waktu harus HH:mm (contoh: 08:30)';
    }
    setErrors(err);
    if (err.incidentTime) setIncidentTimeError(err.incidentTime);
    return Object.keys(err).length === 0;
  };

  // Submit
  const handleSubmit = async () => {
    if (!validate()) {
      toast.current?.show({
        severity: 'warn',
        summary: 'Validasi Gagal',
        detail: 'Periksa kembali field yang ditandai merah.',
        life: 5000,
      });
      return;
    }
    setSubmitting(true);

    try {
      const dateStr = toLocalISODateOrUndefined(incidentDate);

      const timeStr = incidentTime.length === 5 ? incidentTime : undefined;

      if (isEdit) {
        // Upload new photos if any
        let allPhotos = [...existingPhotos];
        if (photoFiles.length > 0) {
          setUploading(true);
          try {
            const uploaded = await fileUploadService.uploadPhotos(photoFiles);
            allPhotos = [...allPhotos, ...uploaded];
          } catch (uploadErr) {
            setUploading(false);
            throw new Error(`Gagal mengunggah foto: ${uploadErr instanceof Error ? uploadErr.message : 'Unknown error'}`);
          } finally {
            setUploading(false);
          }
        }
        const payload: UpdateIncidentReportPayload = {
          title,
          description,
          incident_date: dateStr,
          incident_time: timeStr,
          location_detail: locationDetail || undefined,
          category_id: categoryId ?? undefined,
          classification_id: classificationId ?? undefined,
          root_cause_id: rootCauseId ?? undefined,
          area_id: areaId ?? undefined,
          region_id: regionId ?? undefined,
          origin_department_id: originDeptId ?? undefined,
          designation_department_id: designationDeptId ?? undefined,
          status: reportStatus || undefined,
          reviewer_id: reviewerId ?? undefined,
          acknowledger_id: acknowledgerId ?? undefined,
          designated_manager_id: designatedManagerId ?? undefined,
          action_by_id: actionById ?? undefined,
          assigned_to_id: assignedToId ?? undefined,
          photos: allPhotos.length > 0 ? JSON.stringify(allPhotos) : undefined,
        };
        await incidentReportService.update(Number(id), payload);
        toast.current?.show({
          severity: 'success',
          summary: 'Berhasil',
          detail: 'Laporan insiden berhasil diperbarui',
          life: 3000,
        });
      } else {
        // Upload photos if any
        let uploadedPhotos: string[] = [];
        if (photoFiles.length > 0) {
          setUploading(true);
          try {
            uploadedPhotos = await fileUploadService.uploadPhotos(photoFiles);
          } catch (uploadErr) {
            setUploading(false);
            throw new Error(`Gagal mengunggah foto: ${uploadErr instanceof Error ? uploadErr.message : 'Unknown error'}`);
          } finally {
            setUploading(false);
          }
        }
        const payload: AddIncidentReportPayload = {
          title,
          description,
          incident_date: dateStr,
          incident_time: timeStr,
          location_detail: locationDetail || undefined,
          category_id: categoryId ?? undefined,
          classification_id: classificationId ?? undefined,
          root_cause_id: rootCauseId ?? undefined,
          area_id: areaId ?? undefined,
          region_id: regionId ?? undefined,
          origin_department_id: originDeptId ?? undefined,
          designation_department_id: designationDeptId ?? undefined,
          // reporter_id intentionally omitted — server derives it from the JWT.
          photos: uploadedPhotos.length > 0 ? JSON.stringify(uploadedPhotos) : undefined,
        };
        await incidentReportService.create(payload);
        toast.current?.show({
          severity: 'success',
          summary: 'Berhasil',
          detail: 'Laporan insiden berhasil dibuat',
          life: 3000,
        });
      }

      setTimeout(() => navigate('/dashboard/irms/incident-reports'), 1500);
    } catch (err: unknown) {
      console.error('[IncidentReportForm] Submit error:', err);
      let detail = 'Gagal menyimpan laporan insiden';
      if (err && typeof err === 'object' && 'status' in err) {
        const httpErr = err as { status?: number; response?: { data?: unknown; status?: number }; message?: string };
        const respData = httpErr.response?.data as Record<string, unknown> | undefined;
        const msg = (respData?.errorMessage || respData?.message || httpErr.message) as string | undefined;
        if (msg) detail += `: ${msg}`;
        else if (httpErr.status) detail += ` (HTTP ${httpErr.status})`;
      } else if (err instanceof Error) {
        detail += `: ${err.message}`;
      }
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail,
        life: 8000,
      });
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex align-items-center justify-content-center" style={{ minHeight: '40vh' }}>
        <ProgressSpinner strokeWidth="3" />
      </div>
    );
  }

  return (
    <div className="flex gap-4 p-3 flex-column md:p-4" style={{ maxWidth: 1100, margin: '0 auto' }}>
      <Toast ref={toast} />

      {/* ── Header ── */}
      <div className="overflow-hidden border-round-2xl shadow-3" style={{ background: 'linear-gradient(135deg, #7f1d1d 0%, #dc2626 60%, #ef4444 100%)' }}>
        <div className="flex gap-3 p-4 align-items-center">
          <Button icon="pi pi-arrow-left" rounded text className="text-white" onClick={() => navigate('/dashboard/irms/incident-reports')} />
          <div className="flex-1">
            <h2 className="m-0 text-xl font-bold text-white md:text-2xl">
              <i className={`pi ${isEdit ? 'pi-pencil' : 'pi-plus-circle'} mr-2`} />
              {isEdit ? 'Edit Laporan Insiden' : 'Buat Laporan Insiden'}
            </h2>
            <p className="mt-1 mb-0 text-sm" style={{ color: 'rgba(255,255,255,.7)' }}>
              {isEdit ? 'Perbarui data laporan insiden' : 'Buat laporan insiden baru untuk dicatat dan diinvestigasi'}
            </p>
          </div>

        </div>
      </div>

      {/* ── Matrix Referensi Severity ── */}
      <IncidentMatrixTable defaultCollapsed={true} />

      {/* ── TabView Content ── */}
      <TabView className="overflow-hidden shadow-2 border-round-xl">
        {/* Tab 1: Informasi Dasar */}
        <TabPanel header="Informasi Insiden" leftIcon="pi pi-info-circle mr-2">
          <div className="flex gap-4 p-2 flex-column">
            {/* Title */}
            <div className="flex gap-1 flex-column">
              <label htmlFor="title" className="text-sm font-semibold text-700">
                <i className="mr-1 text-red-500 pi pi-bookmark" />Judul Laporan <span className="text-red-500">*</span>
              </label>
              <InputText
                id="title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Masukkan judul laporan"
                className={errors.title ? 'p-invalid' : ''}
              />
              {errors.title && <small className="p-error">{errors.title}</small>}
            </div>

            {/* Description */}
            <div className="flex gap-1 flex-column">
              <label className="text-sm font-semibold text-700">
                <i className="mr-1 text-red-500 pi pi-align-left" />Deskripsi
              </label>

              {/* Formatting toolbar — symbols, bullet & numbered list */}
              <div
                className="flex flex-wrap gap-1 p-2 align-items-center bg-gray-50 border-1 border-gray-200 border-round-top"
                style={{ borderBottom: 'none' }}
              >
                <Button
                  type="button"
                  icon="pi pi-list"
                  className="p-button-sm p-button-text"
                  tooltip="Bullet list (•)"
                  tooltipOptions={{ position: 'top' }}
                  onClick={() => insertIntoDescription('• ', 'line')}
                />
                <Button
                  type="button"
                  icon="pi pi-sort-numeric-down"
                  className="p-button-sm p-button-text"
                  tooltip="Numbered list (1. 2. 3.)"
                  tooltipOptions={{ position: 'top' }}
                  onClick={() => insertIntoDescription('', 'ordered')}
                />
                <Button
                  type="button"
                  label="a."
                  className="p-button-sm p-button-text"
                  tooltip="Sub-poin (a. b. c.)"
                  tooltipOptions={{ position: 'top' }}
                  onClick={() => insertIntoDescription('   a. ', 'line')}
                />
                <Button
                  type="button"
                  icon="pi pi-minus"
                  className="p-button-sm p-button-text"
                  tooltip="Dash list (-)"
                  tooltipOptions={{ position: 'top' }}
                  onClick={() => insertIntoDescription('- ', 'line')}
                />

                <span className="mx-2 text-gray-300">|</span>

                {DESC_SYMBOLS.map((s) => (
                  <Button
                    key={s.label}
                    type="button"
                    label={s.label}
                    className="p-button-sm p-button-text p-button-plain"
                    tooltip={s.title}
                    tooltipOptions={{ position: 'top' }}
                    onClick={() => insertIntoDescription(s.value, 'inline')}
                  />
                ))}

                <span className="mx-2 text-gray-300">|</span>

                <Button
                  type="button"
                  label="Kronologi"
                  icon="pi pi-clock"
                  className="p-button-sm p-button-text"
                  tooltip="Sisipkan template Kronologi Kejadian"
                  tooltipOptions={{ position: 'top' }}
                  onClick={() =>
                    insertIntoDescription(
                      'Kronologi Kejadian:\n• \n• \n• \n\n',
                      'inline',
                    )
                  }
                />
                <Button
                  type="button"
                  label="Analisa"
                  icon="pi pi-search"
                  className="p-button-sm p-button-text"
                  tooltip="Sisipkan template Analisa Kejadian"
                  tooltipOptions={{ position: 'top' }}
                  onClick={() =>
                    insertIntoDescription(
                      'Analisa Kejadian:\n• \n• \n\n',
                      'inline',
                    )
                  }
                />
                <Button
                  type="button"
                  label="Tindakan"
                  icon="pi pi-wrench"
                  className="p-button-sm p-button-text"
                  tooltip="Sisipkan template Tindakan Perbaikan"
                  tooltipOptions={{ position: 'top' }}
                  onClick={() =>
                    insertIntoDescription(
                      'Tindakan Perbaikan:\n1. \n2. \n3. \n\n',
                      'inline',
                    )
                  }
                />
              </div>

              <InputTextarea
                ref={descriptionRef}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={8}
                autoResize
                placeholder={
                  'Deskripsikan detail insiden...\n\nContoh:\nKronologi Kejadian:\n• PV-0508-1 MV = 100 %\n• PV-0508-2 MV = 14.46 %\n\nAnalisa Kejadian:\n• PV-0508-1 dilapangan nutup\n• Selenoid rusak, akan dilakukan penggantian.'
                }
                style={{ borderTopLeftRadius: 0, borderTopRightRadius: 0, fontFamily: 'Consolas, "Courier New", monospace' }}
              />
              <small className="text-500">
                Gunakan toolbar di atas untuk menambahkan simbol, bullet list, atau numbered list agar deskripsi lebih informatif dan terstruktur.
              </small>
            </div>

            {/* Status Alur Persetujuan — edit only */}
            {isEdit && (
              <div className="flex gap-1 flex-column">
                <label className="text-sm font-semibold text-700">
                  <i className="mr-1 text-green-600 pi pi-sitemap" />Alur Persetujuan (Status)
                </label>
                <Dropdown
                  value={reportStatus}
                  options={STATUS_OPTIONS}
                  onChange={(e) => setReportStatus(e.value)}
                  placeholder="Pilih status"
                />
                <small className="text-500">Ubah status untuk memajukan atau mengembalikan posisi laporan dalam alur persetujuan.</small>
              </div>
            )}

            {/* Date, Time, Location */}
            <div className="grid">
              <div className="col-12 md:col-4">
                <div className="flex gap-1 flex-column">
                  <label className="text-sm font-semibold text-700">
                    <i className="mr-1 text-orange-500 pi pi-calendar" />Tanggal Insiden
                  </label>
                  <Calendar
                    value={incidentDate}
                    onChange={(e) => setIncidentDate(e.value as Date)}
                    dateFormat="dd M yy"
                    showIcon
                    placeholder="Pilih tanggal"
                  />
                </div>
              </div>
              <div className="col-12 md:col-4">
                <div className="flex gap-1 flex-column">
                  <label className="text-sm font-semibold text-700">
                    <i className="mr-1 text-blue-500 pi pi-clock" />Waktu Insiden
                  </label>
                  <Calendar
                    value={(() => {
                      if (!incidentTime || incidentTime.length !== 5) return null;
                      const [hh, mm] = incidentTime.split(':').map((s) => Number(s));
                      if (Number.isNaN(hh) || Number.isNaN(mm)) return null;
                      const d = new Date();
                      d.setHours(hh, mm, 0, 0);
                      return d;
                    })()}
                    onChange={(e) => {
                      const v = e.value as Date | null;
                      if (!v) {
                        setIncidentTime('');
                        setIncidentTimeError('');
                        return;
                      }
                      const hh = String(v.getHours()).padStart(2, '0');
                      const mm = String(v.getMinutes()).padStart(2, '0');
                      setIncidentTime(`${hh}:${mm}`);
                      setIncidentTimeError('');
                    }}
                    timeOnly
                    hourFormat="24"
                    showIcon
                    icon="pi pi-clock"
                    placeholder="HH:mm"
                    className={incidentTimeError || errors.incidentTime ? 'p-invalid' : ''}
                  />
                  {(incidentTimeError || errors.incidentTime) && (
                    <small className="p-error">{incidentTimeError || errors.incidentTime}</small>
                  )}
                </div>
              </div>
              <div className="col-12 md:col-4">
                <div className="flex gap-1 flex-column">
                  <label className="text-sm font-semibold text-700">
                    <i className="mr-1 text-red-500 pi pi-map-marker" />Detail Lokasi
                  </label>
                  <InputText
                    value={locationDetail}
                    onChange={(e) => setLocationDetail(e.target.value)}
                    placeholder="Lokasi kejadian insiden"
                  />
                </div>
              </div>
            </div>
          </div>
        </TabPanel>

        {/* Tab 2: Kategori & Klasifikasi */}
        <TabPanel header="Kategori & Area" leftIcon="pi pi-th-large mr-2">
          <div className="flex gap-4 p-2 flex-column">
            {/* Category & Classification */}
            <div className="grid">
              <div className="col-12 md:col-4">
                <div className="flex gap-1 flex-column">
                  <label className="text-sm font-semibold text-700"><i className="mr-1 text-purple-500 pi pi-tag" />Kategori</label>
                  <Dropdown
                    value={categoryId}
                    options={lookups?.categories.map((c) => ({ label: c.name, value: c.id })) ?? []}
                    onChange={(e) => setCategoryId(e.value)}
                    placeholder="Pilih kategori"
                    showClear filter loading={!lookups}
                  />
                </div>
              </div>
              <div className="col-12 md:col-4">
                <div className="flex gap-1 flex-column">
                  <label className="text-sm font-semibold text-700"><i className="mr-1 text-indigo-500 pi pi-list" />Klasifikasi</label>
                  <Dropdown
                    value={classificationId}
                    options={lookups?.classifications.map((c) => ({ label: c.name, value: c.id })) ?? []}
                    onChange={(e) => setClassificationId(e.value)}
                    placeholder="Pilih klasifikasi"
                    showClear filter loading={!lookups}
                  />
                </div>
              </div>
              <div className="col-12 md:col-4">
                <div className="flex gap-1 flex-column">
                  <label className="text-sm font-semibold text-700"><i className="mr-1 text-red-400 pi pi-bolt" />Root Cause</label>
                  <Dropdown
                    value={rootCauseId}
                    options={lookups?.rootCauses.map((r) => ({ label: r.name, value: r.id })) ?? []}
                    onChange={(e) => setRootCauseId(e.value)}
                    placeholder="Pilih root cause"
                    showClear filter loading={!lookups}
                  />
                </div>
              </div>
            </div>

            {/* Area, Region, Departments */}
            <div className="p-3 border-round-xl" style={{ background: 'linear-gradient(135deg, #fef2f2, #fff1f2)', border: '1px solid #fecaca' }}>
              <p className="m-0 mb-2 text-xs font-semibold" style={{ color: '#991b1b' }}>
                <i className="mr-1 pi pi-map" />Informasi Wilayah & Departemen
              </p>
              <div className="grid">
                <div className="col-12 md:col-4">
                  <div className="flex gap-1 flex-column">
                    <label className="text-xs font-semibold text-600"><i className="mr-1 text-xs text-teal-500 pi pi-map" />Area</label>
                    <Dropdown
                      value={areaId}
                      options={lookups?.areas.map((a) => ({ label: a.name, value: a.id })) ?? []}
                      onChange={(e) => setAreaId(e.value)}
                      placeholder="Pilih area"
                      showClear filter loading={!lookups}
                    />
                  </div>
                </div>
                <div className="col-12 md:col-4">
                  <div className="flex gap-1 flex-column">
                    <label className="text-xs font-semibold text-600"><i className="mr-1 text-xs text-green-500 pi pi-globe" />Region</label>
                    <Dropdown
                      value={regionId}
                      options={lookups?.regions.map((r) => ({ label: r.name, value: r.id })) ?? []}
                      onChange={(e) => setRegionId(e.value)}
                      placeholder="Pilih region"
                      showClear filter loading={!lookups}
                    />
                  </div>
                </div>
                <div className="col-12 md:col-4">
                  <div className="flex gap-1 flex-column">
                    <label className="text-xs font-semibold text-600"><i className="mr-1 text-xs text-purple-500 pi pi-send" />Dept. Tujuan</label>
                    <Dropdown
                      value={designationDeptId}
                      options={lookups?.designationDepartments.map((d) => ({ label: d.name, value: d.id })) ?? []}
                      onChange={(e) => setDesignationDeptId(e.value)}
                      placeholder="Pilih departemen"
                      showClear filter loading={!lookups}
                    />
                  </div>
                </div>
              </div>


            </div>
          </div>
        </TabPanel>

        {/* Tab 3: Alur Persetujuan — edit only */}
        {isEdit && (
          <TabPanel header="Alur Persetujuan" leftIcon="pi pi-sitemap mr-2">
            <div className="flex gap-4 p-2 flex-column">
              <p className="m-0 text-xs text-500">
                <i className="mr-1 pi pi-info-circle" />
                Tentukan pengguna yang bertanggung jawab di setiap tahap alur persetujuan laporan ini.
              </p>
              <div className="grid">
                <div className="col-12 md:col-6">
                  <div className="flex gap-1 flex-column">
                    <label className="text-sm font-semibold text-700">
                      <i className="mr-1 text-blue-500 pi pi-search" />Review Superintendent
                      <span className="ml-1 text-xs font-normal text-500">(Atasan langsung pelapor)</span>
                    </label>
                    <Dropdown
                      value={reviewerId}
                      options={usersList.map((u) => ({ label: u.npk ? `${u.name} — ${u.npk}` : u.name, value: u.id }))}
                      onChange={(e) => setReviewerId(e.value)}
                      placeholder="Pilih superintendent"
                      showClear filter
                    />
                  </div>
                </div>
                <div className="col-12 md:col-6">
                  <div className="flex gap-1 flex-column">
                    <label className="text-sm font-semibold text-700">
                      <i className="mr-1 text-green-600 pi pi-check-circle" />Persetujuan Manager
                      <span className="ml-1 text-xs font-normal text-500">(Atasan dari superintendent)</span>
                    </label>
                    <Dropdown
                      value={acknowledgerId}
                      options={usersList.map((u) => ({ label: u.npk ? `${u.name} — ${u.npk}` : u.name, value: u.id }))}
                      onChange={(e) => setAcknowledgerId(e.value)}
                      placeholder="Pilih manager"
                      showClear filter
                    />
                  </div>
                </div>
                <div className="col-12 md:col-6">
                  <div className="flex gap-1 flex-column">
                    <label className="text-sm font-semibold text-700">
                      <i className="mr-1 text-purple-500 pi pi-user-plus" />Penugasan Pelaksana
                      <span className="ml-1 text-xs font-normal text-500">(Manajer Dept. Tujuan)</span>
                    </label>
                    <Dropdown
                      value={designatedManagerId}
                      options={usersList.map((u) => ({ label: u.npk ? `${u.name} — ${u.npk}` : u.name, value: u.id }))}
                      onChange={(e) => setDesignatedManagerId(e.value)}
                      placeholder="Pilih manajer dept. tujuan"
                      showClear filter
                    />
                  </div>
                </div>
                <div className="col-12 md:col-6">
                  <div className="flex gap-1 flex-column">
                    <label className="text-sm font-semibold text-700">
                      <i className="mr-1 text-orange-500 pi pi-wrench" />Tindak Lanjut
                      <span className="ml-1 text-xs font-normal text-500">(Pelaksana / Action By)</span>
                    </label>
                    <Dropdown
                      value={actionById}
                      options={usersList.map((u) => ({ label: u.npk ? `${u.name} — ${u.npk}` : u.name, value: u.id }))}
                      onChange={(e) => setActionById(e.value)}
                      placeholder="Pilih pelaksana"
                      showClear filter
                    />
                  </div>
                </div>
                <div className="col-12 md:col-6">
                  <div className="flex gap-1 flex-column">
                    <label className="text-sm font-semibold text-700">
                      <i className="mr-1 text-teal-500 pi pi-user" />Ditugaskan Kepada
                      <span className="ml-1 text-xs font-normal text-500">(Bawahan yang ditugaskan)</span>
                    </label>
                    <Dropdown
                      value={assignedToId}
                      options={usersList.map((u) => ({ label: u.npk ? `${u.name} — ${u.npk}` : u.name, value: u.id }))}
                      onChange={(e) => setAssignedToId(e.value)}
                      placeholder="Pilih bawahan"
                      showClear filter
                    />
                  </div>
                </div>
              </div>
            </div>
          </TabPanel>
        )}

      </TabView>

      {/* ── Photos Section ── */}
      <div className="p-4 surface-card border-round-xl shadow-2">
        <h3 className="m-0 mb-3 text-base font-bold text-700">
          <i className="mr-2 text-red-500 pi pi-images" />Foto Insiden
        </h3>

        {/* Existing photos */}
        {existingPhotos.length > 0 && (
          <div className="flex flex-wrap gap-3 mb-3">
            {existingPhotos.map((photo, idx) => (
              <div key={idx} className="relative" style={{ width: 120, height: 120 }}>
                <img
                  src={fileUploadService.getPhotoUrl(photo)}
                  alt={`Foto ${idx + 1}`}
                  style={{ width: 120, height: 120, objectFit: 'cover', borderRadius: 10, border: '2px solid #e5e7eb' }}
                />
                <button
                  type="button"
                  className="absolute flex align-items-center justify-content-center"
                  style={{ top: -8, right: -8, width: 24, height: 24, borderRadius: '50%', background: '#ef4444', color: '#fff', border: 'none', cursor: 'pointer', fontSize: 12 }}
                  onClick={() => setExistingPhotos((prev) => prev.filter((_, i) => i !== idx))}
                >
                  <i className="pi pi-times" style={{ fontSize: 10 }} />
                </button>
              </div>
            ))}
          </div>
        )}

        {/* New file selection */}
        <div className="flex gap-2 flex-column">
          <input
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            multiple
            onChange={(e) => {
              const files = e.target.files;
              if (files) setPhotoFiles((prev) => [...prev, ...Array.from(files)]);
            }}
            style={{ display: 'none' }}
            id="incident-photo-input"
          />
          <div className="flex gap-2 align-items-center">
            <Button
              type="button"
              label={uploading ? 'Mengunggah...' : 'Pilih Foto'}
              icon="pi pi-camera"
              outlined
              severity="danger"
              size="small"
              disabled={uploading}
              onClick={() => document.getElementById('incident-photo-input')?.click()}
            />
            <span className="text-xs text-500">Format: JPEG, PNG, GIF, WEBP (maks. 10MB per file)</span>
          </div>

          {/* Preview new files */}
          {photoFiles.length > 0 && (
            <div className="flex flex-wrap gap-3 mt-2">
              {photoFiles.map((file, idx) => (
                <div key={idx} className="relative" style={{ width: 120, height: 120 }}>
                  <img
                    src={photoPreviewUrls[idx]}
                    alt={file.name}
                    style={{ width: 120, height: 120, objectFit: 'cover', borderRadius: 10, border: '2px dashed #3b82f6' }}
                  />
                  <button
                    type="button"
                    className="absolute flex align-items-center justify-content-center"
                    style={{ top: -8, right: -8, width: 24, height: 24, borderRadius: '50%', background: '#ef4444', color: '#fff', border: 'none', cursor: 'pointer', fontSize: 12 }}
                    onClick={() => setPhotoFiles((prev) => prev.filter((_, i) => i !== idx))}
                  >
                    <i className="pi pi-times" style={{ fontSize: 10 }} />
                  </button>
                  <div className="absolute px-2 py-1 text-xs font-semibold" style={{ bottom: 4, left: 4, background: 'rgba(0,0,0,0.6)', color: '#fff', borderRadius: 6, maxWidth: 110, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {file.name}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* ── Sticky Actions ── */}
      <div className="flex p-3 justify-content-between align-items-center border-round-xl shadow-3 surface-card" style={{ position: 'sticky', bottom: 16, zIndex: 10 }}>
        <span className="hidden text-xs text-500 md:inline">
          <i className="mr-1 pi pi-info-circle" />
          Lengkapi formulir insiden
        </span>
        <div className="flex gap-2">
          <Button label="Batal" icon="pi pi-times" outlined severity="secondary" className="font-semibold border-round-lg shadow-1" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} onClick={() => navigate('/dashboard/irms/incident-reports')} />
          <Button
            label={isEdit ? 'Simpan Perubahan' : 'Buat Laporan'}
            icon={isEdit ? 'pi pi-check' : 'pi pi-plus'}
            loading={submitting}
            onClick={handleSubmit}
            className="font-semibold border-round-lg shadow-1"
            style={{ background: 'linear-gradient(90deg, #dc2626, #b91c1c)', border: 'none', gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }}
          />
        </div>
      </div>
    </div>
  );
};

export default IncidentReportForm;
