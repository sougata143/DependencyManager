package org.infy.scanner.gradle.versioning;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class LockedVersionAlignment {
    private static final Logger logger = LoggerFactory.getLogger(LockedVersionAlignment.class);
    
    private final VersionLock versionLock;
    private final VersionAlignment versionAlignment;
    private final Map<String, Set<String>> alignmentGroups = new HashMap<>();

    public LockedVersionAlignment(VersionLock versionLock, VersionAlignment versionAlignment) {
        this.versionLock = versionLock;
        this.versionAlignment = versionAlignment;
    }

    public Set<Dependency> alignVersions(Set<Dependency> dependencies) {
        // First apply locks
        Set<Dependency> lockedDependencies = versionLock.enforceLocks(dependencies);
        
        // Then align remaining unlocked dependencies
        Set<Dependency> unlockedDependencies = lockedDependencies.stream()
            .filter(dep -> !versionLock.isLocked(dep))
            .collect(Collectors.toSet());
        
        Set<Dependency> alignedUnlocked = versionAlignment.alignVersions(unlockedDependencies);
        
        // Combine locked and aligned dependencies
        Set<Dependency> result = new HashSet<>(lockedDependencies);
        result.removeAll(unlockedDependencies);
        result.addAll(alignedUnlocked);
        
        // Log alignment results
        logAlignmentResults(dependencies, result);
        
        return result;
    }

    public void addAlignmentGroup(String name, Set<String> patterns) {
        alignmentGroups.put(name, patterns);
        patterns.forEach(pattern -> versionAlignment.addAlignmentGroup(name, pattern));
    }

    public void saveLocks(Set<Dependency> dependencies) {
        // Save only aligned versions to the lock file
        Set<Dependency> alignedDependencies = alignVersions(dependencies);
        versionLock.saveLockFile(alignedDependencies);
    }

    private void logAlignmentResults(Set<Dependency> original, Set<Dependency> aligned) {
        Map<String, String> originalVersions = original.stream()
            .collect(Collectors.toMap(
                dep -> dep.groupId() + ":" + dep.artifactId(),
                Dependency::version
            ));
        
        aligned.forEach(dep -> {
            String key = dep.groupId() + ":" + dep.artifactId();
            String originalVersion = originalVersions.get(key);
            if (!dep.version().equals(originalVersion)) {
                if (versionLock.isLocked(dep)) {
                    logger.info("Locked version alignment: {} {} -> {}",
                        key, originalVersion, dep.version());
                } else {
                    logger.info("Dynamic version alignment: {} {} -> {}",
                        key, originalVersion, dep.version());
                }
            }
        });
    }
} 