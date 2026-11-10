package com.unkittered.api.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfficialWelcomeTest {

    @Test
    void welcomesByFirstNameAndSignsOffAsShenron() {
        String text = OfficialChatService.welcomeText("Amara");
        assertThat(text).startsWith("Welcome to Unkittered, Amara! 🐾");
        assertThat(text).contains("Message us here any time").contains("Verified ✓").endsWith("Shenron, Chief Wag Officer");
    }

    @Test
    void stillWelcomesWithoutAName() {
        assertThat(OfficialChatService.welcomeText(" ")).startsWith("Welcome to Unkittered! 🐾");
        assertThat(OfficialChatService.welcomeText(null)).startsWith("Welcome to Unkittered! 🐾");
    }
}
