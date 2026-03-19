package com.example.robotarm.demo

import android.os.Bundle
import android.widget.Button
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
 */
class RobotArmDebugActivity : AppCompatActivity() {
    private val rosbridgeUrl = "ws://10.0.2.2:9090"

    private lateinit var rosClient: RosbridgeClient
    private lateinit var controller: RobotArmController

    private var jointStatesJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_robot_arm_debug)

        rosClient = RosbridgeClient(rosbridgeUrl)
        controller = RobotArmController(rosClient)

        val statusText = findViewById<TextView>(R.id.statusText)

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            controller.connect()
            statusText.text = "Connected: $rosbridgeUrl"
        }

        findViewById<Button>(R.id.btnDisconnect).setOnClickListener {
            jointStatesJob?.cancel()
            controller.disconnect()
            statusText.text = "Disconnected"
        }

        findViewById<Button>(R.id.btnMoveJ).setOnClickListener {
            controller.moveJ(0.0, -0.3, 1.0, 0.5, 0.2, 0.0)
            statusText.text = "MoveJ command sent"
        }

        findViewById<Button>(R.id.btnGripper).setOnClickListener {
            controller.controlGripper(1.0, 0.0, 0.0)
            statusText.text = "Gripper command sent"
        }

        findViewById<Button>(R.id.btnSubscribeJointStates).setOnClickListener {
            jointStatesJob?.cancel()
            jointStatesJob = lifecycleScope.launch {
                controller.observeJointStates().collect { state ->
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
        controller.disconnect()
        super.onDestroy()
    }
}
