package com.cine.proxy.client;

public class CatedraException extends RuntimeException {
    private final int status;

    public CatedraException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }
}