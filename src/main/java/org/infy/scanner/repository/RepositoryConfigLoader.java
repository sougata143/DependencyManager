package org.infy.scanner.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class RepositoryConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(RepositoryConfigLoader.class);
    private final ObjectMapper jsonMapper;

    public RepositoryConfigLoader() {
        this.jsonMapper = new ObjectMapper();
    }

    public void loadConfig(Path configFile, RepositoryManager repositoryManager) {
        try {
            if (!Files.exists(configFile)) {
                logger.info("No repository configuration file found at: {}", configFile);
                return;
            }

            Map<String, Object> config = jsonMapper.readValue(
                Files.readString(configFile),
                Map.class
            );

            // Load repositories
            List<Map<String, Object>> repositories = 
                (List<Map<String, Object>>) config.get("repositories");
            if (repositories != null) {
                for (Map<String, Object> repoConfig : repositories) {
                    loadRepository(repoConfig, repositoryManager);
                }
            }

            // Load credentials
            Map<String, Map<String, String>> credentials = 
                (Map<String, Map<String, String>>) config.get("credentials");
            if (credentials != null) {
                loadCredentials(credentials, repositoryManager);
            }

        } catch (Exception e) {
            logger.error("Error loading repository configuration", e);
            throw new RepositoryConfigurationException("Failed to load repository configuration", e);
        }
    }

    private void loadRepository(Map<String, Object> repoConfig, RepositoryManager repositoryManager) {
        String name = (String) repoConfig.get("name");
        String url = (String) repoConfig.get("url");
        String type = (String) repoConfig.get("type");
        boolean enabled = (boolean) repoConfig.getOrDefault("enabled", true);
        boolean allowsSnapshots = (boolean) repoConfig.getOrDefault("allowsSnapshots", false);

        repositoryManager.addRepository(new RepositoryManager.Repository(
            name,
            URI.create(url),
            RepositoryManager.RepositoryType.valueOf(type.toUpperCase()),
            enabled,
            allowsSnapshots
        ));
    }

    private void loadCredentials(
        Map<String, Map<String, String>> credentials,
        RepositoryManager repositoryManager
    ) {
        credentials.forEach((repoName, creds) -> {
            String username = creds.get("username");
            String password = creds.get("password");
            if (username != null && password != null) {
                repositoryManager.addCredentials(repoName, username, password);
            }
        });
    }
} 