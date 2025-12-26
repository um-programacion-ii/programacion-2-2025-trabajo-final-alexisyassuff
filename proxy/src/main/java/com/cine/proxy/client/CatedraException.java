package com.cine.proxy.client;

public class CatedraException extends RuntimeException {
    private final int statusCode;

    public CatedraException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {  // ← agregá esto si no está
        return statusCode;
    }
}