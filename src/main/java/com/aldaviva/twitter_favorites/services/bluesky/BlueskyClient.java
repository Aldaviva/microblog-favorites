package com.aldaviva.twitter_favorites.services.bluesky;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BlueskyClient {

	private static final URI API_BASE = URI.create("https://bsky.social/xrpc/");

	private final Client httpClient;
	private final BearerAuthenticationFilter authFilter = new BearerAuthenticationFilter();

	public BlueskyClient(final Client httpClient) {
		this.httpClient = httpClient;
	}

	protected WebTarget target(final String namespaceId) {
		return httpClient.target(API_BASE).register(authFilter);
	}

	/**
	 * Sign in with a handle/email address and password/app password.
	 * After this method returns, subsequent requests made by this object instance will automatically use the received access token.
	 * To sign out, call {@link #signOut()}.
	 * @param credentials The username can be a Bluesky handle or email address. The password can be the account password or an <a href="https://bsky.app/settings/app-passwords">app password</a>.
	 * @return the user handle
	 * @see https://github.com/bluesky-social/atproto/blob/main/lexicons/com/atproto/server/createSession.json
	 */
	public String signIn(final PasswordAuthentication credentials) {
		final Map<String, String> authenticationRequestBody = new HashMap<>();
		authenticationRequestBody.put("identifier", credentials.getUserName());
		authenticationRequestBody.put("password", new String(credentials.getPassword()));

		final Map<String, String> authenticationResponseBody = httpClient.target(API_BASE)
		    .path("com.atproto.server.createSession")
		    .request()
		    .post(Entity.json(authenticationRequestBody), new GenericType<Map<String, String>>() {
		    });

		this.authFilter.setAccessToken(authenticationResponseBody.get("accessJwt"));
		return authenticationResponseBody.get("handle");
	}

	/**
	 * Sign out of the current session for this object instance. Subsequent requests will not send authentication until you call {@link #signIn(PasswordAuthentication)}.
	 */
	public void signOut() {
		target("com.atproto.server.deleteSession")
		    .request()
		    .post(null)
		    .close();

		this.authFilter.setAccessToken(null);
	}

	/**
	 * Return the user's favorite posts.
	 * @param user handle or DID of the user who did the liking
	 * @param limit maximum number of favorites to return at a time, in the range [1, 100]
	 * @param cursor pagination token returned by prior requests, or <c>null</c> if this is the first request
	 * @return JSON output
	 * @see https://atproto.com/lexicons/app-bsky#appbskyfeed
	 * @see https://github.com/bluesky-social/atproto/blob/main/lexicons/app/bsky/feed/getLikes.json
	 */
	public ObjectNode listFavorites(final String user, final int limit, final String cursor) {
		return target("app.bsky.feed.getActorLikes")
		    .queryParam("actor", user)
		    .queryParam("limit", limit)
		    .queryParam("cursor", cursor)
		    .request()
		    .get(ObjectNode.class);
	}

	protected static class BearerAuthenticationFilter implements ClientRequestFilter {

		private String accessToken;

		public void setAccessToken(final String accessToken) {
			this.accessToken = accessToken;
		}

		@Override
		public void filter(final ClientRequestContext requestContext) throws IOException {
			if (accessToken != null) {
				requestContext.getHeaders().putIfAbsent(HttpHeaders.AUTHORIZATION, Arrays.asList("Bearer " + accessToken));
			}
		}

	}
}
