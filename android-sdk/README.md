# ROS2 机械臂 Android SDK (com.ir.sdk)

该目录提供了针对 ROS2 机械臂控制的 Android Library 封装，具备高可靠性、异常反馈及报文调试能力。

## 核心环境
- **Kotlin**: `1.9.24` (推荐，兼容性最稳)
- **Serialization**: `kotlinx-serialization-json:1.6.3`
- **WebSocket**: `org.java-websocket:Java-WebSocket:1.5.7`
- **Gradle**: 支持 AGP 8.0+

## 核心组件 (Package: `com.ir.sdk.robotarm`)

- `ros.RosbridgeClient`: 协议底层。支持自动重连、**异步错误流 (errorEvents)**、**报文日志开关 (loggingEnabled)**。
- `control.RobotArmController`: 业务高层。提供 `moveJ`、`controlGripper`、`observeJointStates` 等标准 API。
- `model.*`: 包含 `RosbridgeMessage` 协议定义及 `JointState` 数据模型。

## 快速接入示例

```kotlin
// 1. 初始化 (内网模拟器地址通常为 10.0.2.2)
val rosClient = RosbridgeClient("ws://10.0.2.2:9090")
rosClient.loggingEnabled = true // 开启报文日志调试 (Tag: RosbridgeClient)

// 2. 全局异常监听 (重要：处理断连、协议错误等)
lifecycleScope.launch {
    rosClient.errorEvents.collect { error ->
        Log.e("SDK_ERR", "捕获到异常: ${error.message}")
    }
}

val controller = RobotArmController(rosClient)
controller.connect()

// 3. 指令下发 (返回 Boolean 指示是否成功发出)
val isSent = controller.moveJ(0.0, -0.3, 1.0, 0.5, 0.2, 0.0)

// 4. 状态订阅 (Flow)
lifecycleScope.launch {
    controller.observeJointStates().collect { state ->
        println("关节角度: ${state.position}")
    }
}
```

## 分发与构建
- 请查阅 [打包教程.md](./robot-arm-sdk/打包教程.md) 生成标准 AAR 产物。
- 建议通过 `maven-publish` 插件发布到公司私有仓库。

## 协议约定
- **MoveJ**: 发布到 `/arm/movej` (`std_msgs/msg/Float64MultiArray`)，data 长度为 6。
- **Gripper**: 发布到 `/arm/gripper` (`std_msgs/msg/Float64MultiArray`)，data 长度为 3。
- **Feedback**: 订阅 `/joint_states` (`sensor_msgs/msg/JointState`)。
