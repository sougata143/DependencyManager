package org.infy.scanner.gradle.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.infy.scanner.gradle.versioning.VersionCatalogUpdater;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class SharedVersionCatalog {
    private static final Logger logger = LoggerFactory.getLogger(SharedVersionCatalog.class);
    
    private final Path sharedCatalogPath;
    private final Map<String, String> versions = new HashMap<>();
    private final Map<String, Dependency> libraries = new HashMap<>();
    private final ObjectMapper objectMapper;

    public SharedVersionCatalog(Path sharedCatalogPath) {
        this.sharedCatalogPath = sharedCatalogPath;
        this.objectMapper = new ObjectMapper();
        loadSharedCatalog();
    }

    private void loadSharedCatalog() {
        if (!Files.exists(sharedCatalogPath)) {
            return;
        }

        try {
            Map<String, Object> catalog = objectMapper.readValue(
                Files.readString(sharedCatalogPath),
                Map.class
            );

            loadVersions(catalog);
            loadLibraries(catalog);
        } catch (Exception e) {
            logger.error("Error loading shared catalog", e);
        }
    }

    private void loadVersions(Map<String, Object> catalog) {
        Map<String, String> versionMap = (Map<String, String>) catalog.get("versions");
        if (versionMap != null) {
            versions.putAll(versionMap);
        }
    }

    private void loadLibraries(Map<String, Object> catalog) {
        Map<String, Object> libraryMap = (Map<String, Object>) catalog.get("libraries");
        if (libraryMap != null) {
            libraryMap.forEach((alias, value) -> {
                if (value instanceof String) {
                    parseStringLibrary(alias, (String) value);
                } else if (value instanceof Map) {
                    parseMapLibrary(alias, (Map<String, String>) value);
                }
            });
        }
    }

    private void parseStringLibrary(String alias, String value) {
        String[] parts = value.split(":");
        if (parts.length >= 3) {
            libraries.put(alias, new Dependency(
                parts[0],
                parts[1],
                parts[2],
                "implementation",
                true
            ));
        }
    }

    private void parseMapLibrary(String alias, Map<String, String> value) {
        libraries.put(alias, new Dependency(
            value.get("group"),
            value.get("name"),
            value.get("version"),
            value.getOrDefault("scope", "implementation"),
            true
        ));
    }

    public void importTo(VersionCatalogUpdater catalogUpdater) {
        // Import versions
        catalogUpdater.importVersions(versions);
        
        // Import libraries
        catalogUpdater.importLibraries(libraries);
    }

    public void exportFrom(VersionCatalogUpdater catalogUpdater) {
        try {
            Map<String, Object> catalog = new HashMap<>();
            
            // Export versions
            catalog.put("versions", catalogUpdater.getVersions());
            
            // Export libraries
            Map<String, Object> libraryMap = new HashMap<>();
            catalogUpdater.getLibraries().forEach((alias, lib) -> {
                libraryMap.put(alias, String.format("%s:%s:%s",
                    lib.groupId(), lib.artifactId(), lib.version()));
            });
            catalog.put("libraries", libraryMap);

            // Save shared catalog
            Files.createDirectories(sharedCatalogPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(sharedCatalogPath.toFile(), catalog);
            
            logger.info("Exported shared catalog to: {}", sharedCatalogPath);
        } catch (Exception e) {
            logger.error("Error exporting shared catalog", e);
            throw new RuntimeException("Failed to export shared catalog", e);
        }
    }

    public Set<Dependency> getDependencies() {
        return new HashSet<>(libraries.values());
    }

    public Map<String, String> getVersions() {
        return new HashMap<>(versions);
    }
} 