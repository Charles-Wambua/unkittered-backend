package com.unkittered.api.interaction;

import com.unkittered.api.chat.ChatService;
import com.unkittered.api.common.ApiException;
import com.unkittered.api.discover.DiscoverService;
import com.unkittered.api.messaging.MatchEventPublisher;
import com.unkittered.api.pet.PetRepository;
import com.unkittered.api.profile.ProfileMapper;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.push.PushService;
import com.unkittered.api.realtime.RealtimeGateway;
import com.unkittered.api.safety.BlockRepository;
import com.unkittered.api.subscription.Allowances;
import com.unkittered.api.user.User;
import com.unkittered.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TakeBackWagTest {

    final UUID me = UUID.randomUUID();
    final UUID them = UUID.randomUUID();

    LikeRepository likes;
    MatchRepository matches;
    UserRepository users;
    DiscoverService discover;
    RedisTemplate<String, Object> redis;
    ValueOperations<String, Object> values;
    InteractionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        likes = mock(LikeRepository.class);
        matches = mock(MatchRepository.class);
        users = mock(UserRepository.class);
        discover = mock(DiscoverService.class);
        redis = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new InteractionService(likes, mock(PassRepository.class), matches, users,
                mock(ProfileRepository.class), mock(ProfileMapper.class), mock(MatchEventPublisher.class),
                discover, redis, mock(RealtimeGateway.class), mock(PushService.class),
                mock(BlockRepository.class), mock(ChatService.class), mock(PetRepository.class));
        when(likes.findByLikerIdAndLikeeId(me, them)).thenReturn(Optional.empty());
    }

    void tier(String tier) {
        User u = mock(User.class);
        when(u.getSubscriptionTier()).thenReturn(tier);
        when(users.findById(me)).thenReturn(Optional.of(u));
    }

    Like liked(boolean superLike, Instant at) {
        Like like = new Like(me, them, superLike);
        ReflectionTestUtils.setField(like, "createdAt", at);
        when(likes.findByLikerIdAndLikeeId(me, them)).thenReturn(Optional.of(like));
        return like;
    }

    @Test
    void rewind_is_for_plus_and_gold() {
        assertThat(Allowances.canRewind("plus")).isTrue();
        assertThat(Allowances.canRewind("Gold")).isTrue();
        assertThat(Allowances.canRewind("free")).isFalse();
        assertThat(Allowances.canRewind(null)).isFalse();

        tier("free");
        liked(false, Instant.now());
        assertThatThrownBy(() -> service.unlike(me, them)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.getMessage()).isEqualTo("Rewind is a Plus & Gold perk.");
        });
        verify(likes, never()).delete(any());
    }

    @Test
    void a_match_cannot_be_taken_back() {
        tier("gold");
        liked(false, Instant.now());
        when(matches.existsByUserLowAndUserHigh(Match.low(me, them), Match.high(me, them))).thenReturn(true);

        assertThatThrownBy(() -> service.unlike(me, them)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getMessage()).isEqualTo("You've already matched — unmatch from the chat instead.");
        });
        verify(likes, never()).delete(any());
    }

    @Test
    void taking_back_nothing_is_fine() {
        tier("plus");
        service.unlike(me, them);
        verify(likes, never()).delete(any());
        verifyNoInteractions(discover);
    }

    @Test
    void a_wag_is_removed_and_both_decks_rebuilt() {
        tier("plus");
        Like like = liked(false, Instant.now());

        service.unlike(me, them);

        verify(likes).delete(like);
        verify(discover).evict(me);
        verify(discover).evict(them);
        verify(values, never()).decrement(anyString());
    }

    @Test
    void a_pet_note_from_this_week_goes_back_into_the_allowance() {
        tier("gold");
        Like like = liked(true, Instant.now());
        String key = Allowances.weeklySuperLikesKey(me);
        when(values.get(key)).thenReturn(3);

        service.unlike(me, them);

        verify(likes).delete(like);
        verify(values).decrement(key);
    }

    @Test
    void an_older_pet_note_or_an_empty_counter_gives_nothing_back() {
        tier("gold");
        liked(true, Instant.now().minus(Duration.ofDays(8)));
        service.unlike(me, them);

        liked(true, Instant.now());
        when(values.get(anyString())).thenReturn(null);
        service.unlike(me, them);

        verify(values, never()).decrement(anyString());
    }

    @Test
    void a_redis_failure_never_fails_the_rewind() {
        tier("gold");
        Like like = liked(true, Instant.now());
        when(values.get(anyString())).thenThrow(new IllegalStateException("redis down"));

        service.unlike(me, them);

        verify(likes).delete(like);
    }
}
