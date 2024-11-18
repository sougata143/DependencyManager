package org.infy.scanner.repository;

public class RepositoryConfigurationException extends RuntimeException {
    public RepositoryConfigurationException(String message) {
        super(message);
    }

    public RepositoryConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
} 