package com.leaseledger.lease;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.leaseledger.support.ApiTestBase;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class LeaseFlowTest extends ApiTestBase {

    private static final LocalDate START = LocalDate.of(2030, 1, 1);

    private Map<String, Object> leaseRequest(long unitId, Account tenant, LocalDate start, int installments) {
        Map<String, Object> body = new HashMap<>();
        body.put("unitId", unitId);
        body.put("tenantEmail", tenant.email());
        body.put("startDate", start.toString());
        body.put("installments", installments);
        return body;
    }

    private ResultActions createLease(Account manager, Map<String, Object> body) throws Exception {
        return send(post("/api/leases"), manager, body);
    }

    @Test
    void creatingALeaseGeneratesInvoicesAndOccupiesTheUnit() throws Exception {
        Account manager = register("MANAGER");
        Account tenant = register("TENANT");
        long unitId = createUnit(manager, "8500.00");
        Map<String, Object> body = leaseRequest(unitId, tenant, START, 4);
        body.put("securityDeposit", "5000.00");

        JsonNode created = bodyOf(createLease(manager, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lease.status").value("ACTIVE"))
                .andExpect(jsonPath("$.lease.endDate").value("2030-12-31"))
                // Not supplied, so 12 x 8500.
                .andExpect(jsonPath("$.lease.totalRent").value(102000.00))
                .andExpect(jsonPath("$.lease.unitNumber").value("101"))
                .andExpect(jsonPath("$.lease.propertyName").value("Marina Heights"))
                .andExpect(jsonPath("$.lease.tenantEmail").value(tenant.email()))
                .andExpect(jsonPath("$.invoices.length()").value(5)));

        JsonNode invoices = created.get("invoices");
        assertThat(invoices.get(0).get("type").asText()).isEqualTo("DEPOSIT");
        assertThat(invoices.get(0).get("amount").decimalValue()).isEqualByComparingTo("5000.00");
        assertThat(invoices.findValuesAsText("dueDate"))
                .containsExactly("2030-01-01", "2030-01-01", "2030-04-01", "2030-07-01", "2030-10-01");
        for (int i = 1; i <= 4; i++) {
            assertThat(invoices.get(i).get("type").asText()).isEqualTo("RENT");
            assertThat(invoices.get(i).get("amount").decimalValue()).isEqualByComparingTo("25500.00");
            assertThat(invoices.get(i).get("status").asText()).isEqualTo("PENDING");
            assertThat(invoices.get(i).get("overdue").asBoolean()).isFalse();
        }

        // The same data read back through GET, and the unit now shows as occupied.
        long leaseId = created.get("lease").get("id").asLong();
        send(get("/api/leases/" + leaseId), manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoices.length()").value(5));
        send(get("/api/properties"), manager, null)
                .andExpect(jsonPath("$.items[0].unitCount").value(1))
                .andExpect(jsonPath("$.items[0].vacantUnitCount").value(0));
    }

    @Test
    void explicitTotalRentThatDoesNotDivideEvenlyStillSumsExactly() throws Exception {
        Account manager = register("MANAGER");
        Map<String, Object> body = leaseRequest(createUnit(manager, "8500.00"), register("TENANT"), START, 6);
        body.put("totalRent", "100000.00");

        JsonNode invoices = bodyOf(createLease(manager, body).andExpect(status().isCreated())).get("invoices");

        assertThat(invoices).hasSize(6); // no deposit invoice when no deposit was asked for
        assertThat(invoices.get(0).get("amount").decimalValue()).isEqualByComparingTo("16666.66");
        assertThat(invoices.get(5).get("amount").decimalValue()).isEqualByComparingTo("16666.70");
    }

    @Test
    void anOccupiedUnitCannotBeLeasedAgain() throws Exception {
        Account manager = register("MANAGER");
        long unitId = createUnit(manager, "5000.00");
        createLease(manager, leaseRequest(unitId, register("TENANT"), START, 12)).andExpect(status().isCreated());

        createLease(manager, leaseRequest(unitId, register("TENANT"), START, 12))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This unit is not vacant"));
    }

    @Test
    void tenantSeesOnlyTheirOwnLeaseAndInvoices() throws Exception {
        Account manager = register("MANAGER");
        Account tenant = register("TENANT");
        Account otherTenant = register("TENANT");
        Account otherManager = register("MANAGER");
        long leaseId = bodyOf(createLease(manager, leaseRequest(createUnit(manager, "5000.00"), tenant, START, 12))
                .andExpect(status().isCreated())).get("lease").get("id").asLong();

        send(get("/api/leases"), tenant, null).andExpect(jsonPath("$.totalItems").value(1));
        send(get("/api/leases/" + leaseId), tenant, null).andExpect(status().isOk());
        send(get("/api/invoices"), tenant, null).andExpect(jsonPath("$.totalItems").value(12));
        send(get("/api/leases"), manager, null).andExpect(jsonPath("$.totalItems").value(1));
        send(get("/api/invoices?size=5"), manager, null)
                .andExpect(jsonPath("$.totalItems").value(12))
                .andExpect(jsonPath("$.items.length()").value(5));

        for (Account outsider : new Account[] {otherTenant, otherManager}) {
            send(get("/api/leases"), outsider, null).andExpect(jsonPath("$.totalItems").value(0));
            send(get("/api/leases/" + leaseId), outsider, null).andExpect(status().isNotFound());
            send(get("/api/invoices"), outsider, null).andExpect(jsonPath("$.totalItems").value(0));
        }
        send(post("/api/leases/" + leaseId + "/terminate"), otherManager, null).andExpect(status().isNotFound());
    }

    @Test
    void tenantCannotCreateLeasesOrRecordPayments() throws Exception {
        Account manager = register("MANAGER");
        Account tenant = register("TENANT");
        long unitId = createUnit(manager, "5000.00");
        JsonNode lease = bodyOf(createLease(manager, leaseRequest(unitId, tenant, START, 1))
                .andExpect(status().isCreated()));
        long invoiceId = lease.get("invoices").get(0).get("id").asLong();
        long leaseId = lease.get("lease").get("id").asLong();

        createLease(tenant, leaseRequest(unitId, tenant, START, 1)).andExpect(status().isForbidden());
        send(post("/api/invoices/" + invoiceId + "/mark-paid"), tenant, null).andExpect(status().isForbidden());
        send(post("/api/leases/" + leaseId + "/terminate"), tenant, null).andExpect(status().isForbidden());
        send(get("/api/leases"), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void overdueFilterAndMarkingAnInvoicePaid() throws Exception {
        Account manager = register("MANAGER");
        Account tenant = register("TENANT");
        // Started 40 days ago, monthly: the first two installments are past due, the rest are not.
        LocalDate start = LocalDate.now().minusDays(40);
        createLease(manager, leaseRequest(createUnit(manager, "5000.00"), tenant, start, 12))
                .andExpect(status().isCreated());

        JsonNode overdue = bodyOf(send(get("/api/invoices?filter=OVERDUE"), manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].overdue").value(true)));
        long firstOverdueId = overdue.get("items").get(0).get("id").asLong();
        send(get("/api/invoices?filter=OVERDUE"), tenant, null).andExpect(jsonPath("$.totalItems").value(2));

        send(post("/api/invoices/" + firstOverdueId + "/mark-paid"), manager, Map.of("reference", "Cheque 000123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.paymentReference").value("Cheque 000123"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());

        // Paying twice is refused, and the money is not recorded twice.
        send(post("/api/invoices/" + firstOverdueId + "/mark-paid"), manager, null)
                .andExpect(status().isConflict());

        send(get("/api/invoices?filter=OVERDUE"), manager, null).andExpect(jsonPath("$.totalItems").value(1));
        send(get("/api/invoices?filter=PAID"), manager, null).andExpect(jsonPath("$.totalItems").value(1));
        send(get("/api/invoices?filter=PENDING"), manager, null).andExpect(jsonPath("$.totalItems").value(11));
        send(get("/api/invoices?filter=NONSENSE"), manager, null).andExpect(status().isBadRequest());
    }

    @Test
    void managerCannotMarkAnotherManagersInvoicePaid() throws Exception {
        Account manager = register("MANAGER");
        long invoiceId = bodyOf(createLease(manager,
                leaseRequest(createUnit(manager, "5000.00"), register("TENANT"), START, 1))
                .andExpect(status().isCreated())).get("invoices").get(0).get("id").asLong();

        send(post("/api/invoices/" + invoiceId + "/mark-paid"), register("MANAGER"), null)
                .andExpect(status().isNotFound());
    }

    @Test
    void terminatingCancelsFutureInvoicesKeepsDueOnesAndFreesTheUnit() throws Exception {
        Account manager = register("MANAGER");
        long unitId = createUnit(manager, "5000.00");
        LocalDate start = LocalDate.now().minusDays(40);
        long leaseId = bodyOf(createLease(manager, leaseRequest(unitId, register("TENANT"), start, 12))
                .andExpect(status().isCreated())).get("lease").get("id").asLong();

        send(post("/api/leases/" + leaseId + "/terminate"), manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lease.status").value("TERMINATED"))
                .andExpect(jsonPath("$.lease.terminatedOn").isNotEmpty());

        // The two installments already due are still owed; the ten future ones are void.
        send(get("/api/invoices?filter=PENDING"), manager, null).andExpect(jsonPath("$.totalItems").value(2));
        send(get("/api/invoices?filter=CANCELLED"), manager, null).andExpect(jsonPath("$.totalItems").value(10));

        send(post("/api/leases/" + leaseId + "/terminate"), manager, null).andExpect(status().isConflict());

        // The unit can be leased to someone else now.
        createLease(manager, leaseRequest(unitId, register("TENANT"), LocalDate.now(), 12))
                .andExpect(status().isCreated());
    }

    @Test
    void unitWithLeaseHistoryCannotBeDeleted() throws Exception {
        Account manager = register("MANAGER");
        long unitId = createUnit(manager, "5000.00");
        long leaseId = bodyOf(createLease(manager, leaseRequest(unitId, register("TENANT"), START, 12))
                .andExpect(status().isCreated())).get("lease").get("id").asLong();
        long propertyId = bodyOf(send(get("/api/properties"), manager, null)).get("items").get(0).get("id").asLong();
        String unitUrl = "/api/properties/" + propertyId + "/units/" + unitId;

        send(delete(unitUrl), manager, null).andExpect(status().isConflict()); // occupied
        send(post("/api/leases/" + leaseId + "/terminate"), manager, null).andExpect(status().isOk());
        send(delete(unitUrl), manager, null).andExpect(status().isConflict()); // vacant, but has history
    }

    @Test
    void invalidLeaseRequestsAreRejected() throws Exception {
        Account manager = register("MANAGER");
        Account tenant = register("TENANT");
        long unitId = createUnit(manager, "5000.00");

        // 5 payments cannot be spread evenly over 12 months.
        createLease(manager, leaseRequest(unitId, tenant, START, 5)).andExpect(status().isBadRequest());
        // The "tenant" must be a registered tenant account.
        createLease(manager, leaseRequest(unitId, new Account("nobody@example.com", ""), START, 12))
                .andExpect(status().isBadRequest());
        createLease(manager, leaseRequest(unitId, manager, START, 12)).andExpect(status().isBadRequest());
        // Someone else's unit looks like it does not exist.
        createLease(register("MANAGER"), leaseRequest(unitId, tenant, START, 12)).andExpect(status().isNotFound());

        Map<String, Object> missingStart = leaseRequest(unitId, tenant, START, 12);
        missingStart.remove("startDate");
        createLease(manager, missingStart).andExpect(status().isBadRequest());

        // After all those failures the unit is still free: nothing was half-saved.
        createLease(manager, leaseRequest(unitId, tenant, START, 12)).andExpect(status().isCreated());
    }
}
