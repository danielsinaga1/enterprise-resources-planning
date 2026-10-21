import api from "./api";
import type { ApiResponse } from "../types";
import type {
  AreaIncident,
  CategoryIncident,
  ClassificationIncident,
  RootCause,
  RegionIncident,
  OriginDepartment,
  DesignationDepartment,
  JsoLeaderMaster,
  JsoMemberMaster,
  JobTitle,
  JsoLocation,
  Division,
  ChartOfAccount,
} from "../types/irms";

const PREFIX = "/api/v1/master";

// Generic CRUD factory
function createCrudService<T extends { id?: number }>(resource: string) {
  return {
    async list(params?: Record<string, string | number | boolean | undefined>): Promise<T[]> {
      const { data } = await api.get<ApiResponse<T[]>>(`${PREFIX}/${resource}`, { params });
      return data.data;
    },
    async create(payload: Omit<T, "id">): Promise<T> {
      const { data } = await api.post<ApiResponse<T>>(`${PREFIX}/${resource}`, payload);
      return data.data;
    },
    async update(id: number, payload: Partial<T>): Promise<T> {
      const { data } = await api.put<ApiResponse<T>>(`${PREFIX}/${resource}/${id}`, payload);
      return data.data;
    },
    async remove(id: number): Promise<void> {
      await api.delete(`${PREFIX}/${resource}/${id}`);
    },
  };
}

export const masterAreaService = createCrudService<AreaIncident>("areas");
export const masterCategoryService = createCrudService<CategoryIncident>("categories");
export const masterClassificationService = createCrudService<ClassificationIncident>("classifications");
export const masterRootCauseService = createCrudService<RootCause>("root-causes");
export const masterRegionService = createCrudService<RegionIncident>("regions");
export const masterOriginDeptService = createCrudService<OriginDepartment>("origin-departments");
export const masterDesignationDeptService = createCrudService<DesignationDepartment>("designation-departments");
export const masterJsoLeaderService = createCrudService<JsoLeaderMaster>("jso-leaders");
export const masterJsoMemberService = createCrudService<JsoMemberMaster>("jso-members");
export const masterJobTitleService = createCrudService<JobTitle>("job-titles");
export const masterJsoLocationService = createCrudService<JsoLocation>("jso-locations");
export const masterDivisionService = createCrudService<Division>("divisions");
export const masterCoaService = createCrudService<ChartOfAccount>("coa");

// --- LIMS Master Data ---
import type {
  LabParameter,
  LabTestType,
  LabMethod,
  LabStandard,
  LabUnit,
  LabDepartment,
  LabResult,
  LabCompound,
} from "../types/lims";

export const masterLimsParameterService = createCrudService<LabParameter>("lims-parameters");
export const masterLimsTestTypeService = createCrudService<LabTestType>("lims-test-types");
export const masterLimsMethodService = createCrudService<LabMethod>("lims-methods");
export const masterLimsStandardService = createCrudService<LabStandard>("lims-standards");
export const masterLimsUnitService = createCrudService<LabUnit>("lims-units");
export const masterLimsDepartmentService = createCrudService<LabDepartment>("lims-departments");
export const masterLimsResultService = createCrudService<LabResult>("lims-results");
export const masterLimsCompoundService = createCrudService<LabCompound>("lims-compounds");
