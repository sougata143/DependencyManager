package org.infy.scanner.gradle.dependencies;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BomHandler {
    private static final Logger logger = LoggerFactory.getLogger(BomHandler.class);
    
    private final Map<String, Map<String, String>> bomVersions = new HashMap<>();
    private final Path localMavenRepo;
    private final List<String> remoteRepositories;

    public BomHandler() {
        this.localMavenRepo = Path.of(System.getProperty("user.home"), ".m2", "repository");
        this.remoteRepositories = Arrays.asList(
            "https://repo.maven.apache.org/maven2",
            "https://repo1.maven.org/maven2"
        );
    }

    public void processBom(Dependency bomDependency) {
        try {
            Model pom = loadBomPom(bomDependency);
            if (pom == null) {
                return;
            }

            Map<String, String> managedVersions = new HashMap<>();
            
            // Process dependency management section
            if (pom.getDependencyManagement() != null) {
                pom.getDependencyManagement().getDependencies().forEach(dep -> {
                    String key = dep.getGroupId() + ":" + dep.getArtifactId();
                    String version = resolveVersion(dep.getVersion(), pom);
                    managedVersions.put(key, version);
                });
            }

            // Process properties section for version definitions
            pom.getProperties().forEach((key, value) -> {
                if (key.toString().endsWith(".version")) {
                    String artifactKey = key.toString().replace(".version", "");
                    managedVersions.put(artifactKey, value.toString());
                }
            });

            String bomKey = bomDependency.groupId() + ":" + bomDependency.artifactId();
            bomVersions.put(bomKey, managedVersions);
            
            logger.info("Processed BOM {}: {} managed dependencies", 
                bomKey, managedVersions.size());
        } catch (Exception e) {
            logger.error("Error processing BOM {}: {}", bomDependency, e.getMessage());
        }
    }

    public Set<Dependency> applyBomVersions(Set<Dependency> dependencies) {
        Set<Dependency> resolvedDependencies = new HashSet<>();
        
        for (Dependency dependency : dependencies) {
            String version = findBomVersion(dependency);
            if (version != null && !version.equals(dependency.version())) {
                logger.info("Applying BOM version for {}: {} -> {}", 
                    dependency.groupId() + ":" + dependency.artifactId(),
                    dependency.version(), 
                    version);
                
                resolvedDependencies.add(new Dependency(
                    dependency.groupId(),
                    dependency.artifactId(),
                    version,
                    dependency.scope(),
                    dependency.isDirectDependency()
                ));
            } else {
                resolvedDependencies.add(dependency);
            }
        }
        
        return resolvedDependencies;
    }

    private String findBomVersion(Dependency dependency) {
        String dependencyKey = dependency.groupId() + ":" + dependency.artifactId();
        
        // Check each BOM for the dependency version
        for (Map<String, String> versions : bomVersions.values()) {
            String version = versions.get(dependencyKey);
            if (version != null) {
                return version;
            }
            
            // Check for group-level version definitions
            version = versions.get(dependency.groupId());
            if (version != null) {
                return version;
            }
        }
        
        return null;
    }

    private Model loadBomPom(Dependency dependency) {
        try {
            // Try local repository first
            Path localPom = getLocalPomPath(dependency);
            if (Files.exists(localPom)) {
                return readPom(Files.newInputStream(localPom));
            }

            // Try remote repositories
            for (String repo : remoteRepositories) {
                String pomUrl = String.format("%s/%s/%s/%s/%s-%s.pom",
                    repo,
                    dependency.groupId().replace('.', '/'),
                    dependency.artifactId(),
                    dependency.version(),
                    dependency.artifactId(),
                    dependency.version()
                );

                try {
                    URL url = new URL(pomUrl);
                    return readPom(url.openStream());
                } catch (Exception e) {
                    logger.debug("Failed to load BOM from {}: {}", pomUrl, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Error loading BOM POM for {}: {}", dependency, e.getMessage());
        }
        return null;
    }

    private Path getLocalPomPath(Dependency dependency) {
        return localMavenRepo.resolve(
            dependency.groupId().replace('.', '/')
        ).resolve(
            dependency.artifactId()
        ).resolve(
            dependency.version()
        ).resolve(
            dependency.artifactId() + "-" + dependency.version() + ".pom"
        );
    }

    private Model readPom(InputStream inputStream) {
        try {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            return reader.read(inputStream);
        } catch (Exception e) {
            logger.warn("Error reading POM: {}", e.getMessage());
            return null;
        } finally {
            try {
                inputStream.close();
            } catch (Exception e) {
                logger.warn("Error closing POM stream: {}", e.getMessage());
            }
        }
    }

    private String resolveVersion(String version, Model pom) {
        if (version == null) {
            return "latest.release";
        }

        // Handle property references
        if (version.startsWith("${") && version.endsWith("}")) {
            String property = version.substring(2, version.length() - 1);
            String resolvedVersion = pom.getProperties().getProperty(property);
            if (resolvedVersion != null) {
                return resolvedVersion;
            }
        }

        return version;
    }
} 