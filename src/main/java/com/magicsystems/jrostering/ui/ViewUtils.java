package com.magicsystems.jrostering.ui;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/** Shared UI utilities used across Vaadin view classes. */
public final class ViewUtils {

    private ViewUtils() {}

    public static void notify(String message, NotificationVariant variant) {
        Notification n = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(variant);
    }
}
