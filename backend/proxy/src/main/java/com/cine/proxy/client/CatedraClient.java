package com.cine.proxy.client;

import com.cine.proxy.config.CatedraProperties;
import com.cine.proxy.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class CatedraClient {

    private static final Logger log = LoggerFactory.getLogger(CatedraClient.class);

    private final CatedraProperties props;
    private final TokenService tokenService;
    private final WebClient.Builder webClientBuilder;

    public CatedraClient(CatedraProperties props, TokenService tokenService, WebClient.Builder webClientBuilder) {
        this.props = props;
        this.tokenService = tokenService;
        this.webClientBuilder = webClientBuilder;
    }

    private WebClient buildClient() {
        return webClientBuilder
                .baseUrl(props.getBaseUrl())
                .build();
    }

    public String listEventos() { return executeGet("/eventos"); }
    public String getEvento(String id) { return executeGet("/evento/" + id); }
    public String listarVentas() { return executeGet("/listar-ventas"); }
    public String listarVenta(String id) { return executeGet("/listar-venta/" + id); }

    private String executeGet(String path) {
        WebClient client = buildClient();
        String token = tokenService.getToken();

        try {
            return client.get()
                    .uri(path)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> { if (token != null && !token.isBlank()) h.setBearerAuth(token); })
                    .retrieve()
                    .onStatus(response -> {
                        int code = response.value();
                        return code >= 400 && code < 500;
                    }, response -> response.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new CatedraException(response.statusCode().value(), body))))
                    .onStatus(response -> {
                        int code = response.value();
                        return code >= 500 && code < 600;
                    }, response -> response.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new CatedraException(502, "Upstream error: " + body))))
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .block();
        } catch (WebClientResponseException.Unauthorized ex) {
            log.warn("Cátedra respondió 401 Unauthorized — token inválido/expirado. Cargar nuevo token mediante POST /internal/catedra/token");
            throw new CatedraException(401, "Token inválido o expirado. Cargar nuevo token mediante POST /internal/catedra/token");
        } catch (WebClientResponseException.Forbidden ex) {
            log.warn("Cátedra respondió 403 Forbidden — token no autorizado/expirado. Cargar nuevo token mediante POST /internal/catedra/token");
            throw new CatedraException(403, "Token no autorizado o expirado. Cargar nuevo token mediante POST /internal/catedra/token");
        } catch (WebClientRequestException ex) {
            log.warn("WebClientRequestException: {}", ex.getMessage());
            throw new CatedraException(504, "Timeout/IO error calling cátedra: " + ex.getMessage());
        } catch (WebClientResponseException ex) {
            throw new CatedraException(ex.getRawStatusCode(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            Throwable cause = ex;
            while (cause != null) {
                if (cause instanceof java.util.concurrent.TimeoutException) {
                    throw new CatedraException(504, "Timeout calling cátedra");
                }
                cause = cause.getCause();
            }
            throw new CatedraException(502, "Unknown error calling cátedra: " + ex.getMessage());
        }
    }

    /**
     * Execute POST to upstream Cátedra: posts JSON body string and returns upstream body (string).
     */
    public String executePost(String path, String jsonBody) {
        WebClient client = buildClient();
        String token = tokenService.getToken();

        try {
            return client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> { if (token != null && !token.isBlank()) h.setBearerAuth(token); })
                    .bodyValue(jsonBody)
                    .retrieve()
                    .onStatus(response -> {
                        int code = response.value();
                        return code >= 400 && code < 500;
                    }, response -> response.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new CatedraException(response.statusCode().value(), body))))
                    .onStatus(response -> {
                        int code = response.value();
                        return code >= 500 && code < 600;
                    }, response -> response.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new CatedraException(502, "Upstream error: " + body))))
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .block();
        } catch (WebClientResponseException.Unauthorized ex) {
            log.warn("Cátedra respondió 401 Unauthorized — token inválido/expirado. Cargar nuevo token mediante POST /internal/catedra/token");
            throw new CatedraException(401, "Token inválido o expirado. Cargar nuevo token mediante POST /internal/catedra/token");
        } catch (WebClientResponseException.Forbidden ex) {
            log.warn("Cátedra respondió 403 Forbidden — token no autorizado/expirado. Cargar nuevo token mediante POST /internal/catedra/token");
            throw new CatedraException(403, "Token no autorizado o expirado. Cargar nuevo token mediante POST /internal/catedra/token");
        } catch (WebClientRequestException ex) {
            log.warn("WebClientRequestException: {}", ex.getMessage());
            throw new CatedraException(504, "Timeout/IO error calling cátedra: " + ex.getMessage());
        } catch (WebClientResponseException ex) {
            throw new CatedraException(ex.getRawStatusCode(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            Throwable cause = ex;
            while (cause != null) {
                if (cause instanceof java.util.concurrent.TimeoutException) {
                    throw new CatedraException(504, "Timeout calling cátedra");
                }
                cause = cause.getCause();
            }
            throw new CatedraException(502, "Unknown error calling cátedra: " + ex.getMessage());
        }
    }
}