package com.magicsystems.jrostering.api;

import com.magicsystems.jrostering.domain.RuleConfiguration;
import com.magicsystems.jrostering.domain.Site;
import com.magicsystems.jrostering.domain.ShiftType;
import com.magicsystems.jrostering.service.SiteService;
import com.magicsystems.jrostering.service.SiteService.RuleConfigurationUpdateRequest;
import com.magicsystems.jrostering.service.SiteService.SiteCreateRequest;
import com.magicsystems.jrostering.service.SiteService.SiteUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for site management, shift type reference data, and rule configuration.
 *
 * <p>All endpoints require HTTP Basic authentication with the {@code MANAGER} role
 * and are under {@code /api/sites}.</p>
 */
@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class SiteController {

    private final SiteService siteService;

    // =========================================================================
    // Site CRUD
    // =========================================================================

    @GetMapping("/{siteId}")
    public ResponseEntity<Site> getSite(@PathVariable Long siteId) {
        return ResponseEntity.ok(siteService.getById(siteId));
    }

    @GetMapping("/organisations/{organisationId}")
    public ResponseEntity<List<Site>> getSitesByOrganisation(@PathVariable Long organisationId) {
        return ResponseEntity.ok(siteService.getAllActiveByOrganisation(organisationId));
    }

    @PostMapping("/organisations/{organisationId}")
    public ResponseEntity<Site> createSite(
            @PathVariable Long organisationId,
            @RequestBody SiteCreateRequest request) {

        return ResponseEntity.ok(siteService.create(organisationId, request));
    }

    @PutMapping("/{siteId}")
    public ResponseEntity<Site> updateSite(
            @PathVariable Long siteId,
            @RequestBody SiteUpdateRequest request) {

        return ResponseEntity.ok(siteService.update(siteId, request));
    }

    @DeleteMapping("/{siteId}")
    public ResponseEntity<Site> deactivateSite(@PathVariable Long siteId) {
        return ResponseEntity.ok(siteService.deactivate(siteId));
    }

    // =========================================================================
    // Shift type management
    // =========================================================================

    @GetMapping("/{siteId}/shift-types")
    public ResponseEntity<List<ShiftType>> getShiftTypes(@PathVariable Long siteId) {
        return ResponseEntity.ok(siteService.getShiftTypes(siteId));
    }

    @PostMapping("/{siteId}/shift-types")
    public ResponseEntity<ShiftType> addShiftType(
            @PathVariable Long siteId,
            @RequestParam String name) {

        return ResponseEntity.ok(siteService.addShiftType(siteId, name));
    }

    @PutMapping("/shift-types/{shiftTypeId}")
    public ResponseEntity<ShiftType> updateShiftType(
            @PathVariable Long shiftTypeId,
            @RequestParam String name) {

        return ResponseEntity.ok(siteService.updateShiftType(shiftTypeId, name));
    }

    @DeleteMapping("/shift-types/{shiftTypeId}")
    public ResponseEntity<Void> removeShiftType(@PathVariable Long shiftTypeId) {
        siteService.removeShiftType(shiftTypeId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Rule configuration
    // =========================================================================

    @GetMapping("/{siteId}/rules")
    public ResponseEntity<List<RuleConfiguration>> getRuleConfigurations(@PathVariable Long siteId) {
        return ResponseEntity.ok(siteService.getRuleConfigurations(siteId));
    }

    @PutMapping("/rules/{ruleConfigId}")
    public ResponseEntity<RuleConfiguration> updateRuleConfiguration(
            @PathVariable Long ruleConfigId,
            @RequestBody RuleConfigurationUpdateRequest request) {

        return ResponseEntity.ok(siteService.updateRuleConfiguration(ruleConfigId, request));
    }
}
