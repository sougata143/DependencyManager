package org.infy.scanner.version;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class UpgradePathAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(UpgradePathAnalyzer.class);
    
    private final VersionStabilityChecker stabilityChecker;
    private final boolean preferStable;
    private final boolean allowMajorUpgrades;
    private final boolean allowDowngrades;

    public UpgradePathAnalyzer(boolean preferStable, boolean allowMajorUpgrades, boolean allowDowngrades) {
        this.stabilityChecker = new VersionStabilityChecker();
        this.preferStable = preferStable;
        this.allowMajorUpgrades = allowMajorUpgrades;
        this.allowDowngrades = allowDowngrades;
    }

    public List<UpgradePath> analyzeUpgradePath(Dependency current, Set<String> availableVersions) {
        List<UpgradePath> paths = new ArrayList<>();
        SemanticVersion currentVersion = SemanticVersion.parse(current.version());
        
        if (currentVersion == null) {
            logger.warn("Current version {} is not a semantic version", current.version());
            return paths;
        }

        // Filter and sort available versions
        List<SemanticVersion> validVersions = availableVersions.stream()
            .map(SemanticVersion::parse)
            .filter(Objects::nonNull)
            .filter(v -> isValidUpgrade(currentVersion, v))
            .sorted()
            .collect(Collectors.toList());

        // Generate upgrade paths
        SemanticVersion previous = currentVersion;
        List<VersionStep> steps = new ArrayList<>();
        
        for (SemanticVersion version : validVersions) {
            if (version.compareTo(currentVersion) <= 0) {
                continue;
            }

            UpgradeType type = determineUpgradeType(previous, version);
            BreakingChangeRisk risk = assessBreakingChangeRisk(previous, version);
            StabilityChange stabilityChange = assessStabilityChange(previous, version);

            steps.add(new VersionStep(
                version.toString(),
                type,
                risk,
                stabilityChange,
                generateChangeDescription(previous, version)
            ));

            previous = version;
        }

        if (!steps.isEmpty()) {
            paths.add(new UpgradePath(current.version(), steps));
        }

        return paths;
    }

    private boolean isValidUpgrade(SemanticVersion current, SemanticVersion target) {
        if (!allowDowngrades && target.compareTo(current) < 0) {
            return false;
        }

        if (!allowMajorUpgrades && target.getMajor() > current.getMajor()) {
            return false;
        }

        if (preferStable && !stabilityChecker.isStable(target.toString())) {
            return false;
        }

        return true;
    }

    private UpgradeType determineUpgradeType(SemanticVersion from, SemanticVersion to) {
        if (to.getMajor() > from.getMajor()) {
            return UpgradeType.MAJOR;
        }
        if (to.getMinor() > from.getMinor()) {
            return UpgradeType.MINOR;
        }
        if (to.getPatch() > from.getPatch()) {
            return UpgradeType.PATCH;
        }
        return UpgradeType.OTHER;
    }

    private BreakingChangeRisk assessBreakingChangeRisk(SemanticVersion from, SemanticVersion to) {
        if (to.getMajor() > from.getMajor()) {
            return BreakingChangeRisk.HIGH;
        }
        if (to.getMajor() == 0 && to.getMinor() > from.getMinor()) {
            return BreakingChangeRisk.HIGH;
        }
        if (to.getMinor() > from.getMinor()) {
            return BreakingChangeRisk.MEDIUM;
        }
        return BreakingChangeRisk.LOW;
    }

    private StabilityChange assessStabilityChange(SemanticVersion from, SemanticVersion to) {
        VersionStabilityChecker.StabilityLevel fromStability = 
            stabilityChecker.checkStability(from.toString());
        VersionStabilityChecker.StabilityLevel toStability = 
            stabilityChecker.checkStability(to.toString());

        if (toStability.isMoreStableThan(fromStability)) {
            return StabilityChange.MORE_STABLE;
        }
        if (fromStability.isMoreStableThan(toStability)) {
            return StabilityChange.LESS_STABLE;
        }
        return StabilityChange.UNCHANGED;
    }

    private String generateChangeDescription(SemanticVersion from, SemanticVersion to) {
        StringBuilder description = new StringBuilder();
        
        if (to.getMajor() > from.getMajor()) {
            description.append("Major version upgrade with potential breaking changes. ");
        } else if (to.getMinor() > from.getMinor()) {
            description.append("Minor version upgrade with new features. ");
        } else if (to.getPatch() > from.getPatch()) {
            description.append("Patch update with bug fixes. ");
        }

        StabilityChange stabilityChange = assessStabilityChange(from, to);
        if (stabilityChange != StabilityChange.UNCHANGED) {
            description.append("Stability ").append(stabilityChange.toString().toLowerCase());
        }

        return description.toString().trim();
    }

    public record UpgradePath(String currentVersion, List<VersionStep> steps) {}

    public record VersionStep(
        String version,
        UpgradeType type,
        BreakingChangeRisk risk,
        StabilityChange stabilityChange,
        String description
    ) {}

    public enum UpgradeType {
        MAJOR,
        MINOR,
        PATCH,
        OTHER
    }

    public enum BreakingChangeRisk {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum StabilityChange {
        MORE_STABLE,
        LESS_STABLE,
        UNCHANGED
    }
} 