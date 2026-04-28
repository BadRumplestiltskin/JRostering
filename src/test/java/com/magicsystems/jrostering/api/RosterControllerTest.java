package com.magicsystems.jrostering.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.service.EntityNotFoundException;
import com.magicsystems.jrostering.service.InvalidOperationException;
import com.magicsystems.jrostering.service.RosterService;
import com.magicsystems.jrostering.service.RosterService.ShiftCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = RosterController.class, excludeAutoConfiguration = {com.magicsystems.jrostering.JpaAuditingConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class RosterControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  RosterService rosterService;

    // =========================================================================
    // Roster period endpoints
    // =========================================================================

    @Test
    void getPeriod_returns200() throws Exception {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.DRAFT);
        when(rosterService.getById(1L)).thenReturn(period);

        mockMvc.perform(get("/api/roster/periods/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void getPeriod_returns404_whenNotFound() throws Exception {
        when(rosterService.getById(99L)).thenThrow(EntityNotFoundException.of("RosterPeriod", 99L));

        mockMvc.perform(get("/api/roster/periods/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPeriodsForSite_returns200WithList() throws Exception {
        when(rosterService.getBySite(10L)).thenReturn(List.of(
                rosterPeriod(1L, RosterPeriodStatus.DRAFT),
                rosterPeriod(2L, RosterPeriodStatus.SOLVED)
        ));

        mockMvc.perform(get("/api/roster/sites/10/periods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void createPeriod_returns200() throws Exception {
        RosterPeriod period = rosterPeriod(3L, RosterPeriodStatus.DRAFT);
        when(rosterService.createRosterPeriod(eq(10L), any(LocalDate.class), isNull()))
                .thenReturn(period);

        mockMvc.perform(post("/api/roster/sites/10/periods")
                        .param("startDate", "2026-05-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void revertToDraft_returns200() throws Exception {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.DRAFT);
        when(rosterService.revertToDraft(1L)).thenReturn(period);

        mockMvc.perform(post("/api/roster/periods/1/revert-to-draft"))
                .andExpect(status().isOk());
    }

    @Test
    void revertToDraft_returns409_whenSolving() throws Exception {
        when(rosterService.revertToDraft(1L))
                .thenThrow(new InvalidOperationException("Period is SOLVING"));

        mockMvc.perform(post("/api/roster/periods/1/revert-to-draft"))
                .andExpect(status().isConflict());
    }

    @Test
    void publish_returns200() throws Exception {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.PUBLISHED);
        when(rosterService.publish(1L)).thenReturn(period);

        mockMvc.perform(post("/api/roster/periods/1/publish"))
                .andExpect(status().isOk());
    }

    @Test
    void cancel_returns200() throws Exception {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.CANCELLED);
        when(rosterService.cancel(1L)).thenReturn(period);

        mockMvc.perform(post("/api/roster/periods/1/cancel"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Shift endpoints
    // =========================================================================

    @Test
    void getShifts_returns200() throws Exception {
        when(rosterService.getShifts(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/roster/periods/1/shifts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void addShift_returns200() throws Exception {
        Shift shift = shift(7L);
        when(rosterService.addShift(eq(1L), any())).thenReturn(shift);

        var req = new ShiftCreateRequest(
                null, "Morning",
                OffsetDateTime.of(2026, 5, 1, 8, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 5, 1, 16, 0, 0, 0, ZoneOffset.UTC),
                2, null);

        mockMvc.perform(post("/api/roster/periods/1/shifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void removeShift_returns204() throws Exception {
        doNothing().when(rosterService).removeShift(7L);

        mockMvc.perform(delete("/api/roster/shifts/7"))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // Assignment endpoints
    // =========================================================================

    @Test
    void getAssignments_returns200() throws Exception {
        when(rosterService.getAssignments(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/roster/periods/1/assignments"))
                .andExpect(status().isOk());
    }

    @Test
    void pin_returns200() throws Exception {
        ShiftAssignment sa = assignment(3L, true);
        when(rosterService.pin(3L)).thenReturn(sa);

        mockMvc.perform(post("/api/roster/assignments/3/pin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true));
    }

    @Test
    void unpin_returns200() throws Exception {
        ShiftAssignment sa = assignment(3L, false);
        when(rosterService.unpin(3L)).thenReturn(sa);

        mockMvc.perform(delete("/api/roster/assignments/3/pin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(false));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static RosterPeriod rosterPeriod(Long id, RosterPeriodStatus status) {
        RosterPeriod p = new RosterPeriod();
        p.setId(id);
        p.setStatus(status);
        p.setSequenceNumber(1);
        return p;
    }

    private static Shift shift(Long id) {
        Shift s = new Shift();
        s.setId(id);
        s.setMinimumStaff(1);
        s.setStartDatetime(OffsetDateTime.now());
        s.setEndDatetime(OffsetDateTime.now().plusHours(8));
        return s;
    }

    private static ShiftAssignment assignment(Long id, boolean pinned) {
        ShiftAssignment a = new ShiftAssignment();
        a.setId(id);
        a.setPinned(pinned);
        return a;
    }
}
