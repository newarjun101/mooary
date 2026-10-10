package com.arjun.mooary.data.remote.firebase

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Firestore encodings used exclusively by the device-registration document. */
object FirestoreCodec {
    fun string(value: String): JsonObject = buildJsonObject { put("stringValue", value) }

    fun timestamp(value: String): JsonObject = buildJsonObject { put("timestampValue", value) }
}
