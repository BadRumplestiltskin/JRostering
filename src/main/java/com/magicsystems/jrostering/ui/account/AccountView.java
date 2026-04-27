package com.magicsystems.jrostering.ui.account;

import com.magicsystems.jrostering.domain.AppUser;
import com.magicsystems.jrostering.service.AppUserService;
import com.magicsystems.jrostering.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Account management view: change your own password and manage all application users.
 */
@Route(value = "account", layout = MainLayout.class)
@PageTitle("Account — JRostering")
@PermitAll
@SuppressWarnings("serial")
public class AccountView extends VerticalLayout {

    private final AppUserService appUserService;

    private final Grid<AppUser> userGrid = new Grid<>(AppUser.class, false);
    private AppUser             selected = null;

    public AccountView(AppUserService appUserService) {
        this.appUserService = appUserService;

        setPadding(true);
        add(new H2("Account"));
        add(buildChangePasswordSection());
        add(new Hr());
        add(buildUserManagementSection());
    }

    // =========================================================================
    // Change password
    // =========================================================================

    private VerticalLayout buildChangePasswordSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3("Change Password"));

        PasswordField current  = new PasswordField("Current Password");
        PasswordField next     = new PasswordField("New Password");
        PasswordField confirm  = new PasswordField("Confirm New Password");

        Button saveBtn = new Button("Change Password", e -> {
            if (next.getValue().isBlank()) {
                notify("New password cannot be empty.", NotificationVariant.LUMO_ERROR);
                return;
            }
            if (!next.getValue().equals(confirm.getValue())) {
                notify("New passwords do not match.", NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                String username = SecurityContextHolder.getContext()
                        .getAuthentication().getName();
                appUserService.changePassword(username, current.getValue(), next.getValue());
                current.clear(); next.clear(); confirm.clear();
                notify("Password changed successfully.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        section.add(new FormLayout(current, next, confirm), saveBtn);
        return section;
    }

    // =========================================================================
    // User management
    // =========================================================================

    private VerticalLayout buildUserManagementSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3("Users"));

        userGrid.addColumn(AppUser::getUsername).setHeader("Username").setAutoWidth(true);
        userGrid.addColumn(u -> u.isActive() ? "Active" : "Inactive").setHeader("Status").setAutoWidth(true);
        userGrid.setHeight("250px");

        Button addBtn        = new Button("Add User",    e -> openAddUserDialog());
        Button deactivateBtn = new Button("Deactivate",  e -> toggleSelected(false));
        Button activateBtn   = new Button("Activate",    e -> toggleSelected(true));

        deactivateBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deactivateBtn.setEnabled(false);
        activateBtn.setEnabled(false);

        userGrid.addSelectionListener(ev -> {
            Optional<AppUser> sel = ev.getFirstSelectedItem();
            selected = sel.orElse(null);
            deactivateBtn.setEnabled(sel.map(AppUser::isActive).orElse(false));
            activateBtn.setEnabled(sel.map(u -> !u.isActive()).orElse(false));
        });

        section.add(new HorizontalLayout(addBtn, deactivateBtn, activateBtn), userGrid);
        refreshUserGrid();
        return section;
    }

    private void refreshUserGrid() {
        userGrid.setItems(appUserService.getAll());
    }

    private void toggleSelected(boolean activate) {
        if (selected == null) return;
        try {
            if (activate) {
                appUserService.activate(selected.getId());
                notify("User activated.", NotificationVariant.LUMO_SUCCESS);
            } else {
                appUserService.deactivate(selected.getId());
                notify("User deactivated.", NotificationVariant.LUMO_SUCCESS);
            }
            selected = null;
            refreshUserGrid();
        } catch (Exception ex) {
            notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

    private void openAddUserDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add User");
        dialog.setWidth("360px");

        TextField     username = new TextField("Username");
        PasswordField password = new PasswordField("Password");
        PasswordField confirm  = new PasswordField("Confirm Password");
        username.setRequired(true);
        password.setRequired(true);

        dialog.add(new FormLayout(username, password, confirm));

        Button save = new Button("Create", e -> {
            if (username.getValue().isBlank() || password.getValue().isBlank()) {
                notify("Username and password are required.", NotificationVariant.LUMO_ERROR);
                return;
            }
            if (!password.getValue().equals(confirm.getValue())) {
                notify("Passwords do not match.", NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                appUserService.create(username.getValue(), password.getValue());
                dialog.close();
                refreshUserGrid();
                notify("User created.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                notify("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });
        Button cancel = new Button("Cancel", e -> dialog.close());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
        username.focus();
    }

    private static void notify(String message, NotificationVariant variant) {
        Notification n = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(variant);
    }
}
