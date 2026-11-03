/**
 * Types, labels and pure helpers for the Pemeliharaan page (GA work orders
 * and preventive schedules). Status labels come from lib/status (StatusBadge
 * docType 'ga_work_order'); priority and type labels live here.
 */
import type { StatusTone } from '@/lib/status';
import { LOCALE } from '@/lib/format';
import { labelMap, tEam } from '../eam-i18n';

export type WoStatus = 'OPEN' | 'ASSIGNED' | 'IN_PROGRESS' | 'DONE' | 'CLOSED' | 'CANCELLED';
export type WoPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type WoType = 'PREVENTIVE' | 'CORRECTIVE';
export type DueState = 'overdue' | 'dueSoon';

export const WO_STATUSES: readonly WoStatus[] = ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'DONE', 'CLOSED', 'CANCELLED'];
export const WO_ACTIVE: ReadonlySet<WoStatus> = new Set<WoStatus>(['OPEN', 'ASSIGNED', 'IN_PROGRESS']);
/** Terminal: no lifecycle action, parts or photos any more. */
export const WO_LOCKED: ReadonlySet<WoStatus> = new Set<WoStatus>(['CLOSED', 'CANCELLED']);
/** Server pseudo status: OPEN + ASSIGNED + IN_PROGRESS. */
export const WO_STATUS_ACTIVE = 'ACTIVE';

export const PRIORITY_ORDER: readonly WoPriority[] = ['URGENT', 'HIGH', 'MEDIUM', 'LOW'];

export const PRIORITY_LABELS: Record<WoPriority, string> = labelMap(PRIORITY_ORDER, 'workOrder.priority');

export const PRIORITY_TONE: Record<WoPriority, StatusTone> = {
  LOW: 'neutral',
  MEDIUM: 'info',
  HIGH: 'warning',
  URGENT: 'danger',
};

export const TYPE_LABELS: Record<WoType, string> = labelMap(['CORRECTIVE', 'PREVENTIVE'], 'workOrder.type');

export const DUE_SOON_DAYS = 7;
const DAY_MS = 24 * 60 * 60 * 1000;

type Money = string | number | null | undefined;

export interface AssetRef {
  id: string;
  code: string;
  name: string;
}

export interface WorkOrderRow {
  id: string;
  code: string;
  title: string;
  status: WoStatus;
  priority: WoPriority;
  type: WoType;
  component?: string | null;
  reportedAt: string;
  dueAt: string | null;
  completedAt: string | null;
  assignedToId: string | null;
  totalCost: number;
  asset?: AssetRef | null;
  assignee?: { id: string; fullName: string | null } | null;
  _count?: { parts: number; photos: number };
}

export interface WorkOrderSummary {
  count: number;
  active: number;
  overdue: number;
  doneThisMonth: number;
  totalCost: number;
}

export interface ChecklistEntry {
  id: string;
  label: string;
  required?: boolean;
  done?: boolean;
  notes?: string | null;
}

export interface WorkOrderDetail extends Omit<WorkOrderRow, 'assignee' | '_count'> {
  reportedById: string | null;
  description: string | null;
  resolution: string | null;
  startedAt: string | null;
  closedAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  laborHours: Money;
  laborCost: Money;
  externalVendor: string | null;
  externalCost: Money;
  scheduleId: string | null;
  checklist: ChecklistEntry[] | null;
  parts: Array<{
    id: string;
    quantity: Money;
    unitCost: Money;
    inventoryItem?: { sku: string; name: string; uom: string } | null;
    fromLocation?: { code: string; name: string } | null;
  }>;
  photos: Array<{ id: string; filePath: string; type: 'BEFORE' | 'AFTER'; caption: string | null }>;
  materialRequests: Array<{ id: string; code: string; status: string; _count?: { items: number } }>;
}

export interface ScheduleRow {
  id: string;
  intervalDays: number | null;
  intervalHours: number | null;
  intervalKm: number | null;
  nextDueAt: string | null;
  /** Km jatuh tempo berikutnya (jadwal ber-interval km). */
  nextDueAtKm?: number | null;
  lastDoneAtKm?: number | null;
  /** Odometer terakhir aset (null bila belum tercatat). */
  currentKm?: number | null;
  /** Jam jatuh tempo berikutnya (jadwal ber-interval jam, dari hour meter). */
  nextDueAtHours?: number | null;
  lastDoneAtHours?: number | null;
  /** Pembacaan hour meter terakhir aset (null bila belum pernah dicatat). */
  currentHours?: number | null;
  notes: string | null;
  isActive: boolean;
  asset?: { id: string; code?: string; name?: string } | null;
}

/** Prefill for "Buat WO" from a due schedule. */
export interface WoPrefill {
  assetId: string;
  type: WoType;
  scheduleId: string;
  /** yyyy-mm-dd */
  dueAt: string;
}

export const toNum = (v: Money): number => {
  const n = typeof v === 'number' ? v : Number(v ?? 0);
  return Number.isFinite(n) ? n : 0;
};

/** Overdue / due within 7 days, for still-active work only. */
export function woDueState(wo: Pick<WorkOrderRow, 'status' | 'dueAt'>, now: Date = new Date()): DueState | null {
  if (!wo.dueAt || !WO_ACTIVE.has(wo.status)) return null;
  const due = new Date(wo.dueAt).getTime();
  if (Number.isNaN(due)) return null;
  if (due < now.getTime()) return 'overdue';
  if (due - now.getTime() < DUE_SOON_DAYS * DAY_MS) return 'dueSoon';
  return null;
}

/** Km remaining before a schedule counts as "due soon" (server: MaintenanceDue.DUE_SOON_KM). */
export const DUE_SOON_KM = 500;
/** Operating hours remaining before a schedule counts as "due soon" (server: MaintenanceDue.DUE_SOON_HOURS). */
export const DUE_SOON_HOURS = 50;

type ScheduleDueFields = Pick<
  ScheduleRow,
  'isActive' | 'nextDueAt' | 'intervalDays' | 'intervalHours' | 'intervalKm' | 'nextDueAtKm' | 'currentKm' | 'nextDueAtHours' | 'currentHours'
>;

/** Km tracking applies when the schedule has a km interval and a known km due point. */
export const scheduleTracksKm = (s: Pick<ScheduleRow, 'intervalKm' | 'nextDueAtKm'>): boolean => !!s.intervalKm && s.nextDueAtKm != null;

/** Hour-meter tracking applies when the schedule has an hour interval and a known due hour meter. */
export const scheduleTracksHours = (s: Pick<ScheduleRow, 'intervalHours' | 'nextDueAtHours'>): boolean =>
  !!s.intervalHours && s.nextDueAtHours != null;

/** The due date applies with a day interval, or when no meter interval (km / hours) is tracked yet. */
export const scheduleUsesDate = (
  s: Pick<ScheduleRow, 'intervalDays' | 'intervalHours' | 'intervalKm' | 'nextDueAtKm' | 'nextDueAtHours'>,
): boolean => !!s.intervalDays || (!scheduleTracksKm(s) && !scheduleTracksHours(s));

/**
 * Preventive schedule due state, whichever comes first: the date (≤ 7 days), the odometer (≤ 500 km) or the
 * hour meter (≤ 50 h). Same rule as the server's notification check (MaintenanceDue.java). Inactive schedules never flag.
 */
export function scheduleDueState(s: ScheduleDueFields, now: Date = new Date()): DueState | null {
  if (!s.isActive) return null;
  const states = [scheduleDateState(s, now), scheduleKmState(s), scheduleHoursState(s)];
  if (states.includes('overdue')) return 'overdue';
  return states.includes('dueSoon') ? 'dueSoon' : null;
}

/** Date part of {@link scheduleDueState} (null when the date does not apply). */
export function scheduleDateState(s: ScheduleDueFields, now: Date = new Date()): DueState | null {
  if (!s.isActive || !scheduleUsesDate(s) || !s.nextDueAt) return null;
  return woDueState({ status: 'OPEN', dueAt: s.nextDueAt }, now);
}

/** Odometer part of {@link scheduleDueState} (null without km tracking or a recorded odometer). */
export function scheduleKmState(s: ScheduleDueFields): DueState | null {
  if (!s.isActive || !scheduleTracksKm(s) || s.currentKm == null) return null;
  const left = (s.nextDueAtKm as number) - s.currentKm;
  return left <= 0 ? 'overdue' : left <= DUE_SOON_KM ? 'dueSoon' : null;
}

/** Hour-meter part of {@link scheduleDueState} (null without hour tracking or a meter reading). */
export function scheduleHoursState(s: ScheduleDueFields): DueState | null {
  if (!s.isActive || !scheduleTracksHours(s) || s.currentHours == null) return null;
  const left = (s.nextDueAtHours as number) - s.currentHours;
  return left <= 0 ? 'overdue' : left <= DUE_SOON_HOURS ? 'dueSoon' : null;
}

export function intervalLabel(s: Pick<ScheduleRow, 'intervalDays' | 'intervalHours' | 'intervalKm'>): string {
  if (s.intervalDays) return tEam('schedule.interval.days', { count: s.intervalDays });
  if (s.intervalHours) return tEam('schedule.interval.hours', { count: s.intervalHours });
  if (s.intervalKm) return tEam('schedule.interval.km', { km: s.intervalKm.toLocaleString(LOCALE) });
  return '—';
}

/** ISO/date string → yyyy-mm-dd for date inputs (UTC date part). */
export const toDateInput = (v: string | null | undefined): string => (v ? new Date(v).toISOString().slice(0, 10) : '');

export const assetLabel = (a: { code?: string; name?: string } | null | undefined): string =>
  a ? [a.code, a.name].filter(Boolean).join(' · ') || '—' : '—';

/** Which lifecycle actions a work order allows (server rules in routes/ga/maintenance.ts). */
export function woActions(status: WoStatus) {
  return {
    assign: status === 'OPEN' || status === 'ASSIGNED',
    start: status === 'ASSIGNED',
    complete: status === 'ASSIGNED' || status === 'IN_PROGRESS',
    close: status === 'DONE',
    // Server refuses to cancel DONE as well as CLOSED / CANCELLED.
    cancel: WO_ACTIVE.has(status),
    addParts: !WO_LOCKED.has(status),
    requestMaterial: !WO_LOCKED.has(status),
  };
}

export const MAX_WO_PHOTOS = 6;

/** Photo upload: open WO, under the limit, and only the reporter or the technician (server rule). */
export function canUploadPhoto(
  wo: Pick<WorkOrderDetail, 'status' | 'reportedById' | 'assignedToId' | 'photos'>,
  userId: string | undefined,
): boolean {
  if (WO_LOCKED.has(wo.status) || wo.photos.length >= MAX_WO_PHOTOS || !userId) return false;
  return wo.reportedById === userId || wo.assignedToId === userId;
}

export function apiErrorMessage(err: unknown, fallback: string): string {
  const e = err as { response?: { data?: { error?: unknown } } };
  return typeof e?.response?.data?.error === 'string' ? e.response.data.error : fallback;
}
