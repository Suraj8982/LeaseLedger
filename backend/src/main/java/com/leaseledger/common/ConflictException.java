package com.leaseledger.common;

/** The request is valid but cannot be applied to the current state of the data. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
