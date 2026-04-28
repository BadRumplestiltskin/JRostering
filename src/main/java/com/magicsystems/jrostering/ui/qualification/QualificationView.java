package com.magicsystems.jrostering.ui.qualification;

import com.magicsystems.jrostering.domain.Qualification;
import com.magicsystems.jrostering.service.QualificationService;
import com.magicsystems.jrostering.ui.MainLayout;
import com.magicsystems.jrostering.ui.ViewUtils;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.Optional;

/**
 * View for managing global qualifications (e.g. First Aid, RSA, Fire Warden).
 * Qualifications are scoped to the organisation and assigned to staff
 * and required on shifts via their respective sub-views.
 */
@Route(value = "qualifications", layout = MainLayout.class)
@PageTitle("Qualifications — JRostering")
@PermitAll
@SuppressWarnings("serial")
public class QualificationView extends VerticalLayout {

    private final QualificationService qualificationService;

    private final Grid<Qualification> grid     = new Grid<>(Qualification.class, false);
    private Qualification             selected = null;

    public QualificationView(QualificationService qualificationService) {
        this.qualificationService = qualificationService;

        setPadding(true);
        add(new H2("Qualifications"));
        add(buildToolbar());
        add(buildGrid());
        refresh();
    }

    // =========================================================================
    // Toolbar
    // =========================================================================

    private HorizontalLayout buildToolbar() {
        Button addBtn    = new Button("Add",    e -> openDialog(null));
        Button editBtn   = new Button("Edit",   e -> openDialog(selected));
        Button deleteBtn = new Button("Delete", e -> deleteSelected());

        editBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

        grid.addSelectionListener(ev -> {
            Optional<Qualification> sel = ev.getFirstSelectedItem();
            selected = sel.orElse(null);
            editBtn.setEnabled(sel.isPresent());
            deleteBtn.setEnabled(sel.isPresent());
        });

        return new HorizontalLayout(addBtn, editBtn, deleteBtn);
    }

    // =========================================================================
    // Grid
    // =========================================================================

    private Grid<Qualification> buildGrid() {
        grid.addColumn(Qualification::getName).setHeader("Name").setAutoWidth(true).setSortable(true);
        grid.addColumn(q -> q.getDescription() != null ? q.getDescription() : "—")
                .setHeader("Description").setFlexGrow(1);
        grid.setHeight("400px");
        return grid;
    }

    private void refresh() {
        grid.setItems(qualificationService.getAll());
    }

    // =========================================================================
    // Add / Edit dialog
    // =========================================================================

    private void openDialog(Qualification existing) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existing == null ? "Add Qualification" : "Edit Qualification");
        dialog.setWidth("400px");

        TextField nameField = new TextField("Name");
        nameField.setRequired(true);
        TextArea descField  = new TextArea("Description");
        descField.setHeight("80px");

        if (existing != null) {
            nameField.setValue(existing.getName());
            if (existing.getDescription() != null) descField.setValue(existing.getDescription());
        }

        dialog.add(new FormLayout(nameField, descField));

        Button save = new Button("Save", e -> {
            if (nameField.getValue().isBlank()) {
                ViewUtils.notify("Name is required.", NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                if (existing == null) {
                    qualificationService.create(nameField.getValue(), descField.getValue());
                } else {
                    qualificationService.update(existing.getId(), nameField.getValue(), descField.getValue());
                }
                dialog.close();
                refresh();
                ViewUtils.notify("Saved.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                ViewUtils.notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });
        Button cancel = new Button("Cancel", e -> dialog.close());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
        nameField.focus();
    }

    private void deleteSelected() {
        if (selected == null) return;
        try {
            qualificationService.delete(selected.getId());
            selected = null;
            refresh();
            ViewUtils.notify("Qualification deleted.", NotificationVariant.LUMO_SUCCESS);
        } catch (Exception ex) {
            ViewUtils.notify("Cannot delete: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

}
