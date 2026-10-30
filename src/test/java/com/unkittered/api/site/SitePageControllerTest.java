package com.unkittered.api.site;

import com.unkittered.api.config.SecurityConfig;
import com.unkittered.api.profile.PresenceService;
import com.unkittered.api.security.JwtAuthFilter;
import com.unkittered.api.security.JwtService;
import com.unkittered.api.storage.LocalStorageWebConfig;
import com.unkittered.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The public site is reachable without a token; the API still isn't. */
// Uploaded-files config needs a storage directory, which this test doesn't.
@WebMvcTest(controllers = SitePageController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, classes = LocalStorageWebConfig.class))
@Import({SecurityConfig.class, JwtAuthFilter.class})
class SitePageControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtService jwtService;
    @MockitoBean
    PresenceService presence;
    @MockitoBean
    UserRepository users;

    @Test
    void pagesArePublicAndForwardToTheirIndex() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/privacy")).andExpect(status().isOk()).andExpect(forwardedUrl("/privacy/index.html"));
        mvc.perform(get("/privacy/")).andExpect(status().isOk()).andExpect(forwardedUrl("/privacy/index.html"));
        mvc.perform(get("/terms")).andExpect(forwardedUrl("/terms/index.html"));
        mvc.perform(get("/guidelines")).andExpect(forwardedUrl("/guidelines/index.html"));
        mvc.perform(get("/safety")).andExpect(forwardedUrl("/safety/index.html"));
        mvc.perform(get("/delete-account")).andExpect(forwardedUrl("/delete-account/index.html"));
        mvc.perform(get("/support")).andExpect(forwardedUrl("/support/index.html"));
        mvc.perform(get("/u/abc")).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void apiStillNeedsAToken() throws Exception {
        mvc.perform(get("/v1/me")).andExpect(status().isUnauthorized());
    }
}
