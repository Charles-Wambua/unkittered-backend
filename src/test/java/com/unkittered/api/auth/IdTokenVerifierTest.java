package com.unkittered.api.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.unkittered.api.common.ApiException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdTokenVerifierTest {

    private static RSAKey googleKey;
    private static RSAKey strangerKey;
    private static IdTokenVerifier verifier;

    @BeforeAll
    static void keys() throws Exception {
        googleKey = new RSAKeyGenerator(2048).keyID("g1").generate();
        strangerKey = new RSAKeyGenerator(2048).keyID("g1").generate();
        verifier = new IdTokenVerifier(Map.of(
                "google", new IdTokenVerifier.ProviderConfig(
                        Set.of("https://accounts.google.com"),
                        Set.of("our-client-id"),
                        new ImmutableJWKSet<>(new JWKSet(googleKey.toPublicJWK()))),
                "apple", new IdTokenVerifier.ProviderConfig(
                        Set.of("https://appleid.apple.com"),
                        Set.of(),
                        new ImmutableJWKSet<>(new JWKSet()))));
    }

    private static String token(RSAKey key, String issuer, String audience, long expiresInSeconds) throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .subject("google-sub-123")
                .issuer(issuer)
                .audience(audience)
                .expirationTime(new Date(System.currentTimeMillis() + expiresInSeconds * 1000))
                .claim("email", "Amara@Example.com")
                .claim("email_verified", true)
                .build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    @Test
    void acceptsAValidToken() throws Exception {
        var id = verifier.verify("google", token(googleKey, "https://accounts.google.com", "our-client-id", 300));
        assertThat(id.subject()).isEqualTo("google-sub-123");
        assertThat(id.email()).isEqualTo("Amara@Example.com");
        assertThat(id.emailVerified()).isTrue();
    }

    @Test
    void rejectsAnotherAppsAudience() throws Exception {
        String t = token(googleKey, "https://accounts.google.com", "someone-else", 300);
        assertThatThrownBy(() -> verifier.verify("google", t)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsTheWrongIssuer() throws Exception {
        String t = token(googleKey, "https://evil.example", "our-client-id", 300);
        assertThatThrownBy(() -> verifier.verify("google", t)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsAForgedSignature() throws Exception {
        String t = token(strangerKey, "https://accounts.google.com", "our-client-id", 300);
        assertThatThrownBy(() -> verifier.verify("google", t)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsAnExpiredToken() throws Exception {
        String t = token(googleKey, "https://accounts.google.com", "our-client-id", -600);
        assertThatThrownBy(() -> verifier.verify("google", t)).isInstanceOf(ApiException.class);
    }

    @Test
    void unconfiguredProviderIsUnavailableNotTrusted() {
        assertThatThrownBy(() -> verifier.verify("apple", "anything"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("isn't set up yet");
    }
}
