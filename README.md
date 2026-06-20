# Miku VVVF Sound V8 - Hook Smooth

车速绑定 VVVF / 发动机 / 飞机声浪 Demo。

## V8 重点

- 默认启用 **MainApp Hook 数据源**。
- 绑定 `com.ts.MainUI/com.ts.can.carinfo.CarInfoService`。
- 读取 `ICarInfoService.requestCarBaseInfo()`：
  - `base[2]` = 车速 km/h
  - `base[3]` = 发动机转速 rpm
- 绑定 `com.ts.MainUI/com.ts.tsspeechlib.car.TsCarService`，仅作为车速兜底。
- 不调用 `requestCarDoorInfo()` / `GetCarDoorInfo()`。
- 轮询周期默认 500ms，低于 500ms 会自动钳制到 500ms。
- 新增音频侧平滑算法：Hook 低频数据不会直接硬跳到目标车速，而是用 0.72s 时间常数连续追随。
- UDP / ADB 手动输入保留为离车调试用；Hook 开启时，实车数据会覆盖手动 SPEED。

## 声音模式

- `SAMPLE_VVVF_0_140`：真实采样 VVVF，使用你提供的 0→140km/h WAV。
- `SIEMENS_GZ_GTO`：广东/广州地铁西门子 GTO 近似。
- `GTO` / `IGBT`
- `AIRCRAFT_TURBINE`
- `POP_BANG_TURBO`
- `NATURAL_ASPIRATED`
- `ROTARY`
- `SUPERCHARGED_V8`

## ADB

```bash
adb shell am broadcast -a com.jlxc.mikuvvvf.SET_STYLE --es style SAMPLE_VVVF_0_140
adb shell am broadcast -a com.jlxc.mikuvvvf.SET_HOOK --ez enabled true
adb shell am broadcast -a com.jlxc.mikuvvvf.SET_HOOK --ez enabled false
adb shell am broadcast -a com.jlxc.mikuvvvf.SET_SPEED --ef speed 45
```

## UDP

默认端口：47230

```text
HOOK 1
HOOK 0
POLL 500
SPEED 45
STATE 45 2200 0.35
STYLE SAMPLE_VVVF_0_140
PING
```

## GitHub Actions

上传到 GitHub 后运行 workflow 即可生成 APK。项目使用 Java 17 / AGP 8.7.3。
