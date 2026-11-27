package io.reader.rebuild.host.system

import com.reader.core.protocol.HostTimeNowRequest
import com.reader.core.protocol.HostTimeNowResponse
import com.reader.core.protocol.M1HostTimeCompleteParams
import com.reader.core.protocol.ReaderHostCapability
import com.reader.core.protocol.ReaderM1
import com.reader.core.protocol.ReaderProtocolJson
import com.reader.core.protocol.ReaderTypedHostRequest
import io.reader.rebuild.core.HostCapabilities
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal class SystemSurfaceHost(private val clock: Clock = Clock.systemUTC()) : HostCapabilities {
    override val available = setOf(ReaderHostCapability.TIME_NOW)

    override suspend fun reply(capability: ReaderHostCapability, request: String, replyId: Long): String {
        require(capability in available)
        val event = ReaderProtocolJson.decodeFromString<ReaderTypedHostRequest<HostTimeNowRequest>>(request)
        check(event.capability == capability && event.protocolVersion == 1 && event.type == "host.request")
        val now = clock.instant()
        val zone = event.params.timezone?.let(ZoneId::of) ?: ZoneId.systemDefault()
        return ReaderM1.hostCompleteTimeNow.encode(replyId, M1HostTimeCompleteParams(
            event.operationId,
            HostTimeNowResponse(now.toEpochMilli(), now.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), zone.id),
        ))
    }
}
