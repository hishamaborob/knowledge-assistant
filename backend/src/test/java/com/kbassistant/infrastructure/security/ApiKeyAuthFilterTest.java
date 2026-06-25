package com.kbassistant.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;

class ApiKeyAuthFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // --- Dev mode (no keys configured) ---

    @Test
    void devMode_noKeysConfigured_chainIsCalled() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void devMode_setsDevAuthenticationInSecurityContext() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("");

        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("dev");
    }

    // --- Valid key ---

    @Test
    void validKey_chainIsCalled() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("key-one,key-two");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "key-one");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void validKey_secondKeyInList_chainIsCalled() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("key-one,key-two");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "key-two");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void validKey_setsAuthenticationInSecurityContext() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("my-secret-key");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "my-secret-key");

        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("my-secret-key");
    }

    // --- Invalid / missing key ---

    @Test
    void invalidKey_returns401AndChainNotCalled() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("real-key");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void missingHeader_returns401AndChainNotCalled() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("real-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void invalidKey_responseBodyIsProblemJson() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("real-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(response.getContentAsString()).contains("X-API-Key");
        assertThat(response.getContentAsString()).contains("401");
    }

    // --- shouldNotFilter path bypass ---

    @Test
    void shouldNotFilter_actuatorSubpath_returnsTrue() {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("any-key");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/actuator/health");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_swaggerUiSubpath_returnsTrue() {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("any-key");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/swagger-ui/index.html");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_swaggerUiHtml_returnsTrue() {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("any-key");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/swagger-ui.html");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_apiDocsPath_returnsTrue() {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("any-key");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/v3/api-docs");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_documentsEndpoint_returnsFalse() {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("any-key");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/documents");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void shouldNotFilter_queriesEndpoint_returnsFalse() {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("any-key");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/queries");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
