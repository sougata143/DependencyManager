package org.infy.scanner.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.infy.scanner.vulnerability.VulnerabilityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Set;

public class JsonReportGenerator implements ReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(JsonReportGenerator.class);
    private final ObjectMapper objectMapper;

    public JsonReportGenerator() {
        this.objectMapper = new ObjectMapper()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .findAndRegisterModules();
    }

    @Override
    public void generateReport(Set<VulnerabilityResult> vulnerabilities, Path outputPath) {
        try {
            objectMapper.writeValue(outputPath.toFile(), new VulnerabilityReport(vulnerabilities));
            logger.info("JSON report generated at: {}", outputPath);
        } catch (Exception e) {
            logger.error("Failed to generate JSON report", e);
            throw new ReportGenerationException("Failed to generate JSON report", e);
        }
    }
} 