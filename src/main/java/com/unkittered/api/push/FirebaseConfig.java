package com.unkittered.api.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;

/**
 * Initialises the Firebase Admin SDK for push notifications.
 *
 * <p>Credentials are resolved from {@code unkittered.firebase.credentials}
 * (default {@code classpath:firebase-service-account.json}) — the service
 * account JSON downloaded from the Firebase console
 * (Project settings → Service accounts → Generate new private key).
 *
 * <p>If no credentials are found the app still boots; the
 * {@link FirebaseMessaging} bean is simply absent and {@link PushService}
 * degrades to a no-op (logged at startup). This keeps local dev and CI working
 * without secrets.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseMessaging firebaseMessaging(
            ResourceLoader resourceLoader,
            @Value("${unkittered.firebase.credentials:classpath:firebase-service-account.json}")
            String credentialsLocation) {
        try {
            Resource resource = resourceLoader.getResource(credentialsLocation);
            if (!resource.exists()) {
                log.warn("⚠️  Firebase credentials not found at '{}' — push notifications DISABLED. "
                        + "Drop the service-account JSON there to enable FCM.", credentialsLocation);
                return null;
            }
            try (InputStream in = resource.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(in))
                        .build();
                FirebaseApp app = FirebaseApp.getApps().isEmpty()
                        ? FirebaseApp.initializeApp(options)
                        : FirebaseApp.getInstance();
                log.info("✅ Firebase initialised — push notifications enabled.");
                return FirebaseMessaging.getInstance(app);
            }
        } catch (Exception e) {
            log.error("Failed to initialise Firebase — push notifications DISABLED: {}", e.getMessage());
            return null;
        }
    }
}
