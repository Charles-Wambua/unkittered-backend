package com.unkittered.api.messaging;

import com.unkittered.api.realtime.RealtimeEvents.MatchPush;
import com.unkittered.api.realtime.RealtimeGateway;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles match events off the request path: notifies both users live over the
 * WebSocket. This is also where push notifications, "It's a match!" emails, and
 * analytics will hook in.
 */
@Component
public class MatchEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MatchEventConsumer.class);

    private final RealtimeGateway realtime;

    public MatchEventConsumer(RealtimeGateway realtime) {
        this.realtime = realtime;
    }

    @SqsListener("${unkittered.queue.match-events}")
    public void onMatchCreated(MatchCreatedEvent event) {
        log.info("🎉 It's a match! {} <-> {} (matchId={}, superLike={})",
                event.userA(), event.userB(), event.matchId(), event.superLike());

        // Tell each side about the new match so their lists update without polling.
        realtime.sendToUser(event.userA(), MatchPush.of(event.matchId(), event.userB()));
        realtime.sendToUser(event.userB(), MatchPush.of(event.matchId(), event.userA()));
        // TODO: also send mobile push notifications / write to a notifications table.
    }
}
