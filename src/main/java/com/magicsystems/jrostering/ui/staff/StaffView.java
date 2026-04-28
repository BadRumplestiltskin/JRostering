package com.magicsystems.jrostering.ui.staff;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.StaffIncompatibilityRepository;
import com.magicsystems.jrostering.repository.StaffPairingRepository;
import com.magicsystems.jrostering.repository.StaffQualificationRepository;
import com.magicsystems.jrostering.repository.StaffSiteAssignmentRepository;
import com.magicsystems.jrostering.domain.StaffIncompatibility;
import com.magicsystems.jrostering.domain.StaffPairing;
import com.magicsystems.jrostering.service.OrganisationService;
import com.magicsystems.jrostering.service.QualificationService;
import com.magicsystems.jrostering.service.SiteService;
import com.magicsystems.jrostering.service.StaffAssignmentService;
import com.magicsystems.jrostering.service.StaffQualificationService;
import com.magicsystems.jrostering.service.StaffRelationshipService;
import com.magicsystems.jrostering.service.StaffService;
import com.magicsystems.jrostering.service.StaffService.StaffCreateRequest;
import com.magicsystems.jrostering.service.StaffService.StaffUpdateRequest;
import com.magicsystems.jrostering.ui.MainLayout;
import com.magicsystems.jrostering.ui.ViewUtils;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Staff management view — CRUD for staff members plus their qualifications,
 * availability, preferences, and leave.
 */
@Route(value = "staff", layout = MainLayout.class)
@PageTitle("Staff — JRostering")
@PermitAll
@SuppressWarnings("serial")
public class StaffView extends VerticalLayout {

    private final StaffService              staffService;
    private final StaffQualificationService qualificationService;
    private final StaffAssignmentService    assignmentService;
    private final StaffRelationshipService  relationshipService;
    private final OrganisationService       organisationService;
    private final QualificationService      qualificationReadService;
    private final SiteService               siteService;
    private final StaffQualificationRepository   staffQualificationRepository;
    private final StaffSiteAssignmentRepository  staffSiteAssignmentRepository;
    private final StaffIncompatibilityRepository staffIncompatibilityRepository;
    private final StaffPairingRepository         staffPairingRepository;

    private final Grid<Staff>    staffGrid   = new Grid<>(Staff.class, false);
    private final VerticalLayout detailPanel = new VerticalLayout();

    private Long  orgId;
    private Staff selectedStaff;

    public StaffView(StaffService staffService,
                     StaffQualificationService qualificationService,
                     StaffAssignmentService assignmentService,
                     StaffRelationshipService relationshipService,
                     OrganisationService organisationService,
                     QualificationService qualificationReadService,
                     SiteService siteService,
                     StaffQualificationRepository staffQualificationRepository,
                     StaffSiteAssignmentRepository staffSiteAssignmentRepository,
                     StaffIncompatibilityRepository staffIncompatibilityRepository,
                     StaffPairingRepository staffPairingRepository) {
        this.staffService               = staffService;
        this.qualificationService       = qualificationService;
        this.assignmentService          = assignmentService;
        this.relationshipService        = relationshipService;
        this.organisationService        = organisationService;
        this.qualificationReadService   = qualificationReadService;
        this.siteService                = siteService;
        this.staffQualificationRepository   = staffQualificationRepository;
        this.staffSiteAssignmentRepository  = staffSiteAssignmentRepository;
        this.staffIncompatibilityRepository = staffIncompatibilityRepository;
        this.staffPairingRepository         = staffPairingRepository;

        orgId = organisationService.currentOrganisationId();

        setPadding(true);
        add(new H2("Staff"));
        add(buildToolbar());
        add(buildStaffGrid());
        detailPanel.setVisible(false);
        detailPanel.setPadding(false);
        add(detailPanel);

        refreshStaffGrid();
    }

    // =========================================================================
    // Staff grid
    // =========================================================================

    private HorizontalLayout buildToolbar() {
        Button addBtn  = new Button("Add Staff",        e -> openStaffDialog(null));
        Button editBtn = new Button("Edit",             e -> openStaffDialog(selectedStaff));
        Button deactivateBtn = new Button("Deactivate", e -> deactivateSelected());

        editBtn.setEnabled(false);
        deactivateBtn.setEnabled(false);
        deactivateBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

        staffGrid.addSelectionListener(ev -> {
            Optional<Staff> sel = ev.getFirstSelectedItem();
            selectedStaff = sel.orElse(null);
            editBtn.setEnabled(sel.isPresent());
            deactivateBtn.setEnabled(sel.isPresent());
            sel.ifPresentOrElse(this::showDetail, () -> {
                detailPanel.setVisible(false);
                detailPanel.removeAll();
            });
        });

        return new HorizontalLayout(addBtn, editBtn, deactivateBtn);
    }

    private Grid<Staff> buildStaffGrid() {
        staffGrid.addColumn(Staff::getFirstName).setHeader("First Name").setAutoWidth(true);
        staffGrid.addColumn(Staff::getLastName).setHeader("Last Name").setAutoWidth(true);
        staffGrid.addColumn(Staff::getEmail).setHeader("Email").setAutoWidth(true);
        staffGrid.addColumn(s -> s.getEmploymentType() != null ? s.getEmploymentType().name() : "")
                .setHeader("Employment").setAutoWidth(true);
        staffGrid.addColumn(s -> s.isActive() ? "Active" : "Inactive")
                .setHeader("Status").setAutoWidth(true);
        staffGrid.setHeight("300px");
        return staffGrid;
    }

    private void refreshStaffGrid() {
        if (orgId == null) return;
        staffGrid.setItems(staffService.getAllActiveByOrganisation(orgId));
    }

    private void deactivateSelected() {
        if (selectedStaff == null) return;
        try {
            staffService.deactivate(selectedStaff.getId());
            ViewUtils.notify("Staff member deactivated.", NotificationVariant.LUMO_SUCCESS);
            selectedStaff = null;
            detailPanel.setVisible(false);
            detailPanel.removeAll();
            refreshStaffGrid();
        } catch (Exception ex) {
            ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

    // =========================================================================
    // Add / Edit dialog
    // =========================================================================

    private void openStaffDialog(Staff existing) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existing == null ? "Add Staff Member" : "Edit Staff Member");
        dialog.setWidth("480px");

        TextField firstName = new TextField("First Name");
        TextField lastName  = new TextField("Last Name");
        TextField email     = new TextField("Email");
        TextField phone     = new TextField("Phone");
        Select<EmploymentType> employment = new Select<>();
        employment.setLabel("Employment Type");
        employment.setItems(EmploymentType.values());
        NumberField contracted = new NumberField("Contracted Hours/Week");
        NumberField hourly     = new NumberField("Hourly Rate");

        if (existing != null) {
            firstName.setValue(existing.getFirstName());
            lastName.setValue(existing.getLastName());
            email.setValue(existing.getEmail());
            if (existing.getPhone() != null) phone.setValue(existing.getPhone());
            employment.setValue(existing.getEmploymentType());
            if (existing.getContractedHoursPerWeek() != null)
                contracted.setValue(existing.getContractedHoursPerWeek().doubleValue());
            if (existing.getHourlyRate() != null)
                hourly.setValue(existing.getHourlyRate().doubleValue());
        }

        FormLayout form = new FormLayout(firstName, lastName, email, phone, employment, contracted, hourly);
        dialog.add(form);

        Button save   = new Button("Save",   e -> {
            try {
                BigDecimal ch = contracted.getValue() != null
                        ? BigDecimal.valueOf(contracted.getValue()) : null;
                BigDecimal hr = hourly.getValue() != null
                        ? BigDecimal.valueOf(hourly.getValue()) : null;

                if (existing == null) {
                    staffService.create(orgId, new StaffCreateRequest(
                            firstName.getValue(), lastName.getValue(), email.getValue(),
                            phone.getValue().isBlank() ? null : phone.getValue(),
                            employment.getValue(), ch, hr));
                } else {
                    staffService.update(existing.getId(), new StaffUpdateRequest(
                            firstName.getValue(), lastName.getValue(), email.getValue(),
                            phone.getValue().isBlank() ? null : phone.getValue(),
                            employment.getValue(), ch, hr));
                }
                dialog.close();
                refreshStaffGrid();
                ViewUtils.notify("Saved.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });
        Button cancel = new Button("Cancel", e -> dialog.close());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    // =========================================================================
    // Detail panel with tabs
    // =========================================================================

    private void showDetail(Staff staff) {
        detailPanel.removeAll();
        detailPanel.add(new H3(staff.getFirstName() + " " + staff.getLastName() + " — Details"));

        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add("Qualifications",    buildQualificationsTab(staff));
        tabs.add("Site Assignments",  buildSiteAssignmentsTab(staff));
        tabs.add("Incompatibilities", buildIncompatibilitiesTab(staff));
        tabs.add("Pairings",          buildPairingsTab(staff));
        tabs.add("Availability",      buildAvailabilityTab(staff));
        tabs.add("Preferences",       buildPreferencesTab(staff));
        tabs.add("Leave",             buildLeaveTab(staff));

        detailPanel.add(tabs);
        detailPanel.setVisible(true);
    }

    // -- Qualifications tab --

    private VerticalLayout buildQualificationsTab(Staff staff) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        Grid<StaffQualification> grid = new Grid<>(StaffQualification.class, false);
        grid.addColumn(sq -> sq.getQualification().getName()).setHeader("Qualification").setAutoWidth(true);
        grid.addColumn(sq -> sq.getAwardedDate() != null ? sq.getAwardedDate().toString() : "—")
                .setHeader("Awarded").setAutoWidth(true);
        grid.setAllRowsVisible(true);

        refreshQualGrid(grid, staff);

        ComboBox<Qualification> qualBox = new ComboBox<>("Add Qualification");
        qualBox.setItems(qualificationReadService.getAll());
        qualBox.setItemLabelGenerator(Qualification::getName);

        DatePicker awardedDate = new DatePicker("Awarded Date");

        Button addBtn = new Button("Add", e -> {
            if (qualBox.getValue() == null) return;
            try {
                qualificationService.addQualification(staff.getId(), qualBox.getValue().getId(),
                        awardedDate.getValue());
                refreshQualGrid(grid, staff);
                qualBox.clear();
                awardedDate.clear();
                ViewUtils.notify("Qualification added.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        Button removeBtn = new Button("Remove Selected");
        removeBtn.setEnabled(false);
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        grid.addSelectionListener(ev -> removeBtn.setEnabled(ev.getFirstSelectedItem().isPresent()));
        removeBtn.addClickListener(e -> grid.getSelectedItems().stream().findFirst().ifPresent(sq -> {
            try {
                qualificationService.removeQualification(staff.getId(), sq.getQualification().getId());
                refreshQualGrid(grid, staff);
                ViewUtils.notify("Qualification removed.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        }));

        layout.add(grid, new HorizontalLayout(qualBox, awardedDate, addBtn, removeBtn));
        return layout;
    }

    private void refreshQualGrid(Grid<StaffQualification> grid, Staff staff) {
        Staff fresh = staffService.getById(staff.getId());
        grid.setItems(staffQualificationRepository.findByStaff(fresh));
    }

    private void refreshSiteGrid(Grid<StaffSiteAssignment> grid, Staff staff) {
        Staff fresh = staffService.getById(staff.getId());
        grid.setItems(staffSiteAssignmentRepository.findByStaff(fresh));
    }

    // -- Site Assignments tab --

    private VerticalLayout buildSiteAssignmentsTab(Staff staff) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        Grid<StaffSiteAssignment> grid = new Grid<>(StaffSiteAssignment.class, false);
        grid.addColumn(a -> a.getSite().getName()).setHeader("Site").setAutoWidth(true);
        grid.addColumn(StaffSiteAssignment::isPrimarySite).setHeader("Primary").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        refreshSiteGrid(grid, staff);

        ComboBox<Site> siteBox = new ComboBox<>("Site");
        siteBox.setItems(siteService.getAllActive());
        siteBox.setItemLabelGenerator(Site::getName);
        Checkbox primary = new Checkbox("Primary Site");

        Button addBtn = new Button("Add", e -> {
            if (siteBox.getValue() == null) return;
            try {
                assignmentService.addSiteAssignment(staff.getId(), siteBox.getValue().getId(),
                        primary.getValue());
                refreshSiteGrid(grid, staff);
                siteBox.clear();
                primary.setValue(false);
                ViewUtils.notify("Site assignment added.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        Button removeBtn = new Button("Remove Selected");
        removeBtn.setEnabled(false);
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        grid.addSelectionListener(ev -> removeBtn.setEnabled(ev.getFirstSelectedItem().isPresent()));
        removeBtn.addClickListener(e -> grid.getSelectedItems().stream().findFirst().ifPresent(assignment -> {
            try {
                assignmentService.removeSiteAssignment(staff.getId(), assignment.getSite().getId());
                refreshSiteGrid(grid, staff);
                ViewUtils.notify("Site assignment removed.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        }));

        layout.add(grid, new HorizontalLayout(siteBox, primary, addBtn, removeBtn));
        return layout;
    }

    // -- Incompatibilities tab --

    private VerticalLayout buildIncompatibilitiesTab(Staff staff) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        Grid<StaffIncompatibility> grid = new Grid<>(StaffIncompatibility.class, false);
        grid.addColumn(i -> {
            Staff other = i.getStaffA().getId().equals(staff.getId()) ? i.getStaffB() : i.getStaffA();
            return other.getFirstName() + " " + other.getLastName();
        }).setHeader("Other Staff").setAutoWidth(true);
        grid.addColumn(i -> i.getReason() != null ? i.getReason() : "—")
                .setHeader("Reason").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        refreshIncompatibilitiesGrid(grid, staff);

        ComboBox<Staff> otherStaff = new ComboBox<>("Staff");
        otherStaff.setItems(staffService.getAllActiveByOrganisation(orgId).stream()
                .filter(s -> !s.getId().equals(staff.getId())).toList());
        otherStaff.setItemLabelGenerator(s -> s.getFirstName() + " " + s.getLastName());

        TextField reason = new TextField("Reason (optional)");

        Button addBtn = new Button("Add", e -> {
            if (otherStaff.getValue() == null) return;
            try {
                relationshipService.addIncompatibility(staff.getId(), otherStaff.getValue().getId(),
                        reason.getValue().isBlank() ? null : reason.getValue());
                refreshIncompatibilitiesGrid(grid, staff);
                otherStaff.clear();
                reason.clear();
                ViewUtils.notify("Incompatibility added.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        Button removeBtn = new Button("Remove Selected");
        removeBtn.setEnabled(false);
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        grid.addSelectionListener(ev -> removeBtn.setEnabled(ev.getFirstSelectedItem().isPresent()));
        removeBtn.addClickListener(e -> grid.getSelectedItems().stream().findFirst().ifPresent(selectedRow -> {
            try {
                Long otherId = selectedRow.getStaffA().getId().equals(staff.getId())
                        ? selectedRow.getStaffB().getId() : selectedRow.getStaffA().getId();
                relationshipService.removeIncompatibility(staff.getId(), otherId);
                refreshIncompatibilitiesGrid(grid, staff);
                ViewUtils.notify("Incompatibility removed.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        }));

        layout.add(grid, new HorizontalLayout(otherStaff, reason, addBtn, removeBtn));
        return layout;
    }

    private void refreshIncompatibilitiesGrid(Grid<StaffIncompatibility> grid, Staff staff) {
        Staff fresh = staffService.getById(staff.getId());
        grid.setItems(staffIncompatibilityRepository.findByStaff(fresh));
    }

    // -- Pairings tab --

    private VerticalLayout buildPairingsTab(Staff staff) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        Grid<StaffPairing> grid = new Grid<>(StaffPairing.class, false);
        grid.addColumn(p -> {
            Staff other = p.getStaffA().getId().equals(staff.getId()) ? p.getStaffB() : p.getStaffA();
            return other.getFirstName() + " " + other.getLastName();
        }).setHeader("Other Staff").setAutoWidth(true);
        grid.addColumn(p -> p.getReason() != null ? p.getReason() : "—")
                .setHeader("Reason").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        refreshPairingsGrid(grid, staff);

        ComboBox<Staff> otherStaff = new ComboBox<>("Staff");
        otherStaff.setItems(staffService.getAllActiveByOrganisation(orgId).stream()
                .filter(s -> !s.getId().equals(staff.getId())).toList());
        otherStaff.setItemLabelGenerator(s -> s.getFirstName() + " " + s.getLastName());

        TextField reason = new TextField("Reason (optional)");

        Button addBtn = new Button("Add", e -> {
            if (otherStaff.getValue() == null) return;
            try {
                relationshipService.addPairing(staff.getId(), otherStaff.getValue().getId(),
                        reason.getValue().isBlank() ? null : reason.getValue());
                refreshPairingsGrid(grid, staff);
                otherStaff.clear();
                reason.clear();
                ViewUtils.notify("Pairing added.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        Button removeBtn = new Button("Remove Selected");
        removeBtn.setEnabled(false);
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        grid.addSelectionListener(ev -> removeBtn.setEnabled(ev.getFirstSelectedItem().isPresent()));
        removeBtn.addClickListener(e -> grid.getSelectedItems().stream().findFirst().ifPresent(selectedRow -> {
            try {
                Long otherId = selectedRow.getStaffA().getId().equals(staff.getId())
                        ? selectedRow.getStaffB().getId() : selectedRow.getStaffA().getId();
                relationshipService.removePairing(staff.getId(), otherId);
                refreshPairingsGrid(grid, staff);
                ViewUtils.notify("Pairing removed.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        }));

        layout.add(grid, new HorizontalLayout(otherStaff, reason, addBtn, removeBtn));
        return layout;
    }

    private void refreshPairingsGrid(Grid<StaffPairing> grid, Staff staff) {
        Staff fresh = staffService.getById(staff.getId());
        grid.setItems(staffPairingRepository.findByStaff(fresh));
    }

    // -- Availability tab --

    private VerticalLayout buildAvailabilityTab(Staff staff) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        Grid<StaffAvailability> grid = new Grid<>(StaffAvailability.class, false);
        grid.addColumn(a -> a.getDayOfWeek().name()).setHeader("Day").setAutoWidth(true);
        grid.addColumn(a -> a.getStartTime() + "–" + a.getEndTime()).setHeader("Window").setAutoWidth(true);
        grid.addColumn(a -> a.isAvailable() ? "Available" : "Unavailable").setHeader("Type").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        grid.setItems(staffService.getAvailability(staff.getId()));

        Select<DayOfWeek> daySelect = new Select<>();
        daySelect.setLabel("Day");
        daySelect.setItems(DayOfWeek.values());
        TextField startField = new TextField("Start (HH:mm)");
        TextField endField   = new TextField("End (HH:mm)");
        Checkbox available   = new Checkbox("Available");
        available.setValue(true);

        Button addBtn = new Button("Add", e -> {
            try {
                staffService.addAvailability(staff.getId(), daySelect.getValue(),
                        LocalTime.parse(startField.getValue()),
                        LocalTime.parse(endField.getValue()),
                        available.getValue());
                grid.setItems(staffService.getAvailability(staff.getId()));
                startField.clear(); endField.clear();
                ViewUtils.notify("Availability added.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        Button removeBtn = new Button("Remove Selected");
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        removeBtn.setEnabled(false);
        grid.addSelectionListener(ev -> removeBtn.setEnabled(ev.getFirstSelectedItem().isPresent()));
        removeBtn.addClickListener(e -> grid.getSelectedItems().stream().findFirst().ifPresent(a -> {
            try {
                staffService.removeAvailability(a.getId());
                grid.setItems(staffService.getAvailability(staff.getId()));
                ViewUtils.notify("Availability removed.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        }));

        layout.add(grid, new HorizontalLayout(daySelect, startField, endField, available, addBtn, removeBtn));
        return layout;
    }

    // -- Preferences tab --

    private VerticalLayout buildPreferencesTab(Staff staff) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        Grid<StaffPreference> grid = new Grid<>(StaffPreference.class, false);
        grid.addColumn(p -> p.getPreferenceType().name()).setHeader("Type").setAutoWidth(true);
        grid.addColumn(p -> p.getDayOfWeek() != null ? p.getDayOfWeek().name() : "—")
                .setHeader("Day").setAutoWidth(true);
        grid.addColumn(p -> p.getShiftType() != null ? p.getShiftType().getName() : "—")
                .setHeader("Shift Type").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        grid.setItems(staffService.getPreferences(staff.getId()));

        Select<PreferenceType> prefType = new Select<>();
        prefType.setLabel("Preference Type");
        prefType.setItems(PreferenceType.values());

        Select<DayOfWeek> daySelect = new Select<>();
        daySelect.setLabel("Day (for DAY_OFF)");
        daySelect.setItems(DayOfWeek.values());

        Button addBtn = new Button("Add", e -> {
            try {
                staffService.addPreference(staff.getId(), prefType.getValue(),
                        daySelect.getValue(), null);
                grid.setItems(staffService.getPreferences(staff.getId()));
                ViewUtils.notify("Preference added.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        Button removeBtn = new Button("Remove Selected");
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        removeBtn.setEnabled(false);
        grid.addSelectionListener(ev -> removeBtn.setEnabled(ev.getFirstSelectedItem().isPresent()));
        removeBtn.addClickListener(e -> grid.getSelectedItems().stream().findFirst().ifPresent(p -> {
            try {
                staffService.removePreference(p.getId());
                grid.setItems(staffService.getPreferences(staff.getId()));
                ViewUtils.notify("Preference removed.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        }));

        layout.add(grid, new HorizontalLayout(prefType, daySelect, addBtn, removeBtn));
        return layout;
    }

    // -- Leave tab --

    private VerticalLayout buildLeaveTab(Staff staff) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        Grid<Leave> grid = new Grid<>(Leave.class, false);
        grid.addColumn(l -> l.getStartDate() + " – " + l.getEndDate()).setHeader("Period").setAutoWidth(true);
        grid.addColumn(l -> l.getLeaveType().name()).setHeader("Type").setAutoWidth(true);
        grid.addColumn(l -> l.getStatus().name()).setHeader("Status").setAutoWidth(true);
        grid.addColumn(l -> l.getNotes() != null ? l.getNotes() : "—").setHeader("Notes").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        grid.setItems(staffService.getLeave(staff.getId()));

        DatePicker startDate   = new DatePicker("Start Date");
        DatePicker endDate     = new DatePicker("End Date");
        Select<LeaveType> type = new Select<>();
        type.setLabel("Type"); type.setItems(LeaveType.values());
        TextField notes        = new TextField("Notes");

        Button addBtn = new Button("Request Leave", e -> {
            try {
                staffService.addLeave(staff.getId(), startDate.getValue(),
                        endDate.getValue(), type.getValue(),
                        notes.getValue().isBlank() ? null : notes.getValue());
                grid.setItems(staffService.getLeave(staff.getId()));
                startDate.clear(); endDate.clear(); notes.clear();
                ViewUtils.notify("Leave requested.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        Button approveBtn = new Button("Approve");
        Button rejectBtn  = new Button("Reject");
        Button cancelBtn2 = new Button("Cancel Request");
        approveBtn.setEnabled(false);
        rejectBtn.setEnabled(false);
        cancelBtn2.setEnabled(false);
        approveBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        cancelBtn2.addThemeVariants(ButtonVariant.LUMO_ERROR);

        grid.addSelectionListener(ev -> {
            boolean sel = ev.getFirstSelectedItem().isPresent();
            approveBtn.setEnabled(sel);
            rejectBtn.setEnabled(sel);
            cancelBtn2.setEnabled(sel);
        });

        approveBtn.addClickListener(e -> grid.getSelectedItems().stream().findFirst().ifPresent(l -> {
            try {
                staffService.updateLeaveStatus(l.getId(), LeaveStatus.APPROVED);
                grid.setItems(staffService.getLeave(staff.getId()));
                ViewUtils.notify("Leave approved.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        }));

        rejectBtn.addClickListener(e -> grid.getSelectedItems().stream().findFirst().ifPresent(l -> {
            try {
                staffService.updateLeaveStatus(l.getId(), LeaveStatus.REJECTED);
                grid.setItems(staffService.getLeave(staff.getId()));
                ViewUtils.notify("Leave rejected.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        }));

        cancelBtn2.addClickListener(e -> grid.getSelectedItems().stream().findFirst().ifPresent(l -> {
            try {
                staffService.removeLeave(l.getId());
                grid.setItems(staffService.getLeave(staff.getId()));
                ViewUtils.notify("Leave request cancelled.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        }));

        layout.add(grid,
                new HorizontalLayout(startDate, endDate, type, notes, addBtn),
                new HorizontalLayout(approveBtn, rejectBtn, cancelBtn2));
        return layout;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

}
