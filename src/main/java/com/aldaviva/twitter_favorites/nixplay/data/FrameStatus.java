package com.aldaviva.twitter_favorites.nixplay.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class FrameStatus {

	@JsonProperty("compatibility") public boolean compatibility;
	@JsonProperty("connected") public boolean connected;
	@JsonProperty("lastSeen") public long lastSeen;
	@JsonProperty("lastConnected") public long lastConnected;
	@JsonProperty("frameId") public String frameId;
	@JsonProperty("framePk") public long framePk;

	public static final class Envelope {
		@JsonProperty("isInitialize") public boolean isInitialized;
		@JsonProperty("frames") public List<FrameStatus> frames;
	}
}
