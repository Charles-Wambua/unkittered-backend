package com.unkittered.api.meetup;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.meetup.MeetupDtos.CreateMeetupRequest;
import com.unkittered.api.meetup.MeetupDtos.MeetupDto;
import com.unkittered.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Meetups: browse the boards, host one, and RSVP. */
@RestController
@RequestMapping("/v1/meetups")
@Tag(name = "Meetups")
@SecurityRequirement(name = "bearerAuth")
public class MeetupController {

    private final MeetupService meetups;
    private final PackChatService chat;

    public MeetupController(MeetupService meetups, PackChatService chat) {
        this.meetups = meetups;
        this.chat = chat;
    }

    @Operation(summary = "Packs the signed-in user went to that have ended (last 90 days)")
    @GetMapping("/past")
    public Map<String, List<MeetupDto>> past() {
        return Map.of("meetups", meetups.pastFor(CurrentUser.id()));
    }

    @Operation(summary = "One meetup, in any state (upcoming, live or ended)")
    @GetMapping("/{id}")
    public MeetupDto one(@PathVariable String id) {
        return meetups.one(parseId(id), CurrentUser.id());
    }

    @Operation(summary = "Another member's profile, to open from the pack chat (both must be going)")
    @GetMapping("/{id}/members/{userId}/profile")
    public com.unkittered.api.profile.ProfileDto memberProfile(@PathVariable String id, @PathVariable String userId) {
        return meetups.memberProfile(parseId(id), CurrentUser.id(), parseId(userId));
    }

    @Operation(summary = "Host: set (or clear, with empty text) where to find the group")
    @PutMapping("/{id}/meeting-point")
    public PackChatService.ThreadDto meetingPoint(@PathVariable String id,
                                                  @Valid @RequestBody PackChatService.MeetingPointRequest body) {
        return chat.setMeetingPoint(parseId(id), CurrentUser.id(), body);
    }

    @Operation(summary = "Pack chat: \"I'm here\" check-in (from an hour before until it ends)")
    @PostMapping("/{id}/arrived")
    public PackChatService.PackMessageDto arrived(@PathVariable String id) {
        return chat.arrived(parseId(id), CurrentUser.id());
    }

    @Operation(summary = "Pack chat: delete a message (your own on Gold; the host can delete any)")
    @DeleteMapping("/{id}/messages/{messageId}")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deleteMessage(@PathVariable String id, @PathVariable String messageId) {
        chat.delete(parseId(id), CurrentUser.id(), parseId(messageId));
    }

    @Operation(summary = "Pack chat: messages (members only); marks them read")
    @GetMapping("/{id}/messages")
    public PackChatService.ThreadDto messages(@PathVariable String id) {
        return chat.thread(parseId(id), CurrentUser.id());
    }

    @Operation(summary = "Pack chat: post a message (members, while the chat is open)")
    @PostMapping("/{id}/messages")
    public PackChatService.PackMessageDto send(@PathVariable String id,
                                               @Valid @RequestBody PackChatService.SendRequest body) {
        return chat.send(parseId(id), CurrentUser.id(), body);
    }

    @Operation(summary = "List packs that haven't ended yet, soonest first")
    @GetMapping
    public Map<String, List<MeetupDto>> list() {
        return Map.of("meetups", meetups.feedFor(CurrentUser.id()));
    }

    @Operation(summary = "Host a new meetup")
    @PostMapping
    public MeetupDto create(@Valid @RequestBody CreateMeetupRequest req) {
        return meetups.create(CurrentUser.id(), req);
    }

    @Operation(summary = "RSVP to a meetup")
    @PostMapping("/{id}/rsvp")
    public MeetupDto join(@PathVariable String id,
                          @RequestBody(required = false) MeetupDtos.RsvpRequest body) {
        boolean withPet = body != null && body.bringingPet();
        return meetups.rsvp(parseId(id), CurrentUser.id(), true, withPet);
    }

    @Operation(summary = "Cancel an RSVP")
    @DeleteMapping("/{id}/rsvp")
    public MeetupDto leave(@PathVariable String id) {
        return meetups.rsvp(parseId(id), CurrentUser.id(), false, false);
    }

    @Operation(summary = "Host-only: cancel (delete) a meetup")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        meetups.delete(parseId(id), CurrentUser.id());
    }

    @Operation(summary = "Report a meetup for moderation")
    @PostMapping("/{id}/report")
    public void report(@PathVariable String id,
                       @RequestBody(required = false) Map<String, String> body) {
        final String reason = body == null ? null : body.get("reason");
        meetups.report(parseId(id), CurrentUser.id(), reason);
    }

    private UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid meetup id");
        }
    }
}
