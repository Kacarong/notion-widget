package com.kacarong.notionwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * GitHub Releases를 확인해 새 버전이 있으면 알려주고 APK를 내려받아 설치한다.
 * (Play 스토어 밖 배포라 자동 무음 설치는 불가 — 사용자가 설치 버튼을 한 번 누르는 방식)
 */
public class UpdateChecker {
    private static final String OWNER = "Kacarong";
    private static final String REPO = "notion-widget";
    private static final String API =
            "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";

    /** silent=true 면 최신일 때 아무 것도 표시하지 않음(앱 시작 시 자동 확인용) */
    public static void check(final Activity act, final boolean silent) {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String l;
                    while ((l = br.readLine()) != null) sb.append(l);
                }
                c.disconnect();

                JSONObject rel = new JSONObject(sb.toString());
                String tag = rel.optString("tag_name", "").replaceFirst("^v", "");
                String notes = rel.optString("body", "");
                String apkUrl = null;
                JSONArray assets = rel.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        if (a.optString("name", "").endsWith(".apk")) {
                            apkUrl = a.optString("browser_download_url");
                            break;
                        }
                    }
                }
                final String current = act.getPackageManager()
                        .getPackageInfo(act.getPackageName(), 0).versionName;

                if (tag.isEmpty() || apkUrl == null || !isNewer(tag, current)) {
                    if (!silent) toastLater(act, "최신 버전입니다. (v" + current + ")");
                    return;
                }
                final String url = apkUrl, ver = tag, body = notes;
                new Handler(Looper.getMainLooper()).post(() ->
                        new AlertDialog.Builder(act)
                                .setTitle("새 버전 v" + ver)
                                .setMessage((body.isEmpty() ? "" : body + "\n\n")
                                        + "지금 내려받아 설치할까요?")
                                .setPositiveButton("설치", (d, w) ->
                                        act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))))
                                .setNegativeButton("나중에", null)
                                .show());
            } catch (Exception e) {
                if (!silent) toastLater(act, "업데이트 확인 실패: " + e.getMessage());
            }
        }).start();
    }

    private static void toastLater(Activity act, String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
                android.widget.Toast.makeText(act, msg, android.widget.Toast.LENGTH_SHORT).show());
    }

    /** "0.3.0" vs "0.2.0" 비교 */
    static boolean isNewer(String remote, String local) {
        String[] r = remote.split("\\."), l = local.split("\\.");
        for (int i = 0; i < Math.max(r.length, l.length); i++) {
            int a = i < r.length ? safe(r[i]) : 0;
            int b = i < l.length ? safe(l[i]) : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    private static int safe(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
    }
}
