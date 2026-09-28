package com.kacarong.notionwidget;

import android.content.Context;
import android.content.SharedPreferences;

/** 위젯 설정 저장소 */
public class Prefs {
    private static final String NAME = "notion_widget_prefs";

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String token(Context c) { return get(c).getString("token", ""); }
    public static String pageId(Context c) { return get(c).getString("pageId", ""); }
    public static String section(Context c) { return get(c).getString("section", ""); }
    public static boolean includeHeading(Context c) { return get(c).getBoolean("includeHeading", true); }
    public static int limit(Context c) { return get(c).getInt("limit", 0); }
    public static int refreshMinutes(Context c) { return get(c).getInt("refreshMinutes", 30); }

    // 외형
    public static int fontSize(Context c) { return get(c).getInt("fontSize", 14); }
    public static String theme(Context c) { return get(c).getString("theme", "dark"); }
    public static int bgAlpha(Context c) { return get(c).getInt("bgAlpha", 220); } // 0~255
    public static String accent(Context c) { return get(c).getString("accent", "#4F8CFF"); }
    public static boolean showTitle(Context c) { return get(c).getBoolean("showTitle", true); }
    public static boolean showCheckbox(Context c) { return get(c).getBoolean("showCheckbox", true); }

    // 마지막으로 받아온 내용 캐시 (오프라인/초기 표시용)
    public static String cache(Context c) { return get(c).getString("cache", ""); }
    public static void setCache(Context c, String json) { get(c).edit().putString("cache", json).apply(); }
    public static String cacheTitle(Context c) { return get(c).getString("cacheTitle", ""); }
    public static void setCacheTitle(Context c, String t) { get(c).edit().putString("cacheTitle", t).apply(); }
    public static String lastError(Context c) { return get(c).getString("lastError", ""); }
    public static void setLastError(Context c, String e) { get(c).edit().putString("lastError", e).apply(); }
    public static long lastAt(Context c) { return get(c).getLong("lastAt", 0L); }
    public static void setLastAt(Context c, long t) { get(c).edit().putLong("lastAt", t).apply(); }
}
