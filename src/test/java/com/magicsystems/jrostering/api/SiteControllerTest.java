package com.magicsystems.jrostering.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.service.EntityNotFoundException;
import com.magicsystems.jrostering.service.InvalidOperationException;
import com.magicsystems.jrostering.service.SiteService;
import com.magicsystems.jrostering.service.SiteService.SiteCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = SiteController.class, excludeAutoConfiguration = {com.magicsystems.jrostering.JpaAuditingConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(roles = "MANAGER")
class SiteControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  SiteService siteService;

    // =========================================================================
    // Site CRUD
    // =========================================================================

    @Test
    void getSite_returns200() throws Exception {
        Site site = site(1L, "Main");
        when(siteService.getById(1L)).thenReturn(site);

        mockMvc.perform(get("/api/sites/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Main"));
    }

    @Test
    void getSite_returns404_whenNotFound() throws Exception {
        when(siteService.getById(99L)).thenThrow(EntityNotFoundException.of("Site", 99L));

        mockMvc.perform(get("/api/sites/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSitesByOrganisation_returns200WithList() throws Exception {
        when(siteService.getAllActiveByOrganisation(2L))
                .thenReturn(List.of(site(1L, "Main"), site(2L, "North")));

        mockMvc.perform(get("/api/sites/organisations/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void createSite_returns200() throws Exception {
        Site site = site(3L, "New Site");
        when(siteService.create(eq(2L), any(SiteCreateRequest.class))).thenReturn(site);

        var req = new SiteCreateRequest("New Site", "Australia/Adelaide", null);

        mockMvc.perform(post("/api/sites/organisations/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void deactivateSite_returns200() throws Exception {
        Site site = site(1L, "Main");
        site.setActive(false);
        when(siteService.deactivate(1L)).thenReturn(site);

        mockMvc.perform(delete("/api/sites/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    // =========================================================================
    // Shift type management
    // =========================================================================

    @Test
    void getShiftTypes_returns200() throws Exception {
        ShiftType st = shiftType(1L, "Morning");
        when(siteService.getShiftTypes(5L)).thenReturn(List.of(st));

        mockMvc.perform(get("/api/sites/5/shift-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Morning"));
    }

    @Test
    void addShiftType_returns200() throws Exception {
        ShiftType st = shiftType(2L, "Night");
        when(siteService.addShiftType(5L, "Night")).thenReturn(st);

        mockMvc.perform(post("/api/sites/5/shift-types").param("name", "Night"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Night"));
    }

    @Test
    void updateShiftType_returns200() throws Exception {
        ShiftType st = shiftType(2L, "Renamed");
        when(siteService.updateShiftType(2L, "Renamed")).thenReturn(st);

        mockMvc.perform(put("/api/sites/shift-types/2").param("name", "Renamed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void removeShiftType_returns204() throws Exception {
        doNothing().when(siteService).removeShiftType(2L);

        mockMvc.perform(delete("/api/sites/shift-types/2"))
                .andExpect(status().isNoContent());
    }

    @Test
    void addShiftType_returns409_whenDuplicate() throws Exception {
        when(siteService.addShiftType(5L, "Morning"))
                .thenThrow(new InvalidOperationException("Duplicate shift type"));

        mockMvc.perform(post("/api/sites/5/shift-types").param("name", "Morning"))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // Rule configuration
    // =========================================================================

    @Test
    void getRuleConfigurations_returns200() throws Exception {
        when(siteService.getRuleConfigurations(5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/sites/5/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Site site(Long id, String name) {
        Site s = new Site();
        s.setId(id);
        s.setName(name);
        s.setActive(true);
        s.setTimezone("UTC");
        return s;
    }

    private static ShiftType shiftType(Long id, String name) {
        ShiftType st = new ShiftType();
        st.setId(id);
        st.setName(name);
        return st;
    }
}
