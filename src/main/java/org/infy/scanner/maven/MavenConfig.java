package org.infy.scanner.maven;

import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class MavenConfig {
    private static final Logger logger = LoggerFactory.getLogger(MavenConfig.class);
    private final Settings settings;

    public MavenConfig() {
        try {
            DefaultSettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();
            DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
            
            // Load global settings
            Path globalSettings = Path.of(System.getenv("MAVEN_HOME"), "conf", "settings.xml");
            if (globalSettings.toFile().exists()) {
                request.setGlobalSettingsFile(globalSettings.toFile());
            }
            
            // Load user settings
            Path userSettings = Path.of(System.getProperty("user.home"), ".m2", "settings.xml");
            if (userSettings.toFile().exists()) {
                request.setUserSettingsFile(userSettings.toFile());
            }
            
            this.settings = settingsBuilder.build(request).getEffectiveSettings();
        } catch (SettingsBuildingException e) {
            logger.error("Failed to load Maven settings", e);
            throw new RuntimeException("Failed to load Maven settings", e);
        }
    }

    public Settings getSettings() {
        return settings;
    }
} 