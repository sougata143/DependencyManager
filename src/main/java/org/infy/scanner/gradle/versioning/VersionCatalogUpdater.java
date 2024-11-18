package org.infy.scanner.gradle.versioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class VersionCatalogUpdater {
    private static final Logger logger = LoggerFactory.getLogger(VersionCatalogUpdater.class);
    
    private final Path catalogFile;
    private final Map<String, String> versions = new HashMap<>();
    private final Map<String, LibraryDefinition> libraries = new HashMap<>();
    private final ObjectMapper tomlMapper;

    public VersionCatalogUpdater(Path projectPath) {
        this.catalogFile = projectPath.resolve("gradle/libs.versions.toml");
        this.tomlMapper = new TomlMapper();
        loadCatalog();
    }

    private void loadCatalog() {
        if (Files.exists(catalogFile)) {
            try {
                Map<String, Object> catalog = tomlMapper.readValue(
                    Files.readString(catalogFile),
                    Map.class
                );

                // Load versions
                Map<String, String> versionMap = (Map<String, String>) catalog.get("versions");
                if (versionMap != null) {
                    versions.putAll(versionMap);
                }

                // Load libraries
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
            } catch (Exception e) {
                logger.error("Error loading version catalog", e);
            }
        }
    }

    private void parseStringLibrary(String alias, String value) {
        String[] parts = value.split(":");
        if (parts.length >= 2) {
            libraries.put(alias, new LibraryDefinition(
                parts[0],
                parts[1],
                parts.length > 2 ? parts[2] : null
            ));
        }
    }

    private void parseMapLibrary(String alias, Map<String, String> value) {
        libraries.put(alias, new LibraryDefinition(
            value.get("group"),
            value.get("name"),
            value.get("version")
        ));
    }

    public void updateFromDependencies(Set<Dependency> dependencies) {
        // Update versions
        Map<String, String> newVersions = new HashMap<>();
        
        // Update library versions
        for (Map.Entry<String, LibraryDefinition> entry : libraries.entrySet()) {
            String alias = entry.getKey();
            LibraryDefinition lib = entry.getValue();
            
            dependencies.stream()
                .filter(dep -> matches(dep, lib))
                .findFirst()
                .ifPresent(dep -> {
                    String versionKey = "version." + alias;
                    newVersions.put(versionKey, dep.version());
                    logger.info("Updating catalog version for {}: {}", 
                        alias, dep.version());
                });
        }

        // Merge with existing versions
        versions.putAll(newVersions);

        // Save updated catalog
        saveCatalog();
    }

    private boolean matches(Dependency dependency, LibraryDefinition library) {
        return dependency.groupId().equals(library.group()) &&
               dependency.artifactId().equals(library.name());
    }

    private void saveCatalog() {
        try {
            Map<String, Object> catalog = new HashMap<>();
            catalog.put("versions", versions);
            
            Map<String, Object> libraryMap = new HashMap<>();
            libraries.forEach((alias, lib) -> {
                if (lib.version() != null) {
                    libraryMap.put(alias, String.format("%s:%s:%s",
                        lib.group(), lib.name(), lib.version()));
                } else {
                    Map<String, String> libDef = new HashMap<>();
                    libDef.put("group", lib.group());
                    libDef.put("name", lib.name());
                    libraryMap.put(alias, libDef);
                }
            });
            catalog.put("libraries", libraryMap);

            String toml = tomlMapper.writeValueAsString(catalog);
            Files.writeString(catalogFile, toml);
            logger.info("Updated version catalog saved to: {}", catalogFile);
        } catch (Exception e) {
            logger.error("Error saving version catalog", e);
            throw new RuntimeException("Failed to save version catalog", e);
        }
    }

    public void importVersions(Map<String, String> newVersions) {
        versions.putAll(newVersions);
    }

    public void importLibraries(Map<String, Dependency> newLibraries) {
        newLibraries.forEach((alias, dep) -> {
            libraries.put(alias, new LibraryDefinition(
                dep.groupId(),
                dep.artifactId(),
                dep.version()
            ));
        });
    }

    public Map<String, String> getVersions() {
        return new HashMap<>(versions);
    }

    public Map<String, Dependency> getLibraries() {
        return libraries.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> new Dependency(
                    e.getValue().group(),
                    e.getValue().name(),
                    e.getValue().version(),
                    "implementation",
                    true
                )
            ));
    }

    private record LibraryDefinition(String group, String name, String version) {}
} 