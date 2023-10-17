package com.aldaviva.microblog_favorites.services.mastodon;

import com.aldaviva.microblog_favorites.http.BearerAuthenticationFilter;
import com.aldaviva.microblog_favorites.services.mastodon.MastodonSchema.FavoritesListResponse;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.core.Response;
import java.net.URI;

public class MastodonClient {

	private final URI mastodonBaseUri;
	private final Client httpClient;
	private final BearerAuthenticationFilter authFilter = new BearerAuthenticationFilter();

	public MastodonClient(final URI mastodonBaseUri, final Client httpClient) {
		this.mastodonBaseUri = mastodonBaseUri;
		this.httpClient = httpClient;
	}

	protected WebTarget target() {
		return target(httpClient.target(mastodonBaseUri).path("api/v1/"));
	}

	protected WebTarget target(final URI uri) {
		return target(httpClient.target(uri));
	}

	private WebTarget target(final WebTarget target) {
		return target.register(authFilter);
	}

	public String signIn(final MastodonCredentials credentials) {
		authFilter.setAccessToken(credentials.getUserAccessToken());
		return null;
	}

	public void signOut() {
		authFilter.setAccessToken(null);
	}

	/**
	 * Returns a page of posts that the authenticated user has favorited (you can't view other users' favorites).
	 * @param limit Maximum number of posts to return in a single page, capped at 40
	 * @param nextPage To fetch the first page, pass <code>null</code>. To fetch the following page, pass the {@link FavoritesListResponse#nextPage} from the previous response.
	 * @return One page of posts that the user favorited. If the returned page has another one after it, its URI will be returned in the {@link FavoritesListResponse#nextPage}, otherwise if this is the last page, that will be <code>null</code>.
	 */
	public FavoritesListResponse listFavorites(final int limit, final URI nextPage) {
		final WebTarget target = nextPage != null
		    ? target(nextPage)
		    : target()
		        .path("favourites")
		        .queryParam("limit", limit);

		final Response response = target
		    .request()
		    .get(Response.class);

		final FavoritesListResponse result = response.readEntity(FavoritesListResponse.class);

		final Link nextLink = response.getLink("next");
		if (nextLink != null) {
			result.nextPage = nextLink.getUri();
		}

		response.close();
		return result;
	}
}
