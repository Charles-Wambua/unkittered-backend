package com.unkittered.api.site;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the public website (landing, legal, safety and support pages) from
 * static/, built by site/build.py. Maps clean URLs like /privacy to their
 * folder's index.html. The stores link to these pages, so they're public.
 */
@Controller
public class SitePageController {

    /** Public paths, also listed in SecurityConfig. */
    public static final String[] PUBLIC_PATHS = {
            "/", "/index.html", "/site/**", "/favicon.ico", "/robots.txt",
            "/privacy", "/privacy/**", "/terms", "/terms/**",
            "/guidelines", "/guidelines/**", "/safety", "/safety/**",
            "/delete-account", "/delete-account/**", "/support", "/support/**",
            "/u/**",
    };

    @GetMapping("/")
    public String home() {
        return "forward:/index.html";
    }

    // Shared profile links (unkittered.app/u/<id>) land on the home page for now.
    @GetMapping("/u/{id}")
    public String profileLink() {
        return "forward:/index.html";
    }

    @GetMapping({"/privacy", "/privacy/"})
    public String privacy() {
        return "forward:/privacy/index.html";
    }

    @GetMapping({"/terms", "/terms/"})
    public String terms() {
        return "forward:/terms/index.html";
    }

    @GetMapping({"/guidelines", "/guidelines/"})
    public String guidelines() {
        return "forward:/guidelines/index.html";
    }

    @GetMapping({"/safety", "/safety/"})
    public String safety() {
        return "forward:/safety/index.html";
    }

    @GetMapping({"/delete-account", "/delete-account/"})
    public String deleteAccount() {
        return "forward:/delete-account/index.html";
    }

    @GetMapping({"/support", "/support/"})
    public String support() {
        return "forward:/support/index.html";
    }

    @GetMapping("/favicon.ico")
    public String favicon() {
        return "forward:/site/img/favicon.png";
    }
}
