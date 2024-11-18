package org.infy;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.infy.scanner.config.NvdConfig;
import org.infy.scanner.core.Dependency;
import org.infy.scanner.core.DependencyScanner;
import org.infy.scanner.core.DependencyScannerFactory;
import org.infy.scanner.maven.MavenDependencyScanner;
import org.infy.scanner.reporting.HtmlReportGenerator;
import org.infy.scanner.reporting.JsonReportGenerator;
import org.infy.scanner.reporting.ReportGenerator;
import org.infy.scanner.vulnerability.NvdVulnerabilityScanner;
import org.infy.scanner.vulnerability.VulnerabilityResult;
import org.infy.scanner.vulnerability.VulnerabilityScanner;
import org.infy.scanner.visualization.VisualizationGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            logger.error("Please provide the project path as an argument");
            System.exit(1);
        }

        try {
            Path projectPath = Path.of(args[0]);
            
            // Load NVD configuration
            NvdConfig nvdConfig = new NvdConfig();
            String apiKey = nvdConfig.getApiKey();
            if (apiKey == null || apiKey.trim().isEmpty()) {
                logger.error("NVD API key not found. Please set it in ~/.config/nvd/config.json");
                System.exit(1);
            }
            
            // Initialize scanners
            DependencyScanner scanner = DependencyScannerFactory.createScanner(projectPath);
            VulnerabilityScanner vulnScanner = new NvdVulnerabilityScanner(apiKey);
            
            // Initialize report generators
            ReportGenerator htmlReporter = new HtmlReportGenerator();
            ReportGenerator jsonReporter = new JsonReportGenerator();
            VisualizationGenerator visualizer = new VisualizationGenerator();
            
            // Scan for dependencies
            Set<Dependency> dependencies = scanner.scanProject(projectPath);
            logger.info("Found {} dependencies", dependencies.size());
            
            // Scan for vulnerabilities
            Set<VulnerabilityResult> vulnerabilities = vulnScanner.scanForVulnerabilities(dependencies);
            logger.info("Found {} vulnerabilities", vulnerabilities.size());
            
            // Generate reports and visualization
            Path reportsDir = projectPath.resolve("dependency-reports");
            Files.createDirectories(reportsDir);
            
            htmlReporter.generateReport(vulnerabilities, reportsDir.resolve("vulnerability-report.html"));
            jsonReporter.generateReport(vulnerabilities, reportsDir.resolve("vulnerability-report.json"));
            visualizer.generateVisualization(
                dependencies, 
                vulnerabilities, 
                reportsDir.resolve("dependency-graph.html")
            );
            
        } catch (Exception e) {
            logger.error("Error scanning project", e);
            System.exit(1);
        }
    }

    public static DependencyScanner createMavenDependencyScanner() {
        try {
            // Create settings builder
            SettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();
            SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
            
            // Set default settings locations
            Path userSettings = Path.of(System.getProperty("user.home"), ".m2", "settings.xml");
            if (Files.exists(userSettings)) {
                request.setUserSettingsFile(userSettings.toFile());
            }
            
            Path globalSettings = Path.of(System.getenv("M2_HOME"), "conf", "settings.xml");
            if (Files.exists(globalSettings)) {
                request.setGlobalSettingsFile(globalSettings.toFile());
            }
            
            // Create Maven dependency scanner
            return new MavenDependencyScanner(settingsBuilder.build(request).getEffectiveSettings());
        } catch (Exception e) {
            logger.error("Failed to create Maven dependency scanner", e);
            throw new RuntimeException("Failed to create Maven dependency scanner", e);
        }
    }
}