package com.aldaviva.twitter_favorites;

import com.aldaviva.twitter_favorites.nixplay.JerseyNixplayClient;
import com.aldaviva.twitter_favorites.nixplay.NixplayClient;
import com.aldaviva.twitter_favorites.nixplay.data.Album;
import com.aldaviva.twitter_favorites.nixplay.data.FrameStatus;
import com.aldaviva.twitter_favorites.nixplay.data.Photo;
import com.aldaviva.twitter_favorites.nixplay.data.Playlist;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Browser.NewContextOptions;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.ElementHandle.ScreenshotOptions;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.AddStyleTagOptions;
import com.microsoft.playwright.Page.WaitForURLOptions;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.impl.CustomPlaywrightImpl;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.ScreenshotType;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.common.bytesource.ByteSource;
import org.apache.commons.imaging.common.bytesource.ByteSourceArray;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.jpeg.iptc.IptcRecord;
import org.apache.commons.imaging.formats.jpeg.iptc.IptcTypes;
import org.apache.commons.imaging.formats.jpeg.iptc.JpegIptcRewriter;
import org.apache.commons.imaging.formats.jpeg.iptc.PhotoshopApp13Data;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.slf4j.bridge.SLF4JBridgeHandler;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

public class Main {

	private static final int SCREENSHOT_DPI_MULTIPLIER = 3;
	private static final int MAX_TWEETS_PER_PAGE = 200;
	private static final int MAX_PHOTOS_PER_PLAYLIST = 2000; //Nixplay playlist limit
	private static final long ONE_DAY_IN_MILLIS = Duration.ofDays(1).toMillis();
	private static final ZoneId MY_TIME_ZONE = ZoneId.of("America/Los_Angeles");
	private static final DateTimeFormatter EXIF_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US).withZone(MY_TIME_ZONE);
	private static final DateTimeFormatter IPTC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US).withZone(MY_TIME_ZONE);
	private static final Pattern FILE_BASENAME_EXTENSION_SPLITTER = Pattern.compile("\\.(?=[^\\.]+$)");
	private static final FilenameFilter SCREENSHOT_FILE_FILTER = new ScreenshotFileFilter();
	private static final File DATA_DIRECTORY = new File("E:\\Backup\\Online services\\Twitter\\Favorites");
	private static final String NIXPLAY_ALBUM_PREFIX = "Favorite Tweets ";
	private static final ExifRewriter EXIF_REWRITER = new ExifRewriter();

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Main.class);

	public static void main(final String[] args) throws IOException, URISyntaxException {
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();

		final Options cliOptions = new Options();
		final Option quietMode = new Option("q", "quiet", false, "Hide brief browser window when starting, even if Twitter is logged out");
		cliOptions.addOption(quietMode);

		final CommandLineParser cliParser = new DefaultParser();
		CommandLine cliArguments;
		try {
			cliArguments = cliParser.parse(cliOptions, args);
		} catch (final ParseException e) {
			System.err.println("Failed to parse command-line arguments: " + e.getMessage());
			return;
		}

		final boolean isQuietMode = cliArguments.hasOption(quietMode.getOpt());

		final File screenshotsDirectory = new File(DATA_DIRECTORY, "screenshots");
		screenshotsDirectory.mkdirs();

		int subdirectoryId = 0;
		File subdirectory = null;
		int subdirectoryChildCount = 0;

		final Set<Long> previouslySavedTweetIds = new HashSet<>();
		Files.walkFileTree(screenshotsDirectory.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
				if (attrs.isRegularFile()) {
					final String filename = file.getFileName().toString();
					final String basename = splitFileBaseNameAndExtension(filename)[0];

					try {
						final long tweetId = Long.parseLong(basename);
						previouslySavedTweetIds.add(tweetId);
					} catch (final NumberFormatException e) {
						//ignore filenames that aren't tweet IDs
					}
				}

				return FileVisitResult.CONTINUE;
			}
		});

		int nixplayAlbumNumber = 0;
		Album nixplayAlbum = null;
		Playlist nixplayPlaylist = null;

		LOGGER.info("Logging into Nixplay...");
		final NixplayClient nixplay = new JerseyNixplayClient();
		nixplay.signIn(ConfigurationFactory.createNixplayCredentials());
		final List<Album> nixplayAlbums = nixplay.listAlbums();
		final List<Playlist> nixplayPlaylists = nixplay.listPlaylists();
		final List<FrameStatus> nixplayFrames = nixplay.listFrameStatuses().frames;

		LOGGER.info("Initializing browser...");
		//only download Chromium (& ffmpeg), not Firefox or WebKit, in order to save download time, download quota, and disk space
		try (Playwright playwright = CustomPlaywrightImpl.create("chromium")) {
			final Path storageStatePath = new File(DATA_DIRECTORY, "storage.json").toPath();
			final Browser loginBrowser = playwright.chromium().launch(new LaunchOptions().setHeadless(isQuietMode));
			final BrowserContext loginBrowserContext = loginBrowser.newContext(new NewContextOptions()
			    .setStorageStatePath(Files.exists(storageStatePath) ? storageStatePath : null)); //setStorageStatePath crashes if storage file is not found

			// start log-in session, interactively if needed, for protected tweets
			try (Page page = loginBrowserContext.newPage()) {
				page.navigate("https://twitter.com");
				LOGGER.info("Waiting for user to log in to Twitter...");
				final double loginTimeoutMs = isQuietMode ? 20 * 1000 : ONE_DAY_IN_MILLIS;
				page.waitForURL("https://twitter.com/home", new WaitForURLOptions().setTimeout(loginTimeoutMs)); // give the user enough time to log in

				// Set page theme to Lights Out (black background) for protected tweets
				page.navigate("https://twitter.com/i/display"); // keyboard shortcuts broke, so use URL routing instead
				page.click("input[type=radio][aria-label='Lights out']"); //use click instead of check, because check tries to read the checked attribute after writing, but twitter removes/detaches the element on click
				page.keyboard().press("Escape");
			}

			Files.writeString(storageStatePath, loginBrowserContext.storageState(), StandardCharsets.UTF_8); //save storage
			loginBrowser.close();

			final Browser tweetBrowser = playwright.chromium().launch(new LaunchOptions().setHeadless(true));
			final BrowserContext tweetBrowserContext = tweetBrowser.newContext(new NewContextOptions()
			    .setDeviceScaleFactor(SCREENSHOT_DPI_MULTIPLIER)
			    .setViewportSize(1920, 1200) //tall enough for protected tweets to not get cut off, especially if they are replies to other long tweets that appear above them
			    .setStorageStatePath(storageStatePath));

			final String customEmbeddedStyle = readResourceFileAsString("/styles/embedded-tweet.css");
			final String customProtectedStyle = readResourceFileAsString("/styles/protected-tweet.css");

			final ConfigurationBuilder config = ConfigurationFactory.createTwitterConfigurationBuilder();
			config.setTrimUserEnabled(false);
			config.setTweetModeExtended(true);
			final Twitter twitter = new TwitterFactory(config.build()).getInstance();

			for (int pageNumber = 1;; pageNumber++) {
				final ResponseList<Status> favorites = twitter.favorites().getFavorites(new Paging(pageNumber, MAX_TWEETS_PER_PAGE));
				if (favorites.isEmpty()) {
					// last page finished, no more favorites
					break;
				}

				for (final Status favorite : favorites) {
					final long tweetId = favorite.getId();
					final String authorName = favorite.getUser().getName();
					final String authorHandle = favorite.getUser().getScreenName();
					final Instant tweetDate = favorite.getCreatedAt().toInstant();
					final String tweetBody = favorite.getText();
					final String tweetUrl = "https://twitter.com/" + authorHandle + "/status/" + tweetId;
					final String embeddedTweetUrl = "https://platform.twitter.com/embed/Tweet.html?dnt=false&embedId=twitter-widget-0&frame=false&hideCard=false&hideThread=false&id="
					    + tweetId + "&lang=en&theme=dark"; // refuses to load protected tweets
					final boolean isProtected = favorite.getUser().isProtected();

					if (previouslySavedTweetIds.contains(tweetId)) {
						// screenshot already exists on disk, skip to next tweet
						continue;
					}

					while (subdirectoryId == 0 || subdirectoryChildCount >= MAX_PHOTOS_PER_PLAYLIST) {
						subdirectoryId++;
						subdirectory = new File(screenshotsDirectory, String.valueOf(subdirectoryId));
						subdirectory.mkdirs();
						subdirectoryChildCount = countScreenshotsInDirectory(subdirectory);
					}

					final File screenshotFile = new File(subdirectory, tweetId + ".jpg");

					while (nixplayAlbum == null || nixplayAlbum.photoCount >= MAX_PHOTOS_PER_PLAYLIST) {
						nixplayAlbumNumber++;
						final String albumTitle = NIXPLAY_ALBUM_PREFIX + nixplayAlbumNumber;

						nixplayAlbum = nixplayAlbums.stream().filter(album -> albumTitle.equals(album.title)).findAny().orElseGet(() -> {
							final Album album = nixplay.createAlbum(albumTitle);
							nixplayAlbums.add(album);
							LOGGER.info("Created Nixplay album " + albumTitle);
							return album;
						});
						nixplayPlaylist = null;
					}

					if (nixplayPlaylist == null) {
						final String albumTitle = nixplayAlbum.title;
						nixplayPlaylist = nixplayPlaylists.stream().filter(playlist -> albumTitle.equals(playlist.name)).findAny()
						    .orElseGet(() -> {
							    final Playlist playlist = nixplay.createPlaylist(albumTitle);
							    LOGGER.info("Created Nixplay playlist " + albumTitle);
							    for (final FrameStatus frame : nixplayFrames) {
								    nixplay.enablePlaylistOnFrame(playlist, frame);
								    LOGGER.info("Enabled Nixplay playlist on frame " + frame.framePk);
							    }
							    return playlist;
						    });
					}

					LOGGER.debug("Loading tweet " + tweetId + "...");
					try (Page page = tweetBrowserContext.newPage()) {
						final String pageUrl = isProtected ? tweetUrl : embeddedTweetUrl;
						page.navigate(pageUrl);

						page.addStyleTag(new AddStyleTagOptions().setContent(isProtected ? customProtectedStyle : customEmbeddedStyle));
						page.waitForLoadState(isProtected ? LoadState.LOAD : LoadState.NETWORKIDLE); //network idle takes like 60 seconds to happen for logged-in twitter webapp pages, maybe an open websocket?

						if (isProtected) {
							page.waitForTimeout(2000);
						}

						final String screenshotSelector = isProtected
						    ? "div[style *= 'position: absolute;']:has(div > div > article a[href='https://help.twitter.com/using-twitter/how-to-tweet#source-labels'])"
						    : "//article/..";
						final ElementHandle screenshotEl = page.querySelector(screenshotSelector);
						if (screenshotEl == null) {
							throw new RuntimeException("Failed to find element on page " + pageUrl + " with selector " + screenshotSelector);
						}

						final byte[] screenshotBytes = screenshotEl.screenshot(new ScreenshotOptions()
						    .setQuality(80)
						    .setType(ScreenshotType.JPEG));

						final ByteSource byteSource = new ByteSourceArray(screenshotBytes);

						final List<IptcRecord> newRecords = new ArrayList<>();
						newRecords.add(new IptcRecord(IptcTypes.BYLINE, normalizeCharacterSet(authorName, StandardCharsets.ISO_8859_1))); // IPTC only supports ISO-8859-1, not UTF-8 or anything, so remove any unsupported characters
						newRecords.add(new IptcRecord(IptcTypes.BYLINE_TITLE, authorHandle)); // I don't think Twitter handles can contain characters that require URL-encoding
						newRecords.add(new IptcRecord(IptcTypes.CAPTION_ABSTRACT, normalizeCharacterSet(tweetBody, StandardCharsets.ISO_8859_1)));
						newRecords.add(new IptcRecord(IptcTypes.DATE_CREATED, IPTC_DATE_FORMATTER.format(tweetDate)));
						newRecords.add(new IptcRecord(IptcTypes.SOURCE, tweetUrl));

						final PhotoshopApp13Data newData = new PhotoshopApp13Data(newRecords, Collections.emptyList());

						final ByteArrayOutputStream bytesWithIptc = new ByteArrayOutputStream();
						new JpegIptcRewriter().writeIPTC(byteSource, bytesWithIptc, newData);

						final TiffOutputSet exifOutputSet = new TiffOutputSet();
						final TiffOutputDirectory rootFolder = exifOutputSet.getOrCreateRootDirectory();
						rootFolder.add(TiffTagConstants.TIFF_TAG_DATE_TIME, EXIF_DATE_FORMATTER.format(tweetDate));
						rootFolder.add(TiffTagConstants.TIFF_TAG_ARTIST, authorName + " (" + authorHandle + ")");

						final TiffOutputDirectory exifFolder = exifOutputSet.getOrCreateExifDirectory();
						exifFolder.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, EXIF_DATE_FORMATTER.format(tweetDate));
						exifFolder.add(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED, EXIF_DATE_FORMATTER.format(Instant.now()));

						final ByteArrayOutputStream exifFileStream = new ByteArrayOutputStream();
						EXIF_REWRITER.updateExifMetadataLossless(bytesWithIptc.toByteArray(), exifFileStream, exifOutputSet);
						final byte[] exifFile = exifFileStream.toByteArray();

						final Photo nixplayPhoto = nixplay.uploadPhoto(exifFile, screenshotFile.getName(), nixplayAlbum);
						nixplay.appendPhotosToPlaylist(nixplayPlaylist, nixplayPhoto);
						LOGGER.debug("Uploaded tweet " + tweetId + " to Nixplay album and playlist " + nixplayAlbum.title);
						nixplayAlbum.photoCount++;

						try (FileOutputStream fileOutputStream = new FileOutputStream(screenshotFile);
						    OutputStream bufferedFileOutputStream = new BufferedOutputStream(fileOutputStream)) {
							bufferedFileOutputStream.write(exifFile);
							LOGGER.info("Saved tweet " + tweetId);
						}

						subdirectoryChildCount++;
						previouslySavedTweetIds.add(tweetId);

					} catch (final ImageReadException | ImageWriteException e) {
						throw new RuntimeException(e);
					}
				}
			}
		} catch (final TwitterException e1) {
			throw new RuntimeException(e1);
		} finally {
			nixplay.close();
		}
	}

	private static int countScreenshotsInDirectory(final File subdirectory) {
		return subdirectory.list(SCREENSHOT_FILE_FILTER).length;
	}

	private static String readResourceFileAsString(final String filePath) throws IOException, URISyntaxException {
		try (InputStream inputStream = Main.class.getResource(filePath).openStream();
		    ByteArrayOutputStream result = new ByteArrayOutputStream()) {

			final byte[] buffer = new byte[1024];
			for (int length; (length = inputStream.read(buffer)) != -1;) {
				result.write(buffer, 0, length);
			}
			return result.toString(StandardCharsets.UTF_8);
		}
	}

	private static String normalizeCharacterSet(final String input, final Charset outputCharacterSet) {
		final CharsetEncoder encoder = outputCharacterSet.newEncoder();
		encoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
		try {
			final ByteBuffer encode = encoder.encode(CharBuffer.wrap(input));
			return outputCharacterSet.decode(encode).toString();
		} catch (final CharacterCodingException e) {
			throw new RuntimeException(e);
		}
	}

	// https://stackoverflow.com/a/4546093/979493
	private static String[] splitFileBaseNameAndExtension(final String fileName) {
		final String[] split = FILE_BASENAME_EXTENSION_SPLITTER.split(fileName);
		String[] result;
		if (split.length == 1) {
			result = new String[2];
			result[0] = split[0];
			result[1] = null;
		} else {
			result = split;
		}
		return result;
	}

	private static final class ScreenshotFileFilter implements FilenameFilter {
		@Override
		public boolean accept(final File dir, final String name) {
			final String extension = splitFileBaseNameAndExtension(name)[1];
			return "jpg".equalsIgnoreCase(extension) || "jpeg".equalsIgnoreCase(extension);
		}
	}

}