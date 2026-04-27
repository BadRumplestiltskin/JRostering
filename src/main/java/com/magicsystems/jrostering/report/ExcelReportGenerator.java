package com.magicsystems.jrostering.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.RosterPeriodRepository;
import com.magicsystems.jrostering.repository.ShiftAssignmentRepository;
import com.magicsystems.jrostering.repository.SolverJobRepository;
import com.magicsystems.jrostering.service.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates Excel reports for roster periods and solver violation summaries.
 *
 * <p>All reports are returned as {@code byte[]} suitable for direct streaming in
 * a REST response or Vaadin file download. Apache POI XSSF is used to produce
 * {@code .xlsx} files.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExcelReportGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final RosterPeriodRepository  rosterPeriodRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final SolverJobRepository     solverJobRepository;
    private final ObjectMapper            objectMapper;

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Generates a staff hours report for the given roster period.
     *
     * <p>The report contains one row per staff member showing total hours worked
     * during the period and a breakdown of shifts by date.</p>
     *
     * @param rosterPeriodId the period to report on
     * @return {@code .xlsx} bytes ready for download
     * @throws EntityNotFoundException if no such period exists
     * @throws ReportGenerationException if workbook serialisation fails
     */
    @Transactional(readOnly = true)
    public byte[] generateHoursReport(Long rosterPeriodId) {
        RosterPeriod period = rosterPeriodRepository.findById(rosterPeriodId)
                .orElseThrow(() -> EntityNotFoundException.of("RosterPeriod", rosterPeriodId));

        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByRosterPeriod(period)
                .stream()
                .filter(sa -> sa.getStaff() != null)
                .toList();

        log.info("Generating hours report for rosterPeriodId={} assignments={}", rosterPeriodId, assignments.size());

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Hours Report");

            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle totalStyle  = buildTotalStyle(wb);

            // Title row
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Staff Hours Report — " + period.getSite().getName()
                    + " (" + period.getStartDate().format(DATE_FMT)
                    + " – " + period.getEndDate().format(DATE_FMT) + ")");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

            // Header row
            Row header = sheet.createRow(2);
            writeHeaderCell(header, 0, "Staff Member", headerStyle);
            writeHeaderCell(header, 1, "Shifts Worked", headerStyle);
            writeHeaderCell(header, 2, "Total Hours", headerStyle);
            writeHeaderCell(header, 3, "Shift Detail", headerStyle);

            // Group assignments by staff
            Map<Staff, List<ShiftAssignment>> byStaff = assignments.stream()
                    .collect(Collectors.groupingBy(ShiftAssignment::getStaff,
                            TreeMap.comparingByKey(Comparator.comparing(s -> s.getLastName() + s.getFirstName()))));

            int rowIdx = 3;
            double grandTotalHours = 0;

            for (Map.Entry<Staff, List<ShiftAssignment>> entry : byStaff.entrySet()) {
                Staff staff = entry.getKey();
                List<ShiftAssignment> staffAssignments = entry.getValue();

                double totalHours = staffAssignments.stream()
                        .mapToLong(sa -> Duration.between(
                                sa.getShift().getStartDatetime(),
                                sa.getShift().getEndDatetime()).toMinutes())
                        .sum() / 60.0;
                grandTotalHours += totalHours;

                String detail = staffAssignments.stream()
                        .sorted(Comparator.comparing(sa -> sa.getShift().getStartDatetime()))
                        .map(sa -> sa.getShift().getStartDatetime().format(DATETIME_FMT)
                                + "–" + sa.getShift().getEndDatetime().toLocalTime())
                        .collect(Collectors.joining(", "));

                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(staff.getFirstName() + " " + staff.getLastName());
                row.createCell(1).setCellValue(staffAssignments.size());
                row.createCell(2).setCellValue(Math.round(totalHours * 10.0) / 10.0);
                row.createCell(3).setCellValue(detail);
            }

            // Totals row
            Row totals = sheet.createRow(rowIdx + 1);
            Cell totalLabel = totals.createCell(0);
            totalLabel.setCellValue("TOTAL");
            totalLabel.setCellStyle(totalStyle);
            Cell totalHoursCell = totals.createCell(2);
            totalHoursCell.setCellValue(Math.round(grandTotalHours * 10.0) / 10.0);
            totalHoursCell.setCellStyle(totalStyle);

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);
            sheet.setColumnWidth(3, 15000);

            return toBytes(wb);
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to generate hours report", e);
        }
    }

    /**
     * Generates a constraint violation summary report for the given solver job.
     *
     * <p>Deserialises {@code violationDetailJson} from the {@link SolverJob} and
     * produces a ranked table of constraint violations by severity.</p>
     *
     * @param solverJobId the completed or infeasible solver job to report on
     * @return {@code .xlsx} bytes ready for download
     * @throws EntityNotFoundException if no such job exists
     * @throws ReportGenerationException if JSON parsing or workbook serialisation fails
     */
    @Transactional(readOnly = true)
    public byte[] generateViolationSummaryReport(Long solverJobId) {
        SolverJob job = solverJobRepository.findById(solverJobId)
                .orElseThrow(() -> EntityNotFoundException.of("SolverJob", solverJobId));

        List<ViolationEntry> violations = parseViolations(job.getViolationDetailJson());
        violations.sort(Comparator.comparingInt(ViolationEntry::violations).reversed());

        log.info("Generating violation report for jobId={} violations={}", solverJobId, violations.size());

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Violation Summary");

            CellStyle headerStyle = buildHeaderStyle(wb);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue(
                    "Constraint Violation Summary — Job #" + solverJobId
                    + " | Score: " + job.getFinalScore());
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

            Row statusRow = sheet.createRow(1);
            statusRow.createCell(0).setCellValue("Status: " + job.getStatus());

            Row header = sheet.createRow(3);
            writeHeaderCell(header, 0, "Constraint Name", headerStyle);
            writeHeaderCell(header, 1, "Score Impact", headerStyle);
            writeHeaderCell(header, 2, "Violation Count", headerStyle);

            int rowIdx = 4;
            for (ViolationEntry v : violations) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(v.constraintName());
                row.createCell(1).setCellValue(v.score());
                row.createCell(2).setCellValue(v.violations());
            }

            if (violations.isEmpty()) {
                sheet.createRow(rowIdx).createCell(0).setCellValue("No violations recorded.");
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);

            return toBytes(wb);
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to generate violation report", e);
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private record ViolationEntry(String constraintName, String score, int violations) {}

    private List<ViolationEntry> parseViolations(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ViolationEntry>>() {});
        } catch (IOException e) {
            log.warn("Could not parse violationDetailJson — returning empty list: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private static CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private static CellStyle buildTotalStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static void writeHeaderCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static byte[] toBytes(XSSFWorkbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    /** Thrown when report generation fails due to an I/O or serialisation error. */
    public static class ReportGenerationException extends RuntimeException {
        public ReportGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
