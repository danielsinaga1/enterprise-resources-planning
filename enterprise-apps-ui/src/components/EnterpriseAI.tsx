import {
  useState,
  useRef,
  useEffect,
  useMemo,
  useCallback,
  type CSSProperties,
  type KeyboardEvent,
} from "react";
import { useLocation } from "react-router-dom";
import ragService from "../services/ragService";
import type { RagDocument } from "../types/rag";

/* =========================================================================
 * EnterpriseAI — AI-powered chat popup widget
 * -------------------------------------------------------------------------
 * Asisten AI global untuk semua modul aplikasi Enterprise.
 * Ditenagai Gemini lewat backend proxy (Spring, /api/v1/assistant/chat) —
 * API key tinggal di server. Request memakai JWT dari sesi login yang
 * sama (localStorage access_token).
 * Grounding: sebelum bertanya ke Gemini, widget mencari potongan dokumen
 * relevan di service RAG (/api/rag/documents/search — retrieval saja) dan
 * menyuntikkannya ke system prompt, sehingga jawaban ikut berdasar pada
 * dokumen yang di-upload lewat panel Dokumen (tombol 📄) di widget ini.
 * ========================================================================= */

/* ----------------------------- Tipe data ------------------------------- */

export interface EnterpriseModule {
  /** ID unik & label tab, mis. "IRMS" */
  id: string;
  /** Nama tampil (default = id) */
  name?: string;
  /** Deskripsi fungsi modul — di-inject ke system prompt */
  description?: string;
  /** Pola path yang menandai modul ini aktif, mis. "/irms". Boleh lebih dari satu. */
  pathMatch?: string | string[];
}

type ModuleInput = string | EnterpriseModule;

export interface EnterpriseAIProps {
  /** Nama aplikasi, mis. "Enterprise Apps" */
  appName?: string;
  /** Daftar modul yang didukung */
  modules?: ModuleInput[];
  /** Konteks bisnis tambahan (data laporan, inventory, dll) untuk system prompt */
  businessContext?: string;
  /** Endpoint backend proxy (default: /api/v1/assistant/chat lewat Vite/gateway) */
  apiUrl?: string;
}

/** Aksi tulis yang diusulkan KPIntar dan menunggu persetujuan pengguna (kartu konfirmasi). */
/** Galat permintaan chat; jangan_ulangi disetel saat backend sudah memastikan retry percuma. */
type ChatError = Error & { jangan_ulangi?: boolean };

interface PendingAction {
  tool: string;
  arguments: Record<string, unknown>;
  summary: string;
}

interface ChatMessage {
  role: "user" | "assistant";
  content: string;
  /** Bila ada, bubble ini dirender sebagai kartu konfirmasi aksi, bukan teks biasa. */
  pendingAction?: PendingAction;
  /** Status kartu aksi: pending (menunggu), confirming (sedang dieksekusi), done, cancelled. */
  actionStatus?: "pending" | "confirming" | "done" | "cancelled";
}

/* --------------------------- Konstanta UI ------------------------------ */

const ACCENT = "#4f46e5"; // indigo-600
const ACCENT_2 = "#7c3aed"; // violet-600
const FONT =
  "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif";

const DEFAULT_MODULES: EnterpriseModule[] = [
  {
    id: "Dashboard",
    description:
      "Ringkasan & statistik lintas modul: kartu KPI, grafik tren, dan notifikasi terbaru.",
    pathMatch: "/dashboard",
  },
  {
    id: "IRMS",
    name: "IRMS",
    description:
      "Incident Reporting & Management System: laporan insiden, task tindak lanjut insiden, dan kalender penugasan. Observasi keselamatan dipisah ke modul SO.",
    pathMatch: "/irms",
  },
  {
    id: "SO",
    name: "SO",
    description:
      "Safety Observation (bagian IRMS): observasi keselamatan NCR & JSO, tugas tindak lanjut temuan, serta alur persetujuan lima tahap dari pelaporan sampai closed.",
    pathMatch: ["/irms/safety-observations", "/irms/observation-tasks"],
  },
  {
    id: "LIMS",
    name: "LIMS",
    description:
      "Laboratory Information Management System: laporan laboratorium, daily report, inventory alat lab, kalibrasi & service.",
    pathMatch: "/lims",
  },
  {
    id: "Settings",
    description:
      "Pengaturan: pengguna, role & hak akses, master data, audit log, backup, dan template email.",
    pathMatch: "/settings",
  },
];

const DEFAULT_QUICK_PROMPTS = [
  "Modul apa saja yang tersedia di aplikasi ini? 🧭",
  "Bagaimana cara membuat Incident Report baru? 📝",
  "Jelaskan fungsi utama modul LIMS 🔬",
  "Apa itu Safety Observation di IRMS? 🦺",
  "Cara menambah data inventory alat lab? 📦",
  "Tips memanfaatkan Dashboard untuk monitoring 📊",
];

/* --- Safety observation: pertanyaan cepat per halaman -------------------- *
 * Alurnya lima tahap dan tiap tahap hanya boleh dijalankan peran tertentu,
 * sehingga "sekarang giliran siapa / saya harus apa" jadi pertanyaan paling
 * sering di modul ini. Pertanyaan dipisah per halaman supaya yang muncul
 * selalu nyambung dengan yang sedang dilihat pengguna. */

const SO_FORM_PROMPTS = [
  "Temuan saya ini sebaiknya dilaporkan sebagai NCR atau JSO? 🤔",
  "Apa saja yang wajib diisi sebelum laporan bisa disimpan? ✍️",
  "Kapan nomor WR wajib saya isi? 🔧",
  "Bagaimana menulis deskripsi temuan yang jelas? 💡",
  "Untuk JSO, siapa yang diisi sebagai Leader dan Member? 👥",
  "Setelah saya simpan, laporan ini masuk ke siapa? ➡️",
];

const SO_DETAIL_PROMPTS = [
  "Observasi ini ada di tahap apa, dan langkah berikutnya apa? ➡️",
  "Siapa yang harus bertindak untuk observasi ini sekarang? 👤",
  "Kenapa saya tidak melihat tombol aksi apa pun di sini? 🔒",
  "Laporan saya diminta revisi — apa yang harus saya lakukan? 🔁",
  "Apa syarat supaya observasi ini bisa ditutup (closed)? ✅",
  "Ringkas isi observasi ini untuk saya 📄",
];

const SO_LIST_PROMPTS = [
  "Jelaskan alur safety observation dari dibuat sampai closed 🔄",
  "Apa bedanya NCR dan JSO, dan kapan saya pakai masing-masing? 🆚",
  "Berapa observasi yang masih terbuka tahun ini? 📊",
  "Observasi mana yang masih menunggu tindak lanjut? ⏰",
  "Siapa yang berhak approve, menugaskan, dan menutup laporan? 🧑‍⚖️",
  "Bantu saya buat observasi keselamatan baru 📝",
];

const SO_TASK_PROMPTS = [
  "Bagaimana cara menyelesaikan tugas observasi? 🎯",
  "Apa isi catatan penyelesaian yang baik? ✍️",
  "Setelah saya selesaikan, tugas ini lanjut ke mana? 🔍",
  "Apa yang terjadi kalau tugas lewat jatuh tempo (overdue)? ⏰",
];

/* Dipakai saat tab SO aktif tapi pengguna belum berada di halaman observasi —
   mis. membuka widget dari dashboard lalu memilih tab SO. Isinya condong ke
   pertanyaan "saya harus apa" karena itu kebutuhan tersering di modul ini. */
const SO_MODULE_PROMPTS = [
  "Apa langkah selanjutnya untuk observasi keselamatan saya? ➡️",
  "Jelaskan alur safety observation dari dibuat sampai closed 🔄",
  "Apa bedanya NCR dan JSO, dan kapan saya pakai masing-masing? 🆚",
  "Siapa yang berhak approve, menugaskan, dan menutup laporan? 🧑‍⚖️",
  "Bantu saya buat observasi keselamatan baru 🦺",
  "Observasi mana yang masih menunggu tindak lanjut? ⏰",
];

/**
 * Bekal alur kerja safety observation untuk system prompt. Tool hanya mengembalikan status
 * mentah ("reviewed", "action_taken"), jadi tanpa peta tahap → peran → aksi ini jawaban
 * "apa langkah selanjutnya" cuma tebakan model.
 */
const SAFETY_OBSERVATION_GUIDE = [
  ``,
  `PANDUAN MODUL SAFETY OBSERVATION (IRMS):`,
  `Dua tipe laporan: NCR (Non-Conformance Report — temuan ketidaksesuaian, dilaporkan siapa`,
  `saja saat menemukan kondisi/tindakan tidak aman) dan JSO (Job Safety Observation —`,
  `observasi terjadwal oleh tim Leader + 2 Member, satu laporan memuat beberapa temuan yang`,
  `masing-masing punya divisi dan PIC sendiri).`,
  `Tahap dan pemegang aksinya:`,
  `1. open / approved_partially — Administrator Safety (QSHE) meninjau: "Approve" untuk`,
  `   melanjutkan, atau "Revise" untuk mengembalikan ke pembuat (status jadi returned).`,
  `2. returned — Pembuat laporan melengkapi data lalu menekan "Ajukan Kembali".`,
  `3. reviewed — Superintendent dari divisi temuan menugaskan subordinate sebagai PIC.`,
  `4. in_progress — PIC yang ditunjuk mengisi "Submit Tindak Lanjut".`,
  `5. action_taken — Administrator Safety memverifikasi tindak lanjut lalu menutup laporan.`,
  `6. closed — selesai. Status lain: rejected (ditolak QSHE), overdue (lewat batas waktu).`,
  `Pada JSO yang punya daftar temuan, penugasan PIC dan tindak lanjut dilakukan per temuan`,
  `di kartu temuan, bukan di level laporan.`,
  `Tombol aksi hanya muncul bagi peran yang berwenang pada tahap berjalan. Bila pengguna`,
  `mengeluh tidak ada tombol, jelaskan tahap saat ini dan peran mana yang sedang ditunggu —`,
  `jangan menyuruh menghubungi IT.`,
  `Saat mengisi form: judul wajib; nomor WR wajib bila departemen tujuan Maintenance (MTN);`,
  `mode JSO membutuhkan lokasi JSO, Leader, Member, dan minimal satu temuan.`,
  `Tugas Observasi (observation task) adalah tindak lanjut turunan: PIC menekan "Selesaikan"`,
  `beserta catatan penyelesaian, lalu tugas berstatus completed dan menunggu diverifikasi.`,
].join("\n");

/** Pertanyaan cepat yang cocok untuk halaman yang sedang dibuka. */
function quickPromptsFor(pathname: string, moduleId: string): string[] {
  if (/\/irms\/safety-observations\/(create|\d+\/edit)$/.test(pathname)) return SO_FORM_PROMPTS;
  if (/\/irms\/safety-observations\/\d+$/.test(pathname)) return SO_DETAIL_PROMPTS;
  if (pathname.includes("/irms/safety-observations")) return SO_LIST_PROMPTS;
  if (pathname.includes("/irms/observation-tasks")) return SO_TASK_PROMPTS;
  if (moduleId === "SO") return SO_MODULE_PROMPTS;
  return DEFAULT_QUICK_PROMPTS;
}

/** Id observasi pada rute detail/edit, supaya "observasi ini" punya acuan nyata. */
function safetyObservationIdFrom(pathname: string): string | null {
  const m = pathname.match(/\/irms\/safety-observations\/(\d+)/);
  return m ? m[1] : null;
}

/* ---------------------------- Komponen --------------------------------- */

export default function EnterpriseAI({
  appName = "Enterprise Apps",
  modules,
  businessContext = "",
  apiUrl = "/api/v1/assistant/chat",
}: EnterpriseAIProps) {
  /* Normalisasi modul */
  const moduleList: EnterpriseModule[] = useMemo(() => {
    const src = modules && modules.length ? modules : DEFAULT_MODULES;
    return src.map((m) =>
      typeof m === "string" ? { id: m, name: m } : { ...m, name: m.name ?? m.id }
    );
  }, [modules]);

  const location = useLocation();

  /* State */
  const [open, setOpen] = useState(false);
  const [mounted, setMounted] = useState(false); // untuk animasi open/close
  const [closing, setClosing] = useState(false);
  const [unread, setUnread] = useState(1);
  const [activeModule, setActiveModule] = useState<string>(moduleList[0].id);
  const [userPicked, setUserPicked] = useState(false);
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Panel dokumen (basis pengetahuan RAG) di dalam widget
  const [docsOpen, setDocsOpen] = useState(false);
  const [documents, setDocuments] = useState<RagDocument[]>([]);
  const [uploading, setUploading] = useState(false);
  const [docsError, setDocsError] = useState<string | null>(null);

  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const closeTimer = useRef<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  /* Auto-detect modul aktif dari rute (kecuali user memilih manual) */
  useEffect(() => {
    if (userPicked) return;
    // Seluruh halaman berada di bawah /dashboard, sehingga startsWith() dulu selalu
    // berhenti di modul Dashboard dan tab widget tidak pernah ikut pindah. Cocokkan
    // dengan includes(), lalu menangkan pola yang muncul paling belakang di rute.
    // Bila posisinya sama, pola terpanjang yang menang — itu yang membuat
    // "/irms/safety-observations" mengalahkan "/irms" pada rute yang sama.
    const best = moduleList
      .flatMap((m) => {
        const patterns = Array.isArray(m.pathMatch)
          ? m.pathMatch
          : m.pathMatch
            ? [m.pathMatch]
            : [];
        return patterns
          .filter((pat) => location.pathname.includes(pat))
          .map((pat) => ({
            id: m.id,
            at: location.pathname.lastIndexOf(pat),
            len: pat.length,
          }));
      })
      .sort((a, b) => b.at - a.at || b.len - a.len)[0];
    if (best) setActiveModule(best.id);
  }, [location.pathname, moduleList, userPicked]);

  /* Auto-scroll ke pesan terbaru */
  useEffect(() => {
    scrollRef.current?.scrollTo({
      top: scrollRef.current.scrollHeight,
      behavior: "smooth",
    });
  }, [messages, loading, open]);

  /* Auto-focus input saat popup terbuka */
  useEffect(() => {
    if (open) {
      const t = window.setTimeout(() => inputRef.current?.focus(), 250);
      return () => window.clearTimeout(t);
    }
  }, [open]);

  /* Auto-resize textarea */
  useEffect(() => {
    const el = inputRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight, 120) + "px";
  }, [input]);

  useEffect(() => {
    return () => {
      if (closeTimer.current) window.clearTimeout(closeTimer.current);
    };
  }, []);

  /* ---- Dokumen (basis pengetahuan RAG) ---- */
  const loadDocuments = useCallback(async () => {
    try {
      const res = await ragService.listDocuments();
      setDocuments(res.data ?? []);
      setDocsError(null);
    } catch {
      setDocsError("Layanan dokumen tidak tersedia.");
    }
  }, []);

  // Muat daftar saat panel dokumen dibuka
  useEffect(() => {
    if (docsOpen) loadDocuments();
  }, [docsOpen, loadDocuments]);

  // Poll status selama masih ada dokumen yang diproses
  const hasProcessing = documents.some(
    (d) => d.status === "pending" || d.status === "processing"
  );
  useEffect(() => {
    if (!docsOpen || !hasProcessing) return;
    const t = window.setInterval(loadDocuments, 3000);
    return () => window.clearInterval(t);
  }, [docsOpen, hasProcessing, loadDocuments]);

  const onFilesPicked = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setUploading(true);
    setDocsError(null);
    try {
      for (const file of Array.from(files)) {
        await ragService.uploadDocument(file);
      }
    } catch {
      setDocsError("Gagal mengunggah dokumen. Coba lagi.");
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = "";
      setUploading(false);
      loadDocuments();
    }
  };

  const removeDocument = async (id: string) => {
    try {
      await ragService.deleteDocument(id);
      setDocuments((prev) => prev.filter((d) => d.id !== id));
    } catch {
      setDocsError("Gagal menghapus dokumen.");
    }
  };

  /* Buka / tutup dengan animasi */
  const openChat = useCallback(() => {
    if (closeTimer.current) window.clearTimeout(closeTimer.current);
    setClosing(false);
    setMounted(true);
    setOpen(true);
    setUnread(0);
  }, []);

  const closeChat = useCallback(() => {
    setClosing(true);
    setOpen(false);
    closeTimer.current = window.setTimeout(() => {
      setMounted(false);
      setClosing(false);
    }, 200);
  }, []);

  /* Pertanyaan cepat & bekal konteks sesuai halaman yang sedang dibuka */
  const quickPrompts = useMemo(
    () => quickPromptsFor(location.pathname, activeModule),
    [location.pathname, activeModule]
  );

  /* Konteks halaman untuk system prompt. Hanya disuntikkan saat modul IRMS memang
     relevan, supaya prompt tidak membengkak di halaman modul lain. */
  const pageContext = useMemo(() => {
    const path = location.pathname;
    // Panduan juga dikirim saat tab IRMS dipilih manual, bukan hanya di rute observasi:
    // pengguna sering membuka widget dari halaman lain lalu bertanya dari sana.
    const needsGuide =
      activeModule === "SO" ||
      path.includes("/irms/safety-observations") ||
      path.includes("/irms/observation-tasks");
    if (!needsGuide) return "";

    const obsId = safetyObservationIdFrom(path);
    return [
      SAFETY_OBSERVATION_GUIDE,
      obsId
        ? `HALAMAN AKTIF: pengguna sedang membuka safety observation id ${obsId}. Bila ia menyebut "observasi ini" atau "laporan ini", ambil datanya lewat tool detailSafetyObservation dengan id tersebut sebelum menjawab.`
        : "",
    ]
      .filter(Boolean)
      .join("\n");
  }, [location.pathname, activeModule]);

  /* System prompt */
  const systemPrompt = useMemo(() => {
    const moduleLines = moduleList
      .map((m) => `- ${m.name}: ${m.description ?? "modul aplikasi."}`)
      .join("\n");

    return [
      `Kamu adalah "KPIntar", asisten AI resmi untuk aplikasi "${appName}".`,
      `Kamu membantu pengguna memahami dan menggunakan seluruh modul aplikasi.`,
      // Prompt ini menjelaskan gaya & cakupan; daftar tool dan aturan agentic ditambahkan
      // server (AssistantAgentService). Kalimat berikut mencegah prompt ini terbaca sebagai
      // "kamu hanya menjelaskan", yang membuat model menolak melakukan aksi yang sebenarnya bisa.
      `Selain menjelaskan, kamu juga BISA bertindak: membaca data nyata, membuat data baru, dan`,
      `mengubah data yang sudah ada lewat tool yang tersedia (dengan konfirmasi pengguna).`,
      ``,
      `MODUL YANG DIDUKUNG:`,
      moduleLines,
      ``,
      businessContext
        ? `KONTEKS BISNIS:\n${businessContext}\n`
        : ``,
      pageContext,
      `PANDUAN GAYA RESPONS:`,
      `- Gunakan Bahasa Indonesia yang profesional namun ramah.`,
      `- Ringkas, jelas, dan langsung ke inti. Hindari bertele-tele.`,
      `- Gunakan emoji secukupnya untuk keramahan (jangan berlebihan).`,
      `- Gunakan poin/langkah bila menjelaskan prosedur.`,
      `- Jika tidak yakin atau di luar cakupan aplikasi, sampaikan dengan jujur.`,
      ``,
      `Setiap pesan pengguna diawali penanda [Modul: NamaModul] yang menunjukkan`,
      `modul yang sedang aktif. Sesuaikan jawabanmu dengan konteks modul tersebut.`,
    ]
      .filter(Boolean)
      .join("\n");
  }, [appName, moduleList, businessContext, pageContext]);

  /* Kirim pesan ke Claude */
  const sendMessage = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      if (!trimmed || loading) return;

      setError(null);

      const userMsg: ChatMessage = {
        role: "user",
        content: `[Modul: ${activeModule}] ${trimmed}`,
      };
      const history = [...messages, userMsg];

      setMessages(history);
      setInput("");
      setLoading(true);

      // Grounding RAG: cari potongan dokumen relevan (retrieval saja, tanpa LLM)
      // dan suntikkan ke system prompt request ini. Gagal/kosong = lanjut tanpa
      // konteks — widget tetap berfungsi sebagai asisten umum.
      let docContext = "";
      try {
        const search = await ragService.searchDocuments(trimmed, 4);
        const hits = search.data ?? [];
        if (hits.length > 0) {
          const blocks = hits
            .map(
              (h, i) =>
                `[Dok ${i + 1}: ${h.filename} — potongan #${h.chunk_number}]\n${h.excerpt}`
            )
            .join("\n\n");
          docContext = [
            ``,
            `KONTEKS DOKUMEN (hasil pencarian basis pengetahuan internal):`,
            blocks,
            ``,
            `Jika pertanyaan pengguna terjawab oleh KONTEKS DOKUMEN di atas, dasarkan`,
            `jawabanmu pada konteks tersebut dan sebutkan nama dokumen sumbernya di akhir`,
            `jawaban (mis. "Sumber: nama-file.pdf"). Jika tidak relevan, abaikan konteks ini.`,
          ].join("\n");
        }
      } catch {
        /* RAG tidak tersedia — lanjut tanpa grounding */
      }

      const MAX_ATTEMPTS = 3;
      let lastError: unknown = null;

      for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        try {
          const token = localStorage.getItem("access_token");
          const res = await fetch(apiUrl, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              ...(token ? { Authorization: `Bearer ${token}` } : {}),
            },
            body: JSON.stringify({
              systemPrompt: systemPrompt + docContext,
              messages: history.map((m) => ({
                role: m.role,
                content: m.content,
              })),
            }),
          });

          const data = await res.json().catch(() => null);
          if (!res.ok) {
            const err: ChatError = new Error(
              data?.errorMessage ?? `HTTP ${res.status}`
            );
            // Backend sudah menghabiskan jatah retry-nya sendiri untuk galat kuota/model
            // padat. Mengulanginya dari sini tidak menambah peluang berhasil: kuota harian
            // baru pulih besok, dan kuota per menit justru makin tergerus. Yang tersisa
            // hanyalah pengguna menunggu tiga kali lebih lama.
            err.jangan_ulangi =
              data?.errorCode === "AI_QUOTA_HARIAN" ||
              data?.errorCode === "AI_QUOTA" ||
              data?.errorCode === "AI_BUSY";
            throw err;
          }

          // Mode agentic: backend bisa mengembalikan aksi tulis yang menunggu konfirmasi
          // (pendingAction) alih-alih jawaban teks (reply).
          const pendingAction: PendingAction | undefined = data?.data?.pendingAction;

          if (pendingAction) {
            setMessages((prev) => [
              ...prev,
              {
                role: "assistant",
                // Bubble ini dirender sebagai kartu aksi, bukan teks — tapi content-nya tetap
                // diisi karena riwayat inilah yang dikirim ulang ke model pada giliran
                // berikutnya. Sewaktu kosong, model kehilangan jejak aksi yang baru saja
                // diusulkan, sehingga permintaan lanjutan seperti "ganti judulnya" tidak
                // punya acuan sama sekali.
                content: `[Aksi diusulkan — menunggu konfirmasi pengguna] ${pendingAction.tool}: ${pendingAction.summary}`,
                pendingAction,
                actionStatus: "pending",
              },
            ]);
          } else {
            const reply: string = data?.data?.reply ?? "";
            setMessages((prev) => [
              ...prev,
              {
                role: "assistant",
                content:
                  reply || "Maaf, saya tidak menerima respons. Coba lagi ya. 🙏",
              },
            ]);
          }
          if (!open) setUnread((u) => u + 1);
          lastError = null;
          break;
        } catch (e) {
          lastError = e;
          if ((e as ChatError)?.jangan_ulangi) break;
          if (attempt < MAX_ATTEMPTS) {
            await new Promise((r) => setTimeout(r, 1000 * attempt));
          }
        }
      }

      if (lastError) {
        const msg =
          lastError instanceof Error ? lastError.message : "Terjadi kesalahan.";
        // Galat kuota sudah punya penjelasan sendiri dari backend; membungkusnya dengan
        // "setelah 3x percobaan" hanya menyesatkan, karena percobaan itu tidak dilakukan.
        setError(
          (lastError as ChatError)?.jangan_ulangi
            ? msg
            : `Gagal terhubung ke asisten AI setelah ${MAX_ATTEMPTS}x percobaan: ${msg}. Silakan coba beberapa saat lagi. 🔌`
        );
      }
      setLoading(false);
    },
    [activeModule, apiUrl, loading, messages, open, systemPrompt]
  );

  /* Ubah status kartu aksi pada indeks tertentu (immutable). */
  const setActionStatus = useCallback(
    (index: number, status: ChatMessage["actionStatus"]) => {
      setMessages((prev) =>
        prev.map((m, i) => (i === index ? { ...m, actionStatus: status } : m))
      );
    },
    []
  );

  /* Setujui aksi tulis → panggil endpoint confirm, lalu tampilkan hasilnya. */
  const confirmAction = useCallback(
    async (index: number, action: PendingAction) => {
      setActionStatus(index, "confirming");
      const confirmUrl = apiUrl.replace(/\/chat$/, "/action/confirm");
      try {
        const token = localStorage.getItem("access_token");
        const res = await fetch(confirmUrl, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          body: JSON.stringify({
            tool: action.tool,
            arguments: action.arguments,
          }),
        });
        const data = await res.json().catch(() => null);
        const ok = res.ok && data?.status === "success";
        const message: string =
          data?.message ??
          data?.errorMessage ??
          (ok ? "Aksi selesai dijalankan. ✅" : "Aksi gagal dijalankan. 🙏");
        setActionStatus(index, "done");
        setMessages((prev) => [
          ...prev,
          { role: "assistant", content: (ok ? "✅ " : "⚠️ ") + message },
        ]);
      } catch {
        // Gagal jaringan — biarkan kartu tetap bisa dicoba lagi.
        setActionStatus(index, "pending");
        setError("Gagal menjalankan aksi (jaringan). Coba lagi. 🔌");
      }
    },
    [apiUrl, setActionStatus]
  );

  /* Batalkan aksi tulis — tidak ada yang dieksekusi di server. */
  const cancelAction = useCallback(
    (index: number) => {
      setActionStatus(index, "cancelled");
      setMessages((prev) => [
        ...prev,
        { role: "assistant", content: "Baik, aksi dibatalkan. 👍" },
      ]);
    },
    [setActionStatus]
  );

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendMessage(input);
    }
  };

  const clearChat = () => {
    setMessages([]);
    setError(null);
  };

  const pickModule = (id: string) => {
    setActiveModule(id);
    setUserPicked(true);
  };

  /* ----------------------------- Render -------------------------------- */

  return (
    <>
      <style>{STYLES}</style>

      {/* FAB */}
      <button
        type="button"
        aria-label="Buka EnterpriseAI"
        onClick={open ? closeChat : openChat}
        className="eai-fab"
        style={fabStyle(open)}
      >
        {!open && unread > 0 && (
          <span className="eai-badge" style={badgeStyle}>
            {unread}
          </span>
        )}
        {!open && <span className="eai-pulse" style={pulseStyle} />}
        <span style={{ position: "relative", zIndex: 2, fontSize: 26 }}>
          {open ? "✕" : <RobotIcon />}
        </span>
      </button>

      {/* Chat window */}
      {mounted && (
        <div
          className={closing ? "eai-window eai-out" : "eai-window eai-in"}
          style={windowStyle}
          role="dialog"
          aria-label="EnterpriseAI chat"
        >
          {/* Header */}
          <div style={headerStyle}>
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
              <div style={avatarStyle}>
                <RobotIcon />
              </div>
              <div>
                <div style={{ fontWeight: 700, fontSize: 15, lineHeight: 1.2 }}>
                  KPIntar
                </div>
                <div
                  style={{
                    fontSize: 12,
                    opacity: 0.9,
                    display: "flex",
                    alignItems: "center",
                    gap: 5,
                  }}
                >
                  <span style={onlineDotStyle} /> Online
                </div>
              </div>
            </div>
            <div style={{ display: "flex", gap: 6 }}>
              <button
                type="button"
                aria-label="Dokumen"
                title="Basis pengetahuan — unggah & kelola dokumen"
                onClick={() => setDocsOpen((p) => !p)}
                style={{
                  ...headerBtnStyle,
                  ...(docsOpen
                    ? { background: "rgba(255,255,255,0.35)" }
                    : null),
                }}
              >
                📄
              </button>
              <button
                type="button"
                aria-label="Hapus riwayat"
                title="Hapus riwayat chat"
                onClick={clearChat}
                style={headerBtnStyle}
              >
                🗑️
              </button>
              <button
                type="button"
                aria-label="Tutup"
                onClick={closeChat}
                style={headerBtnStyle}
              >
                ✕
              </button>
            </div>
          </div>

          {/* Module tabs */}
          <div style={tabsStyle}>
            {moduleList.map((m) => {
              const active = m.id === activeModule;
              return (
                <button
                  key={m.id}
                  type="button"
                  onClick={() => pickModule(m.id)}
                  style={tabStyle(active)}
                  title={m.description}
                >
                  {m.name}
                </button>
              );
            })}
          </div>

          {docsOpen ? (
            /* Panel Dokumen — basis pengetahuan RAG, menyatu di widget */
            <div style={messagesStyle}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 10 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#1e1b4b" }}>
                  📄 Basis Pengetahuan
                </div>
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={uploading}
                  style={uploadBtnStyle(uploading)}
                >
                  {uploading ? "Mengunggah…" : "+ Unggah dokumen"}
                </button>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".pdf,.txt,.docx,.csv,.md"
                  multiple
                  style={{ display: "none" }}
                  onChange={(e) => onFilesPicked(e.target.files)}
                />
              </div>
              <div style={{ fontSize: 12, color: "#64748b", lineHeight: 1.5, marginBottom: 12 }}>
                Dokumen yang diunggah (PDF, TXT, DOCX, CSV, MD) menjadi sumber
                jawaban KPIntar — saya kutip nama dokumennya saat menjawab.
              </div>
              {docsError && <div style={errorStyle}>{docsError}</div>}
              {documents.length === 0 && !docsError ? (
                <div style={{ textAlign: "center", color: "#94a3b8", fontSize: 13, padding: "28px 12px" }}>
                  Belum ada dokumen.
                  <br />
                  Unggah SOP, prosedur, atau manual untuk mulai.
                </div>
              ) : (
                documents.map((d) => (
                  <div key={d.id} style={docRowStyle}>
                    <div style={{ minWidth: 0, flex: 1 }}>
                      <div style={{ fontSize: 13, color: "#334155", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                        {d.filename}
                      </div>
                      <span style={docStatusStyle(d.status)}>
                        {d.status === "completed"
                          ? "✓ siap"
                          : d.status === "failed"
                          ? "✕ gagal"
                          : "⏳ diproses…"}
                      </span>
                    </div>
                    <button
                      type="button"
                      aria-label={`Hapus ${d.filename}`}
                      title="Hapus dokumen"
                      onClick={() => removeDocument(d.id)}
                      style={docDeleteBtnStyle}
                    >
                      ✕
                    </button>
                  </div>
                ))
              )}
            </div>
          ) : (
            <>
              {/* Messages */}
              <div ref={scrollRef} style={messagesStyle}>
                {messages.length === 0 ? (
                  <WelcomeScreen
                    appName={appName}
                    prompts={quickPrompts}
                    onPick={(p) => sendMessage(p)}
                  />
                ) : (
                  messages.map((m, i) =>
                    m.pendingAction ? (
                      <ActionCard
                        key={i}
                        action={m.pendingAction}
                        status={m.actionStatus ?? "pending"}
                        onConfirm={() => confirmAction(i, m.pendingAction!)}
                        onCancel={() => cancelAction(i)}
                      />
                    ) : (
                      <Bubble key={i} role={m.role} content={stripTag(m.content)} />
                    )
                  )
                )}

                {loading && <TypingIndicator />}

                {error && <div style={errorStyle}>{error}</div>}
              </div>

              {/* Input */}
              <div style={inputBarStyle}>
                <textarea
                  ref={inputRef}
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={handleKeyDown}
                  placeholder="Tulis pesan… (Enter kirim, Shift+Enter baris baru)"
                  rows={1}
                  style={textareaStyle}
                  disabled={loading}
                />
                <button
                  type="button"
                  aria-label="Kirim"
                  onClick={() => sendMessage(input)}
                  disabled={loading || !input.trim()}
                  style={sendBtnStyle(loading || !input.trim())}
                >
                  <SendIcon />
                </button>
              </div>
            </>
          )}
        </div>
      )}
    </>
  );
}

/* ------------------------- Subkomponen UI ------------------------------ */

function WelcomeScreen({
  appName,
  prompts,
  onPick,
}: {
  appName: string;
  prompts: string[];
  onPick: (prompt: string) => void;
}) {
  return (
    <div style={{ padding: "8px 2px" }}>
      <div style={welcomeAvatarStyle}>
        <RobotIcon />
      </div>
      <div
        style={{
          textAlign: "center",
          fontWeight: 700,
          fontSize: 16,
          color: "#1e1b4b",
          marginTop: 10,
        }}
      >
        Halo! Saya KPIntar 👋
      </div>
      <div
        style={{
          textAlign: "center",
          fontSize: 13,
          color: "#64748b",
          margin: "4px 18px 16px",
          lineHeight: 1.5,
        }}
      >
        Asisten AI untuk {appName}. Ada yang bisa saya bantu hari ini?
      </div>
      <div style={quickGridStyle}>
        {prompts.map((p, i) => (
          <button
            key={i}
            type="button"
            onClick={() => onPick(p)}
            style={quickBtnStyle}
            className="eai-quick"
          >
            {p}
          </button>
        ))}
      </div>
    </div>
  );
}

/* ------------------ Markdown ringan untuk jawaban AI -------------------- *
 * Model menjawab memakai markdown (**tebal**, daftar bernomor, `kode`), dan
 * bubble dulu merendernya sebagai teks mentah sehingga bintang-bintangnya ikut
 * terbaca pengguna. Subset di bawah sengaja kecil dan tanpa dependency maupun
 * innerHTML: cukup untuk yang benar-benar dipakai model, tanpa membuka celah
 * injeksi HTML dari teks yang datang dari luar. */

type MdBlock =
  | { kind: "heading"; text: string }
  | { kind: "paragraph"; lines: string[] }
  | { kind: "list"; ordered: boolean; items: { text: string; depth: number }[] };

const MD_HEADING = /^\s{0,3}#{1,6}\s+(.*)$/;
// Butir diawali penanda lalu spasi, sehingga "**Tebal**" di awal baris tidak
// salah terbaca sebagai butir.
const MD_BULLET = /^(\s*)[*\-•]\s+(.*)$/;
const MD_ORDERED = /^(\s*)\d+[.)]\s+(.*)$/;
const MD_INLINE = /(\*\*[^*]+\*\*|__[^_]+__|`[^`]+`|\*[^*\n]+\*|_[^_\n]+_)/;

function parseMarkdownBlocks(text: string): MdBlock[] {
  const blocks: MdBlock[] = [];
  let paragraph: string[] = [];

  const flushParagraph = () => {
    if (paragraph.length) {
      blocks.push({ kind: "paragraph", lines: paragraph });
      paragraph = [];
    }
  };

  for (const raw of text.split("\n")) {
    const line = raw.replace(/\s+$/, "");
    if (!line.trim()) {
      flushParagraph();
      continue;
    }

    const heading = MD_HEADING.exec(line);
    if (heading) {
      flushParagraph();
      blocks.push({ kind: "heading", text: heading[1] });
      continue;
    }

    const bullet = MD_BULLET.exec(line);
    const ordered = bullet ? null : MD_ORDERED.exec(line);
    const item = bullet ?? ordered;
    if (item) {
      flushParagraph();
      const entry = {
        text: item[2],
        depth: Math.min(2, Math.floor(item[1].length / 2)),
      };
      const last = blocks[blocks.length - 1];
      if (last && last.kind === "list" && last.ordered === Boolean(ordered)) {
        last.items.push(entry);
      } else {
        blocks.push({ kind: "list", ordered: Boolean(ordered), items: [entry] });
      }
      continue;
    }

    paragraph.push(line);
  }

  flushParagraph();
  return blocks;
}

function renderInline(text: string, keyPrefix: string) {
  // split() dengan grup tangkap ikut mengembalikan pemisahnya, jadi penanda
  // markdown dan teks biasa datang berselang-seling dalam satu larik.
  return text
    .split(MD_INLINE)
    .filter((part) => part)
    .map((part, i) => {
      const key = `${keyPrefix}-${i}`;
      if (part.length > 4 && part.startsWith("**") && part.endsWith("**")) {
        return (
          <strong key={key} style={mdStrongStyle}>
            {part.slice(2, -2)}
          </strong>
        );
      }
      if (part.length > 4 && part.startsWith("__") && part.endsWith("__")) {
        return (
          <strong key={key} style={mdStrongStyle}>
            {part.slice(2, -2)}
          </strong>
        );
      }
      if (part.length > 2 && part.startsWith("`") && part.endsWith("`")) {
        return (
          <code key={key} style={mdCodeStyle}>
            {part.slice(1, -1)}
          </code>
        );
      }
      const italic =
        part.length > 2 &&
        ((part.startsWith("*") && part.endsWith("*")) ||
          (part.startsWith("_") && part.endsWith("_")));
      if (italic) {
        return <em key={key}>{part.slice(1, -1)}</em>;
      }
      return <span key={key}>{part}</span>;
    });
}

/** Merender subset markdown jawaban asisten menjadi elemen React. */
function MarkdownText({ text }: { text: string }) {
  const blocks = useMemo(() => parseMarkdownBlocks(text), [text]);

  return (
    <>
      {blocks.map((block, i) => {
        const last = i === blocks.length - 1;
        const tail: CSSProperties = last ? { marginBottom: 0 } : {};

        if (block.kind === "heading") {
          return (
            <div key={i} style={{ ...mdHeadingStyle, ...tail }}>
              {renderInline(block.text, `h${i}`)}
            </div>
          );
        }

        if (block.kind === "list") {
          const Tag = block.ordered ? "ol" : "ul";
          return (
            <Tag key={i} style={{ ...mdListStyle, ...tail }}>
              {block.items.map((item, j) => (
                <li
                  key={j}
                  style={{ ...mdListItemStyle, marginLeft: item.depth * 12 }}
                >
                  {renderInline(item.text, `l${i}-${j}`)}
                </li>
              ))}
            </Tag>
          );
        }

        return (
          <div key={i} style={{ ...mdParagraphStyle, ...tail }}>
            {block.lines.map((line, j) => (
              <span key={j}>
                {j > 0 && <br />}
                {renderInline(line, `p${i}-${j}`)}
              </span>
            ))}
          </div>
        );
      })}
    </>
  );
}

function Bubble({ role, content }: { role: "user" | "assistant"; content: string }) {
  const isUser = role === "user";
  return (
    <div
      style={{
        display: "flex",
        justifyContent: isUser ? "flex-end" : "flex-start",
        marginBottom: 10,
      }}
    >
      {!isUser && (
        <div style={bubbleAvatarStyle}>
          <RobotIcon small />
        </div>
      )}
      <div style={bubbleStyle(isUser)}>
        {isUser ? content : <MarkdownText text={content} />}
      </div>
    </div>
  );
}

/** Kartu konfirmasi untuk aksi tulis yang diusulkan KPIntar (mode agentic). */
function ActionCard({
  action,
  status,
  onConfirm,
  onCancel,
}: {
  action: PendingAction;
  status: NonNullable<ChatMessage["actionStatus"]>;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const busy = status === "confirming";
  const resolved = status === "done" || status === "cancelled";
  return (
    <div style={{ display: "flex", justifyContent: "flex-start", marginBottom: 10 }}>
      <div style={bubbleAvatarStyle}>
        <RobotIcon small />
      </div>
      <div style={actionCardStyle}>
        <div style={actionHeaderStyle}>⚡ Konfirmasi aksi</div>
        <div style={actionSummaryStyle}>{action.summary}</div>
        {status === "done" ? (
          <div style={actionResolvedStyle("done")}>✓ Disetujui</div>
        ) : status === "cancelled" ? (
          <div style={actionResolvedStyle("cancelled")}>✕ Dibatalkan</div>
        ) : (
          <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
            <button
              type="button"
              onClick={onConfirm}
              disabled={busy || resolved}
              style={actionConfirmBtnStyle(busy)}
            >
              {busy ? "Menjalankan…" : "Setujui"}
            </button>
            <button
              type="button"
              onClick={onCancel}
              disabled={busy || resolved}
              style={actionCancelBtnStyle}
            >
              Batal
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function TypingIndicator() {
  return (
    <div style={{ display: "flex", justifyContent: "flex-start", marginBottom: 10 }}>
      <div style={bubbleAvatarStyle}>
        <RobotIcon small />
      </div>
      <div style={{ ...bubbleStyle(false), padding: "12px 14px" }}>
        <div style={{ display: "flex", gap: 4, alignItems: "center" }}>
          <span className="eai-dot" style={dotStyle(0)} />
          <span className="eai-dot" style={dotStyle(0.15)} />
          <span className="eai-dot" style={dotStyle(0.3)} />
        </div>
      </div>
    </div>
  );
}

function RobotIcon({ small }: { small?: boolean }) {
  const s = small ? 16 : 22;
  return (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="4" y="8" width="16" height="11" rx="3" fill="currentColor" />
      <circle cx="9" cy="13.5" r="1.6" fill="#fff" />
      <circle cx="15" cy="13.5" r="1.6" fill="#fff" />
      <rect x="10.5" y="2.5" width="3" height="3.5" rx="1.5" fill="currentColor" />
      <rect x="1.5" y="11" width="2" height="5" rx="1" fill="currentColor" />
      <rect x="20.5" y="11" width="2" height="5" rx="1" fill="currentColor" />
    </svg>
  );
}

function SendIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M3.4 20.4 21 12 3.4 3.6 3.4 10l12 2-12 2 0 6.4Z"
        fill="currentColor"
      />
    </svg>
  );
}

/* --------------------------- Helpers ----------------------------------- */

/** Buang penanda [Modul: X] dari teks user agar tampil bersih. */
function stripTag(text: string): string {
  return text.replace(/^\[Modul:[^\]]*\]\s*/, "");
}

/* ----------------------------- Styles ---------------------------------- */

function fabStyle(open: boolean): CSSProperties {
  return {
    position: "fixed",
    right: 24,
    bottom: 24,
    width: 60,
    height: 60,
    borderRadius: "50%",
    border: "none",
    cursor: "pointer",
    color: "#fff",
    background: `linear-gradient(135deg, ${ACCENT} 0%, ${ACCENT_2} 100%)`,
    boxShadow: "0 8px 24px rgba(79,70,229,0.45)",
    zIndex: 2147483000,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    transition: "transform 0.2s ease",
    transform: open ? "rotate(90deg)" : "rotate(0deg)",
    fontFamily: FONT,
  };
}

const badgeStyle: CSSProperties = {
  position: "absolute",
  top: -2,
  right: -2,
  minWidth: 20,
  height: 20,
  padding: "0 5px",
  borderRadius: 10,
  background: "#ef4444",
  color: "#fff",
  fontSize: 11,
  fontWeight: 700,
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  zIndex: 3,
  boxShadow: "0 0 0 2px #fff",
};

const pulseStyle: CSSProperties = {
  position: "absolute",
  inset: 0,
  borderRadius: "50%",
  background: ACCENT,
  zIndex: 1,
};

const windowStyle: CSSProperties = {
  position: "fixed",
  right: 24,
  bottom: 96,
  width: 380,
  height: 560,
  maxWidth: "calc(100vw - 32px)",
  maxHeight: "calc(100vh - 120px)",
  background: "#fff",
  borderRadius: 18,
  boxShadow: "0 20px 60px rgba(15,23,42,0.28)",
  display: "flex",
  flexDirection: "column",
  overflow: "hidden",
  zIndex: 2147483000,
  fontFamily: FONT,
  transformOrigin: "bottom right",
};

const headerStyle: CSSProperties = {
  background: `linear-gradient(135deg, ${ACCENT} 0%, ${ACCENT_2} 100%)`,
  color: "#fff",
  padding: "14px 16px",
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
  flexShrink: 0,
};

const avatarStyle: CSSProperties = {
  width: 38,
  height: 38,
  borderRadius: "50%",
  background: "rgba(255,255,255,0.2)",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  color: "#fff",
};

const onlineDotStyle: CSSProperties = {
  width: 8,
  height: 8,
  borderRadius: "50%",
  background: "#4ade80",
  display: "inline-block",
  boxShadow: "0 0 0 2px rgba(74,222,128,0.35)",
};

const headerBtnStyle: CSSProperties = {
  width: 30,
  height: 30,
  borderRadius: 8,
  border: "none",
  background: "rgba(255,255,255,0.16)",
  color: "#fff",
  cursor: "pointer",
  fontSize: 13,
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
};

const tabsStyle: CSSProperties = {
  display: "flex",
  gap: 6,
  padding: "10px 12px",
  background: "#f8fafc",
  borderBottom: "1px solid #eef2f7",
  overflowX: "auto",
  flexShrink: 0,
};

function tabStyle(active: boolean): CSSProperties {
  return {
    border: active ? `1px solid ${ACCENT}` : "1px solid #e2e8f0",
    background: active ? ACCENT : "#fff",
    color: active ? "#fff" : "#475569",
    borderRadius: 20,
    padding: "5px 12px",
    fontSize: 12,
    fontWeight: 600,
    cursor: "pointer",
    whiteSpace: "nowrap",
    flexShrink: 0,
    transition: "all 0.15s ease",
    fontFamily: FONT,
  };
}

const messagesStyle: CSSProperties = {
  flex: 1,
  overflowY: "auto",
  padding: "14px 14px 6px",
  background: "#fbfbfe",
};

const welcomeAvatarStyle: CSSProperties = {
  width: 56,
  height: 56,
  borderRadius: "50%",
  margin: "8px auto 0",
  background: `linear-gradient(135deg, ${ACCENT} 0%, ${ACCENT_2} 100%)`,
  color: "#fff",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
};

const quickGridStyle: CSSProperties = {
  display: "grid",
  gridTemplateColumns: "1fr 1fr",
  gap: 8,
};

const quickBtnStyle: CSSProperties = {
  border: "1px solid #e7e9f5",
  background: "#fff",
  borderRadius: 12,
  padding: "10px 10px",
  fontSize: 12,
  color: "#334155",
  textAlign: "left",
  cursor: "pointer",
  lineHeight: 1.35,
  fontFamily: FONT,
  transition: "all 0.15s ease",
};

function uploadBtnStyle(disabled: boolean): CSSProperties {
  return {
    border: "none",
    background: disabled ? "#c7d2fe" : `linear-gradient(135deg, ${ACCENT} 0%, ${ACCENT_2} 100%)`,
    color: "#fff",
    borderRadius: 10,
    padding: "7px 12px",
    fontSize: 12,
    fontWeight: 600,
    cursor: disabled ? "default" : "pointer",
    fontFamily: FONT,
  };
}

const docRowStyle: CSSProperties = {
  display: "flex",
  alignItems: "center",
  gap: 8,
  border: "1px solid #e7e9f5",
  background: "#fff",
  borderRadius: 12,
  padding: "9px 10px",
  marginBottom: 8,
};

function docStatusStyle(status: string): CSSProperties {
  const palette: Record<string, { bg: string; fg: string }> = {
    completed: { bg: "#dcfce7", fg: "#15803d" },
    failed: { bg: "#fee2e2", fg: "#b91c1c" },
  };
  const c = palette[status] ?? { bg: "#fef9c3", fg: "#854d0e" };
  return {
    display: "inline-block",
    marginTop: 3,
    fontSize: 10,
    fontWeight: 700,
    padding: "2px 8px",
    borderRadius: 8,
    background: c.bg,
    color: c.fg,
  };
}

const docDeleteBtnStyle: CSSProperties = {
  border: "none",
  background: "transparent",
  color: "#94a3b8",
  cursor: "pointer",
  fontSize: 13,
  width: 26,
  height: 26,
  borderRadius: 8,
  flexShrink: 0,
};

const bubbleAvatarStyle: CSSProperties = {
  width: 28,
  height: 28,
  borderRadius: "50%",
  background: `linear-gradient(135deg, ${ACCENT} 0%, ${ACCENT_2} 100%)`,
  color: "#fff",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  flexShrink: 0,
  marginRight: 8,
  alignSelf: "flex-end",
};

function bubbleStyle(isUser: boolean): CSSProperties {
  return {
    maxWidth: "76%",
    padding: "10px 13px",
    borderRadius: 14,
    fontSize: 13.5,
    lineHeight: 1.5,
    whiteSpace: isUser ? "pre-wrap" : "normal",
    wordBreak: "break-word",
    color: isUser ? "#fff" : "#1e293b",
    background: isUser
      ? `linear-gradient(135deg, ${ACCENT} 0%, ${ACCENT_2} 100%)`
      : "#fff",
    border: isUser ? "none" : "1px solid #eef0f7",
    borderBottomRightRadius: isUser ? 4 : 14,
    borderBottomLeftRadius: isUser ? 14 : 4,
    boxShadow: isUser ? "none" : "0 1px 2px rgba(15,23,42,0.05)",
  };
}

const mdStrongStyle: CSSProperties = {
  fontWeight: 700,
  color: "#312e81",
};

const mdCodeStyle: CSSProperties = {
  fontFamily: "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",
  fontSize: 12,
  background: "#eef2ff",
  color: "#3730a3",
  padding: "1px 5px",
  borderRadius: 5,
};

const mdHeadingStyle: CSSProperties = {
  fontWeight: 700,
  fontSize: 13.5,
  color: "#1e1b4b",
  margin: "8px 0 4px",
};

const mdParagraphStyle: CSSProperties = {
  margin: "0 0 6px",
};

const mdListStyle: CSSProperties = {
  margin: "0 0 6px",
  paddingLeft: 18,
};

const mdListItemStyle: CSSProperties = {
  marginBottom: 3,
};

const actionCardStyle: CSSProperties = {
  maxWidth: "82%",
  padding: "12px 14px",
  borderRadius: 14,
  borderBottomLeftRadius: 4,
  background: "#fff",
  border: "1px solid #ddd6fe",
  boxShadow: "0 1px 2px rgba(15,23,42,0.05)",
};

const actionHeaderStyle: CSSProperties = {
  fontSize: 12,
  fontWeight: 700,
  color: ACCENT,
  marginBottom: 6,
};

const actionSummaryStyle: CSSProperties = {
  fontSize: 13,
  lineHeight: 1.5,
  color: "#1e293b",
  whiteSpace: "pre-wrap",
  wordBreak: "break-word",
};

function actionConfirmBtnStyle(busy: boolean): CSSProperties {
  return {
    border: "none",
    borderRadius: 10,
    padding: "7px 16px",
    fontSize: 12.5,
    fontWeight: 600,
    color: "#fff",
    cursor: busy ? "default" : "pointer",
    background: busy
      ? "#c7d2fe"
      : `linear-gradient(135deg, ${ACCENT} 0%, ${ACCENT_2} 100%)`,
    fontFamily: FONT,
  };
}

const actionCancelBtnStyle: CSSProperties = {
  border: "1px solid #e2e8f0",
  borderRadius: 10,
  padding: "7px 16px",
  fontSize: 12.5,
  fontWeight: 600,
  color: "#475569",
  background: "#fff",
  cursor: "pointer",
  fontFamily: FONT,
};

function actionResolvedStyle(kind: "done" | "cancelled"): CSSProperties {
  return {
    marginTop: 10,
    display: "inline-block",
    fontSize: 11,
    fontWeight: 700,
    padding: "3px 10px",
    borderRadius: 8,
    background: kind === "done" ? "#dcfce7" : "#f1f5f9",
    color: kind === "done" ? "#15803d" : "#64748b",
  };
}

function dotStyle(delay: number): CSSProperties {
  return {
    width: 7,
    height: 7,
    borderRadius: "50%",
    background: "#94a3b8",
    display: "inline-block",
    animationDelay: `${delay}s`,
  };
}

const errorStyle: CSSProperties = {
  background: "#fef2f2",
  border: "1px solid #fecaca",
  color: "#b91c1c",
  borderRadius: 10,
  padding: "9px 12px",
  fontSize: 12.5,
  lineHeight: 1.45,
  marginBottom: 10,
};

const inputBarStyle: CSSProperties = {
  display: "flex",
  alignItems: "flex-end",
  gap: 8,
  padding: "10px 12px",
  borderTop: "1px solid #eef2f7",
  background: "#fff",
  flexShrink: 0,
};

const textareaStyle: CSSProperties = {
  flex: 1,
  resize: "none",
  border: "1px solid #e2e8f0",
  borderRadius: 12,
  padding: "10px 12px",
  fontSize: 13.5,
  fontFamily: FONT,
  lineHeight: 1.4,
  maxHeight: 120,
  outline: "none",
  color: "#1e293b",
};

function sendBtnStyle(disabled: boolean): CSSProperties {
  return {
    width: 40,
    height: 40,
    borderRadius: 12,
    border: "none",
    cursor: disabled ? "not-allowed" : "pointer",
    color: "#fff",
    background: disabled
      ? "#c7d2fe"
      : `linear-gradient(135deg, ${ACCENT} 0%, ${ACCENT_2} 100%)`,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
    transition: "background 0.15s ease",
  };
}

/* ------------------------ CSS (animasi) -------------------------------- */

const STYLES = `
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');

@keyframes eai-pulse {
  0%   { transform: scale(1);   opacity: 0.55; }
  70%  { transform: scale(1.8); opacity: 0; }
  100% { transform: scale(1.8); opacity: 0; }
}
.eai-pulse { animation: eai-pulse 2s ease-out infinite; }

.eai-fab:hover { transform: scale(1.06); }

@keyframes eai-scale-in {
  from { opacity: 0; transform: scale(0.85) translateY(10px); }
  to   { opacity: 1; transform: scale(1) translateY(0); }
}
@keyframes eai-scale-out {
  from { opacity: 1; transform: scale(1) translateY(0); }
  to   { opacity: 0; transform: scale(0.85) translateY(10px); }
}
.eai-in  { animation: eai-scale-in 0.2s ease forwards; }
.eai-out { animation: eai-scale-out 0.2s ease forwards; }

@keyframes eai-bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.5; }
  40%           { transform: translateY(-5px); opacity: 1; }
}
.eai-dot { animation: eai-bounce 1.2s infinite ease-in-out; }

.eai-quick:hover {
  border-color: ${ACCENT};
  background: #f5f4ff;
  transform: translateY(-1px);
}

.eai-window ::-webkit-scrollbar { width: 6px; }
.eai-window ::-webkit-scrollbar-thumb { background: #d6dae6; border-radius: 3px; }
`;
