import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Dropdown } from 'primereact/dropdown';
import { Button } from 'primereact/button';
import { Checkbox } from 'primereact/checkbox';
import { Card } from 'primereact/card';
import { BreadCrumb } from 'primereact/breadcrumb';
import { Toast } from 'primereact/toast';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Tag } from 'primereact/tag';
import {
  roleService,
  permissionService,
  rolePermissionService,
} from '../../../services/rbacService';
import type { Role, Permission, RolePermission } from '../../../types';

/* ========== helpers ========== */

interface PermRow {
  permissionId: number;
  code: string;
  name: string;
  module: string;
  subModule: string | null;
  canRead: boolean;
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
  canApprove: boolean;
  canExport: boolean;
}

interface ModuleGroup {
  module: string;
  label: string;
  icon: string;
  subs: SubModuleGroup[];
}

interface SubModuleGroup {
  subModule: string | null;
  label: string;
  rows: PermRow[];
}

const MODULE_META: Record<string, { label: string; icon: string; order: number }> = {
  DASHBOARD: { label: 'Dashboard', icon: 'pi pi-th-large', order: 0 },
  INCIDENT: { label: 'Manajemen Insiden', icon: 'pi pi-exclamation-triangle', order: 1 },
  SAFETY: { label: 'Keselamatan', icon: 'pi pi-shield', order: 2 },
  RISK: { label: 'Manajemen Risiko', icon: 'pi pi-chart-bar', order: 3 },
  LIMS: { label: 'Laboratorium (LIMS)', icon: 'pi pi-flask', order: 4 },
  ORGANISASI: { label: 'Organisasi', icon: 'pi pi-sitemap', order: 5 },
  SYSTEM: { label: 'Sistem', icon: 'pi pi-wrench', order: 6 },
};

const SUB_MODULE_LABELS: Record<string, string> = {
  REPORT: 'Laporan Insiden',
  INVESTIGATION: 'Investigasi',
  FOLLOWUP: 'Tindak Lanjut',
  OBSERVATION: 'Observasi',
  AUDIT: 'Audit Keselamatan',
  ASSESSMENT: 'Penilaian Risiko',
  REGISTER: 'Register Risiko',
  MATRIX: 'Matriks Risiko',
  LAB_REPORT: 'Laporan Laboratorium',
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

const ACTIONS = ['canRead', 'canCreate', 'canUpdate', 'canDelete', 'canApprove', 'canExport'] as const;
type ActionKey = (typeof ACTIONS)[number];

const COL_LABELS: Record<ActionKey, string> = {
  canRead: 'View',
  canCreate: 'Create',
  canUpdate: 'Update',
  canDelete: 'Delete',
  canApprove: 'Approve',
  canExport: 'Export',
};

/* ========== Component ========== */
const AccessManagementPage = () => {
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);

  const [roles, setRoles] = useState<Role[]>([]);
  const [selectedRoleId, setSelectedRoleId] = useState<number | null>(null);
  const [allPermissions, setAllPermissions] = useState<Permission[]>([]);
  const [permRows, setPermRows] = useState<PermRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingPerms, setLoadingPerms] = useState(false);
  const [saving, setSaving] = useState(false);

  /* ---- initial load ---- */
  const loadRolesAndPermissions = useCallback(async () => {
    setLoading(true);
    try {
      const [roleList, permList] = await Promise.all([
        roleService.list(),
        permissionService.list(),
      ]);
      setRoles(roleList);
      setAllPermissions(permList);
    } catch {
      toast.current?.show({ severity: 'error', summary: 'Error', detail: 'Gagal memuat data' });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRolesAndPermissions();
  }, [loadRolesAndPermissions]);

  /* ---- load permissions for selected role ---- */
  const loadRolePermissions = useCallback(
    async (roleId: number) => {
      setLoadingPerms(true);
      try {
        const rpList: RolePermission[] = await rolePermissionService.list(roleId);
        const rpMap = new Map<number, RolePermission>();
        for (const rp of rpList) rpMap.set(rp.permissionId, rp);

        setPermRows(
          allPermissions.map((p) => {
            const rp = rpMap.get(p.id);
            return {
              permissionId: p.id,
              code: p.code,
              name: p.name,
              module: p.module,
              subModule: p.subModule ?? null,
              canRead: rp?.canRead ?? false,
              canCreate: rp?.canCreate ?? false,
              canUpdate: rp?.canUpdate ?? false,
              canDelete: rp?.canDelete ?? false,
              canApprove: rp?.canApprove ?? false,
              canExport: rp?.canExport ?? false,
            };
          })
        );
      } catch {
        setPermRows(
          allPermissions.map((p) => ({
            permissionId: p.id,
            code: p.code,
            name: p.name,
            module: p.module,
            subModule: p.subModule ?? null,
            canRead: false,
            canCreate: false,
            canUpdate: false,
            canDelete: false,
            canApprove: false,
            canExport: false,
          }))
        );
      } finally {
        setLoadingPerms(false);
      }
    },
    [allPermissions]
  );

  useEffect(() => {
    if (selectedRoleId != null && allPermissions.length > 0) {
      loadRolePermissions(selectedRoleId);
    }
  }, [selectedRoleId, allPermissions, loadRolePermissions]);

  /* ---- group into hierarchy ---- */
  const moduleGroups: ModuleGroup[] = (() => {
    const map = new Map<string, Map<string | null, PermRow[]>>();
    for (const row of permRows) {
      // Skip modules not defined in MODULE_META (deprecated modules)
      if (!MODULE_META[row.module]) continue;
      if (!map.has(row.module)) map.set(row.module, new Map());
      const subMap = map.get(row.module)!;
      const key = row.subModule;
      if (!subMap.has(key)) subMap.set(key, []);
      subMap.get(key)!.push(row);
    }

    return [...map.entries()]
      .sort(([a], [b]) => (MODULE_META[a]?.order ?? 99) - (MODULE_META[b]?.order ?? 99))
      .map(([mod, subMap]) => {
        const meta = MODULE_META[mod];
        const subs: SubModuleGroup[] = [...subMap.entries()].map(([sub, rows]) => ({
          subModule: sub,
          label: sub ? SUB_MODULE_LABELS[sub] ?? sub : meta.label,
          rows,
        }));
        return { module: mod, label: meta.label, icon: meta.icon, subs };
      });
  })();

  /* ---- toggle helpers ---- */
  const getModuleRows = (mod: string) => permRows.filter((r) => r.module === mod);
  const isModuleFieldChecked = (mod: string, field: ActionKey) =>
    getModuleRows(mod).length > 0 && getModuleRows(mod).every((r) => r[field]);
  const isModuleAllChecked = (mod: string) =>
    getModuleRows(mod).length > 0 && getModuleRows(mod).every((r) => ACTIONS.every((a) => r[a]));

  const toggleModuleField = (mod: string, field: ActionKey) => {
    const setTo = !isModuleFieldChecked(mod, field);
    setPermRows((prev) =>
      prev.map((r) => (r.module === mod ? { ...r, [field]: setTo } : r))
    );
  };

  const toggleModuleAll = (mod: string) => {
    const setTo = !isModuleAllChecked(mod);
    setPermRows((prev) =>
      prev.map((r) =>
        r.module === mod
          ? { ...r, canRead: setTo, canCreate: setTo, canUpdate: setTo, canDelete: setTo, canApprove: setTo, canExport: setTo }
          : r
      )
    );
  };

  /* ---- save ---- */
  const handleSave = async () => {
    if (selectedRoleId == null) return;
    setSaving(true);
    try {
      const payload = permRows.map((r) => ({
        permissionId: r.permissionId,
        canCreate: r.canCreate,
        canRead: r.canRead,
        canUpdate: r.canUpdate,
        canDelete: r.canDelete,
        canApprove: r.canApprove,
        canExport: r.canExport,
      }));
      await rolePermissionService.bulkSave(selectedRoleId, payload);
      toast.current?.show({
        severity: 'success',
        summary: 'Berhasil',
        detail: 'Hak akses berhasil disimpan',
      });
    } catch {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Gagal menyimpan hak akses',
      });
    } finally {
      setSaving(false);
    }
  };

  /* ---- breadcrumb ---- */
  const breadcrumbHome = { icon: 'pi pi-home', command: () => navigate('/dashboard') };
  const breadcrumbItems = [{ label: 'Pengaturan' }, { label: 'Manajemen Hak Akses' }];

  /* ---- render ---- */
  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
        <ProgressSpinner />
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Toast ref={toast} />
      <BreadCrumb model={breadcrumbItems} home={breadcrumbHome} />

      <Card>
        <h2 style={{ margin: '0 0 4px', fontSize: '1.4rem', fontWeight: 700 }}>
          Manajemen Hak Akses
        </h2>
        <p style={{ margin: '0 0 20px', color: '#6b7280' }}>
          Kelola hak akses modul dan submodul berdasarkan role pengguna
        </p>

        {/* Role selector + save */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 16,
            marginBottom: 24,
            flexWrap: 'wrap',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <label style={{ fontWeight: 600 }}>Role:</label>
            <Dropdown
              value={selectedRoleId}
              options={roles.map((r) => ({ label: r.name, value: r.id }))}
              onChange={(e) => setSelectedRoleId(e.value)}
              placeholder="Pilih Role"
              style={{ minWidth: 220 }}
              filter
              filterPlaceholder="Cari role..."
            />
          </div>
          <Button
            label="Simpan Hak Akses"
            icon="pi pi-save"
            onClick={handleSave}
            loading={saving}
            disabled={selectedRoleId == null || loadingPerms}
          />
        </div>

        {/* Permission table */}
        {selectedRoleId == null ? (
          <div
            style={{
              textAlign: 'center',
              padding: 40,
              color: '#9ca3af',
              fontSize: '0.95rem',
            }}
          >
            <i className="pi pi-shield" style={{ fontSize: '2rem', display: 'block', marginBottom: 8 }} />
            Pilih role terlebih dahulu untuk mengelola hak akses
          </div>
        ) : loadingPerms ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}>
            <ProgressSpinner style={{ width: 40, height: 40 }} />
          </div>
        ) : (
          <div
            style={{
              border: '1px solid #e5e7eb',
              borderRadius: 10,
              overflow: 'hidden',
            }}
          >
            {/* Table header */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr repeat(7, 80px)',
                background: '#f9fafb',
                fontWeight: 700,
                fontSize: '0.85rem',
                color: '#374151',
                borderBottom: '2px solid #e5e7eb',
              }}
            >
              <div style={{ padding: '12px 16px' }}>Modul / Submodul</div>
              {ACTIONS.map((a) => (
                <div key={a} style={{ padding: '12px 8px', textAlign: 'center' }}>
                  {COL_LABELS[a]}
                </div>
              ))}
              <div style={{ padding: '12px 8px', textAlign: 'center' }}>Semua</div>
            </div>

            {/* Module groups */}
            {moduleGroups.map((group) => (
              <div key={group.module}>
                {/* Module header row */}
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: '1fr repeat(7, 80px)',
                    background: '#eef2ff',
                    borderBottom: '1px solid #e5e7eb',
                    borderTop: '1px solid #e5e7eb',
                  }}
                >
                  <div
                    style={{
                      padding: '10px 16px',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      fontWeight: 700,
                      color: '#4338ca',
                    }}
                  >
                    <i className={group.icon} style={{ fontSize: '1rem' }} />
                    <span>{group.label}</span>
                    <Tag
                      value="Modul"
                      severity="info"
                      style={{ fontSize: '0.65rem', padding: '2px 8px' }}
                    />
                  </div>
                  {ACTIONS.map((a) => (
                    <div
                      key={a}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    >
                      <Checkbox
                        checked={isModuleFieldChecked(group.module, a)}
                        onChange={() => toggleModuleField(group.module, a)}
                      />
                    </div>
                  ))}
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <Checkbox
                      checked={isModuleAllChecked(group.module)}
                      onChange={() => toggleModuleAll(group.module)}
                    />
                  </div>
                </div>

                {/* Submodule / permission rows — one row per submodule */}
                {group.subs.map((sub, subIdx) => {
                  const subIds = new Set(sub.rows.map((r) => r.permissionId));
                  const isSubFieldChecked = (field: ActionKey) =>
                    sub.rows.length > 0 && sub.rows.every((r) => r[field]);
                  const isSubAllChecked = ACTIONS.every((a) => isSubFieldChecked(a));

                  const toggleSubField = (field: ActionKey) => {
                    const setTo = !isSubFieldChecked(field);
                    setPermRows((prev) =>
                      prev.map((r) => (subIds.has(r.permissionId) ? { ...r, [field]: setTo } : r))
                    );
                  };

                  const toggleSubAll = () => {
                    const setTo = !isSubAllChecked;
                    setPermRows((prev) =>
                      prev.map((r) =>
                        subIds.has(r.permissionId)
                          ? { ...r, canRead: setTo, canCreate: setTo, canUpdate: setTo, canDelete: setTo, canApprove: setTo, canExport: setTo }
                          : r
                      )
                    );
                  };

                  return (
                    <div
                      key={sub.subModule ?? `sub-${subIdx}`}
                      style={{
                        display: 'grid',
                        gridTemplateColumns: '1fr repeat(7, 80px)',
                        borderBottom: '1px solid #f3f4f6',
                        background: subIdx % 2 === 0 ? '#fff' : '#fafbfc',
                      }}
                    >
                      <div
                        style={{
                          padding: '9px 16px 9px 44px',
                          fontSize: '0.88rem',
                          color: '#374151',
                        }}
                      >
                        {sub.label}
                      </div>
                      {ACTIONS.map((a) => (
                        <div
                          key={a}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                          }}
                        >
                          <Checkbox
                            checked={isSubFieldChecked(a)}
                            onChange={() => toggleSubField(a)}
                          />
                        </div>
                      ))}
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <Checkbox
                          checked={isSubAllChecked}
                          onChange={toggleSubAll}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
};

export default AccessManagementPage;
