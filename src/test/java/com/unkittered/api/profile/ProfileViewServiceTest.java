package com.unkittered.api.profile;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.discover.DiscoverService;
import com.unkittered.api.interaction.LikeRepository;
import com.unkittered.api.interaction.Match;
import com.unkittered.api.interaction.MatchRepository;
import com.unkittered.api.safety.BlockRepository;
import com.unkittered.api.user.UserRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProfileViewServiceTest {

    final UUID me = UUID.randomUUID();
    final UUID them = UUID.randomUUID();

    ProfileRepository profiles;
    UserRepository users;
    BlockRepository blocks;
    MatchRepository matches;
    LikeRepository likes;
    DiscoverService discover;
    ProfileViewService service;
    Profile theirs;
    ProfileDto card;

    @BeforeEach
    void setUp() {
        profiles = mock(ProfileRepository.class);
        users = mock(UserRepository.class);
        blocks = mock(BlockRepository.class);
        matches = mock(MatchRepository.class);
        likes = mock(LikeRepository.class);
        discover = mock(DiscoverService.class);
        service = new ProfileViewService(profiles, users, blocks, matches, likes, discover);

        theirs = mock(Profile.class);
        when(theirs.getUserId()).thenReturn(them);
        when(profiles.findById(them)).thenReturn(Optional.of(theirs));
        when(users.existsByIdAndSuspendedFalse(them)).thenReturn(true);
        card = mock(ProfileDto.class);
        when(discover.viewOf(me, theirs)).thenReturn(card);
    }

    void matched() {
        when(matches.existsByUserLowAndUserHigh(Match.low(me, them), Match.high(me, them))).thenReturn(true);
    }

    static void assertNotFound(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(e.getMessage()).isEqualTo("We couldn't find that profile.");
        });
    }

    @Test
    void a_visible_profile_is_the_card_the_deck_would_show() {
        assertThat(service.view(me, them.toString())).isSameAs(card);
    }

    @Test
    void bad_and_unknown_ids_are_not_found() {
        assertNotFound(() -> service.view(me, "not-a-uuid"));
        assertNotFound(() -> service.view(me, UUID.randomUUID().toString()));
    }

    @Test
    void suspended_members_are_not_found() {
        when(users.existsByIdAndSuspendedFalse(them)).thenReturn(false);
        assertNotFound(() -> service.view(me, them.toString()));
    }

    @Test
    void a_block_either_way_is_not_found() {
        when(blocks.existsBetween(me, them)).thenReturn(true);
        assertNotFound(() -> service.view(me, them.toString()));
    }

    @Test
    void paused_members_are_only_open_to_their_matches() {
        when(theirs.isPaused()).thenReturn(true);
        when(likes.existsByLikerIdAndLikeeId(them, me)).thenReturn(true);
        assertNotFound(() -> service.view(me, them.toString()));

        matched();
        assertThat(service.view(me, them.toString())).isSameAs(card);
    }

    @Test
    void incognito_members_are_open_to_matches_and_to_people_they_wagged_at() {
        when(theirs.isIncognito()).thenReturn(true);
        assertNotFound(() -> service.view(me, them.toString()));

        when(likes.existsByLikerIdAndLikeeId(them, me)).thenReturn(true);
        assertThat(service.view(me, them.toString())).isSameAs(card);
    }

    @Test
    void you_can_always_open_your_own_profile() {
        Profile mine = mock(Profile.class);
        when(mine.isPaused()).thenReturn(true);
        when(profiles.findById(me)).thenReturn(Optional.of(mine));
        when(discover.viewOf(me, mine)).thenReturn(card);

        assertThat(service.view(me, me.toString())).isSameAs(card);
        verifyNoInteractions(blocks, users);
    }
}
