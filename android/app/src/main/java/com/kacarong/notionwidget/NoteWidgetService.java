package com.kacarong.notionwidget;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.TypedValue;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

/** 위젯 목록에 Notion 블록들을 그려주는 어댑터 */
public class NoteWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    static class Factory implements RemoteViewsService.RemoteViewsFactory {
        private final Context ctx;
        private List<NotionClient.Item> items = new ArrayList<>();

        Factory(Context ctx) { this.ctx = ctx; }

        @Override public void onCreate() { }

        @Override public void onDataSetChanged() { items = NotionClient.cachedItems(ctx); }

        @Override public void onDestroy() { items.clear(); }

        @Override public int getCount() { return items.size(); }

        @Override
        public RemoteViews getViewAt(int position) {
            NotionClient.Item it = items.get(position);
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_item);

            boolean light = "light".equals(Prefs.theme(ctx));
            int fg = light ? Color.rgb(29, 35, 46) : Color.rgb(232, 237, 245);
            int muted = light ? Color.rgb(105, 117, 138) : Color.rgb(154, 167, 184);
            int accent;
            try { accent = Color.parseColor(Prefs.accent(ctx)); } catch (Exception e) { accent = Color.rgb(79, 140, 255); }

            int base = Prefs.fontSize(ctx);
            float size = base;
            int color = fg;
            String prefix = "";
            String text = it.text == null ? "" : it.text;

            switch (it.type) {
                case "h1": size = base + 4; break;
                case "h2": size = base + 2; break;
                case "h3": size = base + 1; break;
                case "bullet": prefix = "• "; break;
                case "number": prefix = "– "; break;
                case "quote": color = muted; prefix = "| "; break;
                case "callout": prefix = (it.icon != null ? it.icon + " " : "💡 "); break;
                case "code": size = base - 1; color = accent; break;
                case "toggle": prefix = "▸ "; break;
                case "divider": text = "──────────"; color = muted; break;
                case "todo":
                    if (Prefs.showCheckbox(ctx)) prefix = it.checked ? "☑ " : "☐ ";
                    if (it.checked) color = muted;
                    break;
                default: break;
            }
            if (it.indent) prefix = "    " + prefix;

            rv.setTextViewText(R.id.item_text, prefix + text);
            rv.setTextColor(R.id.item_text, color);
            rv.setTextViewTextSize(R.id.item_text, TypedValue.COMPLEX_UNIT_SP, size);
            return rv;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return true; }
    }
}
