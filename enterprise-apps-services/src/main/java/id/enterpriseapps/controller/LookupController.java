package id.enterpriseapps.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import id.enterpriseapps.constant.ErrorCodeConstant;
import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.lookup.LookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Lookup", description = "Lookup data APIs for master data")
@RestController
@RequestMapping("/api/v1/lookup")
public class LookupController {
    @Autowired
    private LookupService lookupService;

    @Operation(summary = "Get lookup data", description = "Retrieve lookup/master data by type")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved lookup data", content = @Content(schema = @Schema(implementation = Response.class))),
            @ApiResponse(responseCode = "400", description = "Invalid lookup type"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<Response> getAllLookup(
            @Parameter(description = "Lookup type (e.g., gender, blood_type, religion)") @RequestParam String type) {
        Response response = new Response();

        try {
            response = lookupService.getAllLookup(type);

            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            response = Response.builder().status(StatusConstant.FAILED).httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error).errorMessage(ErrorCodeConstant.error_message_general)
                    .build();
            return ResponseEntity.status(response.getHttpStatus()).body(response);
        }

    }

}

