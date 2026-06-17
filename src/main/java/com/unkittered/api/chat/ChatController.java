package com.unkittered.api.chat;

import com.unkittered.api.chat.ChatDtos.ConversationDto;
import com.unkittered.api.chat.ChatDtos.MatchDto;
import com.unkittered.api.chat.ChatDtos.MessageDto;
import com.unkittered.api.chat.ChatDtos.SendMessageRequest;
import com.unkittered.api.common.ApiException;
import com.unkittered.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Matches and conversations for the current user. */
@RestController
@RequestMapping("/v1")
@Tag(name = "Chat")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @Operation(summary = "List the current user's matches")
    @GetMapping("/matches")
    public Map<String, List<MatchDto>> matches() {
        return Map.of("matches", chat.matchesFor(CurrentUser.id()));
    }

    @Operation(summary = "List the current user's conversations")
    @GetMapping("/conversations")
    public Map<String, List<ConversationDto>> conversations() {
        return Map.of("conversations", chat.conversationsFor(CurrentUser.id()));
    }

    @Operation(summary = "Get the messages in a conversation (marks them read)")
    @GetMapping("/conversations/{matchId}/messages")
    public Map<String, List<MessageDto>> messages(@PathVariable String matchId) {
        return Map.of("messages", chat.messages(CurrentUser.id(), parseId(matchId)));
    }

    @Operation(summary = "Send a message in a conversation")
    @PostMapping("/conversations/{matchId}/messages")
    public MessageDto send(@PathVariable String matchId,
                           @Valid @RequestBody SendMessageRequest req) {
        return chat.send(CurrentUser.id(), parseId(matchId), req.text());
    }

    private UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid conversation id");
        }
    }
}
