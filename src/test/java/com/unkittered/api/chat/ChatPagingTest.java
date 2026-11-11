package com.unkittered.api.chat;

import com.unkittered.api.chat.ChatDtos.MessageDto;
import com.unkittered.api.common.ApiException;
import com.unkittered.api.interaction.Match;
import com.unkittered.api.interaction.MatchRepository;
import com.unkittered.api.pet.PetRepository;
import com.unkittered.api.profile.ProfileMapper;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.push.PushService;
import com.unkittered.api.realtime.RealtimeEvents.ReadPush;
import com.unkittered.api.realtime.RealtimeGateway;
import com.unkittered.api.subscription.SubscriptionService;
import com.unkittered.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatPagingTest {

    static final Instant T0 = Instant.parse("2026-11-01T09:00:00Z");
    final UUID me = UUID.randomUUID();
    final UUID them = UUID.randomUUID();
    final UUID matchId = UUID.randomUUID();

    MatchRepository matches;
    MessageRepository messages;
    ChatClearRepository clears;
    MessageReactionRepository reactions;
    UserRepository users;
    RealtimeGateway realtime;
    ChatService chat;

    @BeforeEach
    void setUp() {
        matches = mock(MatchRepository.class);
        messages = mock(MessageRepository.class);
        clears = mock(ChatClearRepository.class);
        reactions = mock(MessageReactionRepository.class);
        users = mock(UserRepository.class);
        realtime = mock(RealtimeGateway.class);
        chat = new ChatService(matches, messages, mock(ProfileRepository.class), mock(ProfileMapper.class),
                realtime, mock(SubscriptionService.class), mock(PushService.class), mock(PetRepository.class),
                mock(PlanFeedbackRepository.class), clears, reactions, users, "");

        Match match = Match.of(me, them);
        ReflectionTestUtils.setField(match, "id", matchId);
        when(matches.findById(matchId)).thenReturn(Optional.of(match));
        when(users.existsByIdAndSuspendedFalse(them)).thenReturn(true);
        when(clears.findById(any())).thenReturn(Optional.empty());
        when(reactions.findByMessageIdIn(any())).thenReturn(List.of());
    }

    /** A message sent {@code minute} minutes after T0. */
    Message message(int minute, UUID sender) {
        Message m = new Message(matchId, sender, "m" + minute);
        ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(m, "createdAt", T0.plusSeconds(60L * minute));
        return m;
    }

    /** What the page query hands back: newest first. */
    void pageReturns(Message... newestFirst) {
        when(messages.findByMatchIdAndCreatedAtAfterAndCreatedAtBeforeOrderByCreatedAtDesc(
                eq(matchId), any(), any(), any())).thenReturn(new ArrayList<>(List.of(newestFirst)));
    }

    @Test
    void a_page_is_the_newest_messages_oldest_first_and_says_more_remain() {
        // limit 2 asks for 3 rows; the third only proves older messages exist.
        pageReturns(message(5, them), message(4, me), message(3, them));

        var page = chat.messages(me, matchId, 2, null);

        assertThat(page.messages()).extracting(MessageDto::text).containsExactly("m4", "m5");
        assertThat(page.hasMore()).isTrue();
        ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
        verify(messages).findByMatchIdAndCreatedAtAfterAndCreatedAtBeforeOrderByCreatedAtDesc(
                eq(matchId), eq(Instant.EPOCH), any(), asked.capture());
        assertThat(asked.getValue().getPageSize()).isEqualTo(3);
        verify(messages, never()).findByMatchIdOrderByCreatedAtAsc(any());
    }

    @Test
    void the_last_page_has_no_more_and_respects_before_and_delete_chat() {
        Instant cleared = T0.plusSeconds(30);
        when(clears.findById(any())).thenReturn(Optional.of(new ChatClear(me, matchId, cleared)));
        pageReturns(message(2, them), message(1, me));
        Instant before = T0.plusSeconds(180);

        var page = chat.messages(me, matchId, 50, before);

        assertThat(page.messages()).extracting(MessageDto::text).containsExactly("m1", "m2");
        assertThat(page.hasMore()).isFalse();
        verify(messages).findByMatchIdAndCreatedAtAfterAndCreatedAtBeforeOrderByCreatedAtDesc(
                eq(matchId), eq(cleared), eq(before), any());
    }

    @Test
    void limit_is_clamped() {
        assertThat(ChatService.clampLimit(0)).isEqualTo(1);
        assertThat(ChatService.clampLimit(-7)).isEqualTo(1);
        assertThat(ChatService.clampLimit(40)).isEqualTo(40);
        assertThat(ChatService.clampLimit(5000)).isEqualTo(100);
    }

    @Test
    void a_reply_still_quotes_a_message_outside_the_page() {
        Message old = message(1, them);
        Message reply = message(9, me);
        reply.setReplyToId(old.getId());
        pageReturns(reply);
        when(messages.findAllById(any())).thenReturn(List.of(old));

        var page = chat.messages(me, matchId, 1, null);

        assertThat(page.messages().get(0).replyTo().id()).isEqualTo(old.getId().toString());
        assertThat(page.messages().get(0).replyTo().text()).isEqualTo("m1");
    }

    @Test
    void without_a_limit_the_whole_thread_comes_back_as_before() {
        when(messages.findByMatchIdOrderByCreatedAtAsc(matchId))
                .thenReturn(List.of(message(1, them), message(2, me), message(3, them)));

        var page = chat.messages(me, matchId, null, T0);

        assertThat(page.messages()).extracting(MessageDto::text).containsExactly("m1", "m2", "m3");
        assertThat(page.hasMore()).isFalse();
        verify(messages, never()).findByMatchIdAndCreatedAtAfterAndCreatedAtBeforeOrderByCreatedAtDesc(
                any(), any(), any(), any());
    }

    @Test
    void reading_new_messages_tells_the_other_person_once() {
        when(messages.markRead(eq(matchId), eq(me), any())).thenReturn(2);
        pageReturns();

        chat.messages(me, matchId, 20, null);

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(realtime).sendToUser(eq(them), sent.capture());
        ReadPush push = (ReadPush) sent.getValue();
        assertThat(push.type()).isEqualTo("read");
        assertThat(push.matchId()).isEqualTo(matchId.toString());
        assertThat(push.readAt()).isNotNull();
    }

    @Test
    void nothing_newly_read_sends_nothing() {
        when(messages.markRead(eq(matchId), eq(me), any())).thenReturn(0);
        when(messages.findByMatchIdOrderByCreatedAtAsc(matchId)).thenReturn(List.of());

        chat.messages(me, matchId);
        chat.markRead(me, matchId);

        verify(realtime, never()).sendToUser(any(), any());
    }

    @Test
    void mark_read_alone_pushes_too_and_is_closed_to_outsiders() {
        when(messages.markRead(eq(matchId), eq(me), any())).thenReturn(1);

        chat.markRead(me, matchId);

        verify(realtime).sendToUser(eq(them), any(ReadPush.class));
        verify(messages, never()).findByMatchIdOrderByCreatedAtAsc(any());
        assertThatThrownBy(() -> chat.markRead(UUID.randomUUID(), matchId)).isInstanceOf(ApiException.class);
    }
}
