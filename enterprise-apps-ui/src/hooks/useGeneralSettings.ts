import { useEffect, useState } from 'react';
import api from '../services/api';

export const GENERAL_SETTINGS_KEY = 'ea_general_settings';
export const GENERAL_SETTINGS_EVENT = 'ea-general-settings-changed';

// Server adalah sumber kebenaran (dipakai juga oleh template email di backend);
// localStorage hanya cache agar branding tidak berkedip saat halaman dimuat.
const SETTINGS_ENDPOINT = '/api/v1/settings/general';

export interface GeneralSettings {
  siteName: string;
  siteDesc: string;
  email: string;
  phone: string;
  address: string;
}

// Kontak sengaja dikosongkan: diisi admin lewat Pengaturan Umum, bukan ditebak di sini.
export const defaultGeneralSettings: GeneralSettings = {
  siteName: 'KPI ERP',
  siteDesc: 'Enterprise Resources Planning',
  email: '',
  phone: '',
  address: '',
};

// Nilai bawaan template rumah sakit yang sempat tersimpan di browser pengguna.
// Diperlakukan sebagai "belum diisi" supaya default baru yang dipakai — tanpa ini
// pengguna lama harus Reset + Simpan manual agar branding-nya ikut berubah.
const LEGACY_PLACEHOLDERS: Record<keyof GeneralSettings, string> = {
  siteName: 'IRMS Hospital',
  siteDesc: 'Sistem Informasi Manajemen Rumah Sakit',
  email: '',
  phone: '(021) 123456',
  address: 'Jl. Rumah Sakit No. 1',
};

const dropLegacyValues = (saved: Partial<GeneralSettings>): Partial<GeneralSettings> => {
  const cleaned: Partial<GeneralSettings> = {};
  (Object.keys(saved) as (keyof GeneralSettings)[]).forEach((key) => {
    const value = saved[key];
    if (value !== undefined && value !== LEGACY_PLACEHOLDERS[key]) {
      cleaned[key] = value;
    }
  });
  return cleaned;
};

export const readGeneralSettings = (): GeneralSettings => {
  try {
    const saved = localStorage.getItem(GENERAL_SETTINGS_KEY);
    return saved
      ? { ...defaultGeneralSettings, ...dropLegacyValues(JSON.parse(saved)) }
      : defaultGeneralSettings;
  } catch {
    return defaultGeneralSettings;
  }
};

const cacheLocally = (settings: GeneralSettings) => {
  localStorage.setItem(GENERAL_SETTINGS_KEY, JSON.stringify(settings));
  window.dispatchEvent(new Event(GENERAL_SETTINGS_EVENT));
};

export const saveGeneralSettings = async (settings: GeneralSettings): Promise<GeneralSettings> => {
  const { data } = await api.put<GeneralSettings>(SETTINGS_ENDPOINT, settings);
  const saved = { ...defaultGeneralSettings, ...data };
  cacheLocally(saved);
  return saved;
};

// Satu permintaan dipakai bersama semua komponen yang memakai hook ini, supaya
// beberapa pemasangan sekaligus (Login, sidebar) tidak memanggil API berkali-kali.
let hydrated = false;
let inFlight: Promise<GeneralSettings> | null = null;

const hydrateFromServer = (): Promise<GeneralSettings> => {
  if (!inFlight) {
    inFlight = api
      .get<GeneralSettings>(SETTINGS_ENDPOINT)
      .then(({ data }) => {
        const fresh = { ...defaultGeneralSettings, ...data };
        hydrated = true;
        cacheLocally(fresh);
        return fresh;
      })
      // Server tak terjangkau: pakai cache terakhir, jangan kosongkan branding.
      .catch(() => readGeneralSettings())
      .finally(() => {
        inFlight = null;
      });
  }
  return inFlight;
};

export const useGeneralSettings = (): GeneralSettings => {
  const [settings, setSettings] = useState<GeneralSettings>(readGeneralSettings);

  useEffect(() => {
    const sync = () => setSettings(readGeneralSettings());
    window.addEventListener(GENERAL_SETTINGS_EVENT, sync);
    window.addEventListener('storage', sync);

    if (!hydrated) {
      hydrateFromServer().then(setSettings);
    }

    return () => {
      window.removeEventListener(GENERAL_SETTINGS_EVENT, sync);
      window.removeEventListener('storage', sync);
    };
  }, []);

  return settings;
};
