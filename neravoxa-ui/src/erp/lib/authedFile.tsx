/**
 * Berkas ERP yang dilindungi (lampiran transaksi) hanya bisa diambil dengan header Bearer,
 * jadi tidak bisa dipakai langsung di <a href> / <img src> seperti di ENTARIS (yang memakai
 * cookie). Helper ini mengambil berkas lewat klien API lalu memakai object URL.
 */
import { useEffect, useState, type ImgHTMLAttributes } from 'react';
import api from './api';

async function fetchBlob(path: string): Promise<Blob> {
  const res = await api.get(path, { responseType: 'blob' });
  return res.data as Blob;
}

/** Buka berkas di tab baru, atau unduh bila `downloadName` diisi. `path` relatif ke /api/erp. */
export async function openAuthed(path: string, downloadName?: string): Promise<void> {
  const win = downloadName ? null : window.open('', '_blank');
  try {
    const url = URL.createObjectURL(await fetchBlob(path));
    if (downloadName) {
      const a = document.createElement('a');
      a.href = url;
      a.download = downloadName;
      document.body.appendChild(a);
      a.click();
      a.remove();
    } else if (win) {
      win.location.href = url;
    } else {
      window.open(url, '_blank', 'noopener');
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (err) {
    win?.close();
    throw err;
  }
}

/** Object URL untuk berkas terlindungi; null selama memuat/gagal. */
export function useAuthedUrl(path: string | null | undefined): string | null {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!path) {
      setUrl(null);
      return;
    }
    let revoked = false;
    let objectUrl: string | null = null;
    fetchBlob(path)
      .then((blob) => {
        if (revoked) return;
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch(() => setUrl(null));
    return () => {
      revoked = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [path]);
  return url;
}

/** <img> untuk berkas terlindungi. */
export function AuthedImg({ path, ...props }: { path: string } & Omit<ImgHTMLAttributes<HTMLImageElement>, 'src'>) {
  const url = useAuthedUrl(path);
  if (!url) return <span className={props.className} aria-busy="true" />;
  return <img {...props} src={url} />;
}
