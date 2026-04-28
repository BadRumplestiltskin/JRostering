package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    @Mock StaffRepository           staffRepository;
    @Mock OrganisationRepository    organisationRepository;
    @Mock StaffAvailabilityRepository staffAvailabilityRepository;
    @Mock StaffPreferenceRepository staffPreferenceRepository;
    @Mock LeaveRepository           leaveRepository;
    @Mock ShiftTypeRepository       shiftTypeRepository;

    @InjectMocks StaffService service;

    private Organisation org;

    @BeforeEach
    void setUp() {
        org = new Organisation();
        org.setId(1L);
        org.setName("Test Org");
    }

    @Test
    void create_savesStaffLinkedToOrganisation() {
        when(organisationRepository.findById(1L)).thenReturn(Optional.of(org));
        when(staffRepository.findByOrganisationAndActiveTrue(org)).thenReturn(List.of());
        when(staffRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Staff result = service.create(1L, new StaffService.StaffCreateRequest(
                "Jane", "Smith", "jane@example.com", null,
                EmploymentType.FULL_TIME, null, null));

        assertThat(result.getFirstName()).isEqualTo("Jane");
        assertThat(result.getLastName()).isEqualTo("Smith");
        assertThat(result.getEmail()).isEqualTo("jane@example.com");
        assertThat(result.getOrganisation()).isEqualTo(org);
        assertThat(result.isActive()).isTrue();
        verify(staffRepository).save(any());
    }

    @Test
    void create_throwsEntityNotFound_whenOrganisationMissing() {
        when(organisationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(99L, new StaffService.StaffCreateRequest(
                "A", "B", "a@b.com", null, EmploymentType.CASUAL, null, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void deactivate_setsActiveFalse() {
        Staff staff = staff(1L, "Jane", "Smith");
        when(staffRepository.findById(1L)).thenReturn(Optional.of(staff));
        when(staffRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.deactivate(1L);

        assertThat(staff.isActive()).isFalse();
        verify(staffRepository).save(staff);
    }

    @Test
    void deactivate_throwsEntityNotFound_whenMissing() {
        when(staffRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getById_returnsStaff_whenExists() {
        Staff staff = staff(1L, "Jane", "Smith");
        when(staffRepository.findById(1L)).thenReturn(Optional.of(staff));

        Staff result = service.getById(1L);

        assertThat(result).isEqualTo(staff);
    }

    @Test
    void getById_throwsEntityNotFound_whenMissing() {
        when(staffRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getAllActiveByOrganisation_returnsOnlyActiveStaff() {
        Staff active   = staff(1L, "Active",   "One");
        Staff inactive = staff(2L, "Inactive", "Two");
        inactive.setActive(false);
        when(organisationRepository.findById(1L)).thenReturn(Optional.of(org));
        when(staffRepository.findByOrganisationAndActiveTrue(org)).thenReturn(List.of(active));

        List<Staff> result = service.getAllActiveByOrganisation(1L);

        assertThat(result).containsExactly(active);
        assertThat(result).doesNotContain(inactive);
    }

    @Test
    void create_throwsInvalidOperation_whenEmailAlreadyExists() {
        Staff existing = staff(1L, "Alice", "Smith");
        existing.setEmail("alice@example.com");
        when(organisationRepository.findById(1L)).thenReturn(Optional.of(org));
        when(staffRepository.findByOrganisationAndActiveTrue(org)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.create(1L, new StaffService.StaffCreateRequest(
                "Alice", "Duplicate", "alice@example.com", null,
                EmploymentType.FULL_TIME, null, null)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void addLeave_throwsInvalidOperation_whenEndDateBeforeStartDate() {
        Staff staff = staff(1L, "Jane", "Smith");
        when(staffRepository.findById(1L)).thenReturn(Optional.of(staff));

        LocalDate start = LocalDate.of(2026, 6, 10);
        LocalDate end   = LocalDate.of(2026, 6, 5);

        assertThatThrownBy(() -> service.addLeave(1L, start, end, LeaveType.ANNUAL, null))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("end date");
    }

    @Test
    void addLeave_throwsInvalidOperation_whenOverlappingLeaveExists() {
        Staff staff = staff(1L, "Jane", "Smith");
        when(staffRepository.findById(1L)).thenReturn(Optional.of(staff));

        Leave existing = new Leave();
        existing.setId(99L);
        existing.setStartDate(LocalDate.of(2026, 6, 8));
        existing.setEndDate(LocalDate.of(2026, 6, 14));
        existing.setStatus(LeaveStatus.APPROVED);
        when(leaveRepository.findOverlapping(any(), any(), any(), any(), isNull()))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() ->
                service.addLeave(1L, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 17),
                        LeaveType.ANNUAL, null))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("overlaps");
    }

    // -------------------------------------------------------------------------

    private static Staff staff(Long id, String first, String last) {
        Staff s = new Staff();
        s.setId(id);
        s.setFirstName(first);
        s.setLastName(last);
        s.setEmail(first.toLowerCase() + "@example.com");
        s.setActive(true);
        return s;
    }
}
