# ROS2 机械臂 Android 控制系统 (Android-ROS2-ArmControl)

本项目提供了一套完整的 ROS2 机械臂 Android 控制方案，包含高性能的 **Android SDK (com.ir.sdk)** 及配套的 **联调测试 Demo**。

---

## 1. SDK 架构与组件
SDK 位于 `android-sdk/robot-arm-sdk` 目录下，采用极简主义设计（除 JVM 基础库外零多余依赖）：

- **`com.ir.sdk.robotarm.ros`**: 核心通讯层。支持自动重连、**异步错误流 (errorEvents)**、**报文级日志 (loggingEnabled)**。
- **`com.ir.sdk.robotarm.control`**: 业务封装层。提供 `moveJ`、`controlGripper`、`observeJointStates` 等标准 API。
- **`com.ir.sdk.robotarm.model`**: 协议与数据模型。

---

## 2. 编译与打包 (AAR)

如果您需要生成 AAR 库文件供其他项目使用：

1.  确保项目根目录有完整的 Gradle Wrapper (`gradlew`, `gradle-wrapper.jar`)。
2.  在终端执行：
    ```bash
    ./gradlew :android-sdk:robot-arm-sdk:assembleRelease
    ```
3.  产物路径：`android-sdk/robot-arm-sdk/build/outputs/aar/robot-arm-sdk-release.aar`

---

## 3. SDK 集成指南

### 3.1 引入 AAR
将生成的 `.aar` 文件拷贝至目标工程的 `app/libs` 目录，并在 `build.gradle.kts` 中配置：

```kotlin
dependencies {
    // 引入 libs 下所有 aar
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    
    // 补全 SDK 所需的运行时依赖
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
}
```

### 3.2 代码调用示例
```kotlin
val rosClient = RosbridgeClient("ws://10.0.2.2:9090") // 模拟器专用地址
rosClient.loggingEnabled = true 

// 全局异常监听
lifecycleScope.launch {
    rosClient.errorEvents.collect { error ->
        Toast.makeText(context, "SDK 异常: ${error.message}", Toast.LENGTH_SHORT).show()
    }
}

val controller = RobotArmController(rosClient)
controller.connect()

// 发送 MoveJ 指令 (返回 Boolean 表示是否发送成功)
controller.moveJ(0.0, -0.3, 1.0, 0.5, 0.2, 0.0)
```

---

## 4. 联调测试指导 (Emulator + ROS2)

### 4.1 环境准备
- **ROS2 侧**：启动 `rosbridge_server` (默认端口 9090)。
- **Android 侧**：使用 Android Studio 模拟器 (API 30+)，地址设为 `10.0.2.2`。

### 4.2 调试技巧 (Logcat)
建议在 Logcat 中添加以下过滤器以监控全链路：
- `Tag: RosbridgeClient` (报文级日志：查看原始 JSON 收发)
- `Tag: RobotArmDemo` (业务级日志：查看用户操作与解析反馈)

---

## 5. 常见问题
- **无法连接**：确认 `rosbridge` 已开启且 9090 端口未被防火墙阻拦。
- **版本冲突**：请确保 Kotlin 版本为 `1.9.24` 以获得最佳兼容性。
- **日志查看 (非安卓开发者调试)**：如果您是机器人侧（ROS/算法）同学，想实时观测 App 发出的原始 JSON：
    1. 确保电脑已安装 `adb` 工具。
    2. 执行：`adb logcat -v time -s RosbridgeClient:D RobotArmDemo:D`
    3. 即可实时捕捉到下发的话题指令与接收到的状态反馈，方便协议对齐。
- **发送可靠性**：SDK 已内置状态机。在断连时点击发送，不会闪退，会通过上述日志抛出 `Cannot send: Not connected` 警告。
