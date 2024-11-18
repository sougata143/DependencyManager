package org.infy.scanner.gradle.versioning;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class VersionCompatibility {
    private static final Logger logger = LoggerFactory.getLogger(VersionCompatibility.class);
    
    private final Map<String, Set<CompatibilityRule>> compatibilityRules = new HashMap<>();

    public void addCompatibilityRule(String group, CompatibilityRule rule) {
        compatibilityRules.computeIfAbsent(group, k -> new HashSet<>()).add(rule);
    }

    public Set<CompatibilityIssue> checkCompatibility(Set<Dependency> dependencies) {
        Set<CompatibilityIssue> issues = new HashSet<>();
        Map<String, Set<Dependency>> groupedDependencies = groupDependencies(dependencies);

        for (Map.Entry<String, Set<Dependency>> entry : groupedDependencies.entrySet()) {
            String group = entry.getKey();
            Set<Dependency> groupDeps = entry.getValue();
            
            Set<CompatibilityRule> rules = compatibilityRules.getOrDefault(group, Set.of());
            for (CompatibilityRule rule : rules) {
                issues.addAll(rule.check(groupDeps));
            }
        }

        return issues;
    }

    private Map<String, Set<Dependency>> groupDependencies(Set<Dependency> dependencies) {
        Map<String, Set<Dependency>> grouped = new HashMap<>();
        for (Dependency dep : dependencies) {
            grouped.computeIfAbsent(dep.groupId(), k -> new HashSet<>()).add(dep);
        }
        return grouped;
    }

    public record CompatibilityIssue(
        Dependency dependency1,
        Dependency dependency2,
        String reason,
        Severity severity
    ) {
        public enum Severity {
            ERROR,
            WARNING
        }
    }

    public interface CompatibilityRule {
        Set<CompatibilityIssue> check(Set<Dependency> dependencies);
    }

    public static class MajorVersionCompatibilityRule implements CompatibilityRule {
        @Override
        public Set<CompatibilityIssue> check(Set<Dependency> dependencies) {
            Set<CompatibilityIssue> issues = new HashSet<>();
            List<Dependency> depList = new ArrayList<>(dependencies);

            for (int i = 0; i < depList.size(); i++) {
                for (int j = i + 1; j < depList.size(); j++) {
                    Dependency dep1 = depList.get(i);
                    Dependency dep2 = depList.get(j);

                    RichVersion version1 = new RichVersion(dep1.version());
                    RichVersion version2 = new RichVersion(dep2.version());

                    if (!version1.isCompatibleWith(version2)) {
                        issues.add(new CompatibilityIssue(
                            dep1,
                            dep2,
                            "Incompatible major versions",
                            CompatibilityIssue.Severity.ERROR
                        ));
                    }
                }
            }

            return issues;
        }
    }
} 