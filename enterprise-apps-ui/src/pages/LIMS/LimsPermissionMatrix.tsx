import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { BreadCrumb } from 'primereact/breadcrumb';
import { Checkbox } from 'primereact/checkbox';
import { Toast } from 'primereact/toast';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Tag } from 'primereact/tag';
import { Tooltip } from 'primereact/tooltip';
import {
  roleService,
  permissionService,
  rolePermissionService,
} from '../../services/rbacService';
import type { Role, Permission, RolePermission } from '../../types';

/* ==========================================================================
   TYPES
   ========================================================================== */

/** Access flags per role-permission intersection */
interface AccessFlags {
  canCreate: boolean;
  canRead: boolean;
  canUpdate: boolean;
  canDelete: boolean;
  canApprove: boolean;
  canExport: boolean;
}

const EMPTY_FLAGS: AccessFlags = {
  canCreate: false,
  canRead: false,
  canUpdate: false,
  canDelete: false,
  canApprove: false,
  canExport: false,
};

const ACCESS_KEYS: { key: keyof AccessFlags; label: string; abbr: string; icon: string }[] = [
  { key: 'canCreate', label: 'Create', abbr: 'C', icon: 'pi pi-plus' },
  { key: 'canRead', label: 'Read', abbr: 'R', icon: 'pi pi-eye' },
  { key: 'canUpdate', label: 'Update', abbr: 'U', icon: 'pi pi-pencil' },
  { key: 'canDelete', label: 'Delete', abbr: 'D', icon: 'pi pi-trash' },
  { key: 'canApprove', label: 'Approve', abbr: 'A', icon: 'pi pi-check-circle' },
  { key: 'canExport', label: 'Export', abbr: 'E', icon: 'pi pi-download' },
];

/** key = `${roleId}:${permissionId}` */
type MatrixData = Map<string, AccessFlags>;

/* ==========================================================================
   SUB-MODULE LABELS & GROUPS
   ========================================================================== */
const LIMS_SUB_MODULE_LABELS: Record<string, string> = {
  DASHBOARD: 'Dashboard',
  REPORT: 'Laporan Laboratorium',
  ANALYSIS: 'Analisis',
  APPROVAL: 'Approval',
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
};

const SUB_MODULE_ORDER = [
  'DASHBOARD',
  'REPORT',
  'ANALYSIS',
  'APPROVAL',
  'DAILY',
  'CALENDAR',
  'PARAMETER',
  'TEST_TYPE',
  'METHOD',
  'STANDARD',
  'UNIT',
  'RESULT',
  'LAB_DEPT',
  'COMPOUND',
];

/** Group labels for visual sections */
const FEATURE_GROUPS: { label: string; subModules: string[] }[] = [
  { label: 'Dashboard', subModules: ['DASHBOARD'] },
  { label: 'Laporan & Analisis', subModules: ['REPORT', 'ANALYSIS', 'APPROVAL'] },
  { label: 'Laporan Harian', subModules: ['DAILY', 'CALENDAR'] },
  { label: 'Master Data', subModules: ['PARAMETER', 'TEST_TYPE', 'METHOD', 'STANDARD', 'UNIT'] },
  { label: 'Kualitas & Hasil', subModules: ['RESULT', 'LAB_DEPT', 'COMPOUND'] },
];

/* ==========================================================================
   HELPERS
   ========================================================================== */
function matrixKey(roleId: number, permId: number): string {
  return `${roleId}:${permId}`;
}

function sortPermissions(perms: Permission[]): Permission[] {
  const orderMap = new Map(SUB_MODULE_ORDER.map((s, i) => [s, i]));
  return [...perms].sort(
    (a, b) => (orderMap.get(a.subModule ?? '') ?? 999) - (orderMap.get(b.subModule ?? '') ?? 999)
  );
}

/* ==========================================================================
   COMPONENT
   ========================================================================== */
const LimsPermissionMatrix = () => {
  const navigate = useNavigate();
  const toast = useRef<Toast>(null);

  const [roles, setRoles] = useState<Role[]>([]);
  const [limsPermissions, setLimsPermissions] = useState<Permission[]>([]);
  const [matrix, setMatrix] = useState<MatrixData>(new Map());
  const [originalMatrix, setOriginalMatrix] = useState<MatrixData>(new Map());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);

  /* ---------- Active access key filter ---------- */
  const [activeAccessKey, setActiveAccessKey] = useState<keyof AccessFlags | 'all'>('all');

  /* ---------- Load data ---------- */
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [roleList, permList] = await Promise.all([
        roleService.list(),
        permissionService.byModule('LIMS'),
      ]);

      setRoles(roleList);
      const sorted = sortPermissions(permList);
      setLimsPermissions(sorted);

      // Load existing role_permissions for every role, in parallel
      const rpResults = await Promise.all(
        roleList.map((r) => rolePermissionService.list(r.id).then((items) => ({ roleId: r.id, items })))
      );

      const mat: MatrixData = new Map();
      for (const { roleId, items } of rpResults) {
        for (const rp of items) {
          // Only include LIMS permissions
          if (sorted.find((p) => p.id === rp.permissionId)) {
            mat.set(matrixKey(roleId, rp.permissionId), {
              canCreate: rp.canCreate ?? false,
              canRead: rp.canRead ?? false,
              canUpdate: rp.canUpdate ?? false,
              canDelete: rp.canDelete ?? false,
              canApprove: rp.canApprove ?? false,
              canExport: rp.canExport ?? false,
            });
          }
        }
      }

      setMatrix(mat);
      setOriginalMatrix(new Map(mat));
      setDirty(false);
    } catch {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Gagal memuat data permission matrix',
      });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  /* ---------- Mutation helpers ---------- */
  const getFlags = (roleId: number, permId: number): AccessFlags => {
    return matrix.get(matrixKey(roleId, permId)) ?? { ...EMPTY_FLAGS };
  };

  const setFlag = (roleId: number, permId: number, key: keyof AccessFlags, value: boolean) => {
    setMatrix((prev) => {
      const next = new Map(prev);
      const flags = next.get(matrixKey(roleId, permId)) ?? { ...EMPTY_FLAGS };
      flags[key] = value;
      next.set(matrixKey(roleId, permId), flags);
      return next;
    });
    setDirty(true);
  };

  const toggleAll = (roleId: number, permId: number) => {
    const flags = getFlags(roleId, permId);
    const allChecked = ACCESS_KEYS.every((a) => flags[a.key]);
    const value = !allChecked;
    setMatrix((prev) => {
      const next = new Map(prev);
      const newFlags: AccessFlags = {
        canCreate: value,
        canRead: value,
        canUpdate: value,
        canDelete: value,
        canApprove: value,
        canExport: value,
      };
      next.set(matrixKey(roleId, permId), newFlags);
      return next;
    });
    setDirty(true);
  };

  /** Toggle entire column (all permissions for a role) */
  const toggleRoleColumn = (roleId: number, key: keyof AccessFlags) => {
    const allChecked = limsPermissions.every((p) => getFlags(roleId, p.id)[key]);
    const value = !allChecked;
    setMatrix((prev) => {
      const next = new Map(prev);
      for (const perm of limsPermissions) {
        const k = matrixKey(roleId, perm.id);
        const flags = next.get(k) ?? { ...EMPTY_FLAGS };
        flags[key] = value;
        next.set(k, flags);
      }
      return next;
    });
    setDirty(true);
  };

  /** Toggle entire row (all roles for a permission) */
  const togglePermissionRow = (permId: number, key: keyof AccessFlags) => {
    const allChecked = roles.every((r) => getFlags(r.id, permId)[key]);
    const value = !allChecked;
    setMatrix((prev) => {
      const next = new Map(prev);
      for (const role of roles) {
        const k = matrixKey(role.id, permId);
        const flags = next.get(k) ?? { ...EMPTY_FLAGS };
        flags[key] = value;
        next.set(k, flags);
      }
      return next;
    });
    setDirty(true);
  };

  /* ---------- Save ---------- */
  const saveMatrix = async () => {
    setSaving(true);
    try {
      // Build per-role payloads and save in parallel
      const savePromises = roles.map((role) => {
        const permPayload = limsPermissions.map((p) => {
          const flags = getFlags(role.id, p.id);
          return {
            permissionId: p.id,
            canCreate: flags.canCreate,
            canRead: flags.canRead,
            canUpdate: flags.canUpdate,
            canDelete: flags.canDelete,
            canApprove: flags.canApprove,
            canExport: flags.canExport,
          };
        });
        return rolePermissionService.bulkSave(role.id, permPayload);
      });

      await Promise.all(savePromises);

      setOriginalMatrix(new Map(matrix));
      setDirty(false);
      toast.current?.show({
        severity: 'success',
        summary: 'Berhasil',
        detail: 'Permission matrix berhasil disimpan',
      });
    } catch {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Gagal menyimpan permission matrix',
      });
    } finally {
      setSaving(false);
    }
  };

  const resetMatrix = () => {
    setMatrix(new Map(originalMatrix));
    setDirty(false);
  };

  /* ---------- Group permissions for rendering ---------- */
  const groupedPermissions = FEATURE_GROUPS
    .map((group) => ({
      ...group,
      permissions: limsPermissions.filter((p) =>
        group.subModules.includes(p.subModule ?? '')
      ),
    }))
    .filter((g) => g.permissions.length > 0);

  /* ---------- Breadcrumb ---------- */
  const breadcrumbHome = {
    icon: 'pi pi-home',
    command: () => navigate('/dashboard'),
  };
  const breadcrumbItems = [
    { label: 'LIMS', command: () => navigate('/dashboard/lims') },
    { label: 'Permission Matrix' },
  ];

  /* ---------- KPI Cards ---------- */
  const kpiCards = [
    { label: 'Total Role', value: roles.length, icon: 'pi pi-users', color: '#6366f1' },
    { label: 'Fitur LIMS', value: limsPermissions.length, icon: 'pi pi-th-large', color: '#14b8a6' },
    { label: 'Total Hak Akses', value: matrix.size, icon: 'pi pi-lock-open', color: '#f59e0b' },
  ];

  /* ---------- Sub-module badge color ---------- */
  const subModuleSeverity = (sub: string): 'success' | 'info' | 'warning' | 'danger' | undefined => {
    const map: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
      REPORT: 'info',
      ANALYSIS: 'success',
      APPROVAL: 'warning',
      DAILY: 'info',
      DASHBOARD: 'success',
      PARAMETER: 'danger',
      TEST_TYPE: 'danger',
      METHOD: 'danger',
      STANDARD: 'danger',
      UNIT: 'danger',
      RESULT: 'warning',
      LAB_DEPT: 'info',
      COMPOUND: 'success',
      CALENDAR: 'info',
    };
    return map[sub];
  };

  /* ==========================================================================
     RENDER
     ========================================================================== */
  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <ProgressSpinner />
      </div>
    );
  }

  const filteredAccessKeys =
    activeAccessKey === 'all'
      ? ACCESS_KEYS
      : ACCESS_KEYS.filter((a) => a.key === activeAccessKey);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Toast ref={toast} />
      <Tooltip target=".matrix-checkbox" mouseTrack mouseTrackLeft={10} />

      <BreadCrumb model={breadcrumbItems} home={breadcrumbHome} />

      {/* Header */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <div>
          <h2 style={{ margin: 0, fontSize: '1.5rem', fontWeight: 700 }}>
            <i className="pi pi-shield" style={{ marginRight: 8, color: '#6366f1' }} />
            LIMS Permission Matrix
          </h2>
          <p style={{ margin: '4px 0 0', color: '#6b7280', fontSize: '0.9rem' }}>
            Kelola hak akses setiap role terhadap fitur LIMS
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button
            label="Reset"
            icon="pi pi-refresh"
            severity="secondary"
            outlined
            disabled={!dirty}
            onClick={resetMatrix}
          />
          <Button
            label="Simpan Perubahan"
            icon="pi pi-save"
            loading={saving}
            disabled={!dirty}
            onClick={saveMatrix}
          />
        </div>
      </div>

      {/* KPI Cards */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16 }}>
        {kpiCards.map((kpi) => (
          <Card
            key={kpi.label}
            style={{ flex: '1 1 200px', borderLeft: `4px solid ${kpi.color}` }}
          >
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

      {/* Access-key quick filter */}
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <span style={{ fontWeight: 600, fontSize: '0.85rem', color: '#374151' }}>
            Filter akses:
          </span>
          <Button
            label="Semua"
            size="small"
            rounded
            severity={activeAccessKey === 'all' ? undefined : 'secondary'}
            outlined={activeAccessKey !== 'all'}
            onClick={() => setActiveAccessKey('all')}
          />
          {ACCESS_KEYS.map((a) => (
            <Button
              key={a.key}
              label={a.label}
              icon={a.icon}
              size="small"
              rounded
              severity={activeAccessKey === a.key ? undefined : 'secondary'}
              outlined={activeAccessKey !== a.key}
              onClick={() => setActiveAccessKey(a.key)}
            />
          ))}
        </div>
      </Card>

      {/* Matrix Grid */}
      <Card style={{ overflow: 'auto' }}>
        <div style={{ overflowX: 'auto' }}>
          <table
            style={{
              width: '100%',
              borderCollapse: 'collapse',
              fontSize: '0.82rem',
              minWidth: roles.length * filteredAccessKeys.length * 40 + 280,
            }}
          >
            <thead>
              {/* Role names row */}
              <tr style={{ background: '#f8fafc' }}>
                <th
                  rowSpan={2}
                  style={{
                    position: 'sticky',
                    left: 0,
                    zIndex: 3,
                    background: '#f8fafc',
                    padding: '10px 14px',
                    textAlign: 'left',
                    borderBottom: '2px solid #e2e8f0',
                    borderRight: '2px solid #e2e8f0',
                    minWidth: 260,
                    verticalAlign: 'bottom',
                  }}
                >
                  Fitur / Menu
                </th>
                {roles.map((role) => (
                  <th
                    key={role.id}
                    colSpan={filteredAccessKeys.length}
                    style={{
                      padding: '8px 4px',
                      textAlign: 'center',
                      borderBottom: '1px solid #e2e8f0',
                      borderLeft: '2px solid #e2e8f0',
                      fontWeight: 700,
                      color: '#4338ca',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
                      <i className="pi pi-shield" style={{ fontSize: '0.9rem' }} />
                      <span>{role.name}</span>
                      {role.isSystemRole && (
                        <Tag value="System" severity="info" style={{ fontSize: '0.65rem' }} />
                      )}
                    </div>
                  </th>
                ))}
              </tr>

              {/* CRUD header row */}
              <tr style={{ background: '#f1f5f9' }}>
                {roles.map((role) =>
                  filteredAccessKeys.map((a) => (
                    <th
                      key={`${role.id}-${a.key}`}
                      style={{
                        padding: '6px 2px',
                        textAlign: 'center',
                        borderBottom: '2px solid #e2e8f0',
                        borderLeft: a.key === filteredAccessKeys[0].key ? '2px solid #e2e8f0' : '1px solid #f1f5f9',
                        fontSize: '0.7rem',
                        fontWeight: 600,
                        color: '#64748b',
                        cursor: 'pointer',
                        minWidth: 36,
                      }}
                      title={`Toggle semua ${a.label} untuk ${role.name}`}
                      onClick={() => toggleRoleColumn(role.id, a.key)}
                    >
                      {a.abbr}
                    </th>
                  ))
                )}
              </tr>
            </thead>

            <tbody>
              {groupedPermissions.map((group) => (
                <>
                  {/* Group header */}
                  <tr key={`group-${group.label}`}>
                    <td
                      colSpan={1 + roles.length * filteredAccessKeys.length}
                      style={{
                        background: '#eef2ff',
                        padding: '8px 14px',
                        fontWeight: 700,
                        color: '#4338ca',
                        fontSize: '0.9rem',
                        borderTop: '2px solid #c7d2fe',
                        borderBottom: '1px solid #c7d2fe',
                        position: 'sticky',
                        left: 0,
                      }}
                    >
                      <i className="pi pi-folder" style={{ marginRight: 8 }} />
                      {group.label}
                    </td>
                  </tr>

                  {/* Permission rows */}
                  {group.permissions.map((perm, idx) => (
                    <tr
                      key={perm.id}
                      style={{
                        background: idx % 2 === 0 ? '#fff' : '#fafbfc',
                        transition: 'background 0.15s',
                      }}
                      onMouseEnter={(e) => (e.currentTarget.style.background = '#f0f4ff')}
                      onMouseLeave={(e) =>
                        (e.currentTarget.style.background = idx % 2 === 0 ? '#fff' : '#fafbfc')
                      }
                    >
                      {/* Feature name cell */}
                      <td
                        style={{
                          position: 'sticky',
                          left: 0,
                          background: 'inherit',
                          zIndex: 1,
                          padding: '7px 14px',
                          borderRight: '2px solid #e2e8f0',
                          borderBottom: '1px solid #f1f5f9',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <Tag
                            value={LIMS_SUB_MODULE_LABELS[perm.subModule ?? ''] ?? perm.subModule}
                            severity={subModuleSeverity(perm.subModule ?? '')}
                            style={{ fontSize: '0.68rem', minWidth: 70, textAlign: 'center' }}
                          />
                          <span style={{ fontWeight: 500 }}>{perm.name}</span>
                        </div>
                      </td>

                      {/* Checkboxes per role x access key */}
                      {roles.map((role) => {
                        const flags = getFlags(role.id, perm.id);

                        return filteredAccessKeys.map((a) => (
                          <td
                            key={`${role.id}-${perm.id}-${a.key}`}
                            style={{
                              textAlign: 'center',
                              padding: '4px 2px',
                              borderLeft:
                                a.key === filteredAccessKeys[0].key
                                  ? '2px solid #e2e8f0'
                                  : '1px solid #f8fafc',
                              borderBottom: '1px solid #f1f5f9',
                            }}
                          >
                            <Checkbox
                              className="matrix-checkbox"
                              checked={flags[a.key]}
                              onChange={() =>
                                setFlag(role.id, perm.id, a.key, !flags[a.key])
                              }
                              data-pr-tooltip={`${role.name} → ${perm.name}: ${a.label}`}
                            />
                          </td>
                        ));
                      })}
                    </tr>
                  ))}
                </>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Dirty indicator + save bar */}
      {dirty && (
        <div
          style={{
            position: 'sticky',
            bottom: 0,
            zIndex: 10,
            background: '#fffbeb',
            border: '1px solid #fbbf24',
            borderRadius: 8,
            padding: '12px 20px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
          }}
        >
          <span style={{ color: '#92400e', fontWeight: 600 }}>
            <i className="pi pi-exclamation-triangle" style={{ marginRight: 8 }} />
            Anda memiliki perubahan yang belum disimpan
          </span>
          <div style={{ display: 'flex', gap: 8 }}>
            <Button
              label="Batal"
              icon="pi pi-times"
              severity="secondary"
              outlined
              size="small"
              onClick={resetMatrix}
            />
            <Button
              label="Simpan"
              icon="pi pi-check"
              size="small"
              loading={saving}
              onClick={saveMatrix}
            />
          </div>
        </div>
      )}

      {/* Legend */}
      <Card>
        <div style={{ fontSize: '0.82rem', color: '#6b7280' }}>
          <strong>Keterangan:</strong>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, marginTop: 8 }}>
            {ACCESS_KEYS.map((a) => (
              <span key={a.key} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <i className={a.icon} style={{ color: '#6366f1', fontSize: '0.85rem' }} />
                <strong>{a.abbr}</strong> = {a.label}
              </span>
            ))}
          </div>
          <div style={{ marginTop: 8 }}>
            Klik header kolom <strong>C/R/U/D/A/E</strong> untuk toggle seluruh kolom.
            Perubahan baru tersimpan setelah menekan tombol <strong>Simpan</strong>.
          </div>
        </div>
      </Card>
    </div>
  );
};

export default LimsPermissionMatrix;
