/**
 * Peran & Izin ERP. Kunci peran = KODE role platform (pengguna mendapat role di Pengaturan
 * platform); di sini hanya diatur hak modul & hak khusus ERP untuk role tersebut. "Peran Baru"
 * memilih role platform yang belum dikonfigurasi; menghapus hanya membuang konfigurasi izinnya.
 */
import React, { useMemo, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import { Plus, Trash2, ShieldCheck, Lock, KeyRound } from 'lucide-react';
import api from '../../lib/api';
import { cn } from '../../lib/utils';
import { ConfirmDialog, EmptyState, Field, LoadingState } from '@/components/shared';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Link } from '@/lib/router';
import { SettingsCard } from '../../components/settings/SettingsCard';
import { SaveBar } from '../../components/settings/SaveBar';
import { PermissionMatrix } from '../../components/settings/PermissionMatrix';
import {
  draftFromRole, groupModules, isCapsDirty, isPermDraftDirty, toPermissionsPayload, type PermDraft,
} from '../../components/settings/permissionMatrixState';

interface ModuleDef { key: string; label: string; group: string }
interface CapabilityDef { key: string; label: string }
interface Catalog { modules: ModuleDef[]; capabilities: CapabilityDef[] }

interface RolePerm { module: string; canView: boolean; canEdit: boolean }
interface Role {
  id: string;
  key: string;
  name: string;
  description: string | null;
  isSystem: boolean;
  isSuperAdmin: boolean;
  isActive: boolean;
  userCount: number;
  permissions: RolePerm[];
  caps: string[];
}

interface PlatformRole {
  code: string;
  name: string;
  configured: boolean;
  erpRoleId: string | null;
}

const MIN_NAME = 2;
const PLATFORM_ROLES_PATH = '/dashboard/settings/roles';

export const RolePermissionsTab: React.FC = () => {
  const { t } = useTranslation('settings');
  const qc = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [draftPerms, setDraftPerms] = useState<PermDraft>({});
  const [draftCaps, setDraftCaps] = useState<Set<string>>(new Set());
  const [draftName, setDraftName] = useState('');
  const [draftDesc, setDraftDesc] = useState('');
  const [creating, setCreating] = useState(false);
  const [newKey, setNewKey] = useState('');
  const [newKeyTouched, setNewKeyTouched] = useState(false);
  const [confirmSave, setConfirmSave] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const { data: catalog } = useQuery<Catalog>({
    queryKey: ['roles-catalog'],
    queryFn: async () => (await api.get('/roles/catalog')).data,
  });
  const { data: roles = [], isLoading } = useQuery<Role[]>({
    queryKey: ['roles'],
    queryFn: async () => (await api.get('/roles')).data,
  });
  const platformRolesQuery = useQuery<PlatformRole[]>({
    queryKey: ['roles-platform'],
    queryFn: async () => (await api.get('/roles/platform')).data,
    enabled: creating,
  });
  const unconfigured = (platformRolesQuery.data ?? []).filter((r) => !r.configured);

  const selected = roles.find((r) => r.id === selectedId) || null;

  const groups = useMemo(
    () => groupModules(catalog?.modules ?? [], selected?.permissions.map((p) => p.module) ?? []),
    [catalog, selected],
  );

  const loadDraft = (r: Role) => {
    setSelectedId(r.id);
    setDraftPerms(draftFromRole(r.permissions));
    setDraftCaps(new Set(r.caps));
    setDraftName(r.name);
    setDraftDesc(r.description ?? '');
  };

  const dirty = !!selected && (
    draftName !== selected.name ||
    draftDesc !== (selected.description ?? '') ||
    isPermDraftDirty(selected.permissions, draftPerms) ||
    isCapsDirty(selected.caps, draftCaps)
  );
  const nameError = selected && draftName.trim().length < MIN_NAME ? t('roles.errors.nameMin', { count: MIN_NAME }) : undefined;
  const newKeyError = newKeyTouched && !newKey ? t('roles.platform.required') : undefined;
  const invalidateRoles = () => Promise.all([
    qc.invalidateQueries({ queryKey: ['roles'] }),
    qc.invalidateQueries({ queryKey: ['roles-platform'] }),
    qc.invalidateQueries({ queryKey: ['roles-assignable'] }),
  ]);

  const saveMut = useMutation({
    mutationFn: async () => {
      if (!selected) return;
      return api.put(`/roles/${selected.id}`, {
        name: draftName,
        description: draftDesc,
        permissions: toPermissionsPayload(draftPerms),
        caps: [...draftCaps],
      });
    },
    onSuccess: () => { toast.success(t('roles.toast.saved')); void invalidateRoles(); },
    onError: (e: any) => toast.error(e?.response?.data?.error || t('roles.toast.saveError')),
  });

  const createMut = useMutation({
    mutationFn: async () => {
      const platformRole = unconfigured.find((r) => r.code === newKey);
      return api.post('/roles', { key: newKey, name: platformRole?.name || newKey });
    },
    onSuccess: (res: any) => {
      toast.success(t('roles.toast.created'));
      setCreating(false); setNewKey(''); setNewKeyTouched(false);
      invalidateRoles().then(() => {
        const id: string | undefined = res?.data?.id;
        if (!id) return;
        const fresh = qc.getQueryData<Role[]>(['roles'])?.find((r) => r.id === id);
        if (fresh) loadDraft(fresh);
        else setSelectedId(id);
      });
    },
    onError: (e: any) => toast.error(e?.response?.data?.error || t('roles.toast.createError')),
  });

  const deleteMut = useMutation({
    mutationFn: async (id: string) => api.delete(`/roles/${id}`),
    onSuccess: () => { toast.success(t('roles.toast.deleted')); setSelectedId(null); void invalidateRoles(); },
    onError: (e: any) => toast.error(e?.response?.data?.error || t('roles.toast.deleteError')),
  });

  const toggleCap = (key: string, on: boolean) =>
    setDraftCaps((prev) => {
      const next = new Set(prev);
      if (on) next.add(key); else next.delete(key);
      return next;
    });

  const submitCreate = () => {
    setNewKeyTouched(true);
    if (!newKey) return;
    createMut.mutate();
  };
  const cancelCreate = () => { setCreating(false); setNewKey(''); setNewKeyTouched(false); };

  if (isLoading) return <LoadingState label={t('roles.loading')} />;

  return (
    <div className="grid grid-cols-1 gap-5 xl:grid-cols-[260px_minmax(0,1fr)]">
      <SettingsCard
        title={t('roles.listTitle')}
        className="self-start"
        actions={!creating && (
          <Button size="sm" onClick={() => setCreating(true)}>
            <Plus aria-hidden /> {t('roles.new')}
          </Button>
        )}
        contentClassName="space-y-3"
      >
        {creating && (
          <form
            noValidate
            className="space-y-2 rounded-lg border border-border p-3"
            onSubmit={(e) => { e.preventDefault(); submitCreate(); }}
          >
            <Field label={t('roles.platform.label')} required error={newKeyError} hint={t('roles.platform.hint')}>
              {(control) => (
                <Select value={newKey} onValueChange={(v) => { setNewKey(v); setNewKeyTouched(true); }}>
                  <SelectTrigger {...control}>
                    <SelectValue placeholder={platformRolesQuery.isLoading ? t('roles.platform.loading') : t('roles.platform.placeholder')} />
                  </SelectTrigger>
                  <SelectContent>
                    {unconfigured.map((r) => (
                      <SelectItem key={r.code} value={r.code}>
                        {r.name} <span className="font-mono text-xs text-muted-foreground">({r.code})</span>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </Field>
            {!platformRolesQuery.isLoading && unconfigured.length === 0 && (
              <p className="text-xs text-muted-foreground">
                {t('roles.platform.allConfigured')}{' '}
                <Link to={PLATFORM_ROLES_PATH} className="font-medium text-primary underline-offset-4 hover:underline">
                  {t('roles.platform.manageLink')}
                </Link>
              </p>
            )}
            <div className="flex justify-end gap-2">
              <Button variant="outline" size="sm" onClick={cancelCreate}>{t('roles.cancel')}</Button>
              <Button type="submit" size="sm" loading={createMut.isPending} disabled={unconfigured.length === 0}>{t('roles.create')}</Button>
            </div>
          </form>
        )}
        <ul className="space-y-1" aria-label={t('roles.listLabel')}>
          {roles.map((r) => {
            const isActive = selectedId === r.id;
            return (
              <li key={r.id}>
                {/* eslint-disable-next-line jsx-a11y/control-has-associated-label -- accessible name comes from the button's text children */}
                <button
                  type="button"
                  onClick={() => loadDraft(r)}
                  aria-current={isActive ? 'true' : undefined}
                  className={cn(
                    'flex w-full items-center justify-between gap-2 rounded-md border px-3 py-2 text-left text-sm transition-colors',
                    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                    isActive ? 'border-primary bg-primary-subtle text-primary-subtle-foreground' : 'border-border hover:bg-accent',
                  )}
                >
                  <span className="min-w-0">
                    <span className="flex min-w-0 items-center gap-1.5">
                      {r.isSuperAdmin && <ShieldCheck className="size-3.5 shrink-0 text-primary" aria-label={t('roles.superAdmin')} />}
                      <span className="truncate font-medium">{r.name}</span>
                    </span>
                    <span className="block truncate font-mono text-[11px] text-muted-foreground">{r.key}</span>
                    <span className="block text-xs text-muted-foreground">
                      {r.isSystem ? t('roles.system') : t('roles.custom')} · {t('roles.userCount', { count: r.userCount })}
                    </span>
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      </SettingsCard>

      <div className="min-w-0 space-y-5">
        {!selected ? (
          <SettingsCard title={t('roles.permissions')}>
            <EmptyState compact icon={KeyRound} title={t('roles.pick.title')} description={t('roles.pick.description')} />
          </SettingsCard>
        ) : selected.isSuperAdmin ? (
          <SettingsCard title={selected.name}>
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <Lock className="size-4 shrink-0" aria-hidden />
              {t('roles.superAdminNote')}
            </p>
          </SettingsCard>
        ) : (
          <>
            <SettingsCard
              title={t('roles.detail')}
              actions={selected.isSystem
                ? <Badge variant="warning">{t('roles.builtIn')}</Badge>
                : (
                  <Button
                    variant="outline"
                    size="sm"
                    className="text-danger-text"
                    onClick={() => setConfirmDelete(true)}
                    disabled={deleteMut.isPending}
                  >
                    <Trash2 aria-hidden /> {t('roles.delete')}
                  </Button>
                )}
            >
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <Field label={t('roles.name')} required error={nameError}>
                  <Input value={draftName} onChange={(e) => setDraftName(e.target.value)} />
                </Field>
                <Field label={t('roles.description')}>
                  <Input value={draftDesc} onChange={(e) => setDraftDesc(e.target.value)} />
                </Field>
              </div>
              {selected.isSystem && (
                <p className="mt-3 text-xs text-muted-foreground">{t('roles.builtInNote')}</p>
              )}
              <p className="mt-3 text-xs text-muted-foreground">
                {t('roles.platform.keyNote')} <span className="font-mono">{selected.key}</span>
              </p>
            </SettingsCard>

            <SettingsCard
              title={t('roles.modules.title')}
              description={t('roles.modules.description')}
            >
              <PermissionMatrix groups={groups} draft={draftPerms} onChange={setDraftPerms} caption={selected.name} />
            </SettingsCard>

            <SettingsCard title={t('roles.caps.title')} description={t('roles.caps.description')}>
              {(catalog?.capabilities ?? []).length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('roles.caps.empty')}</p>
              ) : (
                <ul className="grid grid-cols-1 gap-2 md:grid-cols-2">
                  {(catalog?.capabilities ?? []).map((c) => (
                    <li key={c.key}>
                      <label className="flex items-start gap-2.5 rounded-md p-1.5 text-sm text-foreground hover:bg-accent">
                        <Checkbox className="mt-0.5" checked={draftCaps.has(c.key)} onCheckedChange={(v) => toggleCap(c.key, v === true)} />
                        <span>
                          {c.label}
                          <span className="block font-mono text-xs text-muted-foreground">{c.key}</span>
                        </span>
                      </label>
                    </li>
                  ))}
                </ul>
              )}
            </SettingsCard>

            <SaveBar
              dirty={dirty}
              saveDisabled={!!nameError}
              saving={saveMut.isPending}
              onSave={() => setConfirmSave(true)}
              onReset={() => loadDraft(selected)}
              saveLabel={t('roles.saveChanges')}
            />

            <ConfirmDialog
              open={confirmSave}
              onOpenChange={setConfirmSave}
              title={t('roles.saveConfirm.title', { name: draftName })}
              description={selected.userCount > 0
                ? t('roles.saveConfirm.descriptionUsers', { count: selected.userCount })
                : t('roles.saveConfirm.descriptionNone')}
              confirmLabel={t('roles.saveConfirm.confirm')}
              loading={saveMut.isPending}
              onConfirm={() => saveMut.mutateAsync()}
            />

            <ConfirmDialog
              open={confirmDelete}
              onOpenChange={setConfirmDelete}
              tone="danger"
              title={t('roles.deleteConfirm.title', { name: selected.name })}
              description={selected.userCount > 0
                ? t('roles.deleteConfirm.descriptionUsers', { count: selected.userCount })
                : t('roles.deleteConfirm.description')}
              confirmLabel={t('roles.delete')}
              loading={deleteMut.isPending}
              onConfirm={() => deleteMut.mutateAsync(selected.id)}
            />
          </>
        )}
      </div>
    </div>
  );
};
