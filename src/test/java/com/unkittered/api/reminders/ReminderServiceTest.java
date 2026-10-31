package com.unkittered.api.reminders;

import com.unkittered.api.mail.MailService;
import com.unkittered.api.push.PushService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderServiceTest {

    final UUID ana = UUID.randomUUID();
    final UUID ben = UUID.randomUUID();
    final UUID asleep = UUID.randomUUID();   // outside quiet hours or reminders off

    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final PushService push = mock(PushService.class);

    private ReminderService.Reminder r(UUID user, String kind) {
        return new ReminderService.Reminder(user, kind, "", "t", "b", Map.of("go", "today"));
    }

    private ReminderService service(List<ReminderService.Reminder> chat, List<ReminderService.Reminder> other) {
        when(push.isEnabled()).thenReturn(true);
        when(jdbc.queryForList(anyString(), eq(UUID.class))).thenReturn(List.of(ana, ben));
        return new ReminderService(jdbc, push, mock(MailService.class)) {
            @Override List<Reminder> unreadMessages() { return chat; }
            @Override List<Reminder> unmessagedMatches() { return List.of(); }
            @Override List<Reminder> stalledChats() { return List.of(); }
            @Override List<Reminder> onboarding() { return other; }
            @Override List<Reminder> wagsWaiting() { return List.of(); }
            @Override List<Reminder> newPeopleNearby() { return List.of(); }
            @Override List<Reminder> takingABreak() { return List.of(); }
            @Override List<Reminder> packsThisWeekend() { return List.of(); }
            @Override List<Reminder> noPhoto() { return List.of(); }
            @Override List<Reminder> notVerified() { return List.of(); }
            @Override List<Reminder> anniversary() { return List.of(); }
        };
    }

    @Test
    void capsChatAndOtherRemindersPerMember() {
        ReminderService s = service(
                List.of(r(ana, "unread"), r(ana, "unread"), r(ana, "unread"), r(asleep, "unread")),
                List.of(r(ana, "onboard_1"), r(ana, "no_photo"), r(ben, "verify")));

        assertThat(s.run()).isEqualTo(4);   // ana: 2 chat + 1 other; ben: 1 other

        verify(push, times(3)).sendToUser(eq(ana), anyString(), anyString(), anyMap());
        verify(push, times(1)).sendToUser(eq(ben), anyString(), anyString(), anyMap());
        verify(push, never()).sendToUser(eq(asleep), anyString(), anyString(), anyMap());
    }

    @Test
    void remindersCarryTheirKindForOpenTracking() {
        ReminderService s = service(List.of(), List.of(r(ben, "verify")));
        s.run();
        verify(push).sendToUser(eq(ben), anyString(), anyString(),
                eq(Map.of("go", "today", "type", "reminder", "kind", "verify", "ref", "", "channel", "unkittered_updates")));
        verify(jdbc).update(anyString(), eq(ben), eq("verify"), eq(""));
    }

    @Test
    void nothingWithoutPush() {
        when(push.isEnabled()).thenReturn(false);
        ReminderService s = new ReminderService(jdbc, push, mock(MailService.class));
        assertThat(s.run()).isZero();
        verify(jdbc, never()).queryForList(anyString(), any(Class.class));
    }
}
