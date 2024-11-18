package org.infy.scanner.gradle.versioning;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionConstraint {
    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile(
        "([\\[\\(])?([^,\\]\\)]+)?(?:,\\s*([^\\]\\)]+))?([\\]\\)])?"
    );

    private final List<VersionRange> ranges;
    private final boolean strict;
    private final String preferredVersion;
    private final List<String> rejectedVersions;

    public VersionConstraint(String constraint) {
        this.ranges = new ArrayList<>();
        this.rejectedVersions = new ArrayList<>();
        this.strict = constraint.startsWith("strictly ");
        
        String constraintStr = strict ? constraint.substring(9) : constraint;
        if (constraintStr.startsWith("prefer ")) {
            this.preferredVersion = parsePreferredVersion(constraintStr);
            constraintStr = constraintStr.substring(constraintStr.indexOf(' ', 7) + 1);
        } else {
            this.preferredVersion = null;
        }

        parseConstraint(constraintStr);
    }

    private String parsePreferredVersion(String constraint) {
        int start = constraint.indexOf("prefer ") + 7;
        int end = constraint.indexOf(' ', start);
        return end > start ? constraint.substring(start, end) : constraint.substring(start);
    }

    private void parseConstraint(String constraint) {
        String[] parts = constraint.split("\\|\\|");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("!")) {
                rejectedVersions.add(part.substring(1).trim());
            } else {
                Matcher matcher = CONSTRAINT_PATTERN.matcher(part);
                if (matcher.matches()) {
                    ranges.add(createRange(matcher));
                }
            }
        }
    }

    private VersionRange createRange(Matcher matcher) {
        String lowerBound = matcher.group(2);
        String upperBound = matcher.group(3);
        boolean includeLower = "[".equals(matcher.group(1));
        boolean includeUpper = "]".equals(matcher.group(4));

        if (upperBound == null) {
            // Single version constraint
            return new VersionRange(
                String.format("%s%s,%s%s",
                    includeLower ? "[" : "(",
                    lowerBound,
                    lowerBound,
                    includeUpper ? "]" : ")")
            );
        }

        return new VersionRange(
            String.format("%s%s,%s%s",
                includeLower ? "[" : "(",
                lowerBound,
                upperBound,
                includeUpper ? "]" : ")")
        );
    }

    public boolean isSatisfiedBy(RichVersion version) {
        // Check rejected versions
        if (rejectedVersions.stream().anyMatch(v -> v.equals(version.toString()))) {
            return false;
        }

        // If no ranges specified but has preferred version
        if (ranges.isEmpty() && preferredVersion != null) {
            return version.toString().equals(preferredVersion);
        }

        // Check ranges
        boolean satisfiesRange = ranges.isEmpty() || 
            ranges.stream().anyMatch(range -> range.contains(version));

        // For strict constraints, version must match exactly if preferred version is specified
        if (strict && preferredVersion != null) {
            return version.toString().equals(preferredVersion);
        }

        return satisfiesRange;
    }

    public String getPreferredVersion() {
        return preferredVersion;
    }

    public boolean isStrict() {
        return strict;
    }

    public List<String> getRejectedVersions() {
        return rejectedVersions;
    }
} 