package com.example.robotarm.control

import com.example.robotarm.model.JointState
import com.example.robotarm.ros.RosbridgeClient
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * 面向机械臂常见控制场景的 ROS2 调用封装。
 *
 * 对接前请确保 rosbridge_server 已启动，并且以下 topic / service 名称与机器人侧保持一致。
 */
class RobotArmController(
    private val rosClient: RosbridgeClient,
    private val moveJTopic: String = "/arm/movej",
    private val gripperCommandTopic: String = "/arm/gripper",
    private val jointStatesTopic: String = "/joint_states",
) {
    fun connect() = rosClient.connect()

    fun disconnect() = rosClient.disconnect()

    /**
     * MoveJ 控制：按 ROS2 `std_msgs/msg/Float64MultiArray` 发送 6 轴数据。
     */
    fun moveJ(
        joint1: Double,
        joint2: Double,
        joint3: Double,
        joint4: Double,
        joint5: Double,
        joint6: Double,
    ) {
        publishFloat64MultiArray(
            topic = moveJTopic,
            values = listOf(joint1, joint2, joint3, joint4, joint5, joint6),
            expectedSize = 6,
        )
    }

    /**
     * 夹爪控制：按 ROS2 `std_msgs/msg/Float64MultiArray` 发送 3 位填充数据。
     */
    fun controlGripper(value1: Double, value2: Double, value3: Double) {
        publishFloat64MultiArray(
            topic = gripperCommandTopic,
            values = listOf(value1, value2, value3),
            expectedSize = 3,
        )
    }

    /**
     * 订阅机械臂关节状态（`sensor_msgs/msg/JointState`）反馈。
     */
    fun observeJointStates(): Flow<JointState> {
        rosClient.subscribe(topic = jointStatesTopic, messageType = "sensor_msgs/msg/JointState")
        return rosClient.incomingMessages.mapNotNull { payload ->
            val op = payload["op"]?.jsonPrimitive?.content
            val topic = payload["topic"]?.jsonPrimitive?.content
            if (op != "publish" || topic != jointStatesTopic) {
                return@mapNotNull null
            }

            val msg = payload["msg"]?.jsonObject ?: return@mapNotNull null
            JointState(
                names = msg.stringArray("name"),
                positions = msg.doubleArray("position"),
                velocities = msg.doubleArray("velocity"),
                efforts = msg.doubleArray("effort"),
            )
        }
    }

    private fun publishFloat64MultiArray(topic: String, values: List<Double>, expectedSize: Int) {
        require(values.size == expectedSize) {
            "Float64MultiArray data size must be $expectedSize, but was ${values.size}"
        }

        rosClient.publish(
            topic = topic,
            message = buildJsonObject {
                put("layout", buildJsonObject {
                    put("dim", buildJsonArray { })
                    put("data_offset", 0)
                })
                put("data", buildJsonArray {
                    values.forEach { add(it) }
                })
            },
        )
    }

    private fun kotlinx.serialization.json.JsonObject.stringArray(key: String): List<String> {
        return this[key]
            ?.jsonArray
            ?.mapNotNull { element ->
                runCatching { element.jsonPrimitive.content }.getOrNull()
            }
            .orEmpty()
    }

    private fun kotlinx.serialization.json.JsonObject.doubleArray(key: String): List<Double> {
        return this[key]
            ?.jsonArray
            ?.mapNotNull { element ->
                element.jsonPrimitive.doubleOrNull
            }
            .orEmpty()
    }
}
