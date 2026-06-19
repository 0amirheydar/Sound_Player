package com.example.soundplayer;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> {

    private List<AudioFile> originalList;
    private List<AudioFile> filteredList;
    private OnItemClickListener listener;
    private int highlightedIndex = -1;

    public interface OnItemClickListener {
        void onItemClick(AudioFile audioFile, int originalIndex);
    }

    public AudioAdapter(List<AudioFile> audioList, OnItemClickListener listener) {
        this.originalList = new ArrayList<>(audioList);
        this.filteredList = new ArrayList<>(audioList);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_audio, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AudioFile audio = filteredList.get(position);
        holder.tvTitle.setText(audio.getTitle());
        holder.tvArtist.setText(audio.getArtist());
        holder.tvDuration.setText(formatDuration(audio.getDuration()));

        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(16f);
        drawable.setColor(Color.TRANSPARENT);

        if (position == highlightedIndex) {
            drawable.setStroke(4, Color.parseColor("#FF6200EE"));
        } else {
            drawable.setStroke(1, Color.parseColor("#33FFFFFF"));
        }

        holder.itemView.setBackground(drawable);

        holder.itemView.setOnClickListener(v -> {
            int originalIndex = originalList.indexOf(audio);
            listener.onItemClick(audio, originalIndex);
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    public void filter(String query) {
        filteredList.clear();
        if (query == null || query.isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (AudioFile audio : originalList) {
                if (audio.getTitle().toLowerCase().contains(lowerQuery) ||
                        audio.getArtist().toLowerCase().contains(lowerQuery)) {
                    filteredList.add(audio);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void sort(int mode) {
        switch (mode) {
            case 0:
                originalList.sort((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
                break;
            case 1:
                originalList.sort((a, b) -> a.getArtist().compareToIgnoreCase(b.getArtist()));
                break;
            case 2:
                originalList.sort(Comparator.comparingLong(AudioFile::getDuration));
                break;
        }
        filter(null);
    }

    public void setHighlightedIndex(int index) {
        if (index != highlightedIndex) {
            int old = highlightedIndex;
            highlightedIndex = index;
            if (old != -1 && old < filteredList.size()) {
                notifyItemChanged(old);
            }
            if (highlightedIndex != -1 && highlightedIndex < filteredList.size()) {
                notifyItemChanged(highlightedIndex);
            }
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvArtist, tvDuration;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            tvDuration = itemView.findViewById(R.id.tvDuration);
        }
    }

    private String formatDuration(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / 1000) / 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}