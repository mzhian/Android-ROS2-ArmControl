# SDK 集成教程

### 1. 导入 AAR 文件
将生成的 `robot-arm-sdk-release.aar` 文件复制到您项目的 `app/libs/` 目录下。

### 2. 配置 build.gradle
在您的 App 模块的 `build.gradle` (或 `build.gradle.kts`) 中添加以下配置：

```gradle
dependencies {
    // 引用本地 AAR
    implementation(files("libs/robot-arm-sdk-release.aar"))

    // SDK 运行必须的第三方依赖（请务必添加，否则会报 ClassNotFound 错误）
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
```

### 3. 代码调用示例
```kotlin
// 1. 初始化客户端
val rosClient = RosbridgeClient("ws://YOUR_ROBOT_IP:9090")
val controller = RobotArmController(rosClient)

// 2. 连接并监听状态
rosClient.loggingEnabled = true // [可选] 开启后可在 Logcat 中查看原始 JSON 报文 (Tag: RosbridgeClient)
controller.connect()
lifecycleScope.launch {
    controller.connectionState.collect { state ->
        println("当前连接状态: $state")
    }
}

// 3. 发送控制指令
controller.moveJ(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
```
