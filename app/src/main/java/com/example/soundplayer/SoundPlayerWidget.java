package com.example.soundplayer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class SoundPlayerWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

            Intent intent = new Intent(context, PlaybackService.class);
            intent.setAction("TOGGLE");
            PendingIntent pendingIntent = PendingIntent.getService(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widgetPlayPause, pendingIntent);

            if (PlaybackService.isServicePlaying) {
                views.setImageViewResource(R.id.widgetPlayPause, android.R.drawable.ic_media_pause);
            } else {
                views.setImageViewResource(R.id.widgetPlayPause, android.R.drawable.ic_media_play);
            }

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}