package com.magicsystems.jrostering.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
import java.util.Optional;

/**
 * Abstract Vaadin component that captures the recurring add / edit / delete grid
 * pattern used across the management views.
 *
 * <h3>Usage</h3>
 * <pre>
 * class QualificationPanel extends CrudGridPanel&lt;Qualification&gt; {
 *     protected void configureColumns(Grid&lt;Qualification&gt; grid) { ... }
 *     protected List&lt;Qualification&gt; loadItems() { ... }
 *     protected void populateForm(Dialog dialog, Qualification item) { ... }
 *     protected void onDelete(Qualification item) { ... }
 * }
 * </pre>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #configureColumns} — add column definitions once at construction.</li>
 *   <li>{@link #loadItems} — called on initial load and after every mutation.</li>
 *   <li>{@link #populateForm} — build dialog content; close the dialog and call
 *       {@link #refresh()} after a successful save.</li>
 *   <li>{@link #onDelete} — remove the item; throw on failure to prevent the
 *       grid refresh.</li>
 * </ol>
 *
 * @param <T> the domain entity type displayed in the grid
 */
public abstract class CrudGridPanel<T> extends VerticalLayout {

    protected final Grid<T> grid;
    private T selected;

    protected CrudGridPanel(Class<T> beanType) {
        grid = new Grid<>(beanType, false);
        setPadding(false);
        configureColumns(grid);
        add(buildToolbar(), grid);
        refresh();
    }

    // =========================================================================
    // Template methods (implement in subclass)
    // =========================================================================

    /** Add column definitions to the grid. Called once at construction. */
    protected abstract void configureColumns(Grid<T> grid);

    /** Return the current list of items to show. Called after every mutation. */
    protected abstract List<T> loadItems();

    /**
     * Build and open a dialog for creating ({@code item == null}) or editing
     * ({@code item != null}) an entity. The implementation must call
     * {@link #refresh()} after a successful save.
     */
    protected abstract void populateForm(Dialog dialog, T item);

    /**
     * Delete the given entity. Throw any exception on failure — the grid will
     * not refresh and the error message will be shown as a notification.
     */
    protected abstract void onDelete(T item);

    // =========================================================================
    // Concrete machinery
    // =========================================================================

    /** Reload the grid from {@link #loadItems()}. */
    protected void refresh() {
        grid.setItems(loadItems());
    }

    private HorizontalLayout buildToolbar() {
        Button addBtn    = new Button("Add",    e -> openDialog(null));
        Button editBtn   = new Button("Edit",   e -> openDialog(selected));
        Button deleteBtn = new Button("Delete", e -> deleteSelected());

        editBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

        grid.addSelectionListener(ev -> {
            Optional<T> sel = ev.getFirstSelectedItem();
            selected = sel.orElse(null);
            editBtn.setEnabled(sel.isPresent());
            deleteBtn.setEnabled(sel.isPresent());
        });

        return new HorizontalLayout(addBtn, editBtn, deleteBtn);
    }

    private void openDialog(T item) {
        Dialog dialog = new Dialog();
        dialog.setWidth("420px");
        populateForm(dialog, item);
        dialog.open();
    }

    private void deleteSelected() {
        if (selected == null) return;
        try {
            onDelete(selected);
            selected = null;
            refresh();
            ViewUtils.notify("Deleted.", NotificationVariant.LUMO_SUCCESS);
        } catch (Exception ex) {
            ViewUtils.notify("Cannot delete: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }
}
