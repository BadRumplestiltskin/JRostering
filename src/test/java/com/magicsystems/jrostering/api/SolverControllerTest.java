package com.magicsystems.jrostering.api;

import com.magicsystems.jrostering.domain.SolverJob;
import com.magicsystems.jrostering.domain.SolverJobStatus;
import com.magicsystems.jrostering.service.EntityNotFoundException;
import com.magicsystems.jrostering.service.InvalidOperationException;
import com.magicsystems.jrostering.service.SolverService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = SolverController.class, excludeAutoConfiguration = {com.magicsystems.jrostering.JpaAuditingConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(roles = "MANAGER")
class SolverControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  SolverService solverService;

    @Test
    void submit_returns200WithQueuedJob() throws Exception {
        SolverJob job = solverJob(1L, SolverJobStatus.QUEUED);
        when(solverService.submitSolve(5L, 60)).thenReturn(job);

        mockMvc.perform(post("/api/solver/5/submit").param("timeLimitSeconds", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void submit_returns409_whenInvalidOperation() throws Exception {
        when(solverService.submitSolve(any(), anyInt()))
                .thenThrow(new InvalidOperationException("Already solving"));

        mockMvc.perform(post("/api/solver/5/submit").param("timeLimitSeconds", "60"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancel_returns204_whenSolving() throws Exception {
        doNothing().when(solverService).cancelSolve(5L);

        mockMvc.perform(delete("/api/solver/5/cancel"))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancel_returns409_whenNotSolving() throws Exception {
        doThrow(new InvalidOperationException("Not solving"))
                .when(solverService).cancelSolve(5L);

        mockMvc.perform(delete("/api/solver/5/cancel"))
                .andExpect(status().isConflict());
    }

    @Test
    void getJob_returns200WithJob() throws Exception {
        SolverJob job = solverJob(42L, SolverJobStatus.RUNNING);
        when(solverService.getSolverJob(42L)).thenReturn(job);

        mockMvc.perform(get("/api/solver/jobs/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void getJob_returns404_whenJobNotFound() throws Exception {
        when(solverService.getSolverJob(99L))
                .thenThrow(EntityNotFoundException.of("SolverJob", 99L));

        mockMvc.perform(get("/api/solver/jobs/99"))
                .andExpect(status().isNotFound());
    }

    private static SolverJob solverJob(Long id, SolverJobStatus status) {
        SolverJob job = new SolverJob();
        job.setId(id);
        job.setStatus(status);
        job.setTimeLimitSeconds(60);
        return job;
    }
}
