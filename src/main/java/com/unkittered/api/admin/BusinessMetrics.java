package com.unkittered.api.admin;

import com.unkittered.api.interaction.MatchRepository;
import com.unkittered.api.meetup.MeetupReportRepository;
import com.unkittered.api.meetup.MeetupRepository;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.safety.ReportRepository;
import com.unkittered.api.user.UserRepository;
import com.unkittered.api.verification.VerificationRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Registers business gauges that Prometheus scrapes from /actuator/prometheus —
 * surfacing them (as `unkittered_*`) for Grafana or the admin dashboard.
 * Counts are evaluated lazily on each scrape.
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final VerificationRepository verifications;
    private final MatchRepository matches;
    private final MeetupRepository meetups;
    private final MeetupReportRepository meetupReports;
    private final ReportRepository userReports;

    public BusinessMetrics(MeterRegistry registry, UserRepository users,
                           ProfileRepository profiles, VerificationRepository verifications,
                           MatchRepository matches, MeetupRepository meetups,
                           MeetupReportRepository meetupReports, ReportRepository userReports) {
        this.registry = registry;
        this.users = users;
        this.profiles = profiles;
        this.verifications = verifications;
        this.matches = matches;
        this.meetups = meetups;
        this.meetupReports = meetupReports;
        this.userReports = userReports;
    }

    @PostConstruct
    void register() {
        gauge("unkittered.users.total", users::count);
        gauge("unkittered.users.premium", () -> users.countBySubscriptionTierNot("free"));
        gauge("unkittered.profiles.verified", profiles::countByVerifiedTrue);
        gauge("unkittered.verifications.pending",
                () -> verifications.countByStatus("pending"));
        gauge("unkittered.matches.total", matches::count);
        gauge("unkittered.meetups.total", meetups::count);
        gauge("unkittered.reports.users", userReports::count);
        gauge("unkittered.reports.meetups", meetupReports::count);
    }

    private void gauge(String name, java.util.function.Supplier<Number> value) {
        Gauge.builder(name, () -> value.get().doubleValue()).register(registry);
    }
}
