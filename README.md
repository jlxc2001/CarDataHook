# VehicleDataProbe / 车辆数据探针

用于测试第三方车机系统 `com.ts.MainUI` 暴露的 `com.ts.can.carinfo.CarInfoService` 接口。

## 目标

先低频读取车辆数据，确认接口可用，再接入正式车机 UI。

## 安全建议

- 默认轮询间隔为 1000ms。
- 不建议低于 500ms。
- 不会自动调用 UART 写入、SendCmd、UartCanSend 等可能改变车辆状态的接口。
- 如果 MainUI/MainApp 崩溃，立即停止轮询并重启车机相关进程。

## 已接入的安全读取接口

- requestCarDoorInfo()
- requestCarIllInfo()
- requestCarBaseInfo()
- requestCarAirLtTemp()
- requestCarAirRtTemp()
- requestT3FlSta()
- requestT3FlDevInfo()

## 手动测试接口

- requestCanRecevieData(para)
- requestInfo(str, para)
- requestCarBaseInfo2(str, para)

