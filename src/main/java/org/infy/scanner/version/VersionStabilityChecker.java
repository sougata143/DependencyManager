package org.infy.scanner.version;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

public class VersionStabilityChecker {
    private static final Logger logger = LoggerFactory.getLogger(VersionStabilityChecker.class);
    
    private static final Pattern ALPHA_PATTERN = Pattern.compile(".*[.-]alpha[.\\d]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BETA_PATTERN = Pattern.compile(".*[.-]beta[.\\d]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RC_PATTERN = Pattern.compile(".*[.-]rc[.\\d]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SNAPSHOT_PATTERN = Pattern.compile(".*-SNAPSHOT$");
    private static final Pattern DEV_PATTERN = Pattern.compile(".*[.-]dev[.\\d]*$", Pattern.CASE_INSENSITIVE);

    public StabilityLevel checkStability(String version) {
        if (SNAPSHOT_PATTERN.matcher(version).matches()) {
            return StabilityLevel.SNAPSHOT;
        }
        if (ALPHA_PATTERN.matcher(version).matches()) {
            return StabilityLevel.ALPHA;
        }
        if (BETA_PATTERN.matcher(version).matches()) {
            return StabilityLevel.BETA;
        }
        if (RC_PATTERN.matcher(version).matches()) {
            return StabilityLevel.RELEASE_CANDIDATE;
        }
        if (DEV_PATTERN.matcher(version).matches()) {
            return StabilityLevel.DEVELOPMENT;
        }

        SemanticVersion semver = SemanticVersion.parse(version);
        if (semver != null) {
            if (semver.isPreRelease()) {
                return StabilityLevel.PRE_RELEASE;
            }
            if (semver.getMajor() == 0) {
                return StabilityLevel.EXPERIMENTAL;
            }
            return StabilityLevel.STABLE;
        }

        return StabilityLevel.UNKNOWN;
    }

    public boolean isStable(String version) {
        return checkStability(version) == StabilityLevel.STABLE;
    }

    public boolean isProductionReady(String version) {
        StabilityLevel level = checkStability(version);
        return level == StabilityLevel.STABLE || level == StabilityLevel.RELEASE_CANDIDATE;
    }

    public enum StabilityLevel {
        STABLE(0),
        RELEASE_CANDIDATE(1),
        BETA(2),
        ALPHA(3),
        PRE_RELEASE(4),
        EXPERIMENTAL(5),
        DEVELOPMENT(6),
        SNAPSHOT(7),
        UNKNOWN(8);

        private final int order;

        StabilityLevel(int order) {
            this.order = order;
        }

        public int getOrder() {
            return order;
        }

        public boolean isMoreStableThan(StabilityLevel other) {
            return this.order < other.order;
        }
    }
} 