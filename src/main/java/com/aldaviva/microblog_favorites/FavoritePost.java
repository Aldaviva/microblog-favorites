package com.aldaviva.microblog_favorites;

import java.net.URI;
import java.time.Instant;

public class FavoritePost {

	private String id;
	private String authorName;
	private String authorHandle;
	private Instant date;
	private String body;
	private URI url;

	public String getPostTypeNoun(final boolean plural) {
		return plural ? "posts" : "post";
	}

	public String getId() {
		return id;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public String getAuthorName() {
		return authorName;
	}

	public void setAuthorName(final String authorName) {
		this.authorName = authorName;
	}

	public String getAuthorHandle() {
		return authorHandle;
	}

	public void setAuthorHandle(final String authorHandle) {
		this.authorHandle = authorHandle;
	}

	public Instant getDate() {
		return date;
	}

	public void setDate(final Instant date) {
		this.date = date;
	}

	public String getBody() {
		return body;
	}

	public void setBody(final String body) {
		this.body = body;
	}

	public URI getUrl() {
		return url;
	}

	public void setUrl(final URI url) {
		this.url = url;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((authorHandle == null) ? 0 : authorHandle.hashCode());
		result = prime * result + ((authorName == null) ? 0 : authorName.hashCode());
		result = prime * result + ((body == null) ? 0 : body.hashCode());
		result = prime * result + ((date == null) ? 0 : date.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((url == null) ? 0 : url.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final FavoritePost other = (FavoritePost) obj;
		if (authorHandle == null) {
			if (other.authorHandle != null) {
				return false;
			}
		} else if (!authorHandle.equals(other.authorHandle)) {
			return false;
		}
		if (authorName == null) {
			if (other.authorName != null) {
				return false;
			}
		} else if (!authorName.equals(other.authorName)) {
			return false;
		}
		if (body == null) {
			if (other.body != null) {
				return false;
			}
		} else if (!body.equals(other.body)) {
			return false;
		}
		if (date == null) {
			if (other.date != null) {
				return false;
			}
		} else if (!date.equals(other.date)) {
			return false;
		}
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		if (url == null) {
			if (other.url != null) {
				return false;
			}
		} else if (!url.equals(other.url)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return String.format("FavoritePost [id=%s, authorName=%s, authorHandle=%s, date=%s, body=%s, url=%s]", id, authorName, authorHandle, date, body, url);
	}

}
