package org.infy.scanner.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class RepositoryManager {
    private static final Logger logger = LoggerFactory.getLogger(RepositoryManager.class);
    
    private final List<Repository> repositories = new ArrayList<>();
    private final Path localRepositoryPath;
    private final Map<String, RepositoryCredentials> credentials = new HashMap<>();
    private final RepositoryHealthChecker healthChecker;
    private final Duration healthCheckTimeout;
    private final Duration healthCacheExpiration;

    public RepositoryManager(Path localRepositoryPath) {
        this.localRepositoryPath = localRepositoryPath;
        this.healthCheckTimeout = Duration.ofSeconds(10);
        this.healthCacheExpiration = Duration.ofMinutes(30);
        this.healthChecker = new RepositoryHealthChecker(healthCheckTimeout, healthCacheExpiration);
        initializeDefaultRepositories();
    }

    private void initializeDefaultRepositories() {
        // Add Maven Central
        addRepository(new Repository(
            "central",
            URI.create("https://repo.maven.apache.org/maven2/"),
            RepositoryType.MAVEN,
            true,
            false
        ));

        // Add Gradle Plugin Portal
        addRepository(new Repository(
            "gradle-plugin-portal",
            URI.create("https://plugins.gradle.org/m2/"),
            RepositoryType.GRADLE,
            true,
            false
        ));

        // Add JCenter (for legacy support)
        addRepository(new Repository(
            "jcenter",
            URI.create("https://jcenter.bintray.com/"),
            RepositoryType.MAVEN,
            true,
            false
        ));

        logger.info("Initialized default repositories");
    }

    public void addRepository(Repository repository) {
        repositories.add(repository);
        logger.info("Added repository: {}", repository.name());
    }

    public void addCredentials(String repositoryName, String username, String password) {
        credentials.put(repositoryName, new RepositoryCredentials(username, password));
        logger.info("Added credentials for repository: {}", repositoryName);
    }

    public Path getLocalRepositoryPath() {
        return localRepositoryPath;
    }

    public List<Repository> getRepositories() {
        return Collections.unmodifiableList(repositories);
    }

    public List<Repository> getRepositoriesByType(RepositoryType type) {
        return repositories.stream()
            .filter(repo -> repo.type() == type)
            .toList();
    }

    public Optional<Repository> findRepository(String name) {
        return repositories.stream()
            .filter(repo -> repo.name().equals(name))
            .findFirst();
    }

    public Optional<RepositoryCredentials> getCredentials(String repositoryName) {
        return Optional.ofNullable(credentials.get(repositoryName));
    }

    public boolean isRepositoryEnabled(String name) {
        return findRepository(name)
            .map(Repository::enabled)
            .orElse(false);
    }

    public void enableRepository(String name) {
        findRepository(name).ifPresent(repo -> {
            repositories.remove(repo);
            repositories.add(new Repository(
                repo.name(),
                repo.url(),
                repo.type(),
                true,
                repo.allowsSnapshots()
            ));
            logger.info("Enabled repository: {}", name);
        });
    }

    public void disableRepository(String name) {
        findRepository(name).ifPresent(repo -> {
            repositories.remove(repo);
            repositories.add(new Repository(
                repo.name(),
                repo.url(),
                repo.type(),
                false,
                repo.allowsSnapshots()
            ));
            logger.info("Disabled repository: {}", name);
        });
    }

    public RepositoryHealthChecker.RepositoryHealth checkRepositoryHealth(String name) {
        return findRepository(name)
            .map(healthChecker::checkHealth)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + name));
    }

    public Map<String, RepositoryHealthChecker.RepositoryHealth> checkAllRepositoriesHealth() {
        return repositories.stream()
            .filter(Repository::enabled)
            .collect(Collectors.toMap(
                Repository::name,
                healthChecker::checkHealth
            ));
    }

    public void refreshHealthStatus(String name) {
        healthChecker.clearCache(name);
    }

    public void refreshAllHealthStatuses() {
        healthChecker.clearCache();
    }

    public List<Repository> getHealthyRepositories() {
        return repositories.stream()
            .filter(Repository::enabled)
            .filter(repo -> {
                var health = healthChecker.checkHealth(repo);
                return health.status() == RepositoryHealthChecker.HealthStatus.HEALTHY;
            })
            .toList();
    }

    public record Repository(
        String name,
        URI url,
        RepositoryType type,
        boolean enabled,
        boolean allowsSnapshots
    ) {}

    public record RepositoryCredentials(
        String username,
        String password
    ) {}

    public enum RepositoryType {
        MAVEN,
        GRADLE
    }
} 