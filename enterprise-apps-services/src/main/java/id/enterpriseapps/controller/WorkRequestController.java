package id.enterpriseapps.controller;

import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.WorkRequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Work Requests", description = "Read-only search against Avantis (kpi_wr_irms) for the WR autocomplete on incident/JSO forms")
@RestController
@RequestMapping("/api/v1/work-requests")
@RequiredArgsConstructor
public class WorkRequestController {

    private final WorkRequestService workRequestService;

    @GetMapping("/search")
    public ResponseEntity<Response> search(@RequestParam(value = "q", required = false, defaultValue = "") String q) {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(workRequestService.search(q))
                .build());
    }

    /**
     * Exact lookup for one WR number. The form uses this to fill the WR info card for a number
     * that was typed by hand or loaded from a saved record, where a LIKE search capped at 50 rows
     * can miss the row it is looking for. Unknown number -> 200 with a null payload, matching the
     * fail-soft contract of the search endpoint (Avantis is not ours to hard-fail on).
     */
    @GetMapping("/{number}")
    public ResponseEntity<Response> findByNumber(@PathVariable("number") String number) {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(workRequestService.findByNumber(number))
                .build());
    }
}
