package org.infy.scanner.gradle.versioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.infy.scanner.core.Dependency;
import org.infy.scanner.gradle.VersionCatalogHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class CatalogVersionLock {
    private static final Logger logger = LoggerFactory.getLogger(CatalogVersionLock.class);
    
    private final VersionCatalogHandler catalogHandler;
    private final VersionLock versionLock;
    private final Path catalogLockFile;
    private final Map<String, String> catalogVersions = new HashMap<>();

    public CatalogVersionLock(Path projectPath, VersionCatalogHandler catalogHandler, VersionLock versionLock) {
        this.catalogHandler = catalogHandler;
        this.versionLock = versionLock;
        this.catalogLockFile = projectPath.resolve("gradle/catalog-versions.lock");
        loadCatalogLocks();
    }

    private void loadCatalogLocks() {
        if (Files.exists(catalogLockFile)) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, String> versions = mapper.readValue(catalogLockFile.toFile(), Map.class);
                catalogVersions.putAll(versions);
            } catch (Exception e) {
                logger.error("Failed to load catalog lock file: {}", catalogLockFile, e);
            }
        }
    }

    public void saveCatalogLocks() {
        try {
            Files.createDirectories(catalogLockFile.getParent());
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter()
                .writeValue(catalogLockFile.toFile(), catalogVersions);
            logger.info("Saved catalog version lock file: {}", catalogLockFile);
        } catch (Exception e) {
            logger.error("Failed to save catalog lock file", e);
            throw new RuntimeException("Failed to save catalog lock file", e);
        }
    }

    public Set<Dependency> applyLocks(Set<Dependency> dependencies) {
        Set<Dependency> result = new HashSet<>();
        
        for (Dependency dep : dependencies) {
            String catalogVersion = getCatalogVersion(dep);
            if (catalogVersion != null) {
                result.add(new Dependency(
                    dep.groupId(),
                    dep.artifactId(),
                    catalogVersion,
                    dep.scope(),
                    dep.isDirectDependency()
                ));
                logger.info("Applied catalog version lock: {} -> {}",
                    dep.groupId() + ":" + dep.artifactId(), catalogVersion);
            } else {
                result.add(dep);
            }
        }
        
        return result;
    }

    private String getCatalogVersion(Dependency dependency) {
        String key = dependency.groupId() + ":" + dependency.artifactId();
        return catalogVersions.get(key);
    }

    public void updateCatalogLocks(Set<Dependency> dependencies) {
        // Update catalog versions from current dependencies
        Map<String, String> newVersions = dependencies.stream()
            .filter(this::isInCatalog)
            .collect(Collectors.toMap(
                dep -> dep.groupId() + ":" + dep.artifactId(),
                Dependency::version,
                (v1, v2) -> v1 // Keep first version in case of duplicates
            ));
        
        catalogVersions.putAll(newVersions);
        saveCatalogLocks();
    }

    private boolean isInCatalog(Dependency dependency) {
        return catalogHandler.resolveDependencyAlias(
            dependency.groupId() + ":" + dependency.artifactId()) != null;
    }
} 