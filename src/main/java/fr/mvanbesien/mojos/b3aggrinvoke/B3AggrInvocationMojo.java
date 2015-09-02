package fr.mvanbesien.mojos.b3aggrinvoke;

import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.twdata.maven.mojoexecutor.MojoExecutor.Element;

@Mojo(name = "aggregate", requiresProject = false, threadSafe = false)
public class B3AggrInvocationMojo extends AbstractMojo {

	@Parameter(required = true, property = "build-model")
	private String modelPath;

	@Parameter(required = false, property = "build-root", defaultValue = "${project.basedir}/target/build")
	private String buildRootPath;

	@Parameter(required = false, property = "build-action", defaultValue = "CLEAN_BUILD")
	private String buildAction;

	@Parameter(required = false, property = "java-args")
	private String javaArgs = "";

	@Parameter(required = false, property = "with-index", defaultValue = "false")
	private boolean withIndex;

	@Parameter(required = false, property = "build-args")
	private String buildArgs = "";

	@Parameter(defaultValue = "${session}", readonly = true)
	private MavenSession session;

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	@Component
	private MavenProject mavenProject;

	@Component
	private MavenSession mavenSession;

	@Component
	private BuildPluginManager pluginManager;

	@Parameter(name = "b3-aggr-site", required = false, defaultValue = "http://download.eclipse.org/modeling/emft/b3/updates-4.3/")
	private String b3AggrMainSiteURL;

	@Parameter(name = "b3-aggr-deps-site", required = false, defaultValue = "http://download.eclipse.org/releases/kepler/")
	private String b3AggrReqSiteURL;

	@Parameter(name = "tycho-version", required = false, defaultValue = "0.23.0")
	private String tychoVersion;

	@Parameter(name = "p2repoindex-version", required = false, defaultValue = "0.2.0-SNAPSHOT")
	private String p2repoIndexVersion;

	public void execute() throws MojoExecutionException, MojoFailureException {

		// Handling Proxy Settings
		File proxyConfPath = null;
		try {
			proxyConfPath = generateProxyConfiguration();
			if (proxyConfPath != null && proxyConfPath.exists()) {
				buildArgs = (buildArgs != null ? buildArgs : "") + " -plugincustomization " + proxyConfPath.getPath();
			}
		} catch (IOException e) {
			getLog().warn("Could not generate proxy configuration. Some sites may not be reachable !");
		}

		// Defining right log level
		String logLevel = "ERROR";
		if (getLog().isDebugEnabled()) {
			logLevel = "DEBUG";
		} else if (getLog().isInfoEnabled()) {
			logLevel = "INFO";
		} else if (getLog().isWarnEnabled()) {
			logLevel = "WARNING";
		}

		// Preparing the mojo...
		Element repositories = element("repositories",
				element("repository", element("id", "B3 Aggregator"), element("layout", "p2"),
						element("url", b3AggrMainSiteURL)),
				element("repository", element("id", "B3 Requirements"), element("layout", "p2"),
						element("url", b3AggrReqSiteURL)));

		Element dependencies = element("dependencies", element("dependency",
				element("artifactId", "org.eclipse.b3.aggregator.engine.feature"), element("type", "eclipse-feature")));

		Element argLine = element("argLine", this.javaArgs != null ? this.javaArgs : "");

		Element appArgLine = element("appArgLine",
				String.format(
						"-application org.eclipse.b3.cli.headless aggregate --buildModel %s --buildRoot %s --action %s --eclipseLogLevel %s --logLevel %S --stacktrace %s",
						modelPath, buildRootPath, buildAction, logLevel, logLevel, buildArgs != null ? buildArgs : ""));

		Xpp3Dom configuration = configuration(repositories, appArgLine, argLine, dependencies);

		// Executing the mojo...
		executeMojo(plugin("org.eclipse.tycho.extras", "tycho-eclipserun-plugin", tychoVersion), goal("eclipse-run"),
				configuration, executionEnvironment(mavenProject, mavenSession, pluginManager));

		// If requested, we generate the index there as well
		if (withIndex) {
			executeMojo(plugin("com.worldline.mojo", "p2repo-index-plugin", p2repoIndexVersion), goal("generate-index"),
					configuration(element("repositoryPath",
							buildRootPath + (buildRootPath.endsWith(File.separator) ? "" : File.separator) + "final")),
					executionEnvironment(mavenProject, mavenSession, pluginManager));
		}
	}

	private File generateProxyConfiguration() throws IOException {
		boolean hasProxyConfiguration = System.getProperty("http.proxyHost") != null
				|| System.getProperty("http.proxyPort") != null || System.getProperty("http.nonProxyHosts") != null
				|| System.getProperty("https.proxyHost") != null || System.getProperty("https.proxyPort") != null;

		if (hasProxyConfiguration) {
			File file = new File(
					buildRootPath + (buildRootPath.endsWith(File.separator) ? "" : File.separator) + "proxyConf.ini");
			FileWriter fileWriter = new FileWriter(file);
			fileWriter.write("org.eclipse.core.net/org.eclipse.core.net.hasMigrated=true\n");
			fileWriter.write("org.eclipse.core.net/systemProxiesEnabled=false\n");
			if (System.getProperty("http.proxyHost") != null && System.getProperty("http.proxyPort") != null) {
				fileWriter.write(
						"org.eclipse.core.net/proxyData/HTTP/host=" + System.getProperty("http.proxyHost") + "\n");
				fileWriter.write(
						"org.eclipse.core.net/proxyData/HTTP/port=" + System.getProperty("http.proxyPort") + "\n");
				fileWriter.write("org.eclipse.core.net/proxyData/HTTP/hasAuth=false\n");
			}
			if (System.getProperty("https.proxyHost") != null && System.getProperty("https.proxyPort") != null) {
				fileWriter.write(
						"org.eclipse.core.net/proxyData/HTTPS/host=" + System.getProperty("https.proxyHost") + "\n");
				fileWriter.write(
						"org.eclipse.core.net/proxyData/HTTPS/port=" + System.getProperty("https.proxyPort") + "\n");
				fileWriter.write("org.eclipse.core.net/proxyData/HTTPS/hasAuth=false\n");
			}
			if (System.getProperty("http.nonProxyHosts") != null) {
				fileWriter.write(
						"org.eclipse.core.net/nonProxiedHosts=" + System.getProperty("https.nonProxyHosts") + "\n");
			}
			fileWriter.close();
			return file;
		}
		return null;
	}
}
