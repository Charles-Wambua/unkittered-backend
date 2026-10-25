package com.unkittered.api.chat;

import com.unkittered.api.common.ApiException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Shared rules for reactions and edits, in 1:1 and pack chats. Pure, so it's unit-tested. */
public final class Reactions {

    private Reactions() { }

    /** The quick-reaction row, in display order. */
    public static final List<String> ALLOWED = List.of("❤️", "😂", "🐾", "👍", "😮", "😢", "🔥", "🙏");

    /** How long after sending a message can still be edited. */
    public static final Duration EDIT_WINDOW = Duration.ofMinutes(15);

    public static String validate(String emoji) {
        if (emoji == null || !ALLOWED.contains(emoji)) {
            throw ApiException.badRequest("Pick one of " + String.join(" ", ALLOWED));
        }
        return emoji;
    }

    public static void requireEditable(Instant sentAt, Instant now) {
        if (now.isAfter(sentAt.plus(EDIT_WINDOW))) {
            throw ApiException.badRequest("Messages can be edited for 15 minutes after sending.");
        }
    }

    /** emoji → who reacted, emoji in the {@link #ALLOWED} order. */
    public static <R> Map<String, List<String>> group(Collection<R> rows, Function<R, String> emoji,
                                                      Function<R, UUID> user) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String e : ALLOWED) {
            List<String> who = new ArrayList<>();
            for (R r : rows) if (e.equals(emoji.apply(r))) who.add(user.apply(r).toString());
            if (!who.isEmpty()) out.put(e, who);
        }
        return out;
    }
}
