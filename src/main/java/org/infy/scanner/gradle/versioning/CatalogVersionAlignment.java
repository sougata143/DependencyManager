package org.infy.scanner.gradle.versioning;

import org.infy.scanner.core.Dependency;
import org.infy.scanner.gradle.VersionCatalogHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

public class CatalogVersionAlignment {
    private static final Logger logger = LoggerFactory.getLogger(CatalogVersionAlignment.class);
    
    private final VersionCatalogHandler catalogHandler;
    private final Map<String, Set<AlignmentRule>> alignmentRules = new HashMap<>();

    public CatalogVersionAlignment(VersionCatalogHandler catalogHandler) {
        this.catalogHandler = catalogHandler;
    }

    public void addAlignmentRule(String alias, String groupPattern) {
        alignmentRules.computeIfAbsent(alias, k -> new HashSet<>())
            .add(new AlignmentRule(groupPattern));
    }

    public Set<Dependency> alignVersions(Set<Dependency> dependencies) {
        Set<Dependency> aligned = new HashSet<>(dependencies);
        
        for (Map.Entry<String, Set<AlignmentRule>> entry : alignmentRules.entrySet()) {
            String alias = entry.getKey();
            Set<AlignmentRule> rules = entry.getValue();
            
            VersionCatalogHandler.DependencyAlias catalogAlias = 
                catalogHandler.resolveDependencyAlias(alias);
            
            if (catalogAlias != null && catalogAlias.version() != null) {
                alignDependenciesWithCatalog(
                    aligned,
                    rules,
                    catalogAlias.version()
                );
            }
        }
        
        return aligned;
    }

    private void alignDependenciesWithCatalog(
            Set<Dependency> dependencies,
            Set<AlignmentRule> rules,
            String targetVersion) {
        
        Set<Dependency> toAlign = new HashSet<>();
        
        // Find dependencies matching the rules
        for (Dependency dep : dependencies) {
            if (rules.stream().anyMatch(rule -> rule.matches(dep))) {
                toAlign.add(dep);
            }
        }
        
        // Align versions
        for (Dependency dep : toAlign) {
            if (!dep.version().equals(targetVersion)) {
                dependencies.remove(dep);
                dependencies.add(new Dependency(
                    dep.groupId(),
                    dep.artifactId(),
                    targetVersion,
                    dep.scope(),
                    dep.isDirectDependency()
                ));
                
                logger.info("Aligned {} to catalog version {}", dep, targetVersion);
            }
        }
    }

    private static class AlignmentRule {
        private final Pattern groupPattern;

        public AlignmentRule(String groupPattern) {
            this.groupPattern = Pattern.compile(
                groupPattern.replace("*", ".*")
            );
        }

        public boolean matches(Dependency dependency) {
            return groupPattern.matcher(dependency.groupId()).matches();
        }
    }
} 