package com.jlxc.vehicledataprobe;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.text.method.ScrollingMovementMethod;
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

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String ACTION_CAR_INFO = "com.ts.can.carinfo.CarInfoService";
    private static final String PKG_MAIN_UI = "com.ts.MainUI";
    private static final String CLS_CAR_INFO = "com.ts.can.carinfo.CarInfoService";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ICarInfoService carInfoService;
    private boolean bound = false;
    private boolean polling = false;

    private TextView statusView;
    private TextView logView;
    private EditText intervalEdit;
    private EditText rawParaEdit;
    private EditText infoStrEdit;
    private EditText infoParaEdit;
    private CheckBox includeT3Box;

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            if (!polling) return;
            readSafeOnce();
            int interval = getIntervalMs();
            handler.postDelayed(this, interval);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            carInfoService = ICarInfoService.Stub.asInterface(service);
            bound = true;
            setStatus("已绑定 CarInfoService: " + name.flattenToShortString());
            appendLog("BIND OK: " + name.flattenToShortString());
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            carInfoService = null;
            bound = false;
            polling = false;
            setStatus("服务断开");
            appendLog("BIND DISCONNECTED: " + name.flattenToShortString());
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
    }

    @Override protected void onDestroy() {
        stopPolling();
        if (bound) {
            try { unbindService(connection); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.setBackgroundColor(Color.rgb(14, 18, 22));

        TextView title = new TextView(this);
        title.setText("车辆数据探针 VehicleDataProbe");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));

        statusView = new TextView(this);
        statusView.setText("未绑定");
        statusView.setTextColor(Color.rgb(150, 255, 220));
        statusView.setTextSize(14);
        root.addView(statusView, new LinearLayout.LayoutParams(-1, dp(30)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout row1 = row();
        row1.addView(button("绑定服务", v -> bindCarInfoService()));
        row1.addView(button("解绑", v -> unbindCarInfoService()));
        row1.addView(button("安全读一次", v -> readSafeOnce()));
        content.addView(row1);

        LinearLayout row2 = row();
        intervalEdit = edit("1000");
        row2.addView(label("轮询ms"));
        row2.addView(intervalEdit, new LinearLayout.LayoutParams(dp(120), dp(46)));
        row2.addView(button("开始低频轮询", v -> startPolling()));
        row2.addView(button("停止轮询", v -> stopPolling()));
        content.addView(row2);

        includeT3Box = new CheckBox(this);
        includeT3Box.setText("轮询时也读取 T3 状态/外设信息（默认关闭，先不要乱开）");
        includeT3Box.setTextColor(Color.WHITE);
        includeT3Box.setTextSize(14);
        content.addView(includeT3Box, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView hint = new TextView(this);
        hint.setText("安全读一次会读取：车门数组、大灯/照明状态、基础数组、左右温度。建议先 1000ms 轮询，不要高频。下面是手动接口，先单次测试。 ");
        hint.setTextColor(Color.rgb(210, 220, 230));
        hint.setTextSize(13);
        content.addView(hint);

        LinearLayout row3 = row();
        rawParaEdit = edit("0");
        row3.addView(label("CAN para"));
        row3.addView(rawParaEdit, new LinearLayout.LayoutParams(dp(110), dp(46)));
        row3.addView(button("requestCanRecevieData 单次", v -> readCanReceiveOnce()));
        content.addView(row3);

        LinearLayout row4 = row();
        infoStrEdit = edit("");
        infoParaEdit = edit("0");
        row4.addView(label("str"));
        row4.addView(infoStrEdit, new LinearLayout.LayoutParams(dp(220), dp(46)));
        row4.addView(label("para"));
        row4.addView(infoParaEdit, new LinearLayout.LayoutParams(dp(100), dp(46)));
        row4.addView(button("requestInfo", v -> requestInfoOnce()));
        row4.addView(button("requestBaseInfo2", v -> requestBaseInfo2Once()));
        content.addView(row4);

        logView = new TextView(this);
        logView.setTextColor(Color.rgb(230, 235, 240));
        logView.setTextSize(13);
        logView.setTextIsSelectable(true);
        logView.setMovementMethod(new ScrollingMovementMethod());
        logView.setBackgroundColor(Color.rgb(4, 7, 10));
        logView.setPadding(dp(8), dp(8), dp(8), dp(8));
        content.addView(logView, new LinearLayout.LayoutParams(-1, dp(520)));

        setContentView(root);
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, dp(3), 0, dp(3));
        return l;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1);
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
        v.setPadding(dp(6), 0, dp(6), 0);
        return v;
    }

    private EditText edit(String text) {
        EditText e = new EditText(this);
        e.setText(text);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        e.setBackgroundColor(Color.rgb(34, 42, 50));
        e.setPadding(dp(8), 0, dp(8), 0);
        return e;
    }

    private void bindCarInfoService() {
        if (bound) {
            appendLog("已经绑定，无需重复绑定");
            return;
        }
        Intent intent = new Intent(ACTION_CAR_INFO);
        intent.setPackage(PKG_MAIN_UI);
        intent.setClassName(PKG_MAIN_UI, CLS_CAR_INFO);
        boolean ok;
        try {
            ok = bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (Throwable t) {
            appendLog("bindService 异常: " + t);
            return;
        }
        appendLog("bindService result=" + ok + ", action=" + ACTION_CAR_INFO);
        setStatus(ok ? "正在绑定..." : "绑定失败：bindService 返回 false");
    }

    private void unbindCarInfoService() {
        stopPolling();
        if (bound) {
            try { unbindService(connection); } catch (Exception e) { appendLog("unbind 异常: " + e); }
        }
        bound = false;
        carInfoService = null;
        setStatus("未绑定");
    }

    private void startPolling() {
        if (!ensureService()) return;
        polling = true;
        handler.removeCallbacks(pollRunnable);
        handler.post(pollRunnable);
        appendLog("开始轮询，interval=" + getIntervalMs() + "ms");
    }

    private void stopPolling() {
        polling = false;
        handler.removeCallbacks(pollRunnable);
        appendLog("停止轮询");
    }

    private int getIntervalMs() {
        int v = parseInt(intervalEdit.getText().toString(), 1000);
        if (v < 500) v = 500;
        if (v > 10000) v = 10000;
        return v;
    }

    private boolean ensureService() {
        if (!bound || carInfoService == null) {
            appendLog("未绑定 CarInfoService，请先点：绑定服务");
            return false;
        }
        return true;
    }

    private void readSafeOnce() {
        if (!ensureService()) return;
        appendLog("---- SAFE READ BEGIN ----");
        tryCall("requestCarDoorInfo", () -> arr(carInfoService.requestCarDoorInfo()));
        tryCall("requestCarIllInfo", () -> String.valueOf(carInfoService.requestCarIllInfo()));
        tryCall("requestCarBaseInfo", () -> arr(carInfoService.requestCarBaseInfo()));
        tryCall("requestCarAirLtTemp", () -> String.valueOf(carInfoService.requestCarAirLtTemp()));
        tryCall("requestCarAirRtTemp", () -> String.valueOf(carInfoService.requestCarAirRtTemp()));
        if (includeT3Box.isChecked()) {
            tryCall("requestT3FlSta", () -> String.valueOf(carInfoService.requestT3FlSta()));
            tryCall("requestT3FlDevInfo", () -> arr(carInfoService.requestT3FlDevInfo()));
        }
        appendLog("---- SAFE READ END ----");
    }

    private void readCanReceiveOnce() {
        if (!ensureService()) return;
        int para = parseInt(rawParaEdit.getText().toString(), 0);
        tryCall("requestCanRecevieData(" + para + ")", () -> arr(carInfoService.requestCanRecevieData(para)));
    }

    private void requestInfoOnce() {
        if (!ensureService()) return;
        String str = infoStrEdit.getText().toString();
        int para = parseInt(infoParaEdit.getText().toString(), 0);
        tryCall("requestInfo(\"" + str + "\", " + para + ")", () -> String.valueOf(carInfoService.requestInfo(str, para)));
    }

    private void requestBaseInfo2Once() {
        if (!ensureService()) return;
        String str = infoStrEdit.getText().toString();
        int para = parseInt(infoParaEdit.getText().toString(), 0);
        tryCall("requestCarBaseInfo2(\"" + str + "\", " + para + ")", () -> String.valueOf(carInfoService.requestCarBaseInfo2(str, para)));
    }

    private interface RemoteCallable { String call() throws RemoteException; }

    private void tryCall(String name, RemoteCallable callable) {
        long start = System.currentTimeMillis();
        try {
            String result = callable.call();
            appendLog(name + " => " + result + "  (" + (System.currentTimeMillis() - start) + "ms)");
        } catch (Throwable t) {
            appendLog(name + " !! " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private String arr(int[] data) {
        if (data == null) return "null";
        return Arrays.toString(data);
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private void setStatus(String s) { statusView.setText(s); }

    private void appendLog(String msg) {
        String t = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        logView.append(t + "  " + msg + "\n");
        final int scrollAmount = logView.getLayout() == null ? 0 : logView.getLayout().getLineTop(logView.getLineCount()) - logView.getHeight();
        if (scrollAmount > 0) logView.scrollTo(0, scrollAmount); else logView.scrollTo(0, 0);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
