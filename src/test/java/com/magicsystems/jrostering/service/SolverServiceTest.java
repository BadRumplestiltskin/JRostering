package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.RosterPeriodRepository;
import com.magicsystems.jrostering.repository.SolverJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SolverServiceTest {

    @Mock SolverExecutor         solverExecutor;
    @Mock RosterPeriodRepository rosterPeriodRepository;
    @Mock SolverJobRepository    solverJobRepository;

    @InjectMocks SolverService service;

    // =========================================================================
    // submitSolve
    // =========================================================================

    @Test
    void submitSolve_createsQueuedJob_andDispatchesAsync_whenDraft() {
        RosterPeriod period = period(1L, RosterPeriodStatus.DRAFT);
        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(solverJobRepository.save(any())).thenAnswer(inv -> {
            SolverJob j = inv.getArgument(0);
            j.setId(100L);
            return j;
        });
        when(rosterPeriodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SolverJob job = service.submitSolve(1L, 60);

        assertThat(job.getStatus()).isEqualTo(SolverJobStatus.QUEUED);
        assertThat(period.getStatus()).isEqualTo(RosterPeriodStatus.SOLVING);
        verify(solverExecutor).executeSolveAsync(100L, 1L, 60);
    }

    @Test
    void submitSolve_acceptsInfeasiblePeriod() {
        RosterPeriod period = period(1L, RosterPeriodStatus.INFEASIBLE);
        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(solverJobRepository.save(any())).thenAnswer(inv -> {
            SolverJob j = inv.getArgument(0);
            j.setId(101L);
            return j;
        });
        when(rosterPeriodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SolverJob job = service.submitSolve(1L, 120);

        assertThat(job.getStatus()).isEqualTo(SolverJobStatus.QUEUED);
        assertThat(period.getStatus()).isEqualTo(RosterPeriodStatus.SOLVING);
    }

    @Test
    void submitSolve_throws_whenPeriodSolved() {
        RosterPeriod period = period(1L, RosterPeriodStatus.SOLVED);
        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.submitSolve(1L, 60))
                .isInstanceOf(InvalidOperationException.class);
        verify(solverExecutor, never()).executeSolveAsync(any(), any(), anyInt());
    }

    @Test
    void submitSolve_throws_whenPeriodNotFound() {
        when(rosterPeriodRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitSolve(99L, 60))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // =========================================================================
    // cancelSolve
    // =========================================================================

    @Test
    void cancelSolve_delegatesToExecutor_whenSolving() {
        RosterPeriod period = period(1L, RosterPeriodStatus.SOLVING);
        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));

        service.cancelSolve(1L);

        verify(solverExecutor).requestCancel(1L);
    }

    @Test
    void cancelSolve_throws_whenNotSolving() {
        RosterPeriod period = period(1L, RosterPeriodStatus.DRAFT);
        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.cancelSolve(1L))
                .isInstanceOf(InvalidOperationException.class);
        verify(solverExecutor, never()).requestCancel(any());
    }

    // =========================================================================
    // getSolverJob
    // =========================================================================

    @Test
    void getSolverJob_returnsJob_whenFound() {
        SolverJob job = new SolverJob();
        job.setId(5L);
        when(solverJobRepository.findById(5L)).thenReturn(Optional.of(job));

        SolverJob result = service.getSolverJob(5L);

        assertThat(result.getId()).isEqualTo(5L);
    }

    @Test
    void getSolverJob_throws_whenNotFound() {
        when(solverJobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSolverJob(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static RosterPeriod period(Long id, RosterPeriodStatus status) {
        RosterPeriod p = new RosterPeriod();
        p.setId(id);
        p.setStatus(status);
        return p;
    }
}
