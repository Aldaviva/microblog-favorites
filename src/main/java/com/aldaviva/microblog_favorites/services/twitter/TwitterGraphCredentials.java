package com.aldaviva.microblog_favorites.services.twitter;

public class TwitterGraphCredentials {

	private final long userId;
	private final String oauthToken;
	private final String csrfToken;
	private final String sessionId;
	private final String authToken;
	private final String ct0;

	public TwitterGraphCredentials(final long userId, final String oauthToken, final String csrfToken, final String sessionId, final String authToken, final String ct0) {
		this.userId = userId;
		this.oauthToken = oauthToken;
		this.csrfToken = csrfToken;
		this.sessionId = sessionId;
		this.authToken = authToken;
		this.ct0 = ct0;
	}

	public long getUserId() {
		return userId;
	}

	/**
	 * Goes in the Authorization header (prefix with "Bearer ")
	 */
	public String getOauthToken() {
		return oauthToken;
	}

	/**
	 * Goes in the x-csrf-token header
	 */
	public String getCsrfToken() {
		return csrfToken;
	}

	/**
	 * Goes in the _twitter_sess cookie
	 */
	public String getSessionId() {
		return sessionId;
	}

	/**
	 * Goes in the auth_token cookie
	 */
	public String getAuthToken() {
		return authToken;
	}

	/**
	 * Goes in the ct0 cookie
	 */
	public String getCt0() {
		return ct0;
	}

}
