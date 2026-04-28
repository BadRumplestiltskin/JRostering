package com.magicsystems.jrostering.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test verifying that the MANAGER role is required for all /api/** endpoints.
 * Runs with the full Spring Security filter chain (no addFilters=false) against a real
 * PostgreSQL container so that both HTTP-level auth and method-security are active.
 */
@AutoConfigureMockMvc
@Tag("integration")
class RbacIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "USER")
    void staffEndpoint_returns403_forUserRole() throws Exception {
        // 403 fires before service call, so no real data is needed
        mockMvc.perform(get("/api/staff/organisations/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void solverEndpoint_returns403_forUserRole() throws Exception {
        mockMvc.perform(get("/api/solver/jobs/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void rosterEndpoint_returns403_forUserRole() throws Exception {
        mockMvc.perform(get("/api/roster/periods/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void siteEndpoint_returns403_forUserRole() throws Exception {
        mockMvc.perform(get("/api/sites/organisations/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void reportEndpoint_returns403_forUserRole() throws Exception {
        mockMvc.perform(get("/api/reports/staff-hours/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void staffEndpoint_isReachable_forManagerRole() throws Exception {
        // Returns 200 with empty list — proves the MANAGER role grants access
        mockMvc.perform(get("/api/staff/organisations/999"))
                .andExpect(status().isOk());
    }
}
