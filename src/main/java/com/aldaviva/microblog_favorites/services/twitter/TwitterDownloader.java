package com.aldaviva.microblog_favorites.services.twitter;

import com.aldaviva.microblog_favorites.ConfigurationFactory;
import com.aldaviva.microblog_favorites.FavoritesDownloader;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.AddStyleTagOptions;
import com.microsoft.playwright.Page.WaitForURLOptions;
import com.microsoft.playwright.options.LoadState;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

public class TwitterDownloader extends FavoritesDownloader<FavoriteTweet> {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(TwitterDownloader.class);

	private static final int MAX_TWEETS_PER_PAGE = 200;
	private static final String CUSTOM_EMBEDDED_STYLE = readResourceFileAsString("/styles/embedded-tweet.css");
	private static final String CUSTOM_PROTECTED_STYLE = readResourceFileAsString("/styles/protected-tweet.css");
	private static final UriBuilder POST_PAGE_URI = UriBuilder.fromUri("https://twitter.com/{author}/status/{tweetid}");
	private static final UriBuilder POST_EMBEDDED_PAGE_URI = UriBuilder
	    .fromUri("https://platform.twitter.com/embed/Tweet.html?dnt=false&embedId=twitter-widget-0&frame=false&hideCard=false&hideThread=false&id={tweetid}&lang=en&theme=dark");

	private final Twitter twitterClient;

	public TwitterDownloader() {
		final ConfigurationBuilder twitterConfiguration = ConfigurationFactory.createTwitterConfigurationBuilder();
		twitterConfiguration.setTrimUserEnabled(false);
		twitterConfiguration.setTweetModeExtended(true);
		twitterClient = new TwitterFactory(twitterConfiguration.build()).getInstance();
	}

	@Override
	protected String getServiceName() {
		return "Twitter";
	}

	@Override
	public void signIn(final Page page) {
		page.navigate("https://twitter.com");
		LOGGER.info("Waiting for user to log in to Twitter...");
		page.waitForURL("https://twitter.com/home", new WaitForURLOptions().setTimeout(ONE_DAY_IN_MILLIS)); // give the user enough time to log in

		// Set page theme to Lights Out (black background) for protected tweets
		page.navigate("https://twitter.com/i/display"); // keyboard shortcuts broke, so use URL routing instead
		page.click("input[type=radio][aria-label='Lights out']"); //use click instead of check, because check tries to read the checked attribute after writing, but twitter removes/detaches the element on click
		page.keyboard().press("Escape");
	}

	@Override
	protected List<FavoriteTweet> listAllFavorites() {
		try {
			final List<FavoriteTweet> favorites = new ArrayList<>();

			for (int pageNumber = 1;; pageNumber++) {
				ResponseList<Status> page;
				page = twitterClient.favorites().getFavorites(new Paging(pageNumber, MAX_TWEETS_PER_PAGE));
				if (page.isEmpty()) {
					// last page finished, no more favorites
					break;
				}

				for (final Status tweet : page) {
					final FavoriteTweet favorite = new FavoriteTweet();

					favorite.setId(String.valueOf(tweet.getId()));
					favorite.setAuthorName(tweet.getUser().getName());
					favorite.setAuthorHandle(tweet.getUser().getScreenName());
					favorite.setDate(tweet.getCreatedAt().toInstant());
					favorite.setBody(tweet.getText());
					favorite.setUrl(POST_PAGE_URI.build(favorite.getAuthorHandle(), favorite.getId()));
					favorite.setEmbeddedUrl(POST_EMBEDDED_PAGE_URI.build(favorite.getId())); // embedded page refuses to load protected tweets
					favorite.setProtected(tweet.getUser().isProtected());

					if (!previouslySavedPostIds.contains(favorite.getId())) {
						favorites.add(favorite);
					}
				}
			}

			return favorites;
		} catch (final TwitterException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void waitForPageToLoad(final Page page, final FavoriteTweet favorite) {
		page.waitForLoadState(favorite.isProtected() ? LoadState.LOAD : LoadState.NETWORKIDLE); //network idle takes like 60 seconds to happen for logged-in twitter webapp pages, maybe an open websocket?

		if (favorite.isProtected()) {
			page.waitForTimeout(2000);
		}
	}

	@Override
	protected void onNavigateToPage(final Page page, final FavoriteTweet favorite) {
		super.onNavigateToPage(page, favorite);
		page.addStyleTag(new AddStyleTagOptions().setContent(favorite.isProtected() ? CUSTOM_PROTECTED_STYLE : CUSTOM_EMBEDDED_STYLE));
	}

	@Override
	protected URI getPageUrl(final FavoriteTweet favorite) {
		return favorite.isProtected() ? favorite.getUrl() : favorite.getEmbeddedUrl();
	}

	@Override
	protected String getScreenshotSelector(final FavoriteTweet favorite) {
		return favorite.isProtected()
		    ? "div[style *= 'position: absolute;']:has(div > div > article div[aria-label=Bookmark])"
		    : "//article/..";
	}

	@Override
	public void close() throws Exception {
	}

}
