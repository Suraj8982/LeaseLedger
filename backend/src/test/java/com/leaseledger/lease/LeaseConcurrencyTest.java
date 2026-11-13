package com.leaseledger.lease;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.leaseledger.support.ApiTestBase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Proves the rule "one unit, one active lease" holds when requests arrive at the same
 * moment, not just one after another.
 */
class LeaseConcurrencyTest extends ApiTestBase {

    private static final int SIMULTANEOUS_REQUESTS = 8;

    @Test
    void manySimultaneousLeaseRequestsForOneUnitProduceExactlyOneLease() throws Exception {
        Account manager = register("MANAGER");
        long unitId = createUnit(manager, "5000.00");
        List<Account> tenants = new ArrayList<>();
        for (int i = 0; i < SIMULTANEOUS_REQUESTS; i++) {
            tenants.add(register("TENANT"));
        }

        ExecutorService pool = Executors.newFixedThreadPool(SIMULTANEOUS_REQUESTS);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<Integer>> responses = new ArrayList<>();
        for (Account tenant : tenants) {
            responses.add(pool.submit(() -> {
                startTogether.await();
                return send(post("/api/leases"), manager, Map.of(
                        "unitId", unitId, "tenantEmail", tenant.email(),
                        "startDate", LocalDate.of(2030, 1, 1).toString(), "installments", 12))
                        .andReturn().getResponse().getStatus();
            }));
        }
        startTogether.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> response : responses) {
            statuses.add(response.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(SIMULTANEOUS_REQUESTS - 1);
        assertThat(bodyOf(send(get("/api/leases"), manager, null)).get("totalItems").asInt()).isEqualTo(1);
        assertThat(bodyOf(send(get("/api/invoices"), manager, null)).get("totalItems").asInt()).isEqualTo(12);
    }
}
