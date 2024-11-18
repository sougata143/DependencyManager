package org.infy.scanner.visualization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.infy.scanner.core.Dependency;
import org.infy.scanner.vulnerability.VulnerabilityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class VisualizationGenerator {
    private static final Logger logger = LoggerFactory.getLogger(VisualizationGenerator.class);
    private final ObjectMapper objectMapper;

    public VisualizationGenerator() {
        this.objectMapper = new ObjectMapper();
    }

    public void generateVisualization(
            Set<Dependency> dependencies,
            Set<VulnerabilityResult> vulnerabilities,
            Path outputPath) {
        try {
            // Generate dependency graph
            DependencyGraph graph = DependencyGraph.fromDependencies(dependencies, vulnerabilities);

            // Create HTML with embedded visualization
            String html = generateHtml(graph);
            Files.writeString(outputPath, html);
            logger.info("Visualization generated at: {}", outputPath);
        } catch (Exception e) {
            logger.error("Failed to generate visualization", e);
            throw new VisualizationException("Failed to generate visualization", e);
        }
    }

    private String generateHtml(DependencyGraph graph) throws Exception {
        String graphJson = objectMapper.writeValueAsString(graph);
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Dependency Graph Visualization</title>
                <script src="webjars/d3js/7.8.5/d3.min.js"></script>
                <link href="webjars/bootstrap/5.3.2/css/bootstrap.min.css" rel="stylesheet">
                <style>
                    .node circle {
                        stroke: #fff;
                        stroke-width: 2px;
                    }
                    .node text {
                        font-size: 12px;
                    }
                    .link {
                        fill: none;
                        stroke: #999;
                        stroke-opacity: 0.6;
                        stroke-width: 1px;
                    }
                    .tooltip {
                        position: absolute;
                        background: white;
                        border: 1px solid #ddd;
                        padding: 10px;
                        border-radius: 5px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Dependency Graph</h1>
                    <div id="graph"></div>
                </div>
                <script>
                    const graph = %s;
                    
                    // D3.js visualization code
                    const width = 960;
                    const height = 600;
                    
                    const svg = d3.select("#graph")
                        .append("svg")
                        .attr("width", width)
                        .attr("height", height);
                    
                    const simulation = d3.forceSimulation(graph.nodes)
                        .force("link", d3.forceLink(graph.edges).id(d => d.id))
                        .force("charge", d3.forceManyBody().strength(-300))
                        .force("center", d3.forceCenter(width / 2, height / 2));
                    
                    const link = svg.append("g")
                        .selectAll("line")
                        .data(graph.edges)
                        .join("line")
                        .attr("class", "link");
                    
                    const node = svg.append("g")
                        .selectAll(".node")
                        .data(graph.nodes)
                        .join("g")
                        .attr("class", "node")
                        .call(d3.drag()
                            .on("start", dragstarted)
                            .on("drag", dragged)
                            .on("end", dragended));
                    
                    node.append("circle")
                        .attr("r", d => d.isDirectDependency ? 8 : 5)
                        .style("fill", d => d.color);
                    
                    node.append("text")
                        .attr("dx", 12)
                        .attr("dy", ".35em")
                        .text(d => d.id + "@" + d.version);
                    
                    simulation.on("tick", () => {
                        link
                            .attr("x1", d => d.source.x)
                            .attr("y1", d => d.source.y)
                            .attr("x2", d => d.target.x)
                            .attr("y2", d => d.target.y);
                        
                        node
                            .attr("transform", d => `translate(${d.x},${d.y})`);
                    });
                    
                    function dragstarted(event) {
                        if (!event.active) simulation.alphaTarget(0.3).restart();
                        event.subject.fx = event.subject.x;
                        event.subject.fy = event.subject.y;
                    }
                    
                    function dragged(event) {
                        event.subject.fx = event.x;
                        event.subject.fy = event.y;
                    }
                    
                    function dragended(event) {
                        if (!event.active) simulation.alphaTarget(0);
                        event.subject.fx = null;
                        event.subject.fy = null;
                    }
                </script>
            </body>
            </html>
            """.formatted(graphJson);
    }
} 