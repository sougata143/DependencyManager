package org.infy.scanner.version.visualization;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.infy.scanner.core.Dependency;
import org.infy.scanner.version.analysis.ImpactAnalysisEngine;
import org.infy.scanner.version.analysis.ImpactAnalysisEngine.ImpactAnalysisResult;
import org.infy.scanner.version.analysis.ImpactAnalysisEngine.DirectImpact;
import org.infy.scanner.version.analysis.ImpactAnalysisEngine.TransitiveImpact;
import org.infy.scanner.version.analysis.ImpactAnalysisEngine.ImpactLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ImpactGraphVisualizer {
    private static final Logger logger = LoggerFactory.getLogger(ImpactGraphVisualizer.class);

    private static final String HTML_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Upgrade Impact Analysis</title>
            <script src="https://d3js.org/d3.v7.min.js"></script>
            <script src="https://d3js.org/d3-force.v2.min.js"></script>
            <script src="https://d3js.org/d3-hierarchy.v2.min.js"></script>
            <script src="https://cdn.jsdelivr.net/npm/dagre-d3@0.6.4/dist/dagre-d3.min.js"></script>
            <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css" rel="stylesheet">
            <style>
                .node { cursor: pointer; }
                .node circle {
                    stroke-width: 2px;
                    stroke: #fff;
                }
                .node text {
                    font-size: 12px;
                    font-family: Arial, sans-serif;
                }
                .link {
                    stroke-opacity: 0.6;
                    stroke-width: 2px;
                }
                .link.direct { stroke-dasharray: none; }
                .link.transitive { stroke-dasharray: 5,5; }
                .link.high-impact { stroke: #dc3545; }
                .link.medium-impact { stroke: #ffc107; }
                .link.low-impact { stroke: #28a745; }
                .tooltip {
                    position: absolute;
                    padding: 10px;
                    background: white;
                    border: 1px solid #ddd;
                    border-radius: 5px;
                    pointer-events: none;
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
                .metrics {
                    position: fixed;
                    top: 20px;
                    right: 20px;
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
                .search-highlight {
                    stroke: #ffc107;
                    stroke-width: 3px;
                }
                
                .context-menu {
                    position: absolute;
                    background: white;
                    border: 1px solid #ddd;
                    border-radius: 5px;
                    padding: 5px;
                    z-index: 1000;
                }
                
                .mini-map {
                    position: fixed;
                    bottom: 20px;
                    right: 20px;
                    width: 200px;
                    height: 150px;
                    background: white;
                    border: 1px solid #ddd;
                    border-radius: 5px;
                }
                
                .advanced-controls {
                    position: fixed;
                    top: 50%;
                    right: 20px;
                    transform: translateY(-50%);
                    background: white;
                    padding: 15px;
                    border: 1px solid #ddd;
                    border-radius: 5px;
                    z-index: 1000;
                }
                
                .node-group {
                    cursor: pointer;
                }
                
                .node-label {
                    font-size: 12px;
                    pointer-events: none;
                }
                
                .link-label {
                    font-size: 10px;
                    fill: #666;
                }
                
                .tooltip-table {
                    margin: 0;
                    padding: 5px;
                }
                
                .tooltip-table td {
                    padding: 2px 5px;
                }
            </style>
        </head>
        <body>
            <div class="controls">
                <h5>View Controls</h5>
                <div class="mb-3">
                    <label>Impact Level:</label>
                    <select id="impactFilter" class="form-select">
                        <option value="all">All Impacts</option>
                        <option value="high">High Impact Only</option>
                        <option value="medium">Medium+ Impact</option>
                        <option value="low">Low+ Impact</option>
                    </select>
                </div>
                <div class="mb-3">
                    <label>Dependency Type:</label>
                    <select id="dependencyFilter" class="form-select">
                        <option value="all">All Dependencies</option>
                        <option value="direct">Direct Only</option>
                        <option value="transitive">Transitive Only</option>
                    </select>
                </div>
                <div class="mb-3">
                    <label>Layout:</label>
                    <select id="layoutType" class="form-select">
                        <option value="force">Force Directed</option>
                        <option value="hierarchical">Hierarchical</option>
                        <option value="radial">Radial</option>
                    </select>
                </div>
            </div>
            <div class="metrics">
                <h5>Impact Metrics</h5>
                %s
            </div>
            <div class="legend">
                <h6>Legend</h6>
                <div><span style="color: #dc3545;">●</span> High Impact</div>
                <div><span style="color: #ffc107;">●</span> Medium Impact</div>
                <div><span style="color: #28a745;">●</span> Low Impact</div>
                <div>―― Direct Dependency</div>
                <div>- - Transitive Dependency</div>
            </div>
            <div class="advanced-controls">
                <h5>Advanced Controls</h5>
                <div class="mb-3">
                    <label>Node Spacing:</label>
                    <input type="range" id="nodeSpacing" class="form-range" min="10" max="200" value="50">
                </div>
                <div class="mb-3">
                    <label>Link Distance:</label>
                    <input type="range" id="linkDistance" class="form-range" min="30" max="300" value="100">
                </div>
                <div class="mb-3">
                    <label>Charge Strength:</label>
                    <input type="range" id="chargeStrength" class="form-range" min="-1000" max="0" value="-300">
                </div>
                <div class="mb-3">
                    <label>Layout Algorithm:</label>
                    <select id="layoutAlgorithm" class="form-select">
                        <option value="force">Force Directed</option>
                        <option value="radial">Radial</option>
                        <option value="dagre">Hierarchical (Dagre)</option>
                        <option value="concentric">Concentric</option>
                        <option value="grid">Grid</option>
                    </select>
                </div>
                <div class="mb-3">
                    <label>Group By:</label>
                    <select id="groupBy" class="form-select">
                        <option value="none">None</option>
                        <option value="impact">Impact Level</option>
                        <option value="risk">Risk Level</option>
                        <option value="type">Dependency Type</option>
                    </select>
                </div>
                <div class="form-check mb-3">
                    <input type="checkbox" id="showLabels" class="form-check-input" checked>
                    <label class="form-check-label">Show Labels</label>
                </div>
                <div class="form-check mb-3">
                    <input type="checkbox" id="showMiniMap" class="form-check-input" checked>
                    <label class="form-check-label">Show Mini Map</label>
                </div>
            </div>
            
            <div id="miniMap" class="mini-map"></div>
            <div id="contextMenu" class="context-menu" style="display: none;"></div>
            <div id="visualization"></div>
            
            <script>
                %s
            </script>
        </body>
        </html>
        """;

    public void visualizeImpact(ImpactAnalysisResult result, Path outputPath) {
        try {
            String metricsHtml = generateMetricsHtml(result);
            String visualizationScript = generateVisualizationScript(result);
            String html = String.format(HTML_TEMPLATE, metricsHtml, visualizationScript);
            Files.writeString(outputPath, html);
            logger.info("Impact visualization generated at: {}", outputPath);
        } catch (IOException e) {
            logger.error("Failed to generate impact visualization", e);
            throw new RuntimeException("Failed to generate impact visualization", e);
        }
    }

    private String generateMetricsHtml(ImpactAnalysisResult result) {
        return String.format("""
            <div class="metric-item">
                <strong>Total Impacted:</strong> %d
            </div>
            <div class="metric-item">
                <strong>High Risk:</strong> %d
            </div>
            <div class="metric-item">
                <strong>Risk Score:</strong> %.2f
            </div>
            <div class="metric-item">
                <strong>Breaking Changes:</strong> %d
            </div>
            """,
            result.metrics().totalImpactedModules(),
            result.metrics().highRiskCount(),
            result.metrics().riskScore(),
            result.metrics().breakingChanges()
        );
    }

    private String generateVisualizationScript(ImpactAnalysisResult result) throws JsonProcessingException {
        Map<String, Object> graphData = generateGraphData(result);
        return """
            const data = %s;
            
            let simulation;
            let zoom;
            let transform = d3.zoomIdentity;
            
            const width = window.innerWidth;
            const height = window.innerHeight;
            
            const svg = d3.select("#visualization")
                .append("svg")
                .attr("width", width)
                .attr("height", height);
            
            const g = svg.append("g");
            
            // Initialize zoom behavior
            zoom = d3.zoom()
                .scaleExtent([0.1, 4])
                .on("zoom", (event) => {
                    transform = event.transform;
                    g.attr("transform", transform);
                    updateMiniMap();
                });
            
            svg.call(zoom);
            
            // Initialize mini map
            const miniMap = d3.select("#miniMap")
                .append("svg")
                .attr("width", "100%")
                .attr("height", "100%");
            
            const miniMapG = miniMap.append("g");
            
            function updateMiniMap() {
                // Clear previous content
                miniMapG.selectAll("*").remove();
                
                // Calculate bounds of the main visualization
                const bounds = g.node().getBBox();
                
                // Calculate scale for mini map
                const scale = Math.min(
                    200 / bounds.width,
                    150 / bounds.height
                );
                
                // Draw nodes
                miniMapG.selectAll("circle")
                    .data(data.nodes)
                    .join("circle")
                    .attr("r", 2)
                    .attr("cx", d => d.x * scale)
                    .attr("cy", d => d.y * scale)
                    .attr("fill", d => d.color);
                
                // Draw viewport rectangle
                const viewportRect = miniMapG.append("rect")
                    .attr("class", "viewport")
                    .attr("stroke", "#000")
                    .attr("stroke-width", "1px")
                    .attr("fill", "none")
                    .attr("x", -transform.x * scale)
                    .attr("y", -transform.y * scale)
                    .attr("width", width * scale / transform.k)
                    .attr("height", height * scale / transform.k);
            }
            
            // Context menu
            function showContextMenu(d, event) {
                const menu = d3.select("#contextMenu")
                    .style("left", (event.pageX + 5) + "px")
                    .style("top", (event.pageY + 5) + "px")
                    .style("display", "block");
                
                menu.html(`
                    <div class="list-group">
                        <a class="list-group-item list-group-item-action" onclick="focusNode(${d.id})">
                            Focus
                        </a>
                        <a class="list-group-item list-group-item-action" onclick="expandNode(${d.id})">
                            Expand/Collapse
                        </a>
                        <a class="list-group-item list-group-item-action" onclick="hideNode(${d.id})">
                            Hide
                        </a>
                    </div>
                `);
            }
            
            // Hide context menu when clicking elsewhere
            d3.select("body").on("click", () => {
                d3.select("#contextMenu").style("display", "none");
            });
            
            // Layout algorithms
            function applyLayout(type) {
                switch (type) {
                    case "force":
                        applyForceLayout();
                        break;
                    case "radial":
                        applyRadialLayout();
                        break;
                    case "dagre":
                        applyDagreLayout();
                        break;
                    case "concentric":
                        applyConcentricLayout();
                        break;
                    case "grid":
                        applyGridLayout();
                        break;
                }
            }
            
            function applyForceLayout() {
                simulation = d3.forceSimulation(data.nodes)
                    .force("link", d3.forceLink(data.links).id(d => d.id))
                    .force("charge", d3.forceManyBody().strength(
                        d3.select("#chargeStrength").property("value")
                    ))
                    .force("center", d3.forceCenter(width / 2, height / 2))
                    .force("collision", d3.forceCollide().radius(
                        d3.select("#nodeSpacing").property("value")
                    ));
                
                simulation.on("tick", updatePositions);
            }
            
            function applyDagreLayout() {
                const g = new dagre.graphlib.Graph();
                g.setGraph({});
                g.setDefaultEdgeLabel(() => ({}));
                
                data.nodes.forEach(node => {
                    g.setNode(node.id, { width: 50, height: 50 });
                });
                
                data.links.forEach(link => {
                    g.setEdge(link.source.id, link.target.id);
                });
                
                dagre.layout(g);
                
                g.nodes().forEach(v => {
                    const node = g.node(v);
                    data.nodes.find(n => n.id === v).x = node.x;
                    data.nodes.find(n => n.id === v).y = node.y;
                });
                
                updatePositions();
            }
            
            // ... Add other layout implementations ...
            
            // Update positions
            function updatePositions() {
                node.attr("transform", d => `translate(${d.x},${d.y})`);
                
                link
                    .attr("x1", d => d.source.x)
                    .attr("y1", d => d.source.y)
                    .attr("x2", d => d.target.x)
                    .attr("y2", d => d.target.y);
                
                if (d3.select("#showLabels").property("checked")) {
                    labels
                        .attr("x", d => d.x)
                        .attr("y", d => d.y + 20);
                }
                
                updateMiniMap();
            }
            
            // Event listeners
            d3.select("#layoutAlgorithm").on("change", function() {
                applyLayout(this.value);
            });
            
            d3.select("#nodeSpacing").on("input", function() {
                simulation.force("collision").radius(+this.value);
                simulation.alpha(0.3).restart();
            });
            
            d3.select("#linkDistance").on("input", function() {
                simulation.force("link").distance(+this.value);
                simulation.alpha(0.3).restart();
            });
            
            d3.select("#chargeStrength").on("input", function() {
                simulation.force("charge").strength(+this.value);
                simulation.alpha(0.3).restart();
            });
            
            d3.select("#showMiniMap").on("change", function() {
                d3.select("#miniMap").style("display", 
                    this.checked ? "block" : "none");
            });
            
            // Initial layout
            applyLayout("force");
            """.formatted(new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(graphData));
    }

    private Map<String, Object> generateGraphData(ImpactAnalysisResult result) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> links = new ArrayList<>();

        // Add target module node
        nodes.add(Map.of(
            "id", result.moduleId(),
            "label", result.moduleId(),
            "version", result.targetVersion(),
            "nodeType", "Target",
            "size", 20,
            "color", "#007bff"
        ));

        // Add direct impact nodes and links
        for (DirectImpact impact : result.directImpacts()) {
            String nodeId = getNodeId(impact.dependency());
            nodes.add(Map.of(
                "id", nodeId,
                "label", getModuleId(impact.dependency()),
                "version", impact.dependency().version(),
                "nodeType", "Direct",
                "impactLevel", impact.impactLevel().toString(),
                "riskLevel", impact.riskLevel().toString(),
                "size", 15,
                "color", getImpactColor(impact.impactLevel())
            ));

            links.add(Map.of(
                "source", result.moduleId(),
                "target", nodeId,
                "type", "direct",
                "impact", impact.impactLevel().toString().toLowerCase()
            ));
        }

        // Add transitive impact nodes and links
        for (TransitiveImpact impact : result.transitiveImpacts()) {
            String sourceId = getNodeId(impact.source());
            String targetId = getNodeId(impact.target());

            // Add source node if not already added
            if (nodes.stream().noneMatch(n -> n.get("id").equals(sourceId))) {
                nodes.add(Map.of(
                    "id", sourceId,
                    "label", getModuleId(impact.source()),
                    "version", impact.source().version(),
                    "nodeType", "Transitive",
                    "impactLevel", impact.overallImpact().toString(),
                    "riskLevel", impact.overallRisk().toString(),
                    "size", 10,
                    "color", getImpactColor(impact.overallImpact())
                ));
            }

            links.add(Map.of(
                "source", sourceId,
                "target", targetId,
                "type", "transitive",
                "impact", impact.overallImpact().toString().toLowerCase()
            ));
        }

        return Map.of(
            "nodes", nodes,
            "links", links
        );
    }

    private String getNodeId(Dependency dependency) {
        return dependency.groupId() + ":" + dependency.artifactId() + ":" + dependency.version();
    }

    private String getModuleId(Dependency dependency) {
        return dependency.groupId() + ":" + dependency.artifactId();
    }

    private String getImpactColor(ImpactLevel level) {
        return switch (level) {
            case HIGH -> "#dc3545";
            case MEDIUM -> "#ffc107";
            case LOW -> "#28a745";
            case NONE -> "#6c757d";
            case UNKNOWN -> "#6c757d";
        };
    }
} 