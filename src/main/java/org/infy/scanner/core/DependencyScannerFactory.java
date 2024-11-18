package org.infy.scanner.core;

import org.infy.scanner.gradle.GradleDependencyScanner;
import org.infy.scanner.maven.MavenDependencyScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class DependencyScannerFactory {
    private static final Logger logger = LoggerFactory.getLogger(DependencyScannerFactory.class);

    public static DependencyScanner createScanner(Path projectPath) {
        if (Files.exists(projectPath.resolve("pom.xml"))) {
            logger.info("Detected Maven project");
            return createMavenScanner();
        }
        
        if (Files.exists(projectPath.resolve("build.gradle")) ||
            Files.exists(projectPath.resolve("build.gradle.kts"))) {
            logger.info("Detected Gradle project");
            return new GradleDependencyScanner(projectPath);
        }
        
        throw new IllegalArgumentException(
            "No supported build files found in project directory: " + projectPath);
    }

    private static DependencyScanner createMavenScanner() {
        try {
            // Create settings builder
            org.apache.maven.settings.building.SettingsBuilder settingsBuilder = 
                new org.apache.maven.settings.building.DefaultSettingsBuilderFactory().newInstance();
            org.apache.maven.settings.building.SettingsBuildingRequest request = 
                new org.apache.maven.settings.building.DefaultSettingsBuildingRequest();
            
            // Set default settings locations
            Path userSettings = Path.of(System.getProperty("user.home"), ".m2", "settings.xml");
            if (Files.exists(userSettings)) {
                request.setUserSettingsFile(userSettings.toFile());
            }
            
            // Try different environment variables for Maven home
            Optional<Path> mavenHome = findMavenHome();
            if (mavenHome.isPresent()) {
                Path globalSettings = mavenHome.get().resolve("conf/settings.xml");
                if (Files.exists(globalSettings)) {
                    request.setGlobalSettingsFile(globalSettings.toFile());
                }
            }
            
            // Create Maven dependency scanner
            return new MavenDependencyScanner(settingsBuilder.build(request).getEffectiveSettings());
        } catch (Exception e) {
            logger.error("Failed to create Maven dependency scanner", e);
            throw new RuntimeException("Failed to create Maven dependency scanner", e);
        }
    }

    private static Optional<Path> findMavenHome() {
        // Try different environment variables
        String[] mavenHomeVars = {"M2_HOME", "MAVEN_HOME", "MVN_HOME"};
        
        for (String var : mavenHomeVars) {
            String path = System.getenv(var);
            if (path != null) {
                Path mavenPath = Path.of(path);
                if (Files.exists(mavenPath)) {
                    logger.debug("Found Maven home at {} from {}", mavenPath, var);
                    return Optional.of(mavenPath);
                }
            }
        }

        // Try to find Maven in common locations
        Path[] commonLocations = {
            Path.of("/usr/local/maven"),
            Path.of("/opt/maven"),
            Path.of("/usr/share/maven"),
            Path.of(System.getProperty("user.home"), "apache-maven")
        };

        for (Path location : commonLocations) {
            if (Files.exists(location)) {
                logger.debug("Found Maven home at common location: {}", location);
                return Optional.of(location);
            }
        }

        logger.warn("Could not find Maven home directory");
        return Optional.empty();
    }
} 