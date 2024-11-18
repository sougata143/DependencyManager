package org.infy.scanner.version.analysis;

import org.infy.scanner.core.Dependency;
import org.infy.scanner.version.CompatibilityMatrix;
import org.infy.scanner.version.UpgradeRecommendationEngine;
import org.infy.scanner.version.VersionStabilityChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class ImpactAnalysisEngine {
    private static final Logger logger = LoggerFactory.getLogger(ImpactAnalysisEngine.class);
    
    private final CompatibilityMatrix compatibilityMatrix;
    private final UpgradeRecommendationEngine recommendationEngine;
    private final VersionStabilityChecker stabilityChecker;
    private final DependencyGraph dependencyGraph;

    public ImpactAnalysisEngine(CompatibilityMatrix compatibilityMatrix,
                               UpgradeRecommendationEngine recommendationEngine) {
        this.compatibilityMatrix = compatibilityMatrix;
        this.recommendationEngine = recommendationEngine;
        this.stabilityChecker = new VersionStabilityChecker();
        this.dependencyGraph = new DependencyGraph();
    }

    public ImpactAnalysisResult analyzeUpgradeImpact(Set<Dependency> dependencies,
                                                    String moduleId,
                                                    String targetVersion) {
        // Build dependency graph
        buildDependencyGraph(dependencies);
        
        // Find current version
        String currentVersion = findCurrentVersion(dependencies, moduleId);
        if (currentVersion == null) {
            throw new IllegalArgumentException("Module not found: " + moduleId);
        }

        // Analyze direct impacts
        Set<DirectImpact> directImpacts = analyzeDirectImpacts(moduleId, currentVersion, targetVersion);
        
        // Analyze transitive impacts
        Set<TransitiveImpact> transitiveImpacts = 
            analyzeTransitiveImpacts(moduleId, currentVersion, targetVersion);
        
        // Generate upgrade paths
        List<UpgradePath> upgradePaths = generateUpgradePaths(moduleId, currentVersion, targetVersion);
        
        // Calculate overall impact metrics
        ImpactMetrics metrics = calculateImpactMetrics(directImpacts, transitiveImpacts);

        return new ImpactAnalysisResult(
            moduleId,
            currentVersion,
            targetVersion,
            directImpacts,
            transitiveImpacts,
            upgradePaths,
            metrics,
            generateRecommendations(moduleId, currentVersion, targetVersion)
        );
    }

    private void buildDependencyGraph(Set<Dependency> dependencies) {
        dependencies.forEach(dep -> dependencyGraph.addDependency(dep));
        dependencies.forEach(dep -> {
            Set<Dependency> transitives = findTransitiveDependencies(dep);
            transitives.forEach(trans -> 
                dependencyGraph.addDependencyRelation(dep, trans));
        });
    }

    private Set<Dependency> findTransitiveDependencies(Dependency dependency) {
        // This would be implemented based on your dependency resolution logic
        return new HashSet<>();
    }

    private String findCurrentVersion(Set<Dependency> dependencies, String moduleId) {
        return dependencies.stream()
            .filter(d -> getModuleId(d).equals(moduleId))
            .map(Dependency::version)
            .findFirst()
            .orElse(null);
    }

    private Set<DirectImpact> analyzeDirectImpacts(String moduleId, 
                                                  String currentVersion,
                                                  String targetVersion) {
        Set<DirectImpact> impacts = new HashSet<>();
        Set<Dependency> dependents = dependencyGraph.getDependents(moduleId);

        for (Dependency dependent : dependents) {
            CompatibilityMatrix.CompatibilityResult compatibility = 
                compatibilityMatrix.getCompatibility(moduleId, currentVersion, targetVersion);
            
            impacts.add(new DirectImpact(
                dependent,
                calculateImpactLevel(compatibility),
                calculateRiskLevel(compatibility),
                generateImpactDescription(dependent, compatibility)
            ));
        }

        return impacts;
    }

    private Set<TransitiveImpact> analyzeTransitiveImpacts(String moduleId,
                                                          String currentVersion,
                                                          String targetVersion) {
        Set<TransitiveImpact> impacts = new HashSet<>();
        Set<DependencyGraph.DependencyChain> chains = dependencyGraph.findTransitiveDependencyChains(moduleId);

        for (DependencyGraph.DependencyChain chain : chains) {
            impacts.add(analyzeChainImpact(chain, moduleId, currentVersion, targetVersion));
        }

        return impacts;
    }

    private TransitiveImpact analyzeChainImpact(DependencyGraph.DependencyChain chain,
                                               String moduleId,
                                               String currentVersion,
                                               String targetVersion) {
        List<ChainLink> impactChain = new ArrayList<>();
        ImpactLevel overallImpact = ImpactLevel.NONE;
        RiskLevel overallRisk = RiskLevel.LOW;

        List<Dependency> dependencies = chain.dependencies();
        for (int i = 0; i < dependencies.size() - 1; i++) {
            Dependency current = dependencies.get(i);
            Dependency next = dependencies.get(i + 1);

            CompatibilityMatrix.CompatibilityResult compatibility = 
                compatibilityMatrix.getCompatibility(
                    getModuleId(current),
                    current.version(),
                    targetVersion
                );

            ChainLink link = new ChainLink(
                current,
                next,
                calculateImpactLevel(compatibility),
                calculateRiskLevel(compatibility)
            );
            impactChain.add(link);

            // Update overall impact and risk
            overallImpact = max(overallImpact, link.impactLevel());
            overallRisk = max(overallRisk, link.riskLevel());
        }

        return new TransitiveImpact(
            dependencies.get(0),
            dependencies.get(dependencies.size() - 1),
            impactChain,
            overallImpact,
            overallRisk
        );
    }

    private List<UpgradePath> generateUpgradePaths(String moduleId,
                                                  String currentVersion,
                                                  String targetVersion) {
        Set<String> availableVersions = compatibilityMatrix.getAvailableVersions(moduleId);
        List<UpgradePath> paths = new ArrayList<>();

        // Find all possible paths using DFS
        findUpgradePaths(
            moduleId,
            currentVersion,
            targetVersion,
            availableVersions,
            new ArrayList<>(),
            new HashSet<>(),
            paths
        );

        // Sort paths by risk and length
        paths.sort(Comparator
            .comparing(UpgradePath::overallRisk)
            .thenComparing(p -> p.steps().size()));

        return paths;
    }

    private void findUpgradePaths(String moduleId,
                                 String currentVersion,
                                 String targetVersion,
                                 Set<String> availableVersions,
                                 List<UpgradeStep> currentPath,
                                 Set<String> visited,
                                 List<UpgradePath> paths) {
        if (currentVersion.equals(targetVersion)) {
            if (!currentPath.isEmpty()) {
                paths.add(createUpgradePath(currentPath));
            }
            return;
        }

        if (!visited.add(currentVersion)) {
            return;
        }

        for (String nextVersion : findNextVersions(moduleId, currentVersion, availableVersions)) {
            CompatibilityMatrix.CompatibilityResult compatibility = 
                compatibilityMatrix.getCompatibility(moduleId, currentVersion, nextVersion);
            
            if (compatibility != null) {
                UpgradeStep step = new UpgradeStep(
                    currentVersion,
                    nextVersion,
                    calculateImpactLevel(compatibility),
                    calculateRiskLevel(compatibility),
                    compatibility.notes()
                );
                
                currentPath.add(step);
                findUpgradePaths(moduleId, nextVersion, targetVersion, availableVersions, 
                    currentPath, visited, paths);
                currentPath.remove(currentPath.size() - 1);
            }
        }

        visited.remove(currentVersion);
    }

    private Set<String> findNextVersions(String moduleId,
                                       String currentVersion,
                                       Set<String> availableVersions) {
        return availableVersions.stream()
            .filter(v -> isValidNextVersion(currentVersion, v))
            .collect(Collectors.toSet());
    }

    private boolean isValidNextVersion(String currentVersion, String nextVersion) {
        // Implement version comparison logic
        return true;
    }

    private UpgradePath createUpgradePath(List<UpgradeStep> steps) {
        ImpactLevel overallImpact = steps.stream()
            .map(UpgradeStep::impactLevel)
            .max(Comparator.naturalOrder())
            .orElse(ImpactLevel.NONE);

        RiskLevel overallRisk = steps.stream()
            .map(UpgradeStep::riskLevel)
            .max(Comparator.naturalOrder())
            .orElse(RiskLevel.LOW);

        return new UpgradePath(
            steps.get(0).fromVersion(),
            steps.get(steps.size() - 1).toVersion(),
            new ArrayList<>(steps),
            overallImpact,
            overallRisk
        );
    }

    private ImpactMetrics calculateImpactMetrics(Set<DirectImpact> directImpacts,
                                               Set<TransitiveImpact> transitiveImpacts) {
        int totalImpactedModules = directImpacts.size() + transitiveImpacts.size();
        int highRiskCount = countHighRiskImpacts(directImpacts, transitiveImpacts);
        double riskScore = calculateRiskScore(directImpacts, transitiveImpacts);
        int breakingChanges = countBreakingChanges(directImpacts, transitiveImpacts);

        return new ImpactMetrics(
            totalImpactedModules,
            highRiskCount,
            riskScore,
            breakingChanges
        );
    }

    private List<String> generateRecommendations(String moduleId,
                                               String currentVersion,
                                               String targetVersion) {
        List<String> recommendations = new ArrayList<>();
        
        // Add version-specific recommendations
        CompatibilityMatrix.CompatibilityResult compatibility = 
            compatibilityMatrix.getCompatibility(moduleId, currentVersion, targetVersion);
        
        if (compatibility != null) {
            recommendations.addAll(compatibility.notes());
        }

        // Add stability recommendations
        if (!stabilityChecker.isStable(targetVersion)) {
            recommendations.add("Target version is not stable - consider waiting for a stable release");
        }

        // Add impact-based recommendations
        ImpactLevel impactLevel = calculateImpactLevel(compatibility);
        if (impactLevel == ImpactLevel.HIGH) {
            recommendations.add("High impact upgrade - comprehensive testing recommended");
        }

        return recommendations;
    }

    private String getModuleId(Dependency dependency) {
        return dependency.groupId() + ":" + dependency.artifactId();
    }

    private ImpactLevel calculateImpactLevel(CompatibilityMatrix.CompatibilityResult compatibility) {
        if (compatibility == null) {
            return ImpactLevel.UNKNOWN;
        }

        return switch (compatibility.level()) {
            case PATCH_COMPATIBLE -> ImpactLevel.LOW;
            case MINOR_COMPATIBLE -> ImpactLevel.MEDIUM;
            case INCOMPATIBLE -> ImpactLevel.HIGH;
            default -> ImpactLevel.UNKNOWN;
        };
    }

    private RiskLevel calculateRiskLevel(CompatibilityMatrix.CompatibilityResult compatibility) {
        if (compatibility == null) {
            return RiskLevel.HIGH;
        }

        return switch (compatibility.level()) {
            case PATCH_COMPATIBLE -> RiskLevel.LOW;
            case MINOR_COMPATIBLE -> RiskLevel.MEDIUM;
            case INCOMPATIBLE -> RiskLevel.HIGH;
            default -> RiskLevel.UNKNOWN;
        };
    }

    private String generateImpactDescription(Dependency dependency,
                                           CompatibilityMatrix.CompatibilityResult compatibility) {
        if (compatibility == null) {
            return "Unknown compatibility impact";
        }

        return String.join("\n", compatibility.notes());
    }

    private <T extends Enum<T>> T max(T a, T b) {
        return a.ordinal() > b.ordinal() ? a : b;
    }

    private int countHighRiskImpacts(Set<DirectImpact> directImpacts,
                                   Set<TransitiveImpact> transitiveImpacts) {
        return (int) (
            directImpacts.stream().filter(i -> i.riskLevel() == RiskLevel.HIGH).count() +
            transitiveImpacts.stream().filter(i -> i.overallRisk() == RiskLevel.HIGH).count()
        );
    }

    private double calculateRiskScore(Set<DirectImpact> directImpacts,
                                    Set<TransitiveImpact> transitiveImpacts) {
        double directScore = calculateDirectRiskScore(directImpacts);
        double transitiveScore = calculateTransitiveRiskScore(transitiveImpacts);
        return (directScore + transitiveScore) / 2.0;
    }

    private int countBreakingChanges(Set<DirectImpact> directImpacts,
                                   Set<TransitiveImpact> transitiveImpacts) {
        return (int) (
            directImpacts.stream().filter(i -> i.impactLevel() == ImpactLevel.HIGH).count() +
            transitiveImpacts.stream().filter(i -> i.overallImpact() == ImpactLevel.HIGH).count()
        );
    }

    private double calculateDirectRiskScore(Set<DirectImpact> impacts) {
        if (impacts.isEmpty()) return 0.0;
        return impacts.stream()
            .mapToDouble(i -> getRiskValue(i.riskLevel()))
            .average()
            .orElse(0.0);
    }

    private double calculateTransitiveRiskScore(Set<TransitiveImpact> impacts) {
        if (impacts.isEmpty()) return 0.0;
        return impacts.stream()
            .mapToDouble(i -> getRiskValue(i.overallRisk()))
            .average()
            .orElse(0.0);
    }

    private double getRiskValue(RiskLevel risk) {
        return switch (risk) {
            case LOW -> 0.25;
            case MEDIUM -> 0.5;
            case HIGH -> 1.0;
            case UNKNOWN -> 0.75;
        };
    }

    // Data classes
    public record ImpactAnalysisResult(
        String moduleId,
        String currentVersion,
        String targetVersion,
        Set<DirectImpact> directImpacts,
        Set<TransitiveImpact> transitiveImpacts,
        List<UpgradePath> upgradePaths,
        ImpactMetrics metrics,
        List<String> recommendations
    ) {}

    public record DirectImpact(
        Dependency dependency,
        ImpactLevel impactLevel,
        RiskLevel riskLevel,
        String description
    ) {}

    public record TransitiveImpact(
        Dependency source,
        Dependency target,
        List<ChainLink> impactChain,
        ImpactLevel overallImpact,
        RiskLevel overallRisk
    ) {}

    public record ChainLink(
        Dependency from,
        Dependency to,
        ImpactLevel impactLevel,
        RiskLevel riskLevel
    ) {}

    public record UpgradePath(
        String fromVersion,
        String toVersion,
        List<UpgradeStep> steps,
        ImpactLevel overallImpact,
        RiskLevel overallRisk
    ) {}

    public record UpgradeStep(
        String fromVersion,
        String toVersion,
        ImpactLevel impactLevel,
        RiskLevel riskLevel,
        List<String> notes
    ) {}

    public record ImpactMetrics(
        int totalImpactedModules,
        int highRiskCount,
        double riskScore,
        int breakingChanges
    ) {}

    public record DependencyChain(List<Dependency> dependencies) {}

    public enum ImpactLevel {
        NONE,
        LOW,
        MEDIUM,
        HIGH,
        UNKNOWN
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        UNKNOWN
    }
} 