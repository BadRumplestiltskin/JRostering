package com.magicsystems.jrostering.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.service.EntityNotFoundException;
import com.magicsystems.jrostering.service.InvalidOperationException;
import com.magicsystems.jrostering.service.StaffService;
import com.magicsystems.jrostering.service.StaffService.StaffCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = StaffController.class, excludeAutoConfiguration = {com.magicsystems.jrostering.JpaAuditingConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class StaffControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  StaffService staffService;

    // =========================================================================
    // Staff CRUD
    // =========================================================================

    @Test
    void getStaff_returns200() throws Exception {
        Staff staff = staff(1L, "Alice", "Smith");
        when(staffService.getById(1L)).thenReturn(staff);

        mockMvc.perform(get("/api/staff/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Alice"));
    }

    @Test
    void getStaff_returns404_whenNotFound() throws Exception {
        when(staffService.getById(99L)).thenThrow(EntityNotFoundException.of("Staff", 99L));

        mockMvc.perform(get("/api/staff/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStaffByOrganisation_returns200WithList() throws Exception {
        when(staffService.getAllActiveByOrganisation(2L))
                .thenReturn(List.of(staff(1L, "Alice", "Smith"), staff(2L, "Bob", "Jones")));

        mockMvc.perform(get("/api/staff/organisations/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void createStaff_returns200() throws Exception {
        Staff staff = staff(5L, "Charlie", "Brown");
        when(staffService.create(eq(2L), any(StaffCreateRequest.class))).thenReturn(staff);

        var req = new StaffCreateRequest("Charlie", "Brown", "charlie@example.com",
                "555-1234", EmploymentType.FULL_TIME, null, null);

        mockMvc.perform(post("/api/staff/organisations/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Charlie"));
    }

    @Test
    void deactivateStaff_returns200() throws Exception {
        Staff staff = staff(1L, "Alice", "Smith");
        staff.setActive(false);
        when(staffService.deactivate(1L)).thenReturn(staff);

        mockMvc.perform(delete("/api/staff/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void createStaff_returns409_whenDuplicateEmail() throws Exception {
        when(staffService.create(eq(2L), any()))
                .thenThrow(new InvalidOperationException("Email already in use"));

        var req = new StaffCreateRequest("Alice", "Smith", "alice@example.com",
                null, EmploymentType.FULL_TIME, null, null);

        mockMvc.perform(post("/api/staff/organisations/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // Qualification management
    // =========================================================================

    @Test
    void addQualification_returns200() throws Exception {
        StaffQualification sq = new StaffQualification();
        sq.setId(10L);
        when(staffService.addQualification(1L, 7L, null)).thenReturn(sq);

        mockMvc.perform(post("/api/staff/1/qualifications/7"))
                .andExpect(status().isOk());
    }

    @Test
    void removeQualification_returns204() throws Exception {
        doNothing().when(staffService).removeQualification(1L, 7L);

        mockMvc.perform(delete("/api/staff/1/qualifications/7"))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // Availability management
    // =========================================================================

    @Test
    void getAvailability_returns200() throws Exception {
        when(staffService.getAvailability(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/staff/1/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void addAvailability_returns200() throws Exception {
        StaffAvailability avail = new StaffAvailability();
        avail.setId(20L);
        when(staffService.addAvailability(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(avail);

        mockMvc.perform(post("/api/staff/1/availability")
                        .param("dayOfWeek", "MONDAY")
                        .param("startTime", "08:00")
                        .param("endTime", "16:00")
                        .param("available", "true"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Leave management
    // =========================================================================

    @Test
    void getLeave_returns200() throws Exception {
        when(staffService.getLeave(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/staff/1/leave"))
                .andExpect(status().isOk());
    }

    @Test
    void addLeave_returns200() throws Exception {
        Leave leave = new Leave();
        leave.setId(30L);
        when(staffService.addLeave(any(), any(), any(), any(), any())).thenReturn(leave);

        mockMvc.perform(post("/api/staff/1/leave")
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-06-07")
                        .param("leaveType", "ANNUAL"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Incompatibility management
    // =========================================================================

    @Test
    void addIncompatibility_returns200() throws Exception {
        StaffIncompatibility inc = new StaffIncompatibility();
        inc.setId(50L);
        when(staffService.addIncompatibility(1L, 2L, null)).thenReturn(inc);

        mockMvc.perform(post("/api/staff/1/incompatibilities/2"))
                .andExpect(status().isOk());
    }

    @Test
    void removeIncompatibility_returns204() throws Exception {
        doNothing().when(staffService).removeIncompatibility(1L, 2L);

        mockMvc.perform(delete("/api/staff/1/incompatibilities/2"))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Staff staff(Long id, String firstName, String lastName) {
        Staff s = new Staff();
        s.setId(id);
        s.setFirstName(firstName);
        s.setLastName(lastName);
        s.setActive(true);
        s.setEmploymentType(EmploymentType.FULL_TIME);
        return s;
    }
}
