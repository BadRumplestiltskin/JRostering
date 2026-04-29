package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.Site;
import com.magicsystems.jrostering.domain.Staff;
import com.magicsystems.jrostering.domain.StaffSiteAssignment;
import com.magicsystems.jrostering.repository.SiteRepository;
import com.magicsystems.jrostering.repository.StaffRepository;
import com.magicsystems.jrostering.repository.StaffSiteAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages site assignments for {@link Staff}.
 * Extracted from {@link StaffService} to keep constructor injection size manageable.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StaffAssignmentService {

    private final StaffRepository               staffRepository;
    private final SiteRepository                siteRepository;
    private final StaffSiteAssignmentRepository staffSiteAssignmentRepository;

    /**
     * Returns all site assignments for the given staff member.
     *
     * @throws EntityNotFoundException if the staff member does not exist
     */
    public List<StaffSiteAssignment> getSiteAssignmentsForStaff(Long staffId) {
        return staffSiteAssignmentRepository.findByStaff(requireStaff(staffId));
    }

    /**
     * Assigns a staff member to a site.
     *
     * @throws EntityNotFoundException   if the staff member or site does not exist
     * @throws InvalidOperationException if the staff member is already assigned to this site
     */
    @Transactional
    public StaffSiteAssignment addSiteAssignment(Long staffId, Long siteId, boolean primarySite) {
        Staff staff = requireStaff(staffId);
        Site site   = requireSite(siteId);

        if (staffSiteAssignmentRepository.existsByStaffAndSite(staff, site)) {
            throw new InvalidOperationException(
                    "Staff id=" + staffId + " is already assigned to site id=" + siteId);
        }

        StaffSiteAssignment assignment = new StaffSiteAssignment();
        assignment.setStaff(staff);
        assignment.setSite(site);
        assignment.setPrimarySite(primarySite);
        return staffSiteAssignmentRepository.save(assignment);
    }

    /**
     * Removes a staff member's assignment to a site.
     *
     * @throws EntityNotFoundException if the assignment does not exist
     */
    @Transactional
    public void removeSiteAssignment(Long staffId, Long siteId) {
        Staff staff = requireStaff(staffId);
        Site site   = requireSite(siteId);

        StaffSiteAssignment assignment = staffSiteAssignmentRepository
                .findByStaffAndSite(staff, site)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No site assignment found for staff id=" + staffId + " and site id=" + siteId));

        staffSiteAssignmentRepository.delete(assignment);
    }

    private Staff requireStaff(Long id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("Staff", id));
    }

    private Site requireSite(Long id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("Site", id));
    }
}
