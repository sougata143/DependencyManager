package org.infy.scanner.gradle.versioning;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionRange {
    private static final Pattern RANGE_PATTERN = Pattern.compile(
        "^(\\[|\\()([^,]+),([^\\]\\)]+)(\\]|\\))$"
    );

    private final RichVersion lowerBound;
    private final RichVersion upperBound;
    private final boolean includeLower;
    private final boolean includeUpper;

    public VersionRange(String range) {
        Matcher matcher = RANGE_PATTERN.matcher(range.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version range format: " + range);
        }

        this.includeLower = matcher.group(1).equals("[");
        this.includeUpper = matcher.group(4).equals("]");
        this.lowerBound = new RichVersion(matcher.group(2).trim());
        this.upperBound = new RichVersion(matcher.group(3).trim());

        if (lowerBound.compareTo(upperBound) > 0) {
            throw new IllegalArgumentException("Lower bound must be less than or equal to upper bound");
        }
    }

    public boolean contains(RichVersion version) {
        int lowerCmp = version.compareTo(lowerBound);
        int upperCmp = version.compareTo(upperBound);

        return (includeLower ? lowerCmp >= 0 : lowerCmp > 0) &&
               (includeUpper ? upperCmp <= 0 : upperCmp < 0);
    }

    public boolean intersects(VersionRange other) {
        return !(this.upperBound.compareTo(other.lowerBound) < 0 ||
                other.upperBound.compareTo(this.lowerBound) < 0);
    }

    public VersionRange merge(VersionRange other) {
        if (!this.intersects(other)) {
            throw new IllegalArgumentException("Cannot merge non-intersecting ranges");
        }

        String lower = this.lowerBound.compareTo(other.lowerBound) <= 0 ?
            this.lowerBound.toString() : other.lowerBound.toString();
        String upper = this.upperBound.compareTo(other.upperBound) >= 0 ?
            this.upperBound.toString() : other.upperBound.toString();

        return new VersionRange(String.format("[%s,%s]", lower, upper));
    }

    @Override
    public String toString() {
        return String.format("%s%s,%s%s",
            includeLower ? "[" : "(",
            lowerBound,
            upperBound,
            includeUpper ? "]" : ")"
        );
    }
} 