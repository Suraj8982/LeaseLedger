package com.leaseledger.property;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PropertyFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    /** Registers a fresh user (unique email, so tests do not interfere) and returns their token. */
    private String tokenFor(String role) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "email", UUID.randomUUID() + "@example.com",
                "password", "correct-horse", "fullName", "Test " + role, "role", role));
        String response = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("accessToken").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private long idOf(ResultActions result) throws Exception {
        JsonNode node = json.readTree(result.andReturn().getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private static Map<String, Object> property(String name) {
        return Map.of("name", name, "address", "12 Al Wasl Road", "city", "Dubai");
    }

    private static Map<String, Object> unit(String number, String rent) {
        return Map.of("unitNumber", number, "bedrooms", 2, "monthlyRent", rent);
    }

    private long createProperty(String token, String name) throws Exception {
        return idOf(send(post("/api/properties"), token, property(name)).andExpect(status().isCreated()));
    }

    @Test
    void managerCreatesPropertyWithUnitsAndListShowsCounts() throws Exception {
        String manager = tokenFor("MANAGER");
        long propertyId = createProperty(manager, "Marina Heights");
        createProperty(manager, "Empty Building");

        send(post("/api/properties/" + propertyId + "/units"), manager, unit("101", "8500.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("VACANT"))
                .andExpect(jsonPath("$.monthlyRent").value(8500.00));
        send(post("/api/properties/" + propertyId + "/units"), manager,
                Map.of("unitNumber", "102", "bedrooms", 1, "monthlyRent", "6000", "status", "UNAVAILABLE"))
                .andExpect(status().isCreated());

        send(get("/api/properties"), manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                // Ordered by name: "Empty Building" then "Marina Heights".
                .andExpect(jsonPath("$.items[0].name").value("Empty Building"))
                .andExpect(jsonPath("$.items[0].unitCount").value(0))
                .andExpect(jsonPath("$.items[0].vacantUnitCount").value(0))
                .andExpect(jsonPath("$.items[1].name").value("Marina Heights"))
                .andExpect(jsonPath("$.items[1].unitCount").value(2))
                .andExpect(jsonPath("$.items[1].vacantUnitCount").value(1));

        send(get("/api/properties/" + propertyId + "/units"), manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].unitNumber").value("101"));
    }

    @Test
    void listIsPaginated() throws Exception {
        String manager = tokenFor("MANAGER");
        for (String name : new String[] {"A", "B", "C"}) {
            createProperty(manager, name);
        }

        send(get("/api/properties?page=1&size=2"), manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("C"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void tenantIsForbiddenAndAnonymousIsUnauthorized() throws Exception {
        send(post("/api/properties"), tokenFor("TENANT"), property("Nope")).andExpect(status().isForbidden());
        send(get("/api/properties"), tokenFor("TENANT"), null).andExpect(status().isForbidden());
        send(get("/api/properties"), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void managerCannotSeeOrChangeAnotherManagersProperty() throws Exception {
        String owner = tokenFor("MANAGER");
        String other = tokenFor("MANAGER");
        long propertyId = createProperty(owner, "Owner's Tower");
        long unitId = idOf(send(post("/api/properties/" + propertyId + "/units"), owner, unit("1", "5000")));

        send(get("/api/properties/" + propertyId), other, null).andExpect(status().isNotFound());
        send(put("/api/properties/" + propertyId), other, property("Hijacked")).andExpect(status().isNotFound());
        send(delete("/api/properties/" + propertyId), other, null).andExpect(status().isNotFound());
        send(get("/api/properties/" + propertyId + "/units"), other, null).andExpect(status().isNotFound());
        send(post("/api/properties/" + propertyId + "/units"), other, unit("2", "5000"))
                .andExpect(status().isNotFound());
        send(delete("/api/properties/" + propertyId + "/units/" + unitId), other, null)
                .andExpect(status().isNotFound());
        send(get("/api/properties"), other, null).andExpect(jsonPath("$.totalItems").value(0));

        // And nothing was changed.
        send(get("/api/properties/" + propertyId), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Owner's Tower"));
    }

    @Test
    void unitCannotBeReachedThroughADifferentProperty() throws Exception {
        String manager = tokenFor("MANAGER");
        long first = createProperty(manager, "First");
        long second = createProperty(manager, "Second");
        long unitInFirst = idOf(send(post("/api/properties/" + first + "/units"), manager, unit("1", "5000")));

        send(delete("/api/properties/" + second + "/units/" + unitInFirst), manager, null)
                .andExpect(status().isNotFound());
    }

    @Test
    void unitNumberIsUniqueWithinAPropertyButNotAcrossProperties() throws Exception {
        String manager = tokenFor("MANAGER");
        long first = createProperty(manager, "First");
        long second = createProperty(manager, "Second");

        send(post("/api/properties/" + first + "/units"), manager, unit("101", "5000"))
                .andExpect(status().isCreated());
        send(post("/api/properties/" + first + "/units"), manager, unit("101", "7000"))
                .andExpect(status().isConflict());
        send(post("/api/properties/" + second + "/units"), manager, unit("101", "5000"))
                .andExpect(status().isCreated());
    }

    @Test
    void updateChangesPropertyAndUnit() throws Exception {
        String manager = tokenFor("MANAGER");
        long propertyId = createProperty(manager, "Old Name");
        long unitId = idOf(send(post("/api/properties/" + propertyId + "/units"), manager, unit("1", "5000")));

        send(put("/api/properties/" + propertyId), manager,
                Map.of("name", "New Name", "address", "1 Sheikh Zayed Road", "city", "Abu Dhabi"))
                .andExpect(status().isOk());
        send(get("/api/properties/" + propertyId), manager, null)
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.city").value("Abu Dhabi"));

        send(put("/api/properties/" + propertyId + "/units/" + unitId), manager,
                Map.of("unitNumber", "1A", "bedrooms", 3, "monthlyRent", "9250.50", "status", "UNAVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitNumber").value("1A"))
                .andExpect(jsonPath("$.monthlyRent").value(9250.50))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));
    }

    @Test
    void propertyWithUnitsCannotBeDeletedUntilUnitsAreRemoved() throws Exception {
        String manager = tokenFor("MANAGER");
        long propertyId = createProperty(manager, "To Delete");
        long unitId = idOf(send(post("/api/properties/" + propertyId + "/units"), manager, unit("1", "5000")));

        send(delete("/api/properties/" + propertyId), manager, null).andExpect(status().isConflict());
        send(delete("/api/properties/" + propertyId + "/units/" + unitId), manager, null)
                .andExpect(status().isNoContent());
        send(delete("/api/properties/" + propertyId), manager, null).andExpect(status().isNoContent());
        send(get("/api/properties/" + propertyId), manager, null).andExpect(status().isNotFound());
    }

    @Test
    void invalidUnitInputIsRejected() throws Exception {
        String manager = tokenFor("MANAGER");
        long propertyId = createProperty(manager, "Validation");
        String url = "/api/properties/" + propertyId + "/units";

        send(post(url), manager, unit("1", "0")).andExpect(status().isBadRequest());
        send(post(url), manager, unit("1", "-100")).andExpect(status().isBadRequest());
        send(post(url), manager, unit("1", "100.999")).andExpect(status().isBadRequest());
        send(post(url), manager, Map.of("unitNumber", "1", "bedrooms", -1, "monthlyRent", "100"))
                .andExpect(status().isBadRequest());
        // Only a lease may mark a unit occupied.
        send(post(url), manager, Map.of("unitNumber", "1", "bedrooms", 1, "monthlyRent", "100", "status", "OCCUPIED"))
                .andExpect(status().isBadRequest());
    }
}
