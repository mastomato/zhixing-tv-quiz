package cn.zhixing.quiztv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int BG = 0xfff7f8fc;
    private static final int INK = 0xff222339;
    private static final int MUTED = 0xff85879d;
    private static final int PURPLE = 0xff7058de;
    private static final int PURPLE_DARK = 0xff5840c1;
    private static final int GREEN = 0xff2aa377;
    private static final int RED = 0xffc25252;
    private static final int WHITE = Color.WHITE;
    private static final String[] EDITIONS = {"通用题库", "人教版", "统编版"};
    private final List<Question> bank = new ArrayList<>();
    private final List<Question> round = new ArrayList<>();
    private final Button[] optionButtons = new Button[4];
    private LinearLayout body;
    private TextView feedback;
    private SharedPreferences prefs;
    private Question current;
    private int grade = 3, amount = 10, index, correct, selected = -1, phase;
    private int totalAnswered, totalCorrect;
    private int editionIndex, correctSound, wrongSound, completeSound;
    private String subject = "全部学科", term = "不限";
    private boolean answered, musicEnabled, effectsEnabled, reviewMode;
    private MediaPlayer musicPlayer;
    private SoundPool soundPool;

    private static class Question {
        int id, grade, answer;
        String label, term, subject, chapter, text, explain, source;
        String[] options;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        prefs = getSharedPreferences("progress", MODE_PRIVATE);
        totalAnswered = prefs.getInt("s0", 0);
        totalCorrect = prefs.getInt("s1", 0);
        grade = Math.max(1, Math.min(9, prefs.getInt("grade", 3)));
        amount = prefs.getInt("amount", 10);
        subject = prefs.getString("subject", "全部学科");
        term = prefs.getString("term", "不限");
        if ("全".equals(term)) {
            term = "不限";
            prefs.edit().putString("term", term).apply();
        }
        editionIndex = Math.max(0, Math.min(EDITIONS.length - 1, prefs.getInt("edition", 0)));
        if (!editionAllowed(editionIndex)) editionIndex = 0;
        musicEnabled = prefs.getBoolean("music_enabled", true);
        effectsEnabled = prefs.getBoolean("effects_enabled", true);
        prepareAudio();
        loadBank();
        showHome();
    }

    private void prepareAudio() {
        try {
            musicPlayer = MediaPlayer.create(this, R.raw.bgm);
            if (musicPlayer != null) {
                musicPlayer.setLooping(true);
                musicPlayer.setVolume(.28f, .28f);
            }
            soundPool = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);
            correctSound = soundPool.load(this, R.raw.correct, 1);
            wrongSound = soundPool.load(this, R.raw.wrong, 1);
            completeSound = soundPool.load(this, R.raw.complete, 1);
        } catch (RuntimeException error) {
            android.util.Log.e("ZhixingTV", "声音资源初始化失败", error);
        }
    }

    private void startMusic() {
        if (musicEnabled && musicPlayer != null && !musicPlayer.isPlaying()) {
            try { musicPlayer.start(); }
            catch (RuntimeException error) { android.util.Log.e("ZhixingTV", "背景音乐播放失败", error); }
        }
    }

    private void pauseMusic() {
        if (musicPlayer != null && musicPlayer.isPlaying()) musicPlayer.pause();
    }

    private void playEffect(int soundId) {
        if (effectsEnabled && soundPool != null && soundId != 0)
            soundPool.play(soundId, .8f, .8f, 1, 0, 1f);
    }

    @Override protected void onResume() {
        super.onResume();
        startMusic();
    }

    @Override protected void onPause() {
        pauseMusic();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (musicPlayer != null) { musicPlayer.release(); musicPlayer = null; }
        if (soundPool != null) { soundPool.release(); soundPool = null; }
        super.onDestroy();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(12));
        if (stroke != 0) d.setStroke(dp(2), stroke);
        return d;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String value, boolean primary) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setSingleLine(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setStateListAnimator(null);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
            shape(primary ? PURPLE_DARK : 0xffebe6ff, PURPLE_DARK));
        states.addState(new int[]{}, shape(primary ? PURPLE : WHITE, primary ? 0 : 0xffe7e6ef));
        b.setBackground(states);
        b.setTextColor(primary ? WHITE : INK);
        return b;
    }

    private Button option(String value) {
        Button b = button(value, false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        b.setTextSize(18);
        b.setMinHeight(dp(52));
        b.setPadding(dp(16), dp(9), dp(16), dp(9));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_selected, android.R.attr.state_focused},
            shape(PURPLE_DARK, 0xffd2c7ff));
        states.addState(new int[]{android.R.attr.state_selected}, shape(PURPLE, 0));
        states.addState(new int[]{android.R.attr.state_focused}, shape(0xffebe6ff, PURPLE));
        states.addState(new int[]{}, shape(0xfff7f7fb, 0xffe9e8f0));
        b.setBackground(states);
        b.setTextColor(new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_selected}, new int[]{}},
            new int[]{WHITE, INK}));
        return b;
    }

    private LinearLayout.LayoutParams spaced(int width, int height, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.topMargin = dp(top);
        return lp;
    }

    private void shell(String heading, String description) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        int sideWidth = Math.min(170, Math.max(142,
            getResources().getConfiguration().screenWidthDp / 6));
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(dp(18), dp(25), dp(12), dp(16));
        side.setBackgroundColor(WHITE);
        root.addView(side, new LinearLayout.LayoutParams(dp(sideWidth), -1));
        side.addView(label("知行答题", 22, INK, true),
            new LinearLayout.LayoutParams(-1, dp(43)));
        side.addView(label("电视答题练习", 12, MUTED, false));
        side.addView(label("答题练习", 17, PURPLE, true), spaced(-1, dp(43), 24));
        side.addView(label("即时判分", 15, MUTED, false), spaced(-1, dp(34), 4));
        side.addView(label("错题复习", 15, MUTED, false), spaced(-1, dp(34), 4));
        side.addView(label("本机记录\n\n累计答题  " + totalAnswered +
            "\n答对题数  " + totalCorrect, 13, MUTED, false),
            spaced(-1, -2, 30));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(28), dp(20), dp(28), dp(24));
        scroll.addView(body);
        body.addView(label("知行  /  " + heading, 13, MUTED, false));
        body.addView(label(heading, 27, INK, true), spaced(-1, -2, 8));
        body.addView(label(description, 14, MUTED, false), spaced(-1, -2, 3));
    }

    private void showHome() { showHome(0); }

    private void showHome(int focusTarget) {
        phase = 0;
        shell("学习首页", "选择范围，开始今天的练习。");
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(stats, spaced(-1, -2, 20));
        addStat(stats, "累计答题", totalAnswered);
        addStat(stats, "答对题数", totalCorrect);
        addStat(stats, "待复习错题", wrongIds().size());

        body.addView(label("练习设置", 19, INK, true), spaced(-1, -2, 20));
        String[] grades = {"一年级", "二年级", "三年级", "四年级", "五年级", "六年级",
            "初一", "初二", "初三"};
        Button gradeBtn = button("年级：" + grades[grade - 1] + "  ›", false);
        gradeBtn.setOnClickListener(v -> {
            grade = grade % 9 + 1;
            prefs.edit().putInt("grade", grade).apply();
            showHome(1);
        });
        Button termBtn = button("学期：" + term + "  ›", false);
        termBtn.setOnClickListener(v -> {
            String[] values = {"不限", "上", "下"};
            int at = 0;
            for (int i = 0; i < values.length; i++) if (values[i].equals(term)) at = i;
            term = values[(at + 1) % values.length];
            prefs.edit().putString("term", term).apply();
            showHome(2);
        });
        addSettingsRow(gradeBtn, termBtn, 11);

        String[] subjects = {"全部学科", "数学", "奥数", "语文", "英语", "科学", "物理"};
        Button subjectBtn = button("学科：" + subject + "  ›", false);
        subjectBtn.setOnClickListener(v -> {
            int at = 0;
            for (int i = 0; i < subjects.length; i++) if (subjects[i].equals(subject)) at = i;
            subject = subjects[(at + 1) % subjects.length];
            prefs.edit().putString("subject", subject).apply();
            if (!editionAllowed(editionIndex)) {
                editionIndex = 0;
                prefs.edit().putInt("edition", 0).apply();
            }
            showHome(3);
        });
        Button amountBtn = button("题量：" + amount + " 道  ›", false);
        amountBtn.setOnClickListener(v -> {
            amount = amount == 10 ? 20 : amount == 20 ? 5 : 10;
            prefs.edit().putInt("amount", amount).apply();
            showHome(4);
        });
        addSettingsRow(subjectBtn, amountBtn, 9);

        Button editionBtn = button("教材：" + EDITIONS[editionIndex] + "  ›", false);
        editionBtn.setOnClickListener(v -> {
            int[] allowed = allowedEditions();
            int at = 0;
            for (int i = 0; i < allowed.length; i++)
                if (allowed[i] == editionIndex) at = i;
            editionIndex = allowed[(at + 1) % allowed.length];
            prefs.edit().putInt("edition", editionIndex).apply();
            showHome(5);
        });
        Button musicBtn = button("背景音乐：" + (musicEnabled ? "开" : "关"), false);
        musicBtn.setOnClickListener(v -> {
            musicEnabled = !musicEnabled;
            prefs.edit().putBoolean("music_enabled", musicEnabled).apply();
            if (musicEnabled) startMusic(); else pauseMusic();
            showHome(6);
        });
        Button effectsBtn = button("答题音效：" + (effectsEnabled ? "开" : "关"), false);
        effectsBtn.setOnClickListener(v -> {
            effectsEnabled = !effectsEnabled;
            prefs.edit().putBoolean("effects_enabled", effectsEnabled).apply();
            showHome(7);
        });
        addSettingsTriple(editionBtn, musicBtn, effectsBtn, 9);
        body.addView(label("人教版含数/英/物理；统编版含语文。通用题库含 498 道奥数题。", 12, MUTED, false),
            spaced(-1, -2, 8));

        Button start = button("开始练习  →", true);
        LinearLayout.LayoutParams startLp = spaced(dp(220), dp(52), 18);
        body.addView(start, startLp);
        start.setOnClickListener(v -> startRound(false));
        body.addView(label("5,735 道离线题目 · 答对后自动进入下一题", 13, MUTED, false),
            spaced(-1, -2, 12));
        Button wrong = button("复习错题  " + wrongIds().size(), false);
        body.addView(wrong, spaced(dp(220), dp(50), 15));
        wrong.setOnClickListener(v -> startRound(true));
        Button[] targets = {start, gradeBtn, termBtn, subjectBtn, amountBtn,
            editionBtn, musicBtn, effectsBtn};
        targets[Math.max(0, Math.min(targets.length - 1, focusTarget))].requestFocus();
    }

    private void addStat(LinearLayout row, String name, int value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(11));
        card.setBackground(shape(WHITE, 0));
        card.addView(label(name, 13, MUTED, false));
        card.addView(label(String.valueOf(value), 25, PURPLE, true), spaced(-1, -2, 5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.rightMargin = dp(10);
        row.addView(card, lp);
    }

    private void addSettingsRow(Button left, Button right, int top) {
        LinearLayout row = new LinearLayout(this);
        body.addView(row, spaced(-1, dp(52), top));
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -1, 1);
        a.rightMargin = dp(9);
        row.addView(left, a);
        row.addView(right, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void addSettingsTriple(Button first, Button second, Button third, int top) {
        LinearLayout row = new LinearLayout(this);
        body.addView(row, spaced(-1, dp(52), top));
        Button[] buttons = {first, second, third};
        for (int i = 0; i < buttons.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
            if (i < buttons.length - 1) lp.rightMargin = dp(9);
            row.addView(buttons[i], lp);
        }
    }

    private int[] allowedEditions() {
        if ("数学".equals(subject) || "英语".equals(subject) || "物理".equals(subject))
            return new int[]{0, 1};
        if ("语文".equals(subject)) return new int[]{0, 2};
        if ("科学".equals(subject) || "奥数".equals(subject)) return new int[]{0};
        return new int[]{0, 1, 2};
    }

    private boolean editionAllowed(int value) {
        for (int option : allowedEditions()) if (value == option) return true;
        return false;
    }

    private boolean editionMatches(Question q) {
        if (editionIndex == 0) return true;
        if (!"cn-math-taxonomy".equals(q.source)) return false;
        if (editionIndex == 1)
            return "数学".equals(q.subject) || "英语".equals(q.subject) || "物理".equals(q.subject);
        return "语文".equals(q.subject);
    }

    private boolean subjectMatches(Question q) {
        if ("奥数".equals(subject)) return "generated-olympiad".equals(q.source);
        return "全部学科".equals(subject) || subject.equals(q.subject);
    }

    private void loadBank() {
        try (InputStream input = getAssets().open("questions.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[32768];
            int n;
            while ((n = input.read(buf)) != -1) out.write(buf, 0, n);
            JSONArray rows = new JSONArray(out.toString("UTF-8"));
            for (int i = 0; i < rows.length(); i++) {
                JSONObject o = rows.getJSONObject(i);
                Question q = new Question();
                q.id = o.optInt("id");
                q.grade = o.optInt("grade");
                q.label = o.optString("grade_label");
                q.term = o.optString("term");
                q.subject = o.optString("subject");
                q.chapter = o.optString("chapter");
                q.text = o.optString("text");
                q.answer = o.optInt("answer_index");
                q.explain = o.optString("explain");
                q.source = o.optString("source");
                q.options = new String[]{o.optString("opt_a"), o.optString("opt_b"),
                    o.optString("opt_c"), o.optString("opt_d")};
                bank.add(q);
            }
        } catch (Exception error) {
            android.util.Log.e("ZhixingTV", "题库加载失败", error);
        }
    }

    private Set<String> wrongIds() {
        Set<String> ids = new HashSet<>();
        for (String id : prefs.getString("wrong_ids", "").split(","))
            if (!id.isEmpty()) ids.add(id);
        return ids;
    }

    private void saveWrongIds(Set<String> ids) {
        StringBuilder text = new StringBuilder();
        for (String id : ids) {
            if (text.length() > 0) text.append(',');
            text.append(id);
        }
        prefs.edit().putString("wrong_ids", text.toString()).putInt("wrong_count", ids.size()).apply();
    }

    private void startRound(boolean wrongOnly) {
        reviewMode = wrongOnly;
        round.clear();
        Set<String> wrong = wrongIds();
        for (Question q : bank) {
            if (wrongOnly ? wrong.contains(String.valueOf(q.id)) :
                q.grade == grade && subjectMatches(q) &&
                (term.equals("不限") || q.term.equals(term) || q.term.equals("全")) &&
                editionMatches(q)) round.add(q);
        }
        if (round.isEmpty()) {
            shell("暂无题目", wrongOnly ? "当前没有需要复习的错题。" : "所选范围没有题目，请调整筛选条件。");
            Button back = button("返回首页", true);
            body.addView(back, spaced(dp(220), dp(52), 20));
            back.setOnClickListener(v -> showHome());
            back.requestFocus();
            return;
        }
        Collections.shuffle(round);
        if (round.size() > amount) round.subList(amount, round.size()).clear();
        index = 0;
        correct = 0;
        showQuestion();
    }

    private void showQuestion() {
        if (index >= round.size()) { showResult(); return; }
        phase = 1;
        current = round.get(index);
        selected = -1;
        answered = false;
        shell("答题练习", "用方向键移动焦点，按确定键直接作答。");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(18));
        card.setBackground(shape(WHITE, 0));
        body.addView(card, spaced(-1, -2, 16));
        String shownSubject = "generated-olympiad".equals(current.source) ? "数学·奥数" : current.subject;
        card.addView(label(current.label + "  ·  " + shownSubject + "  ·  " +
            (reviewMode ? "错题复习" : EDITIONS[editionIndex]) + "  ·  第 " +
            (index + 1) + " / " + round.size() + " 题", 14, PURPLE, true));
        TextView question = label(current.text, 23, INK, true);
        question.setPadding(0, dp(14), 0, dp(11));
        card.addView(question, new LinearLayout.LayoutParams(-1, -2));

        for (int i = 0; i < 4; i++) {
            final int choice = i;
            Button b = option((char) ('A' + i) + "   " + current.options[i]);
            LinearLayout.LayoutParams lp = spaced(-1, -2, 6);
            card.addView(b, lp);
            optionButtons[i] = b;
            b.setOnClickListener(v -> {
                if (answered) return;
                selected = choice;
                submitAnswer();
            });
        }

        feedback = label("", 14, MUTED, false);
        feedback.setPadding(0, dp(8), 0, dp(4));
        card.addView(feedback, new LinearLayout.LayoutParams(-1, -2));
        optionButtons[0].requestFocus();
    }

    private void submitAnswer() {
        if (selected < 0 || answered) return;
        answered = true;
        totalAnswered++;
        boolean right = selected == current.answer;
        playEffect(right ? correctSound : wrongSound);
        Set<String> wrong = wrongIds();
        if (right) {
            totalCorrect++;
            correct++;
            wrong.remove(String.valueOf(current.id));
        } else {
            wrong.add(String.valueOf(current.id));
        }
        saveWrongIds(wrong);
        prefs.edit().putInt("s0", totalAnswered).putInt("s1", totalCorrect).apply();
        for (int i = 0; i < 4; i++) {
            Button b = optionButtons[i];
            b.setEnabled(false);
            b.setSelected(false);
            b.setTextColor(i == current.answer ? GREEN : i == selected ? RED : INK);
            b.setBackground(shape(i == current.answer ? 0xffddf5e9 :
                i == selected ? 0xffffe7e7 : 0xfff7f7fb, 0));
        }
        feedback.setText((right ? "答对了 ✓  " :
            "正确答案：" + (char) ('A' + current.answer) + "  " + current.options[current.answer] + "。  ") +
            current.explain);
        feedback.setTextColor(right ? GREEN : RED);
        if (right) {
            final int completedIndex = index;
            new Handler().postDelayed(() -> {
                if (phase == 1 && index == completedIndex && answered) {
                    index++;
                    showQuestion();
                }
            }, 850);
        } else {
            Button next = button("下一题  →", true);
            body.addView(next, spaced(dp(190), dp(50), 12));
            next.setOnClickListener(v -> { index++; showQuestion(); });
            next.requestFocus();
        }
    }

    private void showResult() {
        phase = 2;
        playEffect(completeSound);
        shell("练习完成", "今天的每次思考都算数。");
        TextView score = label(correct + " / " + round.size() + " 题答对", 32, PURPLE, true);
        body.addView(score, spaced(-1, -2, 28));
        body.addView(label("正确率 " + correct * 100 / round.size() + "%", 18, MUTED, false),
            spaced(-1, -2, 10));
        Button home = button("返回首页", true);
        body.addView(home, spaced(dp(220), dp(52), 25));
        home.setOnClickListener(v -> showHome());
        home.requestFocus();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && phase != 0) {
            showHome();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
