package com.aldaviva.microblog_favorites.services.nixplay.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class Playlist {

	@JsonProperty("id") public long id;
	@JsonProperty("name") public String name;
	@JsonProperty("cover_urls") public String coverUrls;
	@JsonProperty("config_file") public String configFile;
	@JsonProperty("picture_count") public int pictureCount;
	@JsonProperty("upload_key") public String uploadKey;
	@JsonProperty("version") public int version;
	@JsonProperty("duration") public int duration;
	@JsonProperty("transition") public String transition;
	@JsonProperty("converted") public boolean converted;
	@JsonProperty("on_frames") public List<Object> onFrames;
	@JsonProperty("on_scheduled_frames") public List<Object> onScheduledFrames;
	@JsonProperty("type") public String type;
	@JsonProperty("sharing") public Map<String, Object> sharing;
	@JsonProperty("last_updated_date") public String lastUpdatedDate;
	@JsonProperty("created_date") public String createdDate;
	@JsonProperty("member_last_updated_date") public String memberLastUpdatedDate;

}
