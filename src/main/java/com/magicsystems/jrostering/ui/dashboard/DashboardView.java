package com.magicsystems.jrostering.ui.dashboard;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.OrganisationRepository;
import com.magicsystems.jrostering.repository.SolverJobRepository;
import com.magicsystems.jrostering.service.RosterService;
import com.magicsystems.jrostering.service.SiteService;
import com.magicsystems.jrostering.ui.MainLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.List;

/**
 * Landing page showing active roster periods and recent solver jobs across all sites.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Dashboard — JRostering")
@PermitAll
@SuppressWarnings("serial")
public class DashboardView extends VerticalLayout {

    private final OrganisationRepository organisationRepository;
    private final SiteService            siteService;
    private final RosterService          rosterService;
    private final SolverJobRepository    solverJobRepository;

    public DashboardView(OrganisationRepository organisationRepository,
                         SiteService siteService,
                         RosterService rosterService,
                         SolverJobRepository solverJobRepository) {
        this.organisationRepository = organisationRepository;
        this.siteService            = siteService;
        this.rosterService          = rosterService;
        this.solverJobRepository    = solverJobRepository;

        setPadding(true);
        setSpacing(true);

        add(new H2("Dashboard"));
        add(buildPeriodsSection());
        add(buildSolverSection());
    }

    private VerticalLayout buildPeriodsSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3("Active Roster Periods"));

        Grid<RosterPeriod> grid = new Grid<>(RosterPeriod.class, false);
        grid.addColumn(p -> p.getSite().getName()).setHeader("Site").setAutoWidth(true);
        grid.addColumn(p -> p.getStartDate() + " – " + p.getEndDate())
                .setHeader("Period").setAutoWidth(true);
        grid.addColumn(RosterPeriod::getStatus).setHeader("Status").setAutoWidth(true);
        grid.addColumn(p -> p.getSequenceNumber() == 1 ? "Period 1" : "Period 2")
                .setHeader("Seq").setAutoWidth(true);
        grid.setAllRowsVisible(true);

        List<RosterPeriod> periods = loadActivePeriods();
        grid.setItems(periods);

        if (periods.isEmpty()) {
            section.add(new Span("No active roster periods. Go to Roster to create one."));
        } else {
            section.add(grid);
        }
        return section;
    }

    private VerticalLayout buildSolverSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3("Recent Solver Jobs"));

        List<SolverJob> jobs = solverJobRepository
                .findByStatusIn(List.of(SolverJobStatus.QUEUED, SolverJobStatus.RUNNING));

        if (jobs.isEmpty()) {
            // Show last 5 completed/failed jobs instead
            jobs = solverJobRepository.findAll().stream()
                    .sorted((a, b) -> {
                        if (a.getCreatedAt() == null) return 1;
                        if (b.getCreatedAt() == null) return -1;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    })
                    .limit(5)
                    .toList();
        }

        if (jobs.isEmpty()) {
            section.add(new Span("No solver jobs yet."));
            return section;
        }

        Grid<SolverJob> grid = new Grid<>(SolverJob.class, false);
        grid.addColumn(j -> j.getRosterPeriod().getSite().getName())
                .setHeader("Site").setAutoWidth(true);
        grid.addColumn(j -> j.getRosterPeriod().getStartDate() + " – " + j.getRosterPeriod().getEndDate())
                .setHeader("Period").setAutoWidth(true);
        grid.addColumn(SolverJob::getStatus).setHeader("Status").setAutoWidth(true);
        grid.addColumn(j -> j.getFinalScore() != null ? j.getFinalScore() : "—")
                .setHeader("Score").setAutoWidth(true);
        grid.addColumn(j -> j.getCompletedAt() != null ? j.getCompletedAt().toString() : "—")
                .setHeader("Completed").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        grid.setItems(jobs);

        section.add(grid);
        return section;
    }

    private List<RosterPeriod> loadActivePeriods() {
        List<Organisation> orgs = organisationRepository.findAll();
        if (orgs.isEmpty()) return List.of();
        Organisation org = orgs.getFirst();

        return siteService.getAllActiveByOrganisation(org.getId()).stream()
                .flatMap(site -> rosterService.getBySite(site.getId()).stream())
                .filter(p -> p.getStatus() != RosterPeriodStatus.CANCELLED)
                .sorted((a, b) -> b.getStartDate().compareTo(a.getStartDate()))
                .toList();
    }
}
