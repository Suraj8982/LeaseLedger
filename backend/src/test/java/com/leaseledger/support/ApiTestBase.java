package com.leaseledger.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Shared setup and helpers for tests that drive the API over HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public abstract class ApiTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    public record Account(String email, String token) {
    }

    /** Registers a fresh user with a unique email, so tests never interfere with each other. */
    protected Account register(String role) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String body = json.writeValueAsString(Map.of(
                "email", email, "password", "correct-horse", "fullName", "Test " + role, "role", role));
        String response = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Account(email, json.readTree(response).get("accessToken").asText());
    }

    protected ResultActions send(MockHttpServletRequestBuilder request, Account as, Object body) throws Exception {
        if (as != null) {
            request.header("Authorization", "Bearer " + as.token());
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    protected JsonNode bodyOf(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Creates a property with one vacant unit and returns the unit's id. */
    protected long createUnit(Account manager, String monthlyRent) throws Exception {
        long propertyId = bodyOf(send(post("/api/properties"), manager,
                Map.of("name", "Marina Heights", "address", "12 Al Wasl Road", "city", "Dubai"))
                .andExpect(status().isCreated())).get("id").asLong();
        return bodyOf(send(post("/api/properties/" + propertyId + "/units"), manager,
                Map.of("unitNumber", "101", "bedrooms", 2, "monthlyRent", monthlyRent))
                .andExpect(status().isCreated())).get("id").asLong();
    }
}
