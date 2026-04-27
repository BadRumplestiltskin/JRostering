package com.magicsystems.jrostering.integration;

import com.magicsystems.jrostering.domain.EmploymentType;
import com.magicsystems.jrostering.domain.Organisation;
import com.magicsystems.jrostering.domain.Staff;
import com.magicsystems.jrostering.repository.OrganisationRepository;
import com.magicsystems.jrostering.service.EntityNotFoundException;
import com.magicsystems.jrostering.service.StaffService;
import com.magicsystems.jrostering.service.StaffService.StaffCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@Transactional
class StaffServiceIntegrationTest extends IntegrationTestBase {

    @Autowired StaffService staffService;
    @Autowired OrganisationRepository organisationRepository;

    @Test
    void create_persistsAndRetrievesStaff() {
        Organisation org = organisationRepository.findAll().getFirst();

        Staff staff = staffService.create(org.getId(), new StaffCreateRequest(
                "Alice", "Test", "alice@test.com", null,
                EmploymentType.FULL_TIME, null, null));

        assertThat(staff.getId()).isNotNull();
        Staff fetched = staffService.getById(staff.getId());
        assertThat(fetched.getEmail()).isEqualTo("alice@test.com");
        assertThat(fetched.getOrganisation().getId()).isEqualTo(org.getId());
    }

    @Test
    void deactivate_makesStaffInactive() {
        Organisation org = organisationRepository.findAll().getFirst();

        Staff staff = staffService.create(org.getId(), new StaffCreateRequest(
                "Bob", "Test", "bob@test.com", null,
                EmploymentType.PART_TIME, null, null));

        staffService.deactivate(staff.getId());

        assertThatThrownBy(() -> staffService.getAllActiveByOrganisation(org.getId())
                .stream()
                .filter(s -> s.getId().equals(staff.getId()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("not in active list")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void create_throwsEntityNotFound_whenOrgMissing() {
        assertThatThrownBy(() -> staffService.create(9999L, new StaffCreateRequest(
                "X", "Y", "x@y.com", null, EmploymentType.CASUAL, null, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
