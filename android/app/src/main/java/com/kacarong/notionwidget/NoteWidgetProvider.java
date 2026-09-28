package com.kacarong.notionwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NoteWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.kacarong.notionwidget.ACTION_REFRESH";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        refreshAsync(ctx);
        for (int id : ids) render(ctx, mgr, id);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            refreshAsync(ctx);
        }
    }

    /** 백그라운드에서 Notion을 새로 읽고, 끝나면 위젯 다시 그림 */
    private static void refreshAsync(Context ctx) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            NotionClient.refreshCache(app);
            notifyAll(app);
        }).start();
    }

    public static void notifyAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, NoteWidgetProvider.class));
        for (int id : ids) render(ctx, mgr, id);
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
    }

    static void render(Context ctx, AppWidgetManager mgr, int widgetId) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_note);

        // 배경(테마 + 투명도)
        boolean light = "light".equals(Prefs.theme(ctx));
        int base = light ? Color.rgb(255, 255, 255) : Color.rgb(18, 21, 28);
        int alpha = Math.max(0, Math.min(255, Prefs.bgAlpha(ctx)));
        rv.setInt(R.id.widget_root, "setBackgroundColor",
                Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)));

        int fg = light ? Color.rgb(29, 35, 46) : Color.rgb(232, 237, 245);
        int muted = light ? Color.rgb(105, 117, 138) : Color.rgb(154, 167, 184);

        String title = Prefs.showTitle(ctx) ? Prefs.cacheTitle(ctx) : "";
        if (title == null || title.isEmpty()) title = "Notion 메모";
        rv.setTextViewText(R.id.widget_title, title);
        rv.setTextColor(R.id.widget_title, fg);
        rv.setTextViewTextSize(R.id.widget_title, android.util.TypedValue.COMPLEX_UNIT_SP,
                Prefs.fontSize(ctx) + 1);

        String err = Prefs.lastError(ctx);
        String status;
        if (err != null && !err.isEmpty()) {
            status = "오류: " + err;
            rv.setTextColor(R.id.widget_status, Color.rgb(255, 107, 122));
        } else {
            long at = Prefs.lastAt(ctx);
            status = at > 0 ? new SimpleDateFormat("HH:mm", Locale.KOREA).format(new Date(at)) + " 갱신" : "불러오는 중…";
            rv.setTextColor(R.id.widget_status, muted);
        }
        rv.setTextViewText(R.id.widget_status, status);

        // 리스트 어댑터
        Intent svc = new Intent(ctx, NoteWidgetService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        rv.setRemoteAdapter(R.id.widget_list, svc);
        rv.setEmptyView(R.id.widget_list, R.id.widget_empty);
        rv.setTextColor(R.id.widget_empty, muted);

        // 탭 → 즉시 새로고침
        Intent refresh = new Intent(ctx, NoteWidgetProvider.class).setAction(ACTION_REFRESH);
        rv.setOnClickPendingIntent(R.id.widget_refresh,
                PendingIntent.getBroadcast(ctx, 0, refresh,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // 제목 탭 → 설정 열기
        Intent settings = new Intent(ctx, SettingsActivity.class);
        rv.setOnClickPendingIntent(R.id.widget_title,
                PendingIntent.getActivity(ctx, 1, settings, PendingIntent.FLAG_IMMUTABLE));

        mgr.updateAppWidget(widgetId, rv);
    }
}
