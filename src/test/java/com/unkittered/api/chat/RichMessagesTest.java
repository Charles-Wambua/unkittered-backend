package com.unkittered.api.chat;

import com.unkittered.api.chat.RichMessages.Kind;
import com.unkittered.api.chat.RichMessages.PlanRef;
import com.unkittered.api.common.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RichMessagesTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID THEM = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");

    private static RichMessages.Prepared prepare(Kind kind, String text, Map<String, Object> payload) {
        return RichMessages.prepare(kind, text, payload, ME,
                id -> "p1".equals(id) ? new PlanRef(THEM) : "mine".equals(id) ? new PlanRef(ME) : null,
                url -> url.startsWith("/files/"), NOW);
    }

    @Test
    void plainTextKeepsBodyAndHasNoPayload() {
        var p = prepare(Kind.TEXT, "  hi there ", null);
        assertThat(p.body()).isEqualTo("hi there");
        assertThat(p.payload()).isNull();
    }

    @Test
    void textIsRequired() {
        assertThatThrownBy(() -> prepare(Kind.TEXT, " ", null)).isInstanceOf(ApiException.class);
    }

    @Test
    void petNoteStoresBothPetsAndModeratesNames() {
        var p = prepare(Kind.PET_NOTE, null, Map.of("fromPet", "Biscuit", "toPet", "Juno", "text", "Hi Juno"));
        assertThat(p.body()).isEqualTo("Hi Juno");
        assertThat(p.payload()).containsEntry("fromPet", "Biscuit").containsEntry("toPet", "Juno");
        assertThat(p.moderatedText()).contains("Hi Juno", "Biscuit", "Juno");
    }

    @Test
    void photoMustComeFromOurStorage() {
        assertThat(prepare(Kind.PHOTO, null, Map.of("url", "/files/a.jpg")).body()).isEqualTo("📷 Photo");
        assertThatThrownBy(() -> prepare(Kind.PHOTO, null, Map.of("url", "https://evil.example/x.jpg")))
                .hasMessageContaining("uploaded");
    }

    @Test
    void planGetsServerIdAndValidatesTime() {
        var payload = Map.<String, Object>of(
                "title", "Walk with Biscuit & Juno", "place", "Riverside Park",
                "startsAt", NOW.plus(2, ChronoUnit.DAYS).toString(),
                "durationMinutes", 90, "amenities", List.of("Café"));
        var p = prepare(Kind.PLAN, null, payload);
        assertThat(p.payload().get("id")).isInstanceOf(String.class);
        assertThat(p.body()).contains("Walk with Biscuit & Juno");

        var past = new java.util.HashMap<>(payload);
        past.put("startsAt", NOW.minus(2, ChronoUnit.DAYS).toString());
        assertThatThrownBy(() -> prepare(Kind.PLAN, null, past)).hasMessageContaining("next year");
    }

    @Test
    void planResponseMustAnswerSomeoneElsesPlanInThisChat() {
        assertThat(prepare(Kind.PLAN_RESPONSE, null, Map.of("planId", "p1", "accepted", true)).body())
                .contains("accepted");
        assertThatThrownBy(() -> prepare(Kind.PLAN_RESPONSE, null, Map.of("planId", "mine", "accepted", true)))
                .hasMessageContaining("own plan");
        assertThatThrownBy(() -> prepare(Kind.PLAN_RESPONSE, null, Map.of("planId", "nope", "accepted", true)))
                .hasMessageContaining("isn't in this conversation");
    }

    @Test
    void unknownKindIsRejected() {
        assertThatThrownBy(() -> Kind.fromWire("hologram")).isInstanceOf(ApiException.class);
        assertThat(Kind.fromWire(null)).isEqualTo(Kind.TEXT);
    }
}
