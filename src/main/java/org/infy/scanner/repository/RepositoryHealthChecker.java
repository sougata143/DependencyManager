package org.infy.scanner.repository;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RepositoryHealthChecker {
    private static final Logger logger = LoggerFactory.getLogger(RepositoryHealthChecker.class);
    
    private final OkHttpClient httpClient;
    private final Map<String, RepositoryHealth> healthCache = new HashMap<>();
    private final Duration cacheExpiration;

    public RepositoryHealthChecker(Duration timeout, Duration cacheExpiration) {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .build();
        this.cacheExpiration = cacheExpiration;
    }

    public RepositoryHealth checkHealth(RepositoryManager.Repository repository) {
        String repoKey = repository.name();
        RepositoryHealth cachedHealth = healthCache.get(repoKey);
        
        if (cachedHealth != null && !cachedHealth.isExpired(cacheExpiration)) {
            return cachedHealth;
        }

        try {
            RepositoryHealth health = performHealthCheck(repository);
            healthCache.put(repoKey, health);
            return health;
        } catch (Exception e) {
            logger.error("Error checking repository health: {}", repository.name(), e);
            return new RepositoryHealth(
                HealthStatus.ERROR,
                "Failed to check repository health: " + e.getMessage(),
                System.currentTimeMillis()
            );
        }
    }

    private RepositoryHealth performHealthCheck(RepositoryManager.Repository repository) throws IOException {
        // Try to fetch a known artifact that should exist
        String probeUrl = switch (repository.type()) {
            case MAVEN -> repository.url() + "org/slf4j/slf4j-api/maven-metadata.xml";
            case GRADLE -> repository.url() + "org/gradle/gradle-core/maven-metadata.xml";
        };

        Request request = new Request.Builder()
            .url(probeUrl)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return new RepositoryHealth(
                    HealthStatus.HEALTHY,
                    "Repository is accessible and responding",
                    System.currentTimeMillis()
                );
            } else {
                return new RepositoryHealth(
                    HealthStatus.UNHEALTHY,
                    "Repository returned status code: " + response.code(),
                    System.currentTimeMillis()
                );
            }
        }
    }

    public void clearCache() {
        healthCache.clear();
    }

    public void clearCache(String repositoryName) {
        healthCache.remove(repositoryName);
    }

    public record RepositoryHealth(
        HealthStatus status,
        String message,
        long timestamp
    ) {
        public boolean isExpired(Duration expiration) {
            return System.currentTimeMillis() - timestamp > expiration.toMillis();
        }
    }

    public enum HealthStatus {
        HEALTHY,
        UNHEALTHY,
        ERROR
    }
} 