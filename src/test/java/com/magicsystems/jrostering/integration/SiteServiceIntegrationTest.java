package com.magicsystems.jrostering.integration;

import com.magicsystems.jrostering.domain.Organisation;
import com.magicsystems.jrostering.domain.RuleConfiguration;
import com.magicsystems.jrostering.domain.RuleType;
import com.magicsystems.jrostering.domain.ShiftType;
import com.magicsystems.jrostering.domain.Site;
import com.magicsystems.jrostering.repository.OrganisationRepository;
import com.magicsystems.jrostering.service.SiteService;
import com.magicsystems.jrostering.service.SiteService.SiteCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Transactional
class SiteServiceIntegrationTest extends IntegrationTestBase {

    @Autowired SiteService siteService;
    @Autowired OrganisationRepository organisationRepository;

    @Test
    void create_seedsAllRuleConfigurations() {
        Organisation org = organisationRepository.findAll().getFirst();

        Site site = siteService.create(org.getId(),
                new SiteCreateRequest("Test Site", "Australia/Adelaide", null));

        List<RuleConfiguration> rules = siteService.getRuleConfigurations(site.getId());
        assertThat(rules).isNotEmpty();
        assertThat(rules.stream().map(RuleConfiguration::getRuleType))
                .containsAll(List.of(RuleType.values()));
    }

    @Test
    void addShiftType_persistsAndRetrievable() {
        Organisation org = organisationRepository.findAll().getFirst();
        Site site = siteService.create(org.getId(),
                new SiteCreateRequest("Shift Site", "UTC", null));

        ShiftType st = siteService.addShiftType(site.getId(), "Morning");

        assertThat(st.getId()).isNotNull();
        assertThat(siteService.getShiftTypes(site.getId()))
                .extracting(ShiftType::getName)
                .contains("Morning");
    }

    @Test
    void updateShiftType_changesName() {
        Organisation org = organisationRepository.findAll().getFirst();
        Site site = siteService.create(org.getId(),
                new SiteCreateRequest("Update Site", "UTC", null));
        ShiftType st = siteService.addShiftType(site.getId(), "Day");

        siteService.updateShiftType(st.getId(), "Evening");

        assertThat(siteService.getShiftTypes(site.getId()))
                .extracting(ShiftType::getName)
                .contains("Evening")
                .doesNotContain("Day");
    }
}
