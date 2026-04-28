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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RosterServiceTest {

    @Mock SiteRepository                          siteRepository;
    @Mock RosterPeriodRepository                  rosterPeriodRepository;
    @Mock ShiftRepository                         shiftRepository;
    @Mock ShiftAssignmentRepository               shiftAssignmentRepository;
    @Mock ShiftQualificationRequirementRepository shiftQualRequirementRepository;
    @Mock QualificationRepository                 qualificationRepository;
    @Mock ShiftTypeRepository                     shiftTypeRepository;
    @Mock StaffRepository                         staffRepository;

    @InjectMocks RosterService service;

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 5, 1, 8, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T1 = OffsetDateTime.of(2026, 5, 1, 16, 0, 0, 0, ZoneOffset.UTC);

    private Site site;

    @BeforeEach
    void setUp() {
        site = new Site();
        site.setId(10L);
        site.setName("Main");
    }

    // =========================================================================
    // createRosterPeriod
    // =========================================================================

    @Test
    void createRosterPeriod_createsSequence1_whenNoPreviousPeriod() {
        when(siteRepository.findById(10L)).thenReturn(Optional.of(site));
        when(rosterPeriodRepository.save(any())).thenAnswer(inv -> {
            RosterPeriod p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        RosterPeriod result = service.createRosterPeriod(10L, LocalDate.of(2026, 5, 1), null);

        assertThat(result.getSequenceNumber()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo(RosterPeriodStatus.DRAFT);
        assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(result.getEndDate()).isEqualTo(LocalDate.of(2026, 5, 14));
    }

    @Test
    void createRosterPeriod_createsSequence2_whenPreviousSolved() {
        RosterPeriod prev = rosterPeriod(1L, RosterPeriodStatus.SOLVED);
        prev.setStartDate(LocalDate.of(2026, 5, 1));
        prev.setEndDate(LocalDate.of(2026, 5, 14));

        when(siteRepository.findById(10L)).thenReturn(Optional.of(site));
        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(prev));
        when(rosterPeriodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RosterPeriod result = service.createRosterPeriod(10L, LocalDate.of(2026, 5, 15), 1L);

        assertThat(result.getSequenceNumber()).isEqualTo(2);
        assertThat(result.getPreviousPeriod()).isEqualTo(prev);
    }

    @Test
    void createRosterPeriod_throws_whenPreviousNotSolvedOrPublished() {
        RosterPeriod prev = rosterPeriod(1L, RosterPeriodStatus.DRAFT);
        prev.setEndDate(LocalDate.of(2026, 5, 14));

        when(siteRepository.findById(10L)).thenReturn(Optional.of(site));
        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(prev));

        assertThatThrownBy(() ->
                service.createRosterPeriod(10L, LocalDate.of(2026, 5, 15), 1L))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void createRosterPeriod_throws_whenStartDateDoesNotFollowPrevious() {
        RosterPeriod prev = rosterPeriod(1L, RosterPeriodStatus.SOLVED);
        prev.setEndDate(LocalDate.of(2026, 5, 14));

        when(siteRepository.findById(10L)).thenReturn(Optional.of(site));
        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(prev));

        assertThatThrownBy(() ->
                service.createRosterPeriod(10L, LocalDate.of(2026, 5, 16), 1L))
                .isInstanceOf(InvalidOperationException.class);
    }

    // =========================================================================
    // revertToDraft
    // =========================================================================

    @Test
    void revertToDraft_unpimsAndSetsDraft_whenSolved() {
        ShiftAssignment pinned = assignment(1L, null, true);
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.SOLVED);

        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(shiftAssignmentRepository.findByRosterPeriod(period)).thenReturn(List.of(pinned));
        when(rosterPeriodRepository.findByPreviousPeriod(period)).thenReturn(Optional.empty());

        service.revertToDraft(1L);

        assertThat(period.getStatus()).isEqualTo(RosterPeriodStatus.DRAFT);
        assertThat(pinned.isPinned()).isFalse();
        verify(rosterPeriodRepository, atLeastOnce()).save(period);
    }

    @Test
    void revertToDraft_cascadesToFollowingPeriod() {
        RosterPeriod period1 = rosterPeriod(1L, RosterPeriodStatus.SOLVED);
        RosterPeriod period2 = rosterPeriod(2L, RosterPeriodStatus.DRAFT);
        ShiftAssignment p2slot = assignment(10L, new Staff(), false);

        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period1));
        when(shiftAssignmentRepository.findByRosterPeriod(period1)).thenReturn(List.of());
        when(rosterPeriodRepository.findByPreviousPeriod(period1)).thenReturn(Optional.of(period2));
        when(shiftAssignmentRepository.findByRosterPeriod(period2)).thenReturn(List.of(p2slot));

        service.revertToDraft(1L);

        assertThat(period2.getStatus()).isEqualTo(RosterPeriodStatus.DRAFT);
        assertThat(p2slot.getStaff()).isNull();
        assertThat(p2slot.isPinned()).isFalse();
    }

    @Test
    void revertToDraft_skipsCascade_whenFollowingIsCancelled() {
        RosterPeriod period1   = rosterPeriod(1L, RosterPeriodStatus.PUBLISHED);
        RosterPeriod cancelled = rosterPeriod(2L, RosterPeriodStatus.CANCELLED);

        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period1));
        when(shiftAssignmentRepository.findByRosterPeriod(period1)).thenReturn(List.of());
        when(rosterPeriodRepository.findByPreviousPeriod(period1)).thenReturn(Optional.of(cancelled));

        service.revertToDraft(1L);

        verify(shiftAssignmentRepository, never()).findByRosterPeriod(cancelled);
    }

    @Test
    void revertToDraft_throws_whenPeriodInDraft() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.DRAFT);
        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.revertToDraft(1L))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void revertToDraft_throws_whenPeriodSolving() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.SOLVING);
        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.revertToDraft(1L))
                .isInstanceOf(InvalidOperationException.class);
    }

    // =========================================================================
    // publish
    // =========================================================================

    @Test
    void publish_setsPublishedAndPinsAssignedSlots() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.SOLVED);
        Staff staff = new Staff();
        ShiftAssignment filled   = assignment(1L, staff, false);
        ShiftAssignment empty    = assignment(2L, null, false);

        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(shiftAssignmentRepository.findByRosterPeriod(period)).thenReturn(List.of(filled, empty));
        when(rosterPeriodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RosterPeriod result = service.publish(1L);

        assertThat(result.getStatus()).isEqualTo(RosterPeriodStatus.PUBLISHED);
        assertThat(filled.isPinned()).isTrue();
        assertThat(empty.isPinned()).isFalse();
    }

    @Test
    void publish_throws_whenNotSolved() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.DRAFT);
        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.publish(1L))
                .isInstanceOf(InvalidOperationException.class);
    }

    // =========================================================================
    // cancel
    // =========================================================================

    @Test
    void cancel_setCancelledStatus_whenDraft() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.DRAFT);
        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(rosterPeriodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RosterPeriod result = service.cancel(1L);

        assertThat(result.getStatus()).isEqualTo(RosterPeriodStatus.CANCELLED);
    }

    @Test
    void cancel_throws_whenSolving() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.SOLVING);
        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.cancel(1L))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void cancel_throws_whenAlreadyCancelled() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.CANCELLED);
        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.cancel(1L))
                .isInstanceOf(InvalidOperationException.class);
    }

    // =========================================================================
    // addShift
    // =========================================================================

    @Test
    void addShift_createsSlots_equalsMinimumStaff() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.DRAFT);
        Shift saved = shift(5L, period);
        var req = new RosterService.ShiftCreateRequest(null, "Morning", T0, T1, 3, null);

        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(shiftRepository.save(any())).thenReturn(saved);

        service.addShift(1L, req);

        verify(shiftAssignmentRepository).saveAll(argThat((List<ShiftAssignment> slots) ->
                slots.size() == 3 && slots.stream().allMatch(s -> s.getStaff() == null && !s.isPinned())));
    }

    @Test
    void addShift_revertsToRequiredDraft_whenPeriodSolved() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.SOLVED);
        Shift saved = shift(5L, period);
        var req = new RosterService.ShiftCreateRequest(null, "Morning", T0, T1, 1, null);

        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(shiftRepository.save(any())).thenReturn(saved);
        when(shiftAssignmentRepository.findByRosterPeriod(period)).thenReturn(List.of());
        when(rosterPeriodRepository.findByPreviousPeriod(period)).thenReturn(Optional.empty());

        service.addShift(1L, req);

        assertThat(period.getStatus()).isEqualTo(RosterPeriodStatus.DRAFT);
    }

    @Test
    void addShift_throws_whenEndBeforeStart() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.DRAFT);
        var req = new RosterService.ShiftCreateRequest(null, "Bad", T1, T0, 1, null);

        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.addShift(1L, req))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void addShift_throws_whenMinimumStaffZero() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.DRAFT);
        var req = new RosterService.ShiftCreateRequest(null, "Bad", T0, T1, 0, null);

        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.addShift(1L, req))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void addShift_throws_whenPeriodCancelled() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.CANCELLED);
        var req = new RosterService.ShiftCreateRequest(null, "Morning", T0, T1, 2, null);

        when(rosterPeriodRepository.findById(1L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.addShift(1L, req))
                .isInstanceOf(InvalidOperationException.class);
    }

    // =========================================================================
    // updateShift — slot reconciliation
    // =========================================================================

    @Test
    void updateShift_addsSlots_whenMinimumIncreased() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.DRAFT);
        Shift shift = shift(5L, period);
        shift.setMinimumStaff(2);
        ShiftAssignment s1 = assignment(1L, null, false);
        ShiftAssignment s2 = assignment(2L, null, false);

        var req = new RosterService.ShiftUpdateRequest(null, "Morning", T0, T1, 4, null);

        when(shiftRepository.findById(5L)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(shiftAssignmentRepository.findByShift(shift)).thenReturn(List.of(s1, s2));

        service.updateShift(5L, req);

        verify(shiftAssignmentRepository).saveAll(argThat((List<ShiftAssignment> slots) ->
                slots.size() == 2));
    }

    @Test
    void updateShift_removesFreeSlotsOnly_whenMinimumDecreased() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.DRAFT);
        Shift shift = shift(5L, period);
        shift.setMinimumStaff(4);
        Staff staff = new Staff();
        ShiftAssignment assigned = assignment(1L, staff, false);
        ShiftAssignment free1    = assignment(2L, null, false);
        ShiftAssignment free2    = assignment(3L, null, false);
        ShiftAssignment free3    = assignment(4L, null, false);

        var req = new RosterService.ShiftUpdateRequest(null, "Morning", T0, T1, 2, null);

        when(shiftRepository.findById(5L)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(shiftAssignmentRepository.findByShift(shift))
                .thenReturn(List.of(assigned, free1, free2, free3));

        service.updateShift(5L, req);

        // 4 total, want 2, 1 assigned → remove 2 free slots, keep assigned
        verify(shiftAssignmentRepository).deleteAll(argThat((List<ShiftAssignment> removed) ->
                removed.size() == 2 &&
                removed.stream().allMatch(s -> s.getStaff() == null && !s.isPinned())));
    }

    @Test
    void updateShift_doesNotRemovePinnedSlots_whenMinimumDecreased() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.DRAFT);
        Shift shift = shift(5L, period);
        shift.setMinimumStaff(3);
        Staff staff = new Staff();
        ShiftAssignment pinned = assignment(1L, staff, true);
        ShiftAssignment pinned2 = assignment(2L, staff, true);
        ShiftAssignment pinned3 = assignment(3L, staff, true);

        var req = new RosterService.ShiftUpdateRequest(null, "Morning", T0, T1, 1, null);

        when(shiftRepository.findById(5L)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(shiftAssignmentRepository.findByShift(shift))
                .thenReturn(List.of(pinned, pinned2, pinned3));

        service.updateShift(5L, req);

        verify(shiftAssignmentRepository, never()).deleteAll(any());
    }

    // =========================================================================
    // removeShift
    // =========================================================================

    @Test
    void removeShift_deletesAllSlotsAndShift() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.DRAFT);
        Shift shift = shift(5L, period);
        ShiftAssignment s1 = assignment(1L, null, false);
        ShiftAssignment s2 = assignment(2L, new Staff(), false);

        when(shiftRepository.findById(5L)).thenReturn(Optional.of(shift));
        when(shiftAssignmentRepository.findByShift(shift)).thenReturn(List.of(s1, s2));

        service.removeShift(5L);

        verify(shiftAssignmentRepository).deleteAll(List.of(s1, s2));
        verify(shiftRepository).delete(shift);
    }

    // =========================================================================
    // pin / unpin
    // =========================================================================

    @Test
    void pin_setsFlag() {
        ShiftAssignment assignment = assignment(1L, new Staff(), false);
        when(shiftAssignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(shiftAssignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShiftAssignment result = service.pin(1L);

        assertThat(result.isPinned()).isTrue();
    }

    @Test
    void unpin_clearsFlag() {
        ShiftAssignment assignment = assignment(1L, new Staff(), true);
        when(shiftAssignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(shiftAssignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShiftAssignment result = service.unpin(1L);

        assertThat(result.isPinned()).isFalse();
    }

    // =========================================================================
    // assignStaff / clearAssignment
    // =========================================================================

    @Test
    void assignStaff_setsStaff_whenNotPinned() {
        Staff staff = new Staff();
        staff.setId(20L);
        ShiftAssignment assignment = assignment(1L, null, false);

        when(shiftAssignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(staffRepository.findById(20L)).thenReturn(Optional.of(staff));
        when(shiftAssignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShiftAssignment result = service.assignStaff(1L, 20L);

        assertThat(result.getStaff()).isEqualTo(staff);
    }

    @Test
    void assignStaff_throws_whenPinned() {
        ShiftAssignment pinned = assignment(1L, new Staff(), true);
        when(shiftAssignmentRepository.findById(1L)).thenReturn(Optional.of(pinned));

        assertThatThrownBy(() -> service.assignStaff(1L, 99L))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void clearAssignment_setsStaffNull() {
        Staff staff = new Staff();
        ShiftAssignment assignment = assignment(1L, staff, false);

        when(shiftAssignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(shiftAssignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShiftAssignment result = service.clearAssignment(1L);

        assertThat(result.getStaff()).isNull();
    }

    @Test
    void clearAssignment_throws_whenPinned() {
        ShiftAssignment pinned = assignment(1L, new Staff(), true);
        when(shiftAssignmentRepository.findById(1L)).thenReturn(Optional.of(pinned));

        assertThatThrownBy(() -> service.clearAssignment(1L))
                .isInstanceOf(InvalidOperationException.class);
    }

    // =========================================================================
    // addQualificationRequirement
    // =========================================================================

    @Test
    void addQualificationRequirement_throws_whenDuplicate() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.DRAFT);
        Shift shift = shift(5L, period);
        Qualification qual = new Qualification();
        qual.setId(7L);

        when(shiftRepository.findById(5L)).thenReturn(Optional.of(shift));
        when(qualificationRepository.findById(7L)).thenReturn(Optional.of(qual));
        when(shiftQualRequirementRepository.existsByShiftAndQualification(shift, qual)).thenReturn(true);

        assertThatThrownBy(() -> service.addQualificationRequirement(5L, 7L, 1))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void addQualificationRequirement_throws_whenMinimumCountZero() {
        RosterPeriod period = rosterPeriod(1L, RosterPeriodStatus.DRAFT);
        Shift shift = shift(5L, period);
        Qualification qual = new Qualification();
        qual.setId(7L);

        when(shiftRepository.findById(5L)).thenReturn(Optional.of(shift));
        when(qualificationRepository.findById(7L)).thenReturn(Optional.of(qual));

        assertThatThrownBy(() -> service.addQualificationRequirement(5L, 7L, 0))
                .isInstanceOf(InvalidOperationException.class);
    }

    // =========================================================================
    // EntityNotFoundException paths
    // =========================================================================

    @Test
    void getById_throws_whenPeriodMissing() {
        when(rosterPeriodRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static RosterPeriod rosterPeriod(Long id, RosterPeriodStatus status) {
        RosterPeriod p = new RosterPeriod();
        p.setId(id);
        p.setStatus(status);
        return p;
    }

    private static Shift shift(Long id, RosterPeriod period) {
        Shift s = new Shift();
        s.setId(id);
        s.setRosterPeriod(period);
        s.setStartDatetime(T0);
        s.setEndDatetime(T1);
        s.setMinimumStaff(1);
        return s;
    }

    private static ShiftAssignment assignment(Long id, Staff staff, boolean pinned) {
        ShiftAssignment a = new ShiftAssignment();
        a.setId(id);
        a.setStaff(staff);
        a.setPinned(pinned);
        return a;
    }
}
