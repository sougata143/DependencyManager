package org.infy.scanner.gradle.constraints;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlatformConstraintHandler {
    private static final Logger logger = LoggerFactory.getLogger(PlatformConstraintHandler.class);
    private final Map<String, PlatformConstraint> platformConstraints = new HashMap<>();
    
    private static final Pattern PLATFORM_CONSTRAINT_PATTERN = Pattern.compile(
        "platform\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)\\s*\\{([^}]+)\\}"
    );

    public void parsePlatformConstraints(String buildScript) {
        Matcher matcher = PLATFORM_CONSTRAINT_PATTERN.matcher(buildScript);
        while (matcher.find()) {
            String platformCoordinates = matcher.group(1);
            String constraintBlock = matcher.group(2);
            
            PlatformConstraint constraint = parsePlatformConstraint(platformCoordinates, constraintBlock);
            if (constraint != null) {
                platformConstraints.put(platformCoordinates, constraint);
            }
        }
    }

    private PlatformConstraint parsePlatformConstraint(String coordinates, String block) {
        Map<String, String> versionConstraints = new HashMap<>();
        boolean enforced = block.contains("enforce");
        
        Pattern versionPattern = Pattern.compile(
            "version\\s*\\(['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]\\s*\\)"
        );
        
        Matcher matcher = versionPattern.matcher(block);
        while (matcher.find()) {
            String module = matcher.group(1);
            String version = matcher.group(2);
            versionConstraints.put(module, version);
        }

        return new PlatformConstraint(coordinates, versionConstraints, enforced);
    }

    public void applyPlatformConstraints(Set<Dependency> dependencies) {
        platformConstraints.values().forEach(platform -> 
            applyPlatformConstraint(platform, dependencies));
    }

    private void applyPlatformConstraint(PlatformConstraint platform, Set<Dependency> dependencies) {
        platform.versionConstraints().forEach((module, version) -> {
            dependencies.stream()
                .filter(dep -> moduleMatches(dep, module))
                .forEach(dep -> {
                    if (platform.enforced() && !dep.version().equals(version)) {
                        logger.error("Platform version violation: {} requires version {} for {}",
                            platform.coordinates(), version, dep);
                        // Handle enforced platform violation
                    } else if (!platform.enforced() && !dep.version().equals(version)) {
                        logger.warn("Platform version recommendation: {} suggests version {} for {}",
                            platform.coordinates(), version, dep);
                    }
                });
        });
    }

    private boolean moduleMatches(Dependency dependency, String modulePattern) {
        String depCoordinates = dependency.groupId() + ":" + dependency.artifactId();
        return modulePattern.equals(depCoordinates) || 
               modulePattern.equals(dependency.groupId() + ":*");
    }

    record PlatformConstraint(
        String coordinates,
        Map<String, String> versionConstraints,
        boolean enforced
    ) {}
} 