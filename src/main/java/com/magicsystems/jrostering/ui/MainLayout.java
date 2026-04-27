package com.magicsystems.jrostering.ui;

import com.magicsystems.jrostering.ui.account.AccountView;
import com.magicsystems.jrostering.ui.dashboard.DashboardView;
import com.magicsystems.jrostering.ui.qualification.QualificationView;
import com.magicsystems.jrostering.ui.report.ReportView;
import com.magicsystems.jrostering.ui.roster.RosterView;
import com.magicsystems.jrostering.ui.site.SiteView;
import com.magicsystems.jrostering.ui.staff.StaffView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

/**
 * Application shell — AppLayout with side navigation and a top navbar with logout.
 * All authenticated views use this as their layout.
 */
@SpringComponent
@UIScope
@SuppressWarnings("serial")
public class MainLayout extends AppLayout {

    public MainLayout() {
        DrawerToggle toggle = new DrawerToggle();

        H1 appTitle = new H1("JRostering");
        appTitle.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "0");

        Span spacer = new Span();
        spacer.getStyle().set("flex", "1");

        Button logout = new Button("Log out", VaadinIcon.SIGN_OUT.create(),
                e -> UI.getCurrent().getPage().setLocation("/logout"));
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);

        HorizontalLayout navbar = new HorizontalLayout(toggle, appTitle, spacer, logout);
        navbar.setWidthFull();
        navbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        navbar.setPadding(true);

        addToNavbar(navbar);
        addToDrawer(buildNav());
        setPrimarySection(Section.DRAWER);
    }

    private SideNav buildNav() {
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Dashboard",       DashboardView.class,     VaadinIcon.DASHBOARD.create()));
        nav.addItem(new SideNavItem("Staff",           StaffView.class,         VaadinIcon.USERS.create()));
        nav.addItem(new SideNavItem("Sites",           SiteView.class,          VaadinIcon.BUILDING.create()));
        nav.addItem(new SideNavItem("Qualifications",  QualificationView.class, VaadinIcon.DIPLOMA.create()));
        nav.addItem(new SideNavItem("Roster",          RosterView.class,        VaadinIcon.CALENDAR.create()));
        nav.addItem(new SideNavItem("Reports",         ReportView.class,        VaadinIcon.FILE_TABLE.create()));
        nav.addItem(new SideNavItem("Account",         AccountView.class,       VaadinIcon.COG.create()));
        return nav;
    }
}
