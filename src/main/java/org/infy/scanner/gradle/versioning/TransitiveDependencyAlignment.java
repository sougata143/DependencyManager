package org.infy.scanner.gradle.versioning;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class TransitiveDependencyAlignment {
    private static final Logger logger = LoggerFactory.getLogger(TransitiveDependencyAlignment.class);
    
    private final Map<String, Set<String>> alignmentGroups = new HashMap<>();
    private final Map<String, String> transitiveVersions = new HashMap<>();

    public void addAlignmentGroup(String name, Set<String> patterns) {
        alignmentGroups.put(name, patterns);
    }

    public Set<Dependency> alignTransitiveDependencies(Set<Dependency> dependencies) {
        // First, collect all direct dependency versions
        Map<String, String> directVersions = dependencies.stream()
            .filter(Dependency::isDirectDependency)
            .collect(Collectors.toMap(
                this::getModuleKey,
                Dependency::version,
                (v1, v2) -> v1 // Keep first version in case of duplicates
            ));

        // Build transitive version map based on alignment groups
        buildTransitiveVersionMap(dependencies, directVersions);

        // Apply transitive alignment
        return dependencies.stream()
            .map(dep -> alignDependency(dep, directVersions))
            .collect(Collectors.toSet());
    }

    private void buildTransitiveVersionMap(Set<Dependency> dependencies, Map<String, String> directVersions) {
        // Group dependencies by alignment group
        for (Map.Entry<String, Set<String>> entry : alignmentGroups.entrySet()) {
            String groupName = entry.getKey();
            Set<String> patterns = entry.getValue();

            // Find all dependencies matching this group
            Set<Dependency> groupDeps = dependencies.stream()
                .filter(dep -> matchesAnyPattern(dep, patterns))
                .collect(Collectors.toSet());

            if (!groupDeps.isEmpty()) {
                // Find the version to align to (prefer direct dependencies)
                String alignedVersion = groupDeps.stream()
                    .filter(Dependency::isDirectDependency)
                    .map(Dependency::version)
                    .findFirst()
                    .orElseGet(() -> findHighestVersion(groupDeps));

                // Store aligned version for this group
                groupDeps.forEach(dep -> 
                    transitiveVersions.put(getModuleKey(dep), alignedVersion));
            }
        }
    }

    private Dependency alignDependency(Dependency dependency, Map<String, String> directVersions) {
        String moduleKey = getModuleKey(dependency);
        
        // Don't align direct dependencies
        if (dependency.isDirectDependency()) {
            return dependency;
        }

        // Check if this dependency should be aligned
        String alignedVersion = transitiveVersions.get(moduleKey);
        if (alignedVersion != null && !alignedVersion.equals(dependency.version())) {
            logger.info("Aligning transitive dependency {} from version {} to {}",
                moduleKey, dependency.version(), alignedVersion);
            
            return new Dependency(
                dependency.groupId(),
                dependency.artifactId(),
                alignedVersion,
                dependency.scope(),
                dependency.isDirectDependency()
            );
        }

        return dependency;
    }

    private boolean matchesAnyPattern(Dependency dependency, Set<String> patterns) {
        String moduleKey = getModuleKey(dependency);
        return patterns.stream().anyMatch(pattern -> 
            moduleKey.matches(pattern.replace("*", ".*")));
    }

    private String getModuleKey(Dependency dependency) {
        return dependency.groupId() + ":" + dependency.artifactId();
    }

    private String findHighestVersion(Set<Dependency> dependencies) {
        return dependencies.stream()
            .map(Dependency::version)
            .max((v1, v2) -> new RichVersion(v1).compareTo(new RichVersion(v2)))
            .orElseThrow();
    }
} 