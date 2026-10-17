package io.github.stupside.castor.data

import castor.v1.CastOuterClass.WatchResponse
import com.google.protobuf.MessageLite
import java.io.DataInputStream
import java.io.EOFException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Connect's protobuf wire format uses the same generated contracts as the desktop clients.
class Rpc(private val baseUrl: String, private val token: String) {
  private fun open(
    method: String,
    request: MessageLite,
    streaming: Boolean = false,
  ): HttpURLConnection {
    val connection = URL(baseUrl + method).openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = false
    connection.requestMethod = "POST"
    connection.connectTimeout = 10_000
    connection.readTimeout = if (streaming) 0 else 90_000
    connection.doOutput = true
    connection.setRequestProperty("Authorization", "Bearer $token")
    connection.setRequestProperty("Connect-Protocol-Version", "1")
    connection.setRequestProperty(
      "Content-Type",
      if (streaming) "application/connect+proto" else "application/proto",
    )
    connection.outputStream.use { output ->
      val bytes = request.toByteArray()
      if (streaming) {
        java.io.DataOutputStream(output).apply {
          writeByte(0)
          writeInt(bytes.size)
          write(bytes)
        }
      } else output.write(bytes)
    }
    if (connection.responseCode !in 200..299) {
      val message = connection.errorStream?.use { readLimited(it, 4096).toString(Charsets.UTF_8) }
      connection.disconnect()
      throw java.io.IOException(message ?: "Castor returned HTTP ${connection.responseCode}")
    }
    return connection
  }

  suspend fun call(method: String, request: MessageLite): ByteArray =
    withContext(Dispatchers.IO) {
      val connection = open(method, request)
      try {
        connection.inputStream.use { input ->
          readLimited(input, MAX_MESSAGE)
        }
      } finally {
        connection.disconnect()
      }
    }

  suspend fun watch(request: MessageLite, update: suspend (WatchResponse) -> Unit) =
    withContext(Dispatchers.IO) {
      val connection = open("/castor.v1.CastService/Watch", request, true)
      coroutineScope {
        val cancellation =
          launch(Dispatchers.Default, start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            try {
              awaitCancellation()
            } finally {
              connection.disconnect()
            }
          }
        try {
          val input = DataInputStream(connection.inputStream)
          while (currentCoroutineContext().isActive) {
            val message = readWatch(input)
            update(message)
            if (message.hasEnded()) break
          }
        } finally {
          cancellation.cancel()
          connection.disconnect()
        }
      }
    }

  companion object {
    private const val MAX_MESSAGE = 8 * 1024 * 1024

    internal fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
      val output = java.io.ByteArrayOutputStream()
      val buffer = ByteArray(8192)
      while (true) {
        val read = input.read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
        if (read < 0) return output.toByteArray()
        check(output.size() + read <= limit) { "Castor response exceeds the message limit" }
        output.write(buffer, 0, read)
      }
    }

    internal fun readWatch(input: DataInputStream): WatchResponse {
      val flags =
        try {
          input.readUnsignedByte()
        } catch (_: EOFException) {
          throw EOFException("The status stream closed before the cast ended.")
        }
      val size = input.readInt()
      require(size in 0..MAX_MESSAGE) { "Invalid status message length" }
      val bytes = ByteArray(size).also { input.readFully(it) }
      if (flags == 2)
        throw java.io.IOException(
          "Status stream ended before the cast: ${bytes.toString(Charsets.UTF_8)}",
        )
      require(flags == 0) { "Unsupported status message flags" }
      return WatchResponse.parseFrom(bytes).also {
        require(it.hasStatus() || it.hasEnded()) { "Empty status update" }
      }
    }
  }
}
