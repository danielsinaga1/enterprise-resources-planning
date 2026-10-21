import React from 'react';
import { Tag } from 'primereact/tag';

// ============================================================
// APPROVAL STATUS BADGE
// Visual badge for approval workflow status
// ============================================================
type ApprovalStatus = 'pending' | 'approved' | 'rejected';
type IncidentStatus = 'open' | 'in_progress' | 'approved' | 'rejected' | 'closed'
  | 'new' | 'partially_approved' | 'pending_manager_approval' | 'fully_approved' | 'done';
type ObservationStatus = 'open' | 'in_progress' | 'closed' | 'overdue' | 'rejected' | 'returned';

interface ApprovalBadgeProps {
  status: ApprovalStatus | IncidentStatus | ObservationStatus | string;
  label?: string;
  className?: string;
}

const STATUS_CONFIG: Record<string, { severity: 'success' | 'info' | 'warning' | 'danger' | 'secondary'; icon: string }> = {
  // Approval status
  pending:       { severity: 'warning',   icon: 'pi pi-clock' },
  approved:      { severity: 'success',   icon: 'pi pi-check-circle' },
  rejected:      { severity: 'danger',    icon: 'pi pi-times-circle' },
  // Incident workflow statuses
  new:                       { severity: 'info',      icon: 'pi pi-file' },
  partially_approved:        { severity: 'warning',   icon: 'pi pi-replay' },
  pending_manager_approval:  { severity: 'warning',   icon: 'pi pi-clock' },
  fully_approved:            { severity: 'success',   icon: 'pi pi-check-circle' },
  in_progress:               { severity: 'warning',   icon: 'pi pi-spin pi-spinner' },
  done:                      { severity: 'success',   icon: 'pi pi-check-circle' },
  canceled:                  { severity: 'danger',    icon: 'pi pi-ban' },
  revised_by_administrator:  { severity: 'warning',   icon: 'pi pi-pencil' },
  revised_by_manager:        { severity: 'warning',   icon: 'pi pi-pencil' },
  // Legacy / other statuses
  open:          { severity: 'info',      icon: 'pi pi-circle' },
  approved_partially: { severity: 'warning', icon: 'pi pi-check' },
  reviewed:      { severity: 'success',   icon: 'pi pi-check' },
  action_taken:  { severity: 'info',      icon: 'pi pi-wrench' },
  closed:        { severity: 'secondary', icon: 'pi pi-lock' },
  overdue:       { severity: 'danger',    icon: 'pi pi-exclamation-triangle' },
  returned:      { severity: 'warning',   icon: 'pi pi-replay' },
};

const LABEL_MAP: Record<string, string> = {
  pending:       'Menunggu',
  approved:      'Disetujui',
  rejected:      'Ditolak',
  // Incident workflow labels
  new:                       'Baru',
  partially_approved:        'Perlu Revisi',
  pending_manager_approval:  'Menunggu Manager',
  fully_approved:            'Disetujui Penuh',
  in_progress:               'Berjalan',
  done:                      'Selesai',
  canceled:                  'Dibatalkan',
  revised_by_administrator:  'Revisi Admin',
  revised_by_manager:        'Revisi Manager',
  // Legacy / other
  open:          'Pelaporan',
  approved_partially: 'Approved Partially',
  reviewed:      'Ditinjau QSHE',
  action_taken:  'Menunggu Verifikasi',
  closed:        'Selesai',
  close:         'Selesai',
  overdue:       'Overdue',
  returned:      'Dikembalikan',
};

/** Normalise legacy status values coming from the DB (e.g. "Open", "Close") */
function normaliseStatus(raw: string): string {
  if (!raw) return 'unknown';
  const s = raw.toLowerCase().replace(/\s+/g, '_');
  if (s === 'close') return 'closed';
  if (s === 'partially_approved') return 'partially_approved';
  if (s === 'pending_manager_approval') return 'pending_manager_approval';
  if (s === 'fully_approved') return 'fully_approved';
  return s;
}

export const ApprovalBadge: React.FC<ApprovalBadgeProps> = ({
  status,
  label,
  className = '',
}) => {
  const key = normaliseStatus(status);
  const config = STATUS_CONFIG[key] ?? { severity: 'secondary' as const, icon: 'pi pi-question-circle' };
  const displayLabel = label || LABEL_MAP[key] || status;

  return (
    <Tag
      value={displayLabel}
      severity={config.severity}
      icon={config.icon}
      className={`text-xs font-semibold ${className}`}
    />
  );
};

// ============================================================
// RISK LEVEL BADGE
// ============================================================
interface RiskLevelBadgeProps {
  riskLevel?: string;
  severity?: number;
  probability?: number;
}

const RISK_COLORS: Record<string, string> = {
  low:      '#22c55e',
  medium:   '#f59e0b',
  high:     '#ef4444',
  critical: '#991b1b',
};

export const RiskLevelBadge: React.FC<RiskLevelBadgeProps> = ({
  riskLevel,
  severity,
  probability,
}) => {
  if (!riskLevel) return <span className="text-xs text-400">—</span>;

  const color = RISK_COLORS[riskLevel.toLowerCase()] ?? '#6b7280';
  const label = riskLevel.charAt(0).toUpperCase() + riskLevel.slice(1);
  const detail =
    severity !== undefined && probability !== undefined
      ? ` (S:${severity} × P:${probability})`
      : '';

  return (
    <span
      className="inline-flex align-items-center gap-1 px-2 py-1 border-round-md text-xs font-bold"
      style={{ background: color + '18', color }}
    >
      <i className="pi pi-shield" style={{ fontSize: '0.75rem' }} />
      {label}
      {detail}
    </span>
  );
};

// ============================================================
// OBSERVATION TYPE BADGE (NCR / JSO)
// ============================================================
interface ObservationTypeBadgeProps {
  isJso: boolean;
}

export const ObservationTypeBadge: React.FC<ObservationTypeBadgeProps> = ({ isJso }) => (
  <Tag
    value={isJso ? 'JSO' : 'NCR'}
    severity={isJso ? 'info' : 'warning'}
    icon={isJso ? 'pi pi-eye' : 'pi pi-exclamation-triangle'}
    className="text-xs font-bold"
  />
);
