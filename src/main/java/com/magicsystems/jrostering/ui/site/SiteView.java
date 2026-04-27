package com.magicsystems.jrostering.ui.site;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.OrganisationRepository;
import com.magicsystems.jrostering.service.SiteService;
import com.magicsystems.jrostering.service.SiteService.RuleConfigurationUpdateRequest;
import com.magicsystems.jrostering.service.SiteService.SiteCreateRequest;
import com.magicsystems.jrostering.service.SiteService.SiteUpdateRequest;
import com.magicsystems.jrostering.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.List;
import java.util.Optional;

/**
 * Site management view — CRUD for sites, shift types, and per-site rule configurations.
 */
@Route(value = "sites", layout = MainLayout.class)
@PageTitle("Sites — JRostering")
@PermitAll
public class SiteView extends VerticalLayout {

    private final SiteService            siteService;
    private final OrganisationRepository organisationRepository;

    private final Grid<Site>    siteGrid   = new Grid<>(Site.class, false);
    private final VerticalLayout detailPanel = new VerticalLayout();

    private Long orgId;
    private Site selectedSite;

    public SiteView(SiteService siteService,
                    OrganisationRepository organisationRepository) {
        this.siteService            = siteService;
        this.organisationRepository = organisationRepository;

        orgId = organisationRepository.findAll().stream()
                .findFirst().map(Organisation::getId).orElse(null);

        setPadding(true);
        add(new H2("Sites"));
        add(buildToolbar());
        add(buildSiteGrid());
        detailPanel.setVisible(false);
        detailPanel.setPadding(false);
        add(detailPanel);

        refreshSiteGrid();
    }

    // =========================================================================
    // Site grid
    // =========================================================================

    private HorizontalLayout buildToolbar() {
        Button addBtn  = new Button("Add Site",    e -> openSiteDialog(null));
        Button editBtn = new Button("Edit",         e -> openSiteDialog(selectedSite));
        Button deactivateBtn = new Button("Deactivate", e -> deactivateSelected());

        editBtn.setEnabled(false);
        deactivateBtn.setEnabled(false);
        deactivateBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

        siteGrid.addSelectionListener(ev -> {
            Optional<Site> sel = ev.getFirstSelectedItem();
            selectedSite = sel.orElse(null);
            editBtn.setEnabled(sel.isPresent());
            deactivateBtn.setEnabled(sel.isPresent());
            sel.ifPresentOrElse(this::showDetail, () -> {
                detailPanel.setVisible(false);
                detailPanel.removeAll();
            });
        });

        return new HorizontalLayout(addBtn, editBtn, deactivateBtn);
    }

    private Grid<Site> buildSiteGrid() {
        siteGrid.addColumn(Site::getName).setHeader("Name").setAutoWidth(true);
        siteGrid.addColumn(Site::getTimezone).setHeader("Timezone").setAutoWidth(true);
        siteGrid.addColumn(s -> s.getAddress() != null ? s.getAddress() : "—").setHeader("Address").setAutoWidth(true);
        siteGrid.setHeight("250px");
        return siteGrid;
    }

    private void refreshSiteGrid() {
        if (orgId == null) return;
        siteGrid.setItems(siteService.getAllActiveByOrganisation(orgId));
    }

    private void deactivateSelected() {
        if (selectedSite == null) return;
        try {
            siteService.deactivate(selectedSite.getId());
            notify("Site deactivated.", NotificationVariant.LUMO_SUCCESS);
            selectedSite = null;
            detailPanel.setVisible(false);
            detailPanel.removeAll();
            refreshSiteGrid();
        } catch (Exception ex) {
            notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

    // =========================================================================
    // Add / Edit site dialog
    // =========================================================================

    private void openSiteDialog(Site existing) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existing == null ? "Add Site" : "Edit Site");
        dialog.setWidth("420px");

        TextField name     = new TextField("Name");
        TextField timezone = new TextField("Timezone (IANA, e.g. Australia/Adelaide)");
        TextArea  address  = new TextArea("Address");

        if (existing != null) {
            name.setValue(existing.getName());
            timezone.setValue(existing.getTimezone());
            if (existing.getAddress() != null) address.setValue(existing.getAddress());
        }

        dialog.add(new FormLayout(name, timezone, address));

        Button save   = new Button("Save", e -> {
            try {
                String addr = address.getValue().isBlank() ? null : address.getValue();
                if (existing == null) {
                    siteService.create(orgId, new SiteCreateRequest(name.getValue(), timezone.getValue(), addr));
                } else {
                    siteService.update(existing.getId(), new SiteUpdateRequest(name.getValue(), timezone.getValue(), addr));
                }
                dialog.close();
                refreshSiteGrid();
                notify("Saved.", NotificationVariant.LUMO_SUCCESS);
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
    // Detail panel — Shift Types & Rules
    // =========================================================================

    private void showDetail(Site site) {
        detailPanel.removeAll();
        detailPanel.add(new H3(site.getName() + " — Configuration"));

        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add("Shift Types", buildShiftTypesTab(site));
        tabs.add("Rule Configuration", buildRulesTab(site));

        detailPanel.add(tabs);
        detailPanel.setVisible(true);
    }

    // -- Shift Types tab --

    private VerticalLayout buildShiftTypesTab(Site site) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        Grid<ShiftType> grid = new Grid<>(ShiftType.class, false);
        grid.addColumn(ShiftType::getName).setHeader("Name").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        grid.setItems(siteService.getShiftTypes(site.getId()));

        TextField nameField = new TextField("New Shift Type Name");
        Button addBtn = new Button("Add", e -> {
            if (nameField.getValue().isBlank()) return;
            try {
                siteService.addShiftType(site.getId(), nameField.getValue());
                grid.setItems(siteService.getShiftTypes(site.getId()));
                nameField.clear();
                notify("Shift type added.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        Button removeBtn = new Button("Remove Selected");
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        removeBtn.setEnabled(false);
        grid.addSelectionListener(ev -> removeBtn.setEnabled(ev.getFirstSelectedItem().isPresent()));
        removeBtn.addClickListener(e -> grid.getSelectedItems().stream().findFirst().ifPresent(st -> {
            try {
                siteService.removeShiftType(st.getId());
                grid.setItems(siteService.getShiftTypes(site.getId()));
                notify("Shift type removed.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        }));

        layout.add(grid, new HorizontalLayout(nameField, addBtn, removeBtn));
        return layout;
    }

    // -- Rules tab --

    private VerticalLayout buildRulesTab(Site site) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        Grid<RuleConfiguration> grid = new Grid<>(RuleConfiguration.class, false);
        grid.addColumn(rc -> rc.getRuleType().name()).setHeader("Rule").setAutoWidth(true);
        grid.addColumn(rc -> rc.isEnabled() ? "Enabled" : "Disabled").setHeader("Status").setAutoWidth(true);
        grid.addColumn(rc -> rc.getConstraintLevel().name()).setHeader("Level").setAutoWidth(true);
        grid.addColumn(rc -> rc.getWeight() != null ? rc.getWeight().toString() : "—").setHeader("Weight").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        grid.setItems(siteService.getRuleConfigurations(site.getId()));

        Button editBtn = new Button("Edit Selected Rule");
        editBtn.setEnabled(false);
        grid.addSelectionListener(ev -> editBtn.setEnabled(ev.getFirstSelectedItem().isPresent()));
        editBtn.addClickListener(e -> grid.getSelectedItems().stream().findFirst().ifPresent(rc ->
                openRuleDialog(rc, site, grid)));

        layout.add(grid, editBtn);
        return layout;
    }

    private void openRuleDialog(RuleConfiguration rc, Site site, Grid<RuleConfiguration> grid) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Rule: " + rc.getRuleType().name());
        dialog.setWidth("420px");

        Checkbox enabled = new Checkbox("Enabled", rc.isEnabled());
        Select<ConstraintLevel> level = new Select<>();
        level.setLabel("Constraint Level");
        level.setItems(ConstraintLevel.values());
        level.setValue(rc.getConstraintLevel());

        IntegerField weight = new IntegerField("Weight (soft rules)");
        if (rc.getWeight() != null) weight.setValue(rc.getWeight());

        TextArea params = new TextArea("Parameters (JSON)");
        params.setValue(rc.getParameterJson() != null ? rc.getParameterJson() : "{}");
        params.setHeight("100px");

        dialog.add(new FormLayout(enabled, level, weight, params));

        Button save   = new Button("Save", e -> {
            try {
                siteService.updateRuleConfiguration(rc.getId(),
                        new RuleConfigurationUpdateRequest(
                                enabled.getValue(), level.getValue(),
                                weight.getValue(), params.getValue()));
                grid.setItems(siteService.getRuleConfigurations(site.getId()));
                dialog.close();
                notify("Rule updated.", NotificationVariant.LUMO_SUCCESS);
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
    // Helpers
    // =========================================================================

    private static void notify(String message, NotificationVariant variant) {
        Notification n = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(variant);
    }
}
