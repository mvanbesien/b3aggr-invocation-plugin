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

/**
 * 
 * Mojo implementation of the B3 Aggregation Invocation Maven Plugin.
 * 
 * @author mvanbesien (mvaawl@gmail.com)
 *
 */
@Mojo(name = "aggregate", requiresProject = false, threadSafe = false)
public class B3AggrInvocationMojo extends AbstractMojo {

	/**
	 * Path to the model to build.
	 */
	@Parameter(required = true, property = "build-model")
	private String modelPath;

	/**
	 * Patho to where the aggregation and temporary files will be
	 * generated/copied.
	 */
	@Parameter(required = false, property = "build-root", defaultValue = "${project.basedir}/target/build")
	private String buildRootPath;

	/**
	 * Aggregation action to execute. Values can be VALIDATE, BUILD, CLEAN,
	 * CLEAN_BUILD
	 */
	@Parameter(required = false, property = "build-action", defaultValue = "CLEAN_BUILD")
	private String buildAction;

	/**
	 * Arguments to be passed to the Java command line that will execute the b3
	 * process (eclipse headless delegation)
	 */
	@Parameter(required = false, property = "java-args")
	private String javaArgs = "";

	/**
	 * Boolean parameter, when valuated to true, will generate an index into the
	 * built site, using the p2repo-index-plugin (see AWLTech github community)
	 */
	@Parameter(required = false, property = "with-index", defaultValue = "false")
	private boolean withIndex;

	/**
	 * Arguments to be passed to the b3 execution process.
	 */
	@Parameter(required = false, property = "build-args")
	private String buildArgs = "";

	/*
	 * Internal field holding the current maven session
	 */
	@Parameter(defaultValue = "${session}", readonly = true)
	private MavenSession mavenSession;

	/*
	 * Internal field holding the current maven project
	 */
	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject mavenProject;

	/*
	 * Internal field holding the current maven plugin manager
	 */
	@Component
	private BuildPluginManager pluginManager;

	/**
	 * Parameter allowing to customize the Update Site URL that will be used to
	 * locate the b3 aggregation binaries
	 */
	@Parameter(name = "b3-aggr-site", required = false, defaultValue = "http://download.eclipse.org/modeling/emft/b3/updates-4.3/")
	private String b3AggrMainSiteURL;

	/**
	 * Parameter allowing to customize the Update Site URL that will be used to
	 * locate the b3 aggregation requirement binaries
	 */
	@Parameter(name = "b3-aggr-deps-site", required = false, defaultValue = "http://download.eclipse.org/releases/kepler/")
	private String b3AggrReqSiteURL;

	/**
	 * Parameter allowing to customize the tycho version
	 */
	@Parameter(name = "tycho-version", required = false, defaultValue = "0.23.0")
	private String tychoVersion;

	/**
	 * Parameter allowing to customize the p2repo-index-plugin version
	 */
	@Parameter(name = "p2repoindex-version", required = false, defaultValue = "0.2.0-SNAPSHOT")
	private String p2repoIndexVersion;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.maven.plugin.Mojo#execute()
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {

		// Handling Proxy Settings, when specified. Because proxy is handled in
		// B3, only using the eclipse configuration as p2 relies on the
		// HTTPClient of Apache
		File proxyConfPath = null;
		try {
			proxyConfPath = generateProxyConfiguration();
			if (proxyConfPath != null && proxyConfPath.exists()) {
				buildArgs = (buildArgs != null ? buildArgs : "") + " -plugincustomization " + proxyConfPath.getPath();
			}
		} catch (IOException e) {
			getLog().warn("Could not generate proxy configuration. Some sites may not be reachable !");
		}

		// Defining right log level, to synchronize the Eclipse's one with the
		// Maven's one.
		String logLevel = "ERROR";
		if (getLog().isDebugEnabled()) {
			logLevel = "DEBUG";
		} else if (getLog().isInfoEnabled()) {
			logLevel = "INFO";
		} else if (getLog().isWarnEnabled()) {
			logLevel = "WARNING";
		}

		// Generating the configuration of the Mojo, we are going to delegate
		// the aggregation to.
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

		// Executing the mojo, this plugin's delegating to...
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

	/**
	 * Generates ini file with the proxy configuration, to be used in the
	 * Eclipse process.
	 * 
	 * @return
	 * @throws IOException
	 */
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
			// Http proxy
			if (System.getProperty("http.proxyHost") != null && System.getProperty("http.proxyPort") != null) {
				fileWriter.write(
						"org.eclipse.core.net/proxyData/HTTP/host=" + System.getProperty("http.proxyHost") + "\n");
				fileWriter.write(
						"org.eclipse.core.net/proxyData/HTTP/port=" + System.getProperty("http.proxyPort") + "\n");
				fileWriter.write("org.eclipse.core.net/proxyData/HTTP/hasAuth=false\n");
			}
			// Https proxy
			if (System.getProperty("https.proxyHost") != null && System.getProperty("https.proxyPort") != null) {
				fileWriter.write(
						"org.eclipse.core.net/proxyData/HTTPS/host=" + System.getProperty("https.proxyHost") + "\n");
				fileWriter.write(
						"org.eclipse.core.net/proxyData/HTTPS/port=" + System.getProperty("https.proxyPort") + "\n");
				fileWriter.write("org.eclipse.core.net/proxyData/HTTPS/hasAuth=false\n");
			}
			// Non Proxy Hosts
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
