/**
 * Ubah akses ERP seorang pengguna (Manajemen Pengguna). Nama, NPK, email, kata sandi, dan peran
 * dikelola di platform sehingga hanya ditampilkan; yang dapat diubah di sini: atasan langsung
 * (approver tahap 1 Pengajuan Pembayaran), bahasa antarmuka, dan status akses ERP.
 */
import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslation } from "react-i18next";
import api from "@/lib/api";
import { getStoredUser } from "@/lib/permissions";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Switch } from "@/components/ui/switch";
import { Field } from "@/components/shared";

export interface EditableUser {
  id: string;
  fullName: string;
  email: string;
  npk?: string | null;
  roles?: string[];
  supervisorId?: string | null;
  language?: string | null;
  isActive?: boolean;
}

interface RoleOption {
  key: string;
  name: string;
}

const NO_SUPERVISOR = "__none";
const DEFAULT_LANGUAGE = "__default";

interface UserFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  editUser?: EditableUser | null;
}

function apiError(err: unknown, fallback: string): string {
  const e = err as { response?: { data?: { error?: string } } };
  return e.response?.data?.error || fallback;
}

export default function UserFormModal({
  isOpen,
  onClose,
  editUser,
}: UserFormModalProps) {
  const [busy, setBusy] = useState(false);
  return (
    <Dialog open={isOpen && !!editUser} onOpenChange={(open) => !open && !busy && onClose()}>
      <DialogContent size="md">
        {/* Konten di-unmount saat ditutup sehingga setiap pembukaan mulai dari state baru. */}
        {editUser && (
          <UserForm
            key={editUser.id}
            editUser={editUser}
            onClose={onClose}
            onBusyChange={setBusy}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

interface UserFormProps {
  editUser: EditableUser;
  onClose: () => void;
  onBusyChange: (busy: boolean) => void;
}

function UserForm({ editUser, onClose, onBusyChange }: UserFormProps) {
  const { t } = useTranslation(["shell", "common"]);
  const queryClient = useQueryClient();
  const isSelf = getStoredUser()?.id === editUser.id;
  const [supervisorId, setSupervisorId] = useState<string>(
    editUser.supervisorId ?? NO_SUPERVISOR,
  );
  const [language, setLanguage] = useState<string>(
    editUser.language ?? DEFAULT_LANGUAGE,
  );
  const [isActive, setIsActive] = useState<boolean>(editUser.isActive ?? true);
  const [submitError, setSubmitError] = useState("");

  const { data: roleOptions = [] } = useQuery<RoleOption[]>({
    queryKey: ["roles-assignable"],
    queryFn: async () => (await api.get("/roles/assignable")).data,
  });
  const roleName = new Map(roleOptions.map((r) => [r.key, r.name || r.key]));

  // Atasan langsung: approver tahap 1 Pengajuan Pembayaran (tidak boleh diri sendiri).
  const { data: supervisorOptions = [] } = useQuery<
    Array<{ id: string; fullName: string; isActive: boolean }>
  >({
    queryKey: ["users", "supervisor-options"],
    queryFn: async () =>
      (await api.get("/users", { params: { limit: 200, sort: "fullName" } })).data
        .data,
  });

  const mutation = useMutation({
    mutationFn: (payload: Record<string, string | boolean | null>) =>
      api.put(`/users/${editUser.id}`, payload),
    onMutate: () => onBusyChange(true),
    onSettled: () => onBusyChange(false),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      toast.success(t("userForm.saved"));
      onClose();
    },
    onError: (err) => setSubmitError(apiError(err, t("userForm.saveFailed"))),
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitError("");
    const payload: Record<string, string | boolean | null> = {
      supervisorId: supervisorId === NO_SUPERVISOR ? null : supervisorId,
      language: language === DEFAULT_LANGUAGE ? null : language,
    };
    if (!isSelf) payload.isActive = isActive;
    mutation.mutate(payload);
  };

  const platformRoles = editUser.roles ?? [];

  return (
    <form onSubmit={handleSubmit} noValidate className="grid gap-4">
      <DialogHeader>
        <DialogTitle>{t("userForm.editTitle")}</DialogTitle>
        <DialogDescription>
          {t("userForm.editDescription", { name: editUser.fullName })}
        </DialogDescription>
      </DialogHeader>

      <section
        aria-label={t("userForm.identity")}
        className="grid gap-2 rounded-lg border border-border bg-muted/40 p-3 text-sm"
      >
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
          {t("userForm.identity")}
        </p>
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
          <dt className="text-muted-foreground">{t("userForm.fullName")}</dt>
          <dd className="font-medium text-foreground">{editUser.fullName}</dd>
          <dt className="text-muted-foreground">{t("userForm.npk")}</dt>
          <dd className="font-mono text-xs leading-5">{editUser.npk || "—"}</dd>
          <dt className="text-muted-foreground">{t("userForm.email")}</dt>
          <dd className="break-all">{editUser.email}</dd>
          <dt className="text-muted-foreground">{t("userForm.platformRoles")}</dt>
          <dd className="flex flex-wrap gap-1">
            {platformRoles.length === 0 ? (
              <span className="text-muted-foreground">—</span>
            ) : (
              platformRoles.map((r) => (
                <Badge
                  key={r}
                  variant={roleName.has(r) ? "primary" : "neutral"}
                  title={roleName.has(r) ? r : t("userForm.notErpRole")}
                >
                  {roleName.get(r) ?? r}
                </Badge>
              ))
            )}
          </dd>
        </dl>
        <p className="text-xs text-muted-foreground">{t("userForm.identityHint")}</p>
      </section>

      <Field
        label={t("userForm.supervisor")}
        hint={t("userForm.supervisorHint")}
      >
        {(control) => (
          <Select value={supervisorId} onValueChange={setSupervisorId}>
            <SelectTrigger {...control}>
              <SelectValue placeholder={t("common:labels.none")} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NO_SUPERVISOR}>{t("common:labels.none")}</SelectItem>
              {supervisorOptions
                .filter((u) => u.id !== editUser.id && (u.isActive || u.id === supervisorId))
                .map((u) => (
                  <SelectItem key={u.id} value={u.id}>
                    {u.fullName}
                  </SelectItem>
                ))}
            </SelectContent>
          </Select>
        )}
      </Field>

      <Field label={t("userForm.language")} hint={t("userForm.languageHint")}>
        {(control) => (
          <Select value={language} onValueChange={setLanguage}>
            <SelectTrigger {...control}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={DEFAULT_LANGUAGE}>{t("userForm.languageDefault")}</SelectItem>
              <SelectItem value="id">Bahasa Indonesia</SelectItem>
              <SelectItem value="en">English</SelectItem>
            </SelectContent>
          </Select>
        )}
      </Field>

      <div className="flex items-start justify-between gap-4 rounded-lg border border-border p-3">
        <div className="min-w-0">
          <p id="erp-active-label" className="text-sm font-medium text-foreground">
            {t("userForm.active")}
          </p>
          <p id="erp-active-desc" className="mt-0.5 text-xs text-muted-foreground">
            {isSelf ? t("userForm.activeSelfHint") : t("userForm.activeHint")}
          </p>
        </div>
        <Switch
          checked={isActive}
          onCheckedChange={setIsActive}
          disabled={isSelf}
          aria-labelledby="erp-active-label"
          aria-describedby="erp-active-desc"
        />
      </div>

      {submitError && (
        <p
          role="alert"
          className="rounded-md bg-danger-subtle px-3 py-2 text-sm text-danger-text"
        >
          {submitError}
        </p>
      )}

      <DialogFooter>
        <Button
          type="button"
          variant="outline"
          onClick={onClose}
          disabled={mutation.isPending}
        >
          {t("common:actions.cancel")}
        </Button>
        <Button type="submit" loading={mutation.isPending}>
          {t("userForm.saveChanges")}
        </Button>
      </DialogFooter>
    </form>
  );
}
