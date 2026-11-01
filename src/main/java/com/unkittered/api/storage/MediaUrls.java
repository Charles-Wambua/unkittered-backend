package com.unkittered.api.storage;

import com.unkittered.api.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Which photo links members may save on their profile, pets and stories: only
 * files this server stored (uploaded through the app). Anything else could
 * point at a tracking pixel or, worse, carry markup into the admin dashboard.
 *
 * <p>A link the member already has is kept as-is, so older profiles whose
 * photos came from elsewhere (seed data, social sign-in) can still be edited.
 */
@Component
public class MediaUrls {

    /** Our storage hands out {@code /files/<random hex>.<ext>}, nothing fancier. */
    private static final Pattern FILE_PATH = Pattern.compile("/files/[A-Za-z0-9_-]+\\.[A-Za-z0-9]{1,5}");

    private final String base;

    public MediaUrls(@Value("${unkittered.storage.public-base-url:}") String publicBaseUrl) {
        this.base = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    /** True when {@code url} is a file in our own storage. */
    public boolean isOwn(String url) {
        if (url == null) return false;
        String path = !base.isEmpty() && url.startsWith(base) ? url.substring(base.length()) : url;
        return FILE_PATH.matcher(path).matches();
    }

    /**
     * Returns {@code url} if it may be saved: blank (no photo), one of ours, or
     * one the member {@code alreadyHas}. Otherwise refuses the request.
     */
    public String require(String url, Collection<String> alreadyHas) {
        if (url == null || url.isBlank()) return url;
        String u = url.trim();
        if (isOwn(u) || (alreadyHas != null && alreadyHas.contains(u))) return u;
        throw ApiException.badRequest("Photos need to be uploaded through the app");
    }
}
