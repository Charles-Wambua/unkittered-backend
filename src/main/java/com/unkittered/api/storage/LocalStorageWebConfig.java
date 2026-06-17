package com.unkittered.api.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/** Serves locally-stored files at {@code /files/**} when the local provider is active. */
@Configuration
@ConditionalOnProperty(name = "unkittered.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageWebConfig implements WebMvcConfigurer {

    private final Path root;

    public LocalStorageWebConfig(LocalStorageService storage) {
        this.root = storage.root();
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler(LocalStorageService.URL_PREFIX + "**")
                .addResourceLocations(root.toUri().toString());
    }
}
