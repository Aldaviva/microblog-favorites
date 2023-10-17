package com.aldaviva.twitter_favorites.services.twitter;

import com.aldaviva.twitter_favorites.FavoritePost;

public class FavoriteTweet extends FavoritePost {

	private String embeddedUrl;
	private boolean isProtected;

	public String getEmbeddedUrl() {
		return embeddedUrl;
	}

	public void setEmbeddedUrl(final String embeddedUrl) {
		this.embeddedUrl = embeddedUrl;
	}

	public boolean isProtected() {
		return isProtected;
	}

	public void setProtected(final boolean isProtected) {
		this.isProtected = isProtected;
	}

	@Override
	public String getPostTypeNoun(final boolean plural) {
		return plural ? "tweets" : "tweet";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((embeddedUrl == null) ? 0 : embeddedUrl.hashCode());
		result = prime * result + (isProtected ? 1231 : 1237);
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final FavoriteTweet other = (FavoriteTweet) obj;
		if (embeddedUrl == null) {
			if (other.embeddedUrl != null) {
				return false;
			}
		} else if (!embeddedUrl.equals(other.embeddedUrl)) {
			return false;
		}
		if (isProtected != other.isProtected) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return String.format("FavoriteTweet [embeddedUrl=%s, isProtected=%s, toString()=%s]", embeddedUrl, isProtected, super.toString());
	}

}
