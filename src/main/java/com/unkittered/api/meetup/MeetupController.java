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

    public MeetupController(MeetupService meetups) {
        this.meetups = meetups;
    }

    @Operation(summary = "List upcoming meetups")
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
    public MeetupDto join(@PathVariable String id) {
        return meetups.rsvp(parseId(id), CurrentUser.id(), true);
    }

    @Operation(summary = "Cancel an RSVP")
    @DeleteMapping("/{id}/rsvp")
    public MeetupDto leave(@PathVariable String id) {
        return meetups.rsvp(parseId(id), CurrentUser.id(), false);
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
