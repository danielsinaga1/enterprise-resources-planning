import { useMemo } from 'react';
import { useAuth } from '../context/AuthContext';

const ADMIN_ROLES = ['SUPER_ADMIN', 'ADMIN_IT'];

// Roles that can create/update lab reports & daily reports
const REPORT_WRITE_ROLES = ['KEPALA_LAB', 'MANAGER_LAB', 'STAFF_LAB', 'ANALIS_LAB'];
// Roles that can delete reports
const REPORT_DELETE_ROLES = ['KEPALA_LAB', 'MANAGER_LAB'];
// Roles that can review lab reports (finalisasi Tinjauan Teknis)
const REVIEW_ROLES = ['KEPALA_LAB', 'REVIEWER_LAB', 'QUALITY_MANAGER'];
// Roles that can approve/acknowledge lab reports (Penyelesaian Akhir).
// MANAGER_LAB wajib ada di sini: alur lab menugaskan tahap penutupan padanya.
const APPROVE_ROLES = ['KEPALA_LAB', 'MANAGER_LAB', 'APPROVER_LAB', 'QUALITY_MANAGER'];
// Roles that can reject lab reports
const REJECT_ROLES = ['KEPALA_LAB', 'MANAGER_LAB', 'REVIEWER_LAB', 'APPROVER_LAB', 'QUALITY_MANAGER'];
// Roles that can manage master data (CRUD)
const MASTER_DATA_MANAGE_ROLES = ['KEPALA_LAB', 'MANAGER_LAB'];

function hasAny(userRoles: string[], allowedRoles: string[]): boolean {
  return userRoles.some((r) => ADMIN_ROLES.includes(r) || allowedRoles.includes(r));
}

export function useLimsPermissions() {
  const { user } = useAuth();
  const roles = useMemo(() => user?.roles ?? [], [user?.roles]);

  return useMemo(
    () => ({
      /** Lab report & daily report: create / update */
      canCreateReport: hasAny(roles, REPORT_WRITE_ROLES),
      canEditReport: hasAny(roles, REPORT_WRITE_ROLES),
      /** Lab report & daily report: delete */
      canDeleteReport: hasAny(roles, REPORT_DELETE_ROLES),
      /** Lab report: review */
      canReview: hasAny(roles, REVIEW_ROLES),
      /** Lab report: acknowledge / approve */
      canApprove: hasAny(roles, APPROVE_ROLES),
      /** Lab report: reject */
      canReject: hasAny(roles, REJECT_ROLES),
      /** Master data: create / update / delete */
      canManageMasterData: hasAny(roles, MASTER_DATA_MANAGE_ROLES),
    }),
    [roles],
  );
}
