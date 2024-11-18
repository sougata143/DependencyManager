package org.infy.scanner.gradle.constraints;

import org.gradle.tooling.model.GradleModuleVersion;
import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DependencyConstraintHandler {
    private static final Logger logger = LoggerFactory.getLogger(DependencyConstraintHandler.class);
    private final Map<String, DependencyConstraint> constraints = new HashMap<>();
    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile(
        "constraints\\s*\\{([^}]+)}"
    );

    public void parseConstraints(String buildScript) {
        Matcher matcher = CONSTRAINT_PATTERN.matcher(buildScript);
        while (matcher.find()) {
            String constraintsBlock = matcher.group(1);
            parseConstraintsBlock(constraintsBlock);
        }
    }

    private void parseConstraintsBlock(String block) {
        Pattern constraintPattern = Pattern.compile(
            "(\\w+)\\s*[(']([^'\"]+)['\")]\\s*\\{([^}]+)\\}"
        );
        
        Matcher matcher = constraintPattern.matcher(block);
        while (matcher.find()) {
            String configuration = matcher.group(1);
            String dependency = matcher.group(2);
            String constraintBlock = matcher.group(3);
            
            DependencyConstraint constraint = parseConstraint(dependency, constraintBlock);
            if (constraint != null) {
                constraints.put(dependency, constraint);
            }
        }
    }

    private DependencyConstraint parseConstraint(String dependency, String block) {
        String version = null;
        String reason = null;
        boolean strict = false;
        boolean reject = false;

        Pattern versionPattern = Pattern.compile("version\\s*['\"]([^'\"]+)['\"]");
        Pattern reasonPattern = Pattern.compile("because\\s*['\"]([^'\"]+)['\"]");
        
        Matcher versionMatcher = versionPattern.matcher(block);
        if (versionMatcher.find()) {
            version = versionMatcher.group(1);
        }

        Matcher reasonMatcher = reasonPattern.matcher(block);
        if (reasonMatcher.find()) {
            reason = reasonMatcher.group(1);
        }

        strict = block.contains("strictly");
        reject = block.contains("reject");

        return new DependencyConstraint(dependency, version, reason, strict, reject);
    }

    public void applyConstraints(Set<Dependency> dependencies) {
        dependencies.forEach(this::applyConstraint);
    }

    private void applyConstraint(Dependency dependency) {
        String key = dependency.groupId() + ":" + dependency.artifactId();
        DependencyConstraint constraint = constraints.get(key);
        
        if (constraint != null) {
            if (constraint.reject() && constraint.version().equals(dependency.version())) {
                logger.warn("Rejected dependency version: {} ({})", 
                    dependency, constraint.reason());
                // You might want to throw an exception or handle rejected dependencies differently
            }
            
            if (constraint.strict() && !constraint.version().equals(dependency.version())) {
                logger.error("Strict version violation: {} requires {} ({})", 
                    dependency, constraint.version(), constraint.reason());
                // Handle strict version violation
            }
        }
    }

    record DependencyConstraint(
        String dependency,
        String version,
        String reason,
        boolean strict,
        boolean reject
    ) {}
} 