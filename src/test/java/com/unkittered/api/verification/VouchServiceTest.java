package com.unkittered.api.verification;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VouchServiceTest {

    static final Instant NOW = Instant.parse("2026-10-23T09:00:00Z");
    final UUID nia = UUID.randomUUID();
    final UUID daniel = UUID.randomUUID();

    VouchInviteRepository invites;
    VouchRepository vouches;
    ProfileRepository profiles;
    VouchService service;

    @BeforeEach
    void setUp() {
        invites = mock(VouchInviteRepository.class);
        vouches = mock(VouchRepository.class);
        profiles = mock(ProfileRepository.class);
        when(invites.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new VouchService(invites, vouches, profiles, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    Profile profile(boolean verified) {
        Profile p = mock(Profile.class);
        when(p.isVerified()).thenReturn(verified);
        when(p.getName()).thenReturn("Nia");
        return p;
    }

    @Test
    void codes_are_short_unambiguous_and_last_a_week() {
        when(invites.findOpen(nia, NOW)).thenReturn(List.of());
        var invite = service.invite(nia);
        assertThat(invite.code()).hasSize(VouchService.CODE_LENGTH)
                .matches("[" + VouchService.ALPHABET + "]+");
        assertThat(invite.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void an_open_code_is_handed_out_again() {
        var open = new VouchInvite("ABCD2345", nia, NOW, NOW.plus(Duration.ofDays(3)));
        when(invites.findOpen(nia, NOW)).thenReturn(List.of(open));
        assertThat(service.invite(nia).code()).isEqualTo("ABCD2345");
        verify(invites, never()).save(any());
    }

    @Test
    void only_verified_members_can_vouch() {
        Profile danielProfileFalse = profile(false);
        when(profiles.findById(daniel)).thenReturn(Optional.of(danielProfileFalse));
        assertThatThrownBy(() -> service.accept(daniel, "ABCD2345"))
                .isInstanceOf(ApiException.class).hasMessageContaining("Only verified");
        verify(vouches, never()).save(any());
    }

    @Test
    void a_verified_friend_vouches_once_and_the_code_is_spent() {
        var invite = new VouchInvite("ABCD2345", nia, NOW, NOW.plus(Duration.ofDays(7)));
        Profile niaProfile = profile(false);
        Profile danielProfileTrue = profile(true);
        when(profiles.findById(daniel)).thenReturn(Optional.of(danielProfileTrue));
        when(profiles.findById(nia)).thenReturn(Optional.of(niaProfile));
        when(invites.findForUpdate("ABCD2345")).thenReturn(Optional.of(invite));

        // Typed loosely: lower case with a dash still works.
        assertThat(service.accept(daniel, "abcd-2345").name()).isEqualTo("Nia");
        verify(vouches).save(any(Vouch.class));
        assertThat(invite.usableAt(NOW)).isFalse();
    }

    @Test
    void nobody_can_vouch_for_themselves() {
        var invite = new VouchInvite("ABCD2345", daniel, NOW, NOW.plus(Duration.ofDays(7)));
        Profile danielProfileTrue = profile(true);
        when(profiles.findById(daniel)).thenReturn(Optional.of(danielProfileTrue));
        when(invites.findForUpdate("ABCD2345")).thenReturn(Optional.of(invite));
        assertThatThrownBy(() -> service.accept(daniel, "ABCD2345"))
                .isInstanceOf(ApiException.class).hasMessageContaining("yourself");
    }

    @Test
    void expired_codes_are_refused() {
        var old = new VouchInvite("ABCD2345", nia, NOW.minus(Duration.ofDays(8)), NOW.minus(Duration.ofDays(1)));
        Profile danielProfileTrue = profile(true);
        when(profiles.findById(daniel)).thenReturn(Optional.of(danielProfileTrue));
        when(invites.findForUpdate("ABCD2345")).thenReturn(Optional.of(old));
        assertThatThrownBy(() -> service.accept(daniel, "ABCD2345"))
                .isInstanceOf(ApiException.class).hasMessageContaining("isn't valid");
    }
}
