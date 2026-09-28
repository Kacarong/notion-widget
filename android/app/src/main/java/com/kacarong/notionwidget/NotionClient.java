package com.kacarong.notionwidget;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Notion API 읽기 전용 클라이언트 (PC 위젯과 동일한 규칙) */
public class NotionClient {
    private static final String NOTION_VERSION = "2022-06-28";

    public static class Item {
        public String type;      // h1,h2,h3,p,bullet,number,todo,quote,callout,code,divider,toggle
        public String text;
        public boolean checked;
        public boolean indent;
        public String icon;

        public Item(String type, String text) { this.type = type; this.text = text; }

        public JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("type", type); o.put("text", text);
            o.put("checked", checked); o.put("indent", indent);
            if (icon != null) o.put("icon", icon);
            return o;
        }

        public static Item fromJson(JSONObject o) {
            Item it = new Item(o.optString("type", "p"), o.optString("text", ""));
            it.checked = o.optBoolean("checked", false);
            it.indent = o.optBoolean("indent", false);
            it.icon = o.has("icon") ? o.optString("icon") : null;
            return it;
        }
    }

    /** URL/문자열에서 페이지 ID 추출 → 하이픈 UUID */
    public static String normalizeId(String input) {
        if (input == null) return "";
        String s = input.trim();
        Matcher uuid = Pattern.compile(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matcher(s);
        if (uuid.find()) return uuid.group().toLowerCase(Locale.ROOT);
        Matcher m = Pattern.compile("(?:^|[^0-9a-fA-F])([0-9a-fA-F]{32})(?![0-9a-fA-F])").matcher(s);
        if (!m.find()) return s;
        String id = m.group(1).toLowerCase(Locale.ROOT);
        return id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16)
                + "-" + id.substring(16, 20) + "-" + id.substring(20);
    }

    private static JSONObject request(String token, String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.notion.com/v1" + path).openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Notion-Version", NOTION_VERSION);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        } finally {
            conn.disconnect();
        }
        if (code >= 400) throw new Exception("Notion API " + code + ": " + sb.substring(0, Math.min(200, sb.length())));
        return new JSONObject(sb.toString());
    }

    private static String plain(JSONArray rich) {
        if (rich == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rich.length(); i++) {
            JSONObject t = rich.optJSONObject(i);
            if (t != null) sb.append(t.optString("plain_text", ""));
        }
        return sb.toString();
    }

    private static List<JSONObject> listChildren(String token, String blockId) throws Exception {
        List<JSONObject> out = new ArrayList<>();
        String cursor = null;
        do {
            String qs = "?page_size=100" + (cursor != null ? "&start_cursor=" + cursor : "");
            JSONObject data = request(token, "/blocks/" + blockId + "/children" + qs);
            JSONArray arr = data.optJSONArray("results");
            if (arr != null) for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONObject(i));
            cursor = data.optBoolean("has_more", false) ? data.optString("next_cursor", null) : null;
        } while (cursor != null && !cursor.equals("null"));
        return out;
    }

    private static boolean isHeading(String t) {
        return "heading_1".equals(t) || "heading_2".equals(t) || "heading_3".equals(t);
    }

    private static Item blockToItem(JSONObject b) {
        String t = b.optString("type");
        JSONObject d = b.optJSONObject(t);
        if (d == null) return null;
        String text = plain(d.optJSONArray("rich_text"));
        switch (t) {
            case "heading_1": return new Item("h1", text);
            case "heading_2": return new Item("h2", text);
            case "heading_3": return new Item("h3", text);
            case "paragraph": return new Item("p", text);
            case "bulleted_list_item": return new Item("bullet", text);
            case "numbered_list_item": return new Item("number", text);
            case "to_do": {
                Item it = new Item("todo", text);
                it.checked = d.optBoolean("checked", false);
                return it;
            }
            case "quote": return new Item("quote", text);
            case "callout": {
                Item it = new Item("callout", text);
                JSONObject icon = d.optJSONObject("icon");
                if (icon != null) it.icon = icon.optString("emoji", null);
                return it;
            }
            case "toggle": return new Item("toggle", text);
            case "code": return new Item("code", text);
            case "divider": return new Item("divider", "");
            case "child_page": return new Item("p", "📄 " + d.optString("title", ""));
            default: return text.isEmpty() ? null : new Item("p", text);
        }
    }

    /** 페이지에서 표시할 아이템 목록 (섹션 지정 지원) */
    public static List<Item> getItems(String token, String pageIdOrUrl, String sectionHeading,
                                      boolean includeHeading, int limit) throws Exception {
        String pageId = normalizeId(pageIdOrUrl);
        List<JSONObject> blocks = listChildren(token, pageId);

        // 쉼표로 여러 섹션 지정 가능: "오늘 할 일, 주간 목표"
        List<String> missing = new ArrayList<>();
        if (sectionHeading != null && !sectionHeading.trim().isEmpty()) {
            List<JSONObject> picked = new ArrayList<>();
            for (String raw : sectionHeading.split(",")) {
                String name = raw.trim();
                if (name.isEmpty()) continue;
                String target = name.toLowerCase(Locale.ROOT);
                int start = -1, level = 0;
                for (int i = 0; i < blocks.size(); i++) {
                    JSONObject b = blocks.get(i);
                    String t = b.optString("type");
                    if (isHeading(t)) {
                        JSONObject d = b.optJSONObject(t);
                        String txt = d == null ? "" : plain(d.optJSONArray("rich_text")).trim().toLowerCase(Locale.ROOT);
                        if (txt.equals(target)) { start = i; level = Integer.parseInt(t.substring(t.length() - 1)); break; }
                    }
                }
                if (start < 0) { missing.add(name); continue; }
                int end = blocks.size();
                for (int i = start + 1; i < blocks.size(); i++) {
                    String t = blocks.get(i).optString("type");
                    if (isHeading(t) && Integer.parseInt(t.substring(t.length() - 1)) <= level) { end = i; break; }
                }
                if (!picked.isEmpty()) {
                    try { picked.add(new JSONObject().put("type", "divider").put("divider", new JSONObject())); }
                    catch (Exception ignored) { }
                }
                picked.addAll(blocks.subList(includeHeading ? start : start + 1, end));
            }
            blocks = picked;
        }

        List<Item> items = new ArrayList<>();
        for (String m : missing) items.add(new Item("p", "(\"" + m + "\" 섹션을 찾지 못했습니다)"));
        for (JSONObject b : blocks) {
            Item it = blockToItem(b);
            if (it != null) items.add(it);
            if ("toggle".equals(b.optString("type")) && b.optBoolean("has_children", false)) {
                try {
                    for (JSONObject k : listChildren(token, b.optString("id"))) {
                        Item ki = blockToItem(k);
                        if (ki != null) { ki.indent = true; items.add(ki); }
                    }
                } catch (Exception ignored) { }
            }
            if (limit > 0 && items.size() >= limit) break;
        }
        if (limit > 0 && items.size() > limit) items = new ArrayList<>(items.subList(0, limit));
        return items;
    }

    public static String getTitle(String token, String pageIdOrUrl) {
        try {
            JSONObject page = request(token, "/pages/" + normalizeId(pageIdOrUrl));
            JSONObject props = page.optJSONObject("properties");
            if (props == null) return "";
            for (java.util.Iterator<String> it = props.keys(); it.hasNext(); ) {
                JSONObject p = props.optJSONObject(it.next());
                if (p != null && "title".equals(p.optString("type"))) return plain(p.optJSONArray("title"));
            }
        } catch (Exception ignored) { }
        return "";
    }

    /** 네트워크에서 새로 받아 캐시에 저장. 성공 여부 반환 */
    public static boolean refreshCache(Context ctx) {
        String token = Prefs.token(ctx), page = Prefs.pageId(ctx);
        if (token.isEmpty() || page.isEmpty()) {
            Prefs.setLastError(ctx, "설정 필요");
            return false;
        }
        try {
            List<Item> items = getItems(token, page, Prefs.section(ctx),
                    Prefs.includeHeading(ctx), Prefs.limit(ctx));
            JSONArray arr = new JSONArray();
            for (Item i : items) arr.put(i.toJson());
            Prefs.setCache(ctx, arr.toString());
            if (Prefs.showTitle(ctx)) Prefs.setCacheTitle(ctx, getTitle(token, page));
            Prefs.setLastError(ctx, "");
            Prefs.setLastAt(ctx, System.currentTimeMillis());
            return true;
        } catch (Exception e) {
            Prefs.setLastError(ctx, e.getMessage() == null ? "오류" : e.getMessage());
            return false;
        }
    }

    public static List<Item> cachedItems(Context ctx) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(Prefs.cache(ctx));
            for (int i = 0; i < arr.length(); i++) out.add(Item.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }
}
