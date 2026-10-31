package com.unkittered.api.reminders;

import com.unkittered.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the app tells the server so reminders arrive at sensible times and can be measured. */
@RestController
@RequestMapping("/v1/me")
@Tag(name = "Notifications")
@SecurityRequirement(name = "bearerAuth")
public class ReminderController {

    private final JdbcTemplate jdbc;

    public ReminderController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record OffsetRequest(int minutes) { }

    public record OpenedRequest(String kind, String ref) { }

    @Operation(summary = "The phone's current offset from UTC in minutes, for quiet hours")
    @PutMapping("/utc-offset")
    public void utcOffset(@RequestBody OffsetRequest req) {
        int minutes = Math.max(-12 * 60, Math.min(14 * 60, req.minutes()));
        jdbc.update("UPDATE users SET utc_offset_minutes = ? WHERE id = ?", minutes, CurrentUser.id());
    }

    @Operation(summary = "A reminder or announcement was tapped (kind and ref from its data)")
    @PostMapping("/notifications/opened")
    public void opened(@RequestBody OpenedRequest req) {
        if (req.kind() == null || req.kind().isBlank()) return;
        jdbc.update("""
                UPDATE notification_log SET opened_at = now()
                WHERE id = (SELECT id FROM notification_log
                            WHERE user_id = ? AND kind = ? AND ref = ? AND opened_at IS NULL
                            ORDER BY sent_at DESC LIMIT 1)
                """, CurrentUser.id(), req.kind(), req.ref() == null ? "" : req.ref());
    }
}
