package com.tyler.scenegram.model

/**
 * Absolute monotonic timestamps recorded by the controller. A missing anchor deliberately leaves
 * its cues unresolved, which lets the actor type for as long as needed before triggering replies.
 */
data class ScriptAnchors(
    val sceneStartedAtMs: Long? = null,
    val signalTimesMs: Map<String, Long> = emptyMap(),
) {
    init {
        require(sceneStartedAtMs == null || sceneStartedAtMs >= 0L) {
            "Scene start time cannot be negative"
        }
        require(signalTimesMs.keys.none { it.isBlank() }) { "Signal keys cannot be blank" }
        require(signalTimesMs.values.none { it < 0L }) { "Signal times cannot be negative" }
    }

    fun withSignal(key: String, atMs: Long): ScriptAnchors {
        require(key.isNotBlank()) { "Signal keys cannot be blank" }
        require(atMs >= 0L) { "Signal times cannot be negative" }
        // Signals are latches: an accidental second tap/send must not move an armed cue.
        if (key in signalTimesMs) return this
        return copy(signalTimesMs = signalTimesMs + (key to atMs))
    }
}

data class ScheduledCue(
    val cue: SceneCue,
    val dueAtMs: Long,
    /** Preserves manifest order when multiple cues share the same timestamp. */
    val declarationIndex: Int,
)

/** Deterministic timeline calculations with no clock, Android, coroutine, or storage dependency. */
object ScriptTimeline {
    fun scheduledCues(
        preset: ScenePreset,
        anchors: ScriptAnchors,
    ): List<ScheduledCue> =
        preset.cues
            .mapIndexedNotNull { index, cue ->
                resolveAnchor(cue.anchor, anchors)?.let { anchorTime ->
                    ScheduledCue(
                        cue = cue,
                        dueAtMs = addDelay(anchorTime, cue.delayMs),
                        declarationIndex = index,
                    )
                }
            }
            .sortedWith(compareBy<ScheduledCue> { it.dueAtMs }.thenBy { it.declarationIndex })

    fun dueCues(
        preset: ScenePreset,
        anchors: ScriptAnchors,
        nowMs: Long,
        deliveredCueIds: Set<String> = emptySet(),
    ): List<ScheduledCue> {
        require(nowMs >= 0L) { "Current time cannot be negative" }
        return scheduledCues(preset, anchors).filter { scheduled ->
            scheduled.dueAtMs <= nowMs && scheduled.cue.id !in deliveredCueIds
        }
    }

    fun nextPendingCue(
        preset: ScenePreset,
        anchors: ScriptAnchors,
        deliveredCueIds: Set<String> = emptySet(),
    ): ScheduledCue? =
        scheduledCues(preset, anchors).firstOrNull { it.cue.id !in deliveredCueIds }

    private fun resolveAnchor(anchor: CueAnchor, anchors: ScriptAnchors): Long? =
        when (anchor) {
            CueAnchor.SceneStart -> anchors.sceneStartedAtMs
            is CueAnchor.Signal -> anchors.signalTimesMs[anchor.key]
        }

    private fun addDelay(anchorTimeMs: Long, delayMs: Long): Long {
        require(delayMs <= Long.MAX_VALUE - anchorTimeMs) { "Cue due time exceeds Long range" }
        return anchorTimeMs + delayMs
    }
}
