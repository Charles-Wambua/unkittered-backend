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
