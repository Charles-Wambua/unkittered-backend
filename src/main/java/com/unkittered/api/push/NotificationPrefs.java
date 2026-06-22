package com.unkittered.api.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** A user's per-type push preferences. A missing row means "all enabled". */
@Entity
@Table(name = "notification_prefs")
public class NotificationPrefs {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private boolean matches = true;

    @Column(nullable = false)
    private boolean messages = true;

    @Column(nullable = false)
    private boolean reminders = true;

    @Column(nullable = false)
    private boolean likes = true;

    protected NotificationPrefs() { }

    public NotificationPrefs(UUID userId) {
        this.userId = userId;
    }

    /** Does this user allow a push of the given client "type" tag? */
    public boolean allows(String type) {
        if (type == null) return true;
        return switch (type) {
            case "match" -> matches;
            case "message" -> messages;
            case "meetup" -> reminders;
            case "like" -> likes;
            default -> true;
        };
    }

    public UUID getUserId() { return userId; }
    public boolean isMatches() { return matches; }
    public void setMatches(boolean v) { this.matches = v; }
    public boolean isMessages() { return messages; }
    public void setMessages(boolean v) { this.messages = v; }
    public boolean isReminders() { return reminders; }
    public void setReminders(boolean v) { this.reminders = v; }
    public boolean isLikes() { return likes; }
    public void setLikes(boolean v) { this.likes = v; }
}
