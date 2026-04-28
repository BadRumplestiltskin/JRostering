package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.Staff;
import com.magicsystems.jrostering.domain.StaffIncompatibility;
import com.magicsystems.jrostering.domain.StaffPairing;
import com.magicsystems.jrostering.repository.StaffIncompatibilityRepository;
import com.magicsystems.jrostering.repository.StaffPairingRepository;
import com.magicsystems.jrostering.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages incompatibility and pairing relationships between {@link Staff} members.
 * Extracted from {@link StaffService} to keep constructor injection size manageable.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StaffRelationshipService {

    private final StaffRepository                staffRepository;
    private final StaffIncompatibilityRepository staffIncompatibilityRepository;
    private final StaffPairingRepository         staffPairingRepository;

    /**
     * Records that two staff members must never share a shift.
     *
     * <p>Canonical ordering ({@code staffA.id < staffB.id}) is enforced internally;
     * the caller may supply the IDs in either order.</p>
     *
     * @throws EntityNotFoundException   if either staff member does not exist
     * @throws InvalidOperationException if the pair are already recorded as incompatible,
     *                                   or if both IDs are the same
     */
    @Transactional
    public StaffIncompatibility addIncompatibility(Long staffIdOne, Long staffIdTwo, String reason) {
        requireDistinct(staffIdOne, staffIdTwo);
        Staff lower  = requireStaff(Math.min(staffIdOne, staffIdTwo));
        Staff higher = requireStaff(Math.max(staffIdOne, staffIdTwo));

        if (staffIncompatibilityRepository.existsByStaffAAndStaffB(lower, higher)) {
            throw new InvalidOperationException(
                    "Staff id=" + lower.getId() + " and id=" + higher.getId()
                    + " are already recorded as incompatible.");
        }

        StaffIncompatibility incompatibility = new StaffIncompatibility();
        incompatibility.setStaffA(lower);
        incompatibility.setStaffB(higher);
        incompatibility.setReason(reason);
        return staffIncompatibilityRepository.save(incompatibility);
    }

    /**
     * Removes an incompatibility record between two staff members.
     *
     * @throws EntityNotFoundException if no incompatibility record exists for this pair
     */
    @Transactional
    public void removeIncompatibility(Long staffIdOne, Long staffIdTwo) {
        Staff lower  = requireStaff(Math.min(staffIdOne, staffIdTwo));
        Staff higher = requireStaff(Math.max(staffIdOne, staffIdTwo));

        StaffIncompatibility record = staffIncompatibilityRepository
                .findByStaffAAndStaffB(lower, higher)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No incompatibility record found for staff id=" + lower.getId()
                        + " and id=" + higher.getId()));

        staffIncompatibilityRepository.delete(record);
    }

    /**
     * Records that two staff members must always share a shift.
     *
     * <p>Canonical ordering ({@code staffA.id < staffB.id}) is enforced internally.</p>
     *
     * @throws EntityNotFoundException   if either staff member does not exist
     * @throws InvalidOperationException if the pair are already recorded as paired,
     *                                   or if both IDs are the same
     */
    @Transactional
    public StaffPairing addPairing(Long staffIdOne, Long staffIdTwo, String reason) {
        requireDistinct(staffIdOne, staffIdTwo);
        Staff lower  = requireStaff(Math.min(staffIdOne, staffIdTwo));
        Staff higher = requireStaff(Math.max(staffIdOne, staffIdTwo));

        if (staffPairingRepository.existsByStaffAAndStaffB(lower, higher)) {
            throw new InvalidOperationException(
                    "Staff id=" + lower.getId() + " and id=" + higher.getId()
                    + " are already recorded as a required pair.");
        }

        StaffPairing pairing = new StaffPairing();
        pairing.setStaffA(lower);
        pairing.setStaffB(higher);
        pairing.setReason(reason);
        return staffPairingRepository.save(pairing);
    }

    /**
     * Removes a pairing record between two staff members.
     *
     * @throws EntityNotFoundException if no pairing record exists for this pair
     */
    @Transactional
    public void removePairing(Long staffIdOne, Long staffIdTwo) {
        Staff lower  = requireStaff(Math.min(staffIdOne, staffIdTwo));
        Staff higher = requireStaff(Math.max(staffIdOne, staffIdTwo));

        StaffPairing pairing = staffPairingRepository
                .findByStaffAAndStaffB(lower, higher)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No pairing record found for staff id=" + lower.getId()
                        + " and id=" + higher.getId()));

        staffPairingRepository.delete(pairing);
    }

    private Staff requireStaff(Long id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("Staff", id));
    }

    private void requireDistinct(Long idOne, Long idTwo) {
        if (idOne.equals(idTwo)) {
            throw new InvalidOperationException(
                    "A staff member cannot have an incompatibility or pairing with themselves.");
        }
    }
}
