package com.unkittered.api.chat;

import com.unkittered.api.chat.MessageModeration.Flag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageModerationTest {

    @Test
    void phoneNumbersEmailsAndHandlesAreContactInfo() {
        assertThat(MessageModeration.check("call me on 0712 345 678", false)).isEqualTo(Flag.CONTACT_INFO);
        assertThat(MessageModeration.check("mail me: amara@example.com", false)).isEqualTo(Flag.CONTACT_INFO);
        assertThat(MessageModeration.check("follow @karura_walks", false)).isEqualTo(Flag.CONTACT_INFO);
        assertThat(MessageModeration.check("add me on insta", false)).isEqualTo(Flag.CONTACT_INFO);
        assertThat(MessageModeration.check("text me on whatsapp", false)).isEqualTo(Flag.CONTACT_INFO);
        assertThat(MessageModeration.check("wa.me/254712", false)).isEqualTo(Flag.CONTACT_INFO);
    }

    @Test
    void everydayMessagesAreFine() {
        assertThat(MessageModeration.check("Meet @ 5pm by Gate C", false)).isEqualTo(Flag.CLEAN);
        assertThat(MessageModeration.check("See you at 10:30, bringing 2 dogs", false)).isEqualTo(Flag.CLEAN);
        assertThat(MessageModeration.check("Snapping photos of the sunset", false)).isEqualTo(Flag.CLEAN);
    }

    @Test
    void goldMayShareContactsOneToOne() {
        assertThat(MessageModeration.check("0712 345 678", true)).isEqualTo(Flag.CLEAN);
        assertThat(MessageModeration.reasonInGroup(Flag.CONTACT_INFO)).contains("can't be shared here");
    }
}
