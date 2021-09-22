package com.aldaviva.twitter_favorites.nixplay;

import com.aldaviva.twitter_favorites.http.CustomHttpUrlConnectorProvider;
import com.aldaviva.twitter_favorites.http.JacksonConfig.CustomJacksonFeature;
import com.aldaviva.twitter_favorites.http.JacksonConfig.CustomObjectMapperProvider;
import com.aldaviva.twitter_favorites.nixplay.data.Album;
import com.aldaviva.twitter_favorites.nixplay.data.FrameStatus;
import com.aldaviva.twitter_favorites.nixplay.data.Photo;
import com.aldaviva.twitter_favorites.nixplay.data.Playlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;

public class JerseyNixplayClient implements NixplayClient {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(JerseyNixplayClient.class);

	private static final URI API_BASE_URI = URI.create("https://api.nixplay.com/");
	private static final URI UPLOAD_MONITOR_BASE_URI = URI.create("https://upload-monitor.nixplay.com/");
	private static final String SESSION_ID_COOKIE_NAME = "prod.session.id";
	private static final String CSRF_TOKEN_COOKIE_NAME = "prod.csrftoken";
	private static final String CSRF_TOKEN_HEADER_NAME = "X-CSRFToken";
	private static final MediaType PHOTO_CONTENT_TYPE = MediaType.valueOf("image/jpeg");

	private final Client client;

	private String sessionId;
	private String csrfToken;

	private final Map<Long, String> albumToUploadTokenCache = new HashMap<>();
	private final boolean closeClient;

	public JerseyNixplayClient() {
		this(ClientBuilder.newClient(createDefaultClientConfiguration()), true);
	}

	public JerseyNixplayClient(final Client client, final boolean closeClient) {
		this.client = client
		    .register(new SessionIdFilter())
		    .register(MultiPartFeature.class);
		this.closeClient = closeClient;
	}

	private static final Configuration createDefaultClientConfiguration() {
		final ClientConfig clientConfig = new ClientConfig();
		clientConfig.register(CustomObjectMapperProvider.class);
		clientConfig.register(CustomJacksonFeature.class);
		clientConfig.property(ClientProperties.CONNECT_TIMEOUT, 5 * 1000);
		clientConfig.property(ClientProperties.READ_TIMEOUT, 30 * 1000);
		clientConfig.property(ClientProperties.FOLLOW_REDIRECTS, false);
		clientConfig.connectorProvider(new CustomHttpUrlConnectorProvider());

		final Logger httpLogger = Logger.getLogger("http");
		httpLogger.setLevel(Level.ALL);
		clientConfig.register(new LoggingFeature(httpLogger, LoggingFeature.Verbosity.PAYLOAD_ANY));

		return clientConfig;
	}

	private void resetSessionState() {
		synchronized (albumToUploadTokenCache) {
			sessionId = null;
			csrfToken = null;
			albumToUploadTokenCache.clear();
		}
	}

	protected WebTarget target() {
		return client.target(API_BASE_URI);
	}

	protected class SessionIdFilter implements ClientRequestFilter {

		@Override
		public void filter(final ClientRequestContext requestContext) throws IOException {
			final ClientRequest clientRequest = (ClientRequest) requestContext;

			if (sessionId != null) {
				clientRequest.cookie(new Cookie(SESSION_ID_COOKIE_NAME, sessionId));
			}

			// Don't sent CSRF cookie with every request, since it causes the album list method to redirect somewhere else
		}
	}

	@Override
	public String signIn(final String sessionId) {
		resetSessionState();
		this.sessionId = sessionId;
		return sessionId;
	}

	@Override
	public String signIn(final PasswordAuthentication credentials) {
		resetSessionState();

		LOGGER.debug("Signing in with username {}", credentials.getUserName());

		final Form authenticationForm = new Form();
		authenticationForm.param("email", credentials.getUserName());
		authenticationForm.param("password", String.valueOf(credentials.getPassword()));
		authenticationForm.param("signup_pair", "no");
		authenticationForm.param("login_remember", "false");

		final ObjectNode tokenResponse = target()
		    .path("www-login/")
		    .request()
		    .post(Entity.form(authenticationForm), ObjectNode.class);

		final String token = tokenResponse.path("token").asText();

		LOGGER.debug("Signed in, got auth token {}", token);

		final Form tokenForm = new Form();
		tokenForm.param("token", token);
		tokenForm.param("startPairing", "false");
		tokenForm.param("redirectPath", "");

		try (Response authenticationResponse = target()
		    .path("v2/www-login-redirect/")
		    .request()
		    .post(Entity.form(tokenForm))) {

			final Map<String, NewCookie> responseCookies = authenticationResponse.getCookies();
			csrfToken = responseCookies.get(CSRF_TOKEN_COOKIE_NAME).getValue();
			final String sessionId = responseCookies.get(SESSION_ID_COOKIE_NAME).getValue();
			this.sessionId = sessionId;
			LOGGER.debug("Exchanged auth token for session ID {}", sessionId);
			return sessionId;
		}
	}

	@Override
	public void signOut() {
		if (sessionId != null) {
			try (Response response = target()
			    .path("sign_out/")
			    .request()
			    .get()) {
			}
		}

		resetSessionState();
	}

	@Override
	public List<Album> listAlbums() {
		LOGGER.debug("Listing albums");
		return target()
		    .path("v2/albums/web/json/")
		    .request()
		    .get(new GenericType<List<Album>>() {
		    });
	}

	@Override
	public List<Playlist> listPlaylists() {
		LOGGER.debug("Listing playlists");
		return target()
		    .path("v3/playlists")
		    .request()
		    .get(new GenericType<List<Playlist>>() {
		    });
	}

	@Override
	public Album createAlbum(final String name) {
		LOGGER.debug("Creating album {}", name);
		return target()
		    .path("album/create/json/")
		    .request()
		    .post(Entity.form(new Form("name", name)), Album.class);
	}

	@Override
	public Playlist createPlaylist(final String name) {
		LOGGER.debug("Creating playlist {}", name);
		final long id = target()
		    .path("v3/playlists")
		    .request()
		    .post(Entity.json(Collections.singletonMap("name", name)), ObjectNode.class)
		    .path("playlistId")
		    .asLong();

		return listPlaylists().stream().filter(playlist -> playlist.id == id).findAny().get();
	}

	@Override
	public void appendPhotosToPlaylist(final Playlist playlist, final Photo... photos) {
		final ObjectNode requestBody = new ObjectNode(JsonNodeFactory.instance);
		final ArrayNode items = requestBody.putArray("items");
		for (final Photo photo : photos) {
			items.addObject().put("pictureId", photo.id);
		}

		LOGGER.debug("Appending {} photos to playlist {}", photos.length, playlist.name);
		try (Response response = target()
		    .path("/v3/playlists/{playlistId}/items")
		    .resolveTemplate("playlistId", playlist.id)
		    .request()
		    .cookie(new Cookie(CSRF_TOKEN_COOKIE_NAME, csrfToken))
		    //		    .header(CSRF_TOKEN_HEADER_NAME, csrfToken)
		    .post(Entity.json(requestBody))) {
		}
	}

	@Override
	public FrameStatus.Envelope listFrameStatuses() {
		LOGGER.debug("Listing frame statuses");
		return target()
		    .path("v3/frame/online-status")
		    .request()
		    .get(FrameStatus.Envelope.class);
	}

	@Override
	public void enablePlaylistOnFrame(final Playlist playlist, final FrameStatus frame) {
		final Map<String, List<Map<String, Long>>> requestBody = Collections.singletonMap("add",
		    Collections.singletonList(Collections.singletonMap("playlistId", playlist.id)));

		LOGGER.debug("Enabling playlist {} on frame {}", playlist.name, frame.framePk);
		try (Response response = target()
		    .path("v3/shared-frames/{framePk}/playlists")
		    .resolveTemplate("framePk", frame.framePk)
		    .request()
		    .post(Entity.json(requestBody))) {
		}
	}

	@Override
	public Photo uploadPhoto(final byte[] photoBytes, final String filename, final Album destinationAlbum) {
		final BodyPart photoBodyPart = new FormDataBodyPart("file", photoBytes, PHOTO_CONTENT_TYPE);
		photoBodyPart.getHeaders().add("filename", filename);
		return uploadPhoto(photoBodyPart, photoBytes.length, destinationAlbum);
	}

	@Override
	public Photo uploadPhoto(final InputStream photoStream, final String filename, final long length, final Album destinationAlbum) {
		final BodyPart photoBodyPart = new StreamDataBodyPart("file", photoStream, filename, PHOTO_CONTENT_TYPE);
		return uploadPhoto(photoBodyPart, length, destinationAlbum);
	}

	@Override
	public Photo uploadPhoto(final File photoFile, final Album destinationAlbum) {
		final BodyPart photoBodyPart = new FileDataBodyPart("file", photoFile, PHOTO_CONTENT_TYPE);
		return uploadPhoto(photoBodyPart, photoFile.length(), destinationAlbum);
	}

	protected Photo uploadPhoto(final BodyPart photoBodyPart, final long photoBytes, final Album destinationAlbum) {
		final String filename = photoBodyPart.getHeaders().getFirst("filename");
		String uploadToken;
		LOGGER.debug("Uploading photo {} ({} bytes) to album {}", filename, photoBytes, destinationAlbum.title);

		synchronized (albumToUploadTokenCache) {
			uploadToken = albumToUploadTokenCache.get(destinationAlbum.id);
			if (uploadToken == null) {
				LOGGER.debug("Cache miss on upload token for album {}, creating new upload token", destinationAlbum.title);
				final Form uploadTokenForm = new Form();
				uploadTokenForm.param("albumId", String.valueOf(destinationAlbum.id));
				uploadTokenForm.param("total", "1"); //nominally the number of photos to upload, but not enforced

				final ObjectNode uploadTokenResponse = target().path("v3/upload/receivers")
				    .request()
				    .cookie(new Cookie(CSRF_TOKEN_COOKIE_NAME, csrfToken))
				    //				    .header(CSRF_TOKEN_HEADER_NAME, csrfToken)
				    .post(Entity.form(uploadTokenForm), ObjectNode.class);

				uploadToken = uploadTokenResponse.path("token").asText();
				albumToUploadTokenCache.put(destinationAlbum.id, uploadToken);
				LOGGER.debug("Created upload token {} for album {}", uploadToken, destinationAlbum.title);
			} else {
				LOGGER.debug("Cache hit on upload token {} for album {}", uploadToken, destinationAlbum.title);
			}
		}

		final Form uploadIdForm = new Form();
		uploadIdForm.param("uploadToken", uploadToken);
		uploadIdForm.param("albumId", String.valueOf(destinationAlbum.id));
		uploadIdForm.param("fileName", filename);
		uploadIdForm.param("fileType", PHOTO_CONTENT_TYPE.toString());
		uploadIdForm.param("fileSize", String.valueOf(photoBytes));

		LOGGER.debug("Requesting upload ID for {}", filename);
		final JsonNode uploadIdResponse = target()
		    .path("v3/photo/upload/")
		    .request()
		    .cookie(new Cookie(CSRF_TOKEN_COOKIE_NAME, csrfToken))
		    //		    .header(CSRF_TOKEN_HEADER_NAME, csrfToken)
		    .post(Entity.form(uploadIdForm), ObjectNode.class)
		    .get("data");

		final String uploadMonitorId = uploadIdResponse.path("userUploadIds").path(0).asText();

		LOGGER.debug("Received upload ID (batch upload ID={}, key={})", uploadIdResponse.path("batchUploadId").asText(), uploadIdResponse.path("key").asText());

		LOGGER.debug("Uploading {} bytes to {}", photoBytes, uploadIdResponse.path("s3UploadUrl").asText());
		try (FormDataMultiPart requestBody = new FormDataMultiPart()) {
			requestBody.field("key", uploadIdResponse.path("key").asText())
			    .field("acl", uploadIdResponse.path("acl").asText())
			    .field("content-type", uploadIdResponse.path("fileType").asText())
			    .field("x-amz-meta-batch-upload-id", uploadIdResponse.path("batchUploadId").asText())
			    .field("success_action_status", "201")
			    .field("AWSAccessKeyId", uploadIdResponse.path("AWSAccessKeyId").asText())
			    .field("Policy", uploadIdResponse.path("Policy").asText())
			    .field("Signature", uploadIdResponse.path("Signature").asText())
			    .bodyPart(photoBodyPart);

			try (Response response = client.target(uploadIdResponse.path("s3UploadUrl").asText())
			    .request()
			    .post(Entity.entity(requestBody, requestBody.getMediaType()))) {
			}

			LOGGER.debug("Uploaded {}", filename);
		} catch (final IOException e) {
			//close
		}

		LOGGER.debug("Waiting for {} to be processed by Nixplay", filename);
		//wait for photo to process so it appears in albums. this request takes about 3 seconds.
		final String uploadMonitorResponse = client.target(UPLOAD_MONITOR_BASE_URI)
		    .path("status")
		    .queryParam("id", uploadMonitorId)
		    .request()
		    .get(String.class);

		/*
		 * When you upload a photo to an album, Nixplay doesn't tell you which photo you just uploaded (none of the IDs or S3 URLs match between the upload
		 * response and the album contents).
		 * However, photos are prepended to albums.
		 * Therefore, to work around this limitation, this function fetches the 15 most recently added photos in the album and matches one by filename.
		 * This is subject to race conditions where a lot of files are uploaded in parallel, or deleted too soon, or filenames collide.
		 */
		LOGGER.debug("Finding uploaded photo in most recent photos of album {}", destinationAlbum.title);
		return getPhotosFromAlbum(destinationAlbum, 1, 15).stream().filter(photo -> filename.equals(photo.filename)).findFirst().orElse(null);
	}

	@Override
	public List<Photo> getPhotosFromAlbum(final Album album, final int page, final int limit) {
		LOGGER.debug("Listing photos in album {} (page #{}, photos per page={})", album.title, page, limit);
		return target()
		    .path("album/{albumId}/pictures/json/")
		    .resolveTemplate("albumId", album.id)
		    .queryParam("page", page)
		    .queryParam("limit", limit)
		    .request()
		    .get(Photo.Envelope.class).photos;
	}

	@Override
	public void close() {
		resetSessionState();
		if (closeClient) {
			client.close();
		}
	}
}
