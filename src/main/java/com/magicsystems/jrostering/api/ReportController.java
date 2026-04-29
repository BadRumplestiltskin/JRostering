package com.magicsystems.jrostering.api;

import com.magicsystems.jrostering.report.ExcelReportGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reports", description = "Excel report downloads — staff hours and constraint violations")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExcelReportGenerator reportGenerator;

    @Operation(summary = "Download staff hours report for a roster period as Excel")
    @GetMapping("/staff-hours/{periodId}")
    public ResponseEntity<byte[]> staffHoursReport(@PathVariable Long periodId) {
        byte[] bytes = reportGenerator.generateHoursReport(periodId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"hours-period-" + periodId + ".xlsx\"")
                .contentType(XLSX)
                .body(bytes);
    }

    @Operation(summary = "Download constraint violation report for a solver job as Excel")
    @GetMapping("/violations/{jobId}")
    public ResponseEntity<byte[]> violationReport(@PathVariable Long jobId) {
        byte[] bytes = reportGenerator.generateViolationSummaryReport(jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"violations-job-" + jobId + ".xlsx\"")
                .contentType(XLSX)
                .body(bytes);
    }
}
