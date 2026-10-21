import { useEffect, useState } from 'react';
import { Dialog } from 'primereact/dialog';
import { Message } from 'primereact/message';
import { ProgressSpinner } from 'primereact/progressspinner';
import { fetchSourceFile } from '../../services/smartDocsOcrService';

/* ============================================================
   PRATINJAU BERKAS SUMBER
   ============================================================
   Berkas arsip tidak disajikan sebagai URL publik — isinya invoice
   dengan nama vendor dan nominal. Jadi berkasnya diambil dengan
   token, lalu ditampilkan dari object URL di memori browser.
   ============================================================ */

interface Props {
  /** Nama berkas di arsip (kolom sourceFilePath), bukan nama asli unggahan. */
  filename?: string;
  /** Nama asli untuk ditampilkan ke pengguna. */
  displayName?: string;
  mimeType?: string;
  visible: boolean;
  onHide: () => void;
}

const SmartDocumentSourceDialog = ({ filename, displayName, mimeType, visible, onHide }: Props) => {
  const [url, setUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!visible || !filename) return;

    let revoked = false;
    let objectUrl: string | null = null;

    setLoading(true);
    setError(null);
    fetchSourceFile(filename)
      .then((blob) => {
        if (revoked) return;
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch((e: Error) => {
        if (!revoked) setError(e.message);
      })
      .finally(() => {
        if (!revoked) setLoading(false);
      });

    // Object URL menahan blob di memori sampai dicabut; tanpa ini setiap
    // pembukaan dialog menambah satu salinan berkas yang tidak pernah dilepas.
    return () => {
      revoked = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      setUrl(null);
    };
  }, [visible, filename]);

  const isPdf = (mimeType ?? '').includes('pdf') || (filename ?? '').toLowerCase().endsWith('.pdf');

  return (
    <Dialog
      header={displayName?.trim() || filename || 'Berkas Sumber'}
      visible={visible}
      onHide={onHide}
      style={{ width: 'min(900px, 95vw)' }}
      breakpoints={{ '960px': '95vw' }}
      dismissableMask
    >
      {loading && (
        <div className="flex justify-content-center p-5">
          <ProgressSpinner style={{ width: 40, height: 40 }} />
        </div>
      )}

      {error && <Message severity="error" className="w-full justify-content-start" text={error} />}

      {!loading && !error && url && (
        isPdf ? (
          <iframe
            src={url}
            title={displayName || 'Berkas sumber'}
            style={{ width: '100%', height: '70vh', border: 'none' }}
          />
        ) : (
          <img
            src={url}
            alt={displayName || 'Berkas sumber'}
            style={{ width: '100%', height: 'auto', objectFit: 'contain' }}
          />
        )
      )}

      {!loading && !error && !filename && (
        <Message
          severity="info"
          className="w-full justify-content-start"
          text="Dokumen ini tidak punya berkas sumber yang terarsip."
        />
      )}
    </Dialog>
  );
};

export default SmartDocumentSourceDialog;
