package org.infy.scanner.version.visualization;

import org.infy.scanner.core.Dependency;
import org.infy.scanner.version.CompatibilityMatrix;
import org.infy.scanner.version.UpgradeRecommendationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class UpgradeImpactAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(UpgradeImpactAnalyzer.class);
    
    private final UpgradeRecommendationEngine recommendationEngine;
    private final CompatibilityMatrix compatibilityMatrix;

    public UpgradeImpactAnalyzer(UpgradeRecommendationEngine recommendationEngine, 
                                CompatibilityMatrix compatibilityMatrix) {
        this.recommendationEngine = recommendationEngine;
        this.compatibilityMatrix = compatibilityMatrix;
    }

    public String generateImpactAnalysis(Set<Dependency> dependencies, String moduleId, 
                                       String currentVersion, String targetVersion) {
        Set<ImpactResult> directImpacts = analyzeDirectImpacts(dependencies, moduleId, 
            currentVersion, targetVersion);
        Set<ImpactResult> transitiveImpacts = analyzeTransitiveImpacts(dependencies, moduleId, 
            currentVersion, targetVersion);
        
        return formatImpactAnalysis(directImpacts, transitiveImpacts);
    }

    private Set<ImpactResult> analyzeDirectImpacts(Set<Dependency> dependencies, String moduleId,
                                                  String currentVersion, String targetVersion) {
        return dependencies.stream()
            .filter(d -> getModuleId(d).equals(moduleId) && d.version().equals(currentVersion))
            .map(d -> analyzeImpact(d, targetVersion))
            .collect(Collectors.toSet());
    }

    private Set<ImpactResult> analyzeTransitiveImpacts(Set<Dependency> dependencies, String moduleId,
                                                      String currentVersion, String targetVersion) {
        return dependencies.stream()
            .filter(d -> hasTransitiveDependency(d, moduleId, currentVersion))
            .map(d -> analyzeTransitiveImpact(d, moduleId, targetVersion))
            .collect(Collectors.toSet());
    }

    private ImpactResult analyzeImpact(Dependency dependency, String targetVersion) {
        CompatibilityMatrix.CompatibilityResult compatibility = 
            compatibilityMatrix.getCompatibility(
                getModuleId(dependency),
                dependency.version(),
                targetVersion
            );
        
        Set<String> affectedModules = findAffectedModules(dependency);
        
        return new ImpactResult(
            dependency,
            targetVersion,
            compatibility != null ? compatibility.level() : null,
            calculateRiskLevel(compatibility),
            affectedModules,
            generateRecommendations(dependency, targetVersion)
        );
    }

    private ImpactResult analyzeTransitiveImpact(Dependency dependency, String moduleId, 
                                                String targetVersion) {
        // Similar to analyzeImpact but for transitive dependencies
        return null; // TODO: Implement transitive impact analysis
    }

    private Set<String> findAffectedModules(Dependency dependency) {
        // Find modules that depend on this dependency
        return new HashSet<>(); // TODO: Implement affected modules detection
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

    private List<String> generateRecommendations(Dependency dependency, String targetVersion) {
        List<String> recommendations = new ArrayList<>();
        
        // Add version-specific recommendations
        recommendations.add(String.format("Update %s from %s to %s",
            getModuleId(dependency),
            dependency.version(),
            targetVersion
        ));
        
        // Add compatibility recommendations
        CompatibilityMatrix.CompatibilityResult compatibility = 
            compatibilityMatrix.getCompatibility(
                getModuleId(dependency),
                dependency.version(),
                targetVersion
            );
        
        if (compatibility != null) {
            recommendations.addAll(compatibility.notes());
        }
        
        return recommendations;
    }

    private String formatImpactAnalysis(Set<ImpactResult> directImpacts, 
                                      Set<ImpactResult> transitiveImpacts) {
        StringBuilder analysis = new StringBuilder();
        
        analysis.append("<div class='impact-analysis'>");
        analysis.append("<h4>Upgrade Impact Analysis</h4>");
        
        // Direct impacts
        analysis.append("<div class='direct-impacts'>");
        analysis.append("<h5>Direct Impacts</h5>");
        directImpacts.forEach(impact -> analysis.append(formatImpactResult(impact)));
        analysis.append("</div>");
        
        // Transitive impacts
        analysis.append("<div class='transitive-impacts'>");
        analysis.append("<h5>Transitive Impacts</h5>");
        transitiveImpacts.forEach(impact -> analysis.append(formatImpactResult(impact)));
        analysis.append("</div>");
        
        analysis.append("</div>");
        
        return analysis.toString();
    }

    private String formatImpactResult(ImpactResult result) {
        return String.format("""
            <div class="impact-result %s">
                <div class="impact-header">
                    <span class="module">%s</span>
                    <span class="version-change">%s → %s</span>
                    <span class="risk-level">%s</span>
                </div>
                <div class="impact-details">
                    <div class="affected-modules">
                        <strong>Affected Modules:</strong>
                        <ul>%s</ul>
                    </div>
                    <div class="recommendations">
                        <strong>Recommendations:</strong>
                        <ul>%s</ul>
                    </div>
                </div>
            </div>
            """,
            result.riskLevel().toString().toLowerCase(),
            getModuleId(result.dependency()),
            result.dependency().version(),
            result.targetVersion(),
            result.riskLevel(),
            formatAffectedModules(result.affectedModules()),
            formatRecommendations(result.recommendations())
        );
    }

    private String formatAffectedModules(Set<String> modules) {
        return modules.stream()
            .map(m -> String.format("<li>%s</li>", m))
            .collect(Collectors.joining());
    }

    private String formatRecommendations(List<String> recommendations) {
        return recommendations.stream()
            .map(r -> String.format("<li>%s</li>", r))
            .collect(Collectors.joining());
    }

    private String getModuleId(Dependency dependency) {
        return dependency.groupId() + ":" + dependency.artifactId();
    }

    private boolean hasTransitiveDependency(Dependency dependency, String moduleId, 
                                          String version) {
        // TODO: Implement transitive dependency check
        return false;
    }

    public record ImpactResult(
        Dependency dependency,
        String targetVersion,
        CompatibilityMatrix.CompatibilityLevel compatibilityLevel,
        RiskLevel riskLevel,
        Set<String> affectedModules,
        List<String> recommendations
    ) {}

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        UNKNOWN
    }

    public String getStyles() {
        return """
            .impact-analysis {
                margin-top: 20px;
                padding: 15px;
                background: white;
                border: 1px solid #ddd;
                border-radius: 5px;
            }
            
            .impact-result {
                margin: 10px 0;
                padding: 10px;
                border-radius: 5px;
            }
            
            .impact-result.low { background-color: #d4edda; }
            .impact-result.medium { background-color: #fff3cd; }
            .impact-result.high { background-color: #f8d7da; }
            .impact-result.unknown { background-color: #e9ecef; }
            
            .impact-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 10px;
            }
            
            .impact-details {
                margin-top: 10px;
                padding: 10px;
                background: rgba(255, 255, 255, 0.5);
                border-radius: 3px;
            }
            
            .affected-modules ul,
            .recommendations ul {
                margin: 5px 0;
                padding-left: 20px;
            }
        """;
    }
} 