package com.unkittered.api.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the static admin dashboard at /admin and /admin/ — Spring only
 * auto-resolves index.html for the root path, so we forward the folder URL to
 * the actual file (otherwise it 404s/500s).
 */
@Controller
public class AdminPageController {

    @GetMapping({"/admin", "/admin/"})
    public String dashboard() {
        return "forward:/admin/index.html";
    }
}
