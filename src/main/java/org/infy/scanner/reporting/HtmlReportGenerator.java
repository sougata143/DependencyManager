package org.infy.scanner.reporting;

import org.infy.scanner.vulnerability.VulnerabilityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class HtmlReportGenerator implements ReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(HtmlReportGenerator.class);

    @Override
    public void generateReport(Set<VulnerabilityResult> vulnerabilities, Path outputPath) {
        try {
            String html = generateHtml(vulnerabilities);
            Files.writeString(outputPath, html);
            logger.info("HTML report generated at: {}", outputPath);
        } catch (Exception e) {
            logger.error("Failed to generate HTML report", e);
            throw new ReportGenerationException("Failed to generate HTML report", e);
        }
    }

    private String generateHtml(Set<VulnerabilityResult> vulnerabilities) {
        StringBuilder html = new StringBuilder();
        html.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <title>Dependency Vulnerability Report</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    .vulnerability { border: 1px solid #ddd; margin: 10px 0; padding: 10px; }
                    .critical { border-left: 5px solid #dc3545; }
                    .high { border-left: 5px solid #fd7e14; }
                    .medium { border-left: 5px solid #ffc107; }
                    .low { border-left: 5px solid #28a745; }
                </style>
            </head>
            <body>
                <h1>Dependency Vulnerability Report</h1>
                <p>Generated on: %s</p>
                <h2>Found %d vulnerabilities</h2>
            """.formatted(java.time.LocalDateTime.now(), vulnerabilities.size()));

        // Group vulnerabilities by severity
        var groupedVulnerabilities = vulnerabilities.stream()
            .collect(Collectors.groupingBy(VulnerabilityResult::severity));

        // Generate sections for each severity level
        for (var severity : VulnerabilityResult.Severity.values()) {
            List<VulnerabilityResult> severityVulnerabilities = 
                groupedVulnerabilities.getOrDefault(severity, List.of());
            
            if (!severityVulnerabilities.isEmpty()) {
                html.append(String.format("<h3>%s Severity (%d)</h3>", 
                    severity, severityVulnerabilities.size()));
                
                for (VulnerabilityResult vulnerability : severityVulnerabilities) {
                    html.append(generateVulnerabilityHtml(vulnerability));
                }
            }
        }

        html.append("</body></html>");
        return html.toString();
    }

    private String generateVulnerabilityHtml(VulnerabilityResult vulnerability) {
        return String.format("""
            <div class="vulnerability %s">
                <h4>%s</h4>
                <p><strong>Dependency:</strong> %s</p>
                <p><strong>Severity:</strong> %s</p>
                <p><strong>Description:</strong> %s</p>
                <p><strong>Published:</strong> %s</p>
                <p><strong>Remediation:</strong> %s</p>
            </div>
            """,
            vulnerability.severity().toString().toLowerCase(),
            vulnerability.vulnerabilityId(),
            vulnerability.dependency().toString(),
            vulnerability.severity(),
            vulnerability.description(),
            vulnerability.publishedDate(),
            vulnerability.remediation()
        );
    }
} 