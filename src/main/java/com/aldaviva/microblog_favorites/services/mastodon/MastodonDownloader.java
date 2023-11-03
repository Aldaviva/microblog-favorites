package com.aldaviva.microblog_favorites.services.mastodon;

import com.aldaviva.microblog_favorites.ConfigurationFactory;
import com.aldaviva.microblog_favorites.FavoritePost;
import com.aldaviva.microblog_favorites.FavoritesDownloader;
import com.aldaviva.microblog_favorites.services.mastodon.MastodonSchema.FavoritesListResponse;
import com.aldaviva.microblog_favorites.services.mastodon.MastodonSchema.Status;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.WaitForURLOptions;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class MastodonDownloader extends FavoritesDownloader<FavoritePost> {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MastodonDownloader.class);

	private static final URI BASE_URI = ConfigurationFactory.getMastodonCredentials().getServerBaseUri();
	private static final UriBuilder POST_PAGE_URI = UriBuilder.fromUri(BASE_URI).path("@{author}/{postid}");

	private final MastodonClient mastodon;

	public MastodonDownloader(final Client httpClient) {
		mastodon = new MastodonClient(BASE_URI, httpClient);
	}

	@Override
	public String getServiceName() {
		return "Mastodon";
	}

	@Override
	public void signIn(final Page page) {
		mastodon.signIn(ConfigurationFactory.getMastodonCredentials());

		page.navigate(BASE_URI.toString());
		LOGGER.info("Waiting for user to log in to Mastodon...");
		page.waitForURL(UriBuilder.fromUri(BASE_URI).path("home").build().toString(), new WaitForURLOptions().setTimeout(ONE_HOUR_IN_MILLIS)); // give the user enough time to log in
	}

	@Override
	protected List<FavoritePost> listAllFavorites() {
		final List<FavoritePost> favorites = new ArrayList<>();

		URI nextPage = null;
		do {
			final FavoritesListResponse page = mastodon.listFavorites(40, nextPage);

			for (final Status status : page.statuses) {
				final FavoritePost favorite = new FavoritePost();
				favorites.add(favorite);

				favorite.setAuthorHandle(status.account.acct);
				favorite.setAuthorName(status.account.displayName);
				favorite.setBody(status.content);
				favorite.setDate(status.createdAt);
				favorite.setId(status.id);
				favorite.setUrl(POST_PAGE_URI.build(status.account.acct, status.id));
			}

			nextPage = page.nextPage;
		} while (nextPage != null);

		return favorites;
	}

	@Override
	protected String getScreenshotSelector(final FavoritePost favorite) {
		return "div.detailed-status";
	}

	@Override
	public void close() throws Exception {
	}

}
