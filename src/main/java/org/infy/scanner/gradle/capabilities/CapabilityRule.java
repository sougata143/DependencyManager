package org.infy.scanner.gradle.capabilities;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class CapabilityRule {
    private static final Logger logger = LoggerFactory.getLogger(CapabilityRule.class);
    
    private final String capability;
    private final Set<DependencySelector> selectors;
    private final ResolutionStrategy resolutionStrategy;

    public CapabilityRule(String capability, ResolutionStrategy resolutionStrategy) {
        this.capability = capability;
        this.selectors = new HashSet<>();
        this.resolutionStrategy = resolutionStrategy;
    }

    public void addSelector(String group, String module, String version) {
        selectors.add(new DependencySelector(group, module, version));
    }

    public boolean appliesTo(Dependency dependency) {
        return selectors.stream().anyMatch(selector -> selector.matches(dependency));
    }

    public Set<Dependency> resolve(Set<Dependency> candidates) {
        return resolutionStrategy.resolve(candidates);
    }

    private static class DependencySelector {
        private final Predicate<String> groupMatcher;
        private final Predicate<String> moduleMatcher;
        private final Predicate<String> versionMatcher;

        public DependencySelector(String group, String module, String version) {
            this.groupMatcher = createMatcher(group);
            this.moduleMatcher = createMatcher(module);
            this.versionMatcher = createMatcher(version);
        }

        private Predicate<String> createMatcher(String pattern) {
            if (pattern.equals("*")) {
                return s -> true;
            }
            if (pattern.contains("*")) {
                String regex = pattern.replace("*", ".*");
                Pattern p = Pattern.compile(regex);
                return s -> p.matcher(s).matches();
            }
            return pattern::equals;
        }

        public boolean matches(Dependency dependency) {
            return groupMatcher.test(dependency.groupId()) &&
                   moduleMatcher.test(dependency.artifactId()) &&
                   versionMatcher.test(dependency.version());
        }
    }

    public enum ResolutionStrategy {
        HIGHEST_VERSION((candidates) -> {
            return Set.of(candidates.stream()
                .max((a, b) -> compareVersions(a.version(), b.version()))
                .orElseThrow());
        }),
        LOWEST_VERSION((candidates) -> {
            return Set.of(candidates.stream()
                .min((a, b) -> compareVersions(a.version(), b.version()))
                .orElseThrow());
        }),
        PREFER_RELEASE((candidates) -> {
            return candidates.stream()
                .filter(d -> !d.version().contains("-"))
                .findFirst()
                .map(Set::of)
                .orElseGet(() -> Set.of(candidates.iterator().next()));
        });

        private final CapabilityResolver resolver;

        ResolutionStrategy(CapabilityResolver resolver) {
            this.resolver = resolver;
        }

        public Set<Dependency> resolve(Set<Dependency> candidates) {
            return resolver.resolve(candidates);
        }

        private static int compareVersions(String v1, String v2) {
            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");
            
            for (int i = 0; i < Math.min(parts1.length, parts2.length); i++) {
                int cmp = compareVersionParts(parts1[i], parts2[i]);
                if (cmp != 0) return cmp;
            }
            
            return Integer.compare(parts1.length, parts2.length);
        }

        private static int compareVersionParts(String p1, String p2) {
            try {
                return Integer.compare(Integer.parseInt(p1), Integer.parseInt(p2));
            } catch (NumberFormatException e) {
                return p1.compareTo(p2);
            }
        }
    }

    @FunctionalInterface
    interface CapabilityResolver {
        Set<Dependency> resolve(Set<Dependency> candidates);
    }
} 