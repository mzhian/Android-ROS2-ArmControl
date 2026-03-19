package com.example.robotarm.demo

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.robotarm.control.RobotArmController
import com.example.robotarm.ros.RosbridgeClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 联调测试 Activity 示例：
 * - Connect / Disconnect
 * - MoveJ / Gripper 指令发送
 * - JointStates 订阅显示
 * - UI 输入 rosbridge IP / 端口
 */
class RobotArmDebugActivity : AppCompatActivity() {
    private var rosClient: RosbridgeClient? = null
    private var controller: RobotArmController? = null

    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var moveJInput: EditText
    private lateinit var gripperInput: EditText
    private lateinit var statusText: TextView

    private var jointStatesJob: Job? = null

    private val prefs by lazy {
        getSharedPreferences("robot_arm_demo", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_robot_arm_debug)

        hostInput = findViewById(R.id.inputHost)
        portInput = findViewById(R.id.inputPort)
        moveJInput = findViewById(R.id.inputMoveJ)
        gripperInput = findViewById(R.id.inputGripper)
        statusText = findViewById(R.id.statusText)

        hostInput.setText(prefs.getString(KEY_HOST, DEFAULT_HOST))
        portInput.setText(prefs.getInt(KEY_PORT, DEFAULT_PORT).toString())
        moveJInput.setText(DEFAULT_MOVE_J)
        gripperInput.setText(DEFAULT_GRIPPER)

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            connectToRosbridge()
        }

        findViewById<Button>(R.id.btnDisconnect).setOnClickListener {
            jointStatesJob?.cancel()
            controller?.disconnect()
            statusText.text = "Disconnected"
        }

        findViewById<Button>(R.id.btnMoveJ).setOnClickListener {
            val armController = controller ?: return@setOnClickListener showStatus("Please connect first")
            val values = parseCommandValues(moveJInput, expectedSize = 6, commandName = "MoveJ") ?: return@setOnClickListener
            armController.moveJ(
                values[0],
                values[1],
                values[2],
                values[3],
                values[4],
                values[5],
            )
            statusText.text = "MoveJ command sent"
        }

        findViewById<Button>(R.id.btnGripper).setOnClickListener {
            val armController = controller ?: return@setOnClickListener showStatus("Please connect first")
            val values = parseCommandValues(gripperInput, expectedSize = 3, commandName = "Gripper") ?: return@setOnClickListener
            armController.controlGripper(values[0], values[1], values[2])
            statusText.text = "Gripper command sent"
        }

        findViewById<Button>(R.id.btnSubscribeJointStates).setOnClickListener {
            val armController = controller ?: return@setOnClickListener showStatus("Please connect first")
            jointStatesJob?.cancel()
            jointStatesJob = lifecycleScope.launch {
                armController.observeJointStates().collect { state ->
                    statusText.text = buildString {
                        appendLine("joint_states received")
                        appendLine("name=${state.names}")
                        appendLine("position=${state.positions}")
                        appendLine("velocity=${state.velocities}")
                        appendLine("effort=${state.efforts}")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        jointStatesJob?.cancel()
        controller?.disconnect()
        super.onDestroy()
    }

    private fun connectToRosbridge() {
        val host = hostInput.text.toString().trim()
        val port = portInput.text.toString().trim().toIntOrNull()

        if (host.isBlank()) {
            showStatus("Please input rosbridge host/IP")
            return
        }

        if (port == null || port !in 1..65535) {
            showStatus("Please input a valid port")
            return
        }

        val rosbridgeUrl = "ws://$host:$port"
        jointStatesJob?.cancel()
        controller?.disconnect()

        rosClient = RosbridgeClient(rosbridgeUrl)
        controller = RobotArmController(checkNotNull(rosClient))
        controller?.connect()

        prefs.edit()
            .putString(KEY_HOST, host)
            .putInt(KEY_PORT, port)
            .apply()

        showStatus("Connected: $rosbridgeUrl")
    }

    private fun showStatus(message: String) {
        statusText.text = message
    }

    private fun parseCommandValues(input: EditText, expectedSize: Int, commandName: String): List<Double>? {
        val raw = input.text.toString().trim()
        if (raw.isBlank()) {
            showStatus("Please input $expectedSize values for $commandName")
            return null
        }

        val values = raw
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toDoubleOrNull() }

        if (values.size != expectedSize) {
            showStatus("$commandName requires $expectedSize numeric values")
            return null
        }

        return values
    }

    private companion object {
        const val DEFAULT_HOST = "10.0.2.2"
        const val DEFAULT_PORT = 9090
        const val DEFAULT_MOVE_J = "0.0, -0.3, 1.0, 0.5, 0.2, 0.0"
        const val DEFAULT_GRIPPER = "1.0, 0.0, 0.0"
        const val KEY_HOST = "rosbridge_host"
        const val KEY_PORT = "rosbridge_port"
    }
}
