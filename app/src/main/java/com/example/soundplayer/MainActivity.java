package com.example.soundplayer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.media3.common.Player;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.DynamicColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private ImageButton btnPlayPause, btnNext, btnPrevious, btnShuffle, btnSort, btnRepeat, btnSleepTimer;
    private SeekBar seekBar;
    private TextView tvTime;
    private RecyclerView recyclerView;
    private AudioAdapter adapter;
    private List<AudioFile> audioList = new ArrayList<>();
    private boolean isPlaying = false;
    private boolean permissionsRequested = false;
    private int currentIndex = -1;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable syncStatus;

    private ActivityResultLauncher<String[]> requestPermissionsLauncher;
    private SearchView searchView;

    private int repeatState = 0;
    private static final int[] REPEAT_MODES = {Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL, Player.REPEAT_MODE_ONE};
    private static final int[] REPEAT_ICONS = {
            R.drawable.ic_repeat_off,
            R.drawable.ic_repeat_all,
            R.drawable.ic_repeat_one
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        searchView = findViewById(R.id.searchView);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnSort = findViewById(R.id.btnSort);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnSleepTimer = findViewById(R.id.btnSleepTimer);
        seekBar = findViewById(R.id.seekBar);
        tvTime = findViewById(R.id.tvTime);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        if (toolbar.getBackground() instanceof ColorDrawable) {
            int color = ((ColorDrawable) toolbar.getBackground()).getColor();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setStatusBarColor(color);
            }
        }

        btnShuffle.setImageResource(R.drawable.ic_shuffle);
        btnRepeat.setImageResource(REPEAT_ICONS[repeatState]);

        requestPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                (Map<String, Boolean> permissions) -> {
                    boolean audioGranted = Boolean.TRUE.equals(permissions.get(Manifest.permission.READ_MEDIA_AUDIO));
                    boolean notifGranted = Boolean.TRUE.equals(permissions.get(Manifest.permission.POST_NOTIFICATIONS));
                    if (audioGranted) {
                        loadAudioFiles();
                    } else {
                        Toast.makeText(this, "Need audio permission to list files", Toast.LENGTH_SHORT).show();
                    }
                    if (!notifGranted) {
                        Toast.makeText(this, "Notification permission is required for background playback", Toast.LENGTH_SHORT).show();
                    }
                });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (adapter != null) adapter.filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (adapter != null) adapter.filter(newText);
                return true;
            }
        });

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnNext.setOnClickListener(v -> playNext());
        btnPrevious.setOnClickListener(v -> playPrevious());
        btnShuffle.setOnClickListener(v -> shufflePlay());
        btnSort.setOnClickListener(v -> showSortDialog());
        btnRepeat.setOnClickListener(v -> cycleRepeatMode());
        btnSleepTimer.setOnClickListener(v -> showSleepTimerDialog());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    Intent seekIntent = new Intent(MainActivity.this, PlaybackService.class);
                    seekIntent.setAction("SEEK");
                    seekIntent.putExtra("POSITION", (long) progress);
                    startService(seekIntent);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        syncStatus = new Runnable() {
            @Override
            public void run() {
                isPlaying = PlaybackService.isServicePlaying;
                updatePlayPauseIcon();

                long pos = PlaybackService.currentPosition;
                long dur = PlaybackService.duration;
                if (dur > 0) {
                    seekBar.setMax((int) dur);
                    seekBar.setProgress((int) pos);
                    tvTime.setText(formatTime(pos) + " / " + formatTime(dur));
                }

                handler.postDelayed(this, 200);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(syncStatus);
        if (!permissionsRequested) {
            permissionsRequested = true;
            requestNeededPermissions();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(syncStatus);
    }

    private void requestNeededPermissions() {
        List<String> perms = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.READ_MEDIA_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!perms.isEmpty()) {
            requestPermissionsLauncher.launch(perms.toArray(new String[0]));
        } else {
            loadAudioFiles();
        }
    }

    private void loadAudioFiles() {
        audioList.clear();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION
        };
        try (Cursor cursor = getContentResolver().query(collection, projection, null, null,
                MediaStore.Audio.Media.TITLE + " ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                    String artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
                    long duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
                    Uri contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            String.valueOf(id));
                    if (duration > 1000) {
                        audioList.add(new AudioFile(id, title, artist, duration, contentUri));
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error reading audio files: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

        adapter = new AudioAdapter(audioList, (audioFile, originalIndex) -> {
            currentIndex = originalIndex;
            playAudio(audioFile);
            adapter.setHighlightedIndex(originalIndex);
        });
        recyclerView.setAdapter(adapter);
        Toast.makeText(this, "Loaded " + audioList.size() + " audio files", Toast.LENGTH_SHORT).show();
    }

    private void playAudio(AudioFile audioFile) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction("PLAY");
        intent.putExtra("URI", audioFile.getUri().toString());
        startService(intent);
    }

    private void togglePlayPause() {
        Intent toggleIntent = new Intent(this, PlaybackService.class);
        toggleIntent.setAction("TOGGLE");
        startService(toggleIntent);
    }

    private void playNext() {
        if (!audioList.isEmpty() && currentIndex != -1) {
            currentIndex = (currentIndex + 1) % audioList.size();
            playAudio(audioList.get(currentIndex));
            adapter.setHighlightedIndex(currentIndex);
        }
    }

    private void playPrevious() {
        if (!audioList.isEmpty() && currentIndex != -1) {
            currentIndex = (currentIndex - 1 + audioList.size()) % audioList.size();
            playAudio(audioList.get(currentIndex));
            adapter.setHighlightedIndex(currentIndex);
        }
    }

    private void shufflePlay() {
        if (!audioList.isEmpty()) {
            currentIndex = new Random().nextInt(audioList.size());
            playAudio(audioList.get(currentIndex));
            adapter.setHighlightedIndex(currentIndex);
        }
    }

    private void cycleRepeatMode() {
        repeatState = (repeatState + 1) % 3;
        btnRepeat.setImageResource(REPEAT_ICONS[repeatState]);
        Intent intent = new Intent(MainActivity.this, PlaybackService.class);
        intent.setAction("SET_REPEAT");
        intent.putExtra("MODE", REPEAT_MODES[repeatState]);
        startService(intent);
        String modeText = repeatState == 0 ? "Off" : repeatState == 1 ? "All" : "One";
        Toast.makeText(this, "Repeat: " + modeText, Toast.LENGTH_SHORT).show();
    }

    private void showSortDialog() {
        String[] options = {"Title", "Artist", "Duration"};
        new AlertDialog.Builder(this)
                .setTitle("Sort by")
                .setItems(options, (dialog, which) -> {
                    if (adapter != null) {
                        adapter.sort(which);
                        Toast.makeText(this, "Sorted by " + options[which], Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showSleepTimerDialog() {
        final String[] options = {"15 minutes", "30 minutes", "60 minutes", "Cancel"};
        final long[] values = {15 * 60 * 1000L, 30 * 60 * 1000L, 60 * 60 * 1000L, 0L};

        new AlertDialog.Builder(this)
                .setTitle("Sleep Timer")
                .setItems(options, (dialog, which) -> {
                    long duration = values[which];
                    Intent intent = new Intent(MainActivity.this, PlaybackService.class);
                    intent.setAction("SLEEP_TIMER");
                    intent.putExtra("DURATION", duration);
                    startService(intent);
                    if (duration > 0) {
                        Toast.makeText(MainActivity.this,
                                "Sleep timer set for " + options[which], Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Sleep timer cancelled", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void updatePlayPauseIcon() {
        if (isPlaying) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    private String formatTime(long millis) {
        int sec = (int) (millis / 1000) % 60;
        int min = (int) (millis / 1000) / 60;
        return String.format("%02d:%02d", min, sec);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(syncStatus);
    }
}