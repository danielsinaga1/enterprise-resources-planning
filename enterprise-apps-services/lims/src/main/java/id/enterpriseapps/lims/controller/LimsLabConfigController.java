package id.enterpriseapps.lims.controller;

import id.enterpriseapps.lims.service.LimsLabConfigService;
import id.enterpriseapps.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lims/v1/config")
@RequiredArgsConstructor
@Tag(name = "LIMS - Lab Configuration", description = "Master data and dependent dropdown support for lab configuration")
public class LimsLabConfigController {

    private final LimsLabConfigService labConfigService;

    @GetMapping("/dependencies")
    @Operation(summary = "Get cascading dependent dropdown data (Parameter → TestType → Method → Standard → Unit)")
    public ResponseEntity<Response> getDependencies(
            @RequestParam(required = false) Long parameterId,
            @RequestParam(required = false) Long testTypeId,
            @RequestParam(required = false) Long methodId,
            @RequestParam(required = false) Long standardId) {

        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(labConfigService.getDependencies(parameterId, testTypeId, methodId, standardId))
                .build());
    }

    @GetMapping("/master-data")
    @Operation(summary = "Get all master data (parameters, test types, methods, standards, units, departments, results)")
    public ResponseEntity<Response> getAllMasterData() {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(labConfigService.getAllMasterData())
                .build());
    }
}

