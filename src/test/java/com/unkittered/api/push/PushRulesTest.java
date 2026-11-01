package com.unkittered.api.push;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PushRulesTest {

    @Test
    void chatAndPackPushesShareOneNotificationPerConversation() {
        assertThat(PushService.tagFor(Map.of("type", "message", "matchId", "m1"))).isEqualTo("chat:m1");
        assertThat(PushService.tagFor(Map.of("type", "pack_message", "meetupId", "p1"))).isEqualTo("pack:p1");
        assertThat(PushService.tagFor(Map.of("type", "like"))).isEqualTo("wags");
    }

    @Test
    void otherPushesStandAlone() {
        assertThat(PushService.tagFor(Map.of("type", "match", "matchId", "m1"))).isNull();
        assertThat(PushService.tagFor(Map.of("type", "announcement"))).isNull();
        assertThat(PushService.tagFor(Map.of("type", "message"))).isNull();
        assertThat(PushService.tagFor(Map.of())).isNull();
    }

    @Test
    void remindersAndTipsCoversAnnouncements() {
        NotificationPrefs p = new NotificationPrefs(UUID.randomUUID());
        assertThat(p.allows("announcement")).isTrue();
        p.setTips(false);
        assertThat(p.allows("announcement")).isFalse();
        assertThat(p.allows("reminder")).isFalse();
        assertThat(p.allows("message")).isTrue();
        assertThat(p.allows("account")).isTrue();
    }

    @Test
    void likesSettingCoversWagPushes() {
        NotificationPrefs p = new NotificationPrefs(UUID.randomUUID());
        p.setLikes(false);
        assertThat(p.allows("like")).isFalse();
    }
}
