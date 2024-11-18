package org.infy.scanner.gradle.versioning;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RichVersion implements Comparable<RichVersion> {
    private static final Pattern VERSION_PATTERN = Pattern.compile(
        "^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?" +  // Major.Minor.Patch
        "(?:-([\\w.-]+))?" +                      // Pre-release
        "(?:\\+([\\w.-]+))?$"                     // Build metadata
    );

    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String buildMetadata;

    public RichVersion(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version format: " + version);
        }

        this.major = Integer.parseInt(matcher.group(1));
        this.minor = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        this.patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
        this.preRelease = matcher.group(4);
        this.buildMetadata = matcher.group(5);
    }

    public boolean isStable() {
        return preRelease == null;
    }

    public boolean isPreRelease() {
        return preRelease != null;
    }

    public boolean isCompatibleWith(RichVersion other) {
        return this.major == other.major;
    }

    public boolean satisfies(VersionConstraint constraint) {
        return constraint.isSatisfiedBy(this);
    }

    @Override
    public int compareTo(RichVersion other) {
        int majorCmp = Integer.compare(major, other.major);
        if (majorCmp != 0) return majorCmp;

        int minorCmp = Integer.compare(minor, other.minor);
        if (minorCmp != 0) return minorCmp;

        int patchCmp = Integer.compare(patch, other.patch);
        if (patchCmp != 0) return patchCmp;

        // Pre-release versions have lower precedence
        if (preRelease == null && other.preRelease != null) return 1;
        if (preRelease != null && other.preRelease == null) return -1;
        if (preRelease != null && other.preRelease != null) {
            return preRelease.compareTo(other.preRelease);
        }

        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RichVersion that = (RichVersion) o;
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
        StringBuilder sb = new StringBuilder()
            .append(major)
            .append('.')
            .append(minor)
            .append('.')
            .append(patch);
        
        if (preRelease != null) {
            sb.append('-').append(preRelease);
        }
        if (buildMetadata != null) {
            sb.append('+').append(buildMetadata);
        }
        
        return sb.toString();
    }
} 