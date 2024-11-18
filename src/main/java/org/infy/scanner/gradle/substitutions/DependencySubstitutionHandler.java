package org.infy.scanner.gradle.substitutions;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DependencySubstitutionHandler {
    private static final Logger logger = LoggerFactory.getLogger(DependencySubstitutionHandler.class);
    private final List<SubstitutionRule> substitutionRules = new ArrayList<>();
    
    private static final Pattern SUBSTITUTION_PATTERN = Pattern.compile(
        "substitute\\s*\\(module\\s*\\(['\"]([^'\"]+)['\"]\\s*\\)\\)\\s*\\.with\\s*\\(module\\s*\\(['\"]([^'\"]+)['\"]\\s*\\)\\)"
    );

    public void parseSubstitutions(String buildScript) {
        Matcher matcher = SUBSTITUTION_PATTERN.matcher(buildScript);
        while (matcher.find()) {
            String original = matcher.group(1);
            String substitute = matcher.group(2);
            substitutionRules.add(new SubstitutionRule(original, substitute));
        }
    }

    public Set<Dependency> applySubstitutions(Set<Dependency> dependencies) {
        Set<Dependency> substitutedDependencies = new HashSet<>(dependencies);
        
        for (SubstitutionRule rule : substitutionRules) {
            dependencies.stream()
                .filter(dep -> matchesSubstitutionPattern(dep, rule.original()))
                .forEach(dep -> {
                    Dependency substituted = createSubstitutedDependency(dep, rule);
                    substitutedDependencies.remove(dep);
                    substitutedDependencies.add(substituted);
                    logger.info("Substituted dependency: {} -> {}", dep, substituted);
                });
        }
        
        return substitutedDependencies;
    }

    private boolean matchesSubstitutionPattern(Dependency dependency, String pattern) {
        String[] patternParts = pattern.split(":");
        String[] depParts = {dependency.groupId(), dependency.artifactId(), dependency.version()};
        
        if (patternParts.length != depParts.length) {
            return false;
        }
        
        for (int i = 0; i < patternParts.length; i++) {
            if (!patternParts[i].equals("*") && !patternParts[i].equals(depParts[i])) {
                return false;
            }
        }
        
        return true;
    }

    private Dependency createSubstitutedDependency(Dependency original, SubstitutionRule rule) {
        String[] substituteParts = rule.substitute().split(":");
        String[] originalParts = rule.original().split(":");
        
        String groupId = substituteParts[0].equals("*") ? original.groupId() : substituteParts[0];
        String artifactId = substituteParts[1].equals("*") ? original.artifactId() : substituteParts[1];
        String version = substituteParts[2].equals("*") ? original.version() : substituteParts[2];
        
        return new Dependency(
            groupId,
            artifactId,
            version,
            original.scope(),
            original.isDirectDependency()
        );
    }

    record SubstitutionRule(String original, String substitute) {}
} 