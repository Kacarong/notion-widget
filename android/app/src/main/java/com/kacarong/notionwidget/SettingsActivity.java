package com.kacarong.notionwidget;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    private EditText token, pageId, section, accent;
    private EditText limit, refreshMin;
    private CheckBox includeHeading, showTitle, showCheckbox;
    private SeekBar fontSize, bgAlpha;
    private TextView fontSizeVal, bgAlphaVal, testResult;
    private Spinner theme;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);

        token = findViewById(R.id.token);
        pageId = findViewById(R.id.pageId);
        section = findViewById(R.id.section);
        accent = findViewById(R.id.accent);
        limit = findViewById(R.id.limit);
        refreshMin = findViewById(R.id.refreshMin);
        includeHeading = findViewById(R.id.includeHeading);
        showTitle = findViewById(R.id.showTitle);
        showCheckbox = findViewById(R.id.showCheckbox);
        fontSize = findViewById(R.id.fontSize);
        bgAlpha = findViewById(R.id.bgAlpha);
        fontSizeVal = findViewById(R.id.fontSizeVal);
        bgAlphaVal = findViewById(R.id.bgAlphaVal);
        testResult = findViewById(R.id.testResult);
        theme = findViewById(R.id.theme);

        load();

        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { updateLabels(); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        };
        fontSize.setOnSeekBarChangeListener(l);
        bgAlpha.setOnSeekBarChangeListener(l);

        ((Button) findViewById(R.id.saveBtn)).setOnClickListener(v -> save());
        ((Button) findViewById(R.id.testBtn)).setOnClickListener(v -> test());
        ((Button) findViewById(R.id.updateBtn)).setOnClickListener(v -> UpdateChecker.check(this, false));

        // 앱 열 때 조용히 업데이트 확인
        UpdateChecker.check(this, true);
    }

    private void updateLabels() {
        fontSizeVal.setText((fontSize.getProgress() + 10) + "sp");
        bgAlphaVal.setText(Math.round(bgAlpha.getProgress() / 255f * 100) + "%");
    }

    private void load() {
        token.setText(Prefs.token(this));
        pageId.setText(Prefs.pageId(this));
        section.setText(Prefs.section(this));
        accent.setText(Prefs.accent(this));
        limit.setText(String.valueOf(Prefs.limit(this)));
        refreshMin.setText(String.valueOf(Prefs.refreshMinutes(this)));
        includeHeading.setChecked(Prefs.includeHeading(this));
        showTitle.setChecked(Prefs.showTitle(this));
        showCheckbox.setChecked(Prefs.showCheckbox(this));
        fontSize.setMax(14); // 10~24sp
        fontSize.setProgress(Math.max(0, Prefs.fontSize(this) - 10));
        bgAlpha.setMax(255);
        bgAlpha.setProgress(Prefs.bgAlpha(this));
        theme.setSelection("light".equals(Prefs.theme(this)) ? 1 : 0);
        updateLabels();
    }

    private void save() {
        SharedPreferences.Editor e = Prefs.get(this).edit();
        e.putString("token", token.getText().toString().trim());
        e.putString("pageId", pageId.getText().toString().trim());
        e.putString("section", section.getText().toString().trim());
        e.putString("accent", accent.getText().toString().trim());
        e.putBoolean("includeHeading", includeHeading.isChecked());
        e.putBoolean("showTitle", showTitle.isChecked());
        e.putBoolean("showCheckbox", showCheckbox.isChecked());
        e.putInt("limit", parseInt(limit.getText().toString(), 0));
        e.putInt("refreshMinutes", Math.max(15, parseInt(refreshMin.getText().toString(), 30)));
        e.putInt("fontSize", fontSize.getProgress() + 10);
        e.putInt("bgAlpha", bgAlpha.getProgress());
        e.putString("theme", theme.getSelectedItemPosition() == 1 ? "light" : "dark");
        e.apply();

        Toast.makeText(this, "저장했습니다. 위젯을 갱신합니다.", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            NotionClient.refreshCache(getApplicationContext());
            new Handler(Looper.getMainLooper()).post(() -> NoteWidgetProvider.notifyAll(getApplicationContext()));
        }).start();
    }

    private static int parseInt(String s, int dflt) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return dflt; }
    }

    private void test() {
        testResult.setVisibility(View.VISIBLE);
        testResult.setText("확인 중…");
        final String t = token.getText().toString().trim();
        final String p = pageId.getText().toString().trim();
        final String sec = section.getText().toString().trim();
        new Thread(() -> {
            String msg;
            try {
                List<NotionClient.Item> items =
                        NotionClient.getItems(t, p, sec, true, 5);
                StringBuilder sb = new StringBuilder("연결 성공! 미리보기:\n");
                for (NotionClient.Item i : items) sb.append(" ").append(i.type).append(": ").append(i.text).append("\n");
                msg = sb.toString();
            } catch (Exception ex) {
                msg = "실패: " + ex.getMessage()
                        + "\n\n확인: 토큰이 맞는지 / 페이지 ⋯ → 연결 에 통합을 추가했는지 / 페이지 주소가 맞는지";
            }
            final String out = msg;
            new Handler(Looper.getMainLooper()).post(() -> testResult.setText(out));
        }).start();
    }
}
