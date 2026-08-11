package com.streamer.app.http

import android.content.Context
import fi.iki.elonen.NanoHTTPD

object Assets {
    fun serve(ctx: Context, name: String, mime: String): NanoHTTPD.Response {
        return try {
            val bytes = ctx.assets.open("web/$name").use { it.readBytes() }
            NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mime, bytes.inputStream(), bytes.size.toLong())
        } catch (e: Exception) {
            NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "asset not found: $name")
        }
    }
}
