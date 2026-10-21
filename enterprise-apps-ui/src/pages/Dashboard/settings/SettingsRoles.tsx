import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { InputTextarea } from 'primereact/inputtextarea';
import { Tag } from 'primereact/tag';
import { Card } from 'primereact/card';
import { BreadCrumb } from 'primereact/breadcrumb';
import { Dialog } from 'primereact/dialog';
import { Checkbox } from 'primereact/checkbox';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { Badge } from 'primereact/badge';
import { Toast } from 'primereact/toast';
import { useRef } from 'react';
import {
  roleService,
  permissionService,
  rolePermissionService,
} from '../../../services/rbacService';
import type { Role, Permission, RolePermission } from '../../../types';

/* ========== Types ========== */
interface PermissionRow {
  permissionId: number;
  code: string;
  name: string;
  module: string;
  subModule: string;
  canCreate: boolean;
  canRead: boolean;
  canUpdate: boolean;
  canDelete: boolean;
  canApprove: boolean;
  canExport: boolean;
}

interface SubModuleGroup {
  subModule: string;
  label: string;
  rows: PermissionRow[];
}

interface ModuleGroup {
  module: string;
  rows: PermissionRow[];
  subs: SubModuleGroup[];
}

const MODULE_LABELS: Record<string, string> = {
  DASHBOARD: 'Dashboard',
  INCIDENT: 'Manajemen Insiden',
  SAFETY: 'Keselamatan',
  RISK: 'Manajemen Risiko',
  LIMS: 'Laboratorium (LIMS)',
  ORGANISASI: 'Organisasi',
  SYSTEM: 'Sistem',
};

const MODULE_ICONS: Record<string, string> = {
  DASHBOARD: 'pi pi-th-large',
  INCIDENT: 'pi pi-exclamation-triangle',
  SAFETY: 'pi pi-shield',
  RISK: 'pi pi-chart-bar',
  LIMS: 'pi pi-flask',
  ORGANISASI: 'pi pi-sitemap',
  SYSTEM: 'pi pi-wrench',
};

const SUB_MODULE_LABELS: Record<string, string> = {
  REPORT: 'Laporan',
  INVESTIGATION: 'Investigasi',
  FOLLOWUP: 'Tindak Lanjut',
  OBSERVATION: 'Observasi',
  AUDIT: 'Audit Keselamatan',
  ASSESSMENT: 'Penilaian Risiko',
  REGISTER: 'Register Risiko',
  MATRIX: 'Matriks Risiko',
  DASHBOARD: 'Dashboard',
  ANALYSIS: 'Detail Analisis',
  APPROVAL: 'Review & Approval',
  DAILY: 'Laporan Harian',
  CALENDAR: 'Kalender',
  PARAMETER: 'Parameter Uji',
  TEST_TYPE: 'Tipe Pengujian',
  METHOD: 'Metode',
  STANDARD: 'Standar',
  UNIT: 'Satuan',
  RESULT: 'Hasil Pengujian',
  LAB_DEPT: 'Departemen Lab',
  COMPOUND: 'Senyawa Kimia',
  DEPARTMENT: 'Departemen',
  USER: 'User',
  ROLE: 'Role',
  PERMISSION: 'Permission',
  SETTINGS: 'Pengaturan',
};

const MODULE_ORDER = [
  'DASHBOARD',
  'INCIDENT',
  'SAFETY',
  'RISK',
  'LIMS',
  'ORGANISASI',
  'SYSTEM',
];

const SettingsRoles = () => {
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);

  const [roles, setRoles] = useState<Role[]>([]);
  const [allPermissions, setAllPermissions] = useState<Permission[]>([]);
  const [globalFilter, setGlobalFilter] = useState('');
  const [loading, setLoading] = useState(true);

  // Dialog state
  const [showDialog, setShowDialog] = useState(false);
  const [editingRole, setEditingRole] = useState<Role | null>(null);
  const [roleName, setRoleName] = useState('');
  const [roleCode, setRoleCode] = useState('');
  const [roleDescription, setRoleDescription] = useState('');
  const [permissionRows, setPermissionRows] = useState<PermissionRow[]>([]);
  const [saving, setSaving] = useState(false);

  /* ========== Load data ========== */
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [roleList, permList] = await Promise.all([
        roleService.list(),
        permissionService.list(),
      ]);
      setRoles(roleList);
      setAllPermissions(permList);
    } catch {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Gagal memuat data',
      });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  /* ========== Build permission matrix ========== */
  const buildPermissionRows = (
    perms: Permission[],
    existing: RolePermission[]
  ): PermissionRow[] => {
    const existingMap = new Map<number, RolePermission>();
    for (const rp of existing) {
      existingMap.set(rp.permissionId, rp);
    }

    return perms.map((p) => {
      const rp = existingMap.get(p.id);
      return {
        permissionId: p.id,
        code: p.code,
        name: p.name,
        module: p.module,
        subModule: p.subModule ?? '',
        canCreate: rp?.canCreate ?? false,
        canRead: rp?.canRead ?? false,
        canUpdate: rp?.canUpdate ?? false,
        canDelete: rp?.canDelete ?? false,
        canApprove: rp?.canApprove ?? false,
        canExport: rp?.canExport ?? false,
      };
    });
  };

  const groupByModule = (rows: PermissionRow[]): ModuleGroup[] => {
    const map = new Map<string, PermissionRow[]>();
    for (const row of rows) {
      const existing = map.get(row.module) ?? [];
      existing.push(row);
      map.set(row.module, existing);
    }

    return MODULE_ORDER.filter((m) => map.has(m)).map((m) => {
      const moduleRows = map.get(m)!;
      // Group by sub-module within each module
      const subMap = new Map<string, PermissionRow[]>();
      for (const row of moduleRows) {
        const sub = row.subModule || '_GENERAL_';
        const existing = subMap.get(sub) ?? [];
        existing.push(row);
        subMap.set(sub, existing);
      }

      const subs: SubModuleGroup[] = [];
      for (const [sub, subRows] of subMap) {
        subs.push({
          subModule: sub,
          label: sub === '_GENERAL_' ? 'Umum' : (SUB_MODULE_LABELS[sub] || sub),
          rows: subRows,
        });
      }

      return {
        module: m,
        rows: moduleRows,
        subs,
      };
    });
  };

  /* ========== Dialog handlers ========== */
  const openCreateDialog = () => {
    setEditingRole(null);
    setRoleName('');
    setRoleCode('');
    setRoleDescription('');
    setPermissionRows(buildPermissionRows(allPermissions, []));
    setShowDialog(true);
  };

  const openEditDialog = async (role: Role) => {
    setEditingRole(role);
    setRoleName(role.name);
    setRoleCode(role.code);
    setRoleDescription(role.description ?? '');
    try {
      const rpList = await rolePermissionService.list(role.id);
      setPermissionRows(buildPermissionRows(allPermissions, rpList));
    } catch {
      setPermissionRows(buildPermissionRows(allPermissions, []));
    }
    setShowDialog(true);
  };

  const togglePermission = (
    permissionId: number,
    field: keyof PermissionRow
  ) => {
    setPermissionRows((prev) =>
      prev.map((row) =>
        row.permissionId === permissionId
          ? { ...row, [field]: !row[field] }
          : row
      )
    );
  };

  const toggleModuleAll = (module: string, field: keyof PermissionRow, value: boolean) => {
    setPermissionRows((prev) =>
      prev.map((row) =>
        row.module === module ? { ...row, [field]: value } : row
      )
    );
  };

  const toggleSubModuleAll = (module: string, subModule: string, field: keyof PermissionRow, value: boolean) => {
    setPermissionRows((prev) =>
      prev.map((row) =>
        row.module === module && (row.subModule || '_GENERAL_') === subModule
          ? { ...row, [field]: value }
          : row
      )
    );
  };

  const saveRole = async () => {
    if (!roleName.trim() || !roleCode.trim()) {
      toast.current?.show({
        severity: 'warn',
        summary: 'Validasi',
        detail: 'Nama dan Kode role wajib diisi',
      });
      return;
    }

    setSaving(true);
    try {
      let savedRole: Role;
      if (editingRole) {
        savedRole = await roleService.update(editingRole.id, {
          name: roleName,
          code: roleCode,
          description: roleDescription,
        });
      } else {
        savedRole = await roleService.create({
          name: roleName,
          code: roleCode,
          description: roleDescription,
        });
      }

      // Save permissions
      const permPayload = permissionRows.map((r) => ({
        permissionId: r.permissionId,
        canCreate: r.canCreate,
        canRead: r.canRead,
        canUpdate: r.canUpdate,
        canDelete: r.canDelete,
        canApprove: r.canApprove,
        canExport: r.canExport,
      }));

      await rolePermissionService.bulkSave(savedRole.id, permPayload);

      toast.current?.show({
        severity: 'success',
        summary: 'Berhasil',
        detail: editingRole ? 'Role berhasil diperbarui' : 'Role berhasil dibuat',
      });
      setShowDialog(false);
      loadData();
    } catch (err: unknown) {
      const errData = (err as { response?: { data?: { message?: string; errorMessage?: string } } })?.response?.data;
      const msg = errData?.errorMessage || errData?.message || 'Gagal menyimpan role';
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: msg,
      });
    } finally {
      setSaving(false);
    }
  };

  /* ========== Table templates ========== */
  const breadcrumbHome = {
    icon: 'pi pi-home',
    command: () => navigate('/dashboard'),
  };
  const breadcrumbItems = [
    { label: 'Manajemen User' },
    { label: 'Role & Permission' },
  ];

  const filtered = roles.filter(
    (r) =>
      r.name.toLowerCase().includes(globalFilter.toLowerCase()) ||
      (r.description ?? '').toLowerCase().includes(globalFilter.toLowerCase())
  );

  const nameTemplate = (row: Role) => (
    <div>
      <div style={{ fontWeight: 600 }}>{row.name}</div>
      <div style={{ fontSize: '0.8rem', color: '#6b7280' }}>
        {row.code}
        {row.description ? ` — ${row.description}` : ''}
      </div>
    </div>
  );

  const statusTemplate = (row: Role) => (
    <Tag
      value={row.isSystemRole ? 'System' : 'Custom'}
      severity={row.isSystemRole ? 'info' : 'success'}
    />
  );

  const actionTemplate = (row: Role) => (
    <div style={{ display: 'flex', gap: 8 }}>
      <Button
        icon="pi pi-pencil"
        rounded
        text
        severity="info"
        tooltip="Edit"
        tooltipOptions={{ position: 'top' }}
        onClick={() => openEditDialog(row)}
      />
      {!row.isSystemRole && (
        <Button
          icon="pi pi-trash"
          rounded
          text
          severity="danger"
          tooltip="Hapus"
          tooltipOptions={{ position: 'top' }}
          onClick={() =>
            confirmDialog({
              message: `Hapus role "${row.name}"?`,
              header: 'Konfirmasi',
              icon: 'pi pi-exclamation-triangle',
              acceptClassName: 'p-button-danger',
              accept: async () => {
                try {
                  await roleService.delete(row.id);
                  toast.current?.show({
                    severity: 'success',
                    summary: 'Berhasil',
                    detail: `Role "${row.name}" berhasil dihapus`,
                  });
                  loadData();
                } catch {
                  toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: 'Gagal menghapus role',
                  });
                }
              },
            })
          }
        />
      )}
    </div>
  );

  /* ========== KPI ========== */
  const kpiCards = [
    {
      label: 'Total Role',
      value: roles.length,
      icon: 'pi pi-shield',
      color: '#3B82F6',
    },
    {
      label: 'Total Permission',
      value: allPermissions.length,
      icon: 'pi pi-lock',
      color: '#F59E0B',
    },
    {
      label: 'Modul',
      value: new Set(allPermissions.map((p) => p.module)).size,
      icon: 'pi pi-th-large',
      color: '#10B981',
    },
  ];

  const header = (
    <div
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: 12,
        alignItems: 'center',
        justifyContent: 'space-between',
      }}
    >
      <span className="p-input-icon-left">
        <i className="pi pi-search" />
        <InputText
          placeholder="Cari role..."
          value={globalFilter}
          onChange={(e) => setGlobalFilter(e.target.value)}
          style={{ width: 260 }}
        />
      </span>
    </div>
  );

  /* ========== Permission Matrix in Dialog ========== */
  const moduleGroups = groupByModule(permissionRows);
  const ACTIONS: { key: keyof PermissionRow; label: string }[] = [
    { key: 'canCreate', label: 'Create' },
    { key: 'canRead', label: 'Read' },
    { key: 'canUpdate', label: 'Update' },
    { key: 'canDelete', label: 'Delete' },
    { key: 'canApprove', label: 'Approve' },
    { key: 'canExport', label: 'Export' },
  ];

  const dialogFooter = (
    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
      <Button
        label="Batal"
        icon="pi pi-times"
        severity="secondary"
        outlined
        onClick={() => setShowDialog(false)}
      />
      <Button
        label="Simpan"
        icon="pi pi-check"
        loading={saving}
        onClick={saveRole}
      />
    </div>
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Toast ref={toast} />
      <ConfirmDialog />

      <BreadCrumb model={breadcrumbItems} home={breadcrumbHome} />

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <h2 style={{ margin: 0, fontSize: '1.5rem', fontWeight: 700 }}>
          Role & Permission
        </h2>
        <Button
          label="Tambah Role"
          icon="pi pi-plus"
          severity="info"
          outlined
          className="border-round-lg shadow-1 font-semibold"
          style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem', border: '2px solid #3B82F6' }}
          onClick={openCreateDialog}
        />
      </div>

      {/* KPI Cards */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16 }}>
        {kpiCards.map((kpi) => (
          <Card
            key={kpi.label}
            style={{ flex: '1 1 200px', borderLeft: `4px solid ${kpi.color}` }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
              <i
                className={kpi.icon}
                style={{ fontSize: '1.8rem', color: kpi.color }}
              />
              <div>
                <div style={{ fontSize: '1.6rem', fontWeight: 700 }}>
                  {kpi.value}
                </div>
                <div style={{ color: '#6b7280', fontSize: '0.85rem' }}>
                  {kpi.label}
                </div>
              </div>
            </div>
          </Card>
        ))}
      </div>

      {/* DataTable */}
      <Card>
        <DataTable
          value={filtered}
          paginator
          rows={10}
          header={header}
          emptyMessage="Belum ada data role"
          stripedRows
          removableSort
          dataKey="id"
          rowHover
          loading={loading}
        >
          <Column
            header="Role"
            body={nameTemplate}
            sortable
            sortField="name"
            style={{ minWidth: 300 }}
          />
          <Column
            header="Tipe"
            body={statusTemplate}
            style={{ minWidth: 100 }}
          />
          <Column
            field="priority"
            header="Prioritas"
            sortable
            style={{ minWidth: 100 }}
          />
          <Column
            header="Aksi"
            body={actionTemplate}
            style={{ minWidth: 120 }}
          />
        </DataTable>
      </Card>

      {/* Dialog — Role Form + Permission Matrix */}
      <Dialog
        header={editingRole ? `Edit Role: ${editingRole.name}` : 'Tambah Role Baru'}
        visible={showDialog}
        onHide={() => setShowDialog(false)}
        style={{ width: '90vw', maxWidth: 960 }}
        footer={dialogFooter}
        modal
        maximizable
      >
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
            paddingTop: 8,
          }}
        >
          {/* Role info */}
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            <div style={{ flex: '1 1 200px' }}>
              <label style={{ display: 'block', marginBottom: 6, fontWeight: 600 }}>
                Kode Role
              </label>
              <InputText
                value={roleCode}
                onChange={(e) => setRoleCode(e.target.value.toUpperCase().replace(/\s/g, '_'))}
                placeholder="KODE_ROLE"
                style={{ width: '100%' }}
                disabled={!!editingRole?.isSystemRole}
              />
            </div>
            <div style={{ flex: '1 1 200px' }}>
              <label style={{ display: 'block', marginBottom: 6, fontWeight: 600 }}>
                Nama Role
              </label>
              <InputText
                value={roleName}
                onChange={(e) => setRoleName(e.target.value)}
                placeholder="Nama role"
                style={{ width: '100%' }}
              />
            </div>
          </div>
          <div>
            <label style={{ display: 'block', marginBottom: 6, fontWeight: 600 }}>
              Deskripsi
            </label>
            <InputTextarea
              value={roleDescription}
              onChange={(e) => setRoleDescription(e.target.value)}
              placeholder="Deskripsi singkat role"
              rows={2}
              style={{ width: '100%' }}
            />
          </div>

          {/* Permission Matrix */}
          <div>
            <label
              style={{
                display: 'block',
                marginBottom: 12,
                fontWeight: 700,
                fontSize: '1.1rem',
              }}
            >
              Matriks Permission
            </label>

            <div
              style={{
                border: '1px solid #e5e7eb',
                borderRadius: 8,
                overflow: 'hidden',
              }}
            >
              {/* Header row */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr repeat(6, 70px)',
                  background: '#f3f4f6',
                  fontWeight: 700,
                  fontSize: '0.8rem',
                  textAlign: 'center',
                  borderBottom: '2px solid #d1d5db',
                }}
              >
                <div style={{ padding: '10px 14px', textAlign: 'left' }}>
                  Modul / Sub-modul
                </div>
                {ACTIONS.map((a) => (
                  <div key={a.key} style={{ padding: '10px 4px' }}>
                    {a.label}
                  </div>
                ))}
              </div>

              {/* Module groups */}
              {moduleGroups.map((group) => {
                const isAllChecked = (field: keyof PermissionRow) =>
                  group.rows.every((r) => r[field] as boolean);
                const hasSubModules = group.subs.length > 1 || (group.subs.length === 1 && group.subs[0].subModule !== '_GENERAL_');

                return (
                  <div key={group.module}>
                    {/* Module header */}
                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns: '1fr repeat(6, 70px)',
                        background: '#eef2ff',
                        borderBottom: '1px solid #d1d5db',
                        borderTop: '1px solid #d1d5db',
                      }}
                    >
                      <div
                        style={{
                          padding: '8px 14px',
                          fontWeight: 700,
                          color: '#4338ca',
                          fontSize: '0.9rem',
                        }}
                      >
                        <i className={MODULE_ICONS[group.module] || 'pi pi-folder'} style={{ marginRight: 8 }} />
                        {MODULE_LABELS[group.module] || group.module}
                      </div>
                      {ACTIONS.map((a) => (
                        <div
                          key={a.key}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                          }}
                        >
                          <Checkbox
                            checked={isAllChecked(a.key)}
                            onChange={() =>
                              toggleModuleAll(
                                group.module,
                                a.key,
                                !isAllChecked(a.key)
                              )
                            }
                          />
                        </div>
                      ))}
                    </div>

                    {/* Sub-module groups */}
                    {group.subs.map((sub) => {
                      const isSubAllChecked = (field: keyof PermissionRow) =>
                        sub.rows.every((r) => r[field] as boolean);

                      return (
                        <div key={sub.subModule}>
                          {/* Sub-module header (only show if module has sub-modules) */}
                          {hasSubModules && (
                            <div
                              style={{
                                display: 'grid',
                                gridTemplateColumns: '1fr repeat(6, 70px)',
                                background: '#f0fdf4',
                                borderBottom: '1px solid #e5e7eb',
                              }}
                            >
                              <div
                                style={{
                                  padding: '6px 14px 6px 28px',
                                  fontWeight: 600,
                                  color: '#15803d',
                                  fontSize: '0.82rem',
                                }}
                              >
                                <i className="pi pi-angle-right" style={{ marginRight: 6 }} />
                                {sub.label}
                              </div>
                              {ACTIONS.map((a) => (
                                <div
                                  key={a.key}
                                  style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                  }}
                                >
                                  <Checkbox
                                    checked={isSubAllChecked(a.key)}
                                    onChange={() =>
                                      toggleSubModuleAll(
                                        group.module,
                                        sub.subModule,
                                        a.key,
                                        !isSubAllChecked(a.key)
                                      )
                                    }
                                  />
                                </div>
                              ))}
                            </div>
                          )}

                          {/* Permission rows */}
                          {sub.rows.map((row, idx) => (
                            <div
                              key={row.permissionId}
                              style={{
                                display: 'grid',
                                gridTemplateColumns: '1fr repeat(6, 70px)',
                                borderBottom:
                                  idx < sub.rows.length - 1
                                    ? '1px solid #f3f4f6'
                                    : '1px solid #e5e7eb',
                                background: idx % 2 === 0 ? '#fff' : '#fafafa',
                              }}
                            >
                              <div
                                style={{
                                  padding: hasSubModules ? '7px 14px 7px 46px' : '7px 14px 7px 32px',
                                  fontSize: '0.85rem',
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: 6,
                                }}
                              >
                                {!hasSubModules && row.subModule && (
                                  <Badge
                                    value={row.subModule}
                                    severity="info"
                                    style={{ fontSize: '0.7rem' }}
                                  />
                                )}
                                <span>{row.name}</span>
                              </div>
                              {ACTIONS.map((a) => (
                                <div
                                  key={a.key}
                                  style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                  }}
                                >
                                  <Checkbox
                                    checked={row[a.key] as boolean}
                                    onChange={() =>
                                      togglePermission(row.permissionId, a.key)
                                    }
                                  />
                                </div>
                              ))}
                            </div>
                          ))}
                        </div>
                      );
                    })}
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </Dialog>
    </div>
  );
};

export default SettingsRoles;
