package org.infy.scanner.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class NvdConfig {
    private static final String CONFIG_FILE = "nvd-config.properties";
    private final Properties properties;

    public NvdConfig() throws IOException {
        properties = new Properties();
        Path configPath = Path.of(System.getProperty("user.home"), ".dependency-scanner", CONFIG_FILE);
        
        if (Files.exists(configPath)) {
            properties.load(Files.newBufferedReader(configPath));
        }
    }

    public String getApiKey() {
        return properties.getProperty("nvd.api.key");
    }

    public int getRequestDelay() {
        return Integer.parseInt(properties.getProperty("nvd.request.delay.ms", "6000"));
    }
} 