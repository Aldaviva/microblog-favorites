package com.aldaviva.microblog_favorites.services.nixplay;

import com.aldaviva.microblog_favorites.services.nixplay.data.Album;
import com.aldaviva.microblog_favorites.services.nixplay.data.FrameStatus;
import com.aldaviva.microblog_favorites.services.nixplay.data.Photo;
import com.aldaviva.microblog_favorites.services.nixplay.data.Playlist;

import jakarta.ws.rs.client.Client;
import java.net.PasswordAuthentication;
import java.util.List;

public class NixplayUploader implements AutoCloseable {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(NixplayUploader.class);

	public static final int MAX_PHOTOS_PER_PLAYLIST = 2000; //Nixplay playlist limit

	private final NixplayClient nixplayClient;
	private List<Album> allAlbums;
	private List<Playlist> allPlaylists;
	private List<FrameStatus> allFrames;

	public NixplayUploader(final Client httpClient) {
		nixplayClient = new JerseyNixplayClient(httpClient, false);
	}

	public void signIn(final PasswordAuthentication credentials) {
		nixplayClient.signIn(credentials);
		allAlbums = nixplayClient.listAlbums();
		allPlaylists = nixplayClient.listPlaylists();
		allFrames = nixplayClient.listFrameStatuses().frames;
	}

	public void uploadToAlbumAndPlaylist(final byte[] image, final String filename, final Album album, final Playlist playlist) {
		final Photo nixplayPhoto = nixplayClient.uploadPhoto(image, filename, album);
		nixplayClient.appendPhotosToPlaylist(playlist, nixplayPhoto);
		LOGGER.debug("Uploaded " + filename + " to Nixplay album and playlist " + album.title);
		album.photoCount++;
	}

	public Album getOrCreateAlbum(final String albumNamePrefix) {
		int maxAlbumNumber = 0;
		Album maxAlbum = null;
		for (final Album album : allAlbums) {
			if (album.title.startsWith(albumNamePrefix)) {
				try {
					final int albumNumber = Integer.parseInt(album.title.substring(albumNamePrefix.length()));
					maxAlbumNumber = Math.max(albumNumber, maxAlbumNumber);
					if (albumNumber == maxAlbumNumber) {
						maxAlbum = album;
					}
				} catch (final NumberFormatException e) {
					// continue to next album
				}
			}
		}

		if (maxAlbum != null && maxAlbum.photoCount < MAX_PHOTOS_PER_PLAYLIST) {
			return maxAlbum;
		} else {
			final Album newAlbum = nixplayClient.createAlbum(albumNamePrefix + (maxAlbumNumber + 1));
			allAlbums.add(newAlbum);
			LOGGER.info("Created Nixplay album " + newAlbum.title);
			return newAlbum;
		}
	}

	public Playlist getOrCreatePlaylist(final Album album) {
		final String albumTitle = album.title;
		return allPlaylists.stream().filter(playlist -> albumTitle.equals(playlist.name)).findAny()
		    .orElseGet(() -> {
			    final Playlist playlist = nixplayClient.createPlaylist(albumTitle);
			    allPlaylists.add(playlist);
			    LOGGER.info("Created Nixplay playlist " + albumTitle);
			    for (final FrameStatus frame : allFrames) {
				    nixplayClient.enablePlaylistOnFrame(playlist, frame);
				    LOGGER.info("Enabled Nixplay playlist on frame " + frame.framePk);
			    }
			    return playlist;
		    });
	}

	@Override
	public void close() throws Exception {
		nixplayClient.close();
	}

}
