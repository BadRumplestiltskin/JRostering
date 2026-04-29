package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.Qualification;
import com.magicsystems.jrostering.domain.Staff;
import com.magicsystems.jrostering.domain.StaffQualification;
import com.magicsystems.jrostering.repository.QualificationRepository;
import com.magicsystems.jrostering.repository.StaffQualificationRepository;
import com.magicsystems.jrostering.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Manages qualification records for {@link Staff}.
 * Extracted from {@link StaffService} to keep constructor injection size manageable.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StaffQualificationService {

    private final StaffRepository              staffRepository;
    private final QualificationRepository      qualificationRepository;
    private final StaffQualificationRepository staffQualificationRepository;

    /**
     * Returns all qualifications held by the given staff member.
     *
     * @throws EntityNotFoundException if the staff member does not exist
     */
    public List<StaffQualification> getQualificationsForStaff(Long staffId) {
        return staffQualificationRepository.findByStaff(requireStaff(staffId));
    }

    /**
     * Records that a staff member holds a given qualification.
     *
     * @throws EntityNotFoundException   if the staff member or qualification does not exist
     * @throws InvalidOperationException if the staff member already holds this qualification
     */
    @Transactional
    public StaffQualification addQualification(Long staffId, Long qualificationId, LocalDate awardedDate) {
        Staff staff        = requireStaff(staffId);
        Qualification qual = requireQualification(qualificationId);

        if (staffQualificationRepository.existsByStaffAndQualification(staff, qual)) {
            throw new InvalidOperationException(
                    "Staff id=" + staffId + " already holds qualification id=" + qualificationId);
        }

        StaffQualification sq = new StaffQualification();
        sq.setStaff(staff);
        sq.setQualification(qual);
        sq.setAwardedDate(awardedDate);
        return staffQualificationRepository.save(sq);
    }

    /**
     * Removes a qualification record from a staff member.
     *
     * @throws EntityNotFoundException if the staff member does not hold this qualification
     */
    @Transactional
    public void removeQualification(Long staffId, Long qualificationId) {
        Staff staff        = requireStaff(staffId);
        Qualification qual = requireQualification(qualificationId);

        StaffQualification sq = staffQualificationRepository
                .findByStaffAndQualification(staff, qual)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Staff id=" + staffId + " does not hold qualification id=" + qualificationId));

        staffQualificationRepository.delete(sq);
    }

    private Staff requireStaff(Long id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("Staff", id));
    }

    private Qualification requireQualification(Long id) {
        return qualificationRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("Qualification", id));
    }
}
