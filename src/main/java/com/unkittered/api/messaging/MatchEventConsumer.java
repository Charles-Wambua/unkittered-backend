package com.unkittered.api.messaging;

import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.push.PushService;
import com.unkittered.api.realtime.RealtimeEvents.MatchPush;
import com.unkittered.api.realtime.RealtimeGateway;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Handles match events off the request path: notifies both users live over the
 * WebSocket AND via mobile push (so they're told even when the app is closed).
 */
@Component
public class MatchEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MatchEventConsumer.class);

    private final RealtimeGateway realtime;
    private final PushService push;
    private final ProfileRepository profiles;

    public MatchEventConsumer(RealtimeGateway realtime, PushService push, ProfileRepository profiles) {
        this.realtime = realtime;
        this.push = push;
        this.profiles = profiles;
    }

    @SqsListener("${unkittered.queue.match-events}")
    public void onMatchCreated(MatchCreatedEvent event) {
        log.info("🎉 It's a match! {} <-> {} (matchId={}, superLike={})",
                event.userA(), event.userB(), event.matchId(), event.superLike());

        // Live update over the socket (open app), then push (closed app).
        realtime.sendToUser(event.userA(), MatchPush.of(event.matchId(), event.userB()));
        realtime.sendToUser(event.userB(), MatchPush.of(event.matchId(), event.userA()));

        notify(event.userA(), event.userB(), event.matchId());
        notify(event.userB(), event.userA(), event.matchId());
    }

    private void notify(UUID recipient, UUID other, UUID matchId) {
        String name = profiles.findById(other).map(Profile::getName).filter(n -> !n.isBlank())
                .orElse("Someone");
        push.sendToUser(recipient,
                "It's a match! 🎉",
                "You and " + name + " liked each other. Say hi!",
                Map.of("type", "match", "matchId", matchId.toString(), "otherUserId", other.toString()));
    }
}
