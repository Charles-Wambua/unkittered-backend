package com.unkittered.api.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RealtimeGatewayTest {

    final UUID nia = UUID.randomUUID();
    final UUID daniel = UUID.randomUUID();
    final UUID matchId = UUID.randomUUID();

    RealtimePeers peers;
    RealtimeGateway gateway;

    @BeforeEach
    void setUp() {
        peers = mock(RealtimePeers.class);
        when(peers.presenceAudience(any())).thenReturn(List.of());
        gateway = new RealtimeGateway(new ObjectMapper(), peers);
    }

    WebSocketSession session(UUID userId) {
        WebSocketSession s = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(AuthHandshakeInterceptor.USER_ID, userId);
        when(s.getAttributes()).thenReturn(attributes);
        when(s.getId()).thenReturn(UUID.randomUUID().toString());
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    static List<String> sentTo(WebSocketSession s) throws Exception {
        ArgumentCaptor<TextMessage> frames = ArgumentCaptor.forClass(TextMessage.class);
        verify(s, atLeast(0)).sendMessage(frames.capture());
        return frames.getAllValues().stream().map(TextMessage::getPayload).toList();
    }

    static String typing(UUID matchId) {
        return "{\"type\":\"typing\",\"matchId\":\"" + matchId + "\"}";
    }

    @Test
    void typing_is_relayed_to_the_other_member_only() throws Exception {
        WebSocketSession mine = session(nia);
        WebSocketSession theirs = session(daniel);
        gateway.afterConnectionEstablished(mine);
        gateway.afterConnectionEstablished(theirs);
        when(peers.otherMember(matchId, nia)).thenReturn(Optional.of(daniel));

        gateway.handleMessage(mine, new TextMessage(typing(matchId)));

        assertThat(sentTo(theirs)).containsExactly(
                "{\"type\":\"typing\",\"matchId\":\"" + matchId + "\",\"userId\":\"" + nia + "\"}");
        assertThat(sentTo(mine)).isEmpty();
    }

    @Test
    void typing_in_a_chat_that_is_not_yours_goes_nowhere() throws Exception {
        WebSocketSession mine = session(nia);
        WebSocketSession theirs = session(daniel);
        gateway.afterConnectionEstablished(mine);
        gateway.afterConnectionEstablished(theirs);
        when(peers.otherMember(matchId, nia)).thenReturn(Optional.empty());

        gateway.handleMessage(mine, new TextMessage(typing(matchId)));

        assertThat(sentTo(theirs)).isEmpty();
    }

    @Test
    void typing_frames_faster_than_one_a_second_are_dropped() throws Exception {
        WebSocketSession mine = session(nia);
        WebSocketSession theirs = session(daniel);
        gateway.afterConnectionEstablished(mine);
        gateway.afterConnectionEstablished(theirs);
        when(peers.otherMember(matchId, nia)).thenReturn(Optional.of(daniel));

        for (int i = 0; i < 5; i++) gateway.handleMessage(mine, new TextMessage(typing(matchId)));

        assertThat(sentTo(theirs)).hasSize(1);
        verify(peers, times(1)).otherMember(matchId, nia);
        assertThat(RealtimeGateway.typingAllowed(null, 5_000)).isTrue();
        assertThat(RealtimeGateway.typingAllowed(4_500L, 5_000)).isFalse();
        assertThat(RealtimeGateway.typingAllowed(4_000L, 5_000)).isTrue();
    }

    @Test
    void malformed_and_unknown_frames_are_ignored_and_never_close_the_socket() throws Exception {
        WebSocketSession mine = session(nia);
        gateway.afterConnectionEstablished(mine);
        when(peers.otherMember(any(), any())).thenThrow(new IllegalStateException("db down"));

        for (String frame : List.of("", "not json", "[1,2]", "42", "{\"type\":\"dance\"}", "{\"type\":\"typing\"}",
                "{\"type\":\"typing\",\"matchId\":\"nope\"}", "{\"type\":\"typing\",\"matchId\":null}",
                typing(matchId))) {
            gateway.handleMessage(mine, new TextMessage(frame));
        }
        gateway.handleMessage(mine, new BinaryMessage(ByteBuffer.wrap(new byte[] {1, 2, 3})));

        verify(mine, never()).close();
        verify(mine, never()).close(any());
        assertThat(sentTo(mine)).isEmpty();
    }

    @Test
    void presence_is_sent_for_the_first_session_and_the_last_one_only() throws Exception {
        UUID stranger = UUID.randomUUID();
        WebSocketSession theirs = session(daniel);
        gateway.afterConnectionEstablished(theirs);
        // Matched with Daniel (online) and with someone who isn't connected.
        when(peers.presenceAudience(nia)).thenReturn(List.of(daniel, stranger));

        WebSocketSession phone = session(nia);
        WebSocketSession tablet = session(nia);
        gateway.afterConnectionEstablished(phone);
        gateway.afterConnectionEstablished(tablet);
        gateway.afterConnectionClosed(phone, CloseStatus.NORMAL);
        assertThat(sentTo(theirs)).containsExactly(
                "{\"type\":\"presence\",\"userId\":\"" + nia + "\",\"online\":true}");

        gateway.afterConnectionClosed(tablet, CloseStatus.NORMAL);
        assertThat(sentTo(theirs)).containsExactly(
                "{\"type\":\"presence\",\"userId\":\"" + nia + "\",\"online\":true}",
                "{\"type\":\"presence\",\"userId\":\"" + nia + "\",\"online\":false}");
        assertThat(gateway.onlineUserIds()).containsExactly(daniel);
    }

    @Test
    void presence_failures_never_escape_the_socket_callbacks() {
        WebSocketSession theirs = session(daniel);
        gateway.afterConnectionEstablished(theirs);
        when(peers.presenceAudience(nia)).thenThrow(new IllegalStateException("db down"));

        WebSocketSession mine = session(nia);
        gateway.afterConnectionEstablished(mine);
        gateway.afterConnectionClosed(mine, CloseStatus.NORMAL);

        assertThat(gateway.onlineUserIds()).containsExactly(daniel);
    }
}
