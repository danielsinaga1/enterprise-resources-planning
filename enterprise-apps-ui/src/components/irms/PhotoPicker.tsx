import { useEffect, useMemo } from 'react';
import { Button } from 'primereact/button';
import { fileUploadService } from '../../services';

/* ============================================================
   PHOTO PICKER
   Pemilih foto yang memisahkan dua sumber: nama file yang sudah
   terunggah (`existing`) dan File yang masih menunggu diunggah
   (`files`). Pemanggil yang mengunggah dan menyimpannya — komponen
   ini hanya mengurus pratinjau dan penghapusan.

   Dipakai di level observasi (NCR) maupun per temuan (JSO), jadi
   `inputId` wajib unik: <input type="file"> disembunyikan dan
   dipicu lewat getElementById.
   ============================================================ */
interface PhotoPickerProps {
  /** Wajib unik dalam satu halaman — dipakai untuk memicu input file tersembunyi. */
  inputId: string;
  /** Nama file yang sudah terunggah. */
  existing: string[];
  onExistingChange: (next: string[]) => void;
  /** File baru yang belum diunggah. */
  files: File[];
  onFilesChange: (next: File[]) => void;
  disabled?: boolean;
  /** Sisi thumbnail dalam px. */
  size?: number;
  label?: string;
  hint?: string;
  /** Sembunyikan tombol tambah — untuk daftar yang hanya boleh dilihat/dihapus. */
  hideAdd?: boolean;
}

const PhotoPicker = ({
  inputId,
  existing,
  onExistingChange,
  files,
  onFilesChange,
  disabled = false,
  size = 120,
  label = 'Pilih Foto',
  hint = 'Format: JPEG, PNG, GIF, WEBP (maks. 10MB per file)',
  hideAdd = false,
}: PhotoPickerProps) => {
  // Blob URL stabil per file; dicabut saat unmount / daftar file berubah.
  const previewUrls = useMemo(() => files.map((f) => URL.createObjectURL(f)), [files]);
  useEffect(() => {
    return () => { previewUrls.forEach((u) => URL.revokeObjectURL(u)); };
  }, [previewUrls]);

  const removeButton = (onClick: () => void) => (
    <button
      type="button"
      className="absolute flex align-items-center justify-content-center"
      style={{ top: -8, right: -8, width: 24, height: 24, borderRadius: '50%', background: '#ef4444', color: '#fff', border: 'none', cursor: 'pointer', fontSize: 12 }}
      onClick={onClick}
    >
      <i className="pi pi-times" style={{ fontSize: 10 }} />
    </button>
  );

  return (
    <div className="flex flex-column gap-2">
      {(existing.length > 0 || files.length > 0) && (
        <div className="flex gap-3 flex-wrap">
          {existing.map((photo, idx) => (
            <div key={`e-${photo}-${idx}`} className="relative" style={{ width: size, height: size }}>
              <img
                src={fileUploadService.getPhotoUrl(photo)}
                alt={`Foto ${idx + 1}`}
                style={{ width: size, height: size, objectFit: 'cover', borderRadius: 10, border: '2px solid #e5e7eb' }}
              />
              {removeButton(() => onExistingChange(existing.filter((_, i) => i !== idx)))}
            </div>
          ))}
          {files.map((file, idx) => (
            <div key={`n-${idx}`} className="relative" style={{ width: size, height: size }}>
              <img
                src={previewUrls[idx]}
                alt={file.name}
                style={{ width: size, height: size, objectFit: 'cover', borderRadius: 10, border: '2px dashed #22c55e' }}
              />
              {removeButton(() => onFilesChange(files.filter((_, i) => i !== idx)))}
              <div
                className="absolute px-2 py-1 text-xs font-semibold"
                style={{ bottom: 4, left: 4, background: 'rgba(0,0,0,0.6)', color: '#fff', borderRadius: 6, maxWidth: size - 10, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
              >
                {file.name}
              </div>
            </div>
          ))}
        </div>
      )}

      {!hideAdd && (
        <>
          <input
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            multiple
            onChange={(e) => {
              const picked = e.target.files;
              if (picked) onFilesChange([...files, ...Array.from(picked)]);
              // Reset agar memilih file yang sama dua kali tetap memicu onChange.
              e.target.value = '';
            }}
            style={{ display: 'none' }}
            id={inputId}
          />
          <div className="flex align-items-center gap-2 flex-wrap">
            <Button
              type="button"
              label={disabled ? 'Mengunggah...' : label}
              icon="pi pi-camera"
              outlined
              severity="success"
              size="small"
              disabled={disabled}
              onClick={() => document.getElementById(inputId)?.click()}
            />
            {hint && <span className="text-xs text-500">{hint}</span>}
          </div>
        </>
      )}
    </div>
  );
};

export default PhotoPicker;
