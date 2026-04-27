package com.magicsystems.jrostering.ui.roster;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.OrganisationRepository;
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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Roster management view — period lifecycle, shift management, and solver controls.
 */
@Route(value = "roster", layout = MainLayout.class)
@PageTitle("Roster — JRostering")
@PermitAll
@SuppressWarnings("serial")
public class RosterView extends VerticalLayout {

    private final SiteService            siteService;
    private final RosterService          rosterService;
    private final SolverService          solverService;
    private final OrganisationRepository organisationRepository;

    private final ComboBox<Site>         siteSelector  = new ComboBox<>("Site");
    private final Grid<RosterPeriod>     periodGrid    = new Grid<>(RosterPeriod.class, false);
    private final VerticalLayout         shiftPanel    = new VerticalLayout();
    private final Grid<Shift>            shiftGrid     = new Grid<>(Shift.class, false);
    private final VerticalLayout         solverPanel   = new VerticalLayout();
    private final Span                   solverStatus  = new Span();

    private RosterPeriod selectedPeriod;
    private Long         lastJobId;

    public RosterView(SiteService siteService,
                      RosterService rosterService,
                      SolverService solverService,
                      OrganisationRepository organisationRepository) {
        this.siteService            = siteService;
        this.rosterService          = rosterService;
        this.solverService          = solverService;
        this.organisationRepository = organisationRepository;

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

        // Period grid
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
            } else {
                shiftPanel.setVisible(false);
                solverPanel.setVisible(false);
            }
        });

        // Period toolbar
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

        Button publishBtn = new Button("Publish",        e -> periodAction("publish"));
        Button revertBtn  = new Button("Revert to Draft",e -> periodAction("revert"));
        Button cancelBtn  = new Button("Cancel Period",  e -> periodAction("cancel"));
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
        if (site == null) {
            periodGrid.setItems(List.of());
            return;
        }
        periodGrid.setItems(rosterService.getBySite(site.getId()));
    }

    // =========================================================================
    // Shift section
    // =========================================================================

    private HorizontalLayout buildShiftToolbar() {
        Button addBtn    = new Button("Add Shift",  e -> openShiftDialog(null));
        Button editBtn   = new Button("Edit",       e -> shiftGrid.getSelectedItems().stream()
                .findFirst().ifPresent(this::openShiftDialog));
        Button removeBtn = new Button("Remove",     e -> shiftGrid.getSelectedItems().stream()
                .findFirst().ifPresent(this::removeShift));

        editBtn.setEnabled(false);
        removeBtn.setEnabled(false);
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

        shiftGrid.addSelectionListener(ev -> {
            boolean sel = ev.getFirstSelectedItem().isPresent();
            editBtn.setEnabled(sel);
            removeBtn.setEnabled(sel);
        });

        shiftGrid.addColumn(Shift::getName).setHeader("Name").setAutoWidth(true);
        shiftGrid.addColumn(s -> s.getStartDatetime() != null
                        ? s.getStartDatetime().toLocalDateTime().toString() : "")
                .setHeader("Start").setAutoWidth(true);
        shiftGrid.addColumn(s -> s.getEndDatetime() != null
                        ? s.getEndDatetime().toLocalDateTime().toString() : "")
                .setHeader("End").setAutoWidth(true);
        shiftGrid.addColumn(Shift::getMinimumStaff).setHeader("Min Staff").setAutoWidth(true);

        return new HorizontalLayout(addBtn, editBtn, removeBtn);
    }

    private void refreshShiftGrid() {
        if (selectedPeriod == null) return;
        shiftGrid.setItems(rosterService.getShifts(selectedPeriod.getId()));
    }

    private void removeShift(Shift shift) {
        try {
            rosterService.removeShift(shift.getId());
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

        TextField        name     = new TextField("Name");
        DateTimePicker   start    = new DateTimePicker("Start");
        DateTimePicker   end      = new DateTimePicker("End");
        IntegerField     minStaff = new IntegerField("Minimum Staff");
        TextArea         notes    = new TextArea("Notes");
        minStaff.setValue(1);
        minStaff.setMin(1);

        if (existing != null) {
            if (existing.getName() != null) name.setValue(existing.getName());
            if (existing.getStartDatetime() != null)
                start.setValue(existing.getStartDatetime().toLocalDateTime());
            if (existing.getEndDatetime() != null)
                end.setValue(existing.getEndDatetime().toLocalDateTime());
            minStaff.setValue(existing.getMinimumStaff());
            if (existing.getNotes() != null) notes.setValue(existing.getNotes());
        }

        dialog.add(new FormLayout(name, start, end, minStaff, notes));

        Button save = new Button("Save", e -> {
            try {
                var startDt = start.getValue() != null
                        ? start.getValue().atOffset(ZoneOffset.UTC) : null;
                var endDt   = end.getValue() != null
                        ? end.getValue().atOffset(ZoneOffset.UTC) : null;
                String n    = name.getValue().isBlank() ? null : name.getValue();
                String nt   = notes.getValue().isBlank() ? null : notes.getValue();

                if (existing == null) {
                    rosterService.addShift(selectedPeriod.getId(),
                            new ShiftCreateRequest(null, n, startDt, endDt, minStaff.getValue(), nt));
                } else {
                    rosterService.updateShift(existing.getId(),
                            new ShiftUpdateRequest(null, n, startDt, endDt, minStaff.getValue(), nt));
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

        Button cancelBtn = new Button("Cancel Solve", e -> {
            try {
                solverService.cancelSolve(selectedPeriod.getId());
                notify("Cancel requested.", NotificationVariant.LUMO_SUCCESS);
                refreshSolverStatus();
            } catch (Exception ex) {
                notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        Button refreshBtn = new Button("Refresh Status", e -> refreshSolverStatus());

        solverPanel.add(
                new HorizontalLayout(timeLimitField, submitBtn, cancelBtn, refreshBtn),
                solverStatus);
    }

    private void refreshSolverStatus() {
        if (lastJobId == null) {
            solverStatus.setText("No active job.");
            return;
        }
        try {
            SolverJob job = solverService.getSolverJob(lastJobId);
            String score = job.getFinalScore() != null ? " | Score: " + job.getFinalScore() : "";
            String err   = job.getErrorMessage() != null ? " | Error: " + job.getErrorMessage() : "";
            solverStatus.setText("Job #" + job.getId() + " — " + job.getStatus() + score + err);
            refreshPeriodGrid();
        } catch (Exception ex) {
            solverStatus.setText("Could not load job status: " + ex.getMessage());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void notify(String message, NotificationVariant variant) {
        Notification n = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(variant);
    }
}
