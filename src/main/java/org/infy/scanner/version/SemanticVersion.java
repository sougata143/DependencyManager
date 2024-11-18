package org.infy.scanner.version;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SemanticVersion implements Comparable<SemanticVersion> {
    private static final Pattern SEMVER_PATTERN = Pattern.compile(
        "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
        "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)" +
        "(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))" +
        "?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
    );

    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String buildMetadata;

    public SemanticVersion(String version) {
        Matcher matcher = SEMVER_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid semantic version: " + version);
        }

        this.major = Integer.parseInt(matcher.group(1));
        this.minor = Integer.parseInt(matcher.group(2));
        this.patch = Integer.parseInt(matcher.group(3));
        this.preRelease = matcher.group(4);
        this.buildMetadata = matcher.group(5);
    }

    public boolean isCompatibleWith(SemanticVersion other) {
        // Major version must match for compatibility
        if (this.major != other.major) {
            return false;
        }

        // If major is 0, minor must match (breaking changes expected)
        if (this.major == 0 && this.minor != other.minor) {
            return false;
        }

        return true;
    }

//    public boolean satisfies(VersionConstraint constraint) {
    ////        return constraint.isSatisfiedBy(this);
    ////    }

    public boolean isPreRelease() {
        return preRelease != null;
    }

    public boolean isStable() {
        return major > 0 && !isPreRelease();
    }

    @Override
    public int compareTo(SemanticVersion other) {
        // Compare major versions
        int result = Integer.compare(this.major, other.major);
        if (result != 0) return result;

        // Compare minor versions
        result = Integer.compare(this.minor, other.minor);
        if (result != 0) return result;

        // Compare patch versions
        result = Integer.compare(this.patch, other.patch);
        if (result != 0) return result;

        // Pre-release versions have lower precedence than normal versions
        if (this.preRelease == null && other.preRelease != null) return 1;
        if (this.preRelease != null && other.preRelease == null) return -1;
        if (this.preRelease != null && other.preRelease != null) {
            return comparePreRelease(this.preRelease, other.preRelease);
        }

        return 0;
    }

    private int comparePreRelease(String pr1, String pr2) {
        String[] parts1 = pr1.split("\\.");
        String[] parts2 = pr2.split("\\.");

        int length = Math.min(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            String part1 = parts1[i];
            String part2 = parts2[i];

            boolean isNum1 = part1.matches("\\d+");
            boolean isNum2 = part2.matches("\\d+");

            if (isNum1 && isNum2) {
                int num1 = Integer.parseInt(part1);
                int num2 = Integer.parseInt(part2);
                int result = Integer.compare(num1, num2);
                if (result != 0) return result;
            } else if (isNum1) {
                return -1; // Numeric has lower precedence
            } else if (isNum2) {
                return 1;
            } else {
                int result = part1.compareTo(part2);
                if (result != 0) return result;
            }
        }

        return Integer.compare(parts1.length, parts2.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SemanticVersion that = (SemanticVersion) o;
        return major == that.major &&
               minor == that.minor &&
               patch == that.patch &&
               Objects.equals(preRelease, that.preRelease);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(major).append('.').append(minor).append('.').append(patch);
        if (preRelease != null) {
            sb.append('-').append(preRelease);
        }
        if (buildMetadata != null) {
            sb.append('+').append(buildMetadata);
        }
        return sb.toString();
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    public String getPreRelease() {
        return preRelease;
    }

    public String getBuildMetadata() {
        return buildMetadata;
    }

    public static boolean isValid(String version) {
        return SEMVER_PATTERN.matcher(version).matches();
    }

    public static SemanticVersion parse(String version) {
        try {
            return new SemanticVersion(version);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
} 