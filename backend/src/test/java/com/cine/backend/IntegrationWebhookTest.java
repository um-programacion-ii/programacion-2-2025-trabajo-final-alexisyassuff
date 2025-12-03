package com.cine.backend;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Jedis;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class IntegrationWebhookTest {

    static GenericContainer<?> redis = new GenericContainer<>("redis:7.0.15").withExposedPorts(6379);
    static WireMockServer proxyMock;

    @BeforeAll
    static void setupAll() {
        redis.start();
        proxyMock = new WireMockServer(8089);
        proxyMock.start();
        // stub proxy events endpoint
        proxyMock.stubFor(get(urlPathEqualTo("/internal/kafka/events"))
                .willReturn(aResponse().withHeader("Content-Type","application/json")
                        .withBody("[{\"seatId\":\"r2cX\",\"status\":\"BLOQUEADO\",\"holder\":\"aluX\",\"updatedAt\":\"2025-12-02T00:00:00Z\"}]")));

        System.setProperty("spring.redis.host", redis.getHost());
        System.setProperty("spring.redis.port", redis.getFirstMappedPort().toString());
        System.setProperty("proxy.base-url", "http://localhost:8089");
    }

    @AfterAll
    static void tearDownAll() {
        proxyMock.stop();
        redis.stop();
    }

    @Test
    void webhook_writes_key_in_redis() throws Exception {
        // Call backend webhook endpoint (assumes backend running in defined port 8080 in test profile)
        // use HTTP client to POST /internal/proxy/webhook
        // then connect to redis and assert key exists
        try (Jedis j = new Jedis(redis.getHost(), redis.getFirstMappedPort())) {
            // wait a little for frontend startup if needed
            Thread.sleep(1000);
            // trigger the webhook into backend (HTTP client omitted; use RestTemplate)
            // For brevity, assume test code sends request; then:
            String val = j.get("backend:proxy:webhook:1:r2cX");
            Assertions.assertEquals("2025-12-02T00:00:00Z", val);
        }
    }
}