package com.aldaviva.microblog_favorites.services.bluesky;

import jakarta.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.time.Instant;
import java.util.List;

public final class BlueskySchema {

	private BlueskySchema() {
	}

	@XmlRootElement
	public static final class FavoritesListResponse {
		public List<FeedItem> feed;
		public String cursor;
	}

	public static final class FeedItem {
		public Post post;
	}

	public static final class Post {
		public URI uri;
		public String cid;
		public Author author;
		public Record record;
	}

	public static final class Author {
		public String did;
		public String handle;
		public String displayName;
		public URI avatar;
	}

	public static final class Record {
		public String text;
		public Instant createdAt;
	}
}
