package com.aldaviva.twitter_favorites.nixplay;

import com.aldaviva.twitter_favorites.nixplay.data.Album;
import com.aldaviva.twitter_favorites.nixplay.data.FrameStatus;
import com.aldaviva.twitter_favorites.nixplay.data.Photo;
import com.aldaviva.twitter_favorites.nixplay.data.Playlist;

import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.net.PasswordAuthentication;
import java.util.List;

public interface NixplayClient extends Closeable, AutoCloseable {

	String signIn(PasswordAuthentication credentials);

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

	List<Photo> getPhotosFromAlbum(Album album, int page, int limit);

	void appendPhotosToPlaylist(Playlist playlist, Photo... photos);

}