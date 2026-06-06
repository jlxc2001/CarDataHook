# VehicleDataCoreDemo

车辆数据核心测试 App。

## v0.1.1-buildfix

- 修复 GitHub Actions 编译失败：显式开启 `buildFeatures { aidl true }`。
- compileSdk 调整为 34，避免 AGP 8.5.2 对 compileSdk 35 的兼容警告。
- 保留实时读取、模拟数据、变化日志、JSON 导出功能。

## 功能

- 绑定 `com.ts.MainUI/com.ts.can.carinfo.CarInfoService`
- 读取 `ICarInfoService.requestCarBaseInfo()`
- 绑定 `TsCarService` 读取双闪、总里程、前后雷达等补充信号
- 模拟数据模式，便于离车调试 UI
- 字段变化日志
- 当前状态/记录 JSON 导出

## 注意

默认 1000ms 轮询，最低限制 500ms。第一版不调用高风险的 `requestCarDoorInfo()` / `GetCarDoorInfo()`。
