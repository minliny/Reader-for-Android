package com.reader.host

import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

const val FOREGROUND_TIMER_MIN_DELAY_MS: Long = 250L
const val FOREGROUND_TIMER_MAX_DELAY_MS: Long = 3_600_000L

data class ForegroundTimerContract(
    val timerId: String,
    val correlationId: String,
    val delayMs: Long,
    val generation: Long,
    val oneShot: Boolean,
    val foregroundOnly: Boolean
)

data class ForegroundTimerFire(
    val timerId: String,
    val correlationId: String,
    val generation: Long
)

fun interface ForegroundTimerCancellation {
    fun cancel(): Boolean
}

fun interface ForegroundTimerScheduler {
    fun schedule(delayMs: Long, callback: () -> Unit): ForegroundTimerCancellation
}

private object ProductionForegroundTimerScheduler : ForegroundTimerScheduler {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "reader-foreground-timer").apply { isDaemon = true }
    }

    override fun schedule(delayMs: Long, callback: () -> Unit): ForegroundTimerCancellation {
        val future = executor.schedule(callback, delayMs, TimeUnit.MILLISECONDS)
        return ForegroundTimerCancellation { future.cancel(false) }
    }
}

data class ForegroundTimerArmResult(
    val armed: Boolean,
    val replacedGeneration: Long? = null,
    val duplicate: Boolean = false,
    val stale: Boolean = false,
    val terminalGeneration: Boolean = false
)

data class ForegroundTimerCancelResult(
    val cancelled: Boolean,
    val generationMatched: Boolean,
    val activeGeneration: Long? = null
)

/**
 * One timerId -> one cancellable foreground one-shot. Generation checks are
 * performed before cancellation, so a delayed cancel for generation N can
 * never kill the active N+1 timer. Same-generation rearm is allowed only
 * after the previous one-shot fired; an explicit cancel terminally closes it.
 */
class ForegroundTimerRegistry(
    private val scheduler: ForegroundTimerScheduler = ProductionForegroundTimerScheduler,
    private val onFire: (ForegroundTimerFire) -> Unit = {}
) {
    private enum class Disposition { ARMED, FIRED, CANCELLED }

    private data class Entry(
        val contract: ForegroundTimerContract,
        val token: Long,
        val cancellation: ForegroundTimerCancellation
    )

    private val tokenCounter = AtomicLong(0L)
    private val entries = mutableMapOf<String, Entry>()
    private val latestGeneration = mutableMapOf<String, Long>()
    private val dispositions = mutableMapOf<String, Disposition>()

    @Synchronized
    fun arm(contract: ForegroundTimerContract): ForegroundTimerArmResult {
        val latest = latestGeneration[contract.timerId]
        if (latest != null && contract.generation < latest) {
            return ForegroundTimerArmResult(armed = false, stale = true)
        }
        val existing = entries[contract.timerId]
        if (existing?.contract?.generation == contract.generation) {
            return ForegroundTimerArmResult(armed = false, duplicate = true)
        }
        if (
            latest == contract.generation && existing == null &&
            dispositions[contract.timerId] == Disposition.CANCELLED
        ) {
            return ForegroundTimerArmResult(armed = false, terminalGeneration = true)
        }

        val replaced = existing?.contract?.generation
        existing?.cancellation?.cancel()
        val token = tokenCounter.incrementAndGet()
        val cancellation = scheduler.schedule(contract.delayMs) {
            val fire = synchronized(this) {
                val current = entries[contract.timerId]
                if (current?.token != token || current.contract.generation != contract.generation) {
                    null
                } else {
                    entries.remove(contract.timerId)
                    dispositions[contract.timerId] = Disposition.FIRED
                    ForegroundTimerFire(
                        contract.timerId,
                        contract.correlationId,
                        contract.generation
                    )
                }
            }
            fire?.let(onFire)
        }
        entries[contract.timerId] = Entry(contract, token, cancellation)
        latestGeneration[contract.timerId] = contract.generation
        dispositions[contract.timerId] = Disposition.ARMED
        return ForegroundTimerArmResult(armed = true, replacedGeneration = replaced)
    }

    @Synchronized
    fun cancel(contract: ForegroundTimerContract): ForegroundTimerCancelResult {
        val existing = entries[contract.timerId]
        if (existing == null || existing.contract.generation != contract.generation) {
            return ForegroundTimerCancelResult(
                cancelled = false,
                generationMatched = false,
                activeGeneration = existing?.contract?.generation
            )
        }
        entries.remove(contract.timerId)
        dispositions[contract.timerId] = Disposition.CANCELLED
        existing.cancellation.cancel()
        return ForegroundTimerCancelResult(
            cancelled = true,
            generationMatched = true,
            activeGeneration = null
        )
    }

    @Synchronized
    fun activeGeneration(timerId: String): Long? = entries[timerId]?.contract?.generation

    @Synchronized
    fun cancelAllForBackground(): Int {
        val active = entries.values.toList()
        entries.clear()
        active.forEach { entry ->
            dispositions[entry.contract.timerId] = Disposition.CANCELLED
            entry.cancellation.cancel()
        }
        return active.size
    }
}

class ForegroundTimerArmHandler(private val registry: ForegroundTimerRegistry) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val contract = parseForegroundTimerContract(request.paramsJson())
            .getOrElse { return invalidParams(it.message ?: "invalid timer contract") }
        val result = registry.arm(contract)
        if (result.stale || result.terminalGeneration) {
            return HostReply.error(
                STALE_GENERATION,
                "$CAPABILITY rejected generation=${contract.generation} for timerId=${contract.timerId}",
                false
            )
        }
        return HostReply.complete(JSONObject()
            .put("armed", result.armed)
            .put("duplicate", result.duplicate)
            .put("timerId", contract.timerId)
            .put("correlationId", contract.correlationId)
            .put("generation", contract.generation)
            .put("replacedGeneration", result.replacedGeneration ?: JSONObject.NULL)
            .toString())
    }

    private fun invalidParams(message: String): HostReply =
        HostReply.error(INVALID_PARAMS, "$CAPABILITY $message", false)

    companion object {
        const val CAPABILITY = "timer.foreground.arm"
        private const val INVALID_PARAMS = "INVALID_PARAMS"
        private const val STALE_GENERATION = "STALE_GENERATION"
    }
}

class ForegroundTimerCancelHandler(private val registry: ForegroundTimerRegistry) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val contract = parseForegroundTimerContract(request.paramsJson())
            .getOrElse {
                return HostReply.error(
                    INVALID_PARAMS,
                    "$CAPABILITY ${it.message ?: "invalid timer contract"}",
                    false
                )
            }
        val result = registry.cancel(contract)
        return HostReply.complete(JSONObject()
            .put("cancelled", result.cancelled)
            .put("generationMatched", result.generationMatched)
            .put("timerId", contract.timerId)
            .put("correlationId", contract.correlationId)
            .put("generation", contract.generation)
            .put("activeGeneration", result.activeGeneration ?: JSONObject.NULL)
            .toString())
    }

    companion object {
        const val CAPABILITY = "timer.foreground.cancel"
        private const val INVALID_PARAMS = "INVALID_PARAMS"
    }
}

private fun parseForegroundTimerContract(paramsJson: String): Result<ForegroundTimerContract> = runCatching {
    val params = JSONObject(paramsJson)
    val expectedFields = setOf(
        "timerId",
        "correlationId",
        "delayMs",
        "generation",
        "oneShot",
        "foregroundOnly"
    )
    val actualFields = params.keys().asSequence().toSet()
    require(actualFields == expectedFields) {
        "requires exactly ${expectedFields.sorted()}, got ${actualFields.sorted()}"
    }
    val timerId = params.get("timerId") as? String
        ?: error("requires string timerId")
    val correlationId = params.get("correlationId") as? String
        ?: error("requires string correlationId")
    require(timerId.isNotBlank()) { "requires non-blank timerId" }
    require(correlationId == timerId) { "requires correlationId == timerId" }
    val delayNumber = params.get("delayMs") as? Number
        ?: error("requires integer delayMs")
    val delayMs = delayNumber.toLong()
    require(delayNumber.toDouble().isFinite() && delayNumber.toDouble() == delayMs.toDouble()) {
        "requires integer delayMs"
    }
    require(delayMs in FOREGROUND_TIMER_MIN_DELAY_MS..FOREGROUND_TIMER_MAX_DELAY_MS) {
        "requires delayMs=$FOREGROUND_TIMER_MIN_DELAY_MS..$FOREGROUND_TIMER_MAX_DELAY_MS"
    }
    val generationNumber = params.get("generation") as? Number
        ?: error("requires integer generation")
    val generation = generationNumber.toLong()
    require(generationNumber.toDouble().isFinite() && generationNumber.toDouble() == generation.toDouble()) {
        "requires integer generation"
    }
    require(generation >= 1L) { "requires generation >= 1" }
    val oneShot = params.get("oneShot") as? Boolean
        ?: error("requires boolean oneShot")
    val foregroundOnly = params.get("foregroundOnly") as? Boolean
        ?: error("requires boolean foregroundOnly")
    require(oneShot) { "requires oneShot=true" }
    require(foregroundOnly) { "requires foregroundOnly=true" }
    ForegroundTimerContract(
        timerId,
        correlationId,
        delayMs,
        generation,
        oneShot,
        foregroundOnly
    )
}
