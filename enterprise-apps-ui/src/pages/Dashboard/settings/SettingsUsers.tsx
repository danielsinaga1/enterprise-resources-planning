import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { userRoleService, roleService, masterJobTitleService, masterOriginDeptService, masterDivisionService } from '../../../services';
import ExportButtons from '../../../components/common/ExportButtons';
import type { ExportColumn } from '../../../utils/exportUtils';
import type { User } from '../../../types';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { Dialog } from 'primereact/dialog';
import { Tag } from 'primereact/tag';
import { Card } from 'primereact/card';
import { BreadCrumb } from 'primereact/breadcrumb';
import { Message } from 'primereact/message';
import { Dropdown } from 'primereact/dropdown';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { Toast } from 'primereact/toast';
import { useRef } from 'react';

const roleSeverity = (role: string): 'danger' | 'info' | 'success' | 'warning' | 'secondary' | undefined => {
  const map: Record<string, 'danger' | 'info' | 'success' | 'warning' | 'secondary'> = {
    SUPER_ADMIN: 'danger',
    ADMIN: 'warning',
    DOKTER: 'info',
    PERAWAT: 'success',
    FARMASI: 'secondary',
  };
  return map[role] || 'info';
};

interface RoleOption {
  id: number;
  name: string;
}

const SettingsUsers = () => {
  const { t } = useTranslation();
  const toast = useRef<Toast>(null);
  const [items, setItems] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [error, setError] = useState('');

  // Pagination state
  const [page, setPage] = useState(1);
  const [totalRecords, setTotalRecords] = useState(0);
  const [rows, setRows] = useState(10);

  const [availableRoles, setAvailableRoles] = useState<RoleOption[]>([]);
  const [showRoleDialog, setShowRoleDialog] = useState(false);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [userRoles, setUserRoles] = useState<Array<{ id: number; roleId: number; roleName: string }>>([]);
  const [rolesLoading, setRolesLoading] = useState(false);

  // Filter state
  const [filterDeptId, setFilterDeptId] = useState<number | null>(null);
  const [filterStatus, setFilterStatus] = useState<number | null>(null);

  // Users dropdown for atasan selection
  const [usersDropdown, setUsersDropdown] = useState<{ id: number; name: string; npk?: string }[]>([]);

  // Job titles dropdown
  const [jobTitles, setJobTitles] = useState<{ id: number; name: string }[]>([]);

  // Departments dropdown
  const [departments, setDepartments] = useState<{ id: number; name: string }[]>([]);

  // Divisions dropdown
  const [divisions, setDivisions] = useState<{ id: number; name: string }[]>([]);

  // Hierarchy dialog
  const [showHierarchyDialog, setShowHierarchyDialog] = useState(false);
  const [hierarchyUser, setHierarchyUser] = useState<User | null>(null);

  // Create/Edit User
  const [showUserDialog, setShowUserDialog] = useState(false);
  const [editMode, setEditMode] = useState(false);
  const [editUserId, setEditUserId] = useState<number | null>(null);
  const [userForm, setUserForm] = useState({
    name: '',
    email: '',
    npk: '',
    password: '',
    roleId: null as number | null,
    parentId: null as number | null,
    jobId: null as number | null,
    deptId: null as number | null,
    divisionId: null as number | null,
  });
  const [saving, setSaving] = useState(false);

  const breadcrumbItems = [{ label: t('nav.settings') }, { label: t('users.list') }];
  const breadcrumbHome = { icon: 'pi pi-home', url: '/dashboard' };

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const resp = await userRoleService.listUsers({
        page,
        limit: rows,
        search: search.trim() || undefined,
        deptId: filterDeptId ?? undefined,
        status: filterStatus ?? undefined,
      });
      setItems(resp.data);
      setTotalRecords(resp.countData ?? 0);
    } catch {
      setError(t('users.loadError'));
    } finally {
      setLoading(false);
    }
  }, [page, rows, search, filterDeptId, filterStatus]);

  useEffect(() => { fetchData(); }, [fetchData]);

  useEffect(() => {
    roleService.list().then(setAvailableRoles).catch(() => {});
    userRoleService.getUsersDropdown().then(setUsersDropdown).catch(() => {});
    masterJobTitleService.list().then(setJobTitles).catch(() => {});
    masterOriginDeptService.list().then(setDepartments).catch(() => {});
    masterDivisionService.list().then(setDivisions).catch(() => {});
  }, []);

  // Debounced search: reset page to 1 when search or filters change
  useEffect(() => {
    setPage(1);
  }, [search, filterDeptId, filterStatus]);

  const openRoleDialog = async (user: User) => {
    setSelectedUser(user);
    setShowRoleDialog(true);
    setRolesLoading(true);
    try {
      const roles = await userRoleService.userRoles(user.id);
      setUserRoles(roles.map((r) => ({
        id: r.userId,
        roleId: r.roleId,
        roleName: r.roleName ?? '',
      })));
    } catch {
      setUserRoles([]);
    } finally {
      setRolesLoading(false);
    }
  };

  const assignRole = async (roleId: number) => {
    if (!selectedUser) return;
    try {
      await userRoleService.assign(selectedUser.id, roleId);
      const roles = await userRoleService.userRoles(selectedUser.id);
      setUserRoles(roles.map((r) => ({
        id: r.userId,
        roleId: r.roleId,
        roleName: r.roleName ?? '',
      })));
      fetchData();
    } catch {
      alert(t('users.assignRoleError'));
    }
  };

  const removeRole = async (roleId: number) => {
    if (!selectedUser) return;
    try {
      await userRoleService.remove(selectedUser.id, roleId);
      const roles = await userRoleService.userRoles(selectedUser.id);
      setUserRoles(roles.map((r) => ({
        id: r.userId,
        roleId: r.roleId,
        roleName: r.roleName ?? '',
      })));
      fetchData();
    } catch {
      alert(t('users.removeRoleError'));
    }
  };

  // ===== Create / Edit User =====
  const openCreateDialog = () => {
    setEditMode(false);
    setEditUserId(null);
    setUserForm({ name: '', email: '', npk: '', password: '', roleId: null, parentId: null, jobId: null, deptId: null, divisionId: null });
    setShowUserDialog(true);
  };

  const openEditDialog = (user: User) => {
    setEditMode(true);
    setEditUserId(user.id);
    const matchedJob = user.jobTitle
      ? jobTitles.find(j => j.name === user.jobTitle)?.id ?? null
      : null;
    setUserForm({
      name: user.name || '',
      email: user.email || '',
      npk: user.npk || '',
      password: '',
      roleId: null,
      parentId: user.parentId ?? null,
      jobId: matchedJob,
      deptId: user.departmentId ?? null,
      divisionId: user.divisionId ?? null,
    });
    setShowUserDialog(true);
  };

  const saveUser = async () => {
    if (!userForm.name.trim() || !userForm.email.trim()) {
      toast.current?.show({ severity: 'warn', summary: t('common.validation'), detail: t('users.validationNameEmail'), life: 3000 });
      return;
    }
    setSaving(true);
    try {
      if (editMode && editUserId) {
        await userRoleService.updateUser(editUserId, {
          name: userForm.name,
          email: userForm.email,
          npk: userForm.npk || null,
          password: userForm.password || undefined,
          parentId: userForm.parentId,
          jobId: userForm.jobId ?? null,
          deptId: userForm.deptId ?? null,
          divisionId: userForm.divisionId ?? null,
        });
        toast.current?.show({ severity: 'success', summary: t('common.success'), detail: t('users.updateSuccess'), life: 3000 });
      } else {
        await userRoleService.createUser({
          name: userForm.name,
          email: userForm.email,
          npk: userForm.npk || undefined,
          password: userForm.password || undefined,
          roleId: userForm.roleId || undefined,
          parentId: userForm.parentId,
          jobId: userForm.jobId ?? undefined,
          deptId: userForm.deptId ?? undefined,
          divisionId: userForm.divisionId ?? undefined,
        });
        toast.current?.show({ severity: 'success', summary: t('common.success'), detail: t('users.createSuccess'), life: 3000 });
      }
      setShowUserDialog(false);
      fetchData();
    } catch (err: unknown) {
      const errData = (err as { response?: { data?: { message?: string; errorMessage?: string } } })?.response?.data;
      const msg = errData?.errorMessage || errData?.message || t('users.saveError');
      toast.current?.show({ severity: 'error', summary: 'Error', detail: msg, life: 4000 });
    } finally {
      setSaving(false);
    }
  };

  // ===== Delete User =====
  const confirmDeleteUser = (user: User) => {
    confirmDialog({
      message: t('users.deleteConfirm', { name: user.name }),
      header: t('common.confirm'),
      icon: 'pi pi-exclamation-triangle',
      acceptClassName: 'p-button-danger',
      acceptLabel: t('common.delete'),
      rejectLabel: t('common.cancel'),
      accept: async () => {
        try {
          await userRoleService.deleteUser(user.id);
          toast.current?.show({ severity: 'success', summary: t('common.success'), detail: t('users.deleteSuccess'), life: 3000 });
          fetchData();
        } catch {
          toast.current?.show({ severity: 'error', summary: t('common.error'), detail: t('users.deleteError'), life: 3000 });
        }
      },
    });
  };

  // ===== Toggle Status =====
  const toggleStatus = async (user: User) => {
    try {
      await userRoleService.toggleStatus(user.id);
      toast.current?.show({ severity: 'success', summary: t('common.success'), detail: t('users.statusSuccess'), life: 3000 });
      fetchData();
    } catch {
      toast.current?.show({ severity: 'error', summary: t('common.error'), detail: t('users.statusError'), life: 3000 });
    }
  };

  // ===== Reset Password =====
  const confirmResetPassword = (user: User) => {
    confirmDialog({
      message: t('users.resetPasswordConfirm', { name: user.name }),
      header: t('users.resetPasswordHeader'),
      icon: 'pi pi-lock',
      acceptClassName: 'p-button-warning',
      acceptLabel: t('common.reset'),
      rejectLabel: t('common.cancel'),
      accept: async () => {
        try {
          await userRoleService.resetPassword(user.id);
          toast.current?.show({ severity: 'success', summary: t('common.success'), detail: t('users.resetPasswordSuccess'), life: 3000 });
        } catch {
          toast.current?.show({ severity: 'error', summary: t('common.error'), detail: t('users.resetPasswordError'), life: 3000 });
        }
      },
    });
  };

  const rolesBodyTemplate = (row: User) => (
    <div className="flex flex-wrap gap-1">
      {(row.roles ?? []).map((r, i) => (
        <Tag key={i} value={r} severity={roleSeverity(r)} className="text-xs" />
      ))}
      {(!row.roles || row.roles.length === 0) && (
        <span className="text-xs text-500">{t('users.noRole')}</span>
      )}
    </div>
  );

  const atasanBodyTemplate = (row: User) => (
    <div className="flex gap-2 align-items-center">
      {row.parentName ? (
        <span className="text-sm">{row.parentName}</span>
      ) : (
        <span className="text-sm text-400">-</span>
      )}
    </div>
  );

  const subordinatesBodyTemplate = (row: User) => {
    const count = row.subordinates?.length ?? 0;
    return (
      <div className="flex gap-2 align-items-center">
        {count > 0 ? (
          <Button
            label={`${count} ${t('users.subordinate')}`}
            icon="pi pi-users"
            text
            size="small"
            className="p-0"
            onClick={() => { setHierarchyUser(row); setShowHierarchyDialog(true); }}
          />
        ) : (
          <span className="text-sm text-400">-</span>
        )}
      </div>
    );
  };

  const statusBodyTemplate = (row: User) => (
    <Tag
      value={row.status === 1 ? t('common.active') : t('common.inactive')}
      severity={row.status === 1 ? 'success' : 'danger'}
      className="text-xs"
    />
  );

  const actionBodyTemplate = (row: User) => (
    <div className="flex flex-wrap gap-1">
      <Button icon="pi pi-pencil" text rounded severity="warning" size="small" onClick={() => openEditDialog(row)} tooltip={t('users.editTooltip')} tooltipOptions={{ position: 'top' }} />
      <Button icon="pi pi-shield" text rounded severity="info" size="small" onClick={() => openRoleDialog(row)} tooltip={t('users.manageRolesTooltip')} tooltipOptions={{ position: 'top' }} />
      <Button icon="pi pi-sitemap" text rounded severity="help" size="small" onClick={() => { setHierarchyUser(row); setShowHierarchyDialog(true); }} tooltip={t('users.hierarchyTooltip')} tooltipOptions={{ position: 'top' }} />
      <Button icon={row.status === 1 ? 'pi pi-ban' : 'pi pi-check-circle'} text rounded severity={row.status === 1 ? 'secondary' : 'success'} size="small" onClick={() => toggleStatus(row)} tooltip={row.status === 1 ? t('users.deactivate') : t('users.activate')} tooltipOptions={{ position: 'top' }} />
      <Button icon="pi pi-key" text rounded severity="warning" size="small" onClick={() => confirmResetPassword(row)} tooltip={t('users.resetPasswordTooltip')} tooltipOptions={{ position: 'top' }} />
      <Button icon="pi pi-trash" text rounded severity="danger" size="small" onClick={() => confirmDeleteUser(row)} tooltip={t('users.deleteTooltip')} tooltipOptions={{ position: 'top' }} />
    </div>
  );

  // Export configuration
  const exportColumns: ExportColumn[] = [
    { header: t('users.name'), field: 'name' },
    { header: t('users.email'), field: 'email' },
    { header: t('users.department'), field: 'departmentName', formatter: (v) => (v as string) ?? '-' },
    { header: t('users.division', 'Divisi'), field: 'divisionName', formatter: (v) => (v as string) ?? '-' },
    { header: t('users.jobTitle'), field: 'jobTitle', formatter: (v) => (v as string) ?? '-' },
    { header: t('common.status'), field: 'status', formatter: (v) => v === 1 ? t('common.active') : t('common.inactive') },
  ];

  const fetchExportData = async (): Promise<Record<string, unknown>[]> => {
    const resp = await userRoleService.listUsers({ page: 1, limit: 10000, search: search.trim() || undefined, deptId: filterDeptId ?? undefined, status: filterStatus ?? undefined });
    return resp.data as unknown as Record<string, unknown>[];
  };

  return (
    <div className="flex gap-4 flex-column">
      <Toast ref={toast} />
      <ConfirmDialog />
      <div className="flex flex-wrap gap-3 align-items-center justify-content-between">
        <div>
          <h2 className="m-0 text-2xl font-bold text-800">{t('users.title')}</h2>
          <BreadCrumb model={breadcrumbItems} home={breadcrumbHome} className="p-0 mt-2 border-none surface-ground" />
        </div>
        <div className="flex gap-2">
          <ExportButtons
            permissionCode="USER_MANAGE"
            columns={exportColumns}
            fetchData={fetchExportData}
            fileName="Data_Pengguna"
            title={t('users.title')}
            toastRef={toast}
          />
          <Button label={t('users.create')} icon="pi pi-plus" severity="info" outlined className="font-semibold border-round-lg shadow-1" style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem', border: '2px solid #3B82F6' }} onClick={openCreateDialog} />
        </div>
      </div>

      <Card className="shadow-1 border-round-xl">
        <div className="flex flex-wrap gap-3 mb-4 align-items-center justify-content-between">
          <div className="flex flex-wrap gap-2 align-items-center">
            <span className="p-input-icon-left">
              <i className="pi pi-search" />
              <InputText value={search} onChange={(e) => setSearch(e.target.value)} placeholder={t('users.searchPlaceholder')} className="border-round-xl w-20rem" />
            </span>
            <Dropdown
              value={filterDeptId}
              onChange={(e) => setFilterDeptId(e.value)}
              options={departments}
              optionLabel="name"
              optionValue="id"
              placeholder={t('users.filterDept', 'Semua Departemen')}
              className="border-round-xl w-15rem"
              showClear
              filter
              filterBy="name"
            />
            <Dropdown
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.value)}
              options={[
                { label: t('common.active'), value: 1 },
                { label: t('common.inactive'), value: 0 },
              ]}
              optionLabel="label"
              optionValue="value"
              placeholder={t('users.filterStatus', 'Semua Status')}
              className="border-round-xl w-12rem"
              showClear
            />
            {(filterDeptId !== null || filterStatus !== null) && (
              <Button
                icon="pi pi-filter-slash"
                label={t('common.resetFilter', 'Reset Filter')}
                severity="secondary"
                text
                size="small"
                className="border-round-xl"
                onClick={() => { setFilterDeptId(null); setFilterStatus(null); }}
              />
            )}
          </div>
          <span className="text-sm text-500">{t('common.total')}: {totalRecords} user</span>
        </div>

        {error && <Message severity="error" text={error} className="w-full mb-3" />}

        <DataTable
          value={items}
          loading={loading}
          size="small"
          stripedRows
          responsiveLayout="scroll"
          emptyMessage={t('common.noData')}
          className="border-round-lg"
          lazy
          paginator
          rows={rows}
          totalRecords={totalRecords}
          first={(page - 1) * rows}
          onPage={(e) => { setPage(Math.floor(e.first / e.rows) + 1); setRows(e.rows); }}
          rowsPerPageOptions={[10, 25, 50]}
          paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
        >
          <Column field="name" header={t('users.name')} className="font-medium" style={{ minWidth: '150px' }} />
          <Column field="email" header={t('users.email')} style={{ minWidth: '180px' }} />
          <Column field="departmentName" header={t('users.department')} style={{ minWidth: '150px' }} body={(row: User) => row.departmentName || <span className="text-400">-</span>} />
          <Column field="divisionName" header={t('users.division', 'Divisi')} style={{ minWidth: '150px' }} body={(row: User) => row.divisionName || <span className="text-400">-</span>} />
          <Column field="jobTitle" header={t('users.jobTitle')} style={{ minWidth: '150px' }} body={(row: User) => row.jobTitle || <span className="text-400">-</span>} />
          <Column header={t('users.atasan')} body={atasanBodyTemplate} style={{ minWidth: '150px' }} />
          <Column header={t('users.bawahan')} body={subordinatesBodyTemplate} style={{ minWidth: '130px' }} />
          <Column header={t('users.role')} body={rolesBodyTemplate} style={{ minWidth: '180px' }} />
          <Column header={t('common.status')} body={statusBodyTemplate} alignHeader="center" bodyClassName="text-center" style={{ width: '100px' }} />
          <Column header={t('common.actions')} body={actionBodyTemplate} style={{ width: '260px' }} />
        </DataTable>
      </Card>

      {/* Role Management Dialog */}
      <Dialog
        visible={showRoleDialog}
        onHide={() => setShowRoleDialog(false)}
        header={`${t('users.manageRoles')} — ${selectedUser?.name ?? ''}`}
        style={{ width: '500px' }}
        modal
        className="border-round-xl"
        draggable={false}
      >
        <div className="flex gap-4 flex-column">
          {/* Current Roles */}
          <div>
            <h4 className="mb-2 text-sm font-semibold text-800">{t('users.currentRoles')}</h4>
            {rolesLoading ? (
              <i className="pi pi-spin pi-spinner" />
            ) : userRoles.length > 0 ? (
              <div className="flex gap-2 flex-column">
                {userRoles.map(r => (
                  <div key={r.roleId} className="flex p-2 align-items-center justify-content-between surface-ground border-round-lg">
                    <Tag value={r.roleName} severity={roleSeverity(r.roleName)} />
                    <Button icon="pi pi-times" text rounded severity="danger" size="small" onClick={() => removeRole(r.roleId)} tooltip={t('users.removeRoleTooltip')} />
                  </div>
                ))}
              </div>
            ) : (
              <span className="text-sm text-500">{t('users.noRolesAssigned')}</span>
            )}
          </div>

          {/* Add Role */}
          <div>
            <h4 className="mb-2 text-sm font-semibold text-800">{t('users.addRole')}</h4>
            <div className="flex flex-wrap gap-2">
              {availableRoles
                .filter(ar => !userRoles.some(ur => ur.roleId === ar.id))
                .map(ar => (
                  <Button
                    key={ar.id}
                    label={ar.name}
                    icon="pi pi-plus"
                    size="small"
                    severity="secondary"
                    outlined
                    className="border-round-lg"
                    onClick={() => assignRole(ar.id)}
                  />
                ))}
              {availableRoles.filter(ar => !userRoles.some(ur => ur.roleId === ar.id)).length === 0 && (
                <span className="text-sm text-500">{t('users.allRolesAssigned')}</span>
              )}
            </div>
          </div>
        </div>
      </Dialog>

      {/* Create/Edit User Dialog */}
      <Dialog
        visible={showUserDialog}
        onHide={() => setShowUserDialog(false)}
        header={editMode ? t('users.edit') : t('users.create')}
        style={{ width: '550px' }}
        modal
        className="border-round-xl"
        draggable={false}
        footer={
          <div className="flex gap-2 justify-content-end">
            <Button label={t('common.cancel')} icon="pi pi-times" severity="secondary" outlined className="border-round-lg" onClick={() => setShowUserDialog(false)} />
            <Button label={editMode ? t('common.save') : t('users.createButton')} icon="pi pi-check" className="border-round-lg" onClick={saveUser} loading={saving} />
          </div>
        }
      >
        <div className="flex gap-3 pt-2 flex-column">
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-800">{t('users.formName')} <span className="text-red-500">*</span></label>
            <InputText value={userForm.name} onChange={(e) => setUserForm({ ...userForm, name: e.target.value })} placeholder={t('users.formNamePlaceholder')} className="border-round-lg" />
          </div>
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-800">{t('users.email')} <span className="text-red-500">*</span></label>
            <InputText value={userForm.email} onChange={(e) => setUserForm({ ...userForm, email: e.target.value })} placeholder={t('users.formEmailPlaceholder')} className="border-round-lg" type="email" />
          </div>
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-800">{t('users.formNpk')}</label>
            <InputText value={userForm.npk} onChange={(e) => setUserForm({ ...userForm, npk: e.target.value })} placeholder={t('users.formNpkPlaceholder')} className="border-round-lg" />
          </div>
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-800">{editMode ? t('users.passwordNewHint') : t('users.passwordDefaultHint')}</label>
            <InputText value={userForm.password} onChange={(e) => setUserForm({ ...userForm, password: e.target.value })} placeholder={editMode ? t('users.passwordNewPlaceholder') : 'password123'} className="border-round-lg" type="password" />
          </div>
          {!editMode && (
            <div className="flex gap-1 flex-column">
              <label className="text-sm font-semibold text-800">{t('users.initialRole')}</label>
              <Dropdown
                value={userForm.roleId}
                onChange={(e) => setUserForm({ ...userForm, roleId: e.value })}
                options={availableRoles}
                optionLabel="name"
                optionValue="id"
                placeholder={t('users.selectRolePlaceholder')}
                className="w-full border-round-lg"
                showClear
              />
            </div>
          )}
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-800">{t('users.atasan')}</label>
            <Dropdown
              value={userForm.parentId}
              onChange={(e) => setUserForm({ ...userForm, parentId: e.value })}
              options={usersDropdown.filter(u => u.id !== editUserId)}
              optionLabel="name"
              optionValue="id"
              placeholder={t('users.selectAtasanPlaceholder')}
              className="w-full border-round-lg"
              showClear
              filter
              filterBy="name"
              itemTemplate={(option) => (
                <div className="flex gap-2 align-items-center">
                  <span>{option.name}</span>
                  {option.npk && <span className="text-xs text-500">({option.npk})</span>}
                </div>
              )}
            />
          </div>
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-800">{t('users.jobTitle')}</label>
            <Dropdown
              value={userForm.jobId}
              onChange={(e) => setUserForm({ ...userForm, jobId: e.value })}
              options={jobTitles}
              optionLabel="name"
              optionValue="id"
              placeholder={t('users.selectJobTitlePlaceholder', 'Pilih Job Title')}
              className="w-full border-round-lg"
              showClear
              filter
              filterBy="name"
            />
          </div>
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-800">{t('users.department')}</label>
            <Dropdown
              value={userForm.deptId}
              onChange={(e) => setUserForm({ ...userForm, deptId: e.value })}
              options={departments}
              optionLabel="name"
              optionValue="id"
              placeholder={t('users.selectDepartmentPlaceholder', 'Pilih Departemen')}
              className="w-full border-round-lg"
              showClear
              filter
              filterBy="name"
            />
          </div>
          <div className="flex gap-1 flex-column">
            <label className="text-sm font-semibold text-800">{t('users.division', 'Divisi')}</label>
            <Dropdown
              value={userForm.divisionId}
              onChange={(e) => setUserForm({ ...userForm, divisionId: e.value })}
              options={divisions}
              optionLabel="name"
              optionValue="id"
              placeholder={t('users.selectDivisionPlaceholder', 'Pilih Divisi')}
              className="w-full border-round-lg"
              showClear
              filter
              filterBy="name"
            />
          </div>
        </div>
      </Dialog>

      {/* Hierarchy Dialog (Atasan & Bawahan) */}
      <Dialog
        visible={showHierarchyDialog}
        onHide={() => setShowHierarchyDialog(false)}
        header={`${t('users.hierarchy')} — ${hierarchyUser?.name ?? ''}`}
        style={{ width: '550px' }}
        modal
        className="border-round-xl"
        draggable={false}
      >
        {hierarchyUser && (
          <div className="flex gap-4 flex-column">
            {/* Atasan */}
            <div>
              <h4 className="flex gap-2 mb-2 text-sm font-semibold text-800 align-items-center">
                <i className="text-blue-500 pi pi-arrow-up" />
                {t('users.atasan')}
              </h4>
              {hierarchyUser.parentName ? (
                <div className="flex gap-3 p-3 surface-ground border-round-lg align-items-center">
                  <i className="text-xl text-blue-500 pi pi-user" />
                  <div>
                    <div className="font-medium">{hierarchyUser.parentName}</div>
                    <div className="text-xs text-500">ID: {hierarchyUser.parentId}</div>
                  </div>
                </div>
              ) : (
                <span className="text-sm text-500">{t('users.noAtasan')}</span>
              )}
            </div>

            {/* Bawahan */}
            <div>
              <h4 className="flex gap-2 mb-2 text-sm font-semibold text-800 align-items-center">
                <i className="text-green-500 pi pi-arrow-down" />
                {t('users.bawahan')} ({hierarchyUser.subordinates?.length ?? 0})
              </h4>
              {(hierarchyUser.subordinates?.length ?? 0) > 0 ? (
                <div className="flex gap-2 flex-column">
                  {hierarchyUser.subordinates!.map(sub => (
                    <div key={sub.id} className="flex gap-3 p-3 surface-ground border-round-lg align-items-center">
                      <i className="text-green-500 pi pi-user" />
                      <div>
                        <div className="font-medium">{sub.name}</div>
                        {sub.npk && <div className="text-xs text-500">NPK: {sub.npk}</div>}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <span className="text-sm text-500">{t('users.noBawahan')}</span>
              )}
            </div>
          </div>
        )}
      </Dialog>
    </div>
  );
};

export default SettingsUsers;
