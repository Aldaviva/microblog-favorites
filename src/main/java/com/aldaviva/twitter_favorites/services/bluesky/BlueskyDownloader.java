package com.aldaviva.twitter_favorites.services.bluesky;

import com.aldaviva.twitter_favorites.ConfigurationFactory;
import com.aldaviva.twitter_favorites.FavoriteDownloader;
import com.aldaviva.twitter_favorites.FavoritePost;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.WaitForSelectorOptions;
import jakarta.ws.rs.client.Client;
import java.net.PasswordAuthentication;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class BlueskyDownloader extends FavoriteDownloader<FavoritePost> {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(BlueskyDownloader.class);

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

		final PasswordAuthentication credentials = ConfigurationFactory.createBlueskyCredentials();
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
		JsonNode posts;
		do {
			final ObjectNode favoritesResponseBody = bluesky.listFavorites(handle, 100, cursor);

			posts = favoritesResponseBody.get("feed");

			for (final JsonNode feedItem : posts) {
				final JsonNode post = feedItem.get("post");

				final FavoritePost favorite = new FavoritePost();
				favorites.add(favorite);

				favorite.setAuthorName(post.path("author").path("displayName").asText());
				favorite.setAuthorHandle(post.path("author").path("handle").asText());
				favorite.setDate(Instant.parse(post.path("record").path("createdAt").asText()));
				favorite.setBody(post.path("record").path("text").asText());
				favorite.setId(new LinkedList<>(Arrays.asList(post.path("uri").asText().split("/"))).getLast()); // https://atproto.com/specs/record-key
				favorite.setUrl("https://bsky.app/profile/" + post.path("author").path("did").asText() + "/post/" + favorite.getId());
			}

			cursor = favoritesResponseBody.get("cursor").asText();
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
