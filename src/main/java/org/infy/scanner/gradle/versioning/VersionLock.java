package org.infy.scanner.gradle.versioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class VersionLock {
    private static final Logger logger = LoggerFactory.getLogger(VersionLock.class);
    private final Map<String, String> lockedVersions = new HashMap<>();
    private final Path lockFile;
    private final ObjectMapper objectMapper;

    public VersionLock(Path projectPath) {
        this.lockFile = projectPath.resolve("gradle/dependency-locks/versions.lock");
        this.objectMapper = new ObjectMapper();
        loadLockFile();
    }

    private void loadLockFile() {
        if (Files.exists(lockFile)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> locked = objectMapper.readValue(lockFile.toFile(), Map.class);
                lockedVersions.putAll(locked);
            } catch (Exception e) {
                logger.error("Failed to load lock file: {}", lockFile, e);
            }
        }
    }

    public void saveLockFile(Set<Dependency> dependencies) {
        try {
            Files.createDirectories(lockFile.getParent());
            Map<String, String> versions = dependencies.stream()
                .collect(Collectors.toMap(
                    dep -> dep.groupId() + ":" + dep.artifactId(),
                    Dependency::version,
                    (v1, v2) -> v1 // Keep first version in case of duplicates
                ));
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(lockFile.toFile(), versions);
            logger.info("Saved version lock file: {}", lockFile);
        } catch (Exception e) {
            logger.error("Failed to save lock file", e);
            throw new RuntimeException("Failed to save lock file", e);
        }
    }

    public Set<Dependency> enforceLocks(Set<Dependency> dependencies) {
        return dependencies.stream()
            .map(this::enforceLockedVersion)
            .collect(Collectors.toSet());
    }

    private Dependency enforceLockedVersion(Dependency dependency) {
        String key = dependency.groupId() + ":" + dependency.artifactId();
        String lockedVersion = lockedVersions.get(key);
        
        if (lockedVersion != null && !lockedVersion.equals(dependency.version())) {
            logger.info("Enforcing locked version for {}: {} -> {}",
                key, dependency.version(), lockedVersion);
            
            return new Dependency(
                dependency.groupId(),
                dependency.artifactId(),
                lockedVersion,
                dependency.scope(),
                dependency.isDirectDependency()
            );
        }
        
        return dependency;
    }

    public boolean isLocked(Dependency dependency) {
        String key = dependency.groupId() + ":" + dependency.artifactId();
        return lockedVersions.containsKey(key);
    }

    public String getLockedVersion(Dependency dependency) {
        String key = dependency.groupId() + ":" + dependency.artifactId();
        return lockedVersions.get(key);
    }
} 