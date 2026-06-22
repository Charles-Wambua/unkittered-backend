package com.unkittered.api.admin;

import com.unkittered.api.admin.AdminDtos.InsightsDto;
import com.unkittered.api.admin.AdminDtos.PendingVerificationDto;
import com.unkittered.api.admin.AdminDtos.ReportDto;
import com.unkittered.api.admin.AdminDtos.StatsDto;
import com.unkittered.api.admin.AdminDtos.TopSubscriberDto;
import com.unkittered.api.admin.AdminDtos.WeekPoint;
import com.unkittered.api.common.ApiException;
import com.unkittered.api.interaction.MatchRepository;
import com.unkittered.api.meetup.MeetupReportRepository;
import com.unkittered.api.meetup.MeetupRepository;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.realtime.RealtimeGateway;
import com.unkittered.api.safety.ReportRepository;
import com.unkittered.api.security.CurrentUser;
import com.unkittered.api.user.User;
import com.unkittered.api.user.UserRepository;
import com.unkittered.api.verification.Verification;
import com.unkittered.api.verification.VerificationRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Admin-only moderation, review and metrics. Every entry point guards on the
 *  caller's admin flag. */
@Service
public class AdminService {

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final VerificationRepository verifications;
    private final MatchRepository matches;
    private final MeetupRepository meetups;
    private final MeetupReportRepository meetupReports;
    private final ReportRepository userReports;
    private final RealtimeGateway gateway;
    private final MeterRegistry registry;
    private final Map<String, HealthIndicator> healthIndicators;

    public AdminService(UserRepository users, ProfileRepository profiles,
                        VerificationRepository verifications, MatchRepository matches,
                        MeetupRepository meetups, MeetupReportRepository meetupReports,
                        ReportRepository userReports, RealtimeGateway gateway,
                        MeterRegistry registry,
                        Map<String, HealthIndicator> healthIndicators) {
        this.users = users;
        this.profiles = profiles;
        this.verifications = verifications;
        this.matches = matches;
        this.meetups = meetups;
        this.meetupReports = meetupReports;
        this.userReports = userReports;
        this.gateway = gateway;
        this.registry = registry;
        this.healthIndicators = healthIndicators;
    }

    /** 403s unless the caller is an admin. Call at the top of every action. */
    public void requireAdmin() {
        User u = users.findById(CurrentUser.id()).orElse(null);
        if (u == null || !u.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admins only");
        }
    }

    @Transactional(readOnly = true)
    public StatsDto stats() {
        requireAdmin();
        return new StatsDto(
                users.count(),
                users.countBySubscriptionTierNot("free"),
                profiles.countByVerifiedTrue(),
                verifications.countByStatus("pending"),
                matches.count(),
                meetups.count(),
                userReports.count(),
                meetupReports.count());
    }

    @Transactional(readOnly = true)
    public List<PendingVerificationDto> pendingVerifications() {
        requireAdmin();
        final List<PendingVerificationDto> out = new ArrayList<>();
        for (Verification v : verifications.findByStatusOrderByCreatedAtAsc("pending")) {
            User u = users.findById(v.getUserId()).orElse(null);
            Profile p = profiles.findById(v.getUserId()).orElse(null);
            final List<String> photos = new ArrayList<>();
            if (p != null) {
                if (p.getImageUrl() != null && !p.getImageUrl().isEmpty()) {
                    photos.add(p.getImageUrl());
                }
                photos.addAll(p.getGalleryImages());
            }
            out.add(new PendingVerificationDto(
                    v.getUserId().toString(),
                    p != null ? p.getName() : (u != null ? u.getDisplayName() : "Unknown"),
                    u != null ? u.getEmail() : "",
                    v.getSelfieUrl(),
                    photos,
                    v.getCreatedAt()));
        }
        return out;
    }

    @Transactional
    public void review(UUID userId, boolean approve) {
        requireAdmin();
        Verification v = verifications.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("No verification request"));
        v.setStatus(approve ? "approved" : "rejected");
        v.setReviewedAt(Instant.now());
        verifications.save(v);
        if (approve) {
            profiles.findById(userId).ifPresent(p -> {
                p.setVerified(true);
                profiles.save(p);
            });
        }
    }

    @Transactional(readOnly = true)
    public List<ReportDto> reports() {
        requireAdmin();
        final List<ReportDto> out = new ArrayList<>();
        userReports.findAll().forEach(r -> out.add(new ReportDto(
                r.getId().toString(),
                "user",
                r.getReporterId().toString(),
                r.getReportedId().toString(),
                r.getReason(),
                r.getCreatedAt())));
        meetupReports.findAll().forEach(r -> out.add(new ReportDto(
                r.getId().toString(),
                "meetup",
                r.getReporterId().toString(),
                r.getMeetupId().toString(),
                r.getReason(),
                r.getCreatedAt())));
        out.sort((a, b) -> b.at().compareTo(a.at()));
        return out;
    }

    // ── Moderation actions ─────────────────────────────────────────────────────
    @Transactional
    public void suspendUser(UUID userId, boolean suspend) {
        requireAdmin();
        User u = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        u.setSuspended(suspend);
        users.save(u);
    }

    @Transactional
    public void removeMeetup(UUID meetupId) {
        requireAdmin();
        meetups.deleteById(meetupId); // RSVPs/reports cascade
    }

    @Transactional
    public void dismissReport(String type, UUID id) {
        requireAdmin();
        if ("meetup".equals(type)) {
            meetupReports.deleteById(id);
        } else {
            userReports.deleteById(id);
        }
    }

    // ── Revenue & growth (estimate — no payments ledger yet) ───────────────────
    static final int PLUS_KES = 650;   // monthly, mirrors the app's plan catalog
    static final int GOLD_KES = 1200;

    @Transactional(readOnly = true)
    public InsightsDto insights() {
        requireAdmin();
        final long plus = users.countBySubscriptionTier("plus");
        final long gold = users.countBySubscriptionTier("gold");
        final long free = users.countBySubscriptionTier("free");
        final Instant now = Instant.now();

        final List<WeekPoint> weeks = new ArrayList<>();
        for (int i = 7; i >= 0; i--) {
            Instant start = now.minus(Duration.ofDays((i + 1) * 7L));
            Instant end = now.minus(Duration.ofDays(i * 7L));
            weeks.add(new WeekPoint(
                    LocalDate.ofInstant(start, ZoneOffset.UTC).toString(),
                    users.countByCreatedAtBetween(start, end)));
        }

        final List<TopSubscriberDto> top = new ArrayList<>();
        for (User u : users.findTop20BySubscriptionTierNotOrderByCreatedAtAsc("free")) {
            top.add(new TopSubscriberDto(u.getId().toString(),
                    u.getDisplayName(), u.getSubscriptionTier(), u.getCreatedAt()));
        }

        return new InsightsDto(
                (int) (plus * PLUS_KES + gold * GOLD_KES),
                PLUS_KES, GOLD_KES,
                free, plus, gold,
                users.countByCreatedAtAfter(now.minus(Duration.ofDays(1))),
                users.countByCreatedAtAfter(now.minus(Duration.ofDays(7))),
                users.countByCreatedAtAfter(now.minus(Duration.ofDays(30))),
                weeks, top);
    }

    // ── Live presence (who is connected via WebSocket right now) ───────────────
    @Transactional(readOnly = true)
    public AdminDtos.LiveDto live() {
        requireAdmin();
        final List<AdminDtos.OnlineUserDto> online = new ArrayList<>();
        for (UUID id : gateway.onlineUserIds()) {
            User u = users.findById(id).orElse(null);
            if (u == null) continue;
            Profile p = profiles.findById(id).orElse(null);
            String name = p != null && p.getName() != null ? p.getName() : u.getDisplayName();
            online.add(new AdminDtos.OnlineUserDto(id.toString(), name, u.getSubscriptionTier()));
        }
        online.sort((a, b) -> a.name() == null ? 1 : a.name().compareToIgnoreCase(b.name() == null ? "" : b.name()));
        return new AdminDtos.LiveDto(gateway.onlineCount(), gateway.connectionCount(), online);
    }

    // ── Top endpoints (from Micrometer's http.server.requests timers) ──────────
    @Transactional(readOnly = true)
    public List<AdminDtos.EndpointDto> endpoints() {
        requireAdmin();
        // key = "METHOD URI" → [count, totalMs, maxMs, errorCount]
        final Map<String, double[]> agg = new HashMap<>();
        for (Timer t : registry.find("http.server.requests").timers()) {
            String uri = t.getId().getTag("uri");
            if (uri == null || uri.startsWith("/actuator") || "UNKNOWN".equals(uri)) continue;
            String method = t.getId().getTag("method");
            String status = t.getId().getTag("status");
            String key = (method == null ? "?" : method) + " " + uri;
            double[] a = agg.computeIfAbsent(key, k -> new double[4]);
            double c = t.count();
            a[0] += c;
            a[1] += t.totalTime(TimeUnit.MILLISECONDS);
            a[2] = Math.max(a[2], t.max(TimeUnit.MILLISECONDS));
            if (status != null && status.startsWith("5")) a[3] += c;
        }
        final List<AdminDtos.EndpointDto> out = new ArrayList<>();
        agg.forEach((key, a) -> {
            int sp = key.indexOf(' ');
            String method = key.substring(0, sp);
            String uri = key.substring(sp + 1);
            double avg = a[0] > 0 ? a[1] / a[0] : 0;
            out.add(new AdminDtos.EndpointDto(method, uri, (long) a[0],
                    round1(avg), round1(a[2]), (long) a[3]));
        });
        out.sort((x, y) -> Long.compare(y.count(), x.count()));
        return out.size() > 15 ? out.subList(0, 15) : out;
    }

    // ── Host / process / JVM health (ops view for downtime diagnosis) ──────────
    @Transactional(readOnly = true)
    public AdminDtos.SystemDto system() {
        requireAdmin();

        long uptimeSec = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        var os = ManagementFactory.getOperatingSystemMXBean();
        int cores = os.getAvailableProcessors();
        double loadAvg = round1(os.getSystemLoadAverage());
        double procCpu = 0, sysCpu = 0;
        long hostTotal = 0, hostFree = 0, openFds = 0, maxFds = 0;
        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            procCpu = pct(sun.getProcessCpuLoad());
            sysCpu = pct(sun.getCpuLoad());
            hostTotal = sun.getTotalMemorySize();
            hostFree = sun.getFreeMemorySize();
        }
        if (os instanceof com.sun.management.UnixOperatingSystemMXBean unix) {
            openFds = unix.getOpenFileDescriptorCount();
            maxFds = unix.getMaxFileDescriptorCount();
        }

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();

        var threadMx = ManagementFactory.getThreadMXBean();

        long gcCount = 0, gcTime = 0;
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc.getCollectionCount() > 0) gcCount += gc.getCollectionCount();
            if (gc.getCollectionTime() > 0) gcTime += gc.getCollectionTime();
        }

        // Disk on the volume that holds uploads (falls back to the working dir).
        File diskDir = new File("./uploads");
        if (!diskDir.exists()) diskDir = new File(System.getProperty("user.dir", "."));
        long diskTotal = diskDir.getTotalSpace();
        long diskFree = diskDir.getUsableSpace();

        // Connection pool (HikariCP, surfaced by Micrometer).
        int dbActive = (int) gaugeVal("hikaricp.connections.active");
        int dbIdle = (int) gaugeVal("hikaricp.connections.idle");
        int dbPending = (int) gaugeVal("hikaricp.connections.pending");
        int dbMax = (int) gaugeVal("hikaricp.connections.max");

        // HTTP traffic + error classes.
        long total = 0, c4 = 0, c5 = 0;
        double totMs = 0;
        for (Timer t : registry.find("http.server.requests").timers()) {
            long c = (long) t.count();
            total += c;
            totMs += t.totalTime(TimeUnit.MILLISECONDS);
            String s = t.getId().getTag("status");
            if (s != null && s.startsWith("4")) c4 += c;
            if (s != null && s.startsWith("5")) c5 += c;
        }
        double avgMs = total > 0 ? round1(totMs / total) : 0;

        // Dependency health — auto-discovers Postgres/Redis/diskSpace indicators.
        final List<AdminDtos.HealthDto> deps = new ArrayList<>();
        if (healthIndicators != null) {
            healthIndicators.forEach((name, ind) -> {
                if (name.toLowerCase().contains("ping")) return;
                String status;
                try {
                    status = ind.health().getStatus().getCode();
                } catch (Exception e) {
                    status = "DOWN";
                }
                deps.add(new AdminDtos.HealthDto(prettyHealth(name), status));
            });
            deps.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        }

        return new AdminDtos.SystemDto(
                uptimeSec, cores, procCpu, sysCpu, loadAvg,
                mb(heap.getUsed()), mb(heap.getMax()), mb(heap.getCommitted()),
                mb(nonHeap.getUsed()),
                mb(hostTotal - hostFree), mb(hostTotal),
                gb(diskTotal - diskFree), gb(diskTotal), gb(diskFree),
                threadMx.getThreadCount(), threadMx.getPeakThreadCount(), threadMx.getDaemonThreadCount(),
                gcCount, gcTime,
                openFds, maxFds,
                dbActive, dbIdle, dbPending, dbMax,
                total, c4, c5, avgMs,
                deps);
    }

    private double gaugeVal(String name) {
        var gauges = registry.find(name).gauges();
        if (gauges.isEmpty()) return 0;
        double sum = 0;
        for (Gauge g : gauges) {
            double v = g.value();
            if (!Double.isNaN(v)) sum += v;
        }
        return sum;
    }

    private static double pct(double fraction) {
        return fraction < 0 ? 0 : Math.round(fraction * 1000.0) / 10.0;
    }

    private static long mb(long bytes) {
        return bytes <= 0 ? 0 : bytes / (1024 * 1024);
    }

    private static long gb(long bytes) {
        return bytes <= 0 ? 0 : Math.round(bytes / (1024.0 * 1024 * 1024));
    }

    private static String prettyHealth(String beanName) {
        String n = beanName.replaceAll("(?i)healthindicator$", "");
        switch (n.toLowerCase()) {
            case "db": case "datasource": return "Database";
            case "redis": return "Redis";
            case "diskspace": return "Disk space";
            case "mail": return "Mail";
            case "ssl": return "SSL";
            default:
                if (n.isEmpty()) return beanName;
                return Character.toUpperCase(n.charAt(0)) + n.substring(1);
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
