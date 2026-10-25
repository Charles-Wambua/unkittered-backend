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
        return chat.send(CurrentUser.id(), parseId(matchId), req);
    }

    public record RatePlanRequest(@jakarta.validation.constraints.NotBlank String rating) { }

    @Operation(summary = "My private feedback on this chat's past dates (plan id → good | bad)")
    @GetMapping("/conversations/{matchId}/plan-feedback")
    public Map<String, String> planFeedback(@PathVariable String matchId) {
        return chat.planFeedback(CurrentUser.id(), parseId(matchId));
    }

    @Operation(summary = "How was the date? (confirmed plans, once they're over; private)")
    @PutMapping("/conversations/{matchId}/plans/{planId}/feedback")
    public Map<String, String> ratePlan(@PathVariable String matchId, @PathVariable String planId,
                                        @Valid @RequestBody RatePlanRequest body) {
        return chat.ratePlan(CurrentUser.id(), parseId(matchId), planId, body.rating());
    }

    @Operation(summary = "Edit my text message (within 15 minutes of sending)")
    @PatchMapping("/conversations/{matchId}/messages/{messageId}")
    public MessageDto edit(@PathVariable String matchId, @PathVariable String messageId,
                           @Valid @RequestBody ChatDtos.EditMessageRequest body) {
        return chat.edit(CurrentUser.id(), parseId(matchId), parseId(messageId), body.text());
    }

    @Operation(summary = "React to a message (one emoji per person; sending another replaces it)")
    @PutMapping("/conversations/{matchId}/messages/{messageId}/reaction")
    public MessageDto react(@PathVariable String matchId, @PathVariable String messageId,
                            @Valid @RequestBody ChatDtos.ReactRequest body) {
        return chat.react(CurrentUser.id(), parseId(matchId), parseId(messageId), body.emoji());
    }

    @Operation(summary = "Remove my reaction from a message")
    @DeleteMapping("/conversations/{matchId}/messages/{messageId}/reaction")
    public MessageDto unreact(@PathVariable String matchId, @PathVariable String messageId) {
        return chat.react(CurrentUser.id(), parseId(matchId), parseId(messageId), null);
    }

    @Operation(summary = "Delete my message for both of us (Gold)")
    @DeleteMapping("/conversations/{matchId}/messages/{messageId}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deleteMessage(@PathVariable String matchId, @PathVariable String messageId) {
        chat.deleteMessage(CurrentUser.id(), parseId(matchId), parseId(messageId));
    }

    @Operation(summary = "Delete chat: clear this conversation's history for me (Gold); the match stays")
    @PostMapping("/conversations/{matchId}/clear")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void clear(@PathVariable String matchId) {
        chat.clearConversation(CurrentUser.id(), parseId(matchId));
    }

    @Operation(summary = "Date Concierge: ideas grounded in what you share (Gold sees all three)")
    @PostMapping("/conversations/{matchId}/date-plans")
    public ChatService.ConciergeResult datePlans(@PathVariable String matchId) {
        return chat.datePlans(CurrentUser.id(), parseId(matchId));
    }

    private UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid conversation id");
        }
    }
}
