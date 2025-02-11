package com.aldaviva.microblog_favorites;

import com.aldaviva.microblog_favorites.http.JacksonConfig.CustomJacksonFeature;
import com.aldaviva.microblog_favorites.http.JacksonConfig.CustomObjectMapperProvider;
import com.aldaviva.microblog_favorites.http.UnfuckedCookieSerializer;
import com.aldaviva.microblog_favorites.services.bluesky.BlueskyDownloader;
import com.aldaviva.microblog_favorites.services.mastodon.MastodonDownloader;
import com.aldaviva.microblog_favorites.services.nixplay.NixplayUploader;
import com.aldaviva.microblog_favorites.services.nixplay.data.Album;
import com.aldaviva.microblog_favorites.services.nixplay.data.Playlist;
import com.aldaviva.microblog_favorites.services.twitter.TwitterGraphQlDownloader;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Browser.NewContextOptions;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Playwright.CreateOptions;
import com.microsoft.playwright.impl.PlaywrightImpl;
import com.microsoft.playwright.impl.driver.jar.CustomDriver;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.logging.LoggingFeature.Verbosity;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class Main {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Main.class);

	private static final int SCREENSHOT_DPI_MULTIPLIER = 3;
	private static final Pattern WORD = Pattern.compile("(?<word>(?<head>\\w)(?<tail>\\w*))");

	public static void main(final String[] args) throws IOException, URISyntaxException {
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
		System.setProperty("sun.net.http.allowRestrictedHeaders", "true"); // stop omitting a subset of headers in requests to Nixplay, to avoid 403 errors

		final Client httpClient = createHttpClient();

		final NixplayUploader nixplay = new NixplayUploader(httpClient);
		final Collection<FavoritesDownloader<? extends FavoritePost>> favoriteDownloaders = new ArrayList<>();
		favoriteDownloaders.add(new TwitterGraphQlDownloader(httpClient));
		favoriteDownloaders.add(new BlueskyDownloader(httpClient));
		favoriteDownloaders.add(new MastodonDownloader(httpClient));

		LOGGER.info("Logging into Nixplay...");
		nixplay.signIn(ConfigurationFactory.getNixplayCredentials());

		LOGGER.info("Initializing browser...");
		// Only download Chromium (& ffmpeg), not Firefox or WebKit, in order to save download time, download quota, and disk space
		System.setProperty("playwright.driver.impl", CustomDriver.class.getName());
		int favoritesDownloaded = 0;
		try (final Playwright playwright = PlaywrightImpl.create(new CreateOptions().setEnv(Collections.singletonMap(CustomDriver.PLAYWRIGHT_BROWSERS_TO_INSTALL, "chromium")))) {
			final Path storageStatePath = new File(FavoritesDownloader.ONLINE_SERVICES_BACKUP_DIRECTORY, "storage.json").toPath();
			final Browser loginBrowser = playwright.chromium().launch(new LaunchOptions().setHeadless(false));
			final BrowserContext loginBrowserContext = loginBrowser.newContext(new NewContextOptions()
			    .setStorageStatePath(Files.exists(storageStatePath) ? storageStatePath : null)); //setStorageStatePath crashes if storage file is not found

			// start log-in session, interactively if needed
			for (final FavoritesDownloader<?> favoriteDownloader : favoriteDownloaders) {
				try (Page page = loginBrowserContext.newPage()) {
					favoriteDownloader.signIn(page);
				}
			}

			Files.writeString(storageStatePath, loginBrowserContext.storageState(), StandardCharsets.UTF_8); //save storage
			loginBrowser.close();

			final Browser postBrowser = playwright.chromium().launch(new LaunchOptions().setHeadless(true));
			final BrowserContext postBrowserContext = postBrowser.newContext(new NewContextOptions()
			    .setDeviceScaleFactor(SCREENSHOT_DPI_MULTIPLIER)
			    .setViewportSize(1920, 1200) //tall enough for protected tweets to not get cut off, especially if they are replies to other long tweets that appear above them
			    .setStorageStatePath(storageStatePath));

			for (final FavoritesDownloader<? extends FavoritePost> downloader : favoriteDownloaders) {
				favoritesDownloaded += saveAndUploadNewFavorites(downloader, postBrowserContext, nixplay);
			}

			postBrowser.close();
		}

		for (final FavoritesDownloader<?> favoriteDownloader : favoriteDownloaders) {
			try {
				favoriteDownloader.close();
			} catch (final Exception e) {
			}
		}

		try {
			nixplay.close();
		} catch (final Exception e) {
		}

		LOGGER.info("Done, uploaded {} favorites.", favoritesDownloaded);
	}

	private static <P extends FavoritePost> int saveAndUploadNewFavorites(final FavoritesDownloader<P> downloader, final BrowserContext postBrowserContext, final NixplayUploader nixplay) {
		final List<P> newFavorites = downloader.listNewFavorites();

		for (final P favorite : newFavorites) {
			LOGGER.debug("Loading " + favorite.getPostTypeNoun(false) + " " + favorite.getId() + "...");
			try (Page page = postBrowserContext.newPage()) {
				final byte[] taggedImage = downloader.downloadFavorite(favorite, page);

				if (nixplay != null) {
					final Album album = nixplay.getOrCreateNextNonFullAlbum(downloader.getServiceName() + " Favorites ");
					final Playlist playlist = nixplay.getOrCreatePlaylist(album);
					final String filename = downloader.getFilename(favorite);

					nixplay.uploadToAlbumAndPlaylist(taggedImage, filename, album, playlist);
				}
			}
		}

		return newFavorites.size();
	}

	private static Client createHttpClient() {
		final ClientConfig clientConfig = new ClientConfig();
		clientConfig.register(CustomObjectMapperProvider.class);
		clientConfig.register(CustomJacksonFeature.class);
		clientConfig.register(UnfuckedCookieSerializer.class);
		clientConfig.property(ClientProperties.CONNECT_TIMEOUT, 5 * 1000);
		clientConfig.property(ClientProperties.READ_TIMEOUT, 30 * 1000);
		clientConfig.property(ClientProperties.FOLLOW_REDIRECTS, false);

		// Set the http logger to TRACE level for HTTP wire logging in logback.xml
		final Logger httpLogger = Logger.getLogger("http");
		httpLogger.setLevel(Level.ALL);
		clientConfig.register(new LoggingFeature(httpLogger, Level.FINEST, Verbosity.PAYLOAD_ANY, 1 * 1024 * 1024)); // LoggingInterceptor actually allocates maxEntitySize bytes for every request, so probably shouldn't be MAX_INT

		return ClientBuilder.newClient(clientConfig);
	}

	public static String titleCase(final String input) {
		final Matcher matcher = WORD.matcher(input);
		final StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			matcher.appendReplacement(result, matcher.group("head").toUpperCase() + matcher.group("tail"));
		}
		matcher.appendTail(result);
		return result.toString();
	}

}