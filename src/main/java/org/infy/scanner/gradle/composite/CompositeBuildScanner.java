package org.infy.scanner.gradle.composite;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleProject;
import org.infy.scanner.core.Dependency;
import org.infy.scanner.gradle.GradleDependencyScanner;
import org.infy.scanner.gradle.GradleScanException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class CompositeBuildScanner {
    private static final Logger logger = LoggerFactory.getLogger(CompositeBuildScanner.class);
    private final GradleDependencyScanner scanner;

    public CompositeBuildScanner(GradleDependencyScanner scanner) {
        this.scanner = scanner;
    }

    public Set<Dependency> scanCompositeBuild(Path rootProjectPath) {
        Set<Dependency> allDependencies = new HashSet<>();
        
        try {
            // Scan the root project
            allDependencies.addAll(scanner.scanProject(rootProjectPath));

            // Scan settings.gradle for included builds
            Path settingsGradle = rootProjectPath.resolve("settings.gradle");
            Path settingsGradleKts = rootProjectPath.resolve("settings.gradle.kts");

            if (Files.exists(settingsGradle)) {
                scanIncludedBuilds(settingsGradle, rootProjectPath, allDependencies);
            } else if (Files.exists(settingsGradleKts)) {
                scanIncludedBuilds(settingsGradleKts, rootProjectPath, allDependencies);
            }

            return allDependencies;
        } catch (Exception e) {
            logger.error("Error scanning composite build at {}", rootProjectPath, e);
            throw new GradleScanException("Failed to scan composite build", e);
        }
    }

    private void scanIncludedBuilds(Path settingsFile, Path rootPath, Set<Dependency> dependencies) {
        try {
            String content = Files.readString(settingsFile);
            Set<Path> includedBuildPaths = findIncludedBuildPaths(content, rootPath);

            for (Path buildPath : includedBuildPaths) {
                if (Files.exists(buildPath)) {
                    logger.info("Scanning included build at: {}", buildPath);
                    dependencies.addAll(scanner.scanProject(buildPath));
                }
            }
        } catch (Exception e) {
            logger.error("Error scanning included builds from {}", settingsFile, e);
        }
    }

    private Set<Path> findIncludedBuildPaths(String settingsContent, Path rootPath) {
        Set<Path> paths = new HashSet<>();
        
        // Match includeBuild statements
        Stream.of(settingsContent.split("\n"))
            .map(String::trim)
            .filter(line -> line.startsWith("includeBuild"))
            .forEach(line -> {
                String pathStr = line.replaceAll("includeBuild\\s*['\"]([^'\"]+)['\"].*", "$1");
                Path buildPath = rootPath.resolve(pathStr).normalize();
                paths.add(buildPath);
            });

        return paths;
    }
} 