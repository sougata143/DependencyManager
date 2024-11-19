# Dependency Management Tool

A comprehensive tool for analyzing dependencies, checking vulnerabilities, and managing upgrades in Maven and Gradle projects.

## Features

### 1. Dependency Scanning
- Scans Maven and Gradle projects for dependencies
- Detects direct and transitive dependencies
- Supports multiple project formats (single module, multi-module)
- Handles both Maven and Gradle dependency formats

### 2. Vulnerability Analysis
- Integrates with NVD (National Vulnerability Database)
- Checks for known security vulnerabilities
- Provides detailed vulnerability reports
- Includes severity ratings and remediation advice

### 3. Dependency Graph Visualization
- Interactive visualization of dependency relationships
- Color-coded by vulnerability status
- Filterable by dependency type and severity
- Multiple layout options (force-directed, hierarchical, radial)

### 4. Impact Analysis
- Analyzes upgrade impact
- Identifies breaking changes
- Suggests safe upgrade paths
- Provides compatibility matrices

### 5. Reporting
- HTML reports with interactive features
- JSON reports for programmatic access
- Dependency graphs
- Vulnerability summaries

## Prerequisites

- Java 17 or higher
- Maven 3.6+ (for building)
- NVD API Key (for vulnerability scanning)

## Installation

1. Clone the repository: 
git clone https://github.com/yourusername/DependencyManagementTool.git
cd DependencyManagementTool


2. Build the project:
mvn clean package


3. Configure NVD API Key:
mkdir -p ~/.config/nvd
cat > ~/.config/nvd/config.json << EOL
{
"apiKey": "YOUR-NVD-API-KEY-HERE",
"baseUrl": "https://services.nvd.nist.gov/rest/json/cves/2.0",
"requestTimeout": 30,
"maxRetries": 3,
"rateLimitRequests": 30,
"rateLimitWindow": 30
}
EOL
chmod 600 ~/.config/nvd/config.json


## Usage

### Basic Scanning

Scan a project for dependencies and vulnerabilities:
java -jar target/DependencyManagementTool-1.0-SNAPSHOT-jar-with-dependencies.jar /path/to/project


### Output

The tool generates several reports in the `dependency-reports` directory:

1. `vulnerability-report.html`: Interactive HTML report showing:
   - List of all dependencies
   - Known vulnerabilities
   - Severity levels
   - Remediation advice

2. `vulnerability-report.json`: Machine-readable report containing:
   - Detailed dependency information
   - Vulnerability data
   - Impact analysis
   - Upgrade recommendations

3. `dependency-graph.html`: Interactive visualization showing:
   - Dependency relationships
   - Vulnerability status
   - Impact analysis
   - Upgrade paths

### Advanced Usage

1. Analyze specific dependencies:
java -jar dependency-tool.jar --analyze-dependency group:artifact:version


2. Generate impact analysis:
java -jar dependency-tool.jar --analyze-impact /path/to/project


3. Check for upgrades:
java -jar dependency-tool.jar --check-upgrades /path/to/project


## Configuration

### Repository Configuration

Create `~/.config/dependency-tool/repositories.json`:
{
"repositories": [
{
"name": "maven-central",
"url": "https://repo.maven.apache.org/maven2/",
"type": "MAVEN",
"enabled": true
}
],
"credentials": {
"private-repo": {
"username": "user",
"password": "pass"
}
}
}


### Tool Configuration

Customize behavior through command-line options:
- `--skip-vulnerabilities`: Skip vulnerability scanning
- `--offline`: Work in offline mode
- `--verbose`: Enable detailed logging
- `--output-format`: Specify report format (html, json, all)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Troubleshooting

### Common Issues

1. NVD API Key Issues:
   - Ensure the config file exists at `~/.config/nvd/config.json`
   - Check file permissions (should be 600)
   - Verify API key is valid

2. Maven/Gradle Detection:
   - Ensure project has pom.xml or build.gradle
   - Check file permissions
   - Verify Maven/Gradle installation

3. Report Generation:
   - Check write permissions in output directory
   - Ensure enough disk space
   - Verify JSON/HTML templates exist

### Getting Help

- File an issue on GitHub
- Check the wiki for detailed documentation
- Join our community Discord server

## Roadmap

- [ ] Support for more package managers
- [ ] Enhanced vulnerability detection
- [ ] Machine learning-based impact prediction
- [ ] Cloud integration
- [ ] CI/CD plugins

## Acknowledgments

- NVD for vulnerability data
- D3.js for visualizations
- Maven and Gradle communities
