# Demo 目录说明

本目录包含两部分内容：

1. `联调测试指导说明.md`
   - Android 模拟器 + ROS2 机械臂联调步骤。
2. `app/src/main/...`
   - 可直接拷贝到 Android 测试工程中的示例 Activity 和布局文件。

## 文件清单

- `app/src/main/java/com/example/robotarm/demo/RobotArmDebugActivity.kt`
- `app/src/main/res/layout/activity_robot_arm_debug.xml`

## 接入提示

- 在你的测试 App `build.gradle.kts` 中依赖 `robot-arm-sdk` 模块。
- 在 `AndroidManifest.xml` 注册：

```xml
<activity android:name=".demo.RobotArmDebugActivity" />
```

- 确保 App 使用了 `AppCompat` 与 `lifecycle-runtime-ktx`（示例中使用了 `lifecycleScope`）。
