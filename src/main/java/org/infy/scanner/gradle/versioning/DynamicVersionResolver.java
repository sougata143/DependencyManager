package org.infy.scanner.gradle.versioning;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

public class DynamicVersionResolver {
    private static final Logger logger = LoggerFactory.getLogger(DynamicVersionResolver.class);
    
    private static final Pattern DYNAMIC_VERSION_PATTERN = Pattern.compile(
        "^(\\d+(?:\\.\\d+)*)?\\+$|^latest\\.(?:release|integration)$|^\\[.*\\]$|^\\(.*\\)$"
    );

    private final Map<String, NavigableSet<RichVersion>> versionCache = new HashMap<>();

    public String resolveDynamicVersion(Dependency dependency, Set<String> availableVersions) {
        String version = dependency.version();
        if (!isDynamicVersion(version)) {
            return version;
        }

        String key = dependency.groupId() + ":" + dependency.artifactId();
        NavigableSet<RichVersion> versions = versionCache.computeIfAbsent(key, k -> {
            TreeSet<RichVersion> set = new TreeSet<>();
            availableVersions.forEach(v -> {
                try {
                    set.add(new RichVersion(v));
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid version format: {}", v);
                }
            });
            return set;
        });

        return resolveVersion(version, versions);
    }

    private boolean isDynamicVersion(String version) {
        return DYNAMIC_VERSION_PATTERN.matcher(version).matches();
    }

    private String resolveVersion(String dynamicVersion, NavigableSet<RichVersion> versions) {
        if (dynamicVersion.equals("latest.release")) {
            return versions.stream()
                .filter(RichVersion::isStable)
                .max(RichVersion::compareTo)
                .map(RichVersion::toString)
                .orElse(null);
        }

        if (dynamicVersion.equals("latest.integration")) {
            return versions.isEmpty() ? null : versions.last().toString();
        }

        if (dynamicVersion.endsWith("+")) {
            String prefix = dynamicVersion.substring(0, dynamicVersion.length() - 1);
            return versions.stream()
                .filter(v -> v.toString().startsWith(prefix))
                .max(RichVersion::compareTo)
                .map(RichVersion::toString)
                .orElse(null);
        }

        try {
            VersionRange range = new VersionRange(dynamicVersion);
            return versions.stream()
                .filter(range::contains)
                .max(RichVersion::compareTo)
                .map(RichVersion::toString)
                .orElse(null);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid version range: {}", dynamicVersion);
            return null;
        }
    }
} 