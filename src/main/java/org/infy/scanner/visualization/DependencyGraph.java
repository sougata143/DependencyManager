package org.infy.scanner.visualization;

import org.infy.scanner.core.Dependency;
import org.infy.scanner.vulnerability.VulnerabilityResult;

import java.util.HashSet;
import java.util.Set;

public record DependencyGraph(
    Set<GraphNode> nodes,
    Set<GraphEdge> edges
) {
    public static DependencyGraph fromDependencies(Set<Dependency> dependencies, Set<VulnerabilityResult> vulnerabilities) {
        Set<GraphNode> nodes = new HashSet<>();
        Set<GraphEdge> edges = new HashSet<>();
        
        // Create nodes for each dependency
        for (Dependency dependency : dependencies) {
            VulnerabilityResult.Severity maxSeverity = findMaxSeverity(dependency, vulnerabilities);
            nodes.add(new GraphNode(
                dependency.groupId() + ":" + dependency.artifactId(),
                dependency.version(),
                dependency.isDirectDependency(),
                maxSeverity
            ));
        }
        
        // Create edges between dependencies
        // This would require additional dependency relationship information
        // For now, we'll just connect direct dependencies to their immediate dependencies
        
        return new DependencyGraph(nodes, edges);
    }
    
    private static VulnerabilityResult.Severity findMaxSeverity(
            Dependency dependency, 
            Set<VulnerabilityResult> vulnerabilities) {
        return vulnerabilities.stream()
            .filter(v -> v.dependency().equals(dependency))
            .map(VulnerabilityResult::severity)
            .max(Enum::compareTo)
            .orElse(null);
    }
} 