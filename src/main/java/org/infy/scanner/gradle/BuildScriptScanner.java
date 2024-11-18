package org.infy.scanner.gradle;

import org.infy.scanner.core.Dependency;
import org.infy.scanner.gradle.model.GradleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildScriptScanner {
    private static final Logger logger = LoggerFactory.getLogger(BuildScriptScanner.class);
    
    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
        "(?m)^\\s*(\\w+)\\s*[(']([^'\"]+)['\")]\\s*(?://.*)?$"
    );
    
    private static final Pattern CUSTOM_CONFIGURATION_PATTERN = Pattern.compile(
        "configurations\\s*\\{\\s*([^}]+)\\s*\\}"
    );
    
    private static final Pattern PLATFORM_PATTERN = Pattern.compile(
        "platform\\s*[(']([^'\"]+)['\")]"
    );

    private final VersionCatalogHandler versionCatalogHandler;

    public BuildScriptScanner(VersionCatalogHandler versionCatalogHandler) {
        this.versionCatalogHandler = versionCatalogHandler;
    }

    public Set<Dependency> scanBuildScript(Path projectPath) {
        Set<Dependency> dependencies = new HashSet<>();
        
        try {
            // Scan build.gradle
            Path buildGradle = projectPath.resolve("build.gradle");
            if (Files.exists(buildGradle)) {
                scanFile(buildGradle, dependencies);
            }

            // Scan build.gradle.kts
            Path buildGradleKts = projectPath.resolve("build.gradle.kts");
            if (Files.exists(buildGradleKts)) {
                scanFile(buildGradleKts, dependencies);
            }

            // Scan settings.gradle
            Path settingsGradle = projectPath.resolve("settings.gradle");
            if (Files.exists(settingsGradle)) {
                scanFile(settingsGradle, dependencies);
            }

        } catch (Exception e) {
            logger.error("Error scanning build scripts", e);
        }

        return dependencies;
    }

    private void scanFile(Path file, Set<Dependency> dependencies) throws Exception {
        String content = Files.readString(file);

        // Scan for custom configurations
        scanCustomConfigurations(content);

        // Scan for dependencies
        scanDependencies(content, dependencies);

        // Scan for platforms
        scanPlatforms(content, dependencies);
    }

    private void scanCustomConfigurations(String content) {
        Matcher matcher = CUSTOM_CONFIGURATION_PATTERN.matcher(content);
        if (matcher.find()) {
            String configurationsBlock = matcher.group(1);
            Pattern configPattern = Pattern.compile("(\\w+)\\s*\\{\\s*extendsFrom\\s+(\\w+)\\s*\\}");
            Matcher configMatcher = configPattern.matcher(configurationsBlock);
            
            while (configMatcher.find()) {
                String name = configMatcher.group(1);
                String extendsFrom = configMatcher.group(2);
                GradleConfiguration.registerCustomConfiguration(name, extendsFrom);
            }
        }
    }

    private void scanDependencies(String content, Set<Dependency> dependencies) {
        Matcher matcher = DEPENDENCY_PATTERN.matcher(content);
        while (matcher.find()) {
            String configuration = matcher.group(1);
            String dependencyNotation = matcher.group(2);
            
            try {
                Dependency dependency = parseDependencyNotation(dependencyNotation, configuration);
                if (dependency != null) {
                    dependencies.add(dependency);
                }
            } catch (Exception e) {
                logger.warn("Failed to parse dependency: {}", dependencyNotation, e);
            }
        }
    }

    private void scanPlatforms(String content, Set<Dependency> dependencies) {
        Matcher matcher = PLATFORM_PATTERN.matcher(content);
        while (matcher.find()) {
            String platformNotation = matcher.group(1);
            try {
                Dependency platform = parseDependencyNotation(platformNotation, "platform");
                if (platform != null) {
                    dependencies.add(platform);
                }
            } catch (Exception e) {
                logger.warn("Failed to parse platform dependency: {}", platformNotation, e);
            }
        }
    }

    private Dependency parseDependencyNotation(String notation, String configuration) {
        // Handle version catalog references
        if (notation.startsWith("libs.")) {
            String alias = notation.substring(5);
            VersionCatalogHandler.DependencyAlias dependencyAlias = 
                versionCatalogHandler.resolveDependencyAlias(alias);
            if (dependencyAlias != null) {
                return new Dependency(
                    dependencyAlias.group(),
                    dependencyAlias.name(),
                    dependencyAlias.version(),
                    configuration,
                    true
                );
            }
            return null;
        }

        // Handle standard notation (group:name:version)
        String[] parts = notation.split(":");
        if (parts.length >= 3) {
            String version = parts[2];
            if (version.startsWith("$")) {
                version = versionCatalogHandler.resolveVersion(version);
            }
            return new Dependency(
                parts[0],
                parts[1],
                version,
                configuration,
                true
            );
        }

        return null;
    }
} 