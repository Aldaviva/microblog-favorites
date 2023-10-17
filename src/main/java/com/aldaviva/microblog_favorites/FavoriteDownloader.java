package com.aldaviva.twitter_favorites;

import com.aldaviva.twitter_favorites.services.nixplay.NixplayUploader;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.ElementHandle.ScreenshotOptions;
import com.microsoft.playwright.Page;
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
import java.net.URI;
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

public abstract class FavoriteDownloader<POST extends FavoritePost> implements AutoCloseable {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(FavoriteDownloader.class);

	public static final File ONLINE_SERVICES_BACKUP_DIRECTORY = new File("E:\\Backup\\Online services");
	private static final Pattern FILE_BASENAME_EXTENSION_SPLITTER = Pattern.compile("\\.(?=[^\\.]+$)");
	public static final long ONE_DAY_IN_MILLIS = Duration.ofDays(1).toMillis();
	private static final ZoneId MY_TIME_ZONE = ZoneId.of("America/Los_Angeles");
	private static final DateTimeFormatter EXIF_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US).withZone(MY_TIME_ZONE);
	private static final DateTimeFormatter IPTC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US).withZone(MY_TIME_ZONE);
	private static final ExifRewriter EXIF_REWRITER = new ExifRewriter();
	private static final FilenameFilter SCREENSHOT_FILE_FILTER = new ScreenshotFileFilter();

	protected final Set<String> previouslySavedPostIds = new HashSet<>();
	protected final File screenshotsDirectory = ONLINE_SERVICES_BACKUP_DIRECTORY.toPath().resolve(getServiceName()).resolve("Favorites").toFile();

	private int subdirectoryId = 0;
	private File subdirectory = null;
	private int subdirectoryChildCount = 0;

	public FavoriteDownloader() {
		screenshotsDirectory.mkdirs();

		try {
			Files.walkFileTree(screenshotsDirectory.toPath(), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
					if (attrs.isRegularFile()) {
						final String filename = file.getFileName().toString();
						final String basename = splitFileBaseNameAndExtension(filename)[0];

						previouslySavedPostIds.add(basename);
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (final IOException e) {
			throw new RuntimeException("Failed to list existing downloaded screenshots in " + screenshotsDirectory, e);
		}
	}

	/**
	 * The name of the service used in the backup directory. Must be a valid filesystem path segment.
	 */
	protected abstract String getServiceName();

	public abstract void signIn(final Page page);

	protected abstract List<POST> listAllFavorites();

	public final List<POST> listNewFavorites() {
		final List<POST> allFavorites = listAllFavorites();
		final List<POST> newFavorites = new ArrayList<>(allFavorites.size());
		for (final POST favorite : allFavorites) {
			if (!previouslySavedPostIds.contains(favorite.getId())) {
				newFavorites.add(favorite);
			}
		}
		return newFavorites;
	}

	public final byte[] downloadFavorite(final POST favorite, final Page page) {
		final File subdirectory = getOrCreateFirstNonFullSubdirectory();
		final File screenshotFile = new File(subdirectory, getFilename(favorite));

		final URI pageUrl = getPageUrl(favorite);
		page.navigate(pageUrl.toString());

		onNavigateToPage(page, favorite);
		waitForPageToLoad(page, favorite);

		final String screenshotSelector = getScreenshotSelector(favorite);
		final ElementHandle screenshotEl = page.querySelector(screenshotSelector);
		if (screenshotEl == null) {
			throw new RuntimeException("Failed to find element on page " + pageUrl + " with selector " + screenshotSelector);
		}

		final byte[] untaggedImage = screenshotEl.screenshot(new ScreenshotOptions()
		    .setQuality(80)
		    .setType(ScreenshotType.JPEG));

		final byte[] taggedImage = addMetadataToImage(untaggedImage, favorite);
		try (FileOutputStream fileOutputStream = new FileOutputStream(screenshotFile);
		    OutputStream bufferedFileOutputStream = new BufferedOutputStream(fileOutputStream)) {
			bufferedFileOutputStream.write(taggedImage);
			LOGGER.info("Saved " + favorite.getPostTypeNoun(false) + " " + favorite.getId() + " by " + favorite.getAuthorHandle());
		} catch (final IOException e) {
			throw new RuntimeException("Failed to save screenshot of post", e);
		}

		subdirectoryChildCount++;
		previouslySavedPostIds.add(favorite.getId());

		return taggedImage;
	}

	public final String getFilename(final FavoritePost favorite) {
		return favorite.getId() + ".jpg";
	}

	private byte[] addMetadataToImage(final byte[] untaggedImage, final FavoritePost favorite) {
		try {
			final ByteSource byteSource = new ByteSourceArray(untaggedImage);

			final List<IptcRecord> newRecords = new ArrayList<>();
			newRecords.add(new IptcRecord(IptcTypes.BYLINE, normalizeCharacterSet(favorite.getAuthorName(), StandardCharsets.ISO_8859_1))); // IPTC only supports ISO-8859-1, not UTF-8 or anything, so remove any unsupported characters
			newRecords.add(new IptcRecord(IptcTypes.BYLINE_TITLE, favorite.getAuthorHandle())); // I don't think Twitter handles can contain characters that require URL-encoding
			newRecords.add(new IptcRecord(IptcTypes.CAPTION_ABSTRACT, normalizeCharacterSet(favorite.getBody(), StandardCharsets.ISO_8859_1)));
			newRecords.add(new IptcRecord(IptcTypes.DATE_CREATED, IPTC_DATE_FORMATTER.format(favorite.getDate())));
			newRecords.add(new IptcRecord(IptcTypes.SOURCE, favorite.getUrl().toString()));

			final PhotoshopApp13Data newData = new PhotoshopApp13Data(newRecords, Collections.emptyList());

			final ByteArrayOutputStream bytesWithIptc = new ByteArrayOutputStream();
			new JpegIptcRewriter().writeIPTC(byteSource, bytesWithIptc, newData);

			final TiffOutputSet exifOutputSet = new TiffOutputSet();
			final TiffOutputDirectory rootFolder = exifOutputSet.getOrCreateRootDirectory();
			rootFolder.add(TiffTagConstants.TIFF_TAG_DATE_TIME, EXIF_DATE_FORMATTER.format(favorite.getDate()));
			rootFolder.add(TiffTagConstants.TIFF_TAG_ARTIST, favorite.getAuthorName() + " (" + favorite.getAuthorHandle() + ")");

			final TiffOutputDirectory exifFolder = exifOutputSet.getOrCreateExifDirectory();
			exifFolder.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, EXIF_DATE_FORMATTER.format(favorite.getDate()));
			exifFolder.add(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED, EXIF_DATE_FORMATTER.format(Instant.now()));

			final ByteArrayOutputStream exifFileStream = new ByteArrayOutputStream();
			EXIF_REWRITER.updateExifMetadataLossless(bytesWithIptc.toByteArray(), exifFileStream, exifOutputSet);

			return exifFileStream.toByteArray();
		} catch (final ImageReadException | ImageWriteException | IOException e) {
			throw new RuntimeException("Failed to add metadata to image", e);
		}
	}

	protected abstract String getScreenshotSelector(POST favorite);

	protected void waitForPageToLoad(final Page page, final POST favorite) {
		page.waitForLoadState(LoadState.NETWORKIDLE);
	}

	protected void onNavigateToPage(final Page page, final POST favorite) {
	}

	protected URI getPageUrl(final POST favorite) {
		return favorite.getUrl();
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

	private File getOrCreateFirstNonFullSubdirectory() {
		while (subdirectoryId == 0 || subdirectoryChildCount >= NixplayUploader.MAX_PHOTOS_PER_PLAYLIST) {
			subdirectoryId++;
			subdirectory = new File(screenshotsDirectory, String.valueOf(subdirectoryId));
			subdirectory.mkdirs();
			subdirectoryChildCount = countScreenshotsInDirectory(subdirectory);
		}
		return subdirectory;
	}

	protected final static String readResourceFileAsString(final String filePath) {
		try {
			try (InputStream inputStream = Main.class.getResource(filePath).openStream();
			    ByteArrayOutputStream result = new ByteArrayOutputStream()) {

				final byte[] buffer = new byte[1024];
				for (int length; (length = inputStream.read(buffer)) != -1;) {
					result.write(buffer, 0, length);
				}
				return result.toString(StandardCharsets.UTF_8);
			}
		} catch (final IOException e) {
			throw new RuntimeException(e);
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

	private static int countScreenshotsInDirectory(final File subdirectory) {
		return subdirectory.list(SCREENSHOT_FILE_FILTER).length;
	}

	private static final class ScreenshotFileFilter implements FilenameFilter {
		@Override
		public boolean accept(final File dir, final String name) {
			final String extension = splitFileBaseNameAndExtension(name)[1];
			return "jpg".equalsIgnoreCase(extension) || "jpeg".equalsIgnoreCase(extension);
		}
	}

}
