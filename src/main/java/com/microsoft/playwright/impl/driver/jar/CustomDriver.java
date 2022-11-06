package com.microsoft.playwright.impl.driver.jar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Like {@link DriverJar} except you have it pass extra arguments to {@code playwright.cmd install} so you can restrict which browsers are installed.
 */
public class CustomDriver extends DriverJar {

	protected static final String PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD";
	protected static final String SELENIUM_REMOTE_URL = "SELENIUM_REMOTE_URL";
	static final String PLAYWRIGHT_NODEJS_PATH = "PLAYWRIGHT_NODEJS_PATH";
	public static final String PLAYWRIGHT_BROWSERS_TO_INSTALL = "PLAYWRIGHT_BROWSERS_TO_INSTALL";

	protected Path preinstalledNodePath;

	public CustomDriver() throws IOException {
		super();
		final String nodePath = System.getProperty("playwright.nodejs.path");
		if (nodePath != null) {
			preinstalledNodePath = Paths.get(nodePath);
			if (!Files.exists(preinstalledNodePath)) {
				throw new RuntimeException("Invalid Node.js path specified: " + nodePath);
			}
		}
	}

	@Override
	protected void initialize(final Boolean installBrowsers) throws Exception {
		if (preinstalledNodePath == null && env.containsKey(PLAYWRIGHT_NODEJS_PATH)) {
			preinstalledNodePath = Paths.get(env.get(PLAYWRIGHT_NODEJS_PATH));
			if (!Files.exists(preinstalledNodePath)) {
				throw new RuntimeException("Invalid Node.js path specified: " + preinstalledNodePath);
			}
		} else if (preinstalledNodePath != null) {
			// Pass the env variable to the driver process.
			env.put(PLAYWRIGHT_NODEJS_PATH, preinstalledNodePath.toString());
		}
		extractDriverToTempDir();
		logMessage("extracted driver from jar to " + driverPath());
		if (installBrowsers) {
			installBrowsers(env);
		}
	}

	private void installBrowsers(final Map<String, String> env) throws IOException, InterruptedException {
		String skip = env.get(PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD);
		if (skip == null) {
			skip = System.getenv(PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD);
		}
		if (skip != null && !"0".equals(skip) && !"false".equals(skip)) {
			logMessage("Skipping browsers download because `" + PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD + "` env variable is set");
			return;
		}
		if (env.get(SELENIUM_REMOTE_URL) != null || System.getenv(SELENIUM_REMOTE_URL) != null) {
			logMessage("Skipping browsers download because `" + SELENIUM_REMOTE_URL + "` env variable is set");
			return;
		}
		final Path driver = driverPath();
		if (!Files.exists(driver)) {
			throw new RuntimeException("Failed to find driver: " + driver);
		}
		final ProcessBuilder pb = createProcessBuilder();
		pb.command().add("install");

		String browsersToInstallString = env.get(PLAYWRIGHT_BROWSERS_TO_INSTALL);
		if (browsersToInstallString == null) {
			browsersToInstallString = System.getenv(PLAYWRIGHT_BROWSERS_TO_INSTALL);
		}

		if (browsersToInstallString != null) {
			/*
			 * Added by Ben: pass an extra optional argument to restrict which browsers are installed
			 */
			pb.command().addAll(Arrays.asList(browsersToInstallString.split(",| ")));
		}

		pb.redirectError(ProcessBuilder.Redirect.INHERIT);
		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		/*
		 * Added by Ben: allow extra environment variables to be passed to the install process
		 */
		pb.environment().putAll(env);
		final Process p = pb.start();
		final boolean result = p.waitFor(10, TimeUnit.MINUTES);
		if (!result) {
			p.destroy();
			throw new RuntimeException("Timed out waiting for browsers to install");
		}
		if (p.exitValue() != 0) {
			throw new RuntimeException("Failed to install browsers, exit code: " + p.exitValue());
		}
	}

}
