package com.example.soundplayer;

import android.net.Uri;

public class AudioFile {
    private final long id;
    private final String title;
    private final String artist;
    private final long duration;
    private final Uri uri;

    public AudioFile(long id, String title, String artist, long duration, Uri uri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.duration = duration;
        this.uri = uri;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public long getDuration() { return duration; }
    public Uri getUri() { return uri; }
}