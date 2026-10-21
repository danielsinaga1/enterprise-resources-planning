import React from 'react';

// ============================================================
// KPI STAT CARD — Reusable card for dashboard stats
// Matches DashboardHome.tsx visual pattern
// ============================================================
interface KpiStatCardProps {
  label: string;
  value: string | number;
  delta?: string;
  trend?: 'up' | 'down';
  icon: string;
  color: string;
  bg: string;
  onClick?: () => void;
}

export const KpiStatCard: React.FC<KpiStatCardProps> = ({
  label,
  value,
  delta,
  trend,
  icon,
  color,
  bg,
  onClick,
}) => {
  const interactive = !!onClick;
  const displayValue =
    typeof value === 'number' && Number.isFinite(value)
      ? value.toLocaleString('id-ID')
      : value;

  return (
    <div
      className={`surface-card border-round-xl p-4 shadow-1 flex align-items-start justify-content-between h-full ${
        interactive ? 'cursor-pointer hover:shadow-3' : ''
      } transition-all transition-duration-200`}
      style={{ borderLeft: `4px solid ${color}` }}
      onClick={onClick}
      role={interactive ? 'button' : undefined}
      tabIndex={interactive ? 0 : undefined}
      onKeyDown={
        interactive
          ? (e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onClick();
              }
            }
          : undefined
      }
      aria-label={interactive ? `${label}: ${displayValue}` : undefined}
    >
      <div className="flex flex-column gap-2 min-w-0">
        <span className="text-xs font-medium text-500 uppercase letter-spacing-1 white-space-nowrap overflow-hidden text-overflow-ellipsis">
          {label}
        </span>
        <span
          className="text-2xl font-bold text-900"
          style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
          title={String(displayValue)}
        >
          {displayValue}
        </span>
        {delta && trend && (
          <span
            className={`text-xs font-semibold ${
              trend === 'up' ? 'text-green-500' : 'text-red-500'
            }`}
          >
            <i
              className={`pi ${
                trend === 'up' ? 'pi-arrow-up' : 'pi-arrow-down'
              } text-xs mr-1`}
            />
            {delta}
          </span>
        )}
      </div>
      <div
        className="flex align-items-center justify-content-center border-round-xl flex-shrink-0"
        style={{ width: '3rem', height: '3rem', background: bg }}
      >
        <i className={icon} style={{ fontSize: '1.25rem', color }} aria-hidden="true" />
      </div>
    </div>
  );
};
