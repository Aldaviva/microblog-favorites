package com.aldaviva.twitter_favorites.services.nixplay;

import com.aldaviva.twitter_favorites.services.nixplay.data.Album;
import com.aldaviva.twitter_favorites.services.nixplay.data.FrameStatus;
import com.aldaviva.twitter_favorites.services.nixplay.data.Photo;
import com.aldaviva.twitter_favorites.services.nixplay.data.Playlist;

import java.io.File;
import java.io.InputStream;
import java.net.PasswordAuthentication;
import java.util.List;

public interface NixplayClient extends AutoCloseable {

	/**
	 * @return session ID
	 */
	String signIn(PasswordAuthentication credentials);

	/**
	 * @return session ID
	 */
	String signIn(String sessionId);

	void signOut();

	List<Album> listAlbums();

	List<Playlist> listPlaylists();

	Playlist createPlaylist(String name);

	Album createAlbum(String name);

	void enablePlaylistOnFrame(final Playlist playlist, final FrameStatus frame);

	FrameStatus.Envelope listFrameStatuses();

	Photo uploadPhoto(byte[] photoBytes, String filename, Album destinationAlbum);

	Photo uploadPhoto(File photoFile, Album destinationAlbum);

	Photo uploadPhoto(InputStream photoStream, String filename, long length, Album destinationAlbum);

	/**
	 * List photos in an album.
	 * @param album The album in which to find photos.
	 * @param page The page number to get photos from. The smallest possible value is {@code 1}, which contains the photos <b><i>most recently</i></b> added to the album.
	 * @param limit The maximum number of photos to return. There is no maximum value for this parameter.
	 */
	List<Photo> getPhotosFromAlbum(Album album, int page, int limit);

	void appendPhotosToPlaylist(Playlist playlist, Photo... photos);

}