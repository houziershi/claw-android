package com.openclaw.agent.core.mijia

import kotlinx.serialization.Serializable

/** A single Mijia smart home device */
data class MijiaDevice(
    val did: String,
    val name: String,
    val model: String,
    val isOnline: Boolean,
    val roomName: String = "未知房间",
    val homeId: String = ""
)

/** MIoT property definition (one attribute of a device) */
data class MiotProperty(
    val name: String,
    val description: String,
    val type: String,            // bool | int | uint | float | string
    val rw: String,              // r | w | rw
    val unit: String?,
    val range: List<Number>?,    // [min, max, step]
    val valueList: List<MiotValueItem>?,
    val siid: Int,
    val piid: Int
)

data class MiotValueItem(
    val value: Int,
    val description: String
)

/** MIoT action definition */
data class MiotAction(
    val name: String,
    val description: String,
    val siid: Int,
    val aiid: Int
)

/** Full device specification from miot-spec.org */
data class MiotSpec(
    val name: String,
    val model: String,
    val properties: List<MiotProperty>,
    val actions: List<MiotAction>
)

/** Error returned by MIoT API */
class MijiaApiException(message: String, val code: Int = -1) : Exception(message)
