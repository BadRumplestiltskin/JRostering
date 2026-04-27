package com.magicsystems.jrostering.api;

import com.magicsystems.jrostering.report.ExcelReportGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for Excel report generation.
 *
 * <p>All endpoints require HTTP Basic authentication and are under {@code /api/reports}.
 * Responses are streamed as {@code .xlsx} attachments.</p>
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExcelReportGenerator reportGenerator;

    /**
     * Downloads a staff hours report for a roster period as an Excel file.
     *
     * @param periodId the roster period to report on
     * @return {@code .xlsx} attachment
     */
    @GetMapping("/staff-hours/{periodId}")
    public ResponseEntity<byte[]> staffHoursReport(@PathVariable Long periodId) {
        byte[] bytes = reportGenerator.generateHoursReport(periodId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"hours-period-" + periodId + ".xlsx\"")
                .contentType(XLSX)
                .body(bytes);
    }

    /**
     * Downloads a constraint violation summary report for a completed solver job.
     *
     * @param jobId the solver job to report on
     * @return {@code .xlsx} attachment
     */
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
