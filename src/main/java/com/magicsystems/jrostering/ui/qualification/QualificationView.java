package com.magicsystems.jrostering.ui.qualification;

import com.magicsystems.jrostering.domain.Qualification;
import com.magicsystems.jrostering.service.QualificationService;
import com.magicsystems.jrostering.ui.CrudGridPanel;
import com.magicsystems.jrostering.ui.MainLayout;
import com.magicsystems.jrostering.ui.ViewUtils;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.List;

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
    private final QualificationPanel   panel;

    public QualificationView(QualificationService qualificationService) {
        this.qualificationService = qualificationService;
        this.panel = new QualificationPanel();

        setPadding(true);
        add(new H2("Qualifications"), panel);
    }

    // =========================================================================
    // Inner panel using CrudGridPanel
    // =========================================================================

    private class QualificationPanel extends CrudGridPanel<Qualification> {

        QualificationPanel() {
            super(Qualification.class);
            grid.setHeight("400px");
        }

        @Override
        protected void configureColumns(Grid<Qualification> grid) {
            grid.addColumn(Qualification::getName).setHeader("Name").setAutoWidth(true).setSortable(true);
            grid.addColumn(q -> q.getDescription() != null ? q.getDescription() : "—")
                    .setHeader("Description").setFlexGrow(1);
        }

        @Override
        protected List<Qualification> loadItems() {
            return qualificationService.getAll();
        }

        @Override
        protected void populateForm(Dialog dialog, Qualification existing) {
            dialog.setHeaderTitle(existing == null ? "Add Qualification" : "Edit Qualification");

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
            Button cancel = new Button("Cancel", ev -> dialog.close());
            save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            dialog.getFooter().add(cancel, save);
            nameField.focus();
        }

        @Override
        protected void onDelete(Qualification item) {
            qualificationService.delete(item.getId());
        }
    }
}
