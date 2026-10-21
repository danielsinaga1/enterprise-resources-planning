import { useEffect, useMemo, useState, useRef, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { InputText } from 'primereact/inputtext';
import { InputTextarea } from 'primereact/inputtextarea';
import { Dropdown } from 'primereact/dropdown';
import { AutoComplete, type AutoCompleteCompleteEvent } from 'primereact/autocomplete';
import { Calendar } from 'primereact/calendar';
import { Checkbox } from 'primereact/checkbox';
import { Button } from 'primereact/button';
import { Toast } from 'primereact/toast';
import { ProgressSpinner } from 'primereact/progressspinner';
import { TabView, TabPanel } from 'primereact/tabview';
import { Dialog } from 'primereact/dialog';
import { Divider } from 'primereact/divider';
import { Tag } from 'primereact/tag';
import { Card } from 'primereact/card';
import { safetyObservationService, incidentLookupService, masterJsoLocationService, masterDivisionService, jsoReportService, userRoleService, locationSectionService, fileUploadService, authService, workRequestService } from '../../services';
import type { WorkRequest } from '../../services/workRequestService';
import { masterDesignationDeptService } from '../../services/masterDataService';
import { toLocalISODate } from '../../utils/date';
import ObservationCategoryMatrix from '../../components/irms/ObservationCategoryMatrix';
import DeferredMount from '../../components/common/DeferredMount';
import PhotoPicker from '../../components/irms/PhotoPicker';
import { useAuth } from '../../context/AuthContext';
import type {
  AddSafetyObservationPayload,
  UpdateSafetyObservationPayload,
  IncidentLookups,
  JsoLocation,
  JsoReport,
  Division,
  LocationSection,
  NonPlantLocation,
  SafetyObservation,
  DesignationDepartment,
} from '../../types/irms';

/** Foto disimpan sebagai JSON array nama file; kolom kosong/rusak = tanpa foto. */
const parsePhotoList = (json?: string): string[] => {
  if (!json) return [];
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

/**
 * Temuan sewaktu diedit: `photos` berisi file yang SUDAH terunggah, sedangkan
 * `photoFiles` menampung pilihan yang belum diunggah. Menitipkan file pada
 * objek temuan (bukan array paralel) membuatnya ikut terbawa saat temuan
 * dihapus atau disisipkan — indeks tidak bisa melenceng. Field ini dibuang
 * sebelum payload dikirim ke API.
 */
type FindingDraft = JsoReport & { photoFiles?: File[] };

// Referensi tetap untuk temuan tanpa foto tertunda. `?? []` inline membuat array
// baru tiap render, dan itu mementalkan useMemo/useEffect blob URL di setiap
// kartu temuan pada tiap ketukan tombol.
const NO_FILES: File[] = [];

/* ============================================================
   SAFETY OBSERVATION FORM (Create / Edit)
   Maps from kpi_irms SafetyObservation create/edit blade
   Supports NCR and JSO modes with conditional fields
   ============================================================ */
const SafetyObservationForm = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id;
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);
  const { user } = useAuth();

  // Core fields
  const [reportNumber, setReportNumber] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [observationDate, setObservationDate] = useState<Date | null>(null);
  const [isJso, setIsJso] = useState(false);
  const [locationDetail, setLocationDetail] = useState('');

  // Lookups
  const [areaId, setAreaId] = useState<number | null>(null);
  const [originDeptId, setOriginDeptId] = useState<number | null>(null);
  const [designationDeptId, setDesignationDeptId] = useState<number | null>(null);

  // WR Number (required when designation dept is MTN)
  const [woNumber, setWoNumber] = useState('');
  const [woSuggestions, setWoSuggestions] = useState<WorkRequest[]>([]);
  const [jsoWrSuggestions, setJsoWrSuggestions] = useState<WorkRequest[]>([]);

  const searchWorkRequests = async (e: AutoCompleteCompleteEvent, setter: (r: WorkRequest[]) => void) => {
    try {
      setter(await workRequestService.search(e.query));
    } catch {
      setter([]);
    }
  };

  const wrItemTemplate = (item: WorkRequest) => (
    <div className="flex flex-column">
      <span className="font-semibold">{item.number} — {item.title}</span>
      {item.entityName && <small className="text-500">{item.entityName}{item.workOrder ? ` · WO ${item.workOrder}` : ''}{item.closed ? ' · Closed' : ''}</small>}
    </div>
  );

  // Detail Avantis per nomor WR yang sudah dikenali (null = dicek tapi tidak ada di Avantis).
  // Diisi lewat onSelect autocomplete, plus resolver di bawah untuk nilai yang diketik manual
  // atau yang sudah tersimpan saat form edit dibuka.
  const [wrInfoMap, setWrInfoMap] = useState<Record<string, WorkRequest | null>>({});

  const rememberWr = (wr: WorkRequest) => setWrInfoMap((prev) => ({ ...prev, [wr.number]: wr }));

  // Kartu ringkas di bawah field WR agar user paham WR apa yang dia rujuk.
  // undefined = belum selesai diresolusi (jangan tampilkan apa pun dulu),
  // null = sudah dicek dan tidak ada di Avantis.
  const wrInfoCard = (value?: string) => {
    const key = (value ?? '').trim();
    if (key.length < 2) return null;
    const wr = wrInfoMap[key];
    if (wr === undefined) return null;
    // Peringatan, bukan blokir: nomor tetap boleh disimpan (AutoComplete sengaja
    // tanpa forceSelection). Sejak view Avantis memuat riwayat penuh, nomor yang
    // tidak ketemu hampir selalu salah ketik — itulah asal data seperti "232313".
    if (wr === null) {
      return (
        <div className="p-2 border-round-lg" style={{ background: '#fffbeb', border: '1px solid #fcd34d' }}>
          <p className="text-xs m-0" style={{ color: '#92400e' }}>
            <i className="pi pi-exclamation-triangle mr-1" />
            Nomor WR <span className="font-bold">{key}</span> tidak ditemukan di Avantis — periksa
            kembali penulisannya. Nomor WO tidak akan terisi otomatis.
          </p>
        </div>
      );
    }
    return (
      <div className="p-2 border-round-lg" style={{ background: '#ecfeff', border: '1px solid #67e8f9' }}>
        <p className="text-xs m-0" style={{ color: '#155e75' }}>
          <i className="pi pi-wrench mr-1" />
          <span className="font-bold">WR {wr.number}</span> — {wr.title}
          {wr.entityName ? <> · {wr.entityNumber} {wr.entityName}</> : null}
          {wr.workOrder ? <> · WO {wr.workOrder}{wr.workOrderTitle && wr.workOrderTitle !== wr.title ? ` (${wr.workOrderTitle})` : ''}</> : null}
          <Tag value={wr.closed ? 'Closed' : 'Open'} severity={wr.closed ? 'danger' : 'success'} className="ml-2" style={{ fontSize: '0.65rem', padding: '0.1rem 0.4rem' }} />
        </p>
      </div>
    );
  };

  // Area "Non-Plant" tidak punya region/section plant — lokasinya diambil dari
  // daftar bangunan non-plant (irms_nonplant_locations) via nonPlantLocationId.
  const isNonPlantArea = (aId?: number) => {
    if (!aId) return false;
    const area = lookups?.areas.find((a) => a.id === aId);
    return !!area && /non/i.test(area.name);
  };

  // Auto-set origin department from logged-in user
  useEffect(() => {
    if (!isEdit && user?.departmentId) {
      setOriginDeptId(user.departmentId);
    }
  }, [isEdit, user]);

  // JSO-specific fields
  const [leaderId, setLeaderId] = useState<number | null>(null);
  const [member1Id, setMember1Id] = useState<number | null>(null);
  const [member2Id, setMember2Id] = useState<number | null>(null);

  const [lookups, setLookups] = useState<IncidentLookups | null>(null);
  const [jsoLeaders, setJsoLeaders] = useState<{ id: number; name: string }[]>([]);
  const [jsoMembers, setJsoMembers] = useState<{ id: number; name: string }[]>([]);
  const [jsoLeadersLoading, setJsoLeadersLoading] = useState(false);
  const [jsoMembersLoading, setJsoMembersLoading] = useState(false);
  const [jsoLocations, setJsoLocations] = useState<JsoLocation[]>([]);
  const [jsoLocationId, setJsoLocationId] = useState<number | null>(null);
  const [divisions, setDivisions] = useState<Division[]>([]);
  const [jsoFindings, setJsoFindings] = useState<FindingDraft[]>([]);
  const [sectionsByRegion, setSectionsByRegion] = useState<Record<number, LocationSection[]>>({});
  const [nonPlantLocations, setNonPlantLocations] = useState<NonPlantLocation[]>([]);

  // Opsi dropdown dihitung sekali per perubahan sumbernya, bukan per render.
  // Sebelumnya `options={divisions.map(...)}` ditulis inline di dalam
  // jsoFindings.map(), jadi satu render membuat array baru untuk SETIAP kartu
  // temuan (4 dropdown x jumlah temuan). Referensi array yang selalu baru
  // memaksa PrimeReact menyusun ulang daftar filter tiap kali — dengan belasan
  // temuan, satu ketukan tombol di textarea mana pun ikut membebani semuanya.
  const divisionOptions = useMemo(
    () => divisions.map((d) => ({ label: d.name, value: d.id })),
    [divisions],
  );
  const areaOptions = useMemo(
    () => lookups?.areas.map((a) => ({ label: a.name, value: a.id })) ?? [],
    [lookups],
  );
  const regionOptions = useMemo(
    () => lookups?.regions.map((r) => ({ label: r.name, value: r.id })) ?? [],
    [lookups],
  );
  const nonPlantOptions = useMemo(
    () => nonPlantLocations.map((l) => ({ label: l.name, value: l.id })),
    [nonPlantLocations],
  );

  // Nomor WR yang requestnya sedang berjalan. Tanpa penanda ini, setiap hasil
  // yang masuk mengubah wrInfoMap -> effect di bawah jalan lagi -> nomor yang
  // masih in-flight (belum masuk map) terlihat "belum diminta" dan diminta
  // ULANG. Satu form dengan belasan temuan bisa menembakkan puluhan request
  // duplikat ke Avantis, dan tiap request menempuh gateway -> backend -> SQL Server.
  const wrInFlight = useRef<Set<string>>(new Set());

  // Resolve detail Avantis untuk nomor WR yang diketik manual atau yang sudah
  // tersimpan saat form edit dibuka (onSelect autocomplete mengisi langsung).
  useEffect(() => {
    const unresolved = [woNumber, ...jsoFindings.map((f) => f.wrNumber ?? '')]
      .map((n) => n.trim())
      .filter((n, i, arr) =>
        n.length >= 2 &&
        wrInfoMap[n] === undefined &&
        !wrInFlight.current.has(n) &&
        arr.indexOf(n) === i);
    if (unresolved.length === 0) return;
    const t = setTimeout(() => {
      unresolved.forEach(async (num) => {
        // Ditandai di dalam timeout, bukan saat menjadwalkan: kalau timeout
        // keburu dibatalkan, nomornya tidak boleh tertinggal berstatus in-flight.
        wrInFlight.current.add(num);
        try {
          const exact = await workRequestService.getByNumber(num);
          setWrInfoMap((prev) => ({ ...prev, [num]: exact }));
          // Isi nomor WO temuan yang WR-nya baru saja dikenali. Hanya yang masih
          // kosong: WO yang sudah diketik sendiri oleh user tidak boleh ditimpa.
          // Penjaga itu sekaligus membuat effect ini idempoten — sekali terisi,
          // syaratnya tidak terpenuhi lagi sehingga tidak memicu putaran ulang.
          if (exact?.workOrder) {
            setJsoFindings((prev) => prev.map((f) =>
              f.wrNumber?.trim() === num && !f.wrWoNumber?.trim()
                ? { ...f, wrWoNumber: exact.workOrder }
                : f));
          }
        } catch {
          setWrInfoMap((prev) => ({ ...prev, [num]: null }));
        } finally {
          wrInFlight.current.delete(num);
        }
      });
    }, 600);
    return () => clearTimeout(t);
  }, [woNumber, jsoFindings, wrInfoMap]);
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(isEdit);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [obsStatus, setObsStatus] = useState('');
  const [returnComment, setReturnComment] = useState('');

  // ── Workflow user assignment state (Alur Persetujuan tab) ──
  const [reviewedById, setReviewedById] = useState<number | null>(null);
  const [assignedToId, setAssignedToId] = useState<number | null>(null);
  const [actionById, setActionById] = useState<number | null>(null);

  // Status options for Alur Persetujuan dropdown
  const OBS_STATUS_OPTIONS = [
    { label: 'Open — Pelaporan Awal', value: 'open' },
    { label: 'Reviewed — Ditinjau QSHE', value: 'reviewed' },
    { label: 'In Progress — Sedang Ditangani', value: 'in_progress' },
    { label: 'Action Taken — Tindakan Diambil', value: 'action_taken' },
    { label: 'Closed — Selesai / Tutup', value: 'closed' },
    { label: 'Approved Partially — Sebagian Disetujui', value: 'approved_partially' },
    { label: 'Rejected — Ditolak', value: 'rejected' },
    { label: 'Returned — Dikembalikan', value: 'returned' },
  ];

  // ── Workflow (Alur Persetujuan) state — Admin Safety / Super Admin only ──
  const [fullObs, setFullObs] = useState<SafetyObservation | null>(null);
  const [userMap, setUserMap] = useState<Map<number, string>>(new Map());
  const [userOptions, setUserOptions] = useState<{ label: string; value: number }[]>([]);
  const [designationDeptOptions, setDesignationDeptOptions] = useState<{ label: string; value: number }[]>([]);
  const [showWorkflowDialog, setShowWorkflowDialog] = useState(false);
  const [workflowAction, setWorkflowAction] = useState<string>('');
  const [wfSelectedUser, setWfSelectedUser] = useState<number | null>(null);
  const [wfNote, setWfNote] = useState('');
  const [wfCorrective, setWfCorrective] = useState('');
  const [wfReason, setWfReason] = useState('');
  const [wfComment, setWfComment] = useState('');
  const [wfDesignationDeptId, setWfDesignationDeptId] = useState<number | null>(null);

  const isAdminOrSuperAdmin = user?.roles?.some((r) =>
    r != null && ['ADMIN_SAFETY', 'SUPER_ADMIN', 'Administrator Safety', 'Super Administrator',
      'Admin', 'Administrator'].some((v) => r === v || r.toUpperCase().includes('SUPER') || r.toUpperCase().includes('ADMIN_SAFETY'))
  ) ?? false;

  // Nomor WR/WO temuan adalah rujukan ke Avantis yang dipakai tindak lanjut.
  // Saat laporan dibuat, pelapor masih boleh mengisinya; setelah tersimpan,
  // koreksinya lewat satu pintu — hanya Administrator — supaya nomor yang sudah
  // dipegang tugas tindak lanjut tidak berubah diam-diam. Resolver WR di atas
  // tetap boleh mengisi nomor WO yang MASIH kosong: itu nilai dari Avantis untuk
  // WR yang sudah ada, bukan ketikan user.
  const canEditFindingWr = !isEdit || isAdminOrSuperAdmin;

  const readOnlyValue = (value?: string) => (
    <div
      className="flex align-items-center px-2 border-round-md text-sm text-700"
      style={{ background: '#f8fafc', border: '1px solid #e2e8f0', minHeight: '2.6rem' }}
    >
      {value?.trim() ? value : <span className="text-500">Belum diisi</span>}
    </div>
  );

  const [photoFiles, setPhotoFiles] = useState<File[]>([]);
  const [existingPhotos, setExistingPhotos] = useState<string[]>([]);
  const [uploading, setUploading] = useState(false);


  useEffect(() => {
    incidentLookupService.getAll().then(setLookups).catch(console.error);
    masterJsoLocationService.list().then(setJsoLocations).catch(console.error);
    masterDivisionService.list().then(setDivisions).catch(console.error);
    locationSectionService.getNonPlant().then(setNonPlantLocations).catch(console.error);

    // Load users and dept options for workflow (Admin Safety / Super Admin only)
    if (isEdit) {
      userRoleService.getUsersDropdown().then((users) => {
        const map = new Map<number, string>();
        const opts: { label: string; value: number }[] = [];
        users.forEach((u) => {
          map.set(u.id, u.name);
          opts.push({ label: u.name || `User #${u.id}`, value: u.id });
        });
        setUserMap(map);
        setUserOptions(opts);
      }).catch(console.error);

      masterDesignationDeptService.list().then((depts) => {
        setDesignationDeptOptions((depts ?? []).map((d: DesignationDepartment) => ({ label: d.name, value: d.id })));
      }).catch(console.error);
    }
  }, [isEdit]);

  // Load JSO leader & member lists only when JSO mode is active
  // Also clear the mutually-exclusive field when switching modes
  useEffect(() => {
    if (isJso) {
      // Track the fetch itself, not the result count. Keying the spinner off
      // "list is empty" (as before) left it spinning forever whenever no user
      // holds the role — hiding the emptyMessage that explains why.
      setJsoLeadersLoading(true);
      setJsoMembersLoading(true);
      userRoleService.getUsersDropdownByRole('Leader JSO')
        .then(setJsoLeaders).catch(console.error)
        .finally(() => setJsoLeadersLoading(false));
      userRoleService.getUsersDropdownByRole('Member JSO')
        .then(setJsoMembers).catch(console.error)
        .finally(() => setJsoMembersLoading(false));
      // Clear area selection when switching to JSO
      setAreaId(null);
    } else {
      // Clear JSO-specific selections when switching to NCR mode
      setJsoLeaders([]);
      setJsoMembers([]);
      setLeaderId(null);
      setMember1Id(null);
      setMember2Id(null);
      setJsoLocationId(null);
    }
  }, [isJso]);

  // Filtered dropdown options: exclude already-selected users
  const leaderOptions = jsoLeaders
    .filter((u) => u.id !== member1Id && u.id !== member2Id)
    .map((u) => ({ label: u.name, value: u.id }));

  const member1Options = jsoMembers
    .filter((u) => u.id !== leaderId && u.id !== member2Id)
    .map((u) => ({ label: u.name, value: u.id }));

  const member2Options = jsoMembers
    .filter((u) => u.id !== leaderId && u.id !== member1Id)
    .map((u) => ({ label: u.name, value: u.id }));

  const loadObservation = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const obs = await safetyObservationService.detail(Number(id));
      setFullObs(obs);
      setReportNumber(obs.reportNumber ?? '');
      // For JSO records, title is often empty — fall back to descriptionJso
      setTitle(obs.title || (obs.isJso ? (obs.descriptionJso ?? '') : ''));
      setDescription(obs.description ?? '');
      setObservationDate(obs.observationDate
        ? new Date(obs.observationDate)
        : (obs.isJso && obs.observationDateJso ? new Date(obs.observationDateJso) : null));
      setIsJso(obs.isJso ?? false);
      setLocationDetail(obs.locationDetail ?? '');
      setJsoLocationId(obs.jsoLocationId ?? null);
      setAreaId(obs.area?.id ?? null);
      setOriginDeptId(obs.originDepartment?.id ?? null);
      setDesignationDeptId(obs.designationDepartment?.id ?? null);

      // Foto level observasi. Untuk NCR ini foto utamanya; untuk JSO hanya sisa
      // data lama — foto baru melekat pada masing-masing temuan.
      setExistingPhotos(parsePhotoList(obs.photos));

      setLeaderId(obs.leaderId ?? null);
      setMember1Id(obs.member1Id ?? null);
      setMember2Id(obs.member2Id ?? null);
      setWoNumber(obs.woNumber ?? '');
      setObsStatus(obs.status ?? '');
      setReturnComment(obs.reason ?? '');
      setReviewedById(obs.reviewedById ?? null);
      setAssignedToId(obs.assignedToId ?? null);
      setActionById(obs.actionById ?? null);
      // Load JSO findings if JSO mode
      if (obs.isJso) {
        jsoReportService.list(Number(id)).then((findings) => {
          setJsoFindings(findings);
          // Pre-load sections for findings that have a region selected
          const regionIds = [...new Set(findings.map((f) => f.regionId).filter((r): r is number => r != null))];
          regionIds.forEach((regionId) => {
            locationSectionService.getByRegion(regionId).then((sections) => {
              setSectionsByRegion((prev) => ({ ...prev, [regionId]: sections }));
            }).catch(console.error);
          });
        }).catch(console.error);
      }
    } catch {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Gagal memuat data observasi',
        life: 3000,
      });
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadObservation();
  }, [loadObservation]);

  // Check if selected designation dept is MTN (Maintenance)
  const isMtnDept = (() => {
    if (!designationDeptId || !lookups) return false;
    const dept = lookups.designationDepartments.find((d) => d.id === designationDeptId);
    if (!dept) return false;
    const name = dept.name.toLowerCase();
    return name.includes('mtn') || name.includes('maintenance');
  })();

  const validate = (): boolean => {
    const err: Record<string, string> = {};
    if (!title.trim()) err.title = 'Judul wajib diisi';

    // Check if designation dept is MTN-related and WR number is required
    if (isMtnDept && !woNumber.trim()) {
      err.woNumber = 'Nomor WR wajib diisi untuk departemen Maintenance (MTN)';
    }

    setErrors(err);
    return Object.keys(err).length === 0;
  };

  /**
   * Unggah foto yang masih tertunda di tiap temuan, lalu ubah draft menjadi
   * payload API. Hasilnya juga dikembalikan ke state agar percobaan simpan
   * berikutnya (mis. setelah error di tengah loop) tidak mengunggah ulang
   * file yang sudah masuk.
   */
  const resolveFindingPayloads = async (drafts: FindingDraft[]): Promise<JsoReport[]> => {
    const resolved: JsoReport[] = [];
    for (const draft of drafts) {
      const { photoFiles, ...finding } = draft;
      let photos = parsePhotoList(finding.photos);
      if (photoFiles && photoFiles.length > 0) {
        setUploading(true);
        try {
          photos = [...photos, ...(await fileUploadService.uploadPhotos(photoFiles))];
        } finally {
          setUploading(false);
        }
      }
      // Selalu kirim array utuh — backend mengabaikan null, jadi menghapus
      // foto terakhir hanya tersimpan bila dikirim sebagai "[]".
      resolved.push({
        ...finding,
        wrNumber: finding.wrNumber?.trim() || undefined,
        // Kosong dikirim sebagai undefined, bukan "": backend memperlakukan nomor WO
        // yang dikirim eksplisit sebagai keputusan user dan melewati resolusi Avantis,
        // jadi "" akan mengunci temuan itu selamanya tanpa WO.
        wrWoNumber: finding.wrWoNumber?.trim() || undefined,
        photos: JSON.stringify(photos),
      });
    }
    setJsoFindings(resolved);
    return resolved;
  };

  const saveObservation = async () => {
    const dateStr = observationDate
      ? toLocalISODate(observationDate) + ' 00:00:00'
      : undefined;

    if (isEdit) {
      // Upload new photos if any
      let allPhotos = [...existingPhotos];
      if (photoFiles.length > 0) {
        setUploading(true);
        try {
          const uploaded = await fileUploadService.uploadPhotos(photoFiles);
          allPhotos = [...allPhotos, ...uploaded];
        } finally {
          setUploading(false);
        }
      }
      const payload: UpdateSafetyObservationPayload = {
        title,
        description,
        observation_date: dateStr,
        is_jso: isJso,
        location_detail: locationDetail || undefined,
        jso_location_id: isJso ? (jsoLocationId ?? undefined) : undefined,
        area_id: areaId ?? undefined,
        origin_department_id: originDeptId ?? undefined,
        designation_department_id: designationDeptId ?? undefined,
        leader_id: isJso ? (leaderId ?? undefined) : undefined,
        member_1_id: isJso ? (member1Id ?? undefined) : undefined,
        member_2_id: isJso ? (member2Id ?? undefined) : undefined,
        wo_number: woNumber || undefined,
        // Selalu kirim array utuh — backend mengabaikan null, jadi menghapus
        // foto terakhir hanya tersimpan bila dikirim sebagai "[]".
        photos: JSON.stringify(allPhotos),
        status: obsStatus || undefined,
        reviewed_by_id: reviewedById ?? undefined,
        assigned_to_id: assignedToId ?? undefined,
        action_by_id: actionById ?? undefined,
        report_number: reportNumber.trim() || undefined,
      };
      await safetyObservationService.update(Number(id), payload);

      // Save JSO findings
      if (isJso) {
        for (const finding of await resolveFindingPayloads(jsoFindings)) {
          if (finding.id) {
            await jsoReportService.update(Number(id), finding.id, finding);
          } else {
            await jsoReportService.create(Number(id), finding);
          }
        }
      }
    } else {
      // Upload photos if any
      let uploadedPhotos: string[] = [];
      if (photoFiles.length > 0) {
        setUploading(true);
        try {
          uploadedPhotos = await fileUploadService.uploadPhotos(photoFiles);
        } finally {
          setUploading(false);
        }
      }
      const payload: AddSafetyObservationPayload = {
        title,
        description,
        observation_date: dateStr,
        is_jso: isJso,
        location_detail: locationDetail || undefined,
        jso_location_id: isJso ? (jsoLocationId ?? undefined) : undefined,
        area_id: areaId ?? undefined,
        origin_department_id: originDeptId ?? undefined,
        designation_department_id: designationDeptId ?? undefined,
        leader_id: isJso ? (leaderId ?? undefined) : undefined,
        member_1_id: isJso ? (member1Id ?? undefined) : undefined,
        member_2_id: isJso ? (member2Id ?? undefined) : undefined,
        wo_number: woNumber || undefined,
        photos: uploadedPhotos.length > 0 ? JSON.stringify(uploadedPhotos) : undefined,
      };
      const created = await safetyObservationService.create(payload);

      // Save JSO findings
      if (isJso && created?.id) {
        for (const finding of await resolveFindingPayloads(jsoFindings)) {
          await jsoReportService.create(created.id, finding);
        }
      }
    }
  };

  // ── Workflow action handlers (Admin Safety / Super Admin) ──
  const openWorkflowDialog = (action: string) => {
    setWorkflowAction(action);
    setWfSelectedUser(null);
    setWfNote('');
    setWfCorrective('');
    setWfReason('');
    setWfComment('');
    setWfDesignationDeptId(null);
    setShowWorkflowDialog(true);
  };

  const handleWorkflowSubmit = async () => {
    if (!id) return;
    try {
      const numId = Number(id);
      switch (workflowAction) {
        case 'review':
          if (!wfSelectedUser) return;
          await safetyObservationService.review(numId, wfSelectedUser, wfDesignationDeptId ?? undefined);
          break;
        case 'assign-pic':
          if (!wfSelectedUser) return;
          await safetyObservationService.assignPic(numId, wfSelectedUser);
          break;
        case 'delegate':
          if (!wfSelectedUser) return;
          await safetyObservationService.delegatePic(numId, wfSelectedUser, wfNote);
          break;
        case 'submit-action':
          if (!wfSelectedUser) return;
          await safetyObservationService.submitAction(numId, wfSelectedUser, wfCorrective);
          break;
        case 'complete': {
          const userData = authService.getUser();
          await safetyObservationService.complete(numId, userData?.id);
          break;
        }
        case 'reject':
          await safetyObservationService.reject(numId, wfReason);
          break;
        case 'return-to-creator':
          await safetyObservationService.returnToCreator(numId, wfComment);
          break;
        case 'resubmit':
          await safetyObservationService.resubmit(numId);
          break;
        default:
          return;
      }
      toast.current?.show({ severity: 'success', summary: 'Berhasil', detail: 'Workflow berhasil diperbarui', life: 3000 });
      setShowWorkflowDialog(false);
      // Reload full obs to refresh workflow timeline
      const updated = await safetyObservationService.detail(numId);
      setFullObs(updated);
      setObsStatus(updated.status ?? '');
    } catch {
      toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal memperbarui workflow', life: 3000 });
    }
  };

  const getWorkflowDialogTitle = () => {
    const titles: Record<string, string> = {
      'review': 'Tinjauan QSHE — Tentukan Dept. Terkait (Administrator Safety)',
      'assign-pic': 'Penugasan Subordinate (Superintendent)',
      'delegate': 'Delegasi PIC',
      'submit-action': 'Submit Tindak Lanjut Temuan (Subordinate)',
      'complete': 'Verifikasi & Penutupan (Administrator Safety)',
      'reject': 'Tolak Observasi',
      'return-to-creator': 'Kembalikan ke Creator (Data Belum Jelas)',
      'resubmit': 'Ajukan Kembali Setelah Revisi',
    };
    return titles[workflowAction] || 'Workflow';
  };

  const isWorkflowSubmitDisabled = () => {
    if (workflowAction === 'review' && (!wfSelectedUser || !wfDesignationDeptId)) return true;
    if (['assign-pic', 'delegate', 'submit-action'].includes(workflowAction) && !wfSelectedUser) return true;
    if (workflowAction === 'submit-action' && !wfCorrective.trim()) return true;
    if (workflowAction === 'reject' && !wfReason.trim()) return true;
    if (workflowAction === 'return-to-creator' && !wfComment.trim()) return true;
    return false;
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setSubmitting(true);

    try {
      await saveObservation();

      toast.current?.show({
        severity: 'success',
        summary: 'Berhasil',
        detail: isEdit ? 'Observasi berhasil diperbarui' : 'Observasi berhasil dibuat',
        life: 3000,
      });

      setTimeout(() => navigate('/dashboard/irms/safety-observations'), 1200);
    } catch (err: unknown) {
      console.error('Submit error', err);
      const httpErr = err as { response?: { data?: { error_message?: string; errorMessage?: string; message?: string } } };
      const detail = httpErr?.response?.data?.error_message
        || httpErr?.response?.data?.errorMessage
        || httpErr?.response?.data?.message
        || 'Gagal menyimpan observasi';
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail,
        life: 5000,
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
    <div className="flex flex-column gap-4 p-3 md:p-4" style={{ maxWidth: 1100, margin: '0 auto' }}>
      <Toast ref={toast} />

      {/* ── Header ── */}
      <div className="border-round-2xl overflow-hidden shadow-3" style={{ background: 'linear-gradient(135deg, #1e3a5f 0%, #2563eb 60%, #3b82f6 100%)' }}>
        <div className="flex align-items-center gap-3 p-4">
          <Button icon="pi pi-arrow-left" rounded text className="text-white" onClick={() => navigate('/dashboard/irms/safety-observations')} />
          <div className="flex-1">
            <h2 className="text-xl md:text-2xl font-bold text-white m-0">
              <i className={`pi ${isEdit ? 'pi-pencil' : 'pi-plus-circle'} mr-2`} />
              {isEdit ? 'Edit Observasi' : 'Buat Observasi Keselamatan'}
            </h2>
            <p className="text-sm mt-1 mb-0" style={{ color: 'rgba(255,255,255,.7)' }}>
              {isEdit ? 'Perbarui data observasi keselamatan' : 'Buat laporan observasi baru — NCR atau JSO'}
            </p>
          </div>
          <div className="hidden md:flex align-items-center gap-2 px-3 py-2 border-round-xl" style={{ background: 'rgba(255,255,255,.15)', backdropFilter: 'blur(8px)' }}>
            <i className={`pi ${isJso ? 'pi-shield' : 'pi-file'} text-white`} />
            <span className="text-sm font-semibold text-white">{isJso ? 'Mode JSO' : 'Mode NCR'}</span>
          </div>
        </div>
      </div>

      {/* ── Mode Toggle ── */}
      <div className="flex align-items-center gap-3 p-3 border-round-xl shadow-1" style={{ background: isJso ? 'linear-gradient(90deg, #eef2ff, #e0e7ff)' : 'linear-gradient(90deg, #f0fdf4, #dcfce7)', border: isJso ? '1px solid #a5b4fc' : '1px solid #86efac' }}>
        <Checkbox
          inputId="jso-mode"
          checked={isJso}
          onChange={(e) => {
            const jso = e.checked ?? false;
            setIsJso(jso);
            // Pilihan foto level observasi tidak punya tempat di mode JSO —
            // buang agar tidak diam-diam ikut terunggah ke header.
            if (jso) setPhotoFiles([]);
          }}
          style={{
            width: 22,
            height: 22,
            border: `2px solid ${isJso ? '#6366f1' : '#16a34a'}`,
            borderRadius: 6,
            boxShadow: isJso ? '0 0 0 3px rgba(99,102,241,0.18)' : '0 0 0 3px rgba(22,163,74,0.18)',
            flexShrink: 0,
          }}
        />
        <label htmlFor="jso-mode" className="text-sm font-semibold cursor-pointer" style={{ color: isJso ? '#4338ca' : '#15803d' }}>
          <i className={`pi ${isJso ? 'pi-shield' : 'pi-file'} mr-2`} />
          {isJso ? 'Mode JSO (Job Safety Observation)' : 'Mode NCR (Non-Conformance Report)'}
        </label>
        <span className="text-xs ml-auto px-2 py-1 border-round-lg font-semibold" style={{ background: isJso ? '#6366f1' : '#22c55e', color: '#fff' }}>
          {isJso ? 'JSO' : 'NCR'}
        </span>
      </div>

      {/* Returned Banner — show when status is 'returned' */}
      {isEdit && obsStatus === 'returned' && (
        <div className="p-3 border-round-xl shadow-1" style={{ background: 'linear-gradient(90deg, #fff3e0, #ffe0b2)', border: '1px solid #ff9800' }}>
          <div className="flex align-items-center gap-2 mb-2">
            <i className="pi pi-replay text-xl" style={{ color: '#e65100' }} />
            <span className="text-base font-bold" style={{ color: '#e65100' }}>Laporan Dikembalikan untuk Revisi</span>
          </div>
          {returnComment && (
            <div className="p-2 border-round" style={{ background: 'rgba(255,255,255,0.7)' }}>
              <span className="text-sm font-semibold" style={{ color: '#bf360c' }}>Komentar QSHE:</span>
              <p className="text-sm mt-1 mb-0" style={{ color: '#4e342e' }}>{returnComment}</p>
            </div>
          )}
          <p className="text-xs mt-2 mb-0" style={{ color: '#e65100' }}>
            <i className="pi pi-info-circle mr-1" />
            Silakan revisi data sesuai komentar di atas, lalu klik "Ajukan Kembali" untuk mengirim ulang.
          </p>
        </div>
      )}

      {/* ── TabView Content ── */}
      <TabView className="shadow-2 border-round-xl overflow-hidden">
        {/* Tab 1: Informasi Dasar */}
        <TabPanel header="Informasi Dasar" leftIcon="pi pi-info-circle mr-2">
          <div className="flex flex-column gap-4 p-2">
            {/* Report Number — edit mode only */}
            {isEdit && (
              <div className="flex flex-column gap-1">
                <label htmlFor="reportNumber" className="text-sm font-semibold text-700">
                  <i className="pi pi-hashtag mr-1 text-indigo-500" />Nomor Laporan
                </label>
                <InputText
                  id="reportNumber"
                  value={reportNumber}
                  onChange={(e) => setReportNumber(e.target.value)}
                  placeholder="Contoh: SO-2604-001"
                />
              </div>
            )}

            {/* Title */}
            <div className="flex flex-column gap-1">
              <label htmlFor="title" className="text-sm font-semibold text-700">
                <i className="pi pi-bookmark mr-1 text-blue-500" />Judul Observasi <span className="text-red-500">*</span>
              </label>
              <InputText
                id="title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Masukkan judul observasi"
                className={errors.title ? 'p-invalid' : ''}
              />
              {errors.title && <small className="p-error">{errors.title}</small>}
            </div>

            {/* Description */}
            <div className="flex flex-column gap-1">
              <label className="text-sm font-semibold text-700">
                <i className="pi pi-align-left mr-1 text-blue-500" />Deskripsi
              </label>
              <InputTextarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={4}
                autoResize
                placeholder="Deskripsikan observasi secara detail..."
              />
            </div>

            {/* Date & Location */}
            <div className="grid">
              <div className={isJso ? 'col-12' : 'col-12 md:col-6'}>
                <div className="flex flex-column gap-1">
                  <label className="text-sm font-semibold text-700">
                    <i className="pi pi-calendar mr-1 text-orange-500" />Tanggal Observasi
                  </label>
                  <Calendar
                    value={observationDate}
                    onChange={(e) => setObservationDate(e.value as Date)}
                    dateFormat="dd M yy"
                    showIcon
                    placeholder="Pilih tanggal"
                  />
                </div>
              </div>
              {!isJso && (
                <div className="col-12 md:col-6">
                  <div className="flex flex-column gap-1">
                    <label className="text-sm font-semibold text-700">
                      <i className="pi pi-map-marker mr-1 text-red-500" />Detail Lokasi
                    </label>
                    <InputText
                      value={locationDetail}
                      onChange={(e) => setLocationDetail(e.target.value)}
                      placeholder="Lokasi observasi"
                    />
                  </div>
                </div>
              )}
            </div>
          </div>
        </TabPanel>

        {/* Tab 2: Area & Departemen */}
        <TabPanel header="Area & Departemen" leftIcon="pi pi-sitemap mr-2">
          <div className="flex flex-column gap-4 p-2">
            <div className="grid">
              {/* Area — hanya tampil di mode NCR */}
              {!isJso && (
                <div className="col-12 md:col-6 fadein animation-duration-300">
                  <div className="flex flex-column gap-1">
                    <label className="text-sm font-semibold text-700"><i className="pi pi-map mr-1 text-teal-500" />Area</label>
                    <Dropdown
                      value={areaId}
                      options={areaOptions}
                      onChange={(e) => setAreaId(e.value)}
                      placeholder="Pilih area"
                      showClear filter loading={!lookups}
                    />
                  </div>
                </div>
              )}
              {/* JSO Locations — hanya tampil di mode JSO */}
              {isJso && (
                <div className="col-12 md:col-6 fadein animation-duration-300">
                  <div className="flex flex-column gap-1">
                    <label className="text-sm font-semibold text-700"><i className="pi pi-map-marker mr-1 text-purple-500" />JSO Locations</label>
                    <Dropdown
                      value={jsoLocationId}
                      options={jsoLocations.map((l) => ({ label: l.name, value: l.id }))}
                      onChange={(e) => setJsoLocationId(e.value)}
                      placeholder="Pilih lokasi JSO"
                      showClear filter
                    />
                  </div>
                </div>
              )}
            </div>

            {/* Step 5: WR Number — required when dept is MTN, hidden for other depts */}
            {designationDeptId && (
              <div className="grid">
                <div className="col-12">
                  {isMtnDept ? (
                    <div className="flex flex-column gap-1">
                      <label className="text-sm font-semibold text-700">
                        <i className="pi pi-hashtag mr-1 text-cyan-500" />Nomor WR (Work Request) <span className="text-red-500">*</span>
                      </label>
                      <AutoComplete
                        value={woNumber}
                        suggestions={woSuggestions}
                        completeMethod={(e) => searchWorkRequests(e, setWoSuggestions)}
                        field="number"
                        itemTemplate={wrItemTemplate}
                        onSelect={(e) => rememberWr(e.value as WorkRequest)}
                        onChange={(e) => setWoNumber(typeof e.value === 'string' ? e.value : e.value?.number ?? '')}
                        placeholder="Cari nomor WR..."
                        className={errors.woNumber ? 'p-invalid w-full' : 'w-full'}
                        inputClassName="w-full"
                        minLength={2}
                      />
                      {wrInfoCard(woNumber)}
                      {errors.woNumber && <small className="p-error">{errors.woNumber}</small>}
                      <small className="text-500">
                        <i className="pi pi-info-circle mr-1" />
                        Dept. Maintenance (MTN) — Wajib input nomor Work Request. Assigned ke Team MTN.
                      </small>
                    </div>
                  ) : (
                    <div className="p-3 border-round-xl" style={{ background: 'linear-gradient(90deg, #f0fdf4, #dcfce7)', border: '1px solid #86efac' }}>
                      <p className="text-sm m-0" style={{ color: '#15803d' }}>
                        <i className="pi pi-info-circle mr-1" />
                        Dept. lain — Tidak perlu input No. WR. Laporan akan di-assign ke Team terkait.
                      </p>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </TabPanel>

        {/* Tab 3: Tim JSO — only shown when JSO mode */}
        {isJso && (
          <TabPanel header="Tim JSO" leftIcon="pi pi-users mr-2">
            <div className="flex flex-column gap-4 p-2">
              <div className="p-3 border-round-xl" style={{ background: 'linear-gradient(135deg, #eef2ff, #faf5ff)', border: '1px solid #c7d2fe' }}>
                <p className="text-sm m-0" style={{ color: '#4338ca' }}>
                  <i className="pi pi-info-circle mr-1" />
                  Pilih leader dan anggota tim observasi JSO. Tim terdiri dari 1 leader dan maksimal 2 member.
                </p>
              </div>
              <div className="grid">
                <div className="col-12 md:col-4">
                  <div className="flex flex-column gap-1">
                    <label className="text-sm font-semibold text-700"><i className="pi pi-star mr-1 text-yellow-600" />Leader</label>
                    <Dropdown
                      value={leaderId}
                      options={leaderOptions}
                      onChange={(e) => setLeaderId(e.value)}
                      placeholder="Pilih Leader"
                      showClear filter loading={jsoLeadersLoading}
                      emptyMessage="Tidak ada user dengan role Leader JSO"
                    />
                  </div>
                </div>
                <div className="col-12 md:col-4">
                  <div className="flex flex-column gap-1">
                    <label className="text-sm font-semibold text-700"><i className="pi pi-user mr-1 text-blue-500" />Member 1</label>
                    <Dropdown
                      value={member1Id}
                      options={member1Options}
                      onChange={(e) => setMember1Id(e.value)}
                      placeholder="Pilih Member 1"
                      showClear filter loading={jsoMembersLoading}
                      emptyMessage="Tidak ada user dengan role Member JSO"
                    />
                  </div>
                </div>
                <div className="col-12 md:col-4">
                  <div className="flex flex-column gap-1">
                    <label className="text-sm font-semibold text-700"><i className="pi pi-user mr-1 text-blue-500" />Member 2</label>
                    <Dropdown
                      value={member2Id}
                      options={member2Options}
                      onChange={(e) => setMember2Id(e.value)}
                      placeholder="Pilih Member 2"
                      showClear filter loading={jsoMembersLoading}
                      emptyMessage="Tidak ada user dengan role Member JSO"
                    />
                  </div>
                </div>
              </div>

            </div>
          </TabPanel>
        )}

        {/* Tab 4: Temuan JSO — only shown when JSO mode */}
        {isJso && (
          <TabPanel header={`Temuan JSO (${jsoFindings.length})`} leftIcon="pi pi-search mr-2">
            <div className="flex flex-column gap-3 p-2">
              {/* Add Button */}
              <div className="flex justify-content-end">
                <Button
                  icon="pi pi-plus"
                  label="Tambah Temuan"
                  severity="info"
                  size="small"
                  onClick={() =>
                    setJsoFindings((prev) => [
                      ...prev,
                      { findings: '', rootCauseJso: '', potentialHazard: '', recommendation: '', wrNumber: '', divisionId: undefined, areaId: undefined, regionId: undefined, sectionId: undefined, nonPlantLocationId: undefined, categoryId: undefined },
                    ])
                  }
                />
              </div>

              {jsoFindings.length === 0 && (
                <div className="flex flex-column align-items-center gap-2 p-5 border-round-xl" style={{ background: 'linear-gradient(135deg, #eef2ff 0%, #faf5ff 100%)', border: '2px dashed #a5b4fc' }}>
                  <i className="pi pi-search text-4xl" style={{ color: '#818cf8' }} />
                  <span className="text-base font-semibold" style={{ color: '#4338ca' }}>Belum ada temuan</span>
                  <span className="text-sm text-500">Klik "Tambah Temuan" untuk menambahkan detail temuan JSO</span>
                </div>
              )}

              {/* Kartu di luar layar baru di-mount saat discroll mendekat. TabView
                  memakai renderActiveOnly, jadi tanpa ini SELURUH kartu dibuat
                  dalam satu render sinkron begitu tab diklik — dengan belasan
                  temuan itu berarti puluhan Dropdown/AutoComplete/textarea
                  sekaligus, dan tab menggantung beberapa detik. Nilai field ada
                  di state, bukan di DOM, jadi kartu yang belum dimount tetap
                  utuh saat divalidasi dan dikirim. */}
              {jsoFindings.map((finding, idx) => (
                <DeferredMount key={idx} eager={idx < 2}>
                <div className="border-round-xl overflow-hidden shadow-1" style={{ border: '1px solid #c7d2fe' }}>
                  {/* Finding Header */}
                  <div className="flex align-items-center justify-content-between px-3 py-2" style={{ background: 'linear-gradient(90deg, #4f46e5, #7c3aed)' }}>
                    <span className="text-sm font-bold text-white">
                      <i className="pi pi-search mr-2" />Temuan #{idx + 1}
                    </span>
                    <Button icon="pi pi-trash" rounded text size="small" className="text-white" style={{ color: '#fca5a5' }} onClick={() => { const u = [...jsoFindings]; u.splice(idx, 1); setJsoFindings(u); }} />
                  </div>

                  <div className="flex flex-column gap-3 p-3">
                    {/* Dropdowns Row 1 */}
                    <div className="grid">
                      <div className="col-12 md:col-4">
                        <div className="flex flex-column gap-1">
                          <label className="text-xs font-semibold text-600"><i className="pi pi-building text-xs mr-1 text-indigo-400" />Divisi</label>
                          <Dropdown value={finding.divisionId} options={divisionOptions} onChange={(e) => { const u = [...jsoFindings]; u[idx] = { ...u[idx], divisionId: e.value }; setJsoFindings(u); }} placeholder="Pilih divisi" showClear filter />
                        </div>
                      </div>
                      <div className="col-12 md:col-4">
                        <div className="flex flex-column gap-1">
                          <label className="text-xs font-semibold text-600"><i className="pi pi-map text-xs mr-1 text-teal-400" />Area</label>
                          <Dropdown value={finding.areaId} options={areaOptions} onChange={(e) => {
                            // Ganti area = reset turunan lokasi: region/section hanya untuk Plant,
                            // lokasi non-plant hanya untuk Non-Plant
                            const u = [...jsoFindings]; u[idx] = { ...u[idx], areaId: e.value, regionId: undefined, sectionId: undefined, nonPlantLocationId: undefined }; setJsoFindings(u);
                          }} placeholder="Pilih area" showClear filter />
                        </div>
                      </div>
                      {!isNonPlantArea(finding.areaId) && (
                        <div className="col-12 md:col-4">
                          <div className="flex flex-column gap-1">
                            <label className="text-xs font-semibold text-600"><i className="pi pi-globe text-xs mr-1 text-green-400" />Region</label>
                            <Dropdown value={finding.regionId} options={regionOptions} onChange={(e) => {
                              const regionId = e.value;
                              const u = [...jsoFindings]; u[idx] = { ...u[idx], regionId: regionId, sectionId: undefined }; setJsoFindings(u);
                              if (regionId && !sectionsByRegion[regionId]) {
                                locationSectionService.getByRegion(regionId).then((sections) => {
                                  setSectionsByRegion((prev) => ({ ...prev, [regionId]: sections }));
                                }).catch(console.error);
                              }
                            }} placeholder="Pilih region" showClear filter />
                          </div>
                        </div>
                      )}
                    </div>
                    {/* Section — Non-Plant: daftar bangunan/fasilitas non-plant; Plant: section per region */}
                    {isNonPlantArea(finding.areaId) ? (
                      <div className="grid">
                        <div className="col-12">
                          <div className="flex flex-column gap-1">
                            <label className="text-xs font-semibold text-600"><i className="pi pi-th-large text-xs mr-1 text-purple-400" />Section (Non-Plant)</label>
                            <Dropdown
                              value={finding.nonPlantLocationId}
                              options={nonPlantOptions}
                              onChange={(e) => { const u = [...jsoFindings]; u[idx] = { ...u[idx], nonPlantLocationId: e.value }; setJsoFindings(u); }}
                              placeholder="Pilih lokasi non-plant"
                              showClear
                              filter
                            />
                          </div>
                        </div>
                      </div>
                    ) : finding.regionId ? (
                      <div className="grid">
                        <div className="col-12">
                          <div className="flex flex-column gap-1">
                            <label className="text-xs font-semibold text-600"><i className="pi pi-th-large text-xs mr-1 text-purple-400" />Section</label>
                            <Dropdown
                              value={finding.sectionId}
                              options={(sectionsByRegion[finding.regionId] ?? []).map((s) => ({ label: s.name, value: s.id }))}
                              onChange={(e) => { const u = [...jsoFindings]; u[idx] = { ...u[idx], sectionId: e.value }; setJsoFindings(u); }}
                              placeholder={sectionsByRegion[finding.regionId] ? 'Pilih section' : 'Memuat section...'}
                              showClear
                              filter
                              loading={!sectionsByRegion[finding.regionId]}
                            />
                          </div>
                        </div>
                      </div>
                    ) : null}
                    {/* Dropdowns Row 2 */}
                    <div className="grid">
                      <div className="col-12 md:col-6">
                        <div className="flex flex-column gap-1">
                          <label className="text-xs font-semibold text-600">
                            <i className="pi pi-hashtag text-xs mr-1 text-cyan-400" />WR Number
                            {!canEditFindingWr && <i className="pi pi-lock text-xs ml-1 text-500" />}
                          </label>
                          {canEditFindingWr ? (
                            <AutoComplete
                              value={finding.wrNumber ?? ''}
                              suggestions={jsoWrSuggestions}
                              completeMethod={(e) => searchWorkRequests(e, setJsoWrSuggestions)}
                              field="number"
                              itemTemplate={wrItemTemplate}
                              onSelect={(e) => rememberWr(e.value as WorkRequest)}
                              onChange={(e) => {
                                const picked = typeof e.value === 'string' ? null : (e.value as WorkRequest | null);
                                const v = picked ? picked.number : (typeof e.value === 'string' ? e.value : '');
                                setJsoFindings((prev) => prev.map((f, i) => {
                                  if (i !== idx) return f;
                                  // WR berganti berarti WO lama sudah tidak relevan. Dipilih dari
                                  // daftar: WO Avantis-nya langsung ikut. Diketik manual: dikosongkan
                                  // dulu, resolver di atas yang mengisi setelah nomornya dikenali.
                                  const wo = picked ? (picked.workOrder ?? '') : (v === f.wrNumber ? f.wrWoNumber : '');
                                  return { ...f, wrNumber: v, wrWoNumber: wo };
                                }));
                              }}
                              placeholder="Cari nomor WR..."
                              className="w-full"
                              inputClassName="w-full"
                              minLength={2}
                            />
                          ) : readOnlyValue(finding.wrNumber)}
                          {wrInfoCard(finding.wrNumber)}
                        </div>
                      </div>
                      <div className="col-12 md:col-6">
                        <div className="flex flex-column gap-1">
                          <label className="text-xs font-semibold text-600">
                            <i className="pi pi-wrench text-xs mr-1 text-cyan-400" />Nomor WO
                            {!canEditFindingWr && <i className="pi pi-lock text-xs ml-1 text-500" />}
                          </label>
                          {canEditFindingWr ? (
                            <>
                              <InputText
                                value={finding.wrWoNumber ?? ''}
                                onChange={(e) => setJsoFindings((prev) => prev.map((f, i) =>
                                  i === idx ? { ...f, wrWoNumber: e.target.value } : f))}
                                placeholder="Terisi otomatis dari WR"
                                className="w-full"
                              />
                              <small className="text-500">
                                Diambil dari WO milik nomor WR di Avantis; boleh diubah bila WO-nya berbeda.
                              </small>
                            </>
                          ) : (
                            <>
                              {readOnlyValue(finding.wrWoNumber)}
                              <small className="text-500">
                                <i className="pi pi-lock mr-1" />
                                Nomor WR &amp; WO hanya dapat diubah oleh Administrator.
                              </small>
                            </>
                          )}
                        </div>
                      </div>
                    </div>

                    {/* Category Matrix Selector */}
                    <ObservationCategoryMatrix
                      selectedCategoryId={finding.categoryId}
                      onSelect={(catId) => {
                        const u = [...jsoFindings];
                        u[idx] = { ...u[idx], categoryId: catId || undefined };
                        setJsoFindings(u);
                      }}
                    />

                    {/* Textareas — color-coded */}
                    <div className="grid">
                      <div className="col-12 md:col-6">
                        <div className="flex flex-column gap-1 p-2 border-round-lg" style={{ background: '#fef9c3', border: '1px solid #facc15' }}>
                          <label className="text-xs font-bold" style={{ color: '#854d0e' }}><i className="pi pi-exclamation-triangle text-xs mr-1" />Temuan <span className="text-red-500">*</span></label>
                          <InputTextarea value={finding.findings ?? ''} onChange={(e) => { const u = [...jsoFindings]; u[idx] = { ...u[idx], findings: e.target.value }; setJsoFindings(u); }} rows={3} autoResize placeholder="Deskripsikan temuan..." />
                        </div>
                      </div>
                      <div className="col-12 md:col-6">
                        <div className="flex flex-column gap-1 p-2 border-round-lg" style={{ background: '#fee2e2', border: '1px solid #fca5a5' }}>
                          <label className="text-xs font-bold" style={{ color: '#991b1b' }}><i className="pi pi-bolt text-xs mr-1" />Root Cause <span className="text-red-500">*</span></label>
                          <InputTextarea value={finding.rootCauseJso ?? ''} onChange={(e) => { const u = [...jsoFindings]; u[idx] = { ...u[idx], rootCauseJso: e.target.value }; setJsoFindings(u); }} rows={3} autoResize placeholder="Penyebab akar masalah..." />
                        </div>
                      </div>
                    </div>
                    <div className="grid">
                      <div className="col-12 md:col-6">
                        <div className="flex flex-column gap-1 p-2 border-round-lg" style={{ background: '#fce7f3', border: '1px solid #f9a8d4' }}>
                          <label className="text-xs font-bold" style={{ color: '#831843' }}><i className="pi pi-shield text-xs mr-1" />Potensi Bahaya <span className="text-red-500">*</span></label>
                          <InputTextarea value={finding.potentialHazard ?? ''} onChange={(e) => { const u = [...jsoFindings]; u[idx] = { ...u[idx], potentialHazard: e.target.value }; setJsoFindings(u); }} rows={3} autoResize placeholder="Potensi bahaya yang ditemukan..." />
                        </div>
                      </div>
                      <div className="col-12 md:col-6">
                        <div className="flex flex-column gap-1 p-2 border-round-lg" style={{ background: '#d1fae5', border: '1px solid #6ee7b7' }}>
                          <label className="text-xs font-bold" style={{ color: '#065f46' }}><i className="pi pi-check-circle text-xs mr-1" />Rekomendasi <span className="text-red-500">*</span></label>
                          <InputTextarea value={finding.recommendation ?? ''} onChange={(e) => { const u = [...jsoFindings]; u[idx] = { ...u[idx], recommendation: e.target.value }; setJsoFindings(u); }} rows={3} autoResize placeholder="Rekomendasi perbaikan..." />
                        </div>
                      </div>
                    </div>

                    {/* Foto per temuan — bukti melekat pada temuan yang dimaksud,
                        bukan satu tumpukan untuk seluruh observasi. */}
                    <div className="flex flex-column gap-2 p-2 border-round-lg" style={{ background: '#eff6ff', border: '1px solid #bfdbfe' }}>
                      <label className="text-xs font-bold" style={{ color: '#1e40af' }}>
                        <i className="pi pi-images text-xs mr-1" />Foto Temuan
                      </label>
                      <PhotoPicker
                        inputId={`jso-finding-photo-${idx}`}
                        size={96}
                        label="Tambah Foto"
                        hint="JPEG, PNG, GIF, WEBP — maks. 10MB per file"
                        disabled={uploading}
                        existing={parsePhotoList(finding.photos)}
                        onExistingChange={(next) => {
                          const u = [...jsoFindings];
                          u[idx] = { ...u[idx], photos: JSON.stringify(next) };
                          setJsoFindings(u);
                        }}
                        files={finding.photoFiles ?? NO_FILES}
                        onFilesChange={(next) => {
                          const u = [...jsoFindings];
                          u[idx] = { ...u[idx], photoFiles: next };
                          setJsoFindings(u);
                        }}
                      />
                    </div>
                  </div>
                </div>
                </DeferredMount>
              ))}
            </div>
          </TabPanel>
        )}

        {/* Tab: Alur Persetujuan — edit only, Admin Safety / Super Admin only */}
        {isEdit && isAdminOrSuperAdmin && (
          <TabPanel header="Alur Persetujuan" leftIcon="pi pi-sitemap mr-2">
            <div className="flex flex-column gap-4 p-2">
              <p className="m-0 text-xs text-500">
                <i className="mr-1 pi pi-info-circle" />
                Atur status dan pengguna yang bertanggung jawab di setiap tahap alur persetujuan observasi ini.
              </p>

              {/* Status Dropdown */}
              <div className="flex flex-column gap-1">
                <label className="text-sm font-semibold text-700">
                  <i className="mr-1 text-green-600 pi pi-sitemap" />Status Alur Persetujuan
                </label>
                <Dropdown
                  value={obsStatus}
                  options={OBS_STATUS_OPTIONS}
                  onChange={(e) => setObsStatus(e.value)}
                  placeholder="Pilih status"
                />
                <small className="text-500">Ubah status untuk memajukan atau mengembalikan posisi observasi dalam alur persetujuan.</small>
              </div>

              <div className="grid">
                <div className="col-12 md:col-6">
                  <div className="flex flex-column gap-1">
                    <label className="text-sm font-semibold text-700">
                      <i className="mr-1 text-blue-500 pi pi-search" />Peninjau QSHE
                      <span className="ml-1 text-xs font-normal text-500">(Petugas yang meninjau laporan)</span>
                    </label>
                    <Dropdown
                      value={reviewedById}
                      options={userOptions}
                      onChange={(e) => setReviewedById(e.value)}
                      placeholder="Pilih peninjau"
                      showClear filter
                    />
                  </div>
                </div>
                <div className="col-12 md:col-6">
                  <div className="flex flex-column gap-1">
                    <label className="text-sm font-semibold text-700">
                      <i className="mr-1 text-orange-500 pi pi-wrench" />Tindak Lanjut Oleh
                      <span className="ml-1 text-xs font-normal text-500">(Pelaksana / Action By)</span>
                    </label>
                    <Dropdown
                      value={actionById}
                      options={userOptions}
                      onChange={(e) => setActionById(e.value)}
                      placeholder="Pilih pelaksana"
                      showClear filter
                    />
                  </div>
                </div>
                <div className="col-12 md:col-6">
                  <div className="flex flex-column gap-1">
                    <label className="text-sm font-semibold text-700">
                      <i className="mr-1 text-teal-500 pi pi-user" />Ditugaskan Kepada
                      <span className="ml-1 text-xs font-normal text-500">(Bawahan yang ditugaskan)</span>
                    </label>
                    <Dropdown
                      value={assignedToId}
                      options={userOptions}
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

      {/* ── Foto level observasi ──
          Mode NCR: ini foto laporannya. Mode JSO: foto sudah pindah ke tiap
          temuan, jadi kartu ini hanya muncul untuk observasi lama yang terlanjur
          menyimpan foto di header — bisa dilihat dan dibuang, tidak ditambah. */}
      {(!isJso || existingPhotos.length > 0) && (
        <div className="p-4 surface-card border-round-xl shadow-2">
          <h3 className="m-0 mb-1 text-base font-bold text-700">
            <i className="mr-2 text-green-500 pi pi-images" />
            {isJso ? 'Foto Observasi (lama)' : 'Foto Observasi'}
          </h3>
          {isJso && (
            <p className="mt-0 mb-3 text-xs text-500">
              <i className="mr-1 pi pi-info-circle" />
              Foto sekarang diunggah per temuan di tab "Temuan JSO". Foto di bawah tersimpan
              sebelum perubahan tersebut dan hanya bisa dihapus dari sini.
            </p>
          )}
          <PhotoPicker
            inputId="safety-photo-input"
            existing={existingPhotos}
            onExistingChange={setExistingPhotos}
            files={photoFiles}
            onFilesChange={setPhotoFiles}
            disabled={uploading}
            hideAdd={isJso}
          />
        </div>
      )}

      {/* ── Alur Persetujuan — only for Administrator Safety / Super Administrator in edit mode ── */}
      {isEdit && isAdminOrSuperAdmin && fullObs && (
        <Card
          title={
            <div className="flex align-items-center gap-2">
              <i className="pi pi-list-check text-blue-500" />
              <span>Alur Persetujuan</span>
            </div>
          }
          className="shadow-2 border-round-xl"
        >
          <FormWorkflowTimeline obs={fullObs} userMap={userMap} onAction={openWorkflowDialog} />
        </Card>
      )}

      {/* ── Sticky Actions ── */}
      <div className="flex justify-content-between align-items-center p-3 border-round-xl shadow-3 surface-card" style={{ position: 'sticky', bottom: 16, zIndex: 10 }}>
        <span className="text-xs text-500 hidden md:inline">
          <i className="pi pi-info-circle mr-1" />
          {isJso ? `Mode JSO — ${jsoFindings.length} temuan` : 'Mode NCR'}
        </span>
        <div className="flex gap-2">
          <Button
            label="Batal"
            icon="pi pi-times"
            outlined
            severity="secondary"
            className="font-semibold border-round-xl shadow-1"
            style={{ gap: '0.6rem', padding: '0.75rem 1.75rem', fontSize: '0.925rem' }}
            onClick={() => navigate('/dashboard/irms/safety-observations')}
          />
          {isEdit && obsStatus === 'returned' ? (
            <Button
              label="Simpan & Ajukan Kembali"
              icon="pi pi-replay"
              loading={submitting}
              onClick={async () => {
                if (!validate()) return;
                setSubmitting(true);
                try {
                  await saveObservation();
                  await safetyObservationService.resubmit(Number(id));
                  toast.current?.show({
                    severity: 'success',
                    summary: 'Berhasil',
                    detail: 'Observasi direvisi dan diajukan kembali',
                    life: 3000,
                  });
                  setTimeout(() => navigate('/dashboard/irms/safety-observations'), 1200);
                } catch {
                  toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: 'Gagal mengajukan kembali observasi',
                    life: 3000,
                  });
                } finally {
                  setSubmitting(false);
                }
              }}
              className="font-semibold border-round-xl shadow-2"
              style={{
                background: 'linear-gradient(135deg, #e65100 0%, #ff9800 100%)',
                border: 'none',
                gap: '0.6rem',
                padding: '0.75rem 1.75rem',
                fontSize: '0.925rem',
              }}
            />
          ) : (
            <Button
              label={isEdit ? 'Simpan Perubahan' : 'Buat Observasi'}
              icon={isEdit ? 'pi pi-check' : 'pi pi-plus'}
              loading={submitting}
              onClick={handleSubmit}
              className="font-semibold border-round-xl shadow-2"
              style={{
                background: isEdit
                  ? 'linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)'
                  : 'linear-gradient(135deg, #16a34a 0%, #15803d 100%)',
                border: 'none',
                gap: '0.6rem',
                padding: '0.75rem 1.75rem',
                fontSize: '0.925rem',
              }}
            />
          )}
        </div>
      </div>

      {/* ── Workflow Action Dialog ── */}
      <Dialog
        header={getWorkflowDialogTitle()}
        visible={showWorkflowDialog}
        style={{ width: '500px' }}
        onHide={() => setShowWorkflowDialog(false)}
        footer={
          <div>
            <Button label="Batal" icon="pi pi-times" severity="secondary" outlined className="border-round-lg shadow-1 font-semibold mr-2" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }} onClick={() => setShowWorkflowDialog(false)} />
            <Button
              label={workflowAction === 'reject' ? 'Tolak' : 'Konfirmasi'}
              icon={workflowAction === 'reject' ? 'pi pi-times' : 'pi pi-check'}
              severity={workflowAction === 'reject' ? 'danger' : undefined}
              className="border-round-lg shadow-1 font-semibold"
              style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }}
              onClick={handleWorkflowSubmit}
              disabled={isWorkflowSubmitDisabled()}
            />
          </div>
        }
      >
        <div className="flex flex-column gap-3">
          {['review', 'assign-pic', 'delegate', 'submit-action'].includes(workflowAction) && (
            <div className="flex flex-column gap-1">
              <label className="font-medium text-sm">
                {workflowAction === 'review' ? 'Reviewer (Dept. QSHE)' :
                 workflowAction === 'assign-pic' ? 'Tim Terkait (Spv/Eng)' :
                 workflowAction === 'delegate' ? 'Delegasi Ke' : 'Pelaksana (Spv/Eng)'}
                <span className="text-red-500"> *</span>
              </label>
              <Dropdown value={wfSelectedUser} options={userOptions} onChange={(e) => setWfSelectedUser(e.value)} placeholder="Pilih pengguna..." filter showClear />
            </div>
          )}
          {workflowAction === 'review' && (
            <div className="flex flex-column gap-1">
              <label className="font-medium text-sm">Departemen Terkait <span className="text-red-500">*</span></label>
              <Dropdown value={wfDesignationDeptId} options={designationDeptOptions} onChange={(e) => setWfDesignationDeptId(e.value)} placeholder="Pilih departemen terkait..." filter showClear />
              <small className="text-500">QSHE menentukan departemen yang bertanggung jawab untuk tindak lanjut</small>
            </div>
          )}
          {workflowAction === 'delegate' && (
            <div className="flex flex-column gap-1">
              <label className="font-medium text-sm">Catatan Delegasi</label>
              <InputTextarea value={wfNote} onChange={(e) => setWfNote(e.target.value)} rows={3} placeholder="Alasan delegasi..." />
            </div>
          )}
          {workflowAction === 'submit-action' && (
            <div className="flex flex-column gap-1">
              <label className="font-medium text-sm">Tindak Lanjut Temuan <span className="text-red-500">*</span></label>
              <InputTextarea value={wfCorrective} onChange={(e) => setWfCorrective(e.target.value)} rows={4} placeholder="Deskripsi tindak lanjut temuan yang telah dilakukan..." />
            </div>
          )}
          {workflowAction === 'reject' && (
            <div className="flex flex-column gap-1">
              <label className="font-medium text-sm">Alasan Penolakan <span className="text-red-500">*</span></label>
              <InputTextarea value={wfReason} onChange={(e) => setWfReason(e.target.value)} rows={3} placeholder="Alasan penolakan..." />
            </div>
          )}
          {workflowAction === 'return-to-creator' && (
            <div className="flex flex-column gap-1">
              <label className="font-medium text-sm">Komentar untuk Creator <span className="text-red-500">*</span></label>
              <InputTextarea value={wfComment} onChange={(e) => setWfComment(e.target.value)} rows={3} placeholder="Jelaskan data yang perlu dilengkapi atau direvisi..." />
            </div>
          )}
          {workflowAction === 'resubmit' && (
            <p className="text-sm text-700">Apakah Anda yakin data sudah lengkap dan ingin mengajukan kembali laporan ini untuk ditinjau?</p>
          )}
          {workflowAction === 'complete' && (
            <p className="text-sm text-700">Sebagai Administrator Safety, apakah Anda telah memverifikasi tindak lanjut temuan yang diinput oleh subordinate dan ingin menutup laporan ini? (CHECKLIST Closed)</p>
          )}
        </div>
      </Dialog>
    </div>
  );
};

// ============================================================
// FORM WORKFLOW TIMELINE COMPONENT
// ============================================================
interface FormWorkflowTimelineProps {
  obs: SafetyObservation;
  userMap: Map<number, string>;
  onAction: (action: string) => void;
}

const FORM_STAGE_ORDER = ['open', 'reviewed', 'in_progress', 'action_taken', 'closed'] as const;

function getFormStageIndex(status: string, isJso?: boolean): number {
  if (status === 'rejected') return -1;
  if (status === 'returned') return 0;
  if (status === 'approved_partially') return 1;
  if (isJso) {
    switch (status) {
      case 'reviewed': return 2;
      case 'in_progress': return 3;
      case 'action_taken': return 4;
      case 'closed': return 5;
      default: return 0;
    }
  }
  const idx = FORM_STAGE_ORDER.indexOf(status as typeof FORM_STAGE_ORDER[number]);
  return idx >= 0 ? idx : 0;
}

const FORM_STAGE_CONFIGS = [
  { key: 'open',         label: 'Pelaporan Awal',           icon: 'pi pi-file',         roleLabel: 'Karyawan / User Creator' },
  { key: 'reviewed',     label: 'Tinjauan QSHE',            icon: 'pi pi-search',       roleLabel: 'Administrator Safety' },
  { key: 'in_progress',  label: 'Analisis & Penugasan Tim', icon: 'pi pi-chart-bar',    roleLabel: 'Superintendent Dept. Terkait' },
  { key: 'action_taken', label: 'Tindak Lanjut Temuan',     icon: 'pi pi-wrench',       roleLabel: 'Subordinate (Bawahan yang Ditunjuk)' },
  { key: 'closed',       label: 'Verifikasi & Penutupan',   icon: 'pi pi-check-circle', roleLabel: 'Administrator Safety' },
];

const FORM_STAGE_COLORS: Record<string, { bg: string; border: string; text: string; line: string }> = {
  completed: { bg: '#e8f5e9', border: '#4caf50', text: '#2e7d32', line: '#4caf50' },
  active:    { bg: '#e3f2fd', border: '#2196f3', text: '#1565c0', line: '#bdbdbd' },
  pending:   { bg: '#f5f5f5', border: '#bdbdbd', text: '#9e9e9e', line: '#bdbdbd' },
  rejected:  { bg: '#ffebee', border: '#f44336', text: '#c62828', line: '#f44336' },
  returned:  { bg: '#fff3e0', border: '#ff9800', text: '#e65100', line: '#ff9800' },
};

function getFormStageStatus(
  stageIdx: number, currentIdx: number, isRejected: boolean, isReturned: boolean
): 'completed' | 'active' | 'pending' | 'rejected' | 'returned' {
  if (isRejected) return stageIdx <= currentIdx ? 'rejected' : 'pending';
  if (isReturned && stageIdx === 0) return 'returned';
  if (isReturned) return 'pending';
  if (stageIdx < currentIdx) return 'completed';
  if (stageIdx === currentIdx) return 'active';
  return 'pending';
}

const FormWorkflowTimeline: React.FC<FormWorkflowTimelineProps> = ({ obs, userMap, onAction }) => {
  const isRejected = obs.status === 'rejected';
  const isReturned = obs.status === 'returned';
  const currentIdx = getFormStageIndex(obs.status, obs.isJso);

  const getUserName = (userId?: number) => {
    if (!userId) return null;
    return userMap.get(userId) ?? `User #${userId}`;
  };

  const formatDateTime = (dateStr?: string) => {
    if (!dateStr) return null;
    return new Date(dateStr).toLocaleDateString('id-ID', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  };

  const getStageInfo = (stageIdx: number) => {
    switch (stageIdx) {
      case 0: return { person: getUserName(obs.reporterId), date: formatDateTime(obs.createdAt) };
      case 1: return { person: getUserName(obs.reviewedById), date: null, deptTerkait: obs.designationDepartment?.name };
      case 2: {
        const hasDelegation = !!obs.delegationNote;
        return { person: getUserName(obs.assignedToId), date: formatDateTime(obs.assignedAt), hasDelegation, delegationNote: obs.delegationNote };
      }
      case 3: return { person: getUserName(obs.actionById), date: formatDateTime(obs.actionTakenAt), corrective: obs.corrective };
      case 4: return { person: null, date: formatDateTime(obs.doneAt) };
      default: return { person: null, date: null };
    }
  };

  // Get available actions based on current status (not tied to stage index)
  const getStatusActions = (): { action: string; label: string; icon: string; severity: 'success' | 'info' | 'warning' | 'danger' }[] => {
    if (isRejected || obs.status === 'closed') return [];
    if (isReturned) return [{ action: 'resubmit', label: 'Ajukan Kembali', icon: 'pi pi-replay', severity: 'success' }];

    const actions: { action: string; label: string; icon: string; severity: 'success' | 'info' | 'warning' | 'danger' }[] = [];
    switch (obs.status) {
      case 'open':
        actions.push({ action: 'review', label: 'Data Jelas — Tinjau', icon: 'pi pi-check', severity: 'success' });
        actions.push({ action: 'return-to-creator', label: 'Data Belum Jelas', icon: 'pi pi-replay', severity: 'warning' });
        break;
      case 'approved_partially':
        actions.push({ action: 'review', label: 'Validasi & Teruskan ke Superintendent', icon: 'pi pi-check', severity: 'success' });
        break;
      case 'reviewed':
        actions.push({ action: 'assign-pic', label: 'Tugaskan Subordinate', icon: 'pi pi-user-plus', severity: 'success' });
        break;
      case 'in_progress':
        actions.push({ action: 'delegate', label: 'Delegasi', icon: 'pi pi-share-alt', severity: 'warning' });
        actions.push({ action: 'submit-action', label: 'Submit Tindak Lanjut', icon: 'pi pi-check', severity: 'success' });
        break;
      case 'action_taken':
        actions.push({ action: 'complete', label: 'Verifikasi & Tutup (Admin Safety)', icon: 'pi pi-check-circle', severity: 'success' });
        break;
    }
    return actions;
  };

  const getAvailableActions = (stageIdx: number) => {
    // Render stage-specific actions only when this stage is active
    if (stageIdx !== currentIdx) return [];
    return getStatusActions();
  };

  const allActions = getStatusActions();

  return (
    <div className="flex flex-column gap-0">
      {FORM_STAGE_CONFIGS.map((stage, idx) => {
        const stageStatus = getFormStageStatus(idx, currentIdx, isRejected, isReturned);
        const colors = FORM_STAGE_COLORS[stageStatus];
        const info = getStageInfo(idx);
        const actions = getAvailableActions(idx);
        const isLast = idx === FORM_STAGE_CONFIGS.length - 1;

        return (
          <div key={stage.key} className="flex gap-3" style={{ minHeight: '80px' }}>
            {/* Timeline indicator */}
            <div className="flex flex-column align-items-center" style={{ width: '36px' }}>
              <div
                className="flex align-items-center justify-content-center border-circle"
                style={{ width: '36px', height: '36px', backgroundColor: colors.bg, border: `2px solid ${colors.border}`, flexShrink: 0 }}
              >
                <i className={stageStatus === 'completed' ? 'pi pi-check' : stage.icon} style={{ fontSize: '0.9rem', color: colors.text }} />
              </div>
              {!isLast && <div style={{ width: '2px', flex: 1, backgroundColor: colors.line, minHeight: '20px' }} />}
            </div>

            {/* Content */}
            <div className="flex flex-column pb-3" style={{ flex: 1 }}>
              <div className="flex align-items-center gap-2 mb-1">
                <span className="text-sm font-bold" style={{ color: colors.text }}>{stage.label}</span>
                {stageStatus === 'completed' && <Tag value="Selesai" severity="success" className="text-xs" />}
                {stageStatus === 'active' && <Tag value="Berjalan" severity="info" className="text-xs" />}
                {stageStatus === 'pending' && <Tag value="Menunggu" severity="secondary" className="text-xs" />}
                {stageStatus === 'rejected' && <Tag value="Ditolak" severity="danger" className="text-xs" />}
                {stageStatus === 'returned' && <Tag value="Dikembalikan" severity="warning" className="text-xs" />}
              </div>
              <span className="text-xs text-500 mb-1">{stage.roleLabel}</span>
              {info.person && <span className="text-sm font-medium text-800">{info.person}</span>}
              {info.date && <span className="text-xs text-500">{info.date}</span>}

              {/* Dept. Terkait (stage 1) */}
              {idx === 1 && (info as { deptTerkait?: string }).deptTerkait && (
                <div className="mt-1 p-2 border-round" style={{ backgroundColor: '#e3f2fd', border: '1px solid #bbdefb' }}>
                  <span className="text-xs font-medium" style={{ color: '#1565c0' }}>
                    <i className="pi pi-building mr-1" style={{ fontSize: '0.7rem' }} />
                    Dept. Terkait: {(info as { deptTerkait?: string }).deptTerkait}
                  </span>
                </div>
              )}

              {/* Delegation (stage 2) */}
              {idx === 2 && (info as { hasDelegation?: boolean; delegationNote?: string }).hasDelegation && (
                <div className="mt-1 p-2 border-round" style={{ backgroundColor: '#fff3e0', border: '1px solid #ffe0b2' }}>
                  <span className="text-xs font-medium" style={{ color: '#e65100' }}>
                    <i className="pi pi-share-alt mr-1" style={{ fontSize: '0.7rem' }} />Didelegasikan
                  </span>
                  {(info as { delegationNote?: string }).delegationNote && (
                    <p className="text-xs mt-1 mb-0" style={{ color: '#bf360c' }}>{(info as { delegationNote?: string }).delegationNote}</p>
                  )}
                </div>
              )}

              {/* Corrective (stage 3) */}
              {idx === 3 && (info as { corrective?: string }).corrective && (
                <div className="mt-1 p-2 border-round" style={{ backgroundColor: '#e8f5e9', border: '1px solid #c8e6c9' }}>
                  <span className="text-xs font-medium" style={{ color: '#2e7d32' }}>Tindak Lanjut Temuan:</span>
                  <p className="text-xs mt-1 mb-0" style={{ color: '#1b5e20' }}>{(info as { corrective?: string }).corrective}</p>
                </div>
              )}

              {/* Rejection reason */}
              {isRejected && obs.reason && idx === currentIdx && (
                <div className="mt-1 p-2 border-round" style={{ backgroundColor: '#ffebee', border: '1px solid #ffcdd2' }}>
                  <span className="text-xs font-medium" style={{ color: '#c62828' }}>Alasan Penolakan:</span>
                  <p className="text-xs mt-1 mb-0" style={{ color: '#b71c1c' }}>{obs.reason}</p>
                </div>
              )}

              {/* Return comment */}
              {isReturned && obs.reason && idx === 0 && (
                <div className="mt-1 p-2 border-round" style={{ backgroundColor: '#fff3e0', border: '1px solid #ffe0b2' }}>
                  <span className="text-xs font-medium" style={{ color: '#e65100' }}>
                    <i className="pi pi-replay mr-1" style={{ fontSize: '0.7rem' }} />Komentar QSHE:
                  </span>
                  <p className="text-xs mt-1 mb-0" style={{ color: '#bf360c' }}>{obs.reason}</p>
                </div>
              )}

              {/* Action buttons */}
              {actions.length > 0 && (
                <div className="flex gap-2 mt-2 flex-wrap">
                  {actions.map((a) => (
                    <Button key={a.action} label={a.label} icon={a.icon} severity={a.severity} size="small" className="border-round-lg shadow-1 font-semibold" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.5rem 1rem' }} onClick={() => onAction(a.action)} />
                  ))}
                </div>
              )}
            </div>
          </div>
        );
      })}

      {/* Reject button */}
      {!isRejected && !isReturned && obs.status !== 'closed' && (
        <>
          <Divider className="my-2" />
          {/* Prominent "Aksi Berikutnya" panel for Admin Safety */}
          {allActions.length > 0 && (
            <div className="p-3 mb-3 border-round-xl" style={{ background: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)', border: '1px solid #93c5fd' }}>
              <div className="flex align-items-center gap-2 mb-2">
                <i className="pi pi-bolt text-blue-600" />
                <span className="text-sm font-bold text-blue-800">Aksi Berikutnya (Status: {obs.status})</span>
              </div>
              <div className="flex gap-2 flex-wrap">
                {allActions.map((a) => (
                  <Button
                    key={a.action}
                    label={a.label}
                    icon={a.icon}
                    severity={a.severity}
                    size="small"
                    className="border-round-lg shadow-1 font-semibold"
                    style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }}
                    onClick={() => onAction(a.action)}
                  />
                ))}
              </div>
            </div>
          )}
          <Button
            label="Tolak Observasi"
            icon="pi pi-times"
            severity="danger"
            outlined
            size="small"
            className="w-full border-round-lg shadow-1 font-semibold"
            style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }}
            onClick={() => onAction('reject')}
          />
        </>
      )}
    </div>
  );
};

export default SafetyObservationForm;
