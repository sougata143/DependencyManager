package org.infy.scanner.gradle.catalog;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VersionCatalogPlugin {
    private static final Logger logger = LoggerFactory.getLogger(VersionCatalogPlugin.class);
    
    private final String id;
    private final Map<String, String> pluginVersions = new HashMap<>();
    private final Map<String, PluginDefinition> plugins = new HashMap<>();
    private final Path settingsFile;

    public VersionCatalogPlugin(String id, Path projectPath) {
        this.id = id;
        this.settingsFile = projectPath.resolve("settings.gradle");
        loadPluginDefinitions();
    }

    private void loadPluginDefinitions() {
        if (!Files.exists(settingsFile)) {
            return;
        }

        try {
            String content = Files.readString(settingsFile);
            parsePluginVersions(content);
            parsePluginDefinitions(content);
        } catch (Exception e) {
            logger.error("Error loading plugin definitions", e);
        }
    }

    private void parsePluginVersions(String content) {
        Pattern versionPattern = Pattern.compile(
            "pluginVersion\\s*\\{\\s*([^}]+)\\s*\\}"
        );
        
        Matcher matcher = versionPattern.matcher(content);
        while (matcher.find()) {
            parseVersionBlock(matcher.group(1));
        }
    }

    private void parseVersionBlock(String block) {
        Pattern entryPattern = Pattern.compile(
            "(\\w+)\\s*=\\s*['\"]([^'\"]+)['\"]"
        );
        
        Matcher matcher = entryPattern.matcher(block);
        while (matcher.find()) {
            pluginVersions.put(matcher.group(1), matcher.group(2));
        }
    }

    private void parsePluginDefinitions(String content) {
        Pattern pluginPattern = Pattern.compile(
            "plugins\\s*\\{\\s*([^}]+)\\s*\\}"
        );
        
        Matcher matcher = pluginPattern.matcher(content);
        while (matcher.find()) {
            parsePluginBlock(matcher.group(1));
        }
    }

    private void parsePluginBlock(String block) {
        Pattern entryPattern = Pattern.compile(
            "id\\s*['\"]([^'\"]+)['\"]\\s*version\\s*['\"]([^'\"]+)['\"]"
        );
        
        Matcher matcher = entryPattern.matcher(block);
        while (matcher.find()) {
            String pluginId = matcher.group(1);
            String version = matcher.group(2);
            
            if (version.startsWith("$")) {
                version = resolveVersionReference(version);
            }
            
            plugins.put(pluginId, new PluginDefinition(pluginId, version));
        }
    }

    private String resolveVersionReference(String ref) {
        String key = ref.substring(ref.indexOf('.') + 1);
        return pluginVersions.getOrDefault(key, ref);
    }

    public Set<Dependency> getPluginDependencies() {
        return plugins.values().stream()
            .map(plugin -> new Dependency(
                getGroupId(plugin.id()),
                getArtifactId(plugin.id()),
                plugin.version(),
                "plugin",
                true
            ))
            .collect(Collectors.toSet());
    }

    private String getGroupId(String pluginId) {
        int lastDot = pluginId.lastIndexOf('.');
        return lastDot > 0 ? pluginId.substring(0, lastDot) : pluginId;
    }

    private String getArtifactId(String pluginId) {
        int lastDot = pluginId.lastIndexOf('.');
        return lastDot > 0 ? pluginId.substring(lastDot + 1) : pluginId;
    }

    private record PluginDefinition(String id, String version) {}
} 