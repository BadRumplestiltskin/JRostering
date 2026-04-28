package com.magicsystems.jrostering.ui.roster;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.OrganisationRepository;
import com.magicsystems.jrostering.service.QualificationService;
import com.magicsystems.jrostering.service.RosterService;
import com.magicsystems.jrostering.service.RosterService.ShiftCreateRequest;
import com.magicsystems.jrostering.service.RosterService.ShiftUpdateRequest;
import com.magicsystems.jrostering.service.SiteService;
import com.magicsystems.jrostering.service.SolverService;
import com.magicsystems.jrostering.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Roster management view — period lifecycle, shift management, qualification
 * requirements, solver controls, and solved assignment viewer.
 */
@Route(value = "roster", layout = MainLayout.class)
@PageTitle("Roster — JRostering")
@PermitAll
@SuppressWarnings("serial")
public class RosterView extends VerticalLayout {

    private final SiteService             siteService;
    private final RosterService           rosterService;
    private final SolverService           solverService;
    private final OrganisationRepository  organisationRepository;
    private final QualificationService    qualificationService;

    private final ComboBox<Site>              siteSelector      = new ComboBox<>("Site");
    private final Grid<RosterPeriod>          periodGrid        = new Grid<>(RosterPeriod.class, false);
    private final VerticalLayout              shiftPanel        = new VerticalLayout();
    private final Grid<Shift>                 shiftGrid         = new Grid<>(Shift.class, false);
    private final VerticalLayout              solverPanel       = new VerticalLayout();
    private final Span                        solverStatus      = new Span();
    private final VerticalLayout              assignmentsPanel  = new VerticalLayout();
    private final Grid<ShiftAssignment>       assignmentsGrid   = new Grid<>(ShiftAssignment.class, false);

    private RosterPeriod     selectedPeriod;
    private Shift            selectedShift;
    private ShiftAssignment  selectedAssignment;
    private Long             lastJobId;

    public RosterView(SiteService siteService,
                      RosterService rosterService,
                      SolverService solverService,
                      OrganisationRepository organisationRepository,
                      QualificationService qualificationService) {
        this.siteService            = siteService;
        this.rosterService          = rosterService;
        this.solverService          = solverService;
        this.organisationRepository = organisationRepository;
        this.qualificationService   = qualificationService;

        setPadding(true);
        add(new H2("Roster Management"));
        add(buildSiteSelector());
        add(buildPeriodSection());

        shiftPanel.setPadding(false);
        shiftPanel.setVisible(false);
        shiftPanel.add(new H3("Shifts"), buildShiftToolbar(), shiftGrid);
        add(shiftPanel);

        solverPanel.setPadding(false);
        solverPanel.setVisible(false);
        add(solverPanel);

        buildAssignmentsPanel();
        assignmentsPanel.setVisible(false);
        add(assignmentsPanel);
    }

    // =========================================================================
    // Site selector
    // =========================================================================

    private HorizontalLayout buildSiteSelector() {
        Long orgId = organisationRepository.findAll().stream()
                .findFirst().map(Organisation::getId).orElse(null);
        if (orgId != null) {
            siteSelector.setItems(siteService.getAllActiveByOrganisation(orgId));
        }
        siteSelector.setItemLabelGenerator(Site::getName);
        siteSelector.setPlaceholder("Select a site...");
        siteSelector.addValueChangeListener(e -> {
            selectedPeriod = null;
            shiftPanel.setVisible(false);
            solverPanel.setVisible(false);
            assignmentsPanel.setVisible(false);
            refreshPeriodGrid();
        });
        return new HorizontalLayout(siteSelector);
    }

    // =========================================================================
    // Period section
    // =========================================================================

    private VerticalLayout buildPeriodSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3("Roster Periods"));

        periodGrid.addColumn(p -> p.getStartDate() + " – " + p.getEndDate())
                .setHeader("Period").setAutoWidth(true);
        periodGrid.addColumn(p -> "Period " + p.getSequenceNumber()).setHeader("Seq").setAutoWidth(true);
        periodGrid.addColumn(RosterPeriod::getStatus).setHeader("Status").setAutoWidth(true);
        periodGrid.setHeight("200px");

        periodGrid.addSelectionListener(ev -> {
            Optional<RosterPeriod> sel = ev.getFirstSelectedItem();
            selectedPeriod = sel.orElse(null);
            if (selectedPeriod != null) {
                refreshShiftGrid();
                shiftPanel.setVisible(true);
                buildSolverPanel();
                solverPanel.setVisible(true);
                refreshAssignments();
                assignmentsPanel.setVisible(true);
            } else {
                shiftPanel.setVisible(false);
                solverPanel.setVisible(false);
                assignmentsPanel.setVisible(false);
            }
        });

        DatePicker startDatePicker = new DatePicker("Start Date");
        Button createBtn  = new Button("Create Period", e -> {
            Site site = siteSelector.getValue();
            if (site == null || startDatePicker.getValue() == null) return;
            try {
                rosterService.createRosterPeriod(site.getId(), startDatePicker.getValue(), null);
                startDatePicker.clear();
                refreshPeriodGrid();
                notify("Period created.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        Button publishBtn = new Button("Publish",         e -> periodAction("publish"));
        Button revertBtn  = new Button("Revert to Draft", e -> periodAction("revert"));
        Button cancelBtn  = new Button("Cancel Period",   e -> periodAction("cancel"));
        publishBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

        section.add(new HorizontalLayout(startDatePicker, createBtn, publishBtn, revertBtn, cancelBtn));
        section.add(periodGrid);
        return section;
    }

    private void periodAction(String action) {
        if (selectedPeriod == null) return;
        try {
            switch (action) {
                case "publish" -> rosterService.publish(selectedPeriod.getId());
                case "revert"  -> rosterService.revertToDraft(selectedPeriod.getId());
                case "cancel"  -> rosterService.cancel(selectedPeriod.getId());
            }
            refreshPeriodGrid();
            notify("Done.", NotificationVariant.LUMO_SUCCESS);
        } catch (Exception ex) {
            notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

    private void refreshPeriodGrid() {
        Site site = siteSelector.getValue();
        if (site == null) { periodGrid.setItems(List.of()); return; }
        periodGrid.setItems(rosterService.getBySite(site.getId()));
    }

    // =========================================================================
    // Shift section
    // =========================================================================

    private HorizontalLayout buildShiftToolbar() {
        Button addBtn    = new Button("Add Shift",    e -> openShiftDialog(null));
        Button editBtn   = new Button("Edit",         e -> openShiftDialog(selectedShift));
        Button removeBtn = new Button("Remove",       e -> { if (selectedShift != null) removeShift(selectedShift); });
        Button reqBtn    = new Button("Requirements", e -> openRequirementsDialog(selectedShift));

        editBtn.setEnabled(false);
        removeBtn.setEnabled(false);
        reqBtn.setEnabled(false);
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

        shiftGrid.addSelectionListener(ev -> {
            Optional<Shift> sel = ev.getFirstSelectedItem();
            selectedShift = sel.orElse(null);
            boolean hasSel = sel.isPresent();
            editBtn.setEnabled(hasSel);
            removeBtn.setEnabled(hasSel);
            reqBtn.setEnabled(hasSel);
        });

        shiftGrid.addColumn(Shift::getName).setHeader("Name").setAutoWidth(true);
        shiftGrid.addColumn(s -> s.getStartDatetime() != null
                        ? s.getStartDatetime().toLocalDateTime().toString() : "")
                .setHeader("Start").setAutoWidth(true);
        shiftGrid.addColumn(s -> s.getEndDatetime() != null
                        ? s.getEndDatetime().toLocalDateTime().toString() : "")
                .setHeader("End").setAutoWidth(true);
        shiftGrid.addColumn(Shift::getMinimumStaff).setHeader("Min Staff").setAutoWidth(true);
        shiftGrid.addColumn(s -> s.getShiftType() != null ? s.getShiftType().getName() : "—")
                .setHeader("Type").setAutoWidth(true);

        return new HorizontalLayout(addBtn, editBtn, removeBtn, reqBtn);
    }

    private void refreshShiftGrid() {
        if (selectedPeriod == null) return;
        shiftGrid.setItems(rosterService.getShifts(selectedPeriod.getId()));
    }

    private void removeShift(Shift shift) {
        try {
            rosterService.removeShift(shift.getId());
            selectedShift = null;
            refreshShiftGrid();
            notify("Shift removed.", NotificationVariant.LUMO_SUCCESS);
        } catch (Exception ex) {
            notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

    private void openShiftDialog(Shift existing) {
        if (selectedPeriod == null) return;
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existing == null ? "Add Shift" : "Edit Shift");
        dialog.setWidth("480px");

        TextField          name     = new TextField("Name");
        DateTimePicker     start    = new DateTimePicker("Start");
        DateTimePicker     end      = new DateTimePicker("End");
        IntegerField       minStaff = new IntegerField("Minimum Staff");
        TextArea           notes    = new TextArea("Notes");
        ComboBox<ShiftType> typeBox = new ComboBox<>("Shift Type");
        minStaff.setValue(1);
        minStaff.setMin(1);

        Site site = siteSelector.getValue();
        if (site != null) {
            typeBox.setItems(siteService.getShiftTypes(site.getId()));
            typeBox.setItemLabelGenerator(ShiftType::getName);
        }

        if (existing != null) {
            if (existing.getName() != null) name.setValue(existing.getName());
            if (existing.getStartDatetime() != null)
                start.setValue(existing.getStartDatetime().toLocalDateTime());
            if (existing.getEndDatetime() != null)
                end.setValue(existing.getEndDatetime().toLocalDateTime());
            minStaff.setValue(existing.getMinimumStaff());
            if (existing.getNotes() != null) notes.setValue(existing.getNotes());
            if (existing.getShiftType() != null) typeBox.setValue(existing.getShiftType());
        }

        dialog.add(new FormLayout(name, start, end, minStaff, typeBox, notes));

        Button save = new Button("Save", e -> {
            try {
                var startDt = start.getValue() != null ? start.getValue().atOffset(ZoneOffset.UTC) : null;
                var endDt   = end.getValue()   != null ? end.getValue().atOffset(ZoneOffset.UTC)   : null;
                String n    = name.getValue().isBlank()  ? null : name.getValue();
                String nt   = notes.getValue().isBlank() ? null : notes.getValue();
                Long typeId = typeBox.getValue() != null ? typeBox.getValue().getId() : null;
                if (existing == null) {
                    rosterService.addShift(selectedPeriod.getId(),
                            new ShiftCreateRequest(typeId, n, startDt, endDt, minStaff.getValue(), nt));
                } else {
                    rosterService.updateShift(existing.getId(),
                            new ShiftUpdateRequest(typeId, n, startDt, endDt, minStaff.getValue(), nt));
                }
                dialog.close();
                refreshShiftGrid();
                notify("Shift saved.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });
        Button cancel = new Button("Cancel", e -> dialog.close());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    // =========================================================================
    // Qualification requirements dialog
    // =========================================================================

    private void openRequirementsDialog(Shift shift) {
        if (shift == null) return;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Qualification Requirements — " +
                (shift.getName() != null ? shift.getName() : "Shift #" + shift.getId()));
        dialog.setWidth("520px");

        Grid<ShiftQualificationRequirement> reqGrid = new Grid<>(ShiftQualificationRequirement.class, false);
        reqGrid.addColumn(r -> r.getQualification().getName()).setHeader("Qualification").setAutoWidth(true);
        reqGrid.addColumn(ShiftQualificationRequirement::getMinimumCount).setHeader("Min Count").setAutoWidth(true);
        reqGrid.setHeight("200px");
        reqGrid.setAllRowsVisible(true);

        refreshReqGrid(reqGrid, shift);

        // Add row
        ComboBox<Qualification> qualBox = new ComboBox<>("Qualification");
        qualBox.setItems(qualificationService.getAll());
        qualBox.setItemLabelGenerator(Qualification::getName);

        IntegerField minCount = new IntegerField("Minimum Count");
        minCount.setValue(1);
        minCount.setMin(1);

        Button addBtn = new Button("Add", e -> {
            if (qualBox.getValue() == null) {
                notify("Select a qualification first.", NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                rosterService.addQualificationRequirement(
                        shift.getId(), qualBox.getValue().getId(), minCount.getValue());
                refreshReqGrid(reqGrid, shift);
                qualBox.clear();
                minCount.setValue(1);
                notify("Requirement added.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button removeBtn = new Button("Remove Selected");
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        removeBtn.setEnabled(false);
        reqGrid.addSelectionListener(ev -> removeBtn.setEnabled(ev.getFirstSelectedItem().isPresent()));
        removeBtn.addClickListener(e ->
                reqGrid.getSelectedItems().stream().findFirst().ifPresent(req -> {
                    try {
                        rosterService.removeQualificationRequirement(req.getId());
                        refreshReqGrid(reqGrid, shift);
                        notify("Requirement removed.", NotificationVariant.LUMO_SUCCESS);
                    } catch (Exception ex) {
                        notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
                    }
                }));

        dialog.add(reqGrid, new HorizontalLayout(qualBox, minCount, addBtn, removeBtn));
        dialog.getFooter().add(new Button("Close", e -> dialog.close()));
        dialog.open();
    }

    private void refreshReqGrid(Grid<ShiftQualificationRequirement> grid, Shift shift) {
        grid.setItems(rosterService.getQualificationRequirements(shift.getId()));
    }

    // =========================================================================
    // Solver panel
    // =========================================================================

    private void buildSolverPanel() {
        solverPanel.removeAll();
        solverPanel.add(new H3("Solver"));

        IntegerField timeLimitField = new IntegerField("Time Limit (seconds)");
        timeLimitField.setValue(60);
        timeLimitField.setMin(1);
        timeLimitField.setMax(86400);

        Button submitBtn = new Button("Submit Solve", e -> {
            try {
                SolverJob job = solverService.submitSolve(
                        selectedPeriod.getId(), timeLimitField.getValue());
                lastJobId = job.getId();
                solverStatus.setText("Job #" + job.getId() + " submitted — Status: " + job.getStatus());
                notify("Solve job submitted.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });
        submitBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn  = new Button("Cancel Solve",    e -> {
            try {
                solverService.cancelSolve(selectedPeriod.getId());
                notify("Cancel requested.", NotificationVariant.LUMO_SUCCESS);
                refreshSolverStatus();
            } catch (Exception ex) {
                notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });
        Button refreshBtn = new Button("Refresh Status",  e -> {
            refreshSolverStatus();
            refreshAssignments();
        });

        solverPanel.add(
                new HorizontalLayout(timeLimitField, submitBtn, cancelBtn, refreshBtn),
                solverStatus);
    }

    private void refreshSolverStatus() {
        if (lastJobId == null) { solverStatus.setText("No active job."); return; }
        try {
            SolverJob job = solverService.getSolverJob(lastJobId);
            String score = job.getFinalScore()    != null ? " | Score: " + job.getFinalScore()       : "";
            String err   = job.getErrorMessage()  != null ? " | Error: " + job.getErrorMessage()     : "";
            solverStatus.setText("Job #" + job.getId() + " — " + job.getStatus() + score + err);
            refreshPeriodGrid();
        } catch (Exception ex) {
            solverStatus.setText("Could not load job status: " + ex.getMessage());
        }
    }

    // =========================================================================
    // Assignments viewer
    // =========================================================================

    private void buildAssignmentsPanel() {
        assignmentsPanel.setPadding(false);
        assignmentsPanel.add(new H3("Solved Assignments"));

        assignmentsGrid.addColumn(a -> a.getShift().getName() != null
                        ? a.getShift().getName() : "Shift #" + a.getShift().getId())
                .setHeader("Shift").setAutoWidth(true).setSortable(true);
        assignmentsGrid.addColumn(a -> a.getShift().getStartDatetime() != null
                        ? a.getShift().getStartDatetime().toLocalDateTime().toString() : "")
                .setHeader("Start").setAutoWidth(true).setSortable(true);
        assignmentsGrid.addColumn(a -> a.getShift().getEndDatetime() != null
                        ? a.getShift().getEndDatetime().toLocalDateTime().toString() : "")
                .setHeader("End").setAutoWidth(true);
        assignmentsGrid.addColumn(a -> a.getStaff() != null
                        ? a.getStaff().getFirstName() + " " + a.getStaff().getLastName()
                        : "— unassigned —")
                .setHeader("Staff").setAutoWidth(true).setSortable(true);
        assignmentsGrid.addColumn(a -> a.isPinned() ? "Pinned" : "").setHeader("").setAutoWidth(true);
        assignmentsGrid.setHeight("350px");

        Button pinBtn   = new Button("Pin");
        Button unpinBtn = new Button("Unpin");
        pinBtn.setEnabled(false);
        unpinBtn.setEnabled(false);

        assignmentsGrid.addSelectionListener(sel -> {
            selectedAssignment = sel.getFirstSelectedItem().orElse(null);
            boolean hasSel = selectedAssignment != null;
            pinBtn.setEnabled(hasSel);
            unpinBtn.setEnabled(hasSel);
        });

        pinBtn.addClickListener(e -> {
            try {
                rosterService.pin(selectedAssignment.getId());
                refreshAssignments();
                notify("Assignment pinned.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        unpinBtn.addClickListener(e -> {
            try {
                rosterService.unpin(selectedAssignment.getId());
                refreshAssignments();
                notify("Assignment unpinned.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        assignmentsPanel.add(assignmentsGrid, new HorizontalLayout(pinBtn, unpinBtn));
    }

    private void refreshAssignments() {
        if (selectedPeriod == null) return;
        assignmentsGrid.setItems(rosterService.getAssignments(selectedPeriod.getId()));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void notify(String message, NotificationVariant variant) {
        Notification n = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(variant);
    }
}
