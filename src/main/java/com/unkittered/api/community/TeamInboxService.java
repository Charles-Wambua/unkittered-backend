package com.unkittered.api.community;

import com.unkittered.api.chat.ChatService;
import com.unkittered.api.chat.OfficialChatService;
import com.unkittered.api.chat.RichMessages;
import com.unkittered.api.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * The team's side of the always-on chats: members' messages to the
 * Unkittered account, read and answered from the admin dashboard.
 */
@Service
public class TeamInboxService {

    private static final int THREAD_LIMIT = 100;
    private static final int MAX_REPLY = 2000;

    private final JdbcTemplate jdbc;
    private final OfficialChatService official;
    private final ChatService chat;

    public TeamInboxService(JdbcTemplate jdbc, OfficialChatService official, ChatService chat) {
        this.jdbc = jdbc;
        this.official = official;
        this.chat = chat;
    }

    public record ConversationDto(String matchId, String memberId, String name, String photoUrl,
                                  String lastText, Instant lastAt, boolean lastFromMember, int unread) { }

    public record MemberDto(String id, String name, String photoUrl, String email) { }

    public record MessageDto(String id, boolean fromTeam, String kind, String text, Instant at) { }

    public record ThreadDto(MemberDto member, List<MessageDto> messages) { }

    /** Team chats with at least one message: unread first, then newest. */
    public List<ConversationDto> inbox() {
        UUID team = teamId();
        return jdbc.query("""
                SELECT m.id, o.id AS member, coalesce(p.name, ''), p.image_url,
                       last.body, last.created_at, last.sender_id <> ?,
                       (SELECT count(*) FROM messages x WHERE x.match_id = m.id AND x.sender_id <> ?
                          AND x.read_at IS NULL AND x.deleted_at IS NULL) AS unread
                FROM matches m
                JOIN users o ON o.id = CASE WHEN m.user_low = ? THEN m.user_high ELSE m.user_low END
                LEFT JOIN profiles p ON p.user_id = o.id
                JOIN LATERAL (SELECT body, created_at, sender_id FROM messages y
                              WHERE y.match_id = m.id AND y.deleted_at IS NULL
                              ORDER BY created_at DESC LIMIT 1) last ON true
                WHERE (m.user_low = ? OR m.user_high = ?)
                  AND EXISTS (SELECT 1 FROM messages z WHERE z.match_id = m.id AND z.sender_id <> ?)
                ORDER BY (SELECT count(*) FROM messages x WHERE x.match_id = m.id AND x.sender_id <> ?
                            AND x.read_at IS NULL AND x.deleted_at IS NULL) > 0 DESC,
                         last.created_at DESC
                LIMIT 200
                """, (rs, i) -> new ConversationDto(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getTimestamp(6).toInstant(), rs.getBoolean(7), rs.getInt(8)),
                team, team, team, team, team, team, team);
    }

    /** One conversation, oldest first. Opening it marks the member's messages read. */
    public ThreadDto thread(UUID matchId) {
        UUID team = teamId();
        UUID member = memberOf(matchId, team);
        MemberDto who = jdbc.query("""
                SELECT u.id, coalesce(p.name, ''), p.image_url, u.email
                FROM users u LEFT JOIN profiles p ON p.user_id = u.id WHERE u.id = ?
                """, (rs, i) -> new MemberDto(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                member).stream().findFirst().orElseThrow(() -> ApiException.notFound("Member not found"));
        List<MessageDto> messages = new ArrayList<>(jdbc.query("""
                SELECT id, sender_id = ?, kind, body, created_at FROM messages
                WHERE match_id = ? AND deleted_at IS NULL
                ORDER BY created_at DESC LIMIT ?
                """, (rs, i) -> new MessageDto(rs.getString(1), rs.getBoolean(2), rs.getString(3),
                rs.getString(4), rs.getTimestamp(5).toInstant()), team, matchId, THREAD_LIMIT));
        Collections.reverse(messages);
        jdbc.update("UPDATE messages SET read_at = now() WHERE match_id = ? AND sender_id <> ? AND read_at IS NULL",
                matchId, team);
        return new ThreadDto(who, messages);
    }

    /** Replies as the Unkittered team; the member gets a push. */
    public void reply(UUID matchId, String text) {
        String body = text == null ? "" : text.strip();
        if (body.isEmpty()) throw ApiException.badRequest("Write a reply first");
        if (body.length() > MAX_REPLY) throw ApiException.badRequest("Keep replies under " + MAX_REPLY + " characters");
        UUID team = teamId();
        UUID member = memberOf(matchId, team);
        chat.postAs(matchId, team, member, RichMessages.Kind.TEXT, body, null, true);
        jdbc.update("UPDATE messages SET read_at = now() WHERE match_id = ? AND sender_id <> ? AND read_at IS NULL",
                matchId, team);
    }

    private UUID teamId() {
        return official.officialId().orElseThrow(() -> ApiException.notFound("No official account set up"));
    }

    private UUID memberOf(UUID matchId, UUID team) {
        return jdbc.query("SELECT user_low, user_high FROM matches WHERE id = ?", (rs, i) -> {
            UUID low = rs.getObject(1, UUID.class);
            UUID high = rs.getObject(2, UUID.class);
            return team.equals(low) ? high : team.equals(high) ? low : null;
        }, matchId).stream().filter(Objects::nonNull).findFirst()
                .orElseThrow(() -> ApiException.notFound("Not a team conversation"));
    }
}
