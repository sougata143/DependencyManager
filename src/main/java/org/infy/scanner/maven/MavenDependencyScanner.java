package org.infy.scanner.maven;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.settings.Settings;
import org.infy.scanner.core.Dependency;
import org.infy.scanner.core.DependencyScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class MavenDependencyScanner implements DependencyScanner {
    private static final Logger logger = LoggerFactory.getLogger(MavenDependencyScanner.class);
    private final Settings settings;

    public MavenDependencyScanner(Settings settings) {
        this.settings = settings;
    }

    @Override
    public Set<Dependency> scanProject(Path projectPath) {
        logger.info("Scanning Maven project at: {}", projectPath);
        Set<Dependency> dependencies = new HashSet<>();
        
        try {
            // Read POM file
            Path pomPath = projectPath.resolve("pom.xml");
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(new FileReader(pomPath.toFile()));

            // Process direct dependencies
            model.getDependencies().forEach(dep -> 
                dependencies.add(new Dependency(
                    dep.getGroupId(),
                    dep.getArtifactId(),
                    dep.getVersion(),
                    dep.getScope(),
                    true
                ))
            );

            // Process dependency management section
            if (model.getDependencyManagement() != null) {
                model.getDependencyManagement().getDependencies().forEach(dep ->
                    dependencies.add(new Dependency(
                        dep.getGroupId(),
                        dep.getArtifactId(),
                        dep.getVersion(),
                        dep.getScope(),
                        false
                    ))
                );
            }

            return dependencies;
        } catch (Exception e) {
            logger.error("Error scanning Maven project", e);
            throw new RuntimeException("Failed to scan Maven project", e);
        }
    }
} 