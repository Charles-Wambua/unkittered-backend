package com.unkittered.api.site;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Android App Links: lets shared profile links (/u/&lt;id&gt;) open straight in
 * the app. Android fetches this file to check that the app (by package and
 * signing certificate) really belongs to this site.
 *
 * Set {@code UNKITTERED_ANDROID_CERT_SHA256} to the SHA-256 fingerprint(s) of
 * the key(s) the APK is signed with, comma-separated (and add the Play app
 * signing key's once the app is on Play). With none set the list is empty
 * and links simply keep opening in the browser.
 */
@RestController
public class AppLinksController {

    private final String packageName;
    private final List<String> fingerprints;

    public AppLinksController(
            @Value("${unkittered.android.package:com.unkittered.unkittered}") String packageName,
            @Value("${unkittered.android.cert-sha256:}") String fingerprints) {
        this.packageName = packageName;
        this.fingerprints = Arrays.stream(fingerprints.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> assetLinks() {
        if (fingerprints.isEmpty()) return List.of();
        return List.of(Map.of(
                "relation", List.of("delegate_permission/common.handle_all_urls"),
                "target", Map.of(
                        "namespace", "android_app",
                        "package_name", packageName,
                        "sha256_cert_fingerprints", fingerprints)));
    }
}
