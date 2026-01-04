package com.piplineData.loadService.exception;

public class DataSyncException extends RuntimeException {

    public DataSyncException(String message) {
        super(message);
    }

    public DataSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
