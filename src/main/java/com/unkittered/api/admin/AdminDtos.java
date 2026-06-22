package com.unkittered.api.admin;

import java.time.Instant;
import java.util.List;

/** Wire payloads for the admin dashboard. */
public final class AdminDtos {

    private AdminDtos() { }

    /** Headline metrics for the dashboard cards/tables. */
    public record StatsDto(
            long users,
            long premiumUsers,
            long verifiedUsers,
            long pendingVerifications,
            long matches,
            long meetups,
            long userReports,
            long meetupReports) {
    }

    /** A pending verification awaiting review. */
    public record PendingVerificationDto(
            String userId,
            String name,
            String email,
            String selfieUrl,
            List<String> profilePhotos,
            Instant submittedAt) {
    }

    /** A moderation report (user-on-user or against a meetup). */
    public record ReportDto(
            String id,          // the report row id (for dismiss)
            String type,        // "user" | "meetup"
            String reporterId,
            String targetId,    // reported user id or meetup id
            String reason,
            Instant at) {
    }

    /** Revenue (estimated) + growth + membership for the insights panel. */
    public record InsightsDto(
            int estimatedMrrKes,      // current monthly recurring est. (tier × price)
            int plusPriceKes,
            int goldPriceKes,
            long freeUsers,
            long plusUsers,
            long goldUsers,
            long newUsersToday,
            long newUsersThisWeek,
            long newUsersThisMonth,
            List<WeekPoint> signupsByWeek,
            List<TopSubscriberDto> topSubscribers) {
    }

    public record WeekPoint(String weekStart, long count) { }

    public record TopSubscriberDto(
            String userId, String name, String tier, Instant since) {
    }

    /** Who is connected via WebSocket right now (live presence). */
    public record LiveDto(
            int onlineUsers,
            int connections,
            List<OnlineUserDto> users) {
    }

    public record OnlineUserDto(String userId, String name, String tier) { }

    /** One HTTP route's traffic, aggregated from Micrometer's http.server.requests. */
    public record EndpointDto(
            String method,
            String uri,
            long count,
            double avgMs,
            double maxMs,
            long errors) {
    }

    /** Process / host / JVM health for the ops panel — everything an admin needs
     *  to diagnose a slowdown or downtime at a glance. */
    public record SystemDto(
            long uptimeSeconds,
            int cpuCores,
            double processCpuPercent,   // this app's CPU use
            double systemCpuPercent,    // whole host CPU use
            double loadAverage,         // 1-min run-queue (−1 if unsupported)
            long heapUsedMb, long heapMaxMb, long heapCommittedMb,
            long nonHeapUsedMb,
            long hostMemUsedMb, long hostMemTotalMb,
            long diskUsedGb, long diskTotalGb, long diskFreeGb,
            int threadsLive, int threadsPeak, int threadsDaemon,
            long gcCount, long gcTimeMs,
            long openFileDescriptors, long maxFileDescriptors,
            int dbActive, int dbIdle, int dbPending, int dbMax,
            long totalRequests, long requests4xx, long requests5xx, double avgLatencyMs,
            List<HealthDto> dependencies) {
    }

    /** A single dependency's health (e.g. Database/Redis/Disk → UP/DOWN). */
    public record HealthDto(String name, String status) { }
}
