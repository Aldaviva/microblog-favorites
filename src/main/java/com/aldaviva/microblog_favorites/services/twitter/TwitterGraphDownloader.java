package com.aldaviva.microblog_favorites.services.twitter;

import com.aldaviva.microblog_favorites.ConfigurationFactory;
import com.aldaviva.microblog_favorites.FavoritesDownloader;
import com.aldaviva.microblog_favorites.http.JacksonConfig.CustomObjectMapperProvider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.AddStyleTagOptions;
import com.microsoft.playwright.Page.WaitForURLOptions;
import com.microsoft.playwright.options.LoadState;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TwitterGraphDownloader extends FavoritesDownloader<FavoriteTweet> {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(TwitterGraphDownloader.class);

	private static final int MAX_TWEETS_PER_PAGE = 88; // 20 is default from webapp
	private static final int MAX_TWEETS_TO_LOAD = MAX_TWEETS_PER_PAGE * 3;
	private static final String CUSTOM_EMBEDDED_STYLE = readResourceFileAsString("/styles/embedded-tweet.css");
	private static final String CUSTOM_PROTECTED_STYLE = readResourceFileAsString("/styles/protected-tweet.css");
	private static final UriBuilder POST_PAGE_URI = UriBuilder.fromUri("https://x.com/{author}/status/{tweetid}");
	private static final UriBuilder POST_EMBEDDED_PAGE_URI = UriBuilder
	    .fromUri("https://platform.twitter.com/embed/Tweet.html?dnt=false&embedId=twitter-widget-0&frame=false&hideCard=false&hideThread=false&id={tweetid}&lang=en&theme=dark");
	private static final TwitterGraphCredentials CREDENTIALS = ConfigurationFactory.getTwitterCredentials();
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss xx yyyy"); // Thu Jan 16 19:15:41 +0000 2025

	private final Client httpClient;
	private final ObjectMapper objectMapper = CustomObjectMapperProvider.objectMapper;

	public TwitterGraphDownloader(final Client httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public String getServiceName() {
		return "Twitter";
	}

	@Override
	public void signIn(final Page page) {
		page.navigate("https://x.com");
		LOGGER.info("Waiting for user to log in to Twitter...");
		page.waitForURL("https://x.com/home*", new WaitForURLOptions().setTimeout(ONE_HOUR_IN_MILLIS)); // give the user enough time to log in

		// Set page theme to Lights Out (black background) for protected tweets
		page.navigate("https://x.com/i/display"); // keyboard shortcuts broke, so use URL routing instead
		page.click("input[type=radio][aria-label='Lights out']"); //use click instead of check, because check tries to read the checked attribute after writing, but twitter removes/detaches the element on click
		page.keyboard().press("Escape");
	}

	@Override
	protected List<FavoriteTweet> listAllFavorites() {
		final List<FavoriteTweet> favorites = new ArrayList<>();

		final Map<String, Object> requestVariables = new HashMap<>();
		requestVariables.put("userId", CREDENTIALS.getUserId());
		requestVariables.put("count", MAX_TWEETS_PER_PAGE);
		requestVariables.put("includePromotedContent", false);
		requestVariables.put("withBirdwatchNotes", false);
		requestVariables.put("withClientEventToken", false);
		requestVariables.put("withV2Timeline", true);
		requestVariables.put("withVoice", true);

		final String requestFeatures = "{\"profile_label_improvements_pcf_label_in_post_enabled\":true,\"rweb_tipjar_consumption_enabled\":true,\"responsive_web_graphql_exclude_directive_enabled\":true,\"verified_phone_label_enabled\":false,\"creator_subscriptions_tweet_preview_api_enabled\":true,\"responsive_web_graphql_timeline_navigation_enabled\":true,\"responsive_web_graphql_skip_user_profile_image_extensions_enabled\":false,\"premium_content_api_read_enabled\":false,\"communities_web_enable_tweet_community_results_fetch\":true,\"c9s_tweet_anatomy_moderator_badge_enabled\":true,\"responsive_web_grok_analyze_button_fetch_trends_enabled\":false,\"responsive_web_grok_analyze_post_followups_enabled\":true,\"responsive_web_jetfuel_frame\":false,\"responsive_web_grok_share_attachment_enabled\":true,\"articles_preview_enabled\":true,\"responsive_web_edit_tweet_api_enabled\":true,\"graphql_is_translatable_rweb_tweet_is_translatable_enabled\":true,\"view_counts_everywhere_api_enabled\":true,\"longform_notetweets_consumption_enabled\":true,\"responsive_web_twitter_article_tweet_consumption_enabled\":true,\"tweet_awards_web_tipping_enabled\":false,\"creator_subscriptions_quote_tweet_preview_enabled\":false,\"freedom_of_speech_not_reach_fetch_enabled\":true,\"standardized_nudges_misinfo\":true,\"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\":true,\"rweb_video_timestamps_enabled\":true,\"longform_notetweets_rich_text_read_enabled\":true,\"longform_notetweets_inline_media_enabled\":true,\"responsive_web_grok_image_annotation_enabled\":true,\"responsive_web_enhance_cards_enabled\":false}";
		final Map<String, Object> requestFieldToggles = Collections.singletonMap("withArticlePlainText", false);

		try {
			String pageCursor = null;
			do {
				if (pageCursor == null) {
					requestVariables.remove("cursor");
				} else {
					requestVariables.put("cursor", pageCursor);
				}
				pageCursor = null;

				final ObjectNode graphResponse = httpClient.target("https://x.com/i/api/graphql/ejIZbvsO6hPdJQHetmIF1g/Likes")
				    .queryParam("variables", "{variables}")
				    .resolveTemplate("variables", objectMapper.writeValueAsString(requestVariables))
				    .queryParam("features", "{features}")
				    .resolveTemplate("features", requestFeatures)
				    .queryParam("fieldToggles", "{fieldToggles}")
				    .resolveTemplate("fieldToggles", objectMapper.writeValueAsString(requestFieldToggles))
				    .request()
				    .cookie("_twitter_sess", CREDENTIALS.getSessionId())
				    .cookie("auth_token", CREDENTIALS.getAuthToken())
				    .cookie("ct0", CREDENTIALS.getCt0())
				    .header(HttpHeaders.AUTHORIZATION, "Bearer " + CREDENTIALS.getOauthToken())
				    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36")
				    .header(HttpHeaders.CONTENT_TYPE, "application/json") // this is wrong because the request has no body, but the webapp sends it so we will too
				    .header("x-csrf-token", CREDENTIALS.getCsrfToken())
				    .get(ObjectNode.class);

				final JsonNode entries = graphResponse.path("data").path("user").path("result").path("timeline_v2").path("timeline").path("instructions").path(0).path("entries");
				for (final JsonNode entry : entries) {
					final String entryType = entry.path("content").path("entryType").textValue();
					if ("TimelineTimelineItem".equals(entryType)) {
						final FavoriteTweet favorite = new FavoriteTweet();
						favorites.add(favorite);

						JsonNode graphTweet = entry.path("content").path("itemContent").path("tweet_results").path("result");
						if (graphTweet.path("__typename").textValue().equals("TweetWithVisibilityResults")) {
							graphTweet = graphTweet.path("tweet");
						}

						final JsonNode author = graphTweet.path("core").path("user_results").path("result").path("legacy");

						favorite.setId(graphTweet.path("rest_id").textValue());
						favorite.setAuthorHandle(author.path("screen_name").textValue());
						favorite.setAuthorName(author.path("name").textValue());
						favorite.setProtected(author.path("protected").asBoolean(false) || author.path("possibly_sensitive").asBoolean());
						favorite.setDate(OffsetDateTime.parse(graphTweet.path("legacy").path("created_at").textValue(), DATE_FORMAT).toInstant());
						favorite.setBody(graphTweet.path("legacy").path("full_text").asText());

						favorite.setUrl(POST_PAGE_URI.build(favorite.getAuthorHandle(), favorite.getId()));
						favorite.setEmbeddedUrl(POST_EMBEDDED_PAGE_URI.build(favorite.getId()));
					} else if ("TimelineTimelineCursor".equals(entryType) && "Bottom".equals(entry.path("content").path("cursorType").textValue())) {
						pageCursor = entry.path("content").path("value").textValue();
					}
				}
			} while (pageCursor != null && favorites.size() < MAX_TWEETS_TO_LOAD);

			return favorites;

		} catch (final JsonProcessingException e) {
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
		    ? "div[style *= 'position: absolute;']:has(div > div > article a[aria-label *= '·'] time)"
		    : "//article/..";
	}

	@Override
	public void close() throws Exception {
	}
}
