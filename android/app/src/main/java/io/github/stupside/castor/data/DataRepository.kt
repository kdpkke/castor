package io.github.stupside.castor.data

import castor.v1.CastOuterClass.*
import castor.v1.DeviceOuterClass.*

class DataRepository(private val api: Rpc) {
  suspend fun devices(): List<Device> =
    ListDevicesResponse.parseFrom(
        api.call("/castor.v1.DeviceService/ListDevices", ListDevicesRequest.getDefaultInstance()),
      )
      .devicesList

  suspend fun casts(): List<Cast> =
    ListCastsResponse.parseFrom(
        api.call("/castor.v1.CastService/ListCasts", ListCastsRequest.getDefaultInstance()),
      )
      .castsList

  suspend fun cast(request: CastRequest): String =
    CastResponse.parseFrom(
        api.call("/castor.v1.CastService/Cast", request),
      )
      .castId
      .also { check(it.isNotBlank()) { "The server returned an empty cast ID" } }

  suspend fun stop(id: String) {
    api.call("/castor.v1.CastService/Stop", StopRequest.newBuilder().setCastId(id).build())
  }

  suspend fun watch(id: String, update: suspend (WatchResponse) -> Unit) =
    api.watch(WatchRequest.newBuilder().setCastId(id).build(), update)
}
