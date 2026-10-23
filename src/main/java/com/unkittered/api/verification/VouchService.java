package com.unkittered.api.verification;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * "Get vouched for". A member shares a short code; a verified member who
 * knows them enters it and confirms. Codes are single-use and expire after a
 * week, and only identity-verified members can vouch, so the badge means a
 * real, checked person stands behind it.
 */
@Service
public class VouchService {

    /** No 0/O or 1/I/L, so codes survive being read out or retyped. */
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 8;
    static final Duration LIFETIME = Duration.ofDays(7);

    public record Invite(String code, Instant expiresAt) { }

    /** Who a code belongs to, shown before the voucher confirms. */
    public record Preview(String name, String imageUrl) { }

    private final VouchInviteRepository invites;
    private final VouchRepository vouches;
    private final ProfileRepository profiles;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public VouchService(VouchInviteRepository invites, VouchRepository vouches,
                        ProfileRepository profiles, Clock clock) {
        this.invites = invites;
        this.vouches = vouches;
        this.profiles = profiles;
        this.clock = clock;
    }

    /** 'verified' once someone has vouched, otherwise 'none'. */
    public String statusFor(UUID userId) {
        return vouches.existsByUserId(userId) ? "verified" : "none";
    }

    /** The member's open code, or a fresh one. */
    @Transactional
    public Invite invite(UUID inviterId) {
        Instant now = clock.instant();
        var open = invites.findOpen(inviterId, now);
        if (!open.isEmpty()) return new Invite(open.get(0).getCode(), open.get(0).getExpiresAt());
        var created = invites.save(new VouchInvite(newCode(), inviterId, now, now.plus(LIFETIME)));
        return new Invite(created.getCode(), created.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID viewerId, String rawCode) {
        VouchInvite invite = usable(rawCode);
        if (invite.getInviterId().equals(viewerId)) {
            throw ApiException.badRequest("That's your own code. Send it to a verified friend.");
        }
        Profile p = profiles.findById(invite.getInviterId())
                .orElseThrow(() -> ApiException.notFound("That code isn't valid any more."));
        return new Preview(p.getName(), p.getImageUrl());
    }

    @Transactional
    public Preview accept(UUID voucherId, String rawCode) {
        Profile voucher = profiles.findById(voucherId)
                .orElseThrow(() -> ApiException.notFound("Profile not found"));
        if (!voucher.isVerified()) {
            throw ApiException.forbidden("Only verified members can vouch. Get your selfie check done first.");
        }
        VouchInvite invite = invites.findForUpdate(normalise(rawCode))
                .filter(i -> i.usableAt(clock.instant()))
                .orElseThrow(() -> ApiException.notFound("That code isn't valid any more."));
        UUID inviterId = invite.getInviterId();
        if (inviterId.equals(voucherId)) {
            throw ApiException.badRequest("You can't vouch for yourself.");
        }
        Instant now = clock.instant();
        if (!vouches.existsByUserIdAndVoucherId(inviterId, voucherId)) {
            vouches.save(new Vouch(inviterId, voucherId, now));
        }
        invite.markUsed(voucherId, now);
        invites.save(invite);
        Profile p = profiles.findById(inviterId).orElseThrow();
        return new Preview(p.getName(), p.getImageUrl());
    }

    private VouchInvite usable(String rawCode) {
        return invites.findById(normalise(rawCode))
                .filter(i -> i.usableAt(clock.instant()))
                .orElseThrow(() -> ApiException.notFound("That code isn't valid any more."));
    }

    /** Case and spaces/dashes don't matter when typing a code. */
    static String normalise(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private String newCode() {
        while (true) {
            var sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            String code = sb.toString();
            if (!invites.existsById(code)) return code;
        }
    }
}
