import React, { createContext, useContext } from 'react';
import { useQuery } from '@tanstack/react-query';
import api from '../lib/api';

interface CompanySettings {
  id: string;
  companyName: string;
  address: string | null;
  phone: string | null;
  email: string | null;
  taxId: string | null;
  logoUrl: string | null;
  logoUrlDark: string | null;
  defaultCurrency: string;
  fiscalYearStartMonth?: number;
  onboardedAt?: string | null;
  hasDummyData?: boolean;
}

const CompanySettingsContext = createContext<CompanySettings | null>(null);

/**
 * Pengaturan perusahaan ERP (nama, NPWP, logo untuk kop dokumen/PDF). Favicon & judul aplikasi
 * milik platform, jadi tidak diubah di sini (berbeda dengan ENTARIS).
 */
export const CompanySettingsProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { data } = useQuery<CompanySettings>({
    queryKey: ['company-settings'],
    queryFn: async () => {
      const res = await api.get('/settings/company');
      return res.data;
    },
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  return (
    <CompanySettingsContext.Provider value={data ?? null}>
      {children}
    </CompanySettingsContext.Provider>
  );
};

export const useCompanySettings = () => useContext(CompanySettingsContext);
