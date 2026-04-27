package com.magicsystems.jrostering.ui.report;

import com.magicsystems.jrostering.domain.Organisation;
import com.magicsystems.jrostering.domain.RosterPeriod;
import com.magicsystems.jrostering.domain.Site;
import com.magicsystems.jrostering.domain.SolverJob;
import com.magicsystems.jrostering.domain.SolverJobStatus;
import com.magicsystems.jrostering.repository.OrganisationRepository;
import com.magicsystems.jrostering.repository.SolverJobRepository;
import com.magicsystems.jrostering.report.ExcelReportGenerator;
import com.magicsystems.jrostering.service.RosterService;
import com.magicsystems.jrostering.service.SiteService;
import com.magicsystems.jrostering.ui.MainLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * Report generation view — download staff hours and constraint violation Excel reports.
 */
@Route(value = "reports", layout = MainLayout.class)
@PageTitle("Reports — JRostering")
@PermitAll
@SuppressWarnings("serial")
public class ReportView extends VerticalLayout {

    private final SiteService            siteService;
    private final RosterService          rosterService;
    private final SolverJobRepository    solverJobRepository;
    private final ExcelReportGenerator   reportGenerator;
    private final OrganisationRepository organisationRepository;

    public ReportView(SiteService siteService,
                      RosterService rosterService,
                      SolverJobRepository solverJobRepository,
                      ExcelReportGenerator reportGenerator,
                      OrganisationRepository organisationRepository) {
        this.siteService            = siteService;
        this.rosterService          = rosterService;
        this.solverJobRepository    = solverJobRepository;
        this.reportGenerator        = reportGenerator;
        this.organisationRepository = organisationRepository;

        setPadding(true);
        add(new H2("Reports"));
        add(buildHoursReportSection());
        add(buildViolationReportSection());
    }

    // =========================================================================
    // Staff hours report
    // =========================================================================

    private VerticalLayout buildHoursReportSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3("Staff Hours Report"));

        ComboBox<Site> siteBox   = new ComboBox<>("Site");
        ComboBox<RosterPeriod> periodBox = new ComboBox<>("Roster Period");

        Long orgId = organisationRepository.findAll().stream()
                .findFirst().map(Organisation::getId).orElse(null);
        if (orgId != null) {
            siteBox.setItems(siteService.getAllActiveByOrganisation(orgId));
        }
        siteBox.setItemLabelGenerator(Site::getName);
        siteBox.addValueChangeListener(e -> {
            periodBox.clear();
            if (e.getValue() != null) {
                periodBox.setItems(rosterService.getBySite(e.getValue().getId()));
            }
        });

        periodBox.setItemLabelGenerator(p -> p.getStartDate() + " – " + p.getEndDate()
                + " (" + p.getStatus() + ")");

        // Hidden anchor used to trigger the download
        Anchor downloadAnchor = new Anchor();
        downloadAnchor.getElement().setAttribute("download", true);
        downloadAnchor.setVisible(false);
        add(downloadAnchor);

        Button downloadBtn = new Button("Download Hours Report (.xlsx)");
        downloadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        downloadBtn.addClickListener(e -> {
            RosterPeriod period = periodBox.getValue();
            if (period == null) {
                notify("Select a roster period first.", NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                byte[] bytes = reportGenerator.generateHoursReport(period.getId());
                StreamResource resource = new StreamResource(
                        "hours-period-" + period.getId() + ".xlsx",
                        () -> new ByteArrayInputStream(bytes));
                downloadAnchor.setHref(resource);
                downloadAnchor.getElement().callJsFunction("click");
                notify("Download started.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                notify("Error generating report: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        section.add(new HorizontalLayout(siteBox, periodBox, downloadBtn));
        return section;
    }

    // =========================================================================
    // Violation summary report
    // =========================================================================

    private VerticalLayout buildViolationReportSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3("Constraint Violation Report"));

        List<SolverJob> completedJobs = solverJobRepository.findByStatusIn(
                List.of(SolverJobStatus.COMPLETED, SolverJobStatus.INFEASIBLE,
                        SolverJobStatus.CANCELLED));

        ComboBox<SolverJob> jobBox = new ComboBox<>("Solver Job");
        jobBox.setItems(completedJobs);
        jobBox.setItemLabelGenerator(j ->
                "Job #" + j.getId() + " — " + j.getRosterPeriod().getSite().getName()
                + " " + j.getRosterPeriod().getStartDate()
                + " (" + j.getStatus() + ")");

        Anchor downloadAnchor = new Anchor();
        downloadAnchor.getElement().setAttribute("download", true);
        downloadAnchor.setVisible(false);
        add(downloadAnchor);

        Button downloadBtn = new Button("Download Violation Report (.xlsx)");
        downloadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        downloadBtn.addClickListener(e -> {
            SolverJob job = jobBox.getValue();
            if (job == null) {
                notify("Select a solver job first.", NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                byte[] bytes = reportGenerator.generateViolationSummaryReport(job.getId());
                StreamResource resource = new StreamResource(
                        "violations-job-" + job.getId() + ".xlsx",
                        () -> new ByteArrayInputStream(bytes));
                downloadAnchor.setHref(resource);
                downloadAnchor.getElement().callJsFunction("click");
                notify("Download started.", NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                notify("Error generating report: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        section.add(new HorizontalLayout(jobBox, downloadBtn));
        return section;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void notify(String message, NotificationVariant variant) {
        Notification n = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(variant);
    }
}
