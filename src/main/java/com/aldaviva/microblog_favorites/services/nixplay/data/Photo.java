package com.aldaviva.twitter_favorites.services.nixplay.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Photo {
	public String originalUrl;
	public int orientation;
	public String caption;
	public String url;
	public boolean rotation_allowed;
	public String filename;
	public String sortDate;
	public long albumId;
	public String microThumbnailUrl;
	public String s3filename;
	public String previewUrl;
	public boolean published;
	@JsonProperty("source_id") public String sourceId;
	public int rotation;
	public String thumbnailUrl;
	public long id;
	/**
	 * Lowercase hexadecimal MD5 hash of image bytes
	 */
	public String md5;

	public static final class Envelope {
		public List<Photo> photos;
	}
}
