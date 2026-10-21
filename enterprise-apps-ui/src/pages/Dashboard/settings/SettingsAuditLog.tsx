import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { Tag } from 'primereact/tag';
import { Card } from 'primereact/card';
import { BreadCrumb } from 'primereact/breadcrumb';
import { Dropdown } from 'primereact/dropdown';
import { Calendar } from 'primereact/calendar';
import { Avatar } from 'primereact/avatar';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Toast } from 'primereact/toast';
import { auditLogService, type AuditLogEntry } from '../../../services/auditLogService';
import ExportButtons from '../../../components/common/ExportButtons';
import type { ExportColumn } from '../../../utils/exportUtils';

const AKSI_MAP: Record<string, { severity: 'success' | 'info' | 'warning' | 'danger'; icon: string }> = {
  create: { severity: 'success', icon: 'pi pi-plus' },
  update: { severity: 'info', icon: 'pi pi-pencil' },
  delete: { severity: 'danger', icon: 'pi pi-trash' },
  login: { severity: 'warning', icon: 'pi pi-sign-in' },
};

const getInitials = (name: string) => {
  const parts = name.trim().split(/\s+/);
  return parts.length >= 2
    ? (parts[0][0] + parts[1][0]).toUpperCase()
    : name.slice(0, 2).toUpperCase();
};

const formatDate = (iso: string) => {
  const d = new Date(iso);
  return {
    date: d.toLocaleDateString('id-ID', { day: 'numeric', month: 'short', year: 'numeric' }),
    time: d.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
  };
};

const SettingsAuditLog = () => {
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);
  const [data, setData] = useState<AuditLogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [globalFilter, setGlobalFilter] = useState('');
  const [dateFilter, setDateFilter] = useState<Date | null>(null);

  const breadcrumbHome = { icon: 'pi pi-home', command: () => navigate('/dashboard') };
  const breadcrumbItems = [{ label: 'Sistem' }, { label: 'Audit Log' }];

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const startDate = dateFilter ? dateFilter.toISOString().slice(0, 10) : undefined;
      const endDate = startDate;
      const logs = await auditLogService.getAll({ startDate, endDate });
      setData(logs);
    } catch (err) {
      console.error('Failed to fetch audit logs', err);
      setData([]);
    } finally {
      setLoading(false);
    }
  }, [dateFilter]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const filtered = data.filter((d) => {
    const search = globalFilter.toLowerCase();
    return (
      !globalFilter ||
      d.user.toLowerCase().includes(search) ||
      (d.details ?? '').toLowerCase().includes(search) ||
      d.module.toLowerCase().includes(search) ||
      d.action.toLowerCase().includes(search)
    );
  });

  const counts = {
    total: data.length,
    create: data.filter((d) => d.action.toLowerCase().includes('create')).length,
    update: data.filter((d) => d.action.toLowerCase().includes('update') || d.action.toLowerCase().includes('approve')).length,
    delete: data.filter((d) => d.action.toLowerCase().includes('delete')).length,
  };

  /* ---------- Column Templates ---------- */
  const waktuTemplate = (row: AuditLogEntry) => {
    const { date, time } = formatDate(row.timestamp);
    return (
      <div>
        <div style={{ fontSize: '0.85rem', fontWeight: 500 }}>{date}</div>
        <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>{time}</div>
      </div>
    );
  };

  const userTemplate = (row: AuditLogEntry) => (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
      <Avatar label={getInitials(row.user)} shape="circle" size="normal" style={{ background: '#6366F1', color: '#fff' }} />
      <span style={{ fontWeight: 500 }}>{row.user}</span>
    </div>
  );

  const aksiTemplate = (row: AuditLogEntry) => {
    const key = row.action.toLowerCase();
    const matched = Object.entries(AKSI_MAP).find(([k]) => key.includes(k));
    if (matched) {
      const [, a] = matched;
      return <Tag icon={a.icon} severity={a.severity} value={` ${row.action}`} rounded />;
    }
    return <Tag value={row.action} />;
  };

  const ipTemplate = (row: AuditLogEntry) => (
    <span style={{ fontFamily: 'monospace', fontSize: '0.85rem', background: '#f3f4f6', padding: '2px 8px', borderRadius: 4 }}>{row.ipAddress}</span>
  );

  // Export configuration
  const exportColumns: ExportColumn[] = [
    { header: 'Waktu', field: 'timestamp', formatter: (v) => v ? new Date(v as string).toLocaleString('id-ID') : '-' },
    { header: 'User', field: 'user' },
    { header: 'Modul', field: 'module' },
    { header: 'Aksi', field: 'action' },
    { header: 'Deskripsi', field: 'details', formatter: (v) => (v as string) ?? '-' },
    { header: 'IP Address', field: 'ipAddress' },
  ];

  const fetchExportData = async (): Promise<Record<string, unknown>[]> => {
    const startDate = dateFilter ? dateFilter.toISOString().slice(0, 10) : undefined;
    const endDate = startDate;
    const logs = await auditLogService.getAll({ startDate, endDate });
    return logs as unknown as Record<string, unknown>[];
  };

  /* ---------- KPI Cards ---------- */
  const kpiCards = [
    { label: 'Total Log', value: counts.total, icon: 'pi pi-list', color: '#3B82F6' },
    { label: 'Create', value: counts.create, icon: 'pi pi-plus-circle', color: '#10B981' },
    { label: 'Update', value: counts.update, icon: 'pi pi-pencil', color: '#6366F1' },
    { label: 'Delete', value: counts.delete, icon: 'pi pi-trash', color: '#EF4444' },
  ];

  const header = (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center', justifyContent: 'space-between' }}>
      <span className="p-input-icon-left">
        <i className="pi pi-search" />
        <InputText placeholder="Cari aktivitas..." value={globalFilter} onChange={(e) => setGlobalFilter(e.target.value)} style={{ width: 240 }} />
      </span>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <Calendar value={dateFilter} onChange={(e) => setDateFilter(e.value as Date | null)} placeholder="Filter Tanggal" dateFormat="dd/mm/yy" showIcon showButtonBar style={{ width: 200 }} />
        <Button icon="pi pi-refresh" severity="secondary" outlined onClick={fetchData} tooltip="Refresh" />
      </div>
    </div>
  );

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
        <ProgressSpinner />
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Toast ref={toast} />
      <BreadCrumb model={breadcrumbItems} home={breadcrumbHome} />

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <h2 style={{ margin: 0, fontSize: '1.5rem', fontWeight: 700 }}>Audit Log</h2>
        <ExportButtons
          permissionCode="SETTINGS_MANAGE"
          columns={exportColumns}
          fetchData={fetchExportData}
          fileName="Audit_Log"
          title="Audit Log"
          toastRef={toast}
        />
      </div>

      {/* KPI Cards */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16 }}>
        {kpiCards.map((kpi) => (
          <Card key={kpi.label} style={{ flex: '1 1 180px', borderLeft: `4px solid ${kpi.color}` }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
              <i className={kpi.icon} style={{ fontSize: '1.8rem', color: kpi.color }} />
              <div>
                <div style={{ fontSize: '1.6rem', fontWeight: 700 }}>{kpi.value}</div>
                <div style={{ color: '#6b7280', fontSize: '0.85rem' }}>{kpi.label}</div>
              </div>
            </div>
          </Card>
        ))}
      </div>

      {/* DataTable */}
      <Card>
        <DataTable value={filtered} paginator rows={10} header={header} emptyMessage="Belum ada data audit log" stripedRows removableSort rowHover loading={loading}>
          <Column header="Waktu" body={waktuTemplate} sortable sortField="timestamp" style={{ minWidth: 130 }} />
          <Column header="User" body={userTemplate} sortable sortField="user" style={{ minWidth: 180 }} />
          <Column header="Aksi" body={aksiTemplate} sortable sortField="action" style={{ minWidth: 130 }} />
          <Column field="module" header="Modul" sortable style={{ minWidth: 120 }} />
          <Column field="details" header="Deskripsi" style={{ minWidth: 250 }} />
          <Column header="IP Address" body={ipTemplate} style={{ minWidth: 140 }} />
        </DataTable>
      </Card>
    </div>
  );
};

export default SettingsAuditLog;
