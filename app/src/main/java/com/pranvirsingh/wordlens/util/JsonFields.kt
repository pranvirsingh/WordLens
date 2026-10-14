package com.pranvirsingh.wordlens.util

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** A string (or number/boolean as text) field of a JSON object, or null if missing or not a value. */
internal fun JsonObject.stringField(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** True only when the field is the JSON boolean `true` (not the string "true"). */
internal fun JsonObject.isTrue(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull == true
