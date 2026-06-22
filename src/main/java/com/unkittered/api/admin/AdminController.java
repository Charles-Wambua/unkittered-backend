package com.unkittered.api.admin;

import com.unkittered.api.admin.AdminDtos.PendingVerificationDto;
import com.unkittered.api.admin.AdminDtos.ReportDto;
import com.unkittered.api.admin.AdminDtos.StatsDto;
import com.unkittered.api.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Admin dashboard API. All routes require an admin account (enforced in the service). */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
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
        admin.review(parseId(userId), true);
    }

    @Operation(summary = "Reject a user's verification")
    @PostMapping("/verifications/{userId}/reject")
    public void reject(@PathVariable String userId) {
        admin.review(parseId(userId), false);
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
        admin.suspendUser(parseId(id), true);
    }

    @Operation(summary = "Lift a user's suspension")
    @PostMapping("/users/{id}/unsuspend")
    public void unsuspend(@PathVariable String id) {
        admin.suspendUser(parseId(id), false);
    }

    @Operation(summary = "Remove a meetup (admin override)")
    @DeleteMapping("/meetups/{id}")
    public void removeMeetup(@PathVariable String id) {
        admin.removeMeetup(parseId(id));
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
