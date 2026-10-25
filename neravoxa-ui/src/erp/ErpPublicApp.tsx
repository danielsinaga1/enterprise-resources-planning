/**
 * Halaman ERP publik tanpa login (di luar layout platform):
 * - `/erp/tanya`         formulir pertanyaan/permintaan penawaran untuk calon customer
 * - `/erp/aset/:qrCode`  info aset fisik dari scan label QR
 * Hanya memanggil endpoint `/api/erp/public/**` dan `/api/erp/ga/public/**`.
 */
import { Route, Routes } from 'react-router-dom';
import { lazyPage } from './app/lazy';
import { ErpPublicShell } from './ErpPublicShell';

const PublicInquiry = lazyPage(() => import('./pages/PublicInquiry'));
const PublicAssetScan = lazyPage(() => import('./pages/PublicAssetScan'));

export default function ErpPublicApp() {
  return (
    <ErpPublicShell>
      <Routes>
        <Route path="tanya" element={<PublicInquiry />} />
        <Route path="inquire" element={<PublicInquiry />} />
        <Route path="aset/:qrCode" element={<PublicAssetScan />} />
      </Routes>
    </ErpPublicShell>
  );
}
