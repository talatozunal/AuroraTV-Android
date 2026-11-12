package com.aurora.iptv.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeFormat {

    private fun formatter(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.getDefault())

    @Synchronized
    fun clock(ms: Long): String = formatter("HH:mm").format(Date(ms))

    @Synchronized
    fun clockWithSeconds(ms: Long): String = formatter("HH:mm:ss").format(Date(ms))

    @Synchronized
    fun dateLong(ms: Long): String = formatter("d MMMM, EEEE").format(Date(ms))

    @Synchronized
    fun dateTime(ms: Long): String = formatter("dd.MM.yyyy HH:mm").format(Date(ms))

    @Synchronized
    fun dayLabel(ms: Long): String = formatter("d MMM").format(Date(ms))

    fun range(start: Long, end: Long): String = "${clock(start)} – ${clock(end)}"

    fun duration(ms: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(ms.coerceAtLeast(0L))
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}s ${minutes}dk" else "${minutes}dk"
    }

    fun position(ms: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun floorToMinutes(ms: Long, minutes: Int): Long {
        val step = TimeUnit.MINUTES.toMillis(minutes.toLong())
        return ms - (ms % step)
    }
}
