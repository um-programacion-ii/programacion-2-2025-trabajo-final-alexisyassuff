package com.cine.proxy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Servicio simple para publicar mensajes a Kafka (usado por el endpoint interno de pruebas).
 */
@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);
    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(String topic, String message, long timeoutMillis) {
        try {
            kafkaTemplate.send(topic, message).get(timeoutMillis, TimeUnit.MILLISECONDS);
            log.info("Mensaje enviado a topic={} payloadLen={}", topic, message == null ? 0 : message.length());
        } catch (Exception e) {
            log.error("Fallo enviando mensaje a Kafka topic={}: {}", topic, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}