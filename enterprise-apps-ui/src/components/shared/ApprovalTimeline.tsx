import { Tag } from 'primereact/tag';
import { Button } from 'primereact/button';
import { Divider } from 'primereact/divider';

/* ============================================================
   REUSABLE APPROVAL TIMELINE COMPONENT
   Mirrors the SafetyObservation 5-stage workflow visual style
   but is fully configurable for any module.
   ============================================================ */

export type StageStatus = 'completed' | 'active' | 'pending' | 'rejected' | 'returned';

export interface StageAction {
  action: string;
  label: string;
  icon: string;
  severity: 'success' | 'info' | 'warning' | 'danger';
}

export interface StageConfig {
  key: string;
  label: string;
  icon: string;
  roleLabel: string;
}

export interface StageInfo {
  person?: string | null;
  date?: string | null;
  note?: string | null;
  noteLabel?: string;
  noteColor?: string;
}

export interface ApprovalTimelineProps {
  /** Stage definitions (labels, icons, roles) */
  stages: StageConfig[];
  /** Current active stage index (0-based) */
  currentStageIndex: number;
  /** Whether the report is rejected */
  isRejected?: boolean;
  /** Whether the report is returned to creator */
  isReturned?: boolean;
  /** Rejection/return reason text */
  reason?: string | null;
  /** Get info (person, date) for each stage */
  getStageInfo: (stageIndex: number) => StageInfo;
  /** Get available actions for each stage */
  getStageActions: (stageIndex: number) => StageAction[];
  /** Handler when an action button is clicked */
  onAction: (action: string) => void;
  /** Optional reject button at the bottom */
  rejectButton?: { label: string; action: string } | null;
}

const STAGE_COLORS: Record<StageStatus, { bg: string; border: string; text: string; line: string }> = {
  completed: { bg: '#e8f5e9', border: '#4caf50', text: '#2e7d32', line: '#4caf50' },
  active:    { bg: '#e3f2fd', border: '#2196f3', text: '#1565c0', line: '#bdbdbd' },
  pending:   { bg: '#f5f5f5', border: '#bdbdbd', text: '#9e9e9e', line: '#bdbdbd' },
  rejected:  { bg: '#ffebee', border: '#f44336', text: '#c62828', line: '#f44336' },
  returned:  { bg: '#fff3e0', border: '#ff9800', text: '#e65100', line: '#ff9800' },
};

const STATUS_TAG: Record<StageStatus, { value: string; severity: 'success' | 'info' | 'secondary' | 'danger' | 'warning' }> = {
  completed: { value: 'Selesai',       severity: 'success' },
  active:    { value: 'Berjalan',      severity: 'info' },
  pending:   { value: 'Menunggu',      severity: 'secondary' },
  rejected:  { value: 'Ditolak',       severity: 'danger' },
  returned:  { value: 'Dikembalikan',  severity: 'warning' },
};

function resolveStageStatus(
  stageIdx: number,
  currentIdx: number,
  isRejected: boolean,
  isReturned: boolean,
): StageStatus {
  // Stage 0 (pelaporan awal) selalu completed jika workflow sudah berjalan (currentIdx >= 1)
  if (stageIdx === 0 && currentIdx >= 1) return 'completed';
  if (isRejected) {
    if (stageIdx < currentIdx) return 'completed';
    if (stageIdx === currentIdx) return 'rejected';
    return 'pending';
  }
  if (isReturned && stageIdx === 0) return 'returned';
  if (isReturned) return 'pending';
  if (stageIdx < currentIdx) return 'completed';
  if (stageIdx === currentIdx) return 'active';
  return 'pending';
}

const ApprovalTimeline: React.FC<ApprovalTimelineProps> = ({
  stages,
  currentStageIndex,
  isRejected = false,
  isReturned = false,
  reason,
  getStageInfo,
  getStageActions,
  onAction,
  rejectButton,
}) => {
  return (
    <div className="flex flex-column gap-0">
      {stages.map((stage, idx) => {
        const stageStatus = resolveStageStatus(idx, currentStageIndex, isRejected, isReturned);
        const colors = STAGE_COLORS[stageStatus];
        const tag = STATUS_TAG[stageStatus];
        const info = getStageInfo(idx);
        const actions = getStageActions(idx);
        const isLast = idx === stages.length - 1;

        return (
          <div key={stage.key} className="flex gap-3" style={{ minHeight: '80px' }}>
            {/* Timeline line + circle */}
            <div className="flex flex-column align-items-center" style={{ width: '36px' }}>
              <div
                className="flex align-items-center justify-content-center border-circle"
                style={{
                  width: '36px', height: '36px',
                  backgroundColor: colors.bg,
                  border: `2px solid ${colors.border}`,
                  flexShrink: 0,
                }}
              >
                <i
                  className={stageStatus === 'completed' ? 'pi pi-check' : stage.icon}
                  style={{ fontSize: '0.9rem', color: colors.text }}
                />
              </div>
              {!isLast && (
                <div style={{ width: '2px', flex: 1, backgroundColor: colors.line, minHeight: '20px' }} />
              )}
            </div>

            {/* Content */}
            <div className="flex flex-column pb-3" style={{ flex: 1 }}>
              <div className="flex align-items-center gap-2 mb-1">
                <span className="text-sm font-bold" style={{ color: colors.text }}>{stage.label}</span>
                <Tag value={tag.value} severity={tag.severity} className="text-xs" />
              </div>

              <span className="text-xs text-500 mb-1">{stage.roleLabel}</span>

              {info.person && (
                <span className="text-sm font-medium text-800">{info.person}</span>
              )}
              {info.date && (
                <span className="text-xs text-500">{info.date}</span>
              )}

              {/* Optional info note (delegation, corrective action, etc.) */}
              {info.note && (
                <div
                  className="mt-1 p-2 border-round"
                  style={{
                    backgroundColor: info.noteColor === 'green' ? '#e8f5e9' : '#fff3e0',
                    border: `1px solid ${info.noteColor === 'green' ? '#c8e6c9' : '#ffe0b2'}`,
                  }}
                >
                  {info.noteLabel && (
                    <span className="text-xs font-medium" style={{ color: info.noteColor === 'green' ? '#2e7d32' : '#e65100' }}>
                      {info.noteLabel}
                    </span>
                  )}
                  <p className="text-xs mt-1 mb-0" style={{ color: info.noteColor === 'green' ? '#1b5e20' : '#bf360c' }}>
                    {info.note}
                  </p>
                </div>
              )}

              {/* Rejection reason on the rejected stage */}
              {isRejected && reason && idx === currentStageIndex && (
                <div className="mt-1 p-2 border-round" style={{ backgroundColor: '#ffebee', border: '1px solid #ffcdd2' }}>
                  <span className="text-xs font-medium" style={{ color: '#c62828' }}>Alasan Penolakan:</span>
                  <p className="text-xs mt-1 mb-0" style={{ color: '#b71c1c' }}>{reason}</p>
                </div>
              )}

              {/* Return to creator comment */}
              {isReturned && reason && idx === 0 && (
                <div className="mt-1 p-2 border-round" style={{ backgroundColor: '#fff3e0', border: '1px solid #ffe0b2' }}>
                  <span className="text-xs font-medium" style={{ color: '#e65100' }}>
                    <i className="pi pi-replay mr-1" style={{ fontSize: '0.7rem' }} />
                    Komentar:
                  </span>
                  <p className="text-xs mt-1 mb-0" style={{ color: '#bf360c' }}>{reason}</p>
                </div>
              )}

              {/* Action buttons */}
              {actions.length > 0 && (
                <div className="flex gap-2 mt-2">
                  {actions.map((a) => (
                    <Button
                      key={a.action}
                      label={a.label}
                      icon={a.icon}
                      severity={a.severity}
                      size="small"
                      className="border-round-lg shadow-1 font-semibold"
                      style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.5rem 1rem' }}
                      onClick={() => onAction(a.action)}
                    />
                  ))}
                </div>
              )}
            </div>
          </div>
        );
      })}

      {/* Bottom reject button */}
      {rejectButton && (
        <>
          <Divider className="my-2" />
          <Button
            label={rejectButton.label}
            icon="pi pi-times"
            severity="danger"
            outlined
            size="small"
            className="w-full border-round-lg shadow-1 font-semibold"
            style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }}
            onClick={() => onAction(rejectButton.action)}
          />
        </>
      )}
    </div>
  );
};

export default ApprovalTimeline;
