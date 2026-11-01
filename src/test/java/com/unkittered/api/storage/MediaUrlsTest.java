package com.unkittered.api.storage;

import com.unkittered.api.common.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaUrlsTest {

    final MediaUrls media = new MediaUrls("https://api.example.com/");

    @Test
    void acceptsFilesThisServerStored() {
        assertThat(media.isOwn("https://api.example.com/files/0123abcd.jpg")).isTrue();
        assertThat(media.isOwn("/files/0123abcd.webp")).isTrue();
    }

    @Test
    void refusesEverythingElse() {
        assertThat(media.isOwn("https://evil.example/files/a.jpg")).isFalse();
        assertThat(media.isOwn("/files/../secret.jpg")).isFalse();
        assertThat(media.isOwn("/files/private/abc.jpg")).isFalse();
        assertThat(media.isOwn("/files/a.jpg\" onerror=\"alert(1)")).isFalse();
        assertThat(media.isOwn("javascript:alert(1)")).isFalse();
        assertThat(media.isOwn(null)).isFalse();
        assertThatThrownBy(() -> media.require("https://evil.example/x.jpg", List.of()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void keepsWhatTheMemberAlreadyHadAndAllowsRemovingAPhoto() {
        String old = "https://images.example/seeded.jpg";
        assertThat(media.require(old, List.of(old))).isEqualTo(old);
        assertThat(media.require("", List.of())).isEmpty();
        assertThat(media.require(null, null)).isNull();
    }
}
