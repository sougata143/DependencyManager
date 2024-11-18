package org.infy.scanner.gradle.capabilities;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CapabilityConflictHandler {
    private static final Logger logger = LoggerFactory.getLogger(CapabilityConflictHandler.class);
    private final Map<String, Set<Capability>> declaredCapabilities = new HashMap<>();
    
    private static final Pattern CAPABILITY_PATTERN = Pattern.compile(
        "capabilities\\s*\\{([^}]+)\\}"
    );

    public void parseCapabilities(String buildScript) {
        Matcher matcher = CAPABILITY_PATTERN.matcher(buildScript);
        while (matcher.find()) {
            String capabilityBlock = matcher.group(1);
            parseCapabilityBlock(capabilityBlock);
        }
    }

    private void parseCapabilityBlock(String block) {
        Pattern capabilityPattern = Pattern.compile(
            "([^\\s]+)\\s*\\{\\s*capability\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]\\s*\\}"
        );
        
        Matcher matcher = capabilityPattern.matcher(block);
        while (matcher.find()) {
            String dependencyNotation = matcher.group(1);
            String group = matcher.group(2);
            String name = matcher.group(3);
            
            declaredCapabilities.computeIfAbsent(dependencyNotation, k -> new HashSet<>())
                .add(new Capability(group, name));
        }
    }

    public Set<CapabilityConflict> detectConflicts(Set<Dependency> dependencies) {
        Map<Capability, Set<Dependency>> capabilityProviders = new HashMap<>();
        Set<CapabilityConflict> conflicts = new HashSet<>();

        // Build capability map
        for (Dependency dependency : dependencies) {
            String notation = dependency.groupId() + ":" + dependency.artifactId();
            Set<Capability> capabilities = declaredCapabilities.getOrDefault(notation, Set.of());
            
            for (Capability capability : capabilities) {
                Set<Dependency> providers = capabilityProviders.computeIfAbsent(
                    capability, k -> new HashSet<>());
                providers.add(dependency);
                
                if (providers.size() > 1) {
                    conflicts.add(new CapabilityConflict(capability, providers));
                }
            }
        }

        // Log conflicts
        conflicts.forEach(conflict -> {
            logger.error("Capability conflict detected for {}:", conflict.capability());
            conflict.providers().forEach(dep ->
                logger.error("  - {}", dep));
        });

        return conflicts;
    }

    public Set<Dependency> resolveConflicts(Set<Dependency> dependencies) {
        Set<CapabilityConflict> conflicts = detectConflicts(dependencies);
        Set<Dependency> resolvedDependencies = new HashSet<>(dependencies);

        for (CapabilityConflict conflict : conflicts) {
            // Select the dependency with the highest version
            Optional<Dependency> selected = conflict.providers().stream()
                .max(Comparator.comparing(Dependency::version));
            
            if (selected.isPresent()) {
                // Remove all other providers of this capability
                conflict.providers().stream()
                    .filter(dep -> !dep.equals(selected.get()))
                    .forEach(resolvedDependencies::remove);
                
                logger.info("Resolved capability conflict for {}: selected {}",
                    conflict.capability(), selected.get());
            }
        }

        return resolvedDependencies;
    }

    record Capability(String group, String name) {}
    
    record CapabilityConflict(Capability capability, Set<Dependency> providers) {}
} 