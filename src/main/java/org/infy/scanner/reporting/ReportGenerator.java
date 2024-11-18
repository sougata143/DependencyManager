package org.infy.scanner.reporting;

import org.infy.scanner.vulnerability.VulnerabilityResult;
import java.nio.file.Path;
import java.util.Set;

public interface ReportGenerator {
    void generateReport(Set<VulnerabilityResult> vulnerabilities, Path outputPath);
} 