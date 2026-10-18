package com.twilighty.yuyu.core.network

import kotlinx.serialization.json.Json

/** One JSON configuration for the whole app: tolerant to new server fields. */
val NetworkJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
    isLenient = false
}
