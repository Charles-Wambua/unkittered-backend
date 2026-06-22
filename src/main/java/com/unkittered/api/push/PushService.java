package com.unkittered.api.push;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends FCM push notifications to a user's devices. Resilient by design:
 * if Firebase isn't configured the methods quietly no-op, and tokens that FCM
 * reports as unregistered are pruned so the table stays clean.
 *
 * <p>Sends are {@link Async} so they never sit on the request/event thread.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final ObjectProvider<FirebaseMessaging> messaging;
    private final DeviceTokenRepository tokens;
    private final NotificationPrefsRepository prefs;

    public PushService(ObjectProvider<FirebaseMessaging> messaging, DeviceTokenRepository tokens,
                       NotificationPrefsRepository prefs) {
        this.messaging = messaging;
        this.tokens = tokens;
        this.prefs = prefs;
    }

    public boolean isEnabled() {
        return messaging.getIfAvailable() != null;
    }

    /** Push to every device of a single user. {@code data} is delivered for deep-linking. */
    @Async
    public void sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        sendToUsers(List.of(userId), title, body, data);
    }

    /** Push to every device of several users (e.g. all RSVPs of a meetup). */
    @Async
    public void sendToUsers(List<UUID> userIds, String title, String body, Map<String, String> data) {
        FirebaseMessaging fcm = messaging.getIfAvailable();
        if (fcm == null || userIds.isEmpty()) return;

        // Respect each recipient's per-type preference (missing row = all on).
        String type = data == null ? null : data.get("type");
        List<UUID> recipients = filterByPrefs(userIds, type);
        if (recipients.isEmpty()) return;

        List<DeviceToken> rows = tokens.findByUserIdIn(recipients);
        if (rows.isEmpty()) return;
        List<String> targets = rows.stream().map(DeviceToken::getToken).toList();

        Map<String, String> payload = new HashMap<>(data == null ? Map.of() : data);
        payload.putIfAbsent("title", title);
        payload.putIfAbsent("body", body);

        // FCM multicast caps at 500 tokens per call.
        for (int i = 0; i < targets.size(); i += 500) {
            List<String> chunk = targets.subList(i, Math.min(i + 500, targets.size()));
            MulticastMessage message = MulticastMessage.builder()
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .putAllData(payload)
                    .addAllTokens(chunk)
                    .build();
            try {
                BatchResponse res = fcm.sendEachForMulticast(message);
                pruneFailures(chunk, res);
            } catch (Exception e) {
                log.warn("FCM multicast failed for {} tokens: {}", chunk.size(), e.getMessage());
            }
        }
    }

    /** Keep only users who allow this notification type (no row → allowed). */
    private List<UUID> filterByPrefs(List<UUID> userIds, String type) {
        if (type == null) return userIds;
        Map<UUID, NotificationPrefs> byUser = new HashMap<>();
        prefs.findAllById(userIds).forEach(p -> byUser.put(p.getUserId(), p));
        List<UUID> allowed = new ArrayList<>();
        for (UUID id : userIds) {
            NotificationPrefs p = byUser.get(id);
            if (p == null || p.allows(type)) allowed.add(id);
        }
        return allowed;
    }

    private void pruneFailures(List<String> chunk, BatchResponse res) {
        if (res.getFailureCount() == 0) return;
        List<SendResponse> responses = res.getResponses();
        List<String> dead = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse r = responses.get(i);
            if (r.isSuccessful()) continue;
            String code = r.getException() != null && r.getException().getMessagingErrorCode() != null
                    ? r.getException().getMessagingErrorCode().name() : "";
            // Token no longer valid → drop it.
            if (code.contains("UNREGISTERED") || code.contains("INVALID_ARGUMENT")) {
                dead.add(chunk.get(i));
            }
        }
        for (String token : dead) {
            try { tokens.deleteById(token); } catch (Exception ignored) { }
        }
        if (!dead.isEmpty()) log.info("Pruned {} dead device tokens", dead.size());
    }
}
