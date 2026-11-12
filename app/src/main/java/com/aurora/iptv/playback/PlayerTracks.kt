package com.aurora.iptv.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import java.util.Locale

data class TrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean
)

fun audioTrackOptions(player: Player): List<TrackOption> =
    trackOptions(player, C.TRACK_TYPE_AUDIO)

fun textTrackOptions(player: Player): List<TrackOption> =
    trackOptions(player, C.TRACK_TYPE_TEXT)

private fun trackOptions(player: Player, type: Int): List<TrackOption> {
    val result = mutableListOf<TrackOption>()
    player.currentTracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != type) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex)) continue
            result += TrackOption(
                groupIndex = groupIndex,
                trackIndex = trackIndex,
                label = trackLabel(group, trackIndex),
                selected = group.isTrackSelected(trackIndex)
            )
        }
    }
    return result
}

private fun trackLabel(group: Tracks.Group, trackIndex: Int): String {
    val format = group.getTrackFormat(trackIndex)
    val parts = mutableListOf<String>()
    format.label?.takeIf { it.isNotBlank() }?.let { parts += it }
    val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
    if (language != null) {
        runCatching {
            parts += Locale.forLanguageTag(language).getDisplayLanguage(Locale.getDefault())
        }
    }
    val codec = format.codecs?.substringBefore('.')?.takeIf { it.isNotBlank() }
    if (codec != null && parts.none { it.equals(codec, true) }) parts += codec
    if (parts.isEmpty()) parts += "Track ${trackIndex + 1}"
    return parts.distinct().joinToString(" · ")
}

fun selectAudioTrack(player: Player, option: TrackOption?) {
    val builder = player.trackSelectionParameters.buildUpon()
    if (option == null) {
        builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
    } else {
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        if (group.type != C.TRACK_TYPE_AUDIO) return
        builder.setOverrideForType(
            TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
        )
    }
    player.trackSelectionParameters = builder.build()
}

fun selectTextTrack(player: Player, option: TrackOption?) {
    val builder = player.trackSelectionParameters.buildUpon()
    builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
    if (option == null) {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
    } else {
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        if (group.type != C.TRACK_TYPE_TEXT) return
        builder.setOverrideForType(
            TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
        )
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
    }
    player.trackSelectionParameters = builder.build()
}

fun disableTextTrack(player: Player) {
    val builder = player.trackSelectionParameters.buildUpon()
    builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    player.trackSelectionParameters = builder.build()
}
