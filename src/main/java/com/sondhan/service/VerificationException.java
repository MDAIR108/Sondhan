package com.sondhan.service;

/**
 * Typed failure for the fact-verification pipeline (bad input, unfetchable
 * pages, empty extraction). Callers already handle Exception, so this
 * upgrades previously generic throws without changing any catch blocks.
 */
public class VerificationException extends Exception {

    public VerificationException(String message) {
        super(message);
    }

    public VerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
