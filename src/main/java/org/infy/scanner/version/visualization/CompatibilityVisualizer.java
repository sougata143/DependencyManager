package org.infy.scanner.version.visualization;

import org.infy.scanner.core.Dependency;
import org.infy.scanner.version.CompatibilityMatrix;
import org.infy.scanner.version.VersionStabilityChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class CompatibilityVisualizer {
    private static final Logger logger = LoggerFactory.getLogger(CompatibilityVisualizer.class);
    private final VersionStabilityChecker stabilityChecker;
    private final Set<String> excludedGroups;
    private final Map<String, String> groupMappings;

    public CompatibilityVisualizer() {
        this.stabilityChecker = new VersionStabilityChecker();
        this.excludedGroups = new HashSet<>();
        this.groupMappings = new HashMap<>();
    }

    private static final String HTML_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Dependency Compatibility Visualization</title>
            <script src="https://d3js.org/d3.v7.min.js"></script>
            <script src="https://cdn.jsdelivr.net/npm/file-saver@2.0.5/dist/FileSaver.min.js"></script>
            <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css" rel="stylesheet">
            <style>
                .node { cursor: pointer; }
                .link { stroke: #999; stroke-opacity: 0.6; stroke-width: 1px; }
                .compatible { stroke: #28a745; }
                .incompatible { stroke: #dc3545; }
                .unknown { stroke: #ffc107; }
                .tooltip {
                    position: absolute;
                    padding: 10px;
                    background: white;
                    border: 1px solid #ddd;
                    border-radius: 5px;
                    pointer-events: none;
                    font-family: Arial, sans-serif;
                }
                .controls {
                    position: fixed;
                    top: 20px;
                    left: 20px;
                    background: white;
                    padding: 15px;
                    border: 1px solid #ddd;
                    border-radius: 5px;
                    z-index: 1000;
                }
                .legend {
                    position: fixed;
                    bottom: 20px;
                    left: 20px;
                    background: white;
                    padding: 15px;
                    border: 1px solid #ddd;
                    border-radius: 5px;
                }
                .hidden { display: none; }
                
                .search-box {
                    position: fixed;
                    top: 20px;
                    right: 20px;
                    background: white;
                    padding: 15px;
                    border: 1px solid #ddd;
                    border-radius: 5px;
                    z-index: 1000;
                    width: 300px;
                }
                
                .zoom-controls {
                    position: fixed;
                    bottom: 20px;
                    right: 20px;
                    background: white;
                    padding: 10px;
                    border: 1px solid #ddd;
                    border-radius: 5px;
                    z-index: 1000;
                }
                
                .export-controls {
                    position: fixed;
                    top: 20px;
                    right: 340px;
                    background: white;
                    padding: 15px;
                    border: 1px solid #ddd;
                    border-radius: 5px;
                    z-index: 1000;
                }
                
                .highlighted {
                    stroke-width: 3px !important;
                    stroke-opacity: 1 !important;
                }
                
                .dimmed {
                    opacity: 0.2;
                }
                
                .search-result {
                    background-color: #fff3cd;
                }
            </style>
        </head>
        <body>
            <div class="controls">
                <h5>Filters</h5>
                <div class="mb-3">
                    <label>Group Filter:</label>
                    <select id="groupFilter" class="form-select">
                        <option value="all">All Groups</option>
                        %s
                    </select>
                </div>
                <div class="mb-3">
                    <label>Stability Filter:</label>
                    <select id="stabilityFilter" class="form-select">
                        <option value="all">All Versions</option>
                        <option value="stable">Stable Only</option>
                        <option value="unstable">Unstable Only</option>
                    </select>
                </div>
                <div class="mb-3">
                    <label>Compatibility Filter:</label>
                    <select id="compatibilityFilter" class="form-select">
                        <option value="all">All Relationships</option>
                        <option value="compatible">Compatible Only</option>
                        <option value="incompatible">Incompatible Only</option>
                    </select>
                </div>
                <div class="mb-3">
                    <label>Layout:</label>
                    <select id="layoutType" class="form-select">
                        <option value="force">Force Directed</option>
                        <option value="circular">Circular</option>
                        <option value="hierarchical">Hierarchical</option>
                    </select>
                </div>
                <button id="resetFilters" class="btn btn-secondary">Reset Filters</button>
            </div>
            <div class="legend">
                <h6>Legend</h6>
                <div><span style="color: #28a745;">●</span> Compatible</div>
                <div><span style="color: #dc3545;">●</span> Incompatible</div>
                <div><span style="color: #ffc107;">●</span> Unknown</div>
            </div>
            <div class="search-box">
                <h5>Search</h5>
                <div class="mb-3">
                    <input type="text" id="searchInput" class="form-control" placeholder="Search dependencies...">
                </div>
                <div id="searchResults" class="list-group" style="max-height: 200px; overflow-y: auto;">
                </div>
            </div>
            
            <div class="export-controls">
                <h5>Export</h5>
                <div class="btn-group">
                    <button id="exportSVG" class="btn btn-outline-primary">SVG</button>
                    <button id="exportPNG" class="btn btn-outline-primary">PNG</button>
                    <button id="exportJSON" class="btn btn-outline-primary">JSON</button>
                </div>
            </div>
            
            <div class="zoom-controls">
                <div class="btn-group">
                    <button id="zoomIn" class="btn btn-outline-secondary">+</button>
                    <button id="zoomReset" class="btn btn-outline-secondary">Reset</button>
                    <button id="zoomOut" class="btn btn-outline-secondary">-</button>
                    <button id="fitToScreen" class="btn btn-outline-secondary">Fit</button>
                </div>
            </div>
            
            <div id="visualization"></div>
            <script>
                %s
            </script>
        </body>
        </html>
        """;

    public void generateVisualization(
        Set<Dependency> dependencies,
        CompatibilityMatrix matrix,
        Path outputPath
    ) {
        try {
            String groupOptions = generateGroupOptions(dependencies);
            String visualization = generateD3Visualization(dependencies, matrix);
            String html = String.format(HTML_TEMPLATE, groupOptions, visualization);
            Files.writeString(outputPath, html);
            logger.info("Compatibility visualization generated at: {}", outputPath);
        } catch (IOException e) {
            logger.error("Failed to generate compatibility visualization", e);
            throw new VisualizationException("Failed to generate visualization", e);
        }
    }

    private String generateGroupOptions(Set<Dependency> dependencies) {
        return dependencies.stream()
            .map(d -> d.groupId())
            .distinct()
            .sorted()
            .map(group -> String.format("<option value=\"%s\">%s</option>", 
                group, getGroupDisplayName(group)))
            .collect(Collectors.joining("\n"));
    }

    private String getGroupDisplayName(String groupId) {
        return groupMappings.getOrDefault(groupId, groupId);
    }

    private String generateD3Visualization(Set<Dependency> dependencies, CompatibilityMatrix matrix) {
        Map<String, Set<String>> moduleVersions = groupDependenciesByModule(dependencies);
        List<Node> nodes = new ArrayList<>();
        List<Link> links = new ArrayList<>();

        // Create nodes for each version
        moduleVersions.forEach((moduleId, versions) -> {
            versions.forEach(version -> {
                nodes.add(new Node(
                    moduleId + ":" + version,
                    moduleId,
                    version,
                    calculateNodeSize(dependencies, moduleId, version)
                ));
            });

            // Create links between versions
            List<String> sortedVersions = new ArrayList<>(versions);
            Collections.sort(sortedVersions);

            for (int i = 0; i < sortedVersions.size(); i++) {
                for (int j = i + 1; j < sortedVersions.size(); j++) {
                    String v1 = sortedVersions.get(i);
                    String v2 = sortedVersions.get(j);

                    CompatibilityMatrix.CompatibilityResult result = 
                        matrix.getCompatibility(moduleId, v1, v2);

                    if (result != null) {
                        links.add(new Link(
                            moduleId + ":" + v1,
                            moduleId + ":" + v2,
                            result.level(),
                            result.summary()
                        ));
                    }
                }
            }
        });

        return generateD3Script(nodes, links);
    }

    private int calculateNodeSize(Set<Dependency> dependencies, String moduleId, String version) {
        return (int) dependencies.stream()
            .filter(d -> getModuleId(d).equals(moduleId) && d.version().equals(version))
            .count() * 5 + 10;
    }

    private String getModuleId(Dependency dependency) {
        return dependency.groupId() + ":" + dependency.artifactId();
    }

    private Map<String, Set<String>> groupDependenciesByModule(Set<Dependency> dependencies) {
        return dependencies.stream()
            .collect(Collectors.groupingBy(
                this::getModuleId,
                Collectors.mapping(Dependency::version, Collectors.toSet())
            ));
    }

    private String generateD3Script(List<Node> nodes, List<Link> links) {
        return """
            const data = {
                nodes: %s,
                links: %s
            };
            
            let filteredData = {
                nodes: [...data.nodes],
                links: [...data.links]
            };
            
            const width = window.innerWidth;
            const height = window.innerHeight;
            
            let zoom = d3.zoom()
                .scaleExtent([0.1, 4])
                .on("zoom", zoomed);
            
            const svg = d3.select("#visualization")
                .append("svg")
                .attr("width", width)
                .attr("height", height)
                .call(zoom);
            
            const g = svg.append("g");
            
            // ... previous visualization code ...
            
            // Search functionality
            const searchInput = d3.select("#searchInput");
            const searchResults = d3.select("#searchResults");
            
            searchInput.on("input", function() {
                const searchTerm = this.value.toLowerCase();
                if (!searchTerm) {
                    clearHighlights();
                    searchResults.html("");
                    return;
                }
                
                const matches = filteredData.nodes.filter(n => 
                    n.moduleId.toLowerCase().includes(searchTerm) ||
                    n.version.toLowerCase().includes(searchTerm)
                );
                
                updateSearchResults(matches);
                highlightMatches(matches);
            });
            
            function updateSearchResults(matches) {
                searchResults.html("");
                matches.forEach(match => {
                    searchResults.append("a")
                        .attr("class", "list-group-item list-group-item-action")
                        .text(`${match.moduleId} (${match.version})`)
                        .on("click", () => focusNode(match));
                });
            }
            
            function highlightMatches(matches) {
                const matchIds = new Set(matches.map(m => m.id));
                const relatedLinks = filteredData.links.filter(l =>
                    matchIds.has(l.source.id) || matchIds.has(l.target.id)
                );
                
                node.classed("dimmed", n => !matchIds.has(n.id));
                link.classed("dimmed", l => 
                    !matchIds.has(l.source.id) && !matchIds.has(l.target.id)
                );
            }
            
            function clearHighlights() {
                node.classed("dimmed", false);
                link.classed("dimmed", false);
            }
            
            function focusNode(n) {
                const scale = 2;
                const x = -n.x * scale + width / 2;
                const y = -n.y * scale + height / 2;
                
                svg.transition()
                    .duration(750)
                    .call(zoom.transform, d3.zoomIdentity
                        .translate(x, y)
                        .scale(scale)
                    );
            }
            
            // Zoom controls
            d3.select("#zoomIn").on("click", () => {
                svg.transition().call(zoom.scaleBy, 1.5);
            });
            
            d3.select("#zoomOut").on("click", () => {
                svg.transition().call(zoom.scaleBy, 0.75);
            });
            
            d3.select("#zoomReset").on("click", () => {
                svg.transition().call(zoom.transform, d3.zoomIdentity);
            });
            
            d3.select("#fitToScreen").on("click", fitToScreen);
            
            function fitToScreen() {
                const bounds = g.node().getBBox();
                const fullWidth = bounds.width;
                const fullHeight = bounds.height;
                const midX = bounds.x + fullWidth / 2;
                const midY = bounds.y + fullHeight / 2;
                
                const scale = 0.95 / Math.max(
                    fullWidth / width,
                    fullHeight / height
                );
                
                const translate = [
                    width / 2 - scale * midX,
                    height / 2 - scale * midY
                ];
                
                svg.transition()
                    .duration(750)
                    .call(zoom.transform, d3.zoomIdentity
                        .translate(translate[0], translate[1])
                        .scale(scale)
                    );
            }
            
            // Export functionality
            d3.select("#exportSVG").on("click", exportSVG);
            d3.select("#exportPNG").on("click", exportPNG);
            d3.select("#exportJSON").on("click", exportJSON);
            
            function exportSVG() {
                const svgData = new XMLSerializer()
                    .serializeToString(svg.node());
                const blob = new Blob([svgData], {
                    type: "image/svg+xml;charset=utf-8"
                });
                saveAs(blob, "dependency-graph.svg");
            }
            
            function exportPNG() {
                const svgNode = svg.node();
                const canvas = document.createElement("canvas");
                const context = canvas.getContext("2d");
                const svgData = new XMLSerializer()
                    .serializeToString(svgNode);
                
                const img = new Image();
                img.onload = function() {
                    canvas.width = width;
                    canvas.height = height;
                    context.drawImage(img, 0, 0);
                    canvas.toBlob(blob => {
                        saveAs(blob, "dependency-graph.png");
                    });
                };
                img.src = "data:image/svg+xml;base64," + 
                    btoa(unescape(encodeURIComponent(svgData)));
            }
            
            function exportJSON() {
                const jsonData = {
                    nodes: filteredData.nodes.map(n => ({
                        id: n.id,
                        moduleId: n.moduleId,
                        version: n.version
                    })),
                    links: filteredData.links.map(l => ({
                        source: l.source.id,
                        target: l.target.id,
                        type: l.type,
                        description: l.description
                    }))
                };
                
                const blob = new Blob(
                    [JSON.stringify(jsonData, null, 2)],
                    {type: "application/json"}
                );
                saveAs(blob, "dependency-graph.json");
            }
            
            function zoomed(event) {
                g.attr("transform", event.transform);
            }
            
            // Initial layout
            fitToScreen();
            """.formatted(
                toJson(nodes),
                toJson(links)
            );
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(obj);
        } catch (Exception e) {
            throw new VisualizationException("Failed to serialize to JSON", e);
        }
    }

    private record Node(String id, String moduleId, String version, int size) {}

    private record Link(
        String source,
        String target,
        CompatibilityMatrix.CompatibilityLevel type,
        String description
    ) {}

    public static class VisualizationException extends RuntimeException {
        public VisualizationException(String message) {
            super(message);
        }

        public VisualizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public void addGroupMapping(String groupId, String displayName) {
        groupMappings.put(groupId, displayName);
    }

    public void excludeGroup(String groupId) {
        excludedGroups.add(groupId);
    }
} 