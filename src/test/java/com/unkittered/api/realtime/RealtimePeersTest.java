package com.unkittered.api.realtime;

import com.unkittered.api.interaction.Match;
import com.unkittered.api.interaction.MatchRepository;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RealtimePeersTest {

    final UUID nia = UUID.randomUUID();
    final UUID daniel = UUID.randomUUID();
    final UUID matchId = UUID.randomUUID();

    MatchRepository matches;
    UserRepository users;
    ProfileRepository profiles;
    RealtimePeers peers;

    @BeforeEach
    void setUp() {
        matches = mock(MatchRepository.class);
        users = mock(UserRepository.class);
        profiles = mock(ProfileRepository.class);
        peers = new RealtimePeers(matches, users, profiles);
        when(matches.findById(matchId)).thenReturn(Optional.of(Match.of(nia, daniel)));
        when(users.existsByIdAndSuspendedFalse(daniel)).thenReturn(true);
    }

    void showsActivity(boolean shown) {
        Profile p = mock(Profile.class);
        when(p.isShowActivity()).thenReturn(shown);
        when(profiles.findById(nia)).thenReturn(Optional.of(p));
    }

    @Test
    void a_member_reaches_the_other_member() {
        assertThat(peers.otherMember(matchId, nia)).contains(daniel);
    }

    @Test
    void outsiders_unknown_chats_and_suspended_partners_reach_nobody() {
        assertThat(peers.otherMember(matchId, UUID.randomUUID())).isEmpty();
        assertThat(peers.otherMember(UUID.randomUUID(), nia)).isEmpty();
        when(users.existsByIdAndSuspendedFalse(daniel)).thenReturn(false);
        assertThat(peers.otherMember(matchId, nia)).isEmpty();
    }

    @Test
    void presence_goes_to_matches_when_activity_is_shown() {
        showsActivity(true);
        when(matches.findAllForUser(nia)).thenReturn(List.of(Match.of(nia, daniel)));
        assertThat(peers.presenceAudience(nia)).containsExactly(daniel);
    }

    @Test
    void presence_goes_to_nobody_when_activity_is_hidden() {
        showsActivity(false);
        assertThat(peers.presenceAudience(nia)).isEmpty();
        verify(matches, never()).findAllForUser(nia);
    }
}
