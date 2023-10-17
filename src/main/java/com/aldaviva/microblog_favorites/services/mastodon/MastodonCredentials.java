package com.aldaviva.twitter_favorites.services.mastodon;

import java.net.URI;

public class MastodonCredentials {

	private final URI serverBaseUri;
	private final String clientKey;
	private final String clientSecret;
	private final String userAccessToken;

	public MastodonCredentials(final URI serverBaseUri, final String clientKey, final String clientSecret, final String userAccessToken) {
		this.serverBaseUri = serverBaseUri;
		this.clientKey = clientKey;
		this.clientSecret = clientSecret;
		this.userAccessToken = userAccessToken;
	}

	public URI getServerBaseUri() {
		return serverBaseUri;
	}

	public String getClientKey() {
		return clientKey;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public String getUserAccessToken() {
		return userAccessToken;
	}

}
