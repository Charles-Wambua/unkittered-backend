package com.unkittered.api.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.unkittered.api.common.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verifies Google / Apple ID tokens: RS256 signature against the provider's
 * published keys, issuer, audience (our client ids) and expiry. A provider
 * with no client ids configured is reported as unavailable rather than
 * trusted.
 */
@Component
public class IdTokenVerifier {

    /** What we take from a verified token. */
    public record VerifiedIdentity(String provider, String subject, String email, boolean emailVerified) { }

    record ProviderConfig(Set<String> issuers, Set<String> audiences, JWKSource<SecurityContext> keys) { }

    private final Map<String, ProviderConfig> providers;

    @Autowired
    public IdTokenVerifier(@Value("${unkittered.oauth.google.client-ids:}") String googleClientIds,
                           @Value("${unkittered.oauth.apple.client-ids:}") String appleClientIds) {
        this(Map.of(
                "google", new ProviderConfig(
                        Set.of("https://accounts.google.com", "accounts.google.com"),
                        csv(googleClientIds),
                        remote("https://www.googleapis.com/oauth2/v3/certs")),
                "apple", new ProviderConfig(
                        Set.of("https://appleid.apple.com"),
                        csv(appleClientIds),
                        remote("https://appleid.apple.com/auth/keys"))));
    }

    /** For tests: inject providers with local keys. */
    IdTokenVerifier(Map<String, ProviderConfig> providers) {
        this.providers = providers;
    }

    public VerifiedIdentity verify(String provider, String idToken) {
        ProviderConfig config = providers.get(provider);
        if (config == null) throw ApiException.badRequest("Unknown sign-in provider");
        if (config.audiences().isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    capitalise(provider) + " sign-in isn't set up yet. Use email for now.");
        }

        try {
            var processor = new DefaultJWTProcessor<SecurityContext>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, config.keys()));
            processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                    null, new JWTClaimsSet.Builder().build(), Set.of("sub", "iss", "aud", "exp")));
            JWTClaimsSet claims = processor.process(idToken, null);

            if (!config.issuers().contains(claims.getIssuer())) throw invalid();
            List<String> aud = claims.getAudience();
            if (aud == null || aud.stream().noneMatch(config.audiences()::contains)) throw invalid();

            Object verified = claims.getClaim("email_verified");
            boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
            return new VerifiedIdentity(provider, claims.getSubject(), claims.getStringClaim("email"), emailVerified);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return ApiException.unauthorized("Sign-in couldn't be verified. Please try again.");
    }

    private static Set<String> csv(String raw) {
        Set<String> out = new HashSet<>();
        Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(out::add);
        return out;
    }

    private static JWKSource<SecurityContext> remote(String url) {
        try {
            return JWKSourceBuilder.create(URI.create(url).toURL()).build();
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
