package io.github.stupside.castor.data

import castor.v1.CastOuterClass.*
import com.google.protobuf.Empty
import java.io.*
import org.junit.Assert.*
import org.junit.Test

class RpcTest {
  @Test
  fun responseLimitAcceptsExactBytesAndRejectsOverflow() {
    assertArrayEquals(
      byteArrayOf(1, 2),
      Rpc.readLimited(ByteArrayInputStream(byteArrayOf(1, 2)), 2),
    )
    assertThrows(IllegalStateException::class.java) {
      Rpc.readLimited(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 2)
    }
  }

  private fun frame(flags: Int, bytes: ByteArray): DataInputStream {
    val output = ByteArrayOutputStream()
    DataOutputStream(output).use {
      it.writeByte(flags)
      it.writeInt(bytes.size)
      it.write(bytes)
    }
    return DataInputStream(ByteArrayInputStream(output.toByteArray()))
  }

  @Test
  fun statusIsNotACompletionAndEndedIsPreserved() {
    val status =
      WatchResponse.newBuilder()
        .setStatus(CastStatus.newBuilder().setConnecting(Empty.getDefaultInstance()))
        .build()
    val ended =
      WatchResponse.newBuilder()
        .setEnded(Ended.newBuilder().setStopped(Empty.getDefaultInstance()))
        .build()
    assertEquals(status, Rpc.readWatch(frame(0, status.toByteArray())))
    assertEquals(ended, Rpc.readWatch(frame(0, ended.toByteArray())))
  }

  @Test
  fun transportCloseWithoutEndedIsAnError() {
    assertThrows(EOFException::class.java) {
      Rpc.readWatch(DataInputStream(ByteArrayInputStream(byteArrayOf())))
    }
    assertThrows(IOException::class.java) { Rpc.readWatch(frame(2, "{}".toByteArray())) }
  }

  @Test
  fun malformedAndOversizedMessagesAreRejected() {
    assertThrows(IllegalArgumentException::class.java) { Rpc.readWatch(frame(1, byteArrayOf())) }
    assertThrows(IllegalArgumentException::class.java) { Rpc.readWatch(frame(0, byteArrayOf())) }
    val oversized = byteArrayOf(0, 0x7f, -1, -1, -1)
    assertThrows(IllegalArgumentException::class.java) {
      Rpc.readWatch(DataInputStream(ByteArrayInputStream(oversized)))
    }
    assertThrows(EOFException::class.java) {
      Rpc.readWatch(DataInputStream(ByteArrayInputStream(byteArrayOf(0, 0, 0, 0, 3, 8))))
    }
  }
}
