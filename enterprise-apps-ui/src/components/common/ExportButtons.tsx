import { useState, useEffect, useCallback, useRef } from 'react';
import { SplitButton } from 'primereact/splitbutton';
import { Toast } from 'primereact/toast';
import { userPermissionService } from '../../services/rbacService';
import { useAuth } from '../../context/AuthContext';
import {
  exportToExcel,
  exportToPdf,
  exportGroupedToExcel,
  exportGroupedToPdf,
  type ExportColumn,
  type ExportGroup,
  type ChartSpec,
  type SummaryItem,
} from '../../utils/exportUtils';

export interface GroupedExportPayload {
  groups: ExportGroup[];
  charts?: ChartSpec[];
  summary?: SummaryItem[];
  subtitle?: string;
}

interface ExportButtonsProps {
  /** RBAC permission code, e.g. 'INCIDENT_REPORT' */
  permissionCode: string;
  /** Column definitions for export. Tidak dipakai bila `fetchGroups` diisi. */
  columns?: ExportColumn[];
  /** Function to fetch ALL data for export (no pagination). Tidak dipakai bila `fetchGroups` diisi. */
  fetchData?: () => Promise<Record<string, unknown>[]>;
  /**
   * Mode berkelompok: tiap kelompok jadi sheet Excel / bagian PDF sendiri,
   * plus halaman ringkasan berisi angka kunci dan chart. Dipakai saat satu
   * daftar memuat beberapa jenis laporan dengan kolom yang berbeda.
   */
  fetchGroups?: () => Promise<GroupedExportPayload>;
  /** Base filename without extension */
  fileName: string;
  /** PDF title */
  title?: string;
  /** External toast ref (optional — uses own if not provided) */
  toastRef?: React.RefObject<Toast | null>;
}

const ExportButtons: React.FC<ExportButtonsProps> = ({
  permissionCode,
  columns,
  fetchData,
  fetchGroups,
  fileName,
  title,
  toastRef,
}) => {
  const { user } = useAuth();
  const [canExport, setCanExport] = useState(false);
  const [loading, setLoading] = useState(false);
  const internalToast = useRef<Toast>(null);
  const toast = toastRef ?? internalToast;

  
  const checkPermission = useCallback(async () => {
    if (!user) return;
    // SUPER_ADMIN always has export access
    if (user.roles?.includes('SUPER_ADMIN')) {
      setCanExport(true);
      return;
    }
    try {
      const hasAccess = await userPermissionService.check(user.id, permissionCode, 'export');
      setCanExport(hasAccess);
    } catch {
      setCanExport(false);
    }
  }, [user, permissionCode]);

  useEffect(() => {
    checkPermission();
  }, [checkPermission]);

  const handleExport = async (format: 'excel' | 'pdf') => {
    setLoading(true);
    try {
      const emptyWarning = () =>
        toast.current?.show({
          severity: 'warn',
          summary: 'Peringatan',
          detail: 'Tidak ada data untuk diekspor',
          life: 3000,
        });

      if (fetchGroups) {
        const payload = await fetchGroups();
        const totalRows = payload.groups.reduce((n, g) => n + g.rows.length, 0);
        if (totalRows === 0) {
          emptyWarning();
          return;
        }
        if (format === 'excel') {
          exportGroupedToExcel(payload.groups, fileName, payload.summary);
        } else {
          exportGroupedToPdf(payload.groups, fileName, title ?? fileName, {
            charts: payload.charts,
            summary: payload.summary,
            subtitle: payload.subtitle,
          });
        }
      } else {
        if (!fetchData || !columns) return;
        const data = await fetchData();
        if (!data || data.length === 0) {
          emptyWarning();
          return;
        }
        if (format === 'excel') {
          exportToExcel(data, columns, fileName);
        } else {
          exportToPdf(data, columns, fileName, title);
        }
      }
      toast.current?.show({
        severity: 'success',
        summary: 'Berhasil',
        detail: `Data berhasil diekspor ke ${format === 'excel' ? 'Excel' : 'PDF'}`,
        life: 3000,
      });
    } catch (err) {
      console.error('Export error:', err);
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Gagal mengekspor data',
        life: 3000,
      });
    } finally {
      setLoading(false);
    }
  };

  if (!canExport) return null;

  const items = [
    {
      label: 'Export PDF',
      icon: 'pi pi-file-pdf',
      command: () => handleExport('pdf'),
    },
  ];

  return (
    <>
      {!toastRef && <Toast ref={internalToast} />}
      <SplitButton
        label="Export Excel"
        icon="pi pi-file-excel"
        model={items}
        onClick={() => handleExport('excel')}
        severity="info"
        loading={loading}
        className="shadow-1 border-round-lg"
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        pt={{
          root: { style: { borderRadius: '10px', overflow: 'hidden' } } as any,
          button: { style: { borderRadius: '10px 0 0 10px', gap: '0.5rem', fontWeight: 600, padding: '0.85rem 1.5rem', fontSize: '1rem' } } as any,
          menuButton: { style: { borderRadius: '0 10px 10px 0', padding: '0.85rem 0.9rem', fontSize: '1rem' } } as any,
        }}
      />
    </>
  );
};

export default ExportButtons;
