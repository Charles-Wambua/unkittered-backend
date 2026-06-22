package com.unkittered.api.push;

import com.unkittered.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Per-type push notification preferences for the signed-in user. */
@RestController
@RequestMapping("/v1/me/notification-prefs")
@Tag(name = "Notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationPrefsController {

    private final NotificationPrefsRepository repo;

    public NotificationPrefsController(NotificationPrefsRepository repo) {
        this.repo = repo;
    }

    public record PrefsDto(boolean matches, boolean messages, boolean reminders, boolean likes) {
        static PrefsDto from(NotificationPrefs p) {
            return new PrefsDto(p.isMatches(), p.isMessages(), p.isReminders(), p.isLikes());
        }
    }

    @Operation(summary = "Get the caller's notification preferences")
    @GetMapping
    public PrefsDto get() {
        return PrefsDto.from(repo.findById(CurrentUser.id())
                .orElseGet(() -> new NotificationPrefs(CurrentUser.id())));
    }

    @Operation(summary = "Update the caller's notification preferences")
    @PutMapping
    @Transactional
    public PrefsDto update(@RequestBody PrefsDto req) {
        NotificationPrefs p = repo.findById(CurrentUser.id())
                .orElseGet(() -> new NotificationPrefs(CurrentUser.id()));
        p.setMatches(req.matches());
        p.setMessages(req.messages());
        p.setReminders(req.reminders());
        p.setLikes(req.likes());
        return PrefsDto.from(repo.save(p));
    }
}
