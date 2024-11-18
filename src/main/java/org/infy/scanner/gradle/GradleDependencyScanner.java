package org.infy.scanner.gradle;

import org.infy.scanner.core.Dependency;
import org.infy.scanner.core.DependencyScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GradleDependencyScanner implements DependencyScanner {
    private static final Logger logger = LoggerFactory.getLogger(GradleDependencyScanner.class);

    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
        "(?:implementation|api|compileOnly|runtimeOnly|testImplementation|testCompileOnly|testRuntimeOnly)\\s*(?:platform\\s*)?['\"]([^'\"]+)['\"]"
    );
    
    private static final Pattern GROUP_ARTIFACT_VERSION_PATTERN = Pattern.compile(
        "([^:]+):([^:]+)(?::([^:@]+))?(?:@([^:]+))?"
    );

    private static final Pattern CATALOG_REFERENCE_PATTERN = Pattern.compile(
        "libs\\.([^.]+)(?:\\.([^.]+))?"
    );

    private final VersionCatalogHandler versionCatalogHandler;
    private final Map<String, Set<String>> transitiveCache = new HashMap<>();

    public GradleDependencyScanner(Path projectPath) {
        this.versionCatalogHandler = new VersionCatalogHandler(projectPath);
    }

    @Override
    public Set<Dependency> scanProject(Path projectPath) {
        logger.info("Scanning Gradle project at: {}", projectPath);
        Set<Dependency> dependencies = new HashSet<>();

        try {
            // Scan build.gradle
            Path buildGradle = projectPath.resolve("build.gradle");
            if (Files.exists(buildGradle)) {
                scanBuildFile(buildGradle, dependencies);
            }

            // Scan build.gradle.kts
            Path buildGradleKts = projectPath.resolve("build.gradle.kts");
            if (Files.exists(buildGradleKts)) {
                scanBuildFile(buildGradleKts, dependencies);
            }

            // Scan settings.gradle for included builds
            Path settingsGradle = projectPath.resolve("settings.gradle");
            if (Files.exists(settingsGradle)) {
                scanIncludedBuilds(settingsGradle, projectPath, dependencies);
            }

            return dependencies;
        } catch (Exception e) {
            logger.error("Error scanning Gradle project", e);
            throw new GradleScanException("Failed to scan Gradle project", e);
        }
    }

    private void scanBuildFile(Path buildFile, Set<Dependency> dependencies) {
        try (BufferedReader reader = new BufferedReader(new FileReader(buildFile.toFile()))) {
            String line;
            boolean inDependenciesBlock = false;
            StringBuilder multiLineBuffer = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("dependencies {")) {
                    inDependenciesBlock = true;
                    continue;
                }

                if (inDependenciesBlock) {
                    if (line.equals("}")) {
                        inDependenciesBlock = false;
                        continue;
                    }

                    // Handle multi-line dependencies
                    if (line.endsWith("\\")) {
                        multiLineBuffer.append(line, 0, line.length() - 1);
                        continue;
                    } else if (!multiLineBuffer.isEmpty()) {
                        line = multiLineBuffer.append(line).toString();
                        multiLineBuffer.setLength(0);
                    }

                    Matcher matcher = DEPENDENCY_PATTERN.matcher(line);
                    if (matcher.find()) {
                        String dependencyNotation = matcher.group(1);
                        parseDependency(dependencyNotation, dependencies);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error scanning build file: {}", buildFile, e);
        }
    }

    private void parseDependency(String dependencyNotation, Set<Dependency> dependencies) {
        // Check for version catalog reference
        Matcher catalogMatcher = CATALOG_REFERENCE_PATTERN.matcher(dependencyNotation);
        if (catalogMatcher.matches()) {
            String alias = catalogMatcher.group(1);
            String submodule = catalogMatcher.group(2);
            
            VersionCatalogHandler.DependencyAlias dependencyAlias = 
                versionCatalogHandler.resolveDependencyAlias(
                    submodule != null ? alias + "." + submodule : alias
                );
            
            if (dependencyAlias != null) {
                dependencies.add(new Dependency(
                    dependencyAlias.group(),
                    dependencyAlias.name(),
                    dependencyAlias.version(),
                    "implementation",
                    true
                ));
                return;
            }
        }

        // Parse standard dependency notation
        Matcher matcher = GROUP_ARTIFACT_VERSION_PATTERN.matcher(dependencyNotation);
        if (matcher.matches()) {
            String group = matcher.group(1);
            String artifact = matcher.group(2);
            String version = matcher.group(3);
            String classifier = matcher.group(4);

            // Resolve version from catalog if it starts with $
            if (version != null && version.startsWith("$")) {
                version = versionCatalogHandler.resolveVersion(version);
            }

            dependencies.add(new Dependency(
                group,
                artifact,
                version != null ? version : "latest.release",
                "implementation",
                true
            ));
        }
    }

    private void scanIncludedBuilds(Path settingsFile, Path rootPath, Set<Dependency> dependencies) {
        try {
            String content = Files.readString(settingsFile);
            Set<Path> includedBuildPaths = findIncludedBuildPaths(content, rootPath);

            for (Path buildPath : includedBuildPaths) {
                if (Files.exists(buildPath)) {
                    logger.info("Scanning included build at: {}", buildPath);
                    dependencies.addAll(scanProject(buildPath));
                }
            }
        } catch (Exception e) {
            logger.error("Error scanning included builds from {}", settingsFile, e);
        }
    }

    private Set<Path> findIncludedBuildPaths(String settingsContent, Path rootPath) {
        Set<Path> paths = new HashSet<>();
        Pattern includePattern = Pattern.compile("includeBuild\\s*['\"]([^'\"]+)['\"]");
        
        Matcher matcher = includePattern.matcher(settingsContent);
        while (matcher.find()) {
            String pathStr = matcher.group(1);
            Path buildPath = rootPath.resolve(pathStr).normalize();
            paths.add(buildPath);
        }

        return paths;
    }
} 