package org.infy.scanner.gradle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class VersionCatalogHandler {
    private static final Logger logger = LoggerFactory.getLogger(VersionCatalogHandler.class);
    private final Map<String, String> versionAliases = new HashMap<>();
    private final Map<String, DependencyAlias> dependencyAliases = new HashMap<>();

    public VersionCatalogHandler(Path projectPath) {
        loadVersionCatalog(projectPath);
    }

    private void loadVersionCatalog(Path projectPath) {
        Path catalogPath = projectPath.resolve("gradle/libs.versions.toml");
        if (!Files.exists(catalogPath)) {
            logger.debug("No version catalog found at: {}", catalogPath);
            return;
        }

        try {
            ObjectMapper tomlMapper = new TomlMapper();
            Map<String, Object> catalog = tomlMapper.readValue(
                Files.readString(catalogPath),
                Map.class
            );

            // Parse versions
            Map<String, String> versions = (Map<String, String>) catalog.get("versions");
            if (versions != null) {
                versionAliases.putAll(versions);
            }

            // Parse libraries
            Map<String, Object> libraries = (Map<String, Object>) catalog.get("libraries");
            if (libraries != null) {
                libraries.forEach((alias, value) -> {
                    if (value instanceof String) {
                        String[] parts = ((String) value).split(":");
                        dependencyAliases.put(alias, new DependencyAlias(parts[0], parts[1], 
                            parts.length > 2 ? parts[2] : null));
                    } else if (value instanceof Map) {
                        Map<String, String> depMap = (Map<String, String>) value;
                        dependencyAliases.put(alias, new DependencyAlias(
                            depMap.get("group"),
                            depMap.get("name"),
                            depMap.get("version")
                        ));
                    }
                });
            }
        } catch (Exception e) {
            logger.error("Error loading version catalog", e);
        }
    }

    public String resolveVersion(String versionRef) {
        if (versionRef.startsWith("$")) {
            String alias = versionRef.substring(1);
            return versionAliases.getOrDefault(alias, versionRef);
        }
        return versionRef;
    }

    public DependencyAlias resolveDependencyAlias(String alias) {
        return dependencyAliases.get(alias);
    }

    public record DependencyAlias(String group, String name, String version) {}
} 