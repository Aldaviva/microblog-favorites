package com.microsoft.playwright.impl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CustomDriver extends Driver {
	private static CustomDriver instance;
	private final Path driverTempDir;

	public static synchronized Path ensureDriverInstalled(final Collection<String> browsersToInstall, final Map<String, String> extraEnvironmentVariables) {
		if (instance == null) {
			try {
				if (System.getProperty("playwright.cli.dir") != null) {
					return Driver.ensureDriverInstalled();
				}

				instance = new CustomDriver(browsersToInstall, extraEnvironmentVariables);
			} catch (final Exception exception) {
				throw new RuntimeException("Failed to create driver", exception);
			}
		}
		final String name = instance.cliFileName();
		return instance.driverDir().resolve(name);
	}

	private CustomDriver(final Collection<String> browsersToInstall, final Map<String, String> extraEnvironmentVariables)
	    throws IOException, URISyntaxException, InterruptedException {
		driverTempDir = Files.createTempDirectory("playwright-java-");
		driverTempDir.toFile().deleteOnExit();
		extractDriverToTempDir();
		installBrowsers(browsersToInstall, extraEnvironmentVariables);
	}

	@Override
	Path driverDir() {
		return driverTempDir;
	}

	private void installBrowsers(final Collection<String> browsersToInstall, final Map<String, String> extraEnvironmentVariables)
	    throws IOException, InterruptedException {
		final String cliFileName = super.cliFileName();
		final Path driver = driverTempDir.resolve(cliFileName);
		if (!Files.exists(driver)) {
			throw new RuntimeException("Failed to find " + cliFileName + " at " + driver);
		}

		final List<String> installCommandAndArguments = new ArrayList<>();
		installCommandAndArguments.add(driver.toString());
		installCommandAndArguments.add("install");
		if (browsersToInstall != null) {
			/*
			 * Added by Ben: pass an extra optional argument to restrict which browsers are installed
			 */
			installCommandAndArguments.addAll(browsersToInstall);
		}

		final ProcessBuilder pb = new ProcessBuilder(installCommandAndArguments);
		pb.redirectError(ProcessBuilder.Redirect.INHERIT);
		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

		if (extraEnvironmentVariables != null) {
			/*
			 * Added by Ben: allow extra environment variables to be passed to the install process
			 */
			pb.environment().putAll(extraEnvironmentVariables);
		}

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

	private void extractDriverToTempDir() throws URISyntaxException, IOException {
		final ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		final URI originalUri = classloader.getResource("driver/" + platformDir()).toURI();
		final URI uri = maybeExtractNestedJar(originalUri);

		// Create zip filesystem if loading from jar.
		try (FileSystem fileSystem = "jar".equals(uri.getScheme()) ? FileSystems.newFileSystem(uri, Collections.emptyMap()) : null) {
			final Path srcRoot = Paths.get(uri);
			// jar file system's .relativize gives wrong results when used with
			// spring-boot-maven-plugin, convert to the default filesystem to
			// have predictable results.
			// See https://github.com/microsoft/playwright-java/issues/306
			final Path srcRootDefaultFs = Paths.get(srcRoot.toString());
			Files.walk(srcRoot).forEach(fromPath -> {
				final Path relative = srcRootDefaultFs.relativize(Paths.get(fromPath.toString()));
				final Path toPath = driverTempDir.resolve(relative.toString());
				try {
					if (Files.isDirectory(fromPath)) {
						Files.createDirectories(toPath);
					} else {
						Files.copy(fromPath, toPath);
						if (isExecutable(toPath)) {
							toPath.toFile().setExecutable(true, true);
						}
					}
					toPath.toFile().deleteOnExit();
				} catch (final IOException e) {
					throw new RuntimeException("Failed to extract driver from " + uri + ", full uri: " + originalUri, e);
				}
			});
		}
	}

	private static boolean isExecutable(final Path filePath) {
		final String name = filePath.getFileName().toString();
		return name.endsWith(".sh") || name.endsWith(".exe") || !name.contains(".");
	}

	private URI maybeExtractNestedJar(final URI uri) throws URISyntaxException {
		if (!"jar".equals(uri.getScheme())) {
			return uri;
		}
		final String JAR_URL_SEPARATOR = "!/";
		final String[] parts = uri.toString().split("!/");
		if (parts.length != 3) {
			return uri;
		}
		final String innerJar = String.join(JAR_URL_SEPARATOR, parts[0], parts[1]);
		final URI jarUri = new URI(innerJar);
		try (FileSystem fs = FileSystems.newFileSystem(jarUri, Collections.emptyMap())) {
			final Path fromPath = Paths.get(jarUri);
			final Path toPath = driverTempDir.resolve(fromPath.getFileName().toString());
			Files.copy(fromPath, toPath);
			toPath.toFile().deleteOnExit();
			return new URI("jar:" + toPath.toUri() + JAR_URL_SEPARATOR + parts[2]);
		} catch (final IOException e) {
			throw new RuntimeException("Failed to extract driver's nested .jar from " + jarUri + "; full uri: " + uri, e);
		}
	}

	private static String platformDir() {
		final String name = System.getProperty("os.name").toLowerCase();
		if (name.contains("windows")) {
			return System.getProperty("os.arch").equals("amd64") ? "win32_x64" : "win32";
		}
		if (name.contains("linux")) {
			return "linux";
		}
		if (name.contains("mac os x")) {
			return "mac";
		}
		throw new RuntimeException("Unexpected os.name value: " + name);
	}

}
