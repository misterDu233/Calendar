package com.example.shiftcalendar;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int GREEN = Color.rgb(27, 111, 91);
    private static final int RED = Color.rgb(193, 64, 54);
    private static final int AMBER = Color.rgb(177, 117, 25);

    private final HolidayStore holidayStore = new HolidayStore();
    private SharedPreferences prefs;
    private YearMonth visibleMonth = YearMonth.of(2026, 1);
    private LocalDate selectedDate = LocalDate.of(2026, 1, 1);

    private CalendarMonthView calendarView;
    private TextView monthTitle;
    private TextView selectedInfo;
    private EditText cycleStartInput;
    private EditText cycleLengthInput;
    private EditText shiftNamesInput;
    private EditText alarmTimesInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("shift_calendar", MODE_PRIVATE);
        if (!prefs.contains("cycle_start")) {
            prefs.edit()
                    .putString("cycle_start", "2026-01-01")
                    .putInt("cycle_length", 4)
                    .putString("shift_names", "白班\n夜班\n休息\n休息")
                    .putString("alarm_白班", "07:00")
                    .putString("alarm_夜班", "19:00")
                    .apply();
        }

        setContentView(buildContent());
        refreshAll();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(root);

        TextView title = title("轮班日历");
        root.addView(title);

        LinearLayout nav = row();
        Button prev = button("上月");
        Button next = button("下月");
        monthTitle = label("", 20, Color.rgb(32, 42, 38));
        monthTitle.setGravity(android.view.Gravity.CENTER);
        nav.addView(prev, new LinearLayout.LayoutParams(0, dp(44), 1));
        nav.addView(monthTitle, new LinearLayout.LayoutParams(0, dp(44), 2));
        nav.addView(next, new LinearLayout.LayoutParams(0, dp(44), 1));
        root.addView(nav);

        prev.setOnClickListener(v -> {
            visibleMonth = visibleMonth.minusMonths(1);
            keepSupportedYears();
            refreshAll();
        });
        next.setOnClickListener(v -> {
            visibleMonth = visibleMonth.plusMonths(1);
            keepSupportedYears();
            refreshAll();
        });

        LinearLayout jump = row();
        Button y2026 = button("2026");
        Button y2027 = button("2027");
        jump.addView(y2026, new LinearLayout.LayoutParams(0, dp(42), 1));
        jump.addView(y2027, new LinearLayout.LayoutParams(0, dp(42), 1));
        root.addView(jump);
        y2026.setOnClickListener(v -> {
            visibleMonth = YearMonth.of(2026, 1);
            selectedDate = LocalDate.of(2026, 1, 1);
            refreshAll();
        });
        y2027.setOnClickListener(v -> {
            visibleMonth = YearMonth.of(2027, 1);
            selectedDate = LocalDate.of(2027, 1, 1);
            refreshAll();
        });

        calendarView = new CalendarMonthView(this);
        calendarView.setOnDateSelected(date -> {
            selectedDate = date;
            refreshAll();
        });
        root.addView(calendarView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(430)));

        selectedInfo = label("", 16, Color.rgb(46, 55, 52));
        selectedInfo.setPadding(0, dp(10), 0, dp(10));
        root.addView(selectedInfo);

        root.addView(section("轮班周期"));
        cycleStartInput = input("周期开始日期，如 2026-01-01");
        cycleLengthInput = input("周期天数，如 4");
        cycleLengthInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        shiftNamesInput = multiInput("周期内每天的工作类型，一行一天，如：\n白班\n夜班\n休息\n休息");
        root.addView(cycleStartInput);
        root.addView(cycleLengthInput);
        root.addView(shiftNamesInput);

        Button saveCycle = button("保存轮班周期");
        root.addView(saveCycle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        saveCycle.setOnClickListener(v -> saveCycle());

        root.addView(section("当前班次闹钟"));
        alarmTimesInput = multiInput("给选中日期的班次设置闹钟，一行一个 HH:mm，如：\n07:00\n07:20");
        root.addView(alarmTimesInput);

        Button saveAlarm = button("保存该班次闹钟");
        Button createAlarm = button("创建选中日期系统闹钟");
        root.addView(saveAlarm, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        root.addView(createAlarm, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        saveAlarm.setOnClickListener(v -> saveAlarmForSelectedShift());
        createAlarm.setOnClickListener(v -> createSystemAlarmsForSelectedDate());

        TextView note = label("2026 调休按国务院办公厅正式通知预置；2027 先预置法定节日和周末，官方调休发布后可在 HolidayStore 中更新。", 13, Color.rgb(89, 96, 93));
        note.setPadding(0, dp(10), 0, 0);
        root.addView(note);

        return scroll;
    }

    private void refreshAll() {
        monthTitle.setText(String.format(Locale.CHINA, "%d年%02d月",
                visibleMonth.getYear(), visibleMonth.getMonthValue()));
        calendarView.setMonth(visibleMonth, selectedDate, loadCycle(), holidayStore);
        CycleConfig cycle = loadCycle();
        String shift = cycle.shiftFor(selectedDate);
        HolidayInfo holiday = holidayStore.info(selectedDate);
        selectedInfo.setText(selectedDate + "  " + holiday.label + "\n班次：" + shift);
        cycleStartInput.setText(prefs.getString("cycle_start", "2026-01-01"));
        cycleLengthInput.setText(String.valueOf(prefs.getInt("cycle_length", 4)));
        shiftNamesInput.setText(prefs.getString("shift_names", "白班\n夜班\n休息\n休息"));
        alarmTimesInput.setText(prefs.getString("alarm_" + shift, ""));
    }

    private void saveCycle() {
        int length;
        try {
            length = Integer.parseInt(cycleLengthInput.getText().toString().trim());
            LocalDate.parse(cycleStartInput.getText().toString().trim(), DATE_FMT);
        } catch (Exception ex) {
            toast("请检查周期开始日期和周期天数");
            return;
        }
        if (length < 1 || length > 60) {
            toast("周期天数需在 1-60 之间");
            return;
        }
        prefs.edit()
                .putString("cycle_start", cycleStartInput.getText().toString().trim())
                .putInt("cycle_length", length)
                .putString("shift_names", shiftNamesInput.getText().toString().trim())
                .apply();
        toast("已保存轮班周期");
        refreshAll();
    }

    private void saveAlarmForSelectedShift() {
        String shift = loadCycle().shiftFor(selectedDate);
        List<String> times = parseTimes(alarmTimesInput.getText().toString());
        if (times.isEmpty()) {
            toast("请输入至少一个 HH:mm 闹钟时间");
            return;
        }
        prefs.edit().putString("alarm_" + shift, String.join("\n", times)).apply();
        toast("已保存 " + shift + " 闹钟");
    }

    private void createSystemAlarmsForSelectedDate() {
        String shift = loadCycle().shiftFor(selectedDate);
        List<String> times = parseTimes(prefs.getString("alarm_" + shift, ""));
        if (times.isEmpty()) {
            toast("当前班次还没有闹钟");
            return;
        }
        for (String time : times) {
            String[] parts = time.split(":");
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, Integer.parseInt(parts[0]))
                    .putExtra(AlarmClock.EXTRA_MINUTES, Integer.parseInt(parts[1]))
                    .putExtra(AlarmClock.EXTRA_MESSAGE, selectedDate + " " + shift)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException ex) {
                toast("系统没有可用的闹钟应用");
                return;
            }
        }
    }

    private List<String> parseTimes(String raw) {
        List<String> times = new ArrayList<>();
        for (String token : raw.split("[,，\\n\\s]+")) {
            String text = token.trim();
            if (text.isEmpty()) continue;
            if (!text.matches("([01]?\\d|2[0-3]):[0-5]\\d")) continue;
            String[] parts = text.split(":");
            times.add(String.format(Locale.CHINA, "%02d:%02d",
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
        }
        return times;
    }

    private CycleConfig loadCycle() {
        LocalDate start;
        try {
            start = LocalDate.parse(prefs.getString("cycle_start", "2026-01-01"), DATE_FMT);
        } catch (Exception ex) {
            start = LocalDate.of(2026, 1, 1);
        }
        int length = Math.max(1, prefs.getInt("cycle_length", 4));
        String raw = prefs.getString("shift_names", "白班\n夜班\n休息\n休息");
        List<String> names = new ArrayList<>();
        for (String line : raw.split("\\n")) {
            String value = line.trim();
            if (!value.isEmpty()) names.add(value);
        }
        while (names.size() < length) names.add("休息");
        return new CycleConfig(start, length, names);
    }

    private void keepSupportedYears() {
        if (visibleMonth.getYear() < 2026) visibleMonth = YearMonth.of(2026, 1);
        if (visibleMonth.getYear() > 2027) visibleMonth = YearMonth.of(2027, 12);
    }

    private TextView title(String text) {
        TextView view = label(text, 28, GREEN);
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    private TextView section(String text) {
        TextView view = label(text, 18, Color.rgb(31, 39, 36));
        view.setPadding(0, dp(18), 0, dp(8));
        return view;
    }

    private TextView label(String text, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private EditText input(String hint) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setSingleLine(true);
        view.setTextSize(15);
        return view;
    }

    private EditText multiInput(String hint) {
        EditText view = input(hint);
        view.setSingleLine(false);
        view.setMinLines(3);
        view.setGravity(android.view.Gravity.TOP);
        return view;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(GREEN);
        return button;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        return row;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class CycleConfig {
        final LocalDate start;
        final int length;
        final List<String> names;

        CycleConfig(LocalDate start, int length, List<String> names) {
            this.start = start;
            this.length = length;
            this.names = names;
        }

        String shiftFor(LocalDate date) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(start, date);
            int index = Math.floorMod((int) days, length);
            return names.get(index);
        }
    }

    private static class HolidayInfo {
        final String label;
        final boolean holiday;
        final boolean adjustedWorkday;

        HolidayInfo(String label, boolean holiday, boolean adjustedWorkday) {
            this.label = label;
            this.holiday = holiday;
            this.adjustedWorkday = adjustedWorkday;
        }
    }

    private static class HolidayStore {
        private final Map<LocalDate, String> holidays = new HashMap<>();
        private final Set<LocalDate> workdays = new HashSet<>();

        HolidayStore() {
            range("2026-01-01", "2026-01-03", "元旦");
            work("2026-01-04");
            range("2026-02-15", "2026-02-23", "春节");
            work("2026-02-14");
            work("2026-02-28");
            range("2026-04-04", "2026-04-06", "清明");
            range("2026-05-01", "2026-05-05", "劳动节");
            work("2026-05-09");
            range("2026-06-19", "2026-06-21", "端午");
            range("2026-09-25", "2026-09-27", "中秋");
            range("2026-10-01", "2026-10-07", "国庆");
            work("2026-09-20");
            work("2026-10-10");

            range("2027-01-01", "2027-01-03", "元旦");
            range("2027-02-05", "2027-02-11", "春节预置");
            range("2027-04-03", "2027-04-05", "清明预置");
            range("2027-05-01", "2027-05-03", "劳动节预置");
            day("2027-06-09", "端午预置");
            day("2027-09-15", "中秋预置");
            range("2027-10-01", "2027-10-07", "国庆预置");
        }

        HolidayInfo info(LocalDate date) {
            if (workdays.contains(date)) return new HolidayInfo("调休上班", false, true);
            String festival = holidays.get(date);
            if (festival != null) return new HolidayInfo(festival, true, false);
            DayOfWeek day = date.getDayOfWeek();
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                return new HolidayInfo("周末", true, false);
            }
            return new HolidayInfo("工作日", false, false);
        }

        private void range(String start, String end, String label) {
            LocalDate date = LocalDate.parse(start);
            LocalDate last = LocalDate.parse(end);
            while (!date.isAfter(last)) {
                holidays.put(date, label);
                date = date.plusDays(1);
            }
        }

        private void day(String date, String label) {
            holidays.put(LocalDate.parse(date), label);
        }

        private void work(String date) {
            workdays.add(LocalDate.parse(date));
        }
    }

    public static class CalendarMonthView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private YearMonth month = YearMonth.of(2026, 1);
        private LocalDate selected = LocalDate.of(2026, 1, 1);
        private CycleConfig cycle;
        private HolidayStore holidays;
        private OnDateSelected listener;

        public CalendarMonthView(Context context) {
            super(context);
            setBackgroundColor(Color.rgb(250, 252, 250));
        }

        void setMonth(YearMonth month, LocalDate selected, CycleConfig cycle, HolidayStore holidays) {
            this.month = month;
            this.selected = selected;
            this.cycle = cycle;
            this.holidays = holidays;
            invalidate();
        }

        void setOnDateSelected(OnDateSelected listener) {
            this.listener = listener;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float header = 34f;
            float cellW = width / 7f;
            float cellH = (getHeight() - header) / 6f;
            String[] week = {"日", "一", "二", "三", "四", "五", "六"};
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(30f);
            paint.setColor(Color.rgb(88, 97, 93));
            for (int i = 0; i < 7; i++) {
                canvas.drawText(week[i], cellW * i + cellW / 2f, 26f, paint);
            }

            LocalDate first = month.atDay(1);
            int firstIndex = first.getDayOfWeek().getValue() % 7;
            int days = month.lengthOfMonth();
            for (int day = 1; day <= days; day++) {
                int pos = firstIndex + day - 1;
                int row = pos / 7;
                int col = pos % 7;
                float left = col * cellW;
                float top = header + row * cellH;
                LocalDate date = month.atDay(day);
                drawCell(canvas, date, left, top, cellW, cellH);
            }
        }

        private void drawCell(Canvas canvas, LocalDate date, float left, float top, float w, float h) {
            HolidayInfo info = holidays.info(date);
            boolean isSelected = date.equals(selected);
            RectF rect = new RectF(left + 4, top + 4, left + w - 4, top + h - 4);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(isSelected ? Color.rgb(221, 241, 235) : Color.WHITE);
            canvas.drawRoundRect(rect, 10, 10, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(isSelected ? GREEN : Color.rgb(222, 228, 224));
            canvas.drawRoundRect(rect, 10, 10, paint);
            paint.setStyle(Paint.Style.FILL);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(32f);
            paint.setColor(info.adjustedWorkday ? AMBER : info.holiday ? RED : Color.rgb(32, 42, 38));
            canvas.drawText(String.valueOf(date.getDayOfMonth()), left + w / 2f, top + 33f, paint);

            paint.setTextSize(20f);
            paint.setColor(info.adjustedWorkday ? AMBER : info.holiday ? RED : Color.rgb(104, 112, 108));
            canvas.drawText(info.label, left + w / 2f, top + 58f, paint);

            paint.setTextSize(22f);
            paint.setColor(GREEN);
            canvas.drawText(cycle.shiftFor(date), left + w / 2f, top + h - 16f, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            float header = 34f;
            if (event.getY() < header) return true;
            float cellW = getWidth() / 7f;
            float cellH = (getHeight() - header) / 6f;
            int col = (int) (event.getX() / cellW);
            int row = (int) ((event.getY() - header) / cellH);
            LocalDate first = month.atDay(1);
            int firstIndex = first.getDayOfWeek().getValue() % 7;
            int day = row * 7 + col - firstIndex + 1;
            if (day >= 1 && day <= month.lengthOfMonth() && listener != null) {
                listener.onSelect(month.atDay(day));
            }
            return true;
        }
    }

    interface OnDateSelected {
        void onSelect(LocalDate date);
    }
}
