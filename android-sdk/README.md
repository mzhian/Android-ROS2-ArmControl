# ROS2 机械臂 Android SDK 封装示例

该目录提供一个可直接作为 Android Library 引入的封装骨架，目标环境：

- AGP `9.0+`
- Kotlin `2.3.10`
- WebSocket 通信库：`org.java-websocket:Java-WebSocket`
- rosbridge JSON 协议（适配 ROS2）

## 目录结构

- `robot-arm-sdk/src/main/java/com/example/robotarm/ros/RosbridgeClient.kt`
  - 负责 Java-WebSocket 连接、收发 rosbridge JSON、service call 异步回包匹配。
- `robot-arm-sdk/src/main/java/com/example/robotarm/control/RobotArmController.kt`
  - 面向机械臂控制场景的高层 API（MoveJ、夹爪、joint_states 反馈）。
- `robot-arm-sdk/src/main/java/com/example/robotarm/model/RosbridgeMessages.kt`
  - rosbridge 常用消息结构定义（advertise/publish/subscribe/call_service）。
- `robot-arm-sdk/src/main/java/com/example/robotarm/model/JointStateModels.kt`
  - 机械臂关节状态模型定义。

## 快速接入

1. 启动 ROS2 侧 rosbridge（默认端口 `9090`）：

   ```bash
   ros2 launch rosbridge_server rosbridge_websocket_launch.xml
   ```

2. Android 侧初始化：

   ```kotlin
   val rosbridge = RosbridgeClient("ws://<ros2-host>:9090")
   val controller = RobotArmController(rosbridge)

   controller.connect()
   controller.moveJ(0.0, -0.3, 1.0, 0.5, 0.2, 0.0)
   controller.controlGripper(1.0, 0.0, 0.0)

   controller.observeJointStates().collect { state ->
       // state.names / state.positions / state.velocities / state.efforts
   }
   ```

## 协议建议

- 推荐统一约定 `topic` 与 `service` 命名规则（如 `/arm/*`）。
- 当前封装中 `MoveJ` 和 `夹爪` 都按 `std_msgs/msg/Float64MultiArray` 发布：
  - MoveJ 发送长度为 6 的 `data`
  - 夹爪发送长度为 3 的 `data`
- `joint_states` 反馈按 `sensor_msgs/msg/JointState` 订阅并解析为 `JointState` 数据对象。
- 如果需要可靠重连，可在 `RosbridgeClient` 中增加重连策略和心跳机制。
