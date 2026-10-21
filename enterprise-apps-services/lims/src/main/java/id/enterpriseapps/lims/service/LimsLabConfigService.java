package id.enterpriseapps.lims.service;

import id.enterpriseapps.lims.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "limsTransactionManager", readOnly = true)
public class LimsLabConfigService {

    private final LimsParameterRepository parameterRepository;
    private final LimsTestTypeRepository testTypeRepository;
    private final LimsMethodRepository methodRepository;
    private final LimsStandardRepository standardRepository;
    private final LimsUnitRepository unitRepository;
    private final LimsDepartmentRepository departmentRepository;
    private final LimsResultRepository resultRepository;
    private final LimsChemicalCompoundRepository compoundRepository;

    public Map<String, Object> getDependencies(Long parameterId, Long testTypeId, Long methodId, Long standardId) {
        Map<String, Object> result = new HashMap<>();

        result.put("parameters", parameterRepository.findAllByDeletedAtIsNull().stream()
                .map(p -> Map.of("id", p.getId(), "name", p.getName()))
                .collect(Collectors.toList()));

        if (parameterId != null) {
            result.put("testTypes", testTypeRepository.findByParameterIdAndDeletedAtIsNull(parameterId).stream()
                    .map(t -> Map.of("id", t.getId(), "name", t.getName()))
                    .collect(Collectors.toList()));
        }

        if (testTypeId != null) {
            testTypeRepository.findById(testTypeId).ifPresent(tt -> {
                List<Map<String, Object>> methods = tt.getMethods().stream()
                        .map(m -> Map.<String, Object>of("id", m.getId(), "name", m.getName()))
                        .collect(Collectors.toList());
                result.put("methods", methods);
            });
        }

        if (methodId != null) {
            // Batas spesifikasi ikut dikirim supaya form pengisian hasil bisa
            // menampilkan batas yang berlaku saat analis mengetik angkanya.
            // LinkedHashMap, bukan Map.of: batas boleh null.
            result.put("standards", standardRepository.findByMethodIdAndDeletedAtIsNull(methodId).stream()
                    .map(s -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", s.getId());
                        m.put("measure", s.getMeasure());
                        m.put("limitType", s.getLimitType() != null ? s.getLimitType().name() : "QUALITATIVE");
                        m.put("lowerLimit", s.getLowerLimit());
                        m.put("upperLimit", s.getUpperLimit());
                        m.put("targetValue", s.getTargetValue());
                        m.put("decimalPlaces", s.getDecimalPlaces());
                        return m;
                    })
                    .collect(Collectors.toList()));
        }

        if (standardId != null) {
            standardRepository.findById(standardId).ifPresent(std -> {
                List<Map<String, Object>> units = std.getUnits().stream()
                        .map(u -> Map.<String, Object>of("id", u.getId(), "name", u.getName()))
                        .collect(Collectors.toList());
                result.put("units", units);
            });
        }

        return result;
    }

    public Map<String, Object> getAllMasterData() {
        Map<String, Object> data = new HashMap<>();
        data.put("parameters", parameterRepository.findAllByDeletedAtIsNull());
        data.put("testTypes", testTypeRepository.findAllByDeletedAtIsNull());
        data.put("methods", methodRepository.findAllByDeletedAtIsNull());
        data.put("standards", standardRepository.findAllByDeletedAtIsNull());
        data.put("units", unitRepository.findAllByDeletedAtIsNull());
        data.put("departments", departmentRepository.findAllByDeletedAtIsNull());
        data.put("results", resultRepository.findAllByDeletedAtIsNull());
        data.put("compounds", compoundRepository.findAllByDeletedAtIsNull());
        return data;
    }
}

