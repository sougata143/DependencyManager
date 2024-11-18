package org.infy.scanner.gradle.model;

import java.util.HashMap;
import java.util.Map;

public class GradleConfiguration {
    private static final Map<String, GradleConfiguration> STANDARD_CONFIGURATIONS = new HashMap<>();
    private static final Map<String, GradleConfiguration> CUSTOM_CONFIGURATIONS = new HashMap<>();
    
    private final String configurationName;
    private final String extendsFrom;
    private final boolean isCustom;

    // Standard configurations
    public static final GradleConfiguration IMPLEMENTATION = 
        register("implementation", null, false);
    public static final GradleConfiguration COMPILE_ONLY = 
        register("compileOnly", null, false);
    public static final GradleConfiguration RUNTIME_ONLY = 
        register("runtimeOnly", null, false);
    public static final GradleConfiguration API = 
        register("api", null, false);
    public static final GradleConfiguration COMPILE_CLASSPATH = 
        register("compileClasspath", null, false);
    public static final GradleConfiguration RUNTIME_CLASSPATH = 
        register("runtimeClasspath", null, false);
    public static final GradleConfiguration TEST_IMPLEMENTATION = 
        register("testImplementation", null, false);
    public static final GradleConfiguration TEST_COMPILE_ONLY = 
        register("testCompileOnly", null, false);
    public static final GradleConfiguration TEST_RUNTIME_ONLY = 
        register("testRuntimeOnly", null, false);
    public static final GradleConfiguration PLATFORM = 
        register("platform", null, false);
    public static final GradleConfiguration ENFORCED_PLATFORM = 
        register("enforcedPlatform", null, false);
    public static final GradleConfiguration ANNOTATION_PROCESSOR = 
        register("annotationProcessor", null, false);

    private GradleConfiguration(String configurationName, String extendsFrom, boolean isCustom) {
        this.configurationName = configurationName;
        this.extendsFrom = extendsFrom;
        this.isCustom = isCustom;
    }

    private static GradleConfiguration register(String name, String extendsFrom, boolean isCustom) {
        GradleConfiguration config = new GradleConfiguration(name, extendsFrom, isCustom);
        if (isCustom) {
            CUSTOM_CONFIGURATIONS.put(name.toLowerCase(), config);
        } else {
            STANDARD_CONFIGURATIONS.put(name.toLowerCase(), config);
        }
        return config;
    }

    public String getConfigurationName() {
        return configurationName;
    }

    public String getExtendsFrom() {
        return extendsFrom;
    }

    public boolean isCustom() {
        return isCustom;
    }

    public static GradleConfiguration fromString(String configName) {
        String key = configName.toLowerCase();
        // Check standard configurations first
        GradleConfiguration config = STANDARD_CONFIGURATIONS.get(key);
        if (config != null) {
            return config;
        }
        // Check custom configurations
        return CUSTOM_CONFIGURATIONS.get(key);
    }

    public static GradleConfiguration registerCustomConfiguration(String name, String extendsFrom) {
        return register(name, extendsFrom, true);
    }

    @Override
    public String toString() {
        return configurationName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GradleConfiguration that = (GradleConfiguration) o;
        return configurationName.equals(that.configurationName);
    }

    @Override
    public int hashCode() {
        return configurationName.hashCode();
    }
} 