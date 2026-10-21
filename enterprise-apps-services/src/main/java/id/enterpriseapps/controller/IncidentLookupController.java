package id.enterpriseapps.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.repository.AreaIncidentRepository;
import id.enterpriseapps.repository.CategoryIncidentRepository;
import id.enterpriseapps.repository.ClassificationIncidentRepository;
import id.enterpriseapps.repository.DesignationDepartmentRepository;
import id.enterpriseapps.repository.DivisionRepository;
import id.enterpriseapps.repository.JsoLocationRepository;
import id.enterpriseapps.repository.LocationRepository;
import id.enterpriseapps.repository.OriginDepartmentRepository;
import id.enterpriseapps.repository.RegionIncidentRepository;
import id.enterpriseapps.repository.RootCauseRepository;
import id.enterpriseapps.response.Response;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Incident Lookups", description = "Lookup data for incident report forms")
@RestController
@RequestMapping("/api/v1")
public class IncidentLookupController {

    @Autowired
    private CategoryIncidentRepository categoryIncidentRepository;
    @Autowired
    private ClassificationIncidentRepository classificationIncidentRepository;
    @Autowired
    private RootCauseRepository rootCauseRepository;
    @Autowired
    private AreaIncidentRepository areaIncidentRepository;
    @Autowired
    private RegionIncidentRepository regionIncidentRepository;
    @Autowired
    private OriginDepartmentRepository originDepartmentRepository;
    @Autowired
    private DesignationDepartmentRepository designationDepartmentRepository;
    @Autowired
    private DivisionRepository divisionRepository;
    @Autowired
    private JsoLocationRepository jsoLocationRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private id.enterpriseapps.repository.NonPlantLocationRepository nonPlantLocationRepository;
    @Autowired
    private id.enterpriseapps.repository.IncidentReportRepository incidentReportRepository;

    @GetMapping("/category-incidents")
    public ResponseEntity<Response> getCategories() {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(categoryIncidentRepository.findAll())
                .build());
    }

    @GetMapping("/classification-incidents")
    public ResponseEntity<Response> getClassifications() {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(classificationIncidentRepository.findAll())
                .build());
    }

    @GetMapping("/root-causes")
    public ResponseEntity<Response> getRootCauses() {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(rootCauseRepository.findAll())
                .build());
    }

    @GetMapping("/area-incidents")
    public ResponseEntity<Response> getAreas() {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(areaIncidentRepository.findAll())
                .build());
    }

    @GetMapping("/region-incidents")
    public ResponseEntity<Response> getRegions() {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(regionIncidentRepository.findAll())
                .build());
    }

    @GetMapping("/origin-departments")
    public ResponseEntity<Response> getOriginDepartments() {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(originDepartmentRepository.findAll())
                .build());
    }

    @GetMapping("/designation-departments")
    public ResponseEntity<Response> getDesignationDepartments() {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(designationDepartmentRepository.findAll())
                .build());
    }

    @GetMapping("/jso-locations")
    public ResponseEntity<Response> getJsoLocations() {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(jsoLocationRepository.findAll())
                .build());
    }

    @GetMapping("/divisions")
    public ResponseEntity<Response> getDivisions() {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(divisionRepository.findByDeletedAtIsNull())
                .build());
    }

    @GetMapping("/locations")
    public ResponseEntity<Response> getLocations(@RequestParam(required = false) Long regionId) {
        Object data = (regionId != null)
                ? locationRepository.findByRegionNcrIdAndDeletedAtIsNull(regionId)
                : locationRepository.findByDeletedAtIsNull();
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(data)
                .build());
    }

    @GetMapping("/nonplant-locations")
    public ResponseEntity<Response> getNonPlantLocations() {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(nonPlantLocationRepository.findByDeletedAtIsNullOrderByNameAsc())
                .build());
    }

    @GetMapping("/incident-years")
    public ResponseEntity<Response> getIncidentYears() {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(incidentReportRepository.findDistinctYears())
                .build());
    }
}

