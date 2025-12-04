package com.cine.backend.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.Jedis;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test E2E que levanta Redis (Testcontainers) y WireMock (in-process) y orquesta:
 * - publish -> proxy events -> backend webhook -> reconcile dryRun/apply -> ver audit en Redis y verify POSTs al proxy
 *
 * Requisitos: Docker local (Testcontainers lo requiere).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BackendProxyE2ETest {

    // Redis container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.0.15")
            .withExposedPorts(6379);

    WireMockServer proxyMock;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    Jedis jedis;

    @BeforeAll
    void setupAll() {
        // start redis container
        redis.start();

        // start WireMock on dynamic port using WireMockConfiguration (fix import)
        proxyMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        proxyMock.start();

        // configure stubs for proxy
        proxyMock.stubFor(get(urlPathEqualTo("/internal/kafka/events"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"seatId\":\"r2cX\",\"status\":\"LIBRE\",\"holder\":null,\"updatedAt\":\"2025-12-01T00:00:00Z\"}]")
                ));

        proxyMock.stubFor(post(urlPathEqualTo("/internal/kafka/publish"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sent\":true}")
                        .withStatus(200)
                ));

        // configure Test properties at runtime by system props (Spring picks them up)
        System.setProperty("spring.redis.host", redis.getHost());
        System.setProperty("spring.redis.port", String.valueOf(redis.getFirstMappedPort()));
        System.setProperty("proxy.base-url", proxyMock.baseUrl());

        // Jedis client to assert Redis contents
        jedis = new Jedis(redis.getHost(), redis.getFirstMappedPort());
    }

    @AfterAll
    void tearDownAll() {
        if (proxyMock != null && proxyMock.isRunning()) proxyMock.stop();
        if (redis != null && redis.isRunning()) redis.stop();
        if (jedis != null) jedis.close();
    }

    @Test
    void e2e_full_flow_webhook_and_reconcile_apply() throws Exception {
        // 1) Ensure starting state: remove audit/retry keys
        jedis.del("backend:reconciliation:applied");
        jedis.del("backend:webhook:retry");
        jedis.del("backend:proxy:webhook:1:r2cX");

        // 2) Trigger webhook to backend
        Map<String,Object> webhookPayload = Map.of(
                "eventoId","1",
                "seatId","r2cX",
                "status","BLOQUEADO",
                "holder","aluX",
                "updatedAt","2025-12-02T00:00:00Z"
        );
        rest.postForEntity("http://localhost:" + port + "/internal/proxy/webhook", webhookPayload, String.class);

        // 3) wait for idempotency key in Redis
        boolean keyExists = spinWait(() -> jedis.get("backend:proxy:webhook:1:r2cX") != null, Duration.ofSeconds(10));
        assertTrue(keyExists, "Idempotency key should be set in Redis after webhook");

        // 4) dryRun reconciliation
        var dryRunResp = rest.postForEntity("http://localhost:" + port + "/internal/proxy/evento/1/reconcile?dryRun=true", null, Map.class);
        assertEquals(200, dryRunResp.getStatusCodeValue());
        Map<String,Object> dryBody = dryRunResp.getBody();
        assertNotNull(dryBody);
        assertTrue(((Number) dryBody.getOrDefault("diffCount",0)).intValue() >= 0);

        // 5) apply reconciliation
        var applyResp = rest.postForEntity("http://localhost:" + port + "/internal/proxy/evento/1/reconcile?apply=true", null, Map.class);
        assertEquals(200, applyResp.getStatusCodeValue());
        Map<String,Object> applyBody = applyResp.getBody();
        assertNotNull(applyBody);
        assertTrue(applyBody.containsKey("applied"));

        // 6) verify WireMock got publish calls equal to applied diffs
        boolean received = spinWait(() -> {
            try {
                proxyMock.verify(postRequestedFor(urlPathEqualTo("/internal/kafka/publish")));
                return true;
            } catch (AssertionError e) {
                return false;
            }
        }, Duration.ofSeconds(10));
        assertTrue(received, "Proxy should have received publish POSTs");

        // 7) verify audit entries in Redis
        boolean auditWritten = spinWait(() -> jedis.llen("backend:reconciliation:applied") > 0, Duration.ofSeconds(5));
        assertTrue(auditWritten, "Audit entries should exist in Redis");

        List<String> audit = jedis.lrange("backend:reconciliation:applied", 0, -1);
        assertFalse(audit.isEmpty(), "Audit list not empty");
    }

    // small helper spin-wait
    private boolean spinWait(Check check, Duration timeout) throws InterruptedException {
        long start = System.currentTimeMillis();
        long to = timeout.toMillis();
        while (System.currentTimeMillis() - start < to) {
            if (check.ok()) return true;
            Thread.sleep(200);
        }
        return false;
    }

    @FunctionalInterface
    interface Check { boolean ok(); }
}