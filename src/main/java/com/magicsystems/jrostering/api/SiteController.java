package com.magicsystems.jrostering.api;

import com.magicsystems.jrostering.domain.RuleConfiguration;
import com.magicsystems.jrostering.domain.Site;
import com.magicsystems.jrostering.domain.ShiftType;
import com.magicsystems.jrostering.service.SiteService;
import com.magicsystems.jrostering.service.SiteService.RuleConfigurationUpdateRequest;
import com.magicsystems.jrostering.service.SiteService.SiteCreateRequest;
import com.magicsystems.jrostering.service.SiteService.SiteUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Sites", description = "Site CRUD, shift type reference data and constraint rule configuration")
@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class SiteController {

    private final SiteService siteService;

    // =========================================================================
    // Site CRUD
    // =========================================================================

    @Operation(summary = "Get a site by ID")
    @GetMapping("/{siteId}")
    public ResponseEntity<Site> getSite(@PathVariable Long siteId) {
        return ResponseEntity.ok(siteService.getById(siteId));
    }

    @Operation(summary = "List all active sites for an organisation")
    @GetMapping("/organisations/{organisationId}")
    public ResponseEntity<List<Site>> getSitesByOrganisation(@PathVariable Long organisationId) {
        return ResponseEntity.ok(siteService.getAllActiveByOrganisation(organisationId));
    }

    @Operation(summary = "Create a site (seeds default rule configurations)")
    @PostMapping("/organisations/{organisationId}")
    public ResponseEntity<Site> createSite(
            @PathVariable Long organisationId,
            @RequestBody SiteCreateRequest request) {

        return ResponseEntity.ok(siteService.create(organisationId, request));
    }

    @Operation(summary = "Update a site's core fields")
    @PutMapping("/{siteId}")
    public ResponseEntity<Site> updateSite(
            @PathVariable Long siteId,
            @RequestBody SiteUpdateRequest request) {

        return ResponseEntity.ok(siteService.update(siteId, request));
    }

    @Operation(summary = "Deactivate (soft-delete) a site")
    @DeleteMapping("/{siteId}")
    public ResponseEntity<Site> deactivateSite(@PathVariable Long siteId) {
        return ResponseEntity.ok(siteService.deactivate(siteId));
    }

    // =========================================================================
    // Shift type management
    // =========================================================================

    @Operation(summary = "List shift types for a site")
    @GetMapping("/{siteId}/shift-types")
    public ResponseEntity<List<ShiftType>> getShiftTypes(@PathVariable Long siteId) {
        return ResponseEntity.ok(siteService.getShiftTypes(siteId));
    }

    @Operation(summary = "Add a shift type to a site")
    @PostMapping("/{siteId}/shift-types")
    public ResponseEntity<ShiftType> addShiftType(
            @PathVariable Long siteId,
            @RequestParam String name) {

        return ResponseEntity.ok(siteService.addShiftType(siteId, name));
    }

    @Operation(summary = "Rename a shift type")
    @PutMapping("/shift-types/{shiftTypeId}")
    public ResponseEntity<ShiftType> updateShiftType(
            @PathVariable Long shiftTypeId,
            @RequestParam String name) {

        return ResponseEntity.ok(siteService.updateShiftType(shiftTypeId, name));
    }

    @Operation(summary = "Remove a shift type")
    @DeleteMapping("/shift-types/{shiftTypeId}")
    public ResponseEntity<Void> removeShiftType(@PathVariable Long shiftTypeId) {
        siteService.removeShiftType(shiftTypeId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Rule configuration
    // =========================================================================

    @Operation(summary = "List all rule configurations for a site")
    @GetMapping("/{siteId}/rules")
    public ResponseEntity<List<RuleConfiguration>> getRuleConfigurations(@PathVariable Long siteId) {
        return ResponseEntity.ok(siteService.getRuleConfigurations(siteId));
    }

    @Operation(summary = "Update a rule configuration (level, weight, parameters)")
    @PutMapping("/rules/{ruleConfigId}")
    public ResponseEntity<RuleConfiguration> updateRuleConfiguration(
            @PathVariable Long ruleConfigId,
            @RequestBody RuleConfigurationUpdateRequest request) {

        return ResponseEntity.ok(siteService.updateRuleConfiguration(ruleConfigId, request));
    }
}
