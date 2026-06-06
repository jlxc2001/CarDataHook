package com.jlxc.vehicledatacore;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ts.can.carinfo.ICarInfoService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PKG_MAIN_UI = "com.ts.MainUI";

    private static final String ACTION_CAR_INFO = "com.ts.can.carinfo.CarInfoService";
    private static final String CLS_CAR_INFO = "com.ts.can.carinfo.CarInfoService";

    private static final String ACTION_SPEECH_CAR = "com.ts.tsspeechlib.car.TsCarService";
    private static final String CLS_SPEECH_CAR = "com.ts.tsspeechlib.car.TsCarService";
    private static final String TOKEN_SPEECH_CAR = "com.ts.tsspeechlib.car.ITsSpeechCar";

    private static final String ACTION_COMMON = "android.intent.action.MAIN_UI";
    private static final String CLS_COMMON = "com.ts.main.common.MainUI";
    private static final String TOKEN_COMMON = "com.ts.main.common.ITsCommon";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private ICarInfoService carInfoService;
    private IBinder speechBinder;
    private IBinder commonBinder;

    private boolean carInfoBound;
    private boolean speechBound;
    private boolean commonBound;
    private boolean polling;
    private boolean recording;

    private long pollCount;
    private VehicleState lastState;
    private final ArrayList<JSONObject> recordFrames = new ArrayList<>();

    private TextView statusView;
    private TextView stateView;
    private TextView rawView;
    private TextView changeLogView;
    private EditText intervalEdit;
    private CheckBox simulateBox;
    private CheckBox includeSpeechBox;
    private CheckBox includeCommonBox;

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            if (!polling) return;
            readOnce(false);
            handler.postDelayed(this, getIntervalMs());
        }
    };

    private final ServiceConnection carInfoConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            carInfoService = ICarInfoService.Stub.asInterface(service);
            carInfoBound = true;
            updateStatus();
            appendChange("CarInfoService 已绑定: " + name.flattenToShortString());
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            carInfoService = null;
            carInfoBound = false;
            updateStatus();
            appendChange("CarInfoService 断开: " + name.flattenToShortString());
        }
    };

    private final ServiceConnection speechConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            speechBinder = service;
            speechBound = true;
            updateStatus();
            appendChange("TsCarService 已绑定: " + name.flattenToShortString());
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            speechBinder = null;
            speechBound = false;
            updateStatus();
            appendChange("TsCarService 断开: " + name.flattenToShortString());
        }
    };

    private final ServiceConnection commonConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            commonBinder = service;
            commonBound = true;
            updateStatus();
            appendChange("MainUI Common 已绑定: " + name.flattenToShortString());
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            commonBinder = null;
            commonBound = false;
            updateStatus();
            appendChange("MainUI Common 断开: " + name.flattenToShortString());
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        buildUi();
        updateStatus();
    }

    @Override protected void onDestroy() {
        stopPolling();
        unbindAll();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.setBackgroundColor(Color.rgb(8, 12, 16));

        TextView title = new TextView(this);
        title.setText("VehicleDataCore Demo  车辆数据核心测试");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(40)));

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(120, 255, 220));
        statusView.setTextSize(13);
        root.addView(statusView, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout row1 = row();
        row1.addView(button("绑定全部服务", v -> bindAll()));
        row1.addView(button("解绑", v -> unbindAll()));
        row1.addView(button("安全读一次", v -> readOnce(true)));
        row1.addView(button("开始读取", v -> startPolling()));
        row1.addView(button("停止读取", v -> stopPolling()));
        root.addView(row1);

        LinearLayout row2 = row();
        row2.addView(label("轮询ms"));
        intervalEdit = edit("1000");
        row2.addView(intervalEdit, new LinearLayout.LayoutParams(dp(110), dp(44)));
        simulateBox = check("模拟数据模式");
        includeSpeechBox = check("读取 TsCarService：雷达/双闪/总里程");
        includeSpeechBox.setChecked(true);
        includeCommonBox = check("读取 Common：备用车速/航向");
        includeCommonBox.setChecked(false);
        row2.addView(simulateBox, new LinearLayout.LayoutParams(dp(150), dp(44)));
        row2.addView(includeSpeechBox, new LinearLayout.LayoutParams(dp(300), dp(44)));
        row2.addView(includeCommonBox, new LinearLayout.LayoutParams(dp(240), dp(44)));
        root.addView(row2);

        LinearLayout row3 = row();
        row3.addView(button("开始记录", v -> startRecording()));
        row3.addView(button("停止并导出记录", v -> stopAndExportRecording()));
        row3.addView(button("导出当前 JSON", v -> exportCurrentJson()));
        row3.addView(button("清空变化日志", v -> changeLogView.setText("")));
        root.addView(row3);

        TextView hint = new TextView(this);
        hint.setText("说明：本 Demo 不调用高风险 requestCarDoorInfo()。第一版只读取已确认有效字段。建议 1000ms 轮询；需要测试 UI 时可打开模拟模式。");
        hint.setTextColor(Color.rgb(210, 220, 230));
        hint.setTextSize(13);
        root.addView(hint, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout panels = new LinearLayout(this);
        panels.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(panels, new LinearLayout.LayoutParams(-1, 0, 1));

        stateView = panelText(18, Color.rgb(235, 245, 250));
        rawView = panelText(12, Color.rgb(180, 220, 255));
        changeLogView = panelText(12, Color.rgb(255, 230, 180));

        panels.addView(wrap("解析后的 VehicleState", stateView), new LinearLayout.LayoutParams(0, -1, 1.0f));
        panels.addView(wrap("原始数据 / JSON", rawView), new LinearLayout.LayoutParams(0, -1, 1.15f));
        panels.addView(wrap("字段变化日志", changeLogView), new LinearLayout.LayoutParams(0, -1, 0.9f));

        setContentView(root);
    }

    private LinearLayout wrap(String title, TextView view) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(4), dp(4), dp(4), dp(4));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(14);
        box.addView(t, new LinearLayout.LayoutParams(-1, dp(26)));
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.rgb(2, 6, 10));
        sv.addView(view);
        box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        return box;
    }

    private TextView panelText(int size, int color) {
        TextView tv = new TextView(this);
        tv.setTextColor(color);
        tv.setTextSize(size);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(8), dp(8), dp(8), dp(8));
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        return tv;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, dp(2), 0, dp(2));
        return l;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(13);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private EditText edit(String text) {
        EditText e = new EditText(this);
        e.setText(text);
        e.setSingleLine(true);
        e.setTextSize(14);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        e.setBackgroundColor(Color.rgb(35, 45, 55));
        e.setPadding(dp(8), 0, dp(8), 0);
        return e;
    }

    private CheckBox check(String text) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setTextColor(Color.WHITE);
        c.setTextSize(13);
        return c;
    }

    private void bindAll() {
        bindCarInfo();
        bindSpeech();
        bindCommon();
    }

    private void bindCarInfo() {
        if (carInfoBound) return;
        Intent intent = new Intent(ACTION_CAR_INFO);
        intent.setPackage(PKG_MAIN_UI);
        intent.setClassName(PKG_MAIN_UI, CLS_CAR_INFO);
        try {
            boolean ok = bindService(intent, carInfoConnection, Context.BIND_AUTO_CREATE);
            appendChange("bind CarInfoService result=" + ok);
        } catch (Throwable t) {
            appendChange("bind CarInfoService 异常: " + t);
        }
    }

    private void bindSpeech() {
        if (speechBound) return;
        Intent intent = new Intent(ACTION_SPEECH_CAR);
        intent.setPackage(PKG_MAIN_UI);
        intent.setClassName(PKG_MAIN_UI, CLS_SPEECH_CAR);
        try {
            boolean ok = bindService(intent, speechConnection, Context.BIND_AUTO_CREATE);
            appendChange("bind TsCarService result=" + ok);
        } catch (Throwable t) {
            appendChange("bind TsCarService 异常: " + t);
        }
    }

    private void bindCommon() {
        if (commonBound) return;
        Intent intent = new Intent(ACTION_COMMON);
        intent.setPackage(PKG_MAIN_UI);
        intent.setClassName(PKG_MAIN_UI, CLS_COMMON);
        try {
            boolean ok = bindService(intent, commonConnection, Context.BIND_AUTO_CREATE);
            appendChange("bind MainUI Common result=" + ok);
        } catch (Throwable t) {
            appendChange("bind MainUI Common 异常: " + t);
        }
    }

    private void unbindAll() {
        stopPolling();
        try { if (carInfoBound) unbindService(carInfoConnection); } catch (Throwable ignored) {}
        try { if (speechBound) unbindService(speechConnection); } catch (Throwable ignored) {}
        try { if (commonBound) unbindService(commonConnection); } catch (Throwable ignored) {}
        carInfoBound = false;
        speechBound = false;
        commonBound = false;
        carInfoService = null;
        speechBinder = null;
        commonBinder = null;
        updateStatus();
    }

    private void startPolling() {
        if (!simulateBox.isChecked() && !carInfoBound) {
            bindAll();
        }
        polling = true;
        handler.removeCallbacks(pollRunnable);
        handler.post(pollRunnable);
        appendChange("开始读取，interval=" + getIntervalMs() + "ms");
    }

    private void stopPolling() {
        polling = false;
        handler.removeCallbacks(pollRunnable);
    }

    private int getIntervalMs() {
        int v = parseInt(intervalEdit.getText().toString(), 1000);
        if (v < 500) v = 500;
        if (v > 10000) v = 10000;
        return v;
    }

    private void readOnce(boolean userClick) {
        long start = System.currentTimeMillis();
        VehicleState state;
        int[] base = null;
        int[] frontRadar = null;
        int[] rearRadar = null;
        StringBuilder raw = new StringBuilder();

        if (simulateBox.isChecked()) {
            state = VehicleState.simulated(pollCount);
            base = state.fakeBase;
            frontRadar = state.frontRadar;
            rearRadar = state.rearRadar;
        } else {
            if (!carInfoBound || carInfoService == null) {
                if (userClick) appendChange("CarInfoService 未绑定，请先绑定全部服务");
                return;
            }
            try {
                base = carInfoService.requestCarBaseInfo();
                raw.append("requestCarBaseInfo length=").append(base == null ? "null" : base.length).append("\n");
            } catch (Throwable t) {
                appendChange("requestCarBaseInfo 异常: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }

            SpeechValues speech = null;
            if (includeSpeechBox.isChecked() && speechBound && speechBinder != null) {
                speech = readSpeechValues();
                frontRadar = speech.frontRadar;
                rearRadar = speech.rearRadar;
            }

            CommonValues common = null;
            if (includeCommonBox.isChecked() && commonBound && commonBinder != null) {
                common = readCommonValues();
            }

            state = VehicleState.from(base, speech, common);
        }

        pollCount++;
        long took = System.currentTimeMillis() - start;
        state.pollCount = pollCount;
        state.readTimeMs = took;
        state.timestampMs = System.currentTimeMillis();

        if (recording) {
            try { recordFrames.add(state.toJson()); } catch (Exception ignored) {}
        }

        updateViews(state, base, frontRadar, rearRadar);
        logChanges(lastState, state);
        lastState = state;
    }

    private SpeechValues readSpeechValues() {
        SpeechValues s = new SpeechValues();
        s.totalMileageKm = transactInt(speechBinder, TOKEN_SPEECH_CAR, 17, null);
        s.oilLeftover = transactInt(speechBinder, TOKEN_SPEECH_CAR, 18, null);
        s.leftTurn = transactInt(speechBinder, TOKEN_SPEECH_CAR, 19, null);
        s.rightTurn = transactInt(speechBinder, TOKEN_SPEECH_CAR, 20, null);
        s.hazard = transactInt(speechBinder, TOKEN_SPEECH_CAR, 21, null);
        s.speed = transactInt(speechBinder, TOKEN_SPEECH_CAR, 22, null);
        s.lineEps = transactInt(speechBinder, TOKEN_SPEECH_CAR, 23, null);
        s.frontRadar = transactIntArray(speechBinder, TOKEN_SPEECH_CAR, 25, "frontRadar");
        s.rearRadar = transactIntArray(speechBinder, TOKEN_SPEECH_CAR, 26, "rearRadar");
        s.mcuPowerState = transactInt(speechBinder, TOKEN_SPEECH_CAR, 28, null);
        return s;
    }

    private CommonValues readCommonValues() {
        CommonValues c = new CommonValues();
        c.reverse = transactInt(commonBinder, TOKEN_COMMON, 8, null);
        c.brake = transactInt(commonBinder, TOKEN_COMMON, 9, null);
        c.speedFloat = transactFloat(commonBinder, TOKEN_COMMON, 23, null);
        c.temp = transactInt(commonBinder, TOKEN_COMMON, 24, null);
        c.cog = transactFloat(commonBinder, TOKEN_COMMON, 25, null);
        c.mcuPowerState = transactInt(commonBinder, TOKEN_COMMON, 54, null);
        return c;
    }

    private Integer transactInt(IBinder binder, String token, int code, String name) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(token);
            boolean ok = binder.transact(code, data, reply, 0);
            if (!ok) return null;
            reply.readException();
            return reply.readInt();
        } catch (Throwable t) {
            if (name != null) appendChange(name + " transactInt code=" + code + " 异常: " + t.getClass().getSimpleName());
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private Float transactFloat(IBinder binder, String token, int code, String name) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(token);
            boolean ok = binder.transact(code, data, reply, 0);
            if (!ok) return null;
            reply.readException();
            return reply.readFloat();
        } catch (Throwable t) {
            if (name != null) appendChange(name + " transactFloat code=" + code + " 异常: " + t.getClass().getSimpleName());
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private int[] transactIntArray(IBinder binder, String token, int code, String name) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(token);
            boolean ok = binder.transact(code, data, reply, 0);
            if (!ok) return null;
            reply.readException();
            return reply.createIntArray();
        } catch (Throwable t) {
            if (name != null) appendChange(name + " transactIntArray code=" + code + " 异常: " + t.getClass().getSimpleName());
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void updateViews(VehicleState s, int[] base, int[] frontRadar, int[] rearRadar) {
        stateView.setText(s.toHumanText());
        StringBuilder raw = new StringBuilder();
        raw.append("poll=").append(s.pollCount).append("  took=").append(s.readTimeMs).append("ms\n");
        raw.append("time=").append(formatTime(s.timestampMs)).append("\n\n");
        raw.append("VehicleState JSON:\n").append(s.toJsonSafe()).append("\n\n");
        raw.append("baseInfo:\n").append(base == null ? "null" : Arrays.toString(base)).append("\n\n");
        raw.append("frontRadar:\n").append(frontRadar == null ? "null" : Arrays.toString(frontRadar)).append("\n\n");
        raw.append("rearRadar:\n").append(rearRadar == null ? "null" : Arrays.toString(rearRadar)).append("\n");
        rawView.setText(raw.toString());
        updateStatus();
    }

    private void updateStatus() {
        statusView.setText("CarInfo=" + (carInfoBound ? "OK" : "--")
                + " | TsCar=" + (speechBound ? "OK" : "--")
                + " | Common=" + (commonBound ? "OK" : "--")
                + " | polling=" + polling
                + " | recording=" + recording
                + " | frames=" + recordFrames.size());
    }

    private void logChanges(VehicleState old, VehicleState now) {
        if (old == null) {
            appendChange("首帧: speed=" + now.speedKmh + ", rpm=" + now.rpm + ", valid=" + now.valid);
            return;
        }
        compare("车速", old.speedKmh, now.speedKmh);
        compare("转速", old.rpm, now.rpm);
        compare("主驾安全带", old.driverSeatbelt, now.driverSeatbelt);
        compare("副驾安全带", old.passengerSeatbelt, now.passengerSeatbelt);
        compare("左前门", old.frontLeftDoorOpen, now.frontLeftDoorOpen);
        compare("右前门", old.frontRightDoorOpen, now.frontRightDoorOpen);
        compare("左后门", old.rearLeftDoorOpen, now.rearLeftDoorOpen);
        compare("右后门", old.rearRightDoorOpen, now.rearRightDoorOpen);
        compare("后备箱", old.trunkOpen, now.trunkOpen);
        compare("前备箱/机盖", old.hoodOpen, now.hoodOpen);
        compare("左转向", old.leftTurn, now.leftTurn);
        compare("右转向", old.rightTurn, now.rightTurn);
        compare("远光灯", old.highBeam, now.highBeam);
        compare("双闪", old.hazard, now.hazard);
        compare("油量", old.fuelLevel, now.fuelLevel);
        compare("续航", old.rangeKm, now.rangeKm);
        compare("总里程", old.totalMileageKm, now.totalMileageKm);
        if (!Arrays.equals(old.frontRadar, now.frontRadar)) appendChange("前雷达: " + arr(old.frontRadar) + " → " + arr(now.frontRadar));
        if (!Arrays.equals(old.rearRadar, now.rearRadar)) appendChange("后雷达: " + arr(old.rearRadar) + " → " + arr(now.rearRadar));
    }

    private void compare(String name, Object a, Object b) {
        if (a == null && b == null) return;
        if (a == null || b == null || !a.equals(b)) appendChange(name + ": " + a + " → " + b);
    }

    private void appendChange(String text) {
        if (changeLogView == null) return;
        String line = formatTime(System.currentTimeMillis()) + "  " + text + "\n";
        changeLogView.append(line);
        final int max = 16000;
        if (changeLogView.length() > max) {
            changeLogView.setText(changeLogView.getText().subSequence(changeLogView.length() - max, changeLogView.length()));
        }
    }

    private void startRecording() {
        recordFrames.clear();
        recording = true;
        updateStatus();
        appendChange("开始记录 VehicleState JSON");
    }

    private void stopAndExportRecording() {
        recording = false;
        updateStatus();
        try {
            JSONArray arr = new JSONArray();
            for (JSONObject o : recordFrames) arr.put(o);
            String path = writeText("vehicle_record_" + fileTime() + ".json", arr.toString(2));
            appendChange("记录已导出: " + path);
        } catch (Throwable t) {
            appendChange("导出记录失败: " + t);
        }
    }

    private void exportCurrentJson() {
        if (lastState == null) {
            appendChange("当前没有 VehicleState 可导出");
            return;
        }
        try {
            String path = writeText("vehicle_state_" + fileTime() + ".json", lastState.toJson().toString(2));
            appendChange("当前 JSON 已导出: " + path);
        } catch (Throwable t) {
            appendChange("导出当前 JSON 失败: " + t);
        }
    }

    private String writeText(String name, String text) throws Exception {
        File dir = new File(Environment.getExternalStorageDirectory(), "VehicleDataCoreDemo");
        if (!dir.exists() && !dir.mkdirs()) {
            dir = new File(getExternalFilesDir(null), "export");
            if (!dir.exists()) dir.mkdirs();
        }
        File file = new File(dir, name);
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(text.getBytes(StandardCharsets.UTF_8));
        fos.close();
        return file.getAbsolutePath();
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private String formatTime(long t) { return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date(t)); }
    private String fileTime() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }
    private String arr(int[] a) { return a == null ? "null" : Arrays.toString(a); }

    private static class SpeechValues {
        Integer totalMileageKm;
        Integer oilLeftover;
        Integer leftTurn;
        Integer rightTurn;
        Integer hazard;
        Integer speed;
        Integer lineEps;
        int[] frontRadar;
        int[] rearRadar;
        Integer mcuPowerState;
    }

    private static class CommonValues {
        Integer reverse;
        Integer brake;
        Float speedFloat;
        Integer temp;
        Float cog;
        Integer mcuPowerState;
    }

    private static class VehicleState {
        boolean valid;
        int speedKmh;
        int rpm;
        Boolean driverSeatbelt;
        Boolean passengerSeatbelt;
        Boolean frontLeftDoorOpen;
        Boolean frontRightDoorOpen;
        Boolean rearLeftDoorOpen;
        Boolean rearRightDoorOpen;
        Boolean trunkOpen;
        Boolean hoodOpen;
        Boolean leftTurn;
        Boolean rightTurn;
        Boolean highBeam;
        Boolean hazard;
        Integer totalMileageKm;
        Integer fuelLevel;
        Integer rangeKm;
        int[] frontRadar;
        int[] rearRadar;
        int[] fakeBase;
        long pollCount;
        long timestampMs;
        long readTimeMs;
        String note;

        static VehicleState from(int[] base, SpeechValues speech, CommonValues common) {
            VehicleState s = new VehicleState();
            if (base != null && base.length > 0) s.valid = base[0] == 1;
            else s.valid = false;

            s.speedKmh = get(base, 2, speech != null && speech.speed != null ? speech.speed : 0);
            s.rpm = get(base, 3, 0);
            s.rangeKm = nullableInt(base, 13);
            s.leftTurn = bool(base, 17);
            s.rightTurn = bool(base, 18);
            s.driverSeatbelt = bool(base, 19);
            s.highBeam = bool(base, 20);
            s.fuelLevel = nullableInt(base, 30);
            s.passengerSeatbelt = bool(base, 36);
            s.frontLeftDoorOpen = bool(base, 61);
            s.frontRightDoorOpen = bool(base, 62);
            s.rearLeftDoorOpen = bool(base, 63);
            s.rearRightDoorOpen = bool(base, 64);
            s.trunkOpen = bool(base, 65);
            s.hoodOpen = bool(base, 66);

            if (speech != null) {
                if (speech.leftTurn != null) s.leftTurn = speech.leftTurn == 1;
                if (speech.rightTurn != null) s.rightTurn = speech.rightTurn == 1;
                if (speech.hazard != null) s.hazard = speech.hazard == 1;
                if (speech.totalMileageKm != null) s.totalMileageKm = speech.totalMileageKm;
                if (speech.oilLeftover != null && s.fuelLevel == null) s.fuelLevel = speech.oilLeftover;
                s.frontRadar = speech.frontRadar;
                s.rearRadar = speech.rearRadar;
            }
            if (common != null && common.speedFloat != null && s.speedKmh == 0) {
                s.speedKmh = Math.round(common.speedFloat);
            }
            return s;
        }

        static VehicleState simulated(long n) {
            VehicleState s = new VehicleState();
            s.valid = true;
            double wave = (Math.sin(n / 8.0) + 1.0) / 2.0;
            s.speedKmh = (int)Math.round(wave * 80);
            s.rpm = 800 + s.speedKmh * 45;
            s.driverSeatbelt = n % 40 < 32;
            s.passengerSeatbelt = n % 60 < 48;
            s.frontLeftDoorOpen = n % 50 >= 5 && n % 50 <= 10;
            s.frontRightDoorOpen = n % 70 >= 10 && n % 70 <= 15;
            s.rearLeftDoorOpen = false;
            s.rearRightDoorOpen = false;
            s.trunkOpen = n % 90 >= 10 && n % 90 <= 18;
            s.hoodOpen = false;
            s.leftTurn = n % 24 < 4;
            s.rightTurn = n % 24 >= 12 && n % 24 < 16;
            s.highBeam = n % 80 > 60;
            s.hazard = n % 100 >= 85;
            s.totalMileageKm = 114487;
            s.fuelLevel = 52;
            s.rangeKm = 500 - (int)(n % 80);
            s.frontRadar = new int[]{120 - (int)(n % 60), 95, 80, 110};
            s.rearRadar = new int[]{150, 130 - (int)(n % 50), 140, 160};
            s.fakeBase = new int[82];
            s.fakeBase[0] = 1;
            s.fakeBase[2] = s.speedKmh;
            s.fakeBase[3] = s.rpm;
            s.fakeBase[13] = s.rangeKm;
            s.fakeBase[17] = s.leftTurn ? 1 : 0;
            s.fakeBase[18] = s.rightTurn ? 1 : 0;
            s.fakeBase[19] = s.driverSeatbelt ? 1 : 0;
            s.fakeBase[20] = s.highBeam ? 1 : 0;
            s.fakeBase[30] = s.fuelLevel;
            s.fakeBase[36] = s.passengerSeatbelt ? 1 : 0;
            s.fakeBase[61] = s.frontLeftDoorOpen ? 1 : 0;
            s.fakeBase[62] = s.frontRightDoorOpen ? 1 : 0;
            s.fakeBase[63] = s.rearLeftDoorOpen ? 1 : 0;
            s.fakeBase[64] = s.rearRightDoorOpen ? 1 : 0;
            s.fakeBase[65] = s.trunkOpen ? 1 : 0;
            s.fakeBase[66] = s.hoodOpen ? 1 : 0;
            s.note = "模拟数据";
            return s;
        }

        private static int get(int[] base, int index, int def) {
            return base != null && base.length > index ? base[index] : def;
        }
        private static Integer nullableInt(int[] base, int index) {
            return base != null && base.length > index ? base[index] : null;
        }
        private static Boolean bool(int[] base, int index) {
            return base != null && base.length > index ? base[index] == 1 : null;
        }

        String toHumanText() {
            StringBuilder b = new StringBuilder();
            b.append("有效: ").append(valid).append("\n");
            b.append("车速: ").append(speedKmh).append(" km/h\n");
            b.append("转速: ").append(rpm).append(" rpm\n");
            b.append("续航: ").append(rangeKm).append(" km\n");
            b.append("油量: ").append(fuelLevel).append("\n");
            b.append("总里程: ").append(totalMileageKm).append(" km\n\n");
            b.append("主驾安全带: ").append(driverSeatbelt).append("\n");
            b.append("副驾安全带: ").append(passengerSeatbelt).append("\n\n");
            b.append("左前门: ").append(frontLeftDoorOpen).append("\n");
            b.append("右前门: ").append(frontRightDoorOpen).append("\n");
            b.append("左后门: ").append(rearLeftDoorOpen).append("\n");
            b.append("右后门: ").append(rearRightDoorOpen).append("\n");
            b.append("后备箱: ").append(trunkOpen).append("\n");
            b.append("前备箱/机盖: ").append(hoodOpen).append("\n\n");
            b.append("左转向: ").append(leftTurn).append("\n");
            b.append("右转向: ").append(rightTurn).append("\n");
            b.append("远光灯: ").append(highBeam).append("\n");
            b.append("双闪: ").append(hazard).append("\n\n");
            b.append("前雷达: ").append(frontRadar == null ? "null" : Arrays.toString(frontRadar)).append("\n");
            b.append("后雷达: ").append(rearRadar == null ? "null" : Arrays.toString(rearRadar)).append("\n");
            if (note != null) b.append("\n备注: ").append(note).append("\n");
            return b.toString();
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("valid", valid);
            o.put("speedKmh", speedKmh);
            o.put("rpm", rpm);
            o.put("driverSeatbelt", value(driverSeatbelt));
            o.put("passengerSeatbelt", value(passengerSeatbelt));
            o.put("frontLeftDoorOpen", value(frontLeftDoorOpen));
            o.put("frontRightDoorOpen", value(frontRightDoorOpen));
            o.put("rearLeftDoorOpen", value(rearLeftDoorOpen));
            o.put("rearRightDoorOpen", value(rearRightDoorOpen));
            o.put("trunkOpen", value(trunkOpen));
            o.put("hoodOpen", value(hoodOpen));
            o.put("leftTurn", value(leftTurn));
            o.put("rightTurn", value(rightTurn));
            o.put("highBeam", value(highBeam));
            o.put("hazard", value(hazard));
            o.put("totalMileageKm", value(totalMileageKm));
            o.put("fuelLevel", value(fuelLevel));
            o.put("rangeKm", value(rangeKm));
            o.put("frontRadar", jsonArray(frontRadar));
            o.put("rearRadar", jsonArray(rearRadar));
            o.put("pollCount", pollCount);
            o.put("timestampMs", timestampMs);
            o.put("readTimeMs", readTimeMs);
            if (note != null) o.put("note", note);
            return o;
        }

        String toJsonSafe() {
            try { return toJson().toString(2); } catch (Throwable t) { return "{}"; }
        }

        private Object value(Object o) { return o == null ? JSONObject.NULL : o; }
        private JSONArray jsonArray(int[] a) {
            if (a == null) return null;
            JSONArray arr = new JSONArray();
            for (int v : a) arr.put(v);
            return arr;
        }
    }
}
