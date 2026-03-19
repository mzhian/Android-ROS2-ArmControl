package com.example.robotarm.demo

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ir.sdk.robotarm.control.RobotArmController
import com.ir.sdk.robotarm.ros.RosbridgeClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 联调测试 Activity 示例：
 * - Connect / Disconnect
 * - MoveJ / Gripper 指令发送
 * - JointStates 订阅显示
 */
class RobotArmDebugActivity : AppCompatActivity() {
    private val TAG = "RobotArmDemo"
    private var rosbridgeUrl = "ws://10.136.175.185:9090"

    private lateinit var rosClient: RosbridgeClient
    private lateinit var controller: RobotArmController
    private var jointStatesJob: Job? = null
    private var connectionStateJob: Job? = null
    private var errorEventsJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_robot_arm_debug)

        val edtRosbridgeUrl = findViewById<android.widget.EditText>(R.id.edtRosbridgeUrl)
        val btnModify = findViewById<Button>(R.id.btnModify)
        edtRosbridgeUrl.setText(rosbridgeUrl)

        initRosClient(rosbridgeUrl)

        btnModify.setOnClickListener {
            val newUrl = edtRosbridgeUrl.text.toString()
            if (newUrl.isNotBlank()) {
                rosbridgeUrl = newUrl
                initRosClient(rosbridgeUrl)
                Toast.makeText(this, R.string.msg_url_updated, Toast.LENGTH_SHORT).show()
                Log.d(TAG, "API 地址已更新为: $rosbridgeUrl")
            }
        }

        val statusText = findViewById<TextView>(R.id.statusText)

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            Log.d(TAG, "用户点击：连接机器人 -> $rosbridgeUrl")
            controller.connect()
        }

        findViewById<Button>(R.id.btnDisconnect).setOnClickListener {
            Log.d(TAG, "用户点击：断开连接")
            jointStatesJob?.cancel()
            controller.disconnect()
        }

        findViewById<Button>(R.id.btnMoveJ).setOnClickListener {
            Log.d(TAG, "用户点击：执行 MoveJ 示例")
            val success = controller.moveJ(0.0, -0.3, 1.0, 0.5, 0.2, 0.0)
            if (!success) {
                // 虽然全局错误流会有提示，但针对具体点击，我们也可以做额外处理
                Log.w(TAG, "MoveJ 发送指令失败，请检查连接")
            }
        }

        findViewById<Button>(R.id.btnGripper).setOnClickListener {
            Log.d(TAG, "用户点击：执行夹爪控制示例")
            val success = controller.controlGripper(1.0, 0.0, 0.0)
            if (!success) {
                Log.w(TAG, "Gripper 发送指令失败，请检查连接")
            }
        }

        findViewById<Button>(R.id.btnSubscribeJointStates).setOnClickListener {
            Log.d(TAG, "用户点击：订阅关节状态")
            jointStatesJob?.cancel()
            jointStatesJob = lifecycleScope.launch {
                controller.observeJointStates().collect { state ->
                    Log.d(TAG, "收到关节数据: ${state.name.size} 轴")
                    statusText.text = buildString {
                        appendLine("joint_states received")
                        appendLine("name=${state.name}")
                        appendLine("position=${state.position}")
                        appendLine("velocity=${state.velocity}")
                        appendLine("effort=${state.effort}")
                    }
                }
            }
        }
    }

    private fun initRosClient(url: String) {
        // 如果已经初始化过，先断开旧连接并取消旧的观察任务
        if (::controller.isInitialized) {
            controller.disconnect()
        }
        connectionStateJob?.cancel()
        errorEventsJob?.cancel()
        jointStatesJob?.cancel()
        
        rosClient = RosbridgeClient(url)
        rosClient.loggingEnabled = true
        controller = RobotArmController(rosClient)
        
        // 重新监听状态
        observeState()
        observeErrors()
    }

    private fun observeErrors() {
        errorEventsJob = lifecycleScope.launch {
            rosClient.errorEvents.collect { error ->
                Toast.makeText(this@RobotArmDebugActivity, "SDK 异常: ${error.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "SDK Exception caught in UI: ${error.message}")
            }
        }
    }

    private fun observeState() {
        val statusText = findViewById<TextView>(R.id.statusText)
        connectionStateJob = lifecycleScope.launch {
            controller.connectionState.collect { state ->
                val statusDesc = when (state) {
                    RosbridgeClient.ConnectionState.IDLE -> "空闲"
                    RosbridgeClient.ConnectionState.CONNECTING -> "正在连接..."
                    RosbridgeClient.ConnectionState.CONNECTED -> "已连接 (Connected)"
                    RosbridgeClient.ConnectionState.DISCONNECTED -> "已断开"
                    RosbridgeClient.ConnectionState.RECONNECTING -> "尝试自动重连中..."
                    RosbridgeClient.ConnectionState.ERROR -> "连接出错"
                }
                statusText.text = "当前状态: $statusDesc"
                Log.d(TAG, "连接状态变更: $state")
            }
        }
    }

    override fun onDestroy() {
        jointStatesJob?.cancel()
        controller.disconnect()
        super.onDestroy()
    }
}
