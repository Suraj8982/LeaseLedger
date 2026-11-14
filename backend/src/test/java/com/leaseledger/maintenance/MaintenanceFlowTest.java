package com.leaseledger.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.leaseledger.support.ApiTestBase;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class MaintenanceFlowTest extends ApiTestBase {

    private static final String BASE = "/api/maintenance-requests";

    /** A manager, a tenant, and an active lease between them. */
    private record Tenancy(Account manager, Account tenant, long leaseId) {
    }

    private Tenancy tenancy() throws Exception {
        Account manager = register("MANAGER");
        Account tenant = register("TENANT");
        long leaseId = bodyOf(send(post("/api/leases"), manager, Map.of(
                "unitId", createUnit(manager, "5000.00"), "tenantEmail", tenant.email(),
                "startDate", LocalDate.now().toString(), "installments", 12))
                .andExpect(status().isCreated())).get("lease").get("id").asLong();
        return new Tenancy(manager, tenant, leaseId);
    }

    private long report(Tenancy t, String title) throws Exception {
        return bodyOf(send(post(BASE), t.tenant(), Map.of(
                "leaseId", t.leaseId(), "title", title, "description", "Water is dripping from the ceiling."))
                .andExpect(status().isCreated())).get("request").get("id").asLong();
    }

    private ResultActions setStatus(Account as, long requestId, String status, String note) throws Exception {
        Map<String, Object> body = note == null ? Map.of("status", status) : Map.of("status", status, "note", note);
        return send(post(BASE + "/" + requestId + "/status"), as, body);
    }

    @Test
    void tenantReportsAProblemAndBothSidesCanSeeIt() throws Exception {
        Tenancy t = tenancy();

        JsonNode created = bodyOf(send(post(BASE), t.tenant(), Map.of(
                "leaseId", t.leaseId(), "title", "AC not cooling",
                "description", "Bedroom unit blows warm air.", "priority", "HIGH"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.request.status").value("OPEN"))
                .andExpect(jsonPath("$.request.priority").value("HIGH"))
                .andExpect(jsonPath("$.request.unitNumber").value("101"))
                .andExpect(jsonPath("$.request.propertyName").value("Marina Heights"))
                .andExpect(jsonPath("$.photos.length()").value(0)));
        long id = created.get("request").get("id").asLong();

        for (Account participant : new Account[] {t.tenant(), t.manager()}) {
            send(get(BASE), participant, null)
                    .andExpect(jsonPath("$.totalItems").value(1))
                    .andExpect(jsonPath("$.items[0].title").value("AC not cooling"));
            send(get(BASE + "/" + id), participant, null).andExpect(status().isOk());
        }
    }

    @Test
    void priorityDefaultsToMedium() throws Exception {
        Tenancy t = tenancy();

        send(get(BASE + "/" + report(t, "Leak")), t.tenant(), null)
                .andExpect(jsonPath("$.request.priority").value("MEDIUM"));
    }

    @Test
    void outsidersCannotSeeOrActOnARequest() throws Exception {
        Tenancy t = tenancy();
        long id = report(t, "Leak");

        for (Account outsider : new Account[] {register("TENANT"), register("MANAGER")}) {
            send(get(BASE), outsider, null).andExpect(jsonPath("$.totalItems").value(0));
            send(get(BASE + "/" + id), outsider, null).andExpect(status().isNotFound());
            setStatus(outsider, id, "CANCELLED", null).andExpect(status().isNotFound());
            setStatus(outsider, id, "RESOLVED", null).andExpect(status().isNotFound());
            send(post(BASE + "/" + id + "/photos/upload-url"), outsider, Map.of("contentType", "image/jpeg"))
                    .andExpect(status().isNotFound());
        }
        send(get(BASE), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void requestNeedsTheTenantsOwnActiveLease() throws Exception {
        Tenancy t = tenancy();
        Map<String, Object> body = Map.of("leaseId", t.leaseId(), "title", "Leak", "description", "Dripping.");

        // Someone else's lease looks like it does not exist.
        send(post(BASE), register("TENANT"), body).andExpect(status().isNotFound());
        // Managers do not file requests.
        send(post(BASE), t.manager(), body).andExpect(status().isForbidden());
        send(post(BASE), t.tenant(), Map.of("leaseId", t.leaseId(), "title", "", "description", "x"))
                .andExpect(status().isBadRequest());

        send(post("/api/leases/" + t.leaseId() + "/terminate"), t.manager(), null).andExpect(status().isOk());
        send(post(BASE), t.tenant(), body).andExpect(status().isConflict());
    }

    @Test
    void managerMovesRequestThroughTheWorkflow() throws Exception {
        Tenancy t = tenancy();
        long id = report(t, "Leak");

        setStatus(t.manager(), id, "IN_PROGRESS", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        setStatus(t.manager(), id, "RESOLVED", "Replaced the pipe joint.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionNote").value("Replaced the pipe joint."));

        // RESOLVED is final.
        setStatus(t.manager(), id, "IN_PROGRESS", null).andExpect(status().isConflict());
        setStatus(t.tenant(), id, "CANCELLED", null).andExpect(status().isConflict());
        send(get(BASE + "/" + id), t.tenant(), null).andExpect(jsonPath("$.request.status").value("RESOLVED"));
    }

    @Test
    void tenantCanCancelOnlyWhileOpenAndCannotDoTheManagersJob() throws Exception {
        Tenancy t = tenancy();
        long first = report(t, "Leak");
        long second = report(t, "Broken handle");

        // Wrong role for that status: 403, whatever state the request is in.
        setStatus(t.tenant(), first, "IN_PROGRESS", null).andExpect(status().isForbidden());
        setStatus(t.tenant(), first, "RESOLVED", null).andExpect(status().isForbidden());
        setStatus(t.manager(), first, "CANCELLED", null).andExpect(status().isForbidden());
        setStatus(t.manager(), first, "OPEN", null).andExpect(status().isForbidden());

        setStatus(t.tenant(), first, "CANCELLED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Right role, but work has started: the workflow says no.
        setStatus(t.manager(), second, "IN_PROGRESS", null).andExpect(status().isOk());
        setStatus(t.tenant(), second, "CANCELLED", null).andExpect(status().isConflict());

        send(post(BASE + "/" + first + "/status"), t.tenant(), Map.of("status", "NONSENSE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listCanBeFilteredByStatus() throws Exception {
        Tenancy t = tenancy();
        report(t, "One");
        long two = report(t, "Two");
        long three = report(t, "Three");
        setStatus(t.manager(), two, "IN_PROGRESS", null).andExpect(status().isOk());
        setStatus(t.manager(), three, "RESOLVED", null).andExpect(status().isOk());

        send(get(BASE), t.manager(), null).andExpect(jsonPath("$.totalItems").value(3));
        send(get(BASE + "?status=OPEN"), t.manager(), null)
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("One"));
        send(get(BASE + "?status=RESOLVED"), t.tenant(), null).andExpect(jsonPath("$.totalItems").value(1));
        send(get(BASE + "?status=CANCELLED"), t.manager(), null).andExpect(jsonPath("$.totalItems").value(0));
        send(get(BASE + "?size=2"), t.manager(), null)
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    // ---- photos ----

    private String requestUploadKey(Account as, long requestId, String contentType) throws Exception {
        return bodyOf(send(post(BASE + "/" + requestId + "/photos/upload-url"), as,
                Map.of("contentType", contentType)).andExpect(status().isOk())).get("objectKey").asText();
    }

    private ResultActions attach(Account as, long requestId, String objectKey) throws Exception {
        return send(post(BASE + "/" + requestId + "/photos"), as, Map.of("objectKey", objectKey));
    }

    @Test
    void photoIsUploadedDirectlyToStorageThenAttached() throws Exception {
        Tenancy t = tenancy();
        long id = report(t, "Leak");

        // Step 1: ask for an upload URL.
        JsonNode upload = bodyOf(send(post(BASE + "/" + id + "/photos/upload-url"), t.tenant(),
                Map.of("contentType", "image/jpeg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey", startsWith("maintenance/" + id + "/")))
                .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.expiresInSeconds").value(600)));
        String key = upload.get("objectKey").asText();
        assertThat(key).endsWith(".jpg");

        // Attaching before the file exists is refused: the server checks storage itself.
        attach(t.tenant(), id, key).andExpect(status().isBadRequest());

        // Step 2: the browser uploads to storage (simulated). Step 3: attach.
        storage.simulateUpload(key, "image/jpeg", 204_800);
        attach(t.tenant(), id, key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sizeBytes").value(204_800))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.viewUrl").isNotEmpty());

        attach(t.tenant(), id, key).andExpect(status().isConflict()); // same file twice

        // The manager sees the photo with a link to view it.
        send(get(BASE + "/" + id), t.manager(), null)
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].viewUrl").isNotEmpty());
    }

    @Test
    void uploadRulesAreEnforced() throws Exception {
        Tenancy t = tenancy();
        long id = report(t, "Leak");
        long otherRequest = report(t, "Other");

        // Only images.
        for (String type : new String[] {"application/pdf", "image/svg+xml", "text/html"}) {
            send(post(BASE + "/" + id + "/photos/upload-url"), t.tenant(), Map.of("contentType", type))
                    .andExpect(status().isBadRequest());
        }

        // A key issued for one request cannot be attached to another.
        String keyForOther = requestUploadKey(t.tenant(), otherRequest, "image/png");
        storage.simulateUpload(keyForOther, "image/png", 1_000);
        attach(t.tenant(), id, keyForOther).andExpect(status().isBadRequest());
        attach(t.tenant(), id, "some/other/object.jpg").andExpect(status().isBadRequest());

        // Too large: refused, and the oversized file is removed from storage.
        String bigKey = requestUploadKey(t.tenant(), id, "image/png");
        storage.simulateUpload(bigKey, "image/png", 6L * 1024 * 1024);
        attach(t.tenant(), id, bigKey).andExpect(status().isBadRequest());
        assertThat(storage.contains(bigKey)).isFalse();

        send(get(BASE + "/" + id), t.tenant(), null).andExpect(jsonPath("$.photos.length()").value(0));
    }

    @Test
    void atMostFivePhotosAndNoneOnceTheRequestIsClosed() throws Exception {
        Tenancy t = tenancy();
        long id = report(t, "Leak");

        for (int i = 0; i < 5; i++) {
            String key = requestUploadKey(t.tenant(), id, "image/webp");
            storage.simulateUpload(key, "image/webp", 1_000);
            attach(t.tenant(), id, key).andExpect(status().isCreated());
        }
        send(post(BASE + "/" + id + "/photos/upload-url"), t.tenant(), Map.of("contentType", "image/webp"))
                .andExpect(status().isConflict());

        long closed = report(t, "Closed one");
        setStatus(t.manager(), closed, "RESOLVED", null).andExpect(status().isOk());
        send(post(BASE + "/" + closed + "/photos/upload-url"), t.tenant(), Map.of("contentType", "image/webp"))
                .andExpect(status().isConflict());
    }
}
