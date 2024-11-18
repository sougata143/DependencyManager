package org.infy.scanner.core;

public record Dependency(
    String groupId,
    String artifactId,
    String version,
    String scope,
    boolean isDirectDependency
) {
    @Override
    public String toString() {
        return String.format("%s:%s:%s", groupId, artifactId, version);
    }
} 