package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for managing {@link Site} entities and their associated
 * data: {@link ShiftType} reference data and {@link RuleConfiguration} settings.
 *
 * <h3>Rule seeding</h3>
 * <p>When a site is created, a {@link RuleConfiguration} row is automatically
 * seeded for every {@link RuleType} with production-appropriate defaults.
 * These defaults match the parameter schemas defined in the architecture document.
 * The roster manager may subsequently enable, disable, or adjust individual rules
 * via {@link #updateRuleConfiguration}.</p>
 *
 * <h3>Lifecycle</h3>
 * <p>Sites are never hard-deleted. Deactivation ({@link #deactivate}) sets
 * {@code active = false}. Existing roster periods and rule configurations are preserved.</p>
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class SiteService {

    // =========================================================================
    // Request records
    // =========================================================================

    /**
     * Input for creating a new site.
     *
     * @param name      required; display name for the site
     * @param timezone  required; IANA timezone identifier (e.g. {@code Australia/Adelaide})
     * @param address   optional
     */
    public record SiteCreateRequest(String name, String timezone, String address) {}

    /**
     * Input for updating an existing site's core fields.
     */
    public record SiteUpdateRequest(String name, String timezone, String address) {}

    /**
     * Input for updating a {@link RuleConfiguration}.
     * All fields are replaced; supply the current value for fields that are not changing.
     *
     * @param enabled          whether this rule is active
     * @param constraintLevel  HARD, MEDIUM, or SOFT
     * @param weight           soft-rule weighting; ignored (may be null) for HARD and MEDIUM
     * @param parameterJson    rule-specific JSON parameters; use {@code "{}"} for parameterless rules
     */
    public record RuleConfigurationUpdateRequest(
            boolean enabled,
            ConstraintLevel constraintLevel,
            Integer weight,
            String parameterJson
    ) {}

    // =========================================================================
    // Default rule configurations
    // =========================================================================

    /**
     * Holds the default values seeded for each {@link RuleType} when a site is created.
     */
    private record RuleDefault(ConstraintLevel level, Integer weight, String parameterJson) {}

    /**
     * Default rule configuration values, keyed by {@link RuleType}.
     * Defaults match the parameter schemas defined in the architecture document (Section 6).
     */
    private static final Map<RuleType, RuleDefault> RULE_DEFAULTS = Map.ofEntries(
            // ---- Hard rules ------------------------------------------------
            Map.entry(RuleType.MIN_REST_BETWEEN_SHIFTS,
                    new RuleDefault(ConstraintLevel.HARD, null, "{\"minimumRestHours\":10}")),
            Map.entry(RuleType.MAX_HOURS_PER_DAY,
                    new RuleDefault(ConstraintLevel.HARD, null, "{\"maximumHours\":12}")),
            Map.entry(RuleType.MAX_HOURS_PER_WEEK,
                    new RuleDefault(ConstraintLevel.HARD, null, "{\"maximumHours\":38}")),
            Map.entry(RuleType.MAX_CONSECUTIVE_DAYS,
                    new RuleDefault(ConstraintLevel.HARD, null, "{\"maximumDays\":5}")),
            Map.entry(RuleType.MIN_STAFF_PER_SHIFT,
                    new RuleDefault(ConstraintLevel.HARD, null, "{}")),
            Map.entry(RuleType.MIN_QUALIFIED_STAFF_PER_SHIFT,
                    new RuleDefault(ConstraintLevel.HARD, null, "{}")),
            Map.entry(RuleType.QUALIFICATION_REQUIRED_FOR_SHIFT,
                    new RuleDefault(ConstraintLevel.HARD, null, "{}")),
            Map.entry(RuleType.STAFF_MUTUAL_EXCLUSION,
                    new RuleDefault(ConstraintLevel.HARD, null, "{}")),
            Map.entry(RuleType.STAFF_MUST_PAIR,
                    new RuleDefault(ConstraintLevel.HARD, null, "{}")),
            Map.entry(RuleType.STAFF_LEAVE_BLOCK,
                    new RuleDefault(ConstraintLevel.HARD, null, "{}")),
            // Default HARD; manager may change to MEDIUM for advisory availability sites
            Map.entry(RuleType.STAFF_AVAILABILITY_BLOCK,
                    new RuleDefault(ConstraintLevel.HARD, null, "{}")),

            // ---- Soft rules ------------------------------------------------
            Map.entry(RuleType.PREFERRED_DAYS_OFF,
                    new RuleDefault(ConstraintLevel.SOFT, 1, "{\"penaltyPerViolation\":1}")),
            Map.entry(RuleType.PREFERRED_SHIFT_TYPE,
                    new RuleDefault(ConstraintLevel.SOFT, 1, "{\"penaltyPerViolation\":1}")),
            Map.entry(RuleType.HONOUR_REQUESTED_LEAVE,
                    new RuleDefault(ConstraintLevel.SOFT, 5, "{\"penaltyPerViolation\":5}")),
            Map.entry(RuleType.FAIR_HOURS_DISTRIBUTION,
                    new RuleDefault(ConstraintLevel.SOFT, 3, "{\"maximumDeviationHours\":4}")),
            Map.entry(RuleType.FAIR_WEEKEND_DISTRIBUTION,
                    new RuleDefault(ConstraintLevel.SOFT, 2, "{\"maximumDeviationShifts\":2}")),
            Map.entry(RuleType.FAIR_NIGHT_SHIFT_DISTRIBUTION,
                    new RuleDefault(ConstraintLevel.SOFT, 2, "{\"maximumDeviationShifts\":2}")),
            Map.entry(RuleType.MINIMISE_UNDERSTAFFING,
                    new RuleDefault(ConstraintLevel.SOFT, 10, "{\"penaltyPerMissingStaff\":10}")),
            Map.entry(RuleType.MINIMISE_OVERSTAFFING,
                    new RuleDefault(ConstraintLevel.SOFT, 1, "{\"penaltyPerExtraStaff\":1}")),
            Map.entry(RuleType.AVOID_EXCESSIVE_CONSECUTIVE_DAYS,
                    new RuleDefault(ConstraintLevel.SOFT, 2, "{\"preferredMaxDays\":4,\"penaltyPerExtraDay\":2}")),
            Map.entry(RuleType.SOFT_MAX_HOURS_PER_PERIOD,
                    new RuleDefault(ConstraintLevel.SOFT, 3, "{\"maximumHours\":76,\"penaltyPerExtraHour\":3}"))
    );

    // =========================================================================
    // Dependencies
    // =========================================================================

    private final OrganisationRepository       organisationRepository;
    private final SiteRepository               siteRepository;
    private final ShiftTypeRepository          shiftTypeRepository;
    private final RuleConfigurationRepository  ruleConfigurationRepository;

    public SiteService(OrganisationRepository organisationRepository,
                       SiteRepository siteRepository,
                       ShiftTypeRepository shiftTypeRepository,
                       RuleConfigurationRepository ruleConfigurationRepository) {
        this.organisationRepository      = organisationRepository;
        this.siteRepository              = siteRepository;
        this.shiftTypeRepository         = shiftTypeRepository;
        this.ruleConfigurationRepository = ruleConfigurationRepository;
    }

    // =========================================================================
    // Site CRUD
    // =========================================================================

    /**
     * Returns a site by ID.
     *
     * @throws EntityNotFoundException if no site exists with the given ID
     */
    public Site getById(Long siteId) {
        return siteRepository.findById(siteId)
                .orElseThrow(() -> EntityNotFoundException.of("Site", siteId));
    }

    /**
     * Returns all active sites belonging to the given organisation.
     *
     * @throws EntityNotFoundException if the organisation does not exist
     */
    public List<Site> getAllActiveByOrganisation(Long organisationId) {
        Organisation organisation = requireOrganisation(organisationId);
        return siteRepository.findByOrganisationAndActiveTrue(organisation);
    }

    /**
     * Creates a new site within the given organisation and automatically seeds
     * a {@link RuleConfiguration} row for every {@link RuleType} with the
     * default values defined in {@code RULE_DEFAULTS}.
     *
     * @throws EntityNotFoundException if the organisation does not exist
     */
    @Transactional
    public Site create(Long organisationId, SiteCreateRequest request) {
        Organisation organisation = requireOrganisation(organisationId);

        Site site = new Site();
        site.setOrganisation(organisation);
        applySiteFields(site, request.name(), request.timezone(), request.address());
        Site saved = siteRepository.save(site);

        seedRuleConfigurations(saved);

        log.info("Created site id={} name='{}' in organisation id={}. Seeded {} rule configurations.",
                saved.getId(), saved.getName(), organisationId, RULE_DEFAULTS.size());
        return saved;
    }

    /**
     * Updates the core fields of an existing site.
     *
     * @throws EntityNotFoundException if the site does not exist
     */
    @Transactional
    public Site update(Long siteId, SiteUpdateRequest request) {
        Site site = requireSite(siteId);
        applySiteFields(site, request.name(), request.timezone(), request.address());
        return siteRepository.save(site);
    }

    /**
     * Deactivates a site. Existing roster periods and rule configurations are preserved.
     *
     * @throws EntityNotFoundException if the site does not exist
     */
    @Transactional
    public Site deactivate(Long siteId) {
        Site site = requireSite(siteId);
        site.setActive(false);
        log.info("Deactivated site id={}", siteId);
        return siteRepository.save(site);
    }

    // =========================================================================
    // ShiftType management
    // =========================================================================

    /**
     * Returns all shift types defined for a site.
     *
     * @throws EntityNotFoundException if the site does not exist
     */
    public List<ShiftType> getShiftTypes(Long siteId) {
        return shiftTypeRepository.findBySite(requireSite(siteId));
    }

    /**
     * Adds a shift type to a site.
     * Name must be unique within the site.
     *
     * @throws EntityNotFoundException   if the site does not exist
     * @throws InvalidOperationException if a shift type with the same name already exists for this site
     */
    @Transactional
    public ShiftType addShiftType(Long siteId, String name) {
        Site site = requireSite(siteId);

        boolean nameExists = shiftTypeRepository.findBySite(site).stream()
                .anyMatch(st -> st.getName().equalsIgnoreCase(name));
        if (nameExists) {
            throw new InvalidOperationException(
                    "A shift type named '" + name + "' already exists for site id=" + siteId);
        }

        ShiftType shiftType = new ShiftType();
        shiftType.setSite(site);
        shiftType.setName(name);
        return shiftTypeRepository.save(shiftType);
    }

    /**
     * Updates the name of a shift type.
     * The new name must be unique within the site.
     *
     * @throws EntityNotFoundException   if the shift type does not exist
     * @throws InvalidOperationException if another shift type with the new name already exists
     */
    @Transactional
    public ShiftType updateShiftType(Long shiftTypeId, String name) {
        ShiftType shiftType = shiftTypeRepository.findById(shiftTypeId)
                .orElseThrow(() -> EntityNotFoundException.of("ShiftType", shiftTypeId));

        boolean nameConflict = shiftTypeRepository.findBySite(shiftType.getSite()).stream()
                .anyMatch(st -> !st.getId().equals(shiftTypeId) && st.getName().equalsIgnoreCase(name));
        if (nameConflict) {
            throw new InvalidOperationException(
                    "A shift type named '" + name + "' already exists for this site.");
        }

        shiftType.setName(name);
        return shiftTypeRepository.save(shiftType);
    }

    /**
     * Removes a shift type.
     * The shift type must not be referenced by any {@link com.magicsystems.jrostering.domain.Shift}.
     * This is enforced at the database level via the FK constraint; the caller may receive a
     * DataIntegrityViolationException if active shifts still reference this type.
     *
     * @throws EntityNotFoundException if the shift type does not exist
     */
    @Transactional
    public void removeShiftType(Long shiftTypeId) {
        ShiftType shiftType = shiftTypeRepository.findById(shiftTypeId)
                .orElseThrow(() -> EntityNotFoundException.of("ShiftType", shiftTypeId));
        shiftTypeRepository.delete(shiftType);
    }

    // =========================================================================
    // RuleConfiguration management
    // =========================================================================

    /**
     * Returns all rule configurations for a site.
     *
     * @throws EntityNotFoundException if the site does not exist
     */
    public List<RuleConfiguration> getRuleConfigurations(Long siteId) {
        return ruleConfigurationRepository.findBySite(requireSite(siteId));
    }

    /**
     * Updates an existing rule configuration.
     * Rule configurations are seeded at site creation and cannot be added or removed
     * individually — every {@link RuleType} always has exactly one row per site.
     *
     * @throws EntityNotFoundException   if the rule configuration does not exist
     * @throws InvalidOperationException if a {@code weight} is supplied for a non-SOFT rule
     */
    @Transactional
    public RuleConfiguration updateRuleConfiguration(Long ruleConfigId,
                                                       RuleConfigurationUpdateRequest request) {
        RuleConfiguration config = ruleConfigurationRepository.findById(ruleConfigId)
                .orElseThrow(() -> EntityNotFoundException.of("RuleConfiguration", ruleConfigId));

        if (request.constraintLevel() != ConstraintLevel.SOFT && request.weight() != null) {
            throw new InvalidOperationException(
                    "Weight is only applicable to SOFT rules. Rule " + config.getRuleType()
                    + " is being set to " + request.constraintLevel() + ".");
        }

        config.setEnabled(request.enabled());
        config.setConstraintLevel(request.constraintLevel());
        config.setWeight(request.constraintLevel() == ConstraintLevel.SOFT ? request.weight() : null);
        config.setParameterJson(request.parameterJson());
        return ruleConfigurationRepository.save(config);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Organisation requireOrganisation(Long id) {
        return organisationRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("Organisation", id));
    }

    private Site requireSite(Long id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("Site", id));
    }

    private void applySiteFields(Site site, String name, String timezone, String address) {
        site.setName(name);
        site.setTimezone(timezone);
        site.setAddress(address);
    }

    /**
     * Creates one {@link RuleConfiguration} row per {@link RuleType} for the given site,
     * using the defaults from {@link #RULE_DEFAULTS}.
     */
    private void seedRuleConfigurations(Site site) {
        List<RuleConfiguration> configs = new ArrayList<>();

        for (RuleType ruleType : RuleType.values()) {
            RuleDefault defaults = RULE_DEFAULTS.get(ruleType);
            if (defaults == null) {
                log.warn("No default configuration defined for RuleType {}. Skipping seed.", ruleType);
                continue;
            }

            RuleConfiguration config = new RuleConfiguration();
            config.setSite(site);
            config.setRuleType(ruleType);
            config.setEnabled(true);
            config.setConstraintLevel(defaults.level());
            config.setWeight(defaults.weight());
            config.setParameterJson(defaults.parameterJson());
            configs.add(config);
        }

        ruleConfigurationRepository.saveAll(configs);
    }
}
