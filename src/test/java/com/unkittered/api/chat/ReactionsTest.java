package com.unkittered.api.chat;

import com.unkittered.api.common.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReactionsTest {

    private record Row(String emoji, UUID user) { }

    @Test
    void onlyTheQuickRowIsAllowed() {
        assertThat(Reactions.validate("🐾")).isEqualTo("🐾");
        assertThatThrownBy(() -> Reactions.validate("💩")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Reactions.validate(null)).isInstanceOf(ApiException.class);
    }

    @Test
    void editsCloseAfterFifteenMinutes() {
        Instant sent = Instant.parse("2026-10-25T10:00:00Z");
        Reactions.requireEditable(sent, sent.plusSeconds(14 * 60));
        Reactions.requireEditable(sent, sent.plusSeconds(15 * 60));
        assertThatThrownBy(() -> Reactions.requireEditable(sent, sent.plusSeconds(15 * 60 + 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("15 minutes");
    }

    @Test
    void groupsByEmojiInRowOrder() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var grouped = Reactions.group(List.of(new Row("🔥", a), new Row("❤️", b), new Row("🔥", c)),
                Row::emoji, Row::user);
        assertThat(grouped.keySet()).containsExactly("❤️", "🔥");
        assertThat(grouped.get("🔥")).containsExactly(a.toString(), c.toString());
        assertThat(Reactions.group(List.<Row>of(), Row::emoji, Row::user)).isEmpty();
    }
}
