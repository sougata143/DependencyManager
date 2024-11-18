package org.infy.scanner.version;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VersionManager {
    private static final Logger logger = LoggerFactory.getLogger(VersionManager.class);
    
    private final Map<String, String> versionConstraints = new HashMap<>();
    private final Map<String, String> forcedVersions = new HashMap<>();
    private final Map<String, Set<String>> versionGroups = new HashMap<>();
    private final ResolutionStrategy defaultStrategy;

    private static final Pattern DYNAMIC_VERSION_PATTERN = Pattern.compile(
        "^(\\d+(?:\\.\\d+)?(?:\\.\\d+)?)?([+*]|\\.[+*])?$"
    );

    public VersionManager(ResolutionStrategy defaultStrategy) {
        this.defaultStrategy = defaultStrategy;
    }

    public void addVersionConstraint(String moduleId, String versionConstraint) {
        versionConstraints.put(moduleId, versionConstraint);
        logger.info("Added version constraint for {}: {}", moduleId, versionConstraint);
    }

    public void forceVersion(String moduleId, String version) {
        forcedVersions.put(moduleId, version);
        logger.info("Forced version for {}: {}", moduleId, version);
    }

    public void addToVersionGroup(String groupName, String moduleId) {
        versionGroups.computeIfAbsent(groupName, k -> new HashSet<>()).add(moduleId);
        logger.info("Added {} to version group {}", moduleId, groupName);
    }

    public Set<Dependency> manageDependencies(Set<Dependency> dependencies) {
        // First, apply forced versions
        Set<Dependency> managedDeps = applyForcedVersions(dependencies);
        
        // Then, resolve version conflicts
        managedDeps = resolveVersionConflicts(managedDeps);
        
        // Apply version constraints
        managedDeps = applyVersionConstraints(managedDeps);
        
        // Finally, align versions within groups
        managedDeps = alignVersionGroups(managedDeps);
        
        return managedDeps;
    }

    private Set<Dependency> applyForcedVersions(Set<Dependency> dependencies) {
        return dependencies.stream()
            .map(dep -> {
                String moduleId = getModuleId(dep);
                String forcedVersion = forcedVersions.get(moduleId);
                if (forcedVersion != null) {
                    logger.info("Applying forced version for {}: {} -> {}", 
                        moduleId, dep.version(), forcedVersion);
                    return new Dependency(
                        dep.groupId(),
                        dep.artifactId(),
                        forcedVersion,
                        dep.scope(),
                        dep.isDirectDependency()
                    );
                }
                return dep;
            })
            .collect(Collectors.toSet());
    }

    private Set<Dependency> resolveVersionConflicts(Set<Dependency> dependencies) {
        Map<String, Set<Dependency>> moduleGroups = dependencies.stream()
            .collect(Collectors.groupingBy(this::getModuleId, Collectors.toSet()));

        return moduleGroups.values().stream()
            .map(this::resolveConflict)
            .flatMap(Set::stream)
            .collect(Collectors.toSet());
    }

    private Set<Dependency> resolveConflict(Set<Dependency> conflicts) {
        if (conflicts.size() <= 1) {
            return conflicts;
        }

        Dependency selected = defaultStrategy.select(conflicts);
        logger.info("Resolved version conflict for {}: selected {}",
            getModuleId(selected), selected.version());

        return conflicts.stream()
            .map(dep -> new Dependency(
                dep.groupId(),
                dep.artifactId(),
                selected.version(),
                dep.scope(),
                dep.isDirectDependency()
            ))
            .collect(Collectors.toSet());
    }

    private Set<Dependency> applyVersionConstraints(Set<Dependency> dependencies) {
        return dependencies.stream()
            .map(dep -> {
                String moduleId = getModuleId(dep);
                String constraint = versionConstraints.get(moduleId);
                if (constraint != null && !satisfiesConstraint(dep.version(), constraint)) {
                    String newVersion = findCompatibleVersion(dep.version(), constraint);
                    logger.info("Applying version constraint for {}: {} -> {}", 
                        moduleId, dep.version(), newVersion);
                    return new Dependency(
                        dep.groupId(),
                        dep.artifactId(),
                        newVersion,
                        dep.scope(),
                        dep.isDirectDependency()
                    );
                }
                return dep;
            })
            .collect(Collectors.toSet());
    }

    private Set<Dependency> alignVersionGroups(Set<Dependency> dependencies) {
        Map<String, String> groupVersions = new HashMap<>();
        
        // First pass: determine group versions
        for (Map.Entry<String, Set<String>> entry : versionGroups.entrySet()) {
            String groupName = entry.getKey();
            Set<String> moduleIds = entry.getValue();
            
            Set<String> versions = dependencies.stream()
                .filter(dep -> moduleIds.contains(getModuleId(dep)))
                .map(Dependency::version)
                .collect(Collectors.toSet());
            
            if (!versions.isEmpty()) {
                String alignedVersion = defaultStrategy.selectVersion(versions);
                groupVersions.put(groupName, alignedVersion);
            }
        }

        // Second pass: apply group versions
        return dependencies.stream()
            .map(dep -> {
                String moduleId = getModuleId(dep);
                for (Map.Entry<String, Set<String>> entry : versionGroups.entrySet()) {
                    if (entry.getValue().contains(moduleId)) {
                        String groupVersion = groupVersions.get(entry.getKey());
                        if (groupVersion != null && !groupVersion.equals(dep.version())) {
                            logger.info("Aligning version for {} in group {}: {} -> {}", 
                                moduleId, entry.getKey(), dep.version(), groupVersion);
                            return new Dependency(
                                dep.groupId(),
                                dep.artifactId(),
                                groupVersion,
                                dep.scope(),
                                dep.isDirectDependency()
                            );
                        }
                    }
                }
                return dep;
            })
            .collect(Collectors.toSet());
    }

    private String getModuleId(Dependency dependency) {
        return dependency.groupId() + ":" + dependency.artifactId();
    }

    private boolean satisfiesConstraint(String version, String constraint) {
        SemanticVersion sv = SemanticVersion.parse(version);
        if (sv != null) {
            if (constraint.startsWith("^")) {
                // Caret range (^) allows changes that do not modify the left-most non-zero digit
                SemanticVersion baseVersion = SemanticVersion.parse(constraint.substring(1));
                if (baseVersion != null) {
                    if (baseVersion.getMajor() > 0) {
                        return sv.getMajor() == baseVersion.getMajor() &&
                               sv.compareTo(baseVersion) >= 0;
                    } else if (baseVersion.getMinor() > 0) {
                        return sv.getMajor() == 0 &&
                               sv.getMinor() == baseVersion.getMinor() &&
                               sv.compareTo(baseVersion) >= 0;
                    } else {
                        return sv.getMajor() == 0 &&
                               sv.getMinor() == 0 &&
                               sv.getPatch() == baseVersion.getPatch();
                    }
                }
            } else if (constraint.startsWith("~")) {
                // Tilde range (~) allows patch-level changes if a patch version is specified
                SemanticVersion baseVersion = SemanticVersion.parse(constraint.substring(1));
                if (baseVersion != null) {
                    return sv.getMajor() == baseVersion.getMajor() &&
                           sv.getMinor() == baseVersion.getMinor() &&
                           sv.compareTo(baseVersion) >= 0;
                }
            }
        }

        // Fall back to original constraint checking for non-semantic versions
        if (constraint.startsWith("[") || constraint.startsWith("(")) {
            return checkVersionRange(version, constraint);
        }
        return version.equals(constraint);
    }

    private String findCompatibleVersion(String currentVersion, String constraint) {
        // Implement version resolution logic
        // This is a simplified version
        if (constraint.startsWith("[") || constraint.startsWith("(")) {
            return findVersionInRange(currentVersion, constraint);
        }
        return constraint;
    }

    private boolean checkVersionRange(String version, String range) {
        // Implement version range checking
        // This is a placeholder - implement proper version range logic
        return true;
    }

    private String findVersionInRange(String currentVersion, String range) {
        // Implement version range resolution
        // This is a placeholder - implement proper version range resolution
        return currentVersion;
    }

    public enum ResolutionStrategy {
        NEWEST((versions) -> versions.stream()
            .max(Comparator.comparing(Dependency::version, VersionManager::compareVersions))
            .orElseThrow()),
        
        OLDEST((versions) -> versions.stream()
            .min(Comparator.comparing(Dependency::version, VersionManager::compareVersions))
            .orElseThrow()),
        
        PREFER_RELEASE((deps) -> deps.stream()
            .min(Comparator
                .comparing((Dependency d) -> isSnapshot(d.version()))
                .thenComparing(d -> d.version(), (v1, v2) -> compareVersions(v2, v1)))
            .orElseThrow());

        private final java.util.function.Function<Set<Dependency>, Dependency> selector;
        private final java.util.function.Function<Set<String>, String> versionSelector;

        ResolutionStrategy(java.util.function.Function<Set<Dependency>, Dependency> selector) {
            this.selector = selector;
            this.versionSelector = versions -> selector.apply(
                versions.stream()
                    .map(v -> new Dependency("", "", v, "", true))
                    .collect(Collectors.toSet())
            ).version();
        }

        public Dependency select(Set<Dependency> dependencies) {
            return selector.apply(dependencies);
        }

        public String selectVersion(Set<String> versions) {
            return versionSelector.apply(versions);
        }
    }

    private static boolean isSnapshot(String version) {
        return version.endsWith("-SNAPSHOT");
    }

    private static int compareVersions(String v1, String v2) {
        SemanticVersion sv1 = SemanticVersion.parse(v1);
        SemanticVersion sv2 = SemanticVersion.parse(v2);
        
        // If both are semantic versions, use semantic comparison
        if (sv1 != null && sv2 != null) {
            return sv1.compareTo(sv2);
        }
        
        // Fall back to simple version comparison for non-semantic versions
        String[] parts1 = v1.replaceAll("-SNAPSHOT$", "").split("\\.");
        String[] parts2 = v2.replaceAll("-SNAPSHOT$", "").split("\\.");
        
        for (int i = 0; i < Math.max(parts1.length, parts2.length); i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        
        return 0;
    }

    private boolean isCompatible(String v1, String v2) {
        SemanticVersion sv1 = SemanticVersion.parse(v1);
        SemanticVersion sv2 = SemanticVersion.parse(v2);
        
        if (sv1 != null && sv2 != null) {
            return sv1.isCompatibleWith(sv2);
        }
        
        // For non-semantic versions, consider only major version
        String major1 = v1.split("\\.")[0];
        String major2 = v2.split("\\.")[0];
        return major1.equals(major2);
    }
} 