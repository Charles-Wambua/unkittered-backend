package com.unkittered.api.chat;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPreviewTest {

    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID RECIPIENT = UUID.randomUUID();

    @Test
    void giftPreviewIsWordedForWhoeverIsReading() {
        var gift = new Message(UUID.randomUUID(), SENDER, new RichMessages.Prepared(RichMessages.Kind.GIFT,
                "👑 Sent you a Crown", Map.of("giftId", "crown", "name", "Crown", "emoji", "👑")));
        assertThat(ChatService.previewFor(gift, RECIPIENT)).isEqualTo("👑 Sent you a Crown");
        assertThat(ChatService.previewFor(gift, SENDER)).isEqualTo("👑 You sent a Crown");
    }

    @Test
    void otherMessagesKeepTheirText() {
        var text = new Message(UUID.randomUUID(), SENDER, "See you Sunday");
        assertThat(ChatService.previewFor(text, SENDER)).isEqualTo("See you Sunday");
    }
}
