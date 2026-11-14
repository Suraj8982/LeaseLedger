package com.leaseledger.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Starts the whole application (security filter chain, controllers, JPA, Flyway on
 * in-memory H2) and drives it over HTTP, the way the React app will.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private ResultActions postJson(String url, Map<String, String> body) throws Exception {
        return mvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions register(String email, String role) throws Exception {
        return postJson("/api/auth/register", Map.of(
                "email", email, "password", "correct-horse", "fullName", "Test User", "role", role));
    }

    private String tokenFrom(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    /** A different address on every run, so the tests also pass against a database that keeps its data. */
    private static String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    @Test
    void registerThenCallProtectedEndpointWithToken() throws Exception {
        String email = uniqueEmail();
        ResultActions registered = register(email, "MANAGER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", not(emptyString())))
                .andExpect(jsonPath("$.role").value("MANAGER"));

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tokenFrom(registered)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void protectedEndpointWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithGarbageTokenIs401() throws Exception {
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sameEmailCannotRegisterTwiceEvenWithDifferentCase() throws Exception {
        String email = uniqueEmail();
        register(email, "TENANT").andExpect(status().isCreated());

        register(email.toUpperCase(), "TENANT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with this email already exists"));
    }

    @Test
    void loginSucceedsWithRightPasswordAndFailsWithWrongOne() throws Exception {
        String email = uniqueEmail();
        register(email, "TENANT").andExpect(status().isCreated());

        postJson("/api/auth/login", Map.of("email", email, "password", "correct-horse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyString())));

        postJson("/api/auth/login", Map.of("email", email, "password", "wrong-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailGetsSameErrorAsWrongPassword() throws Exception {
        postJson("/api/auth/login", Map.of("email", uniqueEmail(), "password", "whatever-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void nobodyCanSelfRegisterAsAdmin() throws Exception {
        register(uniqueEmail(), "ADMIN").andExpect(status().isBadRequest());
    }

    @Test
    void invalidInputIsRejectedWithFieldErrors() throws Exception {
        postJson("/api/auth/register", Map.of(
                "email", "not-an-email", "password", "short", "fullName", "X", "role", "TENANT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
