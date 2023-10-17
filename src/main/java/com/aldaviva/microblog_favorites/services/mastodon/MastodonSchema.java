package com.aldaviva.twitter_favorites.services.mastodon;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.time.Instant;
import java.util.List;

public final class MastodonSchema {

	private MastodonSchema() {
	}

	@XmlRootElement
	public static final class FavoritesListResponse {
		public URI nextPage;
		public List<Status> statuses;
	}

	public static final class Status {
		public String id;
		@JsonProperty("created_at") public Instant createdAt;
		public URI uri;
		public URI url;
		public String content;
		public Account author;
	}

	public static final class Account {
		public String id;
		public String username;
		public String acct;
		@JsonProperty("display_name") public String displayName;
		public URI uri;
		public URI url;
		public URI avatar;
		public URI header;
	}
}
