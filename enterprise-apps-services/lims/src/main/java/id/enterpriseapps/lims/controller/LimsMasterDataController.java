package id.enterpriseapps.lims.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.integration.pubchem.PubChemClient;
import id.enterpriseapps.lims.entity.*;
import id.enterpriseapps.lims.repository.*;
import id.enterpriseapps.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "LIMS Master Data", description = "CRUD for LIMS master/lookup data")
@RestController
@RequestMapping("/api/v1/master")
@RequiredArgsConstructor
public class LimsMasterDataController {

    private final LimsParameterRepository parameterRepo;
    private final LimsTestTypeRepository testTypeRepo;
    private final LimsMethodRepository methodRepo;
    private final LimsStandardRepository standardRepo;
    private final LimsUnitRepository unitRepo;
    private final LimsDepartmentRepository departmentRepo;
    private final LimsResultRepository resultRepo;
    private final LimsChemicalCompoundRepository compoundRepo;
    private final PubChemClient pubChemClient;

    // ===================== LIMS PARAMETERS =====================

    @Operation(summary = "List all lab parameters")
    @GetMapping("/lims-parameters")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> listParameters() {
        return ok(parameterRepo.findAllByDeletedAtIsNull());
    }

    @Operation(summary = "Create lab parameter")
    @PostMapping("/lims-parameters")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> createParameter(@RequestBody LimsParameter body) {
        return created(parameterRepo.save(body));
    }

    @Operation(summary = "Update lab parameter")
    @PutMapping("/lims-parameters/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> updateParameter(@PathVariable Long id, @RequestBody LimsParameter body) {
        Optional<LimsParameter> opt = parameterRepo.findById(id);
        if (opt.isEmpty()) return notFound("Parameter not found");
        LimsParameter entity = opt.get();
        entity.setName(body.getName());
        return ok(parameterRepo.save(entity));
    }

    @Operation(summary = "Delete lab parameter")
    @DeleteMapping("/lims-parameters/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> deleteParameter(@PathVariable Long id) {
        Optional<LimsParameter> opt = parameterRepo.findById(id);
        if (opt.isEmpty()) return notFound("Parameter not found");
        opt.get().setDeletedAt(LocalDateTime.now());
        parameterRepo.save(opt.get());
        return deleted();
    }

    // ===================== LIMS TEST TYPES =====================

    @Operation(summary = "List all lab test types")
    @GetMapping("/lims-test-types")
    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> listTestTypes() {
        List<Map<String, Object>> result = testTypeRepo.findAllByDeletedAtIsNull().stream()
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("name", t.getName());
                    m.put("parameterId", t.getParameter() != null ? t.getParameter().getId() : null);
                    m.put("parameterName", t.getParameter() != null ? t.getParameter().getName() : null);
                    return m;
                }).collect(Collectors.toList());
        return ok(result);
    }

    @Operation(summary = "Create lab test type")
    @PostMapping("/lims-test-types")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> createTestType(@RequestBody Map<String, Object> body) {
        LimsTestType entity = new LimsTestType();
        entity.setName((String) body.get("name"));
        if (body.get("parameterId") != null) {
            Long paramId = ((Number) body.get("parameterId")).longValue();
            parameterRepo.findById(paramId).ifPresent(entity::setParameter);
        }
        return created(testTypeRepo.save(entity));
    }

    @Operation(summary = "Update lab test type")
    @PutMapping("/lims-test-types/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> updateTestType(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<LimsTestType> opt = testTypeRepo.findById(id);
        if (opt.isEmpty()) return notFound("Test type not found");
        LimsTestType entity = opt.get();
        entity.setName((String) body.get("name"));
        if (body.containsKey("parameterId")) {
            if (body.get("parameterId") != null) {
                Long paramId = ((Number) body.get("parameterId")).longValue();
                parameterRepo.findById(paramId).ifPresent(entity::setParameter);
            } else {
                entity.setParameter(null);
            }
        }
        return ok(testTypeRepo.save(entity));
    }

    @Operation(summary = "Delete lab test type")
    @DeleteMapping("/lims-test-types/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> deleteTestType(@PathVariable Long id) {
        Optional<LimsTestType> opt = testTypeRepo.findById(id);
        if (opt.isEmpty()) return notFound("Test type not found");
        opt.get().setDeletedAt(LocalDateTime.now());
        testTypeRepo.save(opt.get());
        return deleted();
    }

    // ===================== LIMS METHODS =====================

    @Operation(summary = "List all lab methods")
    @GetMapping("/lims-methods")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> listMethods() {
        List<Map<String, Object>> result = methodRepo.findAllByDeletedAtIsNull().stream()
                .map(m -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", m.getId());
                    map.put("name", m.getName());
                    return map;
                }).collect(Collectors.toList());
        return ok(result);
    }

    @Operation(summary = "Create lab method")
    @PostMapping("/lims-methods")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> createMethod(@RequestBody LimsMethod body) {
        return created(methodRepo.save(body));
    }

    @Operation(summary = "Update lab method")
    @PutMapping("/lims-methods/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> updateMethod(@PathVariable Long id, @RequestBody LimsMethod body) {
        Optional<LimsMethod> opt = methodRepo.findById(id);
        if (opt.isEmpty()) return notFound("Method not found");
        LimsMethod entity = opt.get();
        entity.setName(body.getName());
        return ok(methodRepo.save(entity));
    }

    @Operation(summary = "Delete lab method")
    @DeleteMapping("/lims-methods/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> deleteMethod(@PathVariable Long id) {
        Optional<LimsMethod> opt = methodRepo.findById(id);
        if (opt.isEmpty()) return notFound("Method not found");
        opt.get().setDeletedAt(LocalDateTime.now());
        methodRepo.save(opt.get());
        return deleted();
    }

    // ===================== LIMS STANDARDS =====================

    @Operation(summary = "List all lab standards")
    @GetMapping("/lims-standards")
    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> listStandards() {
        List<Map<String, Object>> result = standardRepo.findAllByDeletedAtIsNull().stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("measure", s.getMeasure());
                    m.put("limitType", s.getLimitType() != null ? s.getLimitType().name() : LimsLimitType.QUALITATIVE.name());
                    m.put("lowerLimit", s.getLowerLimit());
                    m.put("upperLimit", s.getUpperLimit());
                    m.put("targetValue", s.getTargetValue());
                    m.put("decimalPlaces", s.getDecimalPlaces());
                    m.put("methodId", s.getMethod() != null ? s.getMethod().getId() : null);
                    m.put("methodName", s.getMethod() != null ? s.getMethod().getName() : null);
                    return m;
                }).collect(Collectors.toList());
        return ok(result);
    }

    @Operation(summary = "Create lab standard")
    @PostMapping("/lims-standards")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> createStandard(@RequestBody Map<String, Object> body) {
        LimsStandard entity = new LimsStandard();
        entity.setMeasure((String) body.get("measure"));
        applyLimits(entity, body);
        if (body.get("methodId") != null) {
            Long methodId = ((Number) body.get("methodId")).longValue();
            methodRepo.findById(methodId).ifPresent(entity::setMethod);
        }
        return created(standardRepo.save(entity));
    }

    @Operation(summary = "Update lab standard")
    @PutMapping("/lims-standards/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> updateStandard(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<LimsStandard> opt = standardRepo.findById(id);
        if (opt.isEmpty()) return notFound("Standard not found");
        LimsStandard entity = opt.get();
        entity.setMeasure((String) body.get("measure"));
        applyLimits(entity, body);
        if (body.containsKey("methodId")) {
            if (body.get("methodId") != null) {
                Long methodId = ((Number) body.get("methodId")).longValue();
                methodRepo.findById(methodId).ifPresent(entity::setMethod);
            } else {
                entity.setMethod(null);
            }
        }
        return ok(standardRepo.save(entity));
    }

    @Operation(summary = "Delete lab standard")
    @DeleteMapping("/lims-standards/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> deleteStandard(@PathVariable Long id) {
        Optional<LimsStandard> opt = standardRepo.findById(id);
        if (opt.isEmpty()) return notFound("Standard not found");
        opt.get().setDeletedAt(LocalDateTime.now());
        standardRepo.save(opt.get());
        return deleted();
    }

    // ===================== LIMS UNITS =====================

    @Operation(summary = "List all lab units")
    @GetMapping("/lims-units")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> listUnits() {
        return ok(unitRepo.findAllByDeletedAtIsNull());
    }

    @Operation(summary = "Create lab unit")
    @PostMapping("/lims-units")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> createUnit(@RequestBody LimsUnit body) {
        return created(unitRepo.save(body));
    }

    @Operation(summary = "Update lab unit")
    @PutMapping("/lims-units/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> updateUnit(@PathVariable Long id, @RequestBody LimsUnit body) {
        Optional<LimsUnit> opt = unitRepo.findById(id);
        if (opt.isEmpty()) return notFound("Unit not found");
        LimsUnit entity = opt.get();
        entity.setName(body.getName());
        return ok(unitRepo.save(entity));
    }

    @Operation(summary = "Delete lab unit")
    @DeleteMapping("/lims-units/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> deleteUnit(@PathVariable Long id) {
        Optional<LimsUnit> opt = unitRepo.findById(id);
        if (opt.isEmpty()) return notFound("Unit not found");
        opt.get().setDeletedAt(LocalDateTime.now());
        unitRepo.save(opt.get());
        return deleted();
    }

    // ===================== LIMS DEPARTMENTS =====================

    @Operation(summary = "List all lab departments")
    @GetMapping("/lims-departments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> listDepartments() {
        return ok(departmentRepo.findAllByDeletedAtIsNull());
    }

    @Operation(summary = "Create lab department")
    @PostMapping("/lims-departments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> createDepartment(@RequestBody LimsDepartment body) {
        return created(departmentRepo.save(body));
    }

    @Operation(summary = "Update lab department")
    @PutMapping("/lims-departments/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> updateDepartment(@PathVariable Long id, @RequestBody LimsDepartment body) {
        Optional<LimsDepartment> opt = departmentRepo.findById(id);
        if (opt.isEmpty()) return notFound("Department not found");
        LimsDepartment entity = opt.get();
        entity.setName(body.getName());
        return ok(departmentRepo.save(entity));
    }

    @Operation(summary = "Delete lab department")
    @DeleteMapping("/lims-departments/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> deleteDepartment(@PathVariable Long id) {
        Optional<LimsDepartment> opt = departmentRepo.findById(id);
        if (opt.isEmpty()) return notFound("Department not found");
        opt.get().setDeletedAt(LocalDateTime.now());
        departmentRepo.save(opt.get());
        return deleted();
    }

    // ===================== LIMS RESULTS =====================

    @Operation(summary = "List all lab results")
    @GetMapping("/lims-results")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> listResults() {
        return ok(resultRepo.findAllByDeletedAtIsNull());
    }

    @Operation(summary = "Create lab result")
    @PostMapping("/lims-results")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> createResult(@RequestBody LimsResult body) {
        return created(resultRepo.save(body));
    }

    @Operation(summary = "Update lab result")
    @PutMapping("/lims-results/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> updateResult(@PathVariable Long id, @RequestBody LimsResult body) {
        Optional<LimsResult> opt = resultRepo.findById(id);
        if (opt.isEmpty()) return notFound("Result not found");
        LimsResult entity = opt.get();
        entity.setName(body.getName());
        return ok(resultRepo.save(entity));
    }

    @Operation(summary = "Delete lab result")
    @DeleteMapping("/lims-results/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> deleteResult(@PathVariable Long id) {
        Optional<LimsResult> opt = resultRepo.findById(id);
        if (opt.isEmpty()) return notFound("Result not found");
        opt.get().setDeletedAt(LocalDateTime.now());
        resultRepo.save(opt.get());
        return deleted();
    }

    // ===================== LIMS CHEMICAL COMPOUNDS =====================

    @Operation(summary = "List all chemical compounds")
    @GetMapping("/lims-compounds")
    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> listCompounds() {
        List<Map<String, Object>> result = compoundRepo.findAllByDeletedAtIsNull().stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getName());
                    m.put("formula", c.getFormula());
                    m.put("casNumber", c.getCasNumber());
                    m.put("category", c.getCategory());
                    m.put("description", c.getDescription());
                    m.put("isActive", c.getIsActive());
                    return m;
                }).collect(Collectors.toList());
        return ok(result);
    }

    @Operation(summary = "Search compounds by name")
    @GetMapping("/lims-compounds/search")
    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> searchCompounds(@RequestParam String name) {
        List<Map<String, Object>> result = compoundRepo.findByNameContainingIgnoreCaseAndDeletedAtIsNull(name).stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getName());
                    m.put("formula", c.getFormula());
                    m.put("casNumber", c.getCasNumber());
                    m.put("category", c.getCategory());
                    m.put("isActive", c.getIsActive());
                    return m;
                }).collect(Collectors.toList());
        return ok(result);
    }

    @Operation(summary = "Filter compounds by category")
    @GetMapping("/lims-compounds/category/{category}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> listCompoundsByCategory(@PathVariable String category) {
        List<Map<String, Object>> result = compoundRepo.findByCategoryAndDeletedAtIsNull(category).stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getName());
                    m.put("formula", c.getFormula());
                    m.put("casNumber", c.getCasNumber());
                    m.put("category", c.getCategory());
                    m.put("isActive", c.getIsActive());
                    return m;
                }).collect(Collectors.toList());
        return ok(result);
    }

    @Operation(summary = "Create chemical compound")
    @PostMapping("/lims-compounds")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> createCompound(@RequestBody Map<String, Object> body) {
        LimsChemicalCompound entity = new LimsChemicalCompound();
        entity.setName((String) body.get("name"));
        entity.setFormula((String) body.get("formula"));
        entity.setCasNumber((String) body.get("casNumber"));
        entity.setCategory((String) body.get("category"));
        entity.setDescription((String) body.get("description"));
        if (body.get("isActive") != null) {
            entity.setIsActive((Boolean) body.get("isActive"));
        }
        return created(compoundRepo.save(entity));
    }

    @Operation(summary = "Update chemical compound")
    @PutMapping("/lims-compounds/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> updateCompound(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<LimsChemicalCompound> opt = compoundRepo.findById(id);
        if (opt.isEmpty()) return notFound("Chemical compound not found");
        LimsChemicalCompound entity = opt.get();
        if (body.containsKey("name")) entity.setName((String) body.get("name"));
        if (body.containsKey("formula")) entity.setFormula((String) body.get("formula"));
        if (body.containsKey("casNumber")) entity.setCasNumber((String) body.get("casNumber"));
        if (body.containsKey("category")) entity.setCategory((String) body.get("category"));
        if (body.containsKey("description")) entity.setDescription((String) body.get("description"));
        if (body.containsKey("isActive")) entity.setIsActive((Boolean) body.get("isActive"));
        return ok(compoundRepo.save(entity));
    }

    @Operation(summary = "Delete chemical compound")
    @DeleteMapping("/lims-compounds/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> deleteCompound(@PathVariable Long id) {
        Optional<LimsChemicalCompound> opt = compoundRepo.findById(id);
        if (opt.isEmpty()) return notFound("Chemical compound not found");
        opt.get().setDeletedAt(LocalDateTime.now());
        compoundRepo.save(opt.get());
        return deleted();
    }

    /**
     * Enriches a compound with live PubChem data (molecular weight, IUPAC name, GHS hazard
     * classification). Non-blocking: returns 204 when PubChem has no match or is unreachable.
     */
    @Operation(summary = "Fetch PubChem enrichment data for a chemical compound")
    @GetMapping("/lims-compounds/{id}/pubchem")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> enrichCompound(@PathVariable Long id) {
        Optional<LimsChemicalCompound> opt = compoundRepo.findById(id);
        if (opt.isEmpty()) return notFound("Chemical compound not found");

        LimsChemicalCompound c = opt.get();
        return pubChemClient.enrich(c.getCasNumber(), c.getName())
                .map(data -> ok(data))
                .orElse(ResponseEntity.noContent().build());
    }

    // ===================== HELPERS =====================

    /**
     * Menerapkan batas spesifikasi dari body request ke master standar.
     *
     * <p>Validasinya bukan formalitas: standar RANGE tanpa batas, atau MAX
     * tanpa batas atas, membuat evaluasi OOS diam-diam tidak pernah
     * menyimpulkan apa pun — hasil uji terlihat "aman" padahal tidak pernah
     * dinilai. Lebih baik gagal saat master data disimpan.
     */
    private void applyLimits(LimsStandard entity, Map<String, Object> body) {
        if (body.containsKey("limitType")) {
            entity.setLimitType(parseLimitType(body.get("limitType")));
        } else if (entity.getLimitType() == null) {
            entity.setLimitType(LimsLimitType.QUALITATIVE);
        }
        if (body.containsKey("lowerLimit")) {
            entity.setLowerLimit(toDecimal(body.get("lowerLimit"), "lowerLimit"));
        }
        if (body.containsKey("upperLimit")) {
            entity.setUpperLimit(toDecimal(body.get("upperLimit"), "upperLimit"));
        }
        if (body.containsKey("targetValue")) {
            entity.setTargetValue(toDecimal(body.get("targetValue"), "targetValue"));
        }
        if (body.containsKey("decimalPlaces")) {
            entity.setDecimalPlaces(toDecimalPlaces(body.get("decimalPlaces")));
        }

        LimsLimitType type = entity.getLimitType();
        if (type == LimsLimitType.RANGE && (entity.getLowerLimit() == null || entity.getUpperLimit() == null)) {
            throw new IllegalArgumentException("Standar bertipe RANGE wajib punya batas bawah dan batas atas.");
        }
        if (type == LimsLimitType.MAX && entity.getUpperLimit() == null) {
            throw new IllegalArgumentException("Standar bertipe MAX wajib punya batas atas.");
        }
        if (type == LimsLimitType.MIN && entity.getLowerLimit() == null) {
            throw new IllegalArgumentException("Standar bertipe MIN wajib punya batas bawah.");
        }
        if (entity.getLowerLimit() != null && entity.getUpperLimit() != null
                && entity.getLowerLimit().compareTo(entity.getUpperLimit()) > 0) {
            throw new IllegalArgumentException("Batas bawah tidak boleh lebih besar dari batas atas.");
        }
    }

    private LimsLimitType parseLimitType(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return LimsLimitType.QUALITATIVE;
        }
        try {
            return LimsLimitType.valueOf(String.valueOf(raw).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipe batas tidak dikenal: " + raw
                    + ". Pilihan: QUALITATIVE, RANGE, MAX, MIN.");
        }
    }

    private BigDecimal toDecimal(Object raw, String field) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        if (raw instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        // Master data kerap diisi dengan desimal koma gaya Indonesia.
        String text = String.valueOf(raw).trim().replace(',', '.');
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Nilai " + field + " bukan angka yang sah: " + raw);
        }
    }

    private Integer toDecimalPlaces(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        int value;
        try {
            value = raw instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Jumlah desimal harus berupa bilangan bulat: " + raw);
        }
        if (value < 0 || value > 6) {
            throw new IllegalArgumentException("Jumlah desimal harus antara 0 dan 6.");
        }
        return value;
    }

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

