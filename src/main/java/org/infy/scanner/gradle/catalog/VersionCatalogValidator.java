package org.infy.scanner.gradle.catalog;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VersionCatalogValidator {
    private static final Logger logger = LoggerFactory.getLogger(VersionCatalogValidator.class);
    
    private static final Pattern VERSION_PATTERN = Pattern.compile(
        "^\\d+(\\.\\d+)*(-[\\w.-]+)?(\\+[\\w.-]+)?$"
    );
    
    private final List<ValidationRule> rules = new ArrayList<>();

    public VersionCatalogValidator() {
        // Add default validation rules
        rules.add(new VersionSyntaxRule());
        rules.add(new DuplicateAliasRule());
        rules.add(new UnusedVersionRule());
        rules.add(new InconsistentVersionRule());
    }

    public ValidationResult validate(Map<String, String> versions,
                                  Map<String, Dependency> libraries,
                                  Set<Dependency> actualDependencies) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        for (ValidationRule rule : rules) {
            issues.addAll(rule.validate(versions, libraries, actualDependencies));
        }
        
        return new ValidationResult(issues);
    }

    private interface ValidationRule {
        List<ValidationIssue> validate(
            Map<String, String> versions,
            Map<String, Dependency> libraries,
            Set<Dependency> actualDependencies
        );
    }

    private static class VersionSyntaxRule implements ValidationRule {
        @Override
        public List<ValidationIssue> validate(
                Map<String, String> versions,
                Map<String, Dependency> libraries,
                Set<Dependency> actualDependencies) {
            return versions.entrySet().stream()
                .filter(e -> !VERSION_PATTERN.matcher(e.getValue()).matches())
                .map(e -> new ValidationIssue(
                    ValidationSeverity.ERROR,
                    String.format("Invalid version syntax: %s = %s", e.getKey(), e.getValue())
                ))
                .collect(Collectors.toList());
        }
    }

    private static class DuplicateAliasRule implements ValidationRule {
        @Override
        public List<ValidationIssue> validate(
                Map<String, String> versions,
                Map<String, Dependency> libraries,
                Set<Dependency> actualDependencies) {
            Set<String> aliases = new HashSet<>();
            return libraries.keySet().stream()
                .filter(alias -> !aliases.add(alias))
                .map(alias -> new ValidationIssue(
                    ValidationSeverity.ERROR,
                    String.format("Duplicate library alias: %s", alias)
                ))
                .collect(Collectors.toList());
        }
    }

    private static class UnusedVersionRule implements ValidationRule {
        @Override
        public List<ValidationIssue> validate(
                Map<String, String> versions,
                Map<String, Dependency> libraries,
                Set<Dependency> actualDependencies) {
            Set<String> usedVersions = libraries.values().stream()
                .map(Dependency::version)
                .collect(Collectors.toSet());
            
            return versions.entrySet().stream()
                .filter(e -> !usedVersions.contains(e.getValue()))
                .map(e -> new ValidationIssue(
                    ValidationSeverity.WARNING,
                    String.format("Unused version: %s", e.getKey())
                ))
                .collect(Collectors.toList());
        }
    }

    private static class InconsistentVersionRule implements ValidationRule {
        @Override
        public List<ValidationIssue> validate(
                Map<String, String> versions,
                Map<String, Dependency> libraries,
                Set<Dependency> actualDependencies) {
            return libraries.entrySet().stream()
                .filter(e -> {
                    Dependency catalogDep = e.getValue();
                    return actualDependencies.stream()
                        .anyMatch(dep -> dep.groupId().equals(catalogDep.groupId()) &&
                                       dep.artifactId().equals(catalogDep.artifactId()) &&
                                       !dep.version().equals(catalogDep.version()));
                })
                .map(e -> new ValidationIssue(
                    ValidationSeverity.WARNING,
                    String.format("Version mismatch for %s: catalog=%s, actual=%s",
                        e.getKey(),
                        e.getValue().version(),
                        findActualVersion(e.getValue(), actualDependencies))
                ))
                .collect(Collectors.toList());
        }

        private String findActualVersion(Dependency catalogDep, Set<Dependency> actualDeps) {
            return actualDeps.stream()
                .filter(dep -> dep.groupId().equals(catalogDep.groupId()) &&
                             dep.artifactId().equals(catalogDep.artifactId()))
                .map(Dependency::version)
                .findFirst()
                .orElse("unknown");
        }
    }

    public enum ValidationSeverity {
        ERROR,
        WARNING
    }

    public record ValidationIssue(ValidationSeverity severity, String message) {}

    public record ValidationResult(List<ValidationIssue> issues) {
        public boolean hasErrors() {
            return issues.stream()
                .anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
        }

        public List<ValidationIssue> getErrors() {
            return issues.stream()
                .filter(issue -> issue.severity() == ValidationSeverity.ERROR)
                .collect(Collectors.toList());
        }

        public List<ValidationIssue> getWarnings() {
            return issues.stream()
                .filter(issue -> issue.severity() == ValidationSeverity.WARNING)
                .collect(Collectors.toList());
        }
    }
} 