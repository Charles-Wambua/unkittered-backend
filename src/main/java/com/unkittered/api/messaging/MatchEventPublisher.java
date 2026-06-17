package com.unkittered.api.messaging;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MatchEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MatchEventPublisher.class);

    private final SqsTemplate sqsTemplate;
    private final String queue;

    public MatchEventPublisher(SqsTemplate sqsTemplate,
                               @Value("${unkittered.queue.match-events}") String queue) {
        this.sqsTemplate = sqsTemplate;
        this.queue = queue;
    }

    public void publish(MatchCreatedEvent event) {
        try {
            sqsTemplate.send(to -> to.queue(queue).payload(event));
            log.debug("Published MatchCreatedEvent {} to {}", event.matchId(), queue);
        } catch (Exception e) {
            // Never fail the request because the async fan-out hiccupped.
            log.error("Failed to publish MatchCreatedEvent {}: {}", event.matchId(), e.getMessage());
        }
    }
}
