package org.infy.scanner.version;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class CompatibilityMatrix {
    private static final Logger logger = LoggerFactory.getLogger(CompatibilityMatrix.class);
    
    private final Map<String, Set<String>> availableVersions = new HashMap<>();
    private final Map<String, Map<String, CompatibilityResult>> matrix = new HashMap<>();
    private final VersionStabilityChecker stabilityChecker;

    public CompatibilityMatrix() {
        this.stabilityChecker = new VersionStabilityChecker();
    }

    public void analyzeCompatibility(Set<Dependency> dependencies) {
        // Group dependencies by module ID
        Map<String, Set<Dependency>> moduleGroups = dependencies.stream()
            .collect(Collectors.groupingBy(
                this::getModuleId,
                Collectors.toSet()
            ));

        // Build available versions map
        moduleGroups.forEach((moduleId, deps) -> {
            availableVersions.put(moduleId, deps.stream()
                .map(Dependency::version)
                .collect(Collectors.toSet()));
        });

        // Build compatibility matrix
        for (String moduleId : moduleGroups.keySet()) {
            matrix.put(moduleId, new HashMap<>());
            Set<String> versions = availableVersions.get(moduleId);
            
            for (String version1 : versions) {
                Map<String, CompatibilityResult> versionMatrix = matrix.get(moduleId);
                for (String version2 : versions) {
                    if (!versionMatrix.containsKey(version2)) {
                        CompatibilityResult result = analyzeVersionCompatibility(version1, version2);
                        versionMatrix.put(version2, result);
                    }
                }
            }
        }
    }

    private CompatibilityResult analyzeVersionCompatibility(String version1, String version2) {
        SemanticVersion sv1 = SemanticVersion.parse(version1);
        SemanticVersion sv2 = SemanticVersion.parse(version2);

        if (sv1 == null || sv2 == null) {
            return new CompatibilityResult(
                CompatibilityLevel.UNKNOWN,
                "Non-semantic version comparison",
                Collections.emptyList()
            );
        }

        List<String> notes = new ArrayList<>();
        CompatibilityLevel level;

        // Check version stability
        VersionStabilityChecker.StabilityLevel stability1 = stabilityChecker.checkStability(version1);
        VersionStabilityChecker.StabilityLevel stability2 = stabilityChecker.checkStability(version2);
        notes.add(String.format("Stability: %s -> %s", stability1, stability2));

        // Determine compatibility level
        if (sv1.getMajor() != sv2.getMajor()) {
            level = CompatibilityLevel.INCOMPATIBLE;
            notes.add("Major version difference - breaking changes expected");
        } else if (sv1.getMajor() == 0) {
            if (sv1.getMinor() != sv2.getMinor()) {
                level = CompatibilityLevel.INCOMPATIBLE;
                notes.add("Development version with minor changes - breaking changes possible");
            } else {
                level = CompatibilityLevel.PATCH_COMPATIBLE;
                notes.add("Development version with patch changes only");
            }
        } else if (sv1.getMinor() != sv2.getMinor()) {
            level = CompatibilityLevel.MINOR_COMPATIBLE;
            notes.add("Minor version changes - new features added");
        } else if (sv1.getPatch() != sv2.getPatch()) {
            level = CompatibilityLevel.PATCH_COMPATIBLE;
            notes.add("Patch version changes - bug fixes only");
        } else {
            level = CompatibilityLevel.IDENTICAL;
            notes.add("Identical versions");
        }

        return new CompatibilityResult(level, generateSummary(level), notes);
    }

    private String generateSummary(CompatibilityLevel level) {
        return switch (level) {
            case IDENTICAL -> "Versions are identical";
            case PATCH_COMPATIBLE -> "Patch level changes only - safe to upgrade";
            case MINOR_COMPATIBLE -> "Minor version changes - review new features";
            case INCOMPATIBLE -> "Breaking changes expected - major version difference";
            case UNKNOWN -> "Unable to determine compatibility";
        };
    }

    public CompatibilityResult getCompatibility(String moduleId, String version1, String version2) {
        Map<String, CompatibilityResult> versionMatrix = matrix.get(moduleId);
        if (versionMatrix == null) {
            return null;
        }
        return versionMatrix.get(version2);
    }

    public Set<String> getAvailableVersions(String moduleId) {
        return new HashSet<>(availableVersions.getOrDefault(moduleId, Collections.emptySet()));
    }

    public Set<String> getModuleIds() {
        return new HashSet<>(matrix.keySet());
    }

    private String getModuleId(Dependency dependency) {
        return dependency.groupId() + ":" + dependency.artifactId();
    }

    public record CompatibilityResult(
        CompatibilityLevel level,
        String summary,
        List<String> notes
    ) {}

    public enum CompatibilityLevel {
        IDENTICAL,
        PATCH_COMPATIBLE,
        MINOR_COMPATIBLE,
        INCOMPATIBLE,
        UNKNOWN
    }

    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("Dependency Compatibility Matrix Report\n");
        report.append("=====================================\n\n");

        for (String moduleId : getModuleIds()) {
            report.append(String.format("Module: %s\n", moduleId));
            report.append("Available versions: ")
                  .append(String.join(", ", getAvailableVersions(moduleId)))
                  .append("\n\n");

            report.append("Compatibility Matrix:\n");
            Set<String> versions = getAvailableVersions(moduleId);
            
            // Header row
            report.append(String.format("%-15s", ""));
            for (String version : versions) {
                report.append(String.format("%-15s", version));
            }
            report.append("\n");

            // Matrix rows
            for (String version1 : versions) {
                report.append(String.format("%-15s", version1));
                for (String version2 : versions) {
                    CompatibilityResult result = getCompatibility(moduleId, version1, version2);
                    String cell = result != null ? result.level().toString() : "N/A";
                    report.append(String.format("%-15s", cell));
                }
                report.append("\n");
            }
            report.append("\n");
        }

        return report.toString();
    }
} 