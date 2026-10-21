package id.enterpriseapps.controller;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.entity.*;
import id.enterpriseapps.repository.*;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.AuthJobTitleSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Master Data", description = "CRUD for IRMS master/lookup data")
@RestController
@RequestMapping("/api/v1/master")
public class MasterDataController {

    @Autowired private AreaIncidentRepository areaRepo;
    @Autowired private CategoryIncidentRepository categoryRepo;
    @Autowired private ClassificationIncidentRepository classificationRepo;
    @Autowired private RootCauseRepository rootCauseRepo;
    @Autowired private RegionIncidentRepository regionRepo;
    @Autowired private OriginDepartmentRepository originDeptRepo;
    @Autowired private DesignationDepartmentRepository designationDeptRepo;
    @Autowired private JsoLeaderMasterRepository jsoLeaderRepo;
    @Autowired private JsoMemberMasterRepository jsoMemberRepo;
    @Autowired private JobTitleRepository jobTitleRepo;
    @Autowired private AuthJobTitleSyncService authJobTitleSyncService;
    @Autowired private JsoLocationRepository jsoLocationRepo;
    @Autowired private DivisionRepository divisionRepo;
    @Autowired private ChartOfAccountRepository coaRepo;

    // ===================== AREA INCIDENTS =====================

    @Operation(summary = "List all areas")
    @GetMapping("/areas")
    public ResponseEntity<Response> listAreas() {
        return ok(areaRepo.findAll());
    }

    @Operation(summary = "Create area")
    @PostMapping("/areas")
    public ResponseEntity<Response> createArea(@RequestBody AreaIncidentEntity body) {
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        return created(areaRepo.save(body));
    }

    @Operation(summary = "Update area")
    @PutMapping("/areas/{id}")
    public ResponseEntity<Response> updateArea(@PathVariable Long id, @RequestBody AreaIncidentEntity body) {
        Optional<AreaIncidentEntity> opt = areaRepo.findById(id);
        if (opt.isEmpty()) return notFound("Area not found");
        AreaIncidentEntity entity = opt.get();
        entity.setName(body.getName());
        entity.setUpdatedAt(LocalDateTime.now());
        return ok(areaRepo.save(entity));
    }

    @Operation(summary = "Delete area")
    @DeleteMapping("/areas/{id}")
    public ResponseEntity<Response> deleteArea(@PathVariable Long id) {
        if (!areaRepo.existsById(id)) return notFound("Area not found");
        areaRepo.deleteById(id);
        return deleted();
    }

    // ===================== CATEGORY INCIDENTS =====================

    @Operation(summary = "List all categories")
    @GetMapping("/categories")
    public ResponseEntity<Response> listCategories() {
        return ok(categoryRepo.findAll());
    }

    @Operation(summary = "Create category")
    @PostMapping("/categories")
    public ResponseEntity<Response> createCategory(@RequestBody CategoryIncidentEntity body) {
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        return created(categoryRepo.save(body));
    }

    @Operation(summary = "Update category")
    @PutMapping("/categories/{id}")
    public ResponseEntity<Response> updateCategory(@PathVariable Long id, @RequestBody CategoryIncidentEntity body) {
        Optional<CategoryIncidentEntity> opt = categoryRepo.findById(id);
        if (opt.isEmpty()) return notFound("Category not found");
        CategoryIncidentEntity entity = opt.get();
        entity.setName(body.getName());
        entity.setDescription(body.getDescription());
        entity.setUpdatedAt(LocalDateTime.now());
        return ok(categoryRepo.save(entity));
    }

    @Operation(summary = "Delete category")
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Response> deleteCategory(@PathVariable Long id) {
        if (!categoryRepo.existsById(id)) return notFound("Category not found");
        categoryRepo.deleteById(id);
        return deleted();
    }

    // ===================== CLASSIFICATION INCIDENTS =====================

    @Operation(summary = "List all classifications")
    @GetMapping("/classifications")
    public ResponseEntity<Response> listClassifications() {
        return ok(classificationRepo.findAll());
    }

    @Operation(summary = "Create classification")
    @PostMapping("/classifications")
    public ResponseEntity<Response> createClassification(@RequestBody ClassificationIncidentEntity body) {
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        return created(classificationRepo.save(body));
    }

    @Operation(summary = "Update classification")
    @PutMapping("/classifications/{id}")
    public ResponseEntity<Response> updateClassification(@PathVariable Long id, @RequestBody ClassificationIncidentEntity body) {
        Optional<ClassificationIncidentEntity> opt = classificationRepo.findById(id);
        if (opt.isEmpty()) return notFound("Classification not found");
        ClassificationIncidentEntity entity = opt.get();
        entity.setName(body.getName());
        entity.setLevel(body.getLevel());
        entity.setColor(body.getColor());
        entity.setUpdatedAt(LocalDateTime.now());
        return ok(classificationRepo.save(entity));
    }

    @Operation(summary = "Delete classification")
    @DeleteMapping("/classifications/{id}")
    public ResponseEntity<Response> deleteClassification(@PathVariable Long id) {
        if (!classificationRepo.existsById(id)) return notFound("Classification not found");
        classificationRepo.deleteById(id);
        return deleted();
    }

    // ===================== ROOT CAUSES =====================

    @Operation(summary = "List all root causes")
    @GetMapping("/root-causes")
    public ResponseEntity<Response> listRootCauses() {
        return ok(rootCauseRepo.findAll());
    }

    @Operation(summary = "Create root cause")
    @PostMapping("/root-causes")
    public ResponseEntity<Response> createRootCause(@RequestBody RootCauseEntity body) {
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        return created(rootCauseRepo.save(body));
    }

    @Operation(summary = "Update root cause")
    @PutMapping("/root-causes/{id}")
    public ResponseEntity<Response> updateRootCause(@PathVariable Long id, @RequestBody RootCauseEntity body) {
        Optional<RootCauseEntity> opt = rootCauseRepo.findById(id);
        if (opt.isEmpty()) return notFound("Root cause not found");
        RootCauseEntity entity = opt.get();
        entity.setName(body.getName());
        entity.setUpdatedAt(LocalDateTime.now());
        return ok(rootCauseRepo.save(entity));
    }

    @Operation(summary = "Delete root cause")
    @DeleteMapping("/root-causes/{id}")
    public ResponseEntity<Response> deleteRootCause(@PathVariable Long id) {
        if (!rootCauseRepo.existsById(id)) return notFound("Root cause not found");
        rootCauseRepo.deleteById(id);
        return deleted();
    }

    // ===================== REGION INCIDENTS =====================

    @Operation(summary = "List all regions")
    @GetMapping("/regions")
    public ResponseEntity<Response> listRegions() {
        return ok(regionRepo.findAll());
    }

    @Operation(summary = "Create region")
    @PostMapping("/regions")
    public ResponseEntity<Response> createRegion(@RequestBody RegionIncidentEntity body) {
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        return created(regionRepo.save(body));
    }

    @Operation(summary = "Update region")
    @PutMapping("/regions/{id}")
    public ResponseEntity<Response> updateRegion(@PathVariable Long id, @RequestBody RegionIncidentEntity body) {
        Optional<RegionIncidentEntity> opt = regionRepo.findById(id);
        if (opt.isEmpty()) return notFound("Region not found");
        RegionIncidentEntity entity = opt.get();
        entity.setName(body.getName());
        entity.setUpdatedAt(LocalDateTime.now());
        return ok(regionRepo.save(entity));
    }

    @Operation(summary = "Delete region")
    @DeleteMapping("/regions/{id}")
    public ResponseEntity<Response> deleteRegion(@PathVariable Long id) {
        if (!regionRepo.existsById(id)) return notFound("Region not found");
        regionRepo.deleteById(id);
        return deleted();
    }

    // ===================== ORIGIN DEPARTMENTS =====================

    @Operation(summary = "List all origin departments")
    @GetMapping("/origin-departments")
    public ResponseEntity<Response> listOriginDepartments() {
        return ok(originDeptRepo.findAll());
    }

    @Operation(summary = "Create origin department")
    @PostMapping("/origin-departments")
    public ResponseEntity<Response> createOriginDepartment(@RequestBody OriginDepartmentEntity body) {
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        return created(originDeptRepo.save(body));
    }

    @Operation(summary = "Update origin department")
    @PutMapping("/origin-departments/{id}")
    public ResponseEntity<Response> updateOriginDepartment(@PathVariable Long id, @RequestBody OriginDepartmentEntity body) {
        Optional<OriginDepartmentEntity> opt = originDeptRepo.findById(id);
        if (opt.isEmpty()) return notFound("Origin department not found");
        OriginDepartmentEntity entity = opt.get();
        entity.setName(body.getName());
        entity.setCode(body.getCode());
        entity.setUpdatedAt(LocalDateTime.now());
        return ok(originDeptRepo.save(entity));
    }

    @Operation(summary = "Delete origin department")
    @DeleteMapping("/origin-departments/{id}")
    public ResponseEntity<Response> deleteOriginDepartment(@PathVariable Long id) {
        if (!originDeptRepo.existsById(id)) return notFound("Origin department not found");
        originDeptRepo.deleteById(id);
        return deleted();
    }

    // ===================== DESIGNATION DEPARTMENTS =====================

    @Operation(summary = "List all designation departments")
    @GetMapping("/designation-departments")
    public ResponseEntity<Response> listDesignationDepartments() {
        return ok(designationDeptRepo.findAll());
    }

    @Operation(summary = "Create designation department")
    @PostMapping("/designation-departments")
    public ResponseEntity<Response> createDesignationDepartment(@RequestBody DesignationDepartmentEntity body) {
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        return created(designationDeptRepo.save(body));
    }

    @Operation(summary = "Update designation department")
    @PutMapping("/designation-departments/{id}")
    public ResponseEntity<Response> updateDesignationDepartment(@PathVariable Long id, @RequestBody DesignationDepartmentEntity body) {
        Optional<DesignationDepartmentEntity> opt = designationDeptRepo.findById(id);
        if (opt.isEmpty()) return notFound("Designation department not found");
        DesignationDepartmentEntity entity = opt.get();
        entity.setName(body.getName());
        entity.setCode(body.getCode());
        entity.setUpdatedAt(LocalDateTime.now());
        return ok(designationDeptRepo.save(entity));
    }

    @Operation(summary = "Delete designation department")
    @DeleteMapping("/designation-departments/{id}")
    public ResponseEntity<Response> deleteDesignationDepartment(@PathVariable Long id) {
        if (!designationDeptRepo.existsById(id)) return notFound("Designation department not found");
        designationDeptRepo.deleteById(id);
        return deleted();
    }

    // ===================== JSO LEADERS =====================

    @Operation(summary = "List all JSO leaders")
    @GetMapping("/jso-leaders")
    public ResponseEntity<Response> listJsoLeaders() {
        return ok(jsoLeaderRepo.findAll());
    }

    @Operation(summary = "Create JSO leader")
    @PostMapping("/jso-leaders")
    public ResponseEntity<Response> createJsoLeader(@RequestBody JsoLeaderMasterEntity body) {
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        return created(jsoLeaderRepo.save(body));
    }

    @Operation(summary = "Update JSO leader")
    @PutMapping("/jso-leaders/{id}")
    public ResponseEntity<Response> updateJsoLeader(@PathVariable Long id, @RequestBody JsoLeaderMasterEntity body) {
        Optional<JsoLeaderMasterEntity> opt = jsoLeaderRepo.findById(id);
        if (opt.isEmpty()) return notFound("JSO Leader not found");
        JsoLeaderMasterEntity entity = opt.get();
        entity.setName(body.getName());
        entity.setNpp(body.getNpp());
        entity.setDepartment(body.getDepartment());
        entity.setPosition(body.getPosition());
        entity.setIsActive(body.getIsActive());
        entity.setUpdatedAt(LocalDateTime.now());
        return ok(jsoLeaderRepo.save(entity));
    }

    @Operation(summary = "Delete JSO leader")
    @DeleteMapping("/jso-leaders/{id}")
    public ResponseEntity<Response> deleteJsoLeader(@PathVariable Long id) {
        if (!jsoLeaderRepo.existsById(id)) return notFound("JSO Leader not found");
        jsoLeaderRepo.deleteById(id);
        return deleted();
    }

    // ===================== JSO MEMBERS =====================

    @Operation(summary = "List all JSO members")
    @GetMapping("/jso-members")
    public ResponseEntity<Response> listJsoMembers() {
        return ok(jsoMemberRepo.findAll());
    }

    @Operation(summary = "Create JSO member")
    @PostMapping("/jso-members")
    public ResponseEntity<Response> createJsoMember(@RequestBody JsoMemberMasterEntity body) {
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        return created(jsoMemberRepo.save(body));
    }

    @Operation(summary = "Update JSO member")
    @PutMapping("/jso-members/{id}")
    public ResponseEntity<Response> updateJsoMember(@PathVariable Long id, @RequestBody JsoMemberMasterEntity body) {
        Optional<JsoMemberMasterEntity> opt = jsoMemberRepo.findById(id);
        if (opt.isEmpty()) return notFound("JSO Member not found");
        JsoMemberMasterEntity entity = opt.get();
        entity.setName(body.getName());
        entity.setNpp(body.getNpp());
        entity.setDepartment(body.getDepartment());
        entity.setPosition(body.getPosition());
        entity.setIsActive(body.getIsActive());
        entity.setUpdatedAt(LocalDateTime.now());
        return ok(jsoMemberRepo.save(entity));
    }

    @Operation(summary = "Delete JSO member")
    @DeleteMapping("/jso-members/{id}")
    public ResponseEntity<Response> deleteJsoMember(@PathVariable Long id) {
        if (!jsoMemberRepo.existsById(id)) return notFound("JSO Member not found");
        jsoMemberRepo.deleteById(id);
        return deleted();
    }

    // ===================== JSO LOCATIONS =====================

    @Operation(summary = "List all JSO locations")
    @GetMapping("/jso-locations")
    public ResponseEntity<Response> listJsoLocations() {
        return ok(jsoLocationRepo.findAll());
    }

    @Operation(summary = "Create JSO location")
    @PostMapping("/jso-locations")
    public ResponseEntity<Response> createJsoLocation(@RequestBody JsoLocationEntity body) {
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        return created(jsoLocationRepo.save(body));
    }

    @Operation(summary = "Update JSO location")
    @PutMapping("/jso-locations/{id}")
    public ResponseEntity<Response> updateJsoLocation(@PathVariable Long id, @RequestBody JsoLocationEntity body) {
        Optional<JsoLocationEntity> opt = jsoLocationRepo.findById(id);
        if (opt.isEmpty()) return notFound("JSO Location not found");
        JsoLocationEntity entity = opt.get();
        entity.setName(body.getName());
        entity.setUpdatedAt(LocalDateTime.now());
        return ok(jsoLocationRepo.save(entity));
    }

    @Operation(summary = "Delete JSO location")
    @DeleteMapping("/jso-locations/{id}")
    public ResponseEntity<Response> deleteJsoLocation(@PathVariable Long id) {
        if (!jsoLocationRepo.existsById(id)) return notFound("JSO Location not found");
        jsoLocationRepo.deleteById(id);
        return deleted();
    }

    // ===================== JOB TITLES =====================

    @Operation(summary = "List all job titles")
    @GetMapping("/job-titles")
    public ResponseEntity<Response> listJobTitles() {
        return ok(jobTitleRepo.findAll());
    }

    @Operation(summary = "Create job title")
    @PostMapping("/job-titles")
    public ResponseEntity<Response> createJobTitle(@RequestBody JobTitleEntity body) {
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        JobTitleEntity saved = jobTitleRepo.save(body);
        // Mirror into auth_job_titles (db_auth) — auth_users.job_id FKs there,
        // not into irms_job_titles, so new titles must exist in both.
        authJobTitleSyncService.sync(saved.getId(), saved.getName());
        return created(saved);
    }

    @Operation(summary = "Update job title")
    @PutMapping("/job-titles/{id}")
    public ResponseEntity<Response> updateJobTitle(@PathVariable Long id, @RequestBody JobTitleEntity body) {
        Optional<JobTitleEntity> opt = jobTitleRepo.findById(id);
        if (opt.isEmpty()) return notFound("Job title not found");
        JobTitleEntity entity = opt.get();
        entity.setName(body.getName());
        entity.setUpdatedAt(LocalDateTime.now());
        JobTitleEntity saved = jobTitleRepo.save(entity);
        authJobTitleSyncService.sync(saved.getId(), saved.getName());
        return ok(saved);
    }

    @Operation(summary = "Delete job title")
    @DeleteMapping("/job-titles/{id}")
    public ResponseEntity<Response> deleteJobTitle(@PathVariable Long id) {
        if (!jobTitleRepo.existsById(id)) return notFound("Job title not found");
        jobTitleRepo.deleteById(id);
        authJobTitleSyncService.delete(id);
        return deleted();
    }

    // ===================== DIVISIONS =====================

    @Operation(summary = "List all divisions")
    @GetMapping("/divisions")
    public ResponseEntity<Response> listDivisions() {
        return ok(divisionRepo.findByDeletedAtIsNull());
    }

    @Operation(summary = "Create division")
    @PostMapping("/divisions")
    public ResponseEntity<Response> createDivision(@RequestBody DivisionEntity body) {
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        return created(divisionRepo.save(body));
    }

    @Operation(summary = "Update division")
    @PutMapping("/divisions/{id}")
    public ResponseEntity<Response> updateDivision(@PathVariable Long id, @RequestBody DivisionEntity body) {
        Optional<DivisionEntity> opt = divisionRepo.findById(id);
        if (opt.isEmpty()) return notFound("Division not found");
        DivisionEntity entity = opt.get();
        entity.setName(body.getName());
        if (body.getCode() != null) entity.setCode(body.getCode());
        entity.setUpdatedAt(LocalDateTime.now());
        return ok(divisionRepo.save(entity));
    }

    @Operation(summary = "Delete division")
    @DeleteMapping("/divisions/{id}")
    public ResponseEntity<Response> deleteDivision(@PathVariable Long id) {
        if (!divisionRepo.existsById(id)) return notFound("Division not found");
        divisionRepo.deleteById(id);
        return deleted();
    }

    // ===================== CHART OF ACCOUNTS (COA) =====================

    @Operation(summary = "List all chart of accounts")
    @GetMapping("/coa")
    public ResponseEntity<Response> listCoa() {
        return ok(coaRepo.findAllByOrderByAccountCodeAsc());
    }

    @Operation(summary = "Create chart of account")
    @PostMapping("/coa")
    public ResponseEntity<Response> createCoa(@RequestBody ChartOfAccountEntity body) {
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        return created(coaRepo.save(body));
    }

    @Operation(summary = "Update chart of account")
    @PutMapping("/coa/{id}")
    public ResponseEntity<Response> updateCoa(@PathVariable Long id, @RequestBody ChartOfAccountEntity body) {
        Optional<ChartOfAccountEntity> opt = coaRepo.findById(id);
        if (opt.isEmpty()) return notFound("Chart of Account not found");
        ChartOfAccountEntity entity = opt.get();
        entity.setAccountCode(body.getAccountCode());
        entity.setAccountName(body.getAccountName());
        entity.setAccountType(body.getAccountType());
        entity.setParentId(body.getParentId());
        entity.setDescription(body.getDescription());
        entity.setIsActive(body.getIsActive());
        entity.setUpdatedAt(LocalDateTime.now());
        return ok(coaRepo.save(entity));
    }

    @Operation(summary = "Delete chart of account")
    @DeleteMapping("/coa/{id}")
    public ResponseEntity<Response> deleteCoa(@PathVariable Long id) {
        if (!coaRepo.existsById(id)) return notFound("Chart of Account not found");
        coaRepo.deleteById(id);
        return deleted();
    }

    // ===================== HELPERS =====================

    private ResponseEntity<Response> ok(Object data) {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(data)
                .build());
    }

    private ResponseEntity<Response> created(Object data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.CREATED)
                .data(data)
                .build());
    }

    private ResponseEntity<Response> deleted() {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .message("Deleted successfully")
                .build());
    }

    private ResponseEntity<Response> notFound(String msg) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Response.builder()
                .status(StatusConstant.FAILED)
                .httpStatus(HttpStatus.NOT_FOUND)
                .errorMessage(msg)
                .build());
    }
}

