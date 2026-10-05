package com.acme.payments;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:payments-idempotency-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000",
        "spring.flyway.locations=classpath:db/migration-h2"
})
@ActiveProfiles("dev")
class PaymentIdempotencyIntegrationTest {
    @Autowired ObjectMapper mapper;
    @Autowired OutboxRepository outbox;
    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void concurrentRetriesCreateOnePaymentAndOneOutboxEvent() throws Exception {
        var login = send("POST", "/api/v1/auth/login", null, null,
                Map.of("email", "demo@example.com", "password", "Demo123!"));
        assertEquals(200, login.statusCode());
        String token = (String) mapper.readValue(login.body(), Map.class).get("accessToken");

        String key = "concurrent-" + UUID.randomUUID();
        var payload = Map.of("amount", 12.50, "currency", "BRL", "description", "Concurrent test");
        int attempts = 24;
        var ready = new CountDownLatch(attempts);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(attempts);
        try {
            var calls = new ArrayList<java.util.concurrent.Future<HttpResponse<String>>>(attempts);
            for (int i = 0; i < attempts; i++) {
                calls.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return send("POST", "/api/v1/payments", token, key, payload);
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            var ids = new java.util.HashSet<String>();
            for (var call : calls) {
                var response = call.get(30, TimeUnit.SECONDS);
                assertEquals(202, response.statusCode());
                ids.add((String) mapper.readValue(response.body(), Map.class).get("id"));
            }
            assertEquals(1, ids.size(), "all retries must return one logical payment ID");
            assertEquals(1, outbox.count(), "payment creation and outbox event must be atomic and unique");

            var changedBody = Map.of("amount", 13.50, "currency", "BRL", "description", "Concurrent test");
            var conflict = send("POST", "/api/v1/payments", token, key, changedBody);
            assertEquals(409, conflict.statusCode());

            var history = send("GET", "/api/v1/payments?page=0&size=5", token, null, null);
            assertEquals(200, history.statusCode());
            assertEquals(1, ((Number) mapper.readValue(history.body(), Map.class).get("totalElements")).intValue());
        } finally {
            pool.shutdownNow();
        }
    }

    private HttpResponse<String> send(String method, String path, String token, String key, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (key != null) builder.header("Idempotency-Key", key);
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        } else builder.method(method, HttpRequest.BodyPublishers.noBody());
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
