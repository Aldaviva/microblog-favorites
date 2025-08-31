package com.aldaviva.microblog_favorites.services.bluesky;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.*;

import com.aldaviva.microblog_favorites.ConfigurationFactory;
import com.aldaviva.microblog_favorites.FavoritePost;
import com.aldaviva.microblog_favorites.FavoritesDownloader;
import com.aldaviva.microblog_favorites.services.bluesky.BlueskySchema.FavoritesListResponse;
import com.aldaviva.microblog_favorites.services.bluesky.BlueskySchema.FeedItem;
import com.aldaviva.microblog_favorites.services.bluesky.BlueskySchema.Post;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.AddStyleTagOptions;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.UriBuilder;
import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.glassfish.jersey.uri.UriComponent;

public class BlueskyDownloader extends FavoritesDownloader<FavoritePost> {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(BlueskyDownloader.class);

	private static final UriBuilder POST_PAGE_URI = UriBuilder.fromUri("https://bsky.app/profile/{author}/post/{recordkey}");
	private static final String POST_STYLESHEET = readResourceFileAsString("/styles/bluesky-post.css");

	private final BlueskyClient bluesky;

	private String handle;

	public BlueskyDownloader(final Client httpClient) {
		bluesky = new BlueskyClient(httpClient);
	}

	@Override
	public String getServiceName() {
		return "Bluesky";
	}

	@Override
	public void signIn(final Page page) {
		if (handle != null) {
			bluesky.signOut();
		}

		final PasswordAuthentication appCredentials = ConfigurationFactory.getBlueskyCredentials(true);
		LOGGER.debug("Signing in with username {}", appCredentials.getUserName());
		handle = bluesky.signIn(appCredentials);

		page.navigate("https://bsky.app/settings/appearance");
		LOGGER.info("Loading Bluesky apperance settings page...");

		final Locator signInButton1 = page.getByTestId("signInButton");
		final Locator darkColorModeButton = page.locator("div[aria-label='Color mode'] div[aria-label='Dark']");

		assertThat(signInButton1.or(darkColorModeButton).first()).isVisible();
		if (signInButton1.isVisible()) {
			LOGGER.debug("Bluesky signed us out, clicking sign in button");
			signInButton1.click();

			final PasswordAuthentication userCredentials = ConfigurationFactory.getBlueskyCredentials(false);

			final Locator otherAccount = page.getByTestId("chooseAddAccountBtn");
			final Locator usernameField = page.getByTestId("loginUsernameInput");
			assertThat(otherAccount.or(usernameField).first()).isVisible();
			if (otherAccount.isVisible()) {
				LOGGER.debug("Bluesky remembered previous username, clicking Other Account anyway");
				otherAccount.click();
			}

			usernameField.fill(userCredentials.getUserName());
			page.getByTestId("loginPasswordInput").fill(new String(userCredentials.getPassword()));
			page.getByTestId("loginNextButton").click();
			LOGGER.debug("Submitted sign in form with username and password");
		}

		darkColorModeButton.click();
		page.click("div[aria-label='Dark theme'] div[aria-label='Dark']");
	}

	@Override
	protected List<FavoritePost> listAllFavorites() {
		final List<FavoritePost> favorites = new ArrayList<>();

		String cursor = null;
		List<FeedItem> posts;
		do {
			final FavoritesListResponse favoritesResponseBody = bluesky.listFavorites(handle, 100, cursor);
			posts = favoritesResponseBody.feed;

			for (final FeedItem feedItem : posts) {
				final Post post = feedItem.post;
				final FavoritePost favorite = new FavoritePost();
				favorites.add(favorite);

				favorite.setAuthorName(Optional.ofNullable(post.author.displayName).orElse(post.author.handle));
				favorite.setAuthorHandle(post.author.handle);
				favorite.setDate(post.record.createdAt);
				favorite.setBody(post.record.text);
				favorite.setId(UriComponent.decodePath(post.uri, true).get(1).getPath()); // https://atproto.com/specs/record-key
				favorite.setUrl(POST_PAGE_URI.build(post.author.did, favorite.getId()));
			}

			cursor = favoritesResponseBody.cursor;
		} while (!posts.isEmpty());

		return favorites;
	}

	@Override
	protected void onNavigateToPage(final Page page, final FavoritePost favorite) {
		super.onNavigateToPage(page, favorite);
		page.addStyleTag(new AddStyleTagOptions().setContent(POST_STYLESHEET));
	}

	@Override
	protected String getScreenshotSelector(final FavoritePost favorite) {
		return "div[data-testid ^= 'postThreadItem-by-']:has(*[aria-label='Likes on this post'])";
	}

	@Override
	public void close() throws Exception {
		bluesky.signOut();
		handle = null;
	}

}
