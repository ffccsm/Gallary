package com.example.util

import com.example.domain.model.MediaItem
import com.example.domain.model.TimelineGroupingMode
import com.example.domain.model.TimelineSection
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateGroupingUtil {

    fun groupMedia(
        items: List<MediaItem>,
        mode: TimelineGroupingMode,
        nowMillis: Long = System.currentTimeMillis()
    ): List<TimelineSection> {
        if (items.isEmpty()) return emptyList()

        val sorted = if (isSortedDescending(items)) {
            items
        } else {
            items.sortedByDescending { it.effectiveDateMillis }
        }

        return when (mode) {
            TimelineGroupingMode.ALL -> {
                groupSmartTimeline(sorted, nowMillis)
            }
            TimelineGroupingMode.DAYS -> {
                val dayFmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
                val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                sorted.groupBy { keyFmt.format(Date(it.effectiveDateMillis)) }
                    .map { (key, sectionItems) ->
                        val firstTimestamp = sectionItems.first().effectiveDateMillis
                        TimelineSection(
                            key = "day_$key",
                            title = formatDayBucketTitle(firstTimestamp, nowMillis, dayFmt),
                            subtitle = "${sectionItems.size} ${if (sectionItems.size == 1) "item" else "items"}",
                            items = sectionItems
                        )
                    }
            }
            TimelineGroupingMode.MONTHS -> {
                val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                val keyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
                sorted.groupBy { keyFmt.format(Date(it.effectiveDateMillis)) }
                    .map { (key, sectionItems) ->
                        val firstDate = Date(sectionItems.first().effectiveDateMillis)
                        TimelineSection(
                            key = "month_$key",
                            title = monthFmt.format(firstDate),
                            subtitle = "${sectionItems.size} ${if (sectionItems.size == 1) "item" else "items"}",
                            items = sectionItems
                        )
                    }
            }
            TimelineGroupingMode.YEARS -> {
                val yearFmt = SimpleDateFormat("yyyy", Locale.US)
                sorted.groupBy { yearFmt.format(Date(it.effectiveDateMillis)) }
                    .map { (yearKey, sectionItems) ->
                        TimelineSection(
                            key = "year_$yearKey",
                            title = yearKey,
                            subtitle = "${sectionItems.size} ${if (sectionItems.size == 1) "item" else "items"}",
                            items = sectionItems
                        )
                    }
            }
        }
    }

    private fun isSortedDescending(items: List<MediaItem>): Boolean {
        for (i in 1 until items.size) {
            if (items[i - 1].effectiveDateMillis < items[i].effectiveDateMillis) {
                return false
            }
        }
        return true
    }

    private fun groupSmartTimeline(
        sortedItems: List<MediaItem>,
        nowMillis: Long
    ): List<TimelineSection> {
        val reusableCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentYear = reusableCal.get(Calendar.YEAR)

        reusableCal.set(Calendar.HOUR_OF_DAY, 0)
        reusableCal.set(Calendar.MINUTE, 0)
        reusableCal.set(Calendar.SECOND, 0)
        reusableCal.set(Calendar.MILLISECOND, 0)
        val startOfToday = reusableCal.timeInMillis

        val startOfYesterday = startOfToday - 24L * 60L * 60L * 1000L
        val startOfThisWeek = startOfToday - 6L * 24L * 60L * 60L * 1000L

        val monthTitleFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val currentYearMonthFmt = SimpleDateFormat("MMMM", Locale.getDefault())
        val monthKeyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val reusableDate = Date()

        val buckets = LinkedHashMap<String, MutableList<MediaItem>>()
        val bucketTitles = LinkedHashMap<String, String>()

        for (item in sortedItems) {
            val timestamp = item.effectiveDateMillis
            val (key, title) = when {
                timestamp >= startOfToday -> "smart_today" to "Today"
                timestamp >= startOfYesterday -> "smart_yesterday" to "Yesterday"
                timestamp >= startOfThisWeek -> "smart_this_week" to "This Week"
                else -> {
                    reusableDate.time = timestamp
                    reusableCal.timeInMillis = timestamp
                    val mKey = "smart_${monthKeyFmt.format(reusableDate)}"
                    val mTitle = bucketTitles[mKey] ?: if (reusableCal.get(Calendar.YEAR) == currentYear) {
                        currentYearMonthFmt.format(reusableDate)
                    } else {
                        monthTitleFmt.format(reusableDate)
                    }
                    mKey to mTitle
                }
            }
            buckets.getOrPut(key) { mutableListOf() }.add(item)
            if (!bucketTitles.containsKey(key)) {
                bucketTitles[key] = title
            }
        }

        return buckets.map { (key, list) ->
            val locationSummary = list.firstOrNull { !it.locationLabel.isNullOrBlank() }?.locationLabel
            TimelineSection(
                key = key,
                title = bucketTitles[key] ?: "",
                subtitle = locationSummary,
                items = list
            )
        }
    }

    private fun formatDayBucketTitle(
        timestamp: Long,
        nowMillis: Long,
        fallbackFormat: SimpleDateFormat
    ): String {
        val startOfToday = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val startOfYesterday = startOfToday - 24L * 60L * 60L * 1000L

        return when {
            timestamp >= startOfToday -> "Today"
            timestamp >= startOfYesterday -> "Yesterday"
            else -> fallbackFormat.format(Date(timestamp))
        }
    }

    fun formatFullDateTime(millis: Long): String {
        val fmt = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        return fmt.format(Date(millis))
    }

    fun formatShortDate(millis: Long): String {
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return fmt.format(Date(millis))
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "Unknown size"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", kb)
        }
    }
}
