# VehicleDataCoreDemo / 车辆数据核心 Demo

这是用于 JLXC A4L 车机 UI 项目的车辆数据读取测试 App。

## 目标

把车机系统中已确认可用的车辆信息整理成统一的 `VehicleState`，后续直接接入正式车机 Launcher / Dashboard。

## 当前数据源

### 主数据源：`ICarInfoService.requestCarBaseInfo()`

绑定：

- Package: `com.ts.MainUI`
- Service: `com.ts.can.carinfo.CarInfoService`
- Action: `com.ts.can.carinfo.CarInfoService`

当前使用的字段：

| baseInfo Index | 字段 | 说明 |
|---:|---|---|
| 0 | valid | 数据有效标志，1=有效 |
| 2 | speedKmh | 车速 |
| 3 | rpm | 发动机转速 |
| 13 | rangeKm | 剩余续航 |
| 17 | leftTurn | 左转向 |
| 18 | rightTurn | 右转向 |
| 19 | driverSeatbelt | 主驾安全带 |
| 20 | highBeam | 远光灯 |
| 30 | fuelLevel | 当前油量 |
| 36 | passengerSeatbelt | 副驾安全带 |
| 61 | frontLeftDoorOpen | 左前门 |
| 62 | frontRightDoorOpen | 右前门 |
| 63 | rearLeftDoorOpen | 左后门 |
| 64 | rearRightDoorOpen | 右后门 |
| 65 | trunkOpen | 后备箱 |
| 66 | hoodOpen | 前备箱 / 引擎盖 |

### 补充数据源：`ITsSpeechCar`

绑定：

- Package: `com.ts.MainUI`
- Service: `com.ts.tsspeechlib.car.TsCarService`
- Action: `com.ts.tsspeechlib.car.TsCarService`
- Interface token: `com.ts.tsspeechlib.car.ITsSpeechCar`

当前使用：

| Transaction Code | 方法 | 说明 |
|---:|---|---|
| 17 | GetTotalKilometers | 总里程 |
| 18 | GetOilLeftover | 剩余油量 / 油量备用 |
| 19 | GetLeftTurnSignal | 左转向备用 |
| 20 | GetRightTurnSignal | 右转向备用 |
| 21 | GetEmergency | 双闪 |
| 22 | GetSpeed | 车速备用 |
| 25 | GetCarFrRadar | 前雷达 |
| 26 | GetCarLrRadar | 后雷达 |
| 28 | GetMcuPowerState | MCU 电源状态 |

## 安全策略

本 Demo **不调用** `requestCarDoorInfo()` / `GetCarDoorInfo()`，因为之前实测高频调用可能导致 `com.ts.MainUI` 崩溃。

默认轮询间隔为 1000ms，最低限制为 500ms。

## 功能

- 绑定 CarInfoService / TsCarService / MainUI Common
- 低频安全读取
- 模拟数据模式
- 字段变化日志
- 导出当前 `VehicleState` JSON
- 录制并导出一段车辆数据 JSON

## 使用步骤

1. 安装 APK 到车机。
2. 打开“车辆数据核心Demo”。
3. 点击“绑定全部服务”。
4. 点击“安全读一次”。
5. 如果数据显示正常，再点击“开始读取”。
6. 暂时不方便实车测试时，可以打开“模拟数据模式”。

## 构建

GitHub Actions：

`Actions → Build APK → Run workflow`

生成物：

`VehicleDataCoreDemo-debug-apk`
