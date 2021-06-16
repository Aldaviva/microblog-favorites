package com.microsoft.playwright.impl;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Selectors;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CustomPlaywrightImpl implements Playwright {

	private final PlaywrightImpl wrapped;
	private final Process driverProcess;

	public static CustomPlaywrightImpl create() {
		return create(null, null);
	}

	public static CustomPlaywrightImpl create(final Collection<String> browsersToInstall) {
		return create(browsersToInstall, null);
	}

	public static CustomPlaywrightImpl create(final Map<String, String> extraEnvironmentVariables) {
		return create(null, extraEnvironmentVariables);
	}

	public static CustomPlaywrightImpl create(final Collection<String> browsersToInstall, final Map<String, String> extraEnvironmentVariables) {
		try {
			final Path driver = CustomDriver.ensureDriverInstalled(browsersToInstall, extraEnvironmentVariables);
			final ProcessBuilder pb = new ProcessBuilder(driver.toString(), "run-driver");
			pb.redirectError(ProcessBuilder.Redirect.INHERIT);

			/*
			 * Added by Ben: allow extra environment variables to be passed to the driver process
			 */
			if (extraEnvironmentVariables != null) {
				pb.environment().putAll(extraEnvironmentVariables);
			}

			final Process p = pb.start();
			final Connection connection = new Connection(new PipeTransport(p.getInputStream(), p.getOutputStream()));
			final PlaywrightImpl wrapped = (PlaywrightImpl) connection.waitForObjectWithKnownName("Playwright");
			final CustomPlaywrightImpl result = new CustomPlaywrightImpl(wrapped, p);
			wrapped.initSharedSelectors(null);
			return result;
		} catch (final IOException e) {
			throw new PlaywrightException("Failed to launch driver", e);
		}
	}

	private CustomPlaywrightImpl(final PlaywrightImpl wrapped, final Process driverProcess) {
		this.wrapped = wrapped;
		this.driverProcess = driverProcess;
	}

	@Override
	public BrowserType chromium() {
		return wrapped.chromium();
	}

	@Override
	public BrowserType firefox() {
		return wrapped.firefox();
	}

	@Override
	public BrowserType webkit() {
		return wrapped.webkit();
	}

	@Override
	public Selectors selectors() {
		return wrapped.selectors();
	}

	@Override
	public void close() {
		try {
			wrapped.connection.close();
			// playwright-cli will exit when its stdin is closed, we wait for that.
			final boolean didClose = driverProcess.waitFor(30, TimeUnit.SECONDS);
			if (!didClose) {
				System.err.println("WARNING: Timed out while waiting for driver process to exit");
			}
		} catch (final IOException e) {
			throw new PlaywrightException("Failed to terminate", e);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PlaywrightException("Operation interrupted", e);
		}
	}

}
