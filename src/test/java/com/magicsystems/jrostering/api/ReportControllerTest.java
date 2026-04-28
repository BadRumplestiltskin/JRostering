package com.magicsystems.jrostering.api;

import com.magicsystems.jrostering.report.ExcelReportGenerator;
import com.magicsystems.jrostering.service.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = ReportController.class, excludeAutoConfiguration = {com.magicsystems.jrostering.JpaAuditingConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class ReportControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  ExcelReportGenerator reportGenerator;

    @Test
    void staffHoursReport_returns200WithXlsxAttachment() throws Exception {
        byte[] fakeXlsx = new byte[]{0x50, 0x4B, 0x03, 0x04};
        when(reportGenerator.generateHoursReport(1L)).thenReturn(fakeXlsx);

        mockMvc.perform(get("/api/reports/staff-hours/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("hours-period-1.xlsx")))
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void staffHoursReport_returns404_whenPeriodNotFound() throws Exception {
        when(reportGenerator.generateHoursReport(99L))
                .thenThrow(EntityNotFoundException.of("RosterPeriod", 99L));

        mockMvc.perform(get("/api/reports/staff-hours/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void violationReport_returns200WithXlsxAttachment() throws Exception {
        byte[] fakeXlsx = new byte[]{0x50, 0x4B, 0x03, 0x04};
        when(reportGenerator.generateViolationSummaryReport(42L)).thenReturn(fakeXlsx);

        mockMvc.perform(get("/api/reports/violations/42"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("violations-job-42.xlsx")));
    }

    @Test
    void violationReport_returns404_whenJobNotFound() throws Exception {
        when(reportGenerator.generateViolationSummaryReport(99L))
                .thenThrow(EntityNotFoundException.of("SolverJob", 99L));

        mockMvc.perform(get("/api/reports/violations/99"))
                .andExpect(status().isNotFound());
    }
}
