package org.infy.scanner.gradle.composite;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompositeSubstitutionHandler {
    private static final Logger logger = LoggerFactory.getLogger(CompositeSubstitutionHandler.class);
    private final Map<String, SubstitutionRule> substitutions = new HashMap<>();
    
    private static final Pattern COMPOSITE_SUBSTITUTION_PATTERN = Pattern.compile(
        "includeBuild\\s*['\"]([^'\"]+)['\"]\\s*\\{([^}]+)\\}"
    );

    public void parseCompositeSubstitutions(String settingsScript, Path rootPath) {
        Matcher matcher = COMPOSITE_SUBSTITUTION_PATTERN.matcher(settingsScript);
        while (matcher.find()) {
            String includePath = matcher.group(1);
            String substitutionBlock = matcher.group(2);
            
            Path includedBuildPath = rootPath.resolve(includePath).normalize();
            parseSubstitutionBlock(substitutionBlock, includedBuildPath);
        }
    }

    private void parseSubstitutionBlock(String block, Path buildPath) {
        Pattern substitutionPattern = Pattern.compile(
            "substitute\\s*['\"]([^'\"]+)['\"]\\s*with\\s*project\\s*['\"]([^'\"]+)['\"]"
        );
        
        Matcher matcher = substitutionPattern.matcher(block);
        while (matcher.find()) {
            String module = matcher.group(1);
            String project = matcher.group(2);
            substitutions.put(module, new SubstitutionRule(module, project, buildPath));
        }
    }

    public void applySubstitutions(Set<Dependency> dependencies) {
        dependencies.forEach(this::applySubstitution);
    }

    private void applySubstitution(Dependency dependency) {
        String key = dependency.groupId() + ":" + dependency.artifactId();
        SubstitutionRule rule = substitutions.get(key);
        
        if (rule != null) {
            logger.info("Applying composite build substitution: {} -> {} in {}",
                key, rule.projectPath(), rule.buildPath());
            // Here you would typically update the dependency to point to the local project
            // This might involve creating a new Dependency object with updated coordinates
        }
    }

    record SubstitutionRule(
        String module,
        String projectPath,
        Path buildPath
    ) {}
} 