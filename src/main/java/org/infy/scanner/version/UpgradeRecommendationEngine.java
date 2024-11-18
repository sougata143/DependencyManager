package org.infy.scanner.version;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class UpgradeRecommendationEngine {
    private static final Logger logger = LoggerFactory.getLogger(UpgradeRecommendationEngine.class);
    
    private final CompatibilityMatrix compatibilityMatrix;
    private final VersionStabilityChecker stabilityChecker;
    private final Set<String> excludedGroups;
    private final boolean preferStableVersions;
    private final boolean allowMajorUpgrades;

    public UpgradeRecommendationEngine(CompatibilityMatrix compatibilityMatrix,
                                     boolean preferStableVersions,
                                     boolean allowMajorUpgrades) {
        this.compatibilityMatrix = compatibilityMatrix;
        this.stabilityChecker = new VersionStabilityChecker();
        this.excludedGroups = new HashSet<>();
        this.preferStableVersions = preferStableVersions;
        this.allowMajorUpgrades = allowMajorUpgrades;
    }

    public Set<UpgradeRecommendation> generateRecommendations(Set<Dependency> dependencies) {
        Set<UpgradeRecommendation> recommendations = new HashSet<>();
        Map<String, Set<Dependency>> moduleGroups = groupDependencies(dependencies);

        for (Map.Entry<String, Set<Dependency>> entry : moduleGroups.entrySet()) {
            String moduleId = entry.getKey();
            Set<Dependency> moduleDeps = entry.getValue();
            
            if (shouldExcludeModule(moduleId)) {
                continue;
            }

            Set<String> availableVersions = compatibilityMatrix.getAvailableVersions(moduleId);
            if (availableVersions.isEmpty()) {
                continue;
            }

            for (Dependency dependency : moduleDeps) {
                Optional<UpgradeRecommendation> recommendation = 
                    analyzeUpgradePath(dependency, availableVersions);
                recommendation.ifPresent(recommendations::add);
            }
        }

        return prioritizeRecommendations(recommendations);
    }

    private Optional<UpgradeRecommendation> analyzeUpgradePath(
        Dependency dependency,
        Set<String> availableVersions
    ) {
        String currentVersion = dependency.version();
        List<String> upgradePath = findBestUpgradePath(currentVersion, availableVersions);
        
        if (upgradePath.isEmpty() || upgradePath.get(upgradePath.size() - 1).equals(currentVersion)) {
            return Optional.empty();
        }

        String targetVersion = upgradePath.get(upgradePath.size() - 1);
        CompatibilityMatrix.CompatibilityResult compatibility = 
            compatibilityMatrix.getCompatibility(
                getModuleId(dependency),
                currentVersion,
                targetVersion
            );

        if (compatibility == null) {
            return Optional.empty();
        }

        return Optional.of(new UpgradeRecommendation(
            dependency,
            targetVersion,
            compatibility.level(),
            calculateRiskLevel(currentVersion, targetVersion),
            calculatePriority(dependency, targetVersion, compatibility),
            upgradePath,
            generateRecommendationNotes(dependency, targetVersion, compatibility)
        ));
    }

    private List<String> findBestUpgradePath(String currentVersion, Set<String> availableVersions) {
        return availableVersions.stream()
            .filter(version -> isValidUpgradeTarget(currentVersion, version))
            .sorted((v1, v2) -> compareVersionsForUpgrade(v1, v2, currentVersion))
            .collect(Collectors.toList());
    }

    private boolean isValidUpgradeTarget(String currentVersion, String targetVersion) {
        SemanticVersion current = SemanticVersion.parse(currentVersion);
        SemanticVersion target = SemanticVersion.parse(targetVersion);
        
        if (current == null || target == null) {
            return false;
        }

        if (!allowMajorUpgrades && target.getMajor() > current.getMajor()) {
            return false;
        }

        if (preferStableVersions && !stabilityChecker.isStable(targetVersion)) {
            return false;
        }

        return target.compareTo(current) > 0;
    }

    private int compareVersionsForUpgrade(String v1, String v2, String currentVersion) {
        SemanticVersion sv1 = SemanticVersion.parse(v1);
        SemanticVersion sv2 = SemanticVersion.parse(v2);
        SemanticVersion current = SemanticVersion.parse(currentVersion);
        
        if (sv1 == null || sv2 == null || current == null) {
            return 0;
        }

        // Compare stability if preference is set
        if (preferStableVersions) {
            boolean isStable1 = stabilityChecker.isStable(v1);
            boolean isStable2 = stabilityChecker.isStable(v2);
            if (isStable1 != isStable2) {
                return isStable1 ? -1 : 1;
            }
        }

        // Prefer smaller version jumps
        int distance1 = calculateVersionDistance(current, sv1);
        int distance2 = calculateVersionDistance(current, sv2);
        return Integer.compare(distance1, distance2);
    }

    private int calculateVersionDistance(SemanticVersion v1, SemanticVersion v2) {
        return Math.abs(v2.getMajor() - v1.getMajor()) * 10000 +
               Math.abs(v2.getMinor() - v1.getMinor()) * 100 +
               Math.abs(v2.getPatch() - v1.getPatch());
    }

    private RiskLevel calculateRiskLevel(String currentVersion, String targetVersion) {
        SemanticVersion current = SemanticVersion.parse(currentVersion);
        SemanticVersion target = SemanticVersion.parse(targetVersion);
        
        if (current == null || target == null) {
            return RiskLevel.HIGH;
        }

        if (target.getMajor() > current.getMajor()) {
            return RiskLevel.HIGH;
        }
        if (target.getMinor() > current.getMinor()) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private int calculatePriority(
        Dependency dependency,
        String targetVersion,
        CompatibilityMatrix.CompatibilityResult compatibility
    ) {
        int priority = 0;

        // Direct dependencies get higher priority
        if (dependency.isDirectDependency()) {
            priority += 1000;
        }

        // Consider compatibility level
        priority += switch (compatibility.level()) {
            case PATCH_COMPATIBLE -> 500;
            case MINOR_COMPATIBLE -> 300;
            case IDENTICAL -> 100;
            case INCOMPATIBLE -> -500;
            case UNKNOWN -> 0;
        };

        // Consider risk level
        priority += switch (calculateRiskLevel(dependency.version(), targetVersion)) {
            case LOW -> 200;
            case MEDIUM -> 100;
            case HIGH -> -100;
        };

        return priority;
    }

    private List<String> generateRecommendationNotes(
        Dependency dependency,
        String targetVersion,
        CompatibilityMatrix.CompatibilityResult compatibility
    ) {
        List<String> notes = new ArrayList<>(compatibility.notes());
        
        SemanticVersion current = SemanticVersion.parse(dependency.version());
        SemanticVersion target = SemanticVersion.parse(targetVersion);
        
        if (current != null && target != null) {
            if (target.getMajor() > current.getMajor()) {
                notes.add("Major version upgrade - review breaking changes carefully");
            }
            if (!stabilityChecker.isStable(targetVersion)) {
                notes.add("Target version is not stable - consider waiting for stable release");
            }
        }

        return notes;
    }

    private Set<UpgradeRecommendation> prioritizeRecommendations(
        Set<UpgradeRecommendation> recommendations
    ) {
        return recommendations.stream()
            .sorted(Comparator
                .comparingInt(UpgradeRecommendation::priority)
                .reversed())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String getModuleId(Dependency dependency) {
        return dependency.groupId() + ":" + dependency.artifactId();
    }

    private Map<String, Set<Dependency>> groupDependencies(Set<Dependency> dependencies) {
        return dependencies.stream()
            .collect(Collectors.groupingBy(
                this::getModuleId,
                Collectors.toSet()
            ));
    }

    private boolean shouldExcludeModule(String moduleId) {
        return excludedGroups.stream().anyMatch(moduleId::startsWith);
    }

    public void excludeGroup(String groupId) {
        excludedGroups.add(groupId);
    }

    public record UpgradeRecommendation(
        Dependency dependency,
        String recommendedVersion,
        CompatibilityMatrix.CompatibilityLevel compatibilityLevel,
        RiskLevel riskLevel,
        int priority,
        List<String> upgradePath,
        List<String> notes
    ) {}

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }
} 