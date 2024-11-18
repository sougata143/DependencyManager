package org.infy.scanner.version.visualization;

import org.infy.scanner.core.Dependency;
import org.infy.scanner.version.CompatibilityMatrix;
import org.infy.scanner.version.VersionStabilityChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

public class NodeDetailsPanel {
    private static final Logger logger = LoggerFactory.getLogger(NodeDetailsPanel.class);
    private final VersionStabilityChecker stabilityChecker;

    public NodeDetailsPanel() {
        this.stabilityChecker = new VersionStabilityChecker();
    }

    public String generateDetailsHtml(String moduleId, String version, Set<Dependency> dependencies, 
                                    CompatibilityMatrix matrix) {
        return """
            <div class="node-details-panel">
                <h4>Dependency Details</h4>
                <div class="details-content">
                    %s
                </div>
                <div class="compatibility-info">
                    %s
                </div>
                <div class="usage-info">
                    %s
                </div>
            </div>
        """.formatted(
            generateBasicDetails(moduleId, version),
            generateCompatibilityInfo(moduleId, version, matrix),
            generateUsageInfo(moduleId, version, dependencies)
        );
    }

    private String generateBasicDetails(String moduleId, String version) {
        VersionStabilityChecker.StabilityLevel stability = 
            stabilityChecker.checkStability(version);
        
        return """
            <div class="basic-details">
                <p><strong>Module:</strong> %s</p>
                <p><strong>Version:</strong> %s</p>
                <p><strong>Stability:</strong> <span class="stability-%s">%s</span></p>
            </div>
        """.formatted(
            moduleId,
            version,
            stability.toString().toLowerCase(),
            stability
        );
    }

    private String generateCompatibilityInfo(String moduleId, String version, 
                                           CompatibilityMatrix matrix) {
        Set<String> availableVersions = matrix.getAvailableVersions(moduleId);
        StringBuilder info = new StringBuilder("<h5>Compatibility Information</h5>");
        
        for (String otherVersion : availableVersions) {
            if (!otherVersion.equals(version)) {
                CompatibilityMatrix.CompatibilityResult result = 
                    matrix.getCompatibility(moduleId, version, otherVersion);
                
                if (result != null) {
                    info.append(String.format("""
                        <div class="compatibility-entry %s">
                            <span class="version">%s</span>
                            <span class="level">%s</span>
                            <span class="details">%s</span>
                        </div>
                        """,
                        result.level().toString().toLowerCase(),
                        otherVersion,
                        result.level(),
                        result.summary()
                    ));
                }
            }
        }
        
        return info.toString();
    }

    private String generateUsageInfo(String moduleId, String version, Set<Dependency> dependencies) {
        Set<Dependency> usages = dependencies.stream()
            .filter(d -> getModuleId(d).equals(moduleId) && d.version().equals(version))
            .collect(Collectors.toSet());

        StringBuilder info = new StringBuilder("<h5>Usage Information</h5>");
        
        if (usages.isEmpty()) {
            info.append("<p>No direct usages found</p>");
        } else {
            info.append("<ul class='usage-list'>");
            for (Dependency dep : usages) {
                info.append(String.format("""
                    <li>
                        <span class="scope">%s</span>
                        <span class="direct">%s</span>
                    </li>
                    """,
                    dep.scope(),
                    dep.isDirectDependency() ? "Direct" : "Transitive"
                ));
            }
            info.append("</ul>");
        }
        
        return info.toString();
    }

    private String getModuleId(Dependency dependency) {
        return dependency.groupId() + ":" + dependency.artifactId();
    }

    public String getStyles() {
        return """
            .node-details-panel {
                background: white;
                border: 1px solid #ddd;
                border-radius: 5px;
                padding: 15px;
                max-width: 400px;
            }
            
            .details-content {
                margin-bottom: 15px;
            }
            
            .stability-stable { color: #28a745; }
            .stability-experimental { color: #ffc107; }
            .stability-snapshot { color: #dc3545; }
            
            .compatibility-entry {
                margin: 5px 0;
                padding: 5px;
                border-radius: 3px;
            }
            
            .compatibility-entry.patch_compatible { background-color: #d4edda; }
            .compatibility-entry.minor_compatible { background-color: #fff3cd; }
            .compatibility-entry.incompatible { background-color: #f8d7da; }
            
            .usage-list {
                list-style: none;
                padding: 0;
            }
            
            .usage-list li {
                margin: 5px 0;
                padding: 5px;
                background: #f8f9fa;
                border-radius: 3px;
            }
            
            .scope {
                font-weight: bold;
                margin-right: 10px;
            }
            
            .direct {
                font-size: 0.9em;
                color: #6c757d;
            }
        """;
    }
} 