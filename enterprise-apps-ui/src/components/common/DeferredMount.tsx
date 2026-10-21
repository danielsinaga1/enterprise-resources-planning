import { useEffect, useRef, useState, type ReactNode } from 'react';

/* ============================================================
   DEFERRED MOUNT
   Menunda mount anak sampai elemennya mendekati viewport.

   Dipakai untuk daftar kartu form yang berat (mis. Temuan JSO):
   PrimeReact TabView memakai renderActiveOnly, jadi seluruh isi
   tab baru dibuat saat tab diklik — dengan belasan kartu, semua
   Dropdown/AutoComplete/textarea ikut mount dalam SATU render
   sinkron dan tab terasa menggantung beberapa detik.

   AMAN untuk form: nilai field disimpan di state induk, bukan di
   DOM. Kartu yang belum dimount tetap punya datanya, tetap ikut
   tervalidasi, dan tetap terkirim saat submit.
   ============================================================ */

interface Props {
  children: ReactNode;
  /**
   * Tinggi placeholder sebelum konten dimount. Dibuat mendekati tinggi
   * konten asli supaya tinggi scrollbar tidak melompat saat kartu mount.
   */
  placeholderHeight?: number;
  /** Mount langsung tanpa menunggu scroll — untuk kartu pertama yang pasti terlihat. */
  eager?: boolean;
  /** Jarak pra-mount sebelum elemen masuk layar. */
  rootMargin?: string;
}

const DeferredMount = ({
  children,
  placeholderHeight = 900,
  eager = false,
  rootMargin = '800px',
}: Props) => {
  const [mounted, setMounted] = useState(eager);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (mounted) return;
    const el = ref.current;
    if (!el) return;

    // Lingkungan tanpa IntersectionObserver (browser lama, jsdom saat test):
    // langsung mount. Lebih baik lambat daripada konten tidak pernah muncul.
    if (typeof IntersectionObserver === 'undefined') {
      setMounted(true);
      return;
    }

    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) setMounted(true);
      },
      { rootMargin },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [mounted, rootMargin]);

  // Sekali dimount, tidak pernah di-unmount lagi walau discroll keluar layar.
  // State internal PrimeReact (teks filter dropdown, panel autocomplete yang
  // sedang terbuka) hanya hidup di komponennya — unmount akan membuangnya dan
  // input terasa "ter-reset" saat user scroll bolak-balik.
  if (mounted) return <>{children}</>;

  return <div ref={ref} style={{ minHeight: placeholderHeight }} aria-hidden="true" />;
};

export default DeferredMount;
