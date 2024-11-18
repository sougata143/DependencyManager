package org.infy.scanner.version;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionConstraint {
    private static final Pattern RANGE_PATTERN = Pattern.compile(
        "^([\\[\\(])(\\d+\\.\\d+\\.\\d+)\\s*,\\s*(\\d+\\.\\d+\\.\\d+)([\\]\\)])$"
    );
    private static final Pattern CARET_PATTERN = Pattern.compile(
        "^\\^(\\d+\\.\\d+\\.\\d+)$"
    );
    private static final Pattern TILDE_PATTERN = Pattern.compile(
        "^~(\\d+\\.\\d+\\.\\d+)$"
    );

    private final ConstraintType type;
    private final String value;
    private final SemanticVersion minVersion;
    private final SemanticVersion maxVersion;
    private final boolean includeMin;
    private final boolean includeMax;

    public VersionConstraint(String constraint) {
        Matcher rangeMatcher = RANGE_PATTERN.matcher(constraint);
        Matcher caretMatcher = CARET_PATTERN.matcher(constraint);
        Matcher tildeMatcher = TILDE_PATTERN.matcher(constraint);

        if (rangeMatcher.matches()) {
            this.type = ConstraintType.RANGE;
            this.value = constraint;
            this.minVersion = new SemanticVersion(rangeMatcher.group(2));
            this.maxVersion = new SemanticVersion(rangeMatcher.group(3));
            this.includeMin = "[".equals(rangeMatcher.group(1));
            this.includeMax = "]".equals(rangeMatcher.group(4));
        } else if (caretMatcher.matches()) {
            this.type = ConstraintType.CARET;
            this.value = constraint;
            this.minVersion = new SemanticVersion(caretMatcher.group(1));
            this.maxVersion = calculateCaretUpperBound(this.minVersion);
            this.includeMin = true;
            this.includeMax = false;
        } else if (tildeMatcher.matches()) {
            this.type = ConstraintType.TILDE;
            this.value = constraint;
            this.minVersion = new SemanticVersion(tildeMatcher.group(1));
            this.maxVersion = calculateTildeUpperBound(this.minVersion);
            this.includeMin = true;
            this.includeMax = false;
        } else {
            this.type = ConstraintType.EXACT;
            this.value = constraint;
            this.minVersion = new SemanticVersion(constraint);
            this.maxVersion = this.minVersion;
            this.includeMin = true;
            this.includeMax = true;
        }
    }

    public boolean isSatisfiedBy(SemanticVersion version) {
        int compareMin = version.compareTo(minVersion);
        int compareMax = version.compareTo(maxVersion);

        switch (type) {
            case EXACT:
                return compareMin == 0;
            case RANGE:
                boolean satisfiesMin = includeMin ? compareMin >= 0 : compareMin > 0;
                boolean satisfiesMax = includeMax ? compareMax <= 0 : compareMax < 0;
                return satisfiesMin && satisfiesMax;
            case CARET:
                return compareMin >= 0 && compareMax < 0;
            case TILDE:
                return compareMin >= 0 && compareMax < 0;
            default:
                return false;
        }
    }

    private SemanticVersion calculateCaretUpperBound(SemanticVersion version) {
        if (version.getMajor() > 0) {
            return new SemanticVersion(
                (version.getMajor() + 1) + ".0.0"
            );
        } else if (version.getMinor() > 0) {
            return new SemanticVersion(
                "0." + (version.getMinor() + 1) + ".0"
            );
        } else {
            return new SemanticVersion(
                "0.0." + (version.getPatch() + 1)
            );
        }
    }

    private SemanticVersion calculateTildeUpperBound(SemanticVersion version) {
        return new SemanticVersion(
            version.getMajor() + "." + (version.getMinor() + 1) + ".0"
        );
    }

    public ConstraintType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public SemanticVersion getMinVersion() {
        return minVersion;
    }

    public SemanticVersion getMaxVersion() {
        return maxVersion;
    }

    public boolean isIncludeMin() {
        return includeMin;
    }

    public boolean isIncludeMax() {
        return includeMax;
    }

    public enum ConstraintType {
        EXACT,
        RANGE,
        CARET,
        TILDE
    }

    @Override
    public String toString() {
        return value;
    }
} 