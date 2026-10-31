package com.unkittered.api.admin;

import com.unkittered.api.admin.AdminDtos.PendingVerificationDto;
import com.unkittered.api.admin.AdminDtos.ReportDto;
import com.unkittered.api.admin.AdminDtos.StatsDto;
import com.unkittered.api.common.ApiException;
import com.unkittered.api.mail.MemberNotifier;
import com.unkittered.api.reminders.ReminderService;
import com.unkittered.api.storage.StorageService;
import com.unkittered.api.verification.SterilisationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin dashboard API. All routes require an admin account (enforced in the service). */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService admin;
    private final AdminInsightsService insights;
    private final SterilisationService sterilisation;
    private final BroadcastService broadcasts;
    private final MemberNotifier notify;
    private final StorageService storage;
    private final ReminderService reminders;

    public AdminController(AdminService admin, AdminInsightsService insights,
                           SterilisationService sterilisation, BroadcastService broadcasts,
                           MemberNotifier notify, StorageService storage, ReminderService reminders) {
        this.admin = admin;
        this.insights = insights;
        this.sterilisation = sterilisation;
        this.broadcasts = broadcasts;
        this.notify = notify;
        this.storage = storage;
        this.reminders = reminders;
    }

    @Operation(summary = "A private verification file (selfie or sterilisation document), admins only")
    @GetMapping("/private-files/{name}")
    public ResponseEntity<byte[]> privateFile(@PathVariable String name) {
        admin.requireAdmin();
        StorageService.PrivateFile f = storage.readPrivate(name)
                .orElseThrow(() -> ApiException.notFound("No such file"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(f.bytes());
    }

    @Operation(summary = "Which channels can reach members (push, email)")
    @GetMapping("/broadcasts/channels")
    public Map<String, Object> broadcastChannels() {
        return broadcasts.channels();
    }

    @Operation(summary = "How many members an audience reaches (suspended accounts excluded)")
    @GetMapping("/broadcasts/audience")
    public Map<String, Long> broadcastAudience(@RequestParam(defaultValue = "all") String audience) {
        return Map.of("count", broadcasts.audienceSize(audience));
    }

    @Operation(summary = "Send an announcement by push and/or email; test=true sends only to you")
    @PostMapping("/broadcasts")
    public BroadcastService.Result broadcast(@RequestBody BroadcastService.Request request) {
        return broadcasts.send(request);
    }

    @Operation(summary = "Cancel a scheduled announcement (members already reached keep it)")
    @PostMapping("/broadcasts/{id}/cancel")
    public void cancelBroadcast(@PathVariable String id) {
        broadcasts.cancel(parseId(id));
    }

    @Operation(summary = "Reminders and announcements sent and opened in the last 30 days, by kind")
    @GetMapping("/reminders/stats")
    public List<Map<String, Object>> reminderStats() {
        admin.requireAdmin();
        return reminders.stats();
    }

    @Operation(summary = "Announcements sent so far, newest first")
    @GetMapping("/broadcasts")
    public List<Map<String, Object>> broadcastHistory() {
        return broadcasts.history();
    }

    @Operation(summary = "Pending sterilisation proofs (gold badge), with the member who sent each")
    @GetMapping("/sterilisation-proofs")
    public List<Map<String, Object>> pendingSterilisationProofs() {
        return insights.pendingProofs();
    }

    @Operation(summary = "Reports with names, the reporter's details and how often the target was reported")
    @GetMapping("/reports/detailed")
    public List<Map<String, Object>> detailedReports() {
        return insights.reports();
    }

    @Operation(summary = "Search members by name, email or id (filter: suspended, verified, paying, reported, incomplete)")
    @GetMapping("/members")
    public List<Map<String, Object>> members(@RequestParam(defaultValue = "") String q,
                                             @RequestParam(defaultValue = "") String filter) {
        return insights.members(q, filter);
    }

    @Operation(summary = "One member's profile, pets, activity counts and reports against them")
    @GetMapping("/members/{id}")
    public Map<String, Object> member(@PathVariable String id) {
        Map<String, Object> m = insights.member(parseId(id));
        if (m.isEmpty()) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "No such member");
        }
        return m;
    }

    @Operation(summary = "Activity, funnel, audience and wallet analytics")
    @GetMapping("/analytics")
    public Map<String, Object> analytics() {
        return insights.analytics();
    }

    @Operation(summary = "Approve a sterilisation proof (grants the verified badge; clears the document)")
    @PostMapping("/sterilisation-proofs/{userId}/approve")
    public void approveSterilisation(@PathVariable String userId) {
        admin.requireAdmin();
        UUID id = parseId(userId);
        sterilisation.review(id, true);
        notify.sterilisationReviewed(id, true);
    }

    @Operation(summary = "Reject a sterilisation proof (clears the document)")
    @PostMapping("/sterilisation-proofs/{userId}/reject")
    public void rejectSterilisation(@PathVariable String userId) {
        admin.requireAdmin();
        UUID id = parseId(userId);
        sterilisation.review(id, false);
        notify.sterilisationReviewed(id, false);
    }

    @Operation(summary = "Headline metrics")
    @GetMapping("/stats")
    public StatsDto stats() {
        return admin.stats();
    }

    @Operation(summary = "Whether the caller is an admin (for dashboard gating)")
    @GetMapping("/me")
    public java.util.Map<String, Boolean> me() {
        admin.requireAdmin();
        return java.util.Map.of("admin", true);
    }

    @Operation(summary = "Pending verification requests")
    @GetMapping("/verifications")
    public List<PendingVerificationDto> pendingVerifications() {
        return admin.pendingVerifications();
    }

    @Operation(summary = "Approve a user's verification")
    @PostMapping("/verifications/{userId}/approve")
    public void approve(@PathVariable String userId) {
        UUID id = parseId(userId);
        admin.review(id, true);
        notify.selfieReviewed(id, true);
    }

    @Operation(summary = "Reject a user's verification")
    @PostMapping("/verifications/{userId}/reject")
    public void reject(@PathVariable String userId) {
        UUID id = parseId(userId);
        admin.review(id, false);
        notify.selfieReviewed(id, false);
    }

    @Operation(summary = "Moderation reports (users + meetups)")
    @GetMapping("/reports")
    public List<ReportDto> reports() {
        return admin.reports();
    }

    @Operation(summary = "Revenue estimate, growth & top subscribers")
    @GetMapping("/insights")
    public AdminDtos.InsightsDto insights() {
        return admin.insights();
    }

    @Operation(summary = "Users connected via WebSocket right now")
    @GetMapping("/live")
    public AdminDtos.LiveDto live() {
        return admin.live();
    }

    @Operation(summary = "Most-used endpoints (count, latency, errors)")
    @GetMapping("/endpoints")
    public List<AdminDtos.EndpointDto> endpoints() {
        return admin.endpoints();
    }

    @Operation(summary = "Process/JVM health (uptime, memory, CPU, threads)")
    @GetMapping("/system")
    public AdminDtos.SystemDto system() {
        return admin.system();
    }

    @Operation(summary = "Suspend a user (blocks their sign-in)")
    @PostMapping("/users/{id}/suspend")
    public void suspend(@PathVariable String id) {
        UUID userId = parseId(id);
        admin.suspendUser(userId, true);
        notify.suspended(userId);
        notify.reportersThanked(userId);
    }

    @Operation(summary = "Lift a user's suspension")
    @PostMapping("/users/{id}/unsuspend")
    public void unsuspend(@PathVariable String id) {
        UUID userId = parseId(id);
        admin.suspendUser(userId, false);
        notify.reinstated(userId);
    }

    @Operation(summary = "Remove a meetup (admin override)")
    @DeleteMapping("/meetups/{id}")
    public void removeMeetup(@PathVariable String id) {
        UUID meetupId = parseId(id);
        notify.packReportersThanked(meetupId);
        admin.removeMeetup(meetupId);
    }

    @Operation(summary = "Dismiss a report")
    @DeleteMapping("/reports/{type}/{id}")
    public void dismissReport(@PathVariable String type, @PathVariable String id) {
        admin.dismissReport(type, parseId(id));
    }

    private UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid id");
        }
    }
}
