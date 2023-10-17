package com.aldaviva.microblog_favorites.services.nixplay.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class Album {

	@JsonProperty("album_type") public String albumType;
	@JsonProperty("photo_count") public int photoCount;
	@JsonProperty("pending_album_id") public Long pendingAlbumId;
	@JsonProperty("allow_reorder") public boolean allowReorder;
	@JsonProperty("title") public String title;
	@JsonProperty("is_updated") public boolean isUpdated;
	@JsonProperty("cover_urls") public List<String> coverUrls;
	@JsonProperty("dateCreated") public String dateCreated;
	@JsonProperty("allow_upload") public boolean allowUpload;
	@JsonProperty("allow_delete") public boolean allowDelete;
	@JsonProperty("thumbs") public List<Map<String, Object>> thumbs;
	@JsonProperty("published") public boolean published;
	@JsonProperty("id") public long id;
	@JsonProperty("allow_delete_pictures") public boolean allowDeletePictures;
	@JsonProperty("picture_storage_type") public String pictureStorageType;
	@JsonProperty("email") public String email;

}
