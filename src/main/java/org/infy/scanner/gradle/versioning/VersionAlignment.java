package org.infy.scanner.gradle.versioning;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class VersionAlignment {
    private static final Logger logger = LoggerFactory.getLogger(VersionAlignment.class);
    
    private final Map<String, AlignmentGroup> alignmentGroups = new HashMap<>();

    public void addAlignmentGroup(String name, String groupPattern) {
        alignmentGroups.put(name, new AlignmentGroup(name, groupPattern));
    }

    public Set<Dependency> alignVersions(Set<Dependency> dependencies) {
        Set<Dependency> aligned = new HashSet<>(dependencies);
        
        for (AlignmentGroup group : alignmentGroups.values()) {
            Set<Dependency> groupDependencies = dependencies.stream()
                .filter(group::matches)
                .collect(Collectors.toSet());
            
            if (!groupDependencies.isEmpty()) {
                String targetVersion = determineTargetVersion(groupDependencies);
                
                for (Dependency dep : groupDependencies) {
                    if (!dep.version().equals(targetVersion)) {
                        aligned.remove(dep);
                        aligned.add(new Dependency(
                            dep.groupId(),
                            dep.artifactId(),
                            targetVersion,
                            dep.scope(),
                            dep.isDirectDependency()
                        ));
                        
                        logger.info("Aligned {} from version {} to {}",
                            dep.groupId() + ":" + dep.artifactId(),
                            dep.version(),
                            targetVersion);
                    }
                }
            }
        }
        
        return aligned;
    }

    private String determineTargetVersion(Set<Dependency> dependencies) {
        return dependencies.stream()
            .map(Dependency::version)
            .max(VersionComparator::compare)
            .orElseThrow();
    }

    private static class AlignmentGroup {
        private final String name;
        private final String groupPattern;

        public AlignmentGroup(String name, String groupPattern) {
            this.name = name;
            this.groupPattern = groupPattern.replace("*", ".*");
        }

        public boolean matches(Dependency dependency) {
            return dependency.groupId().matches(groupPattern);
        }
    }

    private static class VersionComparator {
        public static int compare(String v1, String v2) {
            List<String> parts1 = splitVersion(v1);
            List<String> parts2 = splitVersion(v2);

            for (int i = 0; i < Math.min(parts1.size(), parts2.size()); i++) {
                int cmp = compareVersionParts(parts1.get(i), parts2.get(i));
                if (cmp != 0) return cmp;
            }

            return Integer.compare(parts1.size(), parts2.size());
        }

        private static List<String> splitVersion(String version) {
            return Arrays.asList(version.split("[.-]"));
        }

        private static int compareVersionParts(String p1, String p2) {
            try {
                return Integer.compare(Integer.parseInt(p1), Integer.parseInt(p2));
            } catch (NumberFormatException e) {
                return p1.compareTo(p2);
            }
        }
    }
} 