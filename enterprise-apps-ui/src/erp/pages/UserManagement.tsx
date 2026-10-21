/**
 * Manajemen Pengguna ERP (/users). Identitas, kata sandi, dan peran pengguna milik platform
 * (Pengaturan → Pengguna/Role); halaman ini hanya mengatur akses ERP: status aktif, atasan
 * langsung (approver Pengajuan Pembayaran), dan bahasa. Pengguna ERP ditambahkan dari akun
 * platform ("Tambah dari Platform") atau otomatis saat pertama kali membuka ERP.
 */
import { useMemo, useState } from 'react';
import { Link } from '@/lib/router';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Pencil, RefreshCw, Search, ToggleLeft, ToggleRight, UserCheck, UserPlus, UserX, Users } from 'lucide-react';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import api from '@/lib/api';
import { formatDateTime, formatNumber } from '@/lib/format';
import { formatRelativeTime } from '@/lib/auditDiff';
import { can, getStoredUser } from '@/lib/permissions';
import { useListState } from '@/hooks/useListState';
import { useTableDensity } from '@/hooks/useTableDensity';
import { useDebounce } from '@/hooks/useDebounce';
import { initials } from '@/components/shell/role-label';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  ComboboxFilter,
  ConfirmDialog,
  DataTable,
  FilterBar,
  PageHeader,
  RowActions,
  StatCard,
  StatusBadge,
  listTableProps,
  type Paginated,
} from '@/components/shared';
import UserFormModal, { type EditableUser } from '@/components/UserFormModal';

interface UserRow extends EditableUser {
  username: string;
  role: string;
  isActive: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  supervisor?: { id: string; fullName: string } | null;
  platformUserId?: number | null;
}

interface UserSummary {
  total: number;
  active: number;
  inactive: number;
}

interface RoleOption {
  key: string;
  name: string;
}

interface PlatformCandidate {
  id: number;
  npk: string | null;
  name: string;
  email: string;
  roles: string[];
  active: boolean;
  provisioned: boolean;
  erpUserId: string | null;
}

interface SyncResult {
  updated: number;
  deactivated: number;
  total: number;
}

const ENDPOINT = '/users';
const FILTER_KEYS = ['role', 'status'] as const;
const PLATFORM_USERS_PATH = '/dashboard/settings/users';
const PLATFORM_ROLES_PATH = '/dashboard/settings/roles';

function apiError(err: unknown, fallback: string): string {
  const e = err as { response?: { data?: { error?: string } } };
  return e.response?.data?.error || fallback;
}

/** Peran platform yang dikonfigurasi di ERP (kode → nama); kode lain tidak memberi akses ERP. */
function ErpRoleBadges({ roles, roleName, emptyLabel }: { roles: string[]; roleName: Map<string, string>; emptyLabel: string }) {
  const erpRoles = roles.filter((r) => roleName.has(r));
  if (erpRoles.length === 0) return <span className="text-muted-foreground">{emptyLabel}</span>;
  return (
    <div className="flex flex-wrap gap-1">
      {erpRoles.map((r) => (
        <Badge key={r} variant="primary" className="whitespace-nowrap" title={r}>
          {roleName.get(r)}
        </Badge>
      ))}
    </div>
  );
}

export const UserManagement = () => {
  const { t } = useTranslation('settings');
  const statusFilter = [
    { value: 'active', label: t('users.status.active') },
    { value: 'inactive', label: t('users.status.inactive') },
  ];
  const list = useListState({ filters: FILTER_KEYS, defaultSort: { id: 'createdAt', desc: true } });
  const [density, setDensity] = useTableDensity();
  const queryClient = useQueryClient();
  const canEdit = can('users', 'edit');
  const selfId = getStoredUser()?.id;

  const [form, setForm] = useState<{ open: boolean; user: UserRow | null }>({ open: false, user: null });
  const [pendingToggle, setPendingToggle] = useState<UserRow | null>(null);
  const [provisionOpen, setProvisionOpen] = useState(false);

  const usersQuery = useQuery({
    queryKey: ['users', 'list', list.params],
    queryFn: async () => (await api.get<Paginated<UserRow>>(ENDPOINT, { params: list.params })).data,
    placeholderData: keepPreviousData,
  });

  const summaryQuery = useQuery({
    queryKey: ['users', 'summary', list.filterParams],
    queryFn: async () => (await api.get<UserSummary>(`${ENDPOINT}/summary`, { params: list.filterParams })).data,
    placeholderData: keepPreviousData,
  });

  const rolesQuery = useQuery<RoleOption[]>({
    queryKey: ['roles-assignable'],
    queryFn: async () => (await api.get('/roles/assignable')).data,
  });

  const roleName = useMemo(() => new Map((rolesQuery.data ?? []).map((r) => [r.key, r.name || r.key])), [rolesQuery.data]);

  const roleOptions = useMemo(
    () => (rolesQuery.data ?? []).map((r) => ({ value: r.key, label: r.name || r.key })),
    [rolesQuery.data],
  );

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['users'] });

  const toggleMutation = useMutation({
    mutationFn: async (id: string) => (await api.patch<UserRow>(`${ENDPOINT}/${id}/toggle`)).data,
    onSuccess: (user) => {
      invalidate();
      toast.success(
        user.isActive
          ? t('users.toast.activated', { name: user.fullName })
          : t('users.toast.deactivated', { name: user.fullName }),
      );
    },
    onError: (err) => toast.error(apiError(err, t('users.toast.toggleError'))),
  });

  const syncMutation = useMutation({
    mutationFn: async () => (await api.post<SyncResult>(`${ENDPOINT}/sync`)).data,
    onSuccess: (r) => {
      invalidate();
      toast.success(t('users.sync.done', { updated: r.updated, deactivated: r.deactivated }));
    },
    onError: (err) => toast.error(apiError(err, t('users.sync.error'))),
  });

  const users = usersQuery.data?.data;
  const total = usersQuery.data?.total;
  const summary = summaryQuery.data;

  const columns = useMemo<ColumnDef<UserRow>[]>(
    () => [
      {
        id: 'fullName',
        header: t('users.columns.name'),
        enableSorting: true,
        meta: { cellClassName: 'min-w-0 max-w-72' },
        cell: ({ row }) => {
          const u = row.original;
          return (
            <div className="flex min-w-0 items-center gap-3">
              <span
                aria-hidden
                className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary-subtle text-xs font-semibold text-primary-subtle-foreground"
              >
                {initials(u.fullName)}
              </span>
              <div className="min-w-0">
                <p className="truncate font-medium text-foreground">{u.fullName}</p>
                <p className="truncate text-xs text-muted-foreground lg:hidden">{u.email}</p>
              </div>
            </div>
          );
        },
      },
      {
        id: 'npk',
        header: t('users.columns.npk'),
        enableSorting: true,
        meta: { hideBelow: 'md', cellClassName: 'font-mono text-xs text-muted-foreground whitespace-nowrap' },
        cell: ({ row }) => row.original.npk || '—',
      },
      {
        id: 'email',
        header: t('users.columns.email'),
        enableSorting: true,
        meta: { hideBelow: 'lg', cellClassName: 'max-w-64 truncate text-muted-foreground' },
        cell: ({ row }) => row.original.email,
      },
      {
        id: 'roles',
        header: t('users.columns.erpRoles'),
        meta: { hideBelow: 'sm', cellClassName: 'max-w-72' },
        cell: ({ row }) => (
          <ErpRoleBadges roles={row.original.roles ?? []} roleName={roleName} emptyLabel={t('users.noErpRole')} />
        ),
      },
      {
        id: 'supervisor',
        header: t('users.columns.supervisor'),
        meta: { hideBelow: 'lg', cellClassName: 'max-w-48 truncate whitespace-nowrap' },
        cell: ({ row }) =>
          row.original.supervisor ? (
            <span title={row.original.supervisor.fullName}>{row.original.supervisor.fullName}</span>
          ) : (
            <span className="text-muted-foreground">—</span>
          ),
      },
      {
        id: 'status',
        header: t('users.columns.status'),
        cell: ({ row }) => <StatusBadge status={row.original.isActive ? 'Active' : 'Inactive'} />,
      },
      {
        id: 'lastLoginAt',
        header: t('users.columns.lastActive'),
        enableSorting: true,
        sortDescFirst: true,
        meta: { hideBelow: 'lg', cellClassName: 'whitespace-nowrap text-muted-foreground' },
        cell: ({ row }) => {
          const at = row.original.lastLoginAt;
          if (!at) return <span>{t('users.never')}</span>;
          return (
            <time dateTime={at} title={formatDateTime(at)}>
              {formatRelativeTime(at)}
            </time>
          );
        },
      },
      {
        id: 'actions',
        header: () => <span className="sr-only">{t('users.columns.actions')}</span>,
        meta: { align: 'right', headerClassName: 'w-12', cellClassName: 'w-12 py-0' },
        cell: ({ row }) => {
          const u = row.original;
          const isSelf = u.id === selfId;
          return (
            <RowActions
              label={t('users.actions.menuLabel', { name: u.fullName })}
              actions={[
                { label: t('users.actions.edit'), icon: Pencil, hidden: !canEdit, onSelect: () => setForm({ open: true, user: u }) },
                {
                  label: t('users.actions.activate'),
                  icon: ToggleLeft,
                  hidden: !canEdit || u.isActive,
                  onSelect: () => setPendingToggle(u),
                },
                {
                  label: isSelf ? t('users.actions.deactivateSelf') : t('users.actions.deactivate'),
                  icon: ToggleRight,
                  tone: 'danger',
                  disabled: isSelf,
                  hidden: !canEdit || !u.isActive,
                  onSelect: () => setPendingToggle(u),
                },
              ]}
            />
          );
        },
      },
    ],
    [canEdit, selfId, roleName, t],
  );

  const toggleCopy = pendingToggle ? confirmCopy(pendingToggle, t) : null;

  return (
    <div className="space-y-5 pb-8">
      <PageHeader
        title={t('users.title')}
        description={t('users.description')}
        actions={
          canEdit ? (
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => syncMutation.mutate()} loading={syncMutation.isPending}>
                <RefreshCw aria-hidden />
                {t('users.sync.button')}
              </Button>
              <Button onClick={() => setProvisionOpen(true)}>
                <UserPlus aria-hidden />
                {t('users.add')}
              </Button>
            </div>
          ) : undefined
        }
      />

      <p className="rounded-md border border-border bg-muted/40 px-3 py-2 text-sm text-muted-foreground">
        {t('users.platformNote')}{' '}
        <Link to={PLATFORM_USERS_PATH} className="font-medium text-primary underline-offset-4 hover:underline">
          {t('users.platformUsersLink')}
        </Link>
        {' · '}
        <Link to={PLATFORM_ROLES_PATH} className="font-medium text-primary underline-offset-4 hover:underline">
          {t('users.platformRolesLink')}
        </Link>
      </p>

      <section aria-label={t('users.summary.label')} className="grid grid-cols-2 gap-3 lg:grid-cols-3">
        <StatCard
          label={t('users.summary.total')}
          icon={Users}
          loading={!summary && !summaryQuery.isError}
          value={summary ? formatNumber(summary.total) : '—'}
        />
        <StatCard
          label={t('users.status.active')}
          icon={UserCheck}
          loading={!summary && !summaryQuery.isError}
          value={summary ? formatNumber(summary.active) : '—'}
          hint={t('users.summary.activeHint')}
        />
        <StatCard
          label={t('users.status.inactive')}
          icon={UserX}
          loading={!summary && !summaryQuery.isError}
          value={summary ? formatNumber(summary.inactive) : '—'}
          hint={t('users.summary.inactiveHint')}
        />
      </section>

      <div className="space-y-3">
        <FilterBar
          list={list}
          searchLabel={t('users.search.label')}
          searchPlaceholder={t('users.search.placeholder')}
          status={{ options: statusFilter }}
        >
          <ComboboxFilter
            label={t('users.columns.erpRoles')}
            allLabel={t('users.filter.allRoles')}
            value={list.filters.role}
            onChange={(v) => list.setFilter('role', v)}
            options={roleOptions}
            loading={rolesQuery.isLoading}
          />
        </FilterBar>

        <DataTable
          {...listTableProps(list, total)}
          label={t('users.table.label')}
          itemLabel={t('users.table.itemLabel')}
          columns={columns}
          data={users}
          getRowId={(row) => row.id}
          loading={usersQuery.isLoading}
          fetching={usersQuery.isFetching && usersQuery.isPlaceholderData}
          error={usersQuery.error}
          onRetry={() => usersQuery.refetch()}
          onRowClick={canEdit ? (row) => setForm({ open: true, user: row }) : undefined}
          rowClassName={(row) => (row.isActive ? undefined : 'text-muted-foreground')}
          density={density}
          onDensityChange={setDensity}
          empty={{
            title: t('users.empty.title'),
            description: t('users.empty.description'),
          }}
        />
      </div>

      <UserFormModal
        isOpen={form.open}
        editUser={form.user}
        onClose={() => setForm({ open: false, user: null })}
      />

      <ProvisionDialog open={provisionOpen} onOpenChange={setProvisionOpen} roleName={roleName} />

      <ConfirmDialog
        open={pendingToggle !== null}
        onOpenChange={(open) => !open && setPendingToggle(null)}
        title={toggleCopy?.title ?? ''}
        description={toggleCopy?.description}
        tone={toggleCopy?.tone}
        confirmLabel={toggleCopy?.confirmLabel}
        onConfirm={async () => {
          if (pendingToggle) await toggleMutation.mutateAsync(pendingToggle.id);
        }}
      />
    </div>
  );
};

/** Cari akun platform lalu provisi sebagai pengguna ERP (POST /users { platformUserId }). */
function ProvisionDialog({
  open,
  onOpenChange,
  roleName,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  roleName: Map<string, string>;
}) {
  const { t } = useTranslation('settings');
  const queryClient = useQueryClient();
  const [q, setQ] = useState('');
  const term = useDebounce(q.trim(), 300);
  const enabled = open && term.length >= 2;

  const searchQuery = useQuery<PlatformCandidate[]>({
    queryKey: ['users', 'platform-search', term],
    queryFn: async () => (await api.get(`${ENDPOINT}/platform-search`, { params: { q: term } })).data,
    enabled,
  });

  const addMutation = useMutation({
    mutationFn: async (c: PlatformCandidate) => (await api.post<UserRow>(ENDPOINT, { platformUserId: c.id })).data,
    onSuccess: (user) => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      toast.success(t('users.provision.added', { name: user.fullName }));
    },
    onError: (err) => toast.error(apiError(err, t('users.provision.error'))),
  });

  const close = (next: boolean) => {
    if (!next) setQ('');
    onOpenChange(next);
  };

  const results = searchQuery.data ?? [];

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent size="lg">
        <DialogHeader>
          <DialogTitle>{t('users.provision.title')}</DialogTitle>
          <DialogDescription>{t('users.provision.description')}</DialogDescription>
        </DialogHeader>

        <div className="relative">
          <Search aria-hidden className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            autoFocus
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder={t('users.provision.searchPlaceholder')}
            aria-label={t('users.provision.searchLabel')}
            className="pl-9"
          />
        </div>

        <div className="max-h-[50vh] min-h-24 overflow-y-auto rounded-md border border-border" aria-live="polite">
          {!enabled ? (
            <p className="px-3 py-6 text-center text-sm text-muted-foreground">{t('users.provision.hint')}</p>
          ) : searchQuery.isLoading ? (
            <p className="px-3 py-6 text-center text-sm text-muted-foreground">{t('users.provision.searching')}</p>
          ) : searchQuery.isError ? (
            <p role="alert" className="px-3 py-6 text-center text-sm text-danger-text">
              {apiError(searchQuery.error, t('users.provision.searchError'))}
            </p>
          ) : results.length === 0 ? (
            <p className="px-3 py-6 text-center text-sm text-muted-foreground">{t('users.provision.empty')}</p>
          ) : (
            <ul className="divide-y divide-border">
              {results.map((c) => {
                const erpRoles = c.roles.filter((r) => roleName.has(r));
                const adding = addMutation.isPending && addMutation.variables?.id === c.id;
                return (
                  <li key={c.id} className="flex items-center gap-3 px-3 py-2.5">
                    <span
                      aria-hidden
                      className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary-subtle text-xs font-semibold text-primary-subtle-foreground"
                    >
                      {initials(c.name)}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-foreground">
                        {c.name}
                        {c.npk && <span className="ml-2 font-mono text-xs text-muted-foreground">{c.npk}</span>}
                      </p>
                      <p className="truncate text-xs text-muted-foreground">{c.email}</p>
                      <div className="mt-1 flex flex-wrap gap-1">
                        {erpRoles.length > 0 ? (
                          erpRoles.map((r) => (
                            <Badge key={r} variant="primary">
                              {roleName.get(r)}
                            </Badge>
                          ))
                        ) : (
                          <span className="text-xs text-warning-text">{t('users.provision.noErpRole')}</span>
                        )}
                      </div>
                    </div>
                    {c.provisioned ? (
                      <Badge variant="success">{t('users.provision.registered')}</Badge>
                    ) : !c.active ? (
                      <Badge variant="neutral">{t('users.provision.inactive')}</Badge>
                    ) : (
                      <Button
                        size="sm"
                        onClick={() => addMutation.mutate(c, { onSuccess: () => searchQuery.refetch() })}
                        loading={adding}
                        disabled={addMutation.isPending && !adding}
                      >
                        <UserPlus aria-hidden />
                        {t('users.provision.add')}
                      </Button>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => close(false)}>
            {t('users.provision.close')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function confirmCopy(user: UserRow, t: TFunction<'settings'>): {
  title: string;
  description: string;
  confirmLabel: string;
  tone: 'default' | 'danger';
} {
  const name = user.fullName;
  return user.isActive
    ? {
        title: t('users.confirm.deactivate.title', { name }),
        description: t('users.confirm.deactivate.description'),
        confirmLabel: t('users.confirm.deactivate.confirm'),
        tone: 'danger',
      }
    : {
        title: t('users.confirm.activate.title', { name }),
        description: t('users.confirm.activate.description'),
        confirmLabel: t('users.confirm.activate.confirm'),
        tone: 'default',
      };
}
