package com.aldaviva.twitter_favorites.nixplay;

import com.aldaviva.twitter_favorites.nixplay.data.Album;
import com.aldaviva.twitter_favorites.nixplay.data.FrameStatus;
import com.aldaviva.twitter_favorites.nixplay.data.Photo;
import com.aldaviva.twitter_favorites.nixplay.data.Playlist;

import com.fasterxml.jackson.core.util.JacksonFeature;
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
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;

public class JerseyNixplayClient implements NixplayClient {

	private static final String SESSION_ID_COOKIE_NAME = "prod.session.id";
	private static final URI BASE_URI = URI.create("https://api.nixplay.com/");
	private static final MediaType PHOTO_CONTENT_TYPE = MediaType.valueOf("image/jpeg");

	private final Client client;

	private String sessionId;

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
		clientConfig.register(JacksonFeature.class);
		clientConfig.property(ClientProperties.FOLLOW_REDIRECTS, false);
		return clientConfig;
	}

	private void resetSessionState() {
		synchronized (albumToUploadTokenCache) {
			sessionId = null;
			albumToUploadTokenCache.clear();
		}
	}

	protected WebTarget target() {
		return client.target(BASE_URI);
	}

	protected class SessionIdFilter implements ClientRequestFilter {

		@Override
		public void filter(final ClientRequestContext requestContext) throws IOException {
			if (sessionId != null) {
				final ClientRequest request = (ClientRequest) requestContext;
				request.cookie(new Cookie(SESSION_ID_COOKIE_NAME, sessionId));
			}
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

		final Form tokenForm = new Form();
		tokenForm.param("token", token);
		tokenForm.param("startPairing", "false");
		tokenForm.param("redirectPath", "");

		try (Response authenticationResponse = target()
		    .path("v2/www-login-redirect/")
		    .request()
		    .post(Entity.form(tokenForm))) {

			final NewCookie sessionIdCookie = authenticationResponse.getCookies().get(SESSION_ID_COOKIE_NAME);
			final String sessionId = sessionIdCookie.getValue();
			this.sessionId = sessionId;
			return sessionId;
		}
	}

	@Override
	public void signOut() {
		if (sessionId == null) {
			return;
		}

		try (Response response = target()
		    .path("sign_out/")
		    .request()
		    .get()) {

			resetSessionState();
		}
	}

	@Override
	public List<Album> listAlbums() {
		return target()
		    .path("v2/albums/web/json/")
		    .request()
		    .get(new GenericType<List<Album>>() {
		    });
	}

	@Override
	public List<Playlist> listPlaylists() {
		return target()
		    .path("v3/playlists")
		    .request()
		    .get(new GenericType<List<Playlist>>() {
		    });
	}

	@Override
	public Album createAlbum(final String name) {
		return target()
		    .path("album/create/json")
		    .request()
		    .post(Entity.form(new Form("name", name)), Album.class);
	}

	@Override
	public Playlist createPlaylist(final String name) {
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

		try (Response response = target()
		    .path("/v3/playlists/{playlistId}/items")
		    .resolveTemplate("playlistId", playlist.id)
		    .request()
		    .post(Entity.json(requestBody))) {
		}
	}

	@Override
	public FrameStatus.Envelope listFrameStatuses() {
		return target()
		    .path("v3/frame/online-status")
		    .request()
		    .get(FrameStatus.Envelope.class);
	}

	@Override
	public void enablePlaylistOnFrame(final Playlist playlist, final FrameStatus frame) {
		final Map<String, List<Map<String, Long>>> requestBody = Collections.singletonMap("add",
		    Collections.singletonList(Collections.singletonMap("playlistId", playlist.id)));

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

		synchronized (albumToUploadTokenCache) {
			uploadToken = albumToUploadTokenCache.get(destinationAlbum.id);
			if (uploadToken == null) {
				final Form uploadTokenForm = new Form();
				uploadTokenForm.param("albumId", String.valueOf(destinationAlbum.id));
				uploadTokenForm.param("total", "1"); //nominally the number of photos to upload, but not enforced

				final ObjectNode uploadTokenResponse = target().path("v3/upload/receivers")
				    .request()
				    .post(Entity.form(uploadTokenForm), ObjectNode.class);

				uploadToken = uploadTokenResponse.path("token").asText();
				albumToUploadTokenCache.put(destinationAlbum.id, uploadToken);
			}
		}

		final Form uploadIdForm = new Form();
		uploadIdForm.param("uploadToken", uploadToken);
		uploadIdForm.param("albumId", String.valueOf(destinationAlbum.id));
		uploadIdForm.param("fileName", filename);
		uploadIdForm.param("fileType", PHOTO_CONTENT_TYPE.toString());
		uploadIdForm.param("fileSize", String.valueOf(photoBytes));

		final JsonNode uploadIdResponse = target()
		    .path("v3/photo/upload/")
		    .request()
		    .post(Entity.form(uploadIdForm), ObjectNode.class)
		    .get("data");

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

		} catch (final IOException e) {
			//close
		}

		/*
		 * When you upload a photo to an album, Nixplay doesn't tell you which photo you just uploaded (none of the IDs or S3 URLs match between the upload
		 * response and the album contents).
		 * However, photos are prepended to albums.
		 * Therefore, to work around this limitation, this function fetches the 15 most recently added photos in the album and matches one by filename.
		 * This is subject to race conditions where a lot of files are uploaded in parallel, or deleted too soon, or filenames collide.
		 */
		return getPhotosFromAlbum(destinationAlbum, 1, 15).stream().filter(photo -> filename.equals(photo.filename)).findFirst().orElse(null);
	}

	@Override
	public List<Photo> getPhotosFromAlbum(final Album album, final int page, final int limit) {
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
