package org.infy.scanner.gradle.catalog;

import org.infy.scanner.core.Dependency;
import org.infy.scanner.gradle.VersionCatalogHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionCatalogConstraintHandler {
    private static final Logger logger = LoggerFactory.getLogger(VersionCatalogConstraintHandler.class);
    private final Map<String, CatalogConstraint> catalogConstraints = new HashMap<>();
    private final VersionCatalogHandler versionCatalogHandler;
    
    private static final Pattern CATALOG_CONSTRAINT_PATTERN = Pattern.compile(
        "versionCatalog\\s*\\{([^}]+)\\}"
    );

    public VersionCatalogConstraintHandler(VersionCatalogHandler versionCatalogHandler) {
        this.versionCatalogHandler = versionCatalogHandler;
    }

    public void parseCatalogConstraints(String buildScript) {
        Matcher matcher = CATALOG_CONSTRAINT_PATTERN.matcher(buildScript);
        while (matcher.find()) {
            String constraintBlock = matcher.group(1);
            parseCatalogBlock(constraintBlock);
        }
    }

    private void parseCatalogBlock(String block) {
        Pattern aliasPattern = Pattern.compile(
            "alias\\s*\\(['\"]([^'\"]+)['\"]\\)\\s*\\{([^}]+)\\}"
        );
        
        Matcher matcher = aliasPattern.matcher(block);
        while (matcher.find()) {
            String alias = matcher.group(1);
            String constraintBlock = matcher.group(2);
            
            CatalogConstraint constraint = parseConstraint(alias, constraintBlock);
            if (constraint != null) {
                catalogConstraints.put(alias, constraint);
            }
        }
    }

    private CatalogConstraint parseConstraint(String alias, String block) {
        Pattern versionPattern = Pattern.compile("version\\s*['\"]([^'\"]+)['\"]");
        Pattern strictPattern = Pattern.compile("strictly\\s*['\"]([^'\"]+)['\"]");
        Pattern preferPattern = Pattern.compile("prefer\\s*['\"]([^'\"]+)['\"]");
        
        String version = null;
        String strictVersion = null;
        String preferredVersion = null;
        
        Matcher versionMatcher = versionPattern.matcher(block);
        if (versionMatcher.find()) {
            version = versionMatcher.group(1);
        }
        
        Matcher strictMatcher = strictPattern.matcher(block);
        if (strictMatcher.find()) {
            strictVersion = strictMatcher.group(1);
        }
        
        Matcher preferMatcher = preferPattern.matcher(block);
        if (preferMatcher.find()) {
            preferredVersion = preferMatcher.group(1);
        }

        return new CatalogConstraint(
            alias,
            version,
            strictVersion,
            preferredVersion
        );
    }

    public void applyCatalogConstraints(Set<Dependency> dependencies) {
        catalogConstraints.values().forEach(constraint -> {
            VersionCatalogHandler.DependencyAlias alias = 
                versionCatalogHandler.resolveDependencyAlias(constraint.alias());
                
            if (alias != null) {
                dependencies.stream()
                    .filter(dep -> matchesDependency(dep, alias))
                    .forEach(dep -> applyConstraint(dep, constraint));
            }
        });
    }

    private boolean matchesDependency(Dependency dependency, VersionCatalogHandler.DependencyAlias alias) {
        return dependency.groupId().equals(alias.group()) && 
               dependency.artifactId().equals(alias.name());
    }

    private void applyConstraint(Dependency dependency, CatalogConstraint constraint) {
        if (constraint.strictVersion() != null && 
            !dependency.version().equals(constraint.strictVersion())) {
            logger.error("Strict version violation for {}: required {}, found {}",
                dependency, constraint.strictVersion(), dependency.version());
            // Handle strict version violation
        } else if (constraint.version() != null && 
                   !dependency.version().equals(constraint.version())) {
            logger.warn("Version mismatch for {}: expected {}, found {}",
                dependency, constraint.version(), dependency.version());
        } else if (constraint.preferredVersion() != null && 
                   !dependency.version().equals(constraint.preferredVersion())) {
            logger.info("Preferred version {} available for {} (current: {})",
                constraint.preferredVersion(), dependency, dependency.version());
        }
    }

    record CatalogConstraint(
        String alias,
        String version,
        String strictVersion,
        String preferredVersion
    ) {}
} 