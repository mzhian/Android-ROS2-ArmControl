package com.ir.sdk.robotarm.control

import com.ir.sdk.robotarm.model.JointState
import com.ir.sdk.robotarm.ros.RosbridgeClient
import kotlinx.serialization.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * 面向机械臂常见控制场景的 ROS2 调用封装。
 */
class RobotArmController(
    private val rosClient: RosbridgeClient,
    private val moveJTopic: String = "/arm/movej",
    private val gripperCommandTopic: String = "/arm/gripper",
    private val jointStatesTopic: String = "/joint_states",
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val advertisedTopics = mutableSetOf<String>()
    private val subscribedTopics = mutableSetOf<String>()

    fun connect() = rosClient.connect()

    fun disconnect() = rosClient.disconnect()

    val connectionState = rosClient.connectionState

    /**
     * MoveJ 控制：按 ROS2 `std_msgs/msg/Float64MultiArray` 发送 6 轴数据。
     * @return 是否成功发出指令
     */
    fun moveJ(
        joint1: Double,
        joint2: Double,
        joint3: Double,
        joint4: Double,
        joint5: Double,
        joint6: Double,
    ): Boolean {
        return publishFloat64MultiArray(
            topic = moveJTopic,
            values = listOf(joint1, joint2, joint3, joint4, joint5, joint6),
            expectedSize = 6,
        )
    }

    /**
     * 夹爪控制：按 ROS2 `std_msgs/msg/Float64MultiArray` 发送 3 位填充数据。
     * @return 是否成功发出指令
     */
    fun controlGripper(value1: Double, value2: Double, value3: Double): Boolean {
        return publishFloat64MultiArray(
            topic = gripperCommandTopic,
            values = listOf(value1, value2, value3),
            expectedSize = 3,
        )
    }

    /**
     * 订阅机械臂关节状态反馈。
     */
    fun observeJointStates(): Flow<JointState> {
        if (subscribedTopics.add(jointStatesTopic)) {
            rosClient.subscribe(topic = jointStatesTopic, messageType = "sensor_msgs/msg/JointState")
        }
        return rosClient.incomingMessages.mapNotNull { payload ->
            val op = payload["op"]?.jsonPrimitive?.content
            val topic = payload["topic"]?.jsonPrimitive?.content
            if (op != "publish" || topic != jointStatesTopic) {
                return@mapNotNull null
            }

            val msg = payload["msg"]?.jsonObject ?: return@mapNotNull null
            runCatching {
                json.decodeFromJsonElement<JointState>(msg)
            }.getOrNull()
        }
    }

    private fun publishFloat64MultiArray(topic: String, values: List<Double>, expectedSize: Int): Boolean {
        require(values.size == expectedSize) {
            "Float64MultiArray data size must be $expectedSize, but was ${values.size}"
        }

        // 预防性：首次发布前先宣告主题，增强兼容性
        if (advertisedTopics.add(topic)) {
            rosClient.advertise(topic, "std_msgs/msg/Float64MultiArray")
        }

        return rosClient.publish(
            topic = topic,
            msg = buildJsonObject {
                put("layout", buildJsonObject {
                    put("dim", buildJsonArray { })
                    put("data_offset", 0)
                })
                put("data", buildJsonArray {
                    values.forEach { add(it) }
                })
            },
            type = "std_msgs/msg/Float64MultiArray"
        )
    }
}
