package org.infy.scanner.gradle.versioning;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class InheritedVersionConstraint {
    private static final Logger logger = LoggerFactory.getLogger(InheritedVersionConstraint.class);
    
    private final Map<String, ConstraintDefinition> constraints = new HashMap<>();
    private final Map<String, Set<String>> inheritanceMap = new HashMap<>();

    public void addConstraint(String group, VersionConstraint constraint, boolean inherited) {
        constraints.put(group, new ConstraintDefinition(constraint, inherited));
    }

    public void addInheritance(String child, String parent) {
        inheritanceMap.computeIfAbsent(child, k -> new HashSet<>()).add(parent);
    }

    public Set<Dependency> applyConstraints(Set<Dependency> dependencies) {
        // Build effective constraints considering inheritance
        Map<String, VersionConstraint> effectiveConstraints = buildEffectiveConstraints();

        // Apply constraints
        return dependencies.stream()
            .map(dep -> applyEffectiveConstraints(dep, effectiveConstraints))
            .collect(Collectors.toSet());
    }

    private Map<String, VersionConstraint> buildEffectiveConstraints() {
        Map<String, VersionConstraint> effective = new HashMap<>();

        // Process each group's constraints
        for (Map.Entry<String, ConstraintDefinition> entry : constraints.entrySet()) {
            String group = entry.getKey();
            ConstraintDefinition definition = entry.getValue();

            if (definition.inherited()) {
                // If inherited, process inheritance chain
                processInheritanceChain(group, effective, new HashSet<>());
            } else {
                // If not inherited, just use the direct constraint
                effective.put(group, definition.constraint());
            }
        }

        return effective;
    }

    private void processInheritanceChain(
            String group,
            Map<String, VersionConstraint> effective,
            Set<String> processed) {
        // Prevent circular inheritance
        if (!processed.add(group)) {
            logger.warn("Circular inheritance detected for group: {}", group);
            return;
        }

        // Get parent constraints
        Set<String> parents = inheritanceMap.getOrDefault(group, Set.of());
        List<VersionConstraint> parentConstraints = new ArrayList<>();

        // Process parent constraints first
        for (String parent : parents) {
            processInheritanceChain(parent, effective, processed);
            ConstraintDefinition parentDef = constraints.get(parent);
            if (parentDef != null && parentDef.inherited()) {
                parentConstraints.add(parentDef.constraint());
            }
        }

        // Combine parent constraints with current constraint
        ConstraintDefinition current = constraints.get(group);
        if (current != null) {
            VersionConstraint combined = combineConstraints(
                parentConstraints, 
                current.constraint()
            );
            effective.put(group, combined);
        }
    }

    private VersionConstraint combineConstraints(
            List<VersionConstraint> parents,
            VersionConstraint current) {
        // Implement constraint combination logic
        // This is a simplified version - you might want to add more sophisticated combining rules
        if (parents.isEmpty()) {
            return current;
        }

        // Combine version ranges, prefer stricter constraints
        // This would need proper implementation based on your VersionConstraint class
        return current; // Placeholder - implement actual combination logic
    }

    private Dependency applyEffectiveConstraints(
            Dependency dependency,
            Map<String, VersionConstraint> effectiveConstraints) {
        String group = dependency.groupId();
        VersionConstraint constraint = effectiveConstraints.get(group);

        if (constraint != null) {
            RichVersion version = new RichVersion(dependency.version());
            if (!constraint.isSatisfiedBy(version)) {
                String newVersion = constraint.getPreferredVersion();
                if (newVersion != null) {
                    logger.info("Applying inherited constraint to {}: {} -> {}",
                        group, dependency.version(), newVersion);
                    
                    return new Dependency(
                        dependency.groupId(),
                        dependency.artifactId(),
                        newVersion,
                        dependency.scope(),
                        dependency.isDirectDependency()
                    );
                }
            }
        }

        return dependency;
    }

    private record ConstraintDefinition(VersionConstraint constraint, boolean inherited) {}
} 