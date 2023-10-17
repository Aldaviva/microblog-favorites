package com.aldaviva.microblog_favorites.services.bluesky;

import com.aldaviva.microblog_favorites.ConfigurationFactory;
import com.aldaviva.microblog_favorites.FavoriteDownloader;
import com.aldaviva.microblog_favorites.FavoritePost;
import com.aldaviva.microblog_favorites.services.bluesky.BlueskySchema.FavoritesListResponse;
import com.aldaviva.microblog_favorites.services.bluesky.BlueskySchema.FeedItem;
import com.aldaviva.microblog_favorites.services.bluesky.BlueskySchema.Post;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.WaitForSelectorOptions;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.UriBuilder;
import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.List;
import org.glassfish.jersey.uri.UriComponent;

public class BlueskyDownloader extends FavoriteDownloader<FavoritePost> {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(BlueskyDownloader.class);

	private static final UriBuilder POST_PAGE_URI = UriBuilder.fromUri("https://bsky.app/profile/{author}/post/{recordkey}");

	private final BlueskyClient bluesky;

	private String handle;

	public BlueskyDownloader(final Client httpClient) {
		bluesky = new BlueskyClient(httpClient);
	}

	@Override
	protected String getServiceName() {
		return "Bluesky";
	}

	@Override
	public void signIn(final Page page) {
		if (handle != null) {
			bluesky.signOut();
		}

		final PasswordAuthentication credentials = ConfigurationFactory.getBlueskyCredentials();
		LOGGER.debug("Signing in with username {}", credentials.getUserName());
		handle = bluesky.signIn(credentials);

		page.navigate("https://bsky.app");
		LOGGER.info("Waiting for user to log in to Bluesky...");
		page.waitForSelector("input[data-testid='searchTextInput']", new WaitForSelectorOptions().setTimeout(ONE_DAY_IN_MILLIS)); // give the user enough time to log in
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

				favorite.setAuthorName(post.author.displayName);
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
	protected String getScreenshotSelector(final FavoritePost favorite) {
		return "div[data-testid ^= 'postThreadItem-by-']";
	}

	@Override
	public void close() throws Exception {
		bluesky.signOut();
		handle = null;
	}

}
