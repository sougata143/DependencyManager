package org.infy.scanner.gradle.versioning;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class VersionConflictResolver {
    private static final Logger logger = LoggerFactory.getLogger(VersionConflictResolver.class);
    
    private final VersionLock versionLock;
    private final ResolutionStrategy defaultStrategy;
    private final Map<String, ResolutionStrategy> moduleStrategies = new HashMap<>();

    public VersionConflictResolver(VersionLock versionLock, ResolutionStrategy defaultStrategy) {
        this.versionLock = versionLock;
        this.defaultStrategy = defaultStrategy;
    }

    public void setModuleStrategy(String modulePattern, ResolutionStrategy strategy) {
        moduleStrategies.put(modulePattern, strategy);
    }

    public Set<Dependency> resolveConflicts(Set<Dependency> dependencies) {
        // Group dependencies by module (groupId:artifactId)
        Map<String, Set<Dependency>> moduleGroups = dependencies.stream()
            .collect(Collectors.groupingBy(
                dep -> dep.groupId() + ":" + dep.artifactId(),
                Collectors.toSet()
            ));

        Set<Dependency> resolved = new HashSet<>();
        
        for (Map.Entry<String, Set<Dependency>> entry : moduleGroups.entrySet()) {
            String module = entry.getKey();
            Set<Dependency> versions = entry.getValue();
            
            if (versions.size() > 1) {
                Dependency selectedVersion = resolveModuleConflict(module, versions);
                resolved.add(selectedVersion);
                logResolution(module, versions, selectedVersion);
            } else {
                resolved.addAll(versions);
            }
        }
        
        return resolved;
    }

    private Dependency resolveModuleConflict(String module, Set<Dependency> versions) {
        // Check for locked version first
        Optional<Dependency> lockedVersion = versions.stream()
            .filter(versionLock::isLocked)
            .findFirst();
        
        if (lockedVersion.isPresent()) {
            return lockedVersion.get();
        }

        // Apply module-specific or default strategy
        ResolutionStrategy strategy = moduleStrategies.entrySet().stream()
            .filter(e -> module.matches(e.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(defaultStrategy);
        
        return strategy.resolve(versions);
    }

    private void logResolution(String module, Set<Dependency> versions, Dependency selected) {
        logger.info("Version conflict for {}: {} versions available, selected {}",
            module,
            versions.size(),
            selected.version());
        
        versions.stream()
            .map(Dependency::version)
            .filter(v -> !v.equals(selected.version()))
            .forEach(v -> logger.debug("  Rejected version: {}", v));
    }

    public enum ResolutionStrategy {
        NEWEST((versions) -> versions.stream()
            .max(Comparator.comparing(d -> new RichVersion(d.version())))
            .orElseThrow()),
        
        OLDEST((versions) -> versions.stream()
            .min(Comparator.comparing(d -> new RichVersion(d.version())))
            .orElseThrow()),
        
        PREFER_RELEASE((versions) -> versions.stream()
            .min(Comparator
                .comparing((Dependency d) -> new RichVersion(d.version()).isPreRelease())
                .thenComparing(d -> new RichVersion(d.version())))
            .orElseThrow()),
        
        PREFER_DIRECT((versions) -> versions.stream()
            .filter(Dependency::isDirectDependency)
            .findFirst()
            .orElseGet(() -> NEWEST.resolver.apply(versions)));

        private final java.util.function.Function<Set<Dependency>, Dependency> resolver;

        ResolutionStrategy(java.util.function.Function<Set<Dependency>, Dependency> resolver) {
            this.resolver = resolver;
        }

        public Dependency resolve(Set<Dependency> versions) {
            return resolver.apply(versions);
        }
    }
} 