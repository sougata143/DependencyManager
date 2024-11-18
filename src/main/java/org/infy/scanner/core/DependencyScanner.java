package org.infy.scanner.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Set;

public interface DependencyScanner {
    Set<Dependency> scanProject(Path projectPath);
} 