package org.infy.scanner.visualization;

import org.infy.scanner.vulnerability.VulnerabilityResult;

public record GraphNode(
    String id,
    String version,
    boolean isDirectDependency,
    VulnerabilityResult.Severity maxSeverity
) {
    public String getColor() {
        if (maxSeverity == null) return "#28a745";
        return switch (maxSeverity) {
            case CRITICAL -> "#dc3545";
            case HIGH -> "#fd7e14";
            case MEDIUM -> "#ffc107";
            case LOW -> "#28a745";
        };
    }
} 