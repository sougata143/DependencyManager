package org.infy.scanner.reporting;

import org.infy.scanner.vulnerability.VulnerabilityResult;
import java.util.Set;

public record ReportSummary(
    int totalVulnerabilities,
    int criticalCount,
    int highCount,
    int mediumCount,
    int lowCount
) {
    public ReportSummary(Set<VulnerabilityResult> vulnerabilities) {
        this(
            vulnerabilities.size(),
            countBySeverity(vulnerabilities, VulnerabilityResult.Severity.CRITICAL),
            countBySeverity(vulnerabilities, VulnerabilityResult.Severity.HIGH),
            countBySeverity(vulnerabilities, VulnerabilityResult.Severity.MEDIUM),
            countBySeverity(vulnerabilities, VulnerabilityResult.Severity.LOW)
        );
    }

    private static int countBySeverity(Set<VulnerabilityResult> vulnerabilities, VulnerabilityResult.Severity severity) {
        return (int) vulnerabilities.stream()
            .filter(v -> v.severity() == severity)
            .count();
    }
} 