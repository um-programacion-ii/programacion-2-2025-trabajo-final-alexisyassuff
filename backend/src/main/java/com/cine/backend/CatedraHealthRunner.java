package com.cine.backend;

import com.cine.backend.service.CatedraClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CatedraHealthRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CatedraHealthRunner.class);

    private final CatedraClient catedraClient;

    // Propiedad para poder desactivar el ping al arranque si es necesario
    private final boolean pingOnStartup;

    public CatedraHealthRunner(CatedraClient catedraClient,
                               @Value("${catedra.ping-on-startup:true}") boolean pingOnStartup) {
        this.catedraClient = catedraClient;
        this.pingOnStartup = pingOnStartup;
    }

    @Override
    public void run(String... args) {
        if (!pingOnStartup) {
            log.info("Ping a cátedra desactivado por catedra.ping-on-startup=false");
            return;
        }
        String result = catedraClient.pingBaseUrl();
        log.info("Resultado ping cátedra: {}", result);
    }
}