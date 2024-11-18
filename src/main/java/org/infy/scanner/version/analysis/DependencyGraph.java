package org.infy.scanner.version.analysis;

import org.infy.scanner.core.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class DependencyGraph {
    private static final Logger logger = LoggerFactory.getLogger(DependencyGraph.class);
    
    private final Map<String, Set<Dependency>> nodes = new HashMap<>();
    private final Map<String, Set<String>> edges = new HashMap<>();
    private final Map<String, Set<String>> reverseEdges = new HashMap<>();

    public void addDependency(Dependency dependency) {
        String moduleId = getModuleId(dependency);
        nodes.computeIfAbsent(moduleId, k -> new HashSet<>()).add(dependency);
    }

    public void addDependencyRelation(Dependency from, Dependency to) {
        String fromId = getModuleId(from);
        String toId = getModuleId(to);

        edges.computeIfAbsent(fromId, k -> new HashSet<>()).add(toId);
        reverseEdges.computeIfAbsent(toId, k -> new HashSet<>()).add(fromId);
    }

    public Set<Dependency> getDependents(String moduleId) {
        return reverseEdges.getOrDefault(moduleId, Collections.emptySet()).stream()
            .flatMap(dependentId -> nodes.getOrDefault(dependentId, Collections.emptySet()).stream())
            .collect(Collectors.toSet());
    }

    public Set<DependencyChain> findTransitiveDependencyChains(String moduleId) {
        Set<DependencyChain> chains = new HashSet<>();
        Set<String> visited = new HashSet<>();
        findChains(moduleId, new ArrayList<>(), visited, chains);
        return chains;
    }

    private void findChains(String currentId, 
                          List<Dependency> currentChain,
                          Set<String> visited,
                          Set<DependencyChain> chains) {
        if (!visited.add(currentId)) {
            // Cycle detected
            return;
        }

        Set<Dependency> currentDeps = nodes.getOrDefault(currentId, Collections.emptySet());
        for (Dependency currentDep : currentDeps) {
            List<Dependency> newChain = new ArrayList<>(currentChain);
            newChain.add(currentDep);

            // Add the chain if it has more than one dependency
            if (newChain.size() > 1) {
                chains.add(new DependencyChain(newChain));
            }

            // Continue traversing
            Set<String> nextModules = edges.getOrDefault(currentId, Collections.emptySet());
            for (String nextId : nextModules) {
                findChains(nextId, newChain, new HashSet<>(visited), chains);
            }
        }
    }

    public Set<String> getModuleIds() {
        return new HashSet<>(nodes.keySet());
    }

    public Set<Dependency> getDependencies(String moduleId) {
        return new HashSet<>(nodes.getOrDefault(moduleId, Collections.emptySet()));
    }

    public Set<String> getDependencies() {
        return edges.keySet();
    }

    public boolean hasDependency(String moduleId) {
        return nodes.containsKey(moduleId);
    }

    public boolean hasRelation(String fromId, String toId) {
        return edges.getOrDefault(fromId, Collections.emptySet()).contains(toId);
    }

    private String getModuleId(Dependency dependency) {
        return dependency.groupId() + ":" + dependency.artifactId();
    }

    public record DependencyChain(List<Dependency> dependencies) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DependencyChain that = (DependencyChain) o;
            return dependencies.equals(that.dependencies);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dependencies);
        }

        @Override
        public String toString() {
            return dependencies.stream()
                .map(d -> d.groupId() + ":" + d.artifactId() + ":" + d.version())
                .collect(Collectors.joining(" -> "));
        }
    }
} 