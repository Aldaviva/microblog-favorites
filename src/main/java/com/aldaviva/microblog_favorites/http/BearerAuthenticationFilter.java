package com.aldaviva.microblog_favorites.http;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.util.Arrays;

public class BearerAuthenticationFilter implements ClientRequestFilter {

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