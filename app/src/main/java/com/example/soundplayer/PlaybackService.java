package com.example.soundplayer;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

public class PlaybackService extends Service {

    private static final String CHANNEL_ID = "music_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREFS_NAME = "sound_player_prefs";
    private static final String KEY_LAST_URI = "last_uri";
    private static final String KEY_LAST_POSITION = "last_position";

    public static boolean isServicePlaying = false;
    public static long currentPosition = 0;
    public static long duration = 0;
    public static int repeatMode = Player.REPEAT_MODE_OFF;

    private ExoPlayer player;
    private boolean isPlaying = false;
    private Handler mainHandler;
    private Runnable updatePositionTask;
    private Handler sleepHandler;
    private Runnable sleepTimerRunnable;
    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        mainHandler = new Handler(Looper.getMainLooper());
        sleepHandler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                .build(),
                        true)
                .build();

        repeatMode = prefs.getInt("repeat_mode", Player.REPEAT_MODE_OFF);
        player.setRepeatMode(repeatMode);

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean playing) {
                isPlaying = playing;
                isServicePlaying = playing;
                updateNotification();
            }
        });

        updatePositionTask = new Runnable() {
            @Override
            public void run() {
                if (player.getCurrentMediaItem() != null) {
                    currentPosition = player.getCurrentPosition();
                    duration = player.getDuration();
                }
                mainHandler.postDelayed(this, 200);
            }
        };
        mainHandler.post(updatePositionTask);

        String lastUri = prefs.getString(KEY_LAST_URI, null);
        long lastPos = prefs.getLong(KEY_LAST_POSITION, 0);
        if (lastUri != null) {
            Uri uri = Uri.parse(lastUri);
            player.setMediaItem(MediaItem.fromUri(uri));
            player.prepare();
            player.seekTo(lastPos);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        if (intent != null) {
            String action = intent.getAction();
            if ("PLAY".equals(action)) {
                String uriString = intent.getStringExtra("URI");
                if (uriString != null) {
                    Uri uri = Uri.parse(uriString);
                    player.setMediaItem(MediaItem.fromUri(uri));
                    player.prepare();
                    player.play();
                    prefs.edit().putString(KEY_LAST_URI, uriString).apply();
                }
            } else if ("PAUSE".equals(action)) {
                player.pause();
            } else if ("TOGGLE".equals(action)) {
                if (player.isPlaying()) {
                    player.pause();
                } else if (player.getCurrentMediaItem() != null) {
                    player.play();
                }
            } else if ("SEEK".equals(action)) {
                long position = intent.getLongExtra("POSITION", 0);
                player.seekTo(position);
            } else if ("SLEEP_TIMER".equals(action)) {
                long durationMs = intent.getLongExtra("DURATION", 0);
                if (sleepTimerRunnable != null) {
                    sleepHandler.removeCallbacks(sleepTimerRunnable);
                }
                if (durationMs > 0) {
                    sleepTimerRunnable = () -> {
                        player.pause();
                        sleepTimerRunnable = null;
                    };
                    sleepHandler.postDelayed(sleepTimerRunnable, durationMs);
                }
            } else if ("SET_REPEAT".equals(action)) {
                int mode = intent.getIntExtra("MODE", Player.REPEAT_MODE_OFF);
                player.setRepeatMode(mode);
                repeatMode = mode;
                prefs.edit().putInt("repeat_mode", mode).apply();
            }
        }

        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (player.getCurrentMediaItem() != null) {
            prefs.edit().putLong(KEY_LAST_POSITION, player.getCurrentPosition()).apply();
        }
        mainHandler.removeCallbacks(updatePositionTask);
        sleepHandler.removeCallbacks(sleepTimerRunnable);
        player.release();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent toggleIntent = new Intent(this, PlaybackService.class);
        toggleIntent.setAction("TOGGLE");
        PendingIntent togglePending = PendingIntent.getService(
                this, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Sound Player")
                .setContentText(isPlaying ? "Now playing" : "Paused")
                .setContentIntent(openPending)
                .addAction(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        isPlaying ? "Pause" : "Play", togglePending)
                .setOngoing(true);

        return builder.build();
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }
}