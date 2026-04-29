package com.magicsystems.jrostering.api;

import com.magicsystems.jrostering.domain.SolverJob;
import com.magicsystems.jrostering.service.SolverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Solver", description = "Async solver lifecycle — submit, cancel and poll jobs")
@RestController
@RequestMapping("/api/solver")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('MANAGER')")
public class SolverController {

    private final SolverService solverService;

    @Operation(summary = "Submit an async solve job for a roster period")
    @PostMapping("/{rosterPeriodId}/submit")
    public ResponseEntity<SolverJob> submit(
            @PathVariable Long rosterPeriodId,
            @RequestParam @Min(1) @Max(86_400) int timeLimitSeconds) {

        SolverJob job = solverService.submitSolve(rosterPeriodId, timeLimitSeconds);
        return ResponseEntity.ok(job);
    }

    @Operation(summary = "Cancel the running solve for a roster period")
    @DeleteMapping("/{rosterPeriodId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long rosterPeriodId) {
        solverService.cancelSolve(rosterPeriodId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Poll a solver job for its current status")
    @GetMapping("/jobs/{solverJobId}")
    public ResponseEntity<SolverJob> getJob(@PathVariable Long solverJobId) {
        return ResponseEntity.ok(solverService.getSolverJob(solverJobId));
    }
}
