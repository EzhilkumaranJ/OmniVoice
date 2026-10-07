package com.example.service

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import com.example.data.model.InteractionMemory

/**
 * Handles seamless system and external integration for Google Calendar and Google Tasks.
 * Dispatches deep Intents that directly create events/tasks in the official Google Calendar
 * and Google Tasks apps (or fallback web/system intents).
 */
object GoogleSyncManager {

    /**
     * Dispatches an Intent to Google Calendar to add an event/reminder,
     * with prefilled title, description, time, and recurring rules.
     */
    fun syncToGoogleCalendar(context: Context, memory: InteractionMemory): Boolean {
        return try {
            val startTime = memory.extractedDueDate ?: (System.currentTimeMillis() + 3600 * 1000)
            val endTime = startTime + 3600 * 1000

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, memory.summary.ifBlank { memory.rawText.take(40) })
                putExtra(CalendarContract.Events.DESCRIPTION, "OmniVoice AI Voice Context:\n${memory.rawText}\nTags: ${memory.tagsCsv}")
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)

                // Add recurring rule if present
                when (memory.recurringPattern?.uppercase()) {
                    "DAILY" -> putExtra(CalendarContract.Events.RRULE, "FREQ=DAILY")
                    "WEEKLY" -> putExtra(CalendarContract.Events.RRULE, "FREQ=WEEKLY")
                    "MONTHLY" -> putExtra(CalendarContract.Events.RRULE, "FREQ=MONTHLY")
                }

                // Suggest Google Calendar package
                setPackage("com.google.android.calendar")
            }

            try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                // If specific Google Calendar package isn't installed, launch default system calendar provider
                val genericIntent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, memory.summary.ifBlank { memory.rawText.take(40) })
                    putExtra(CalendarContract.Events.DESCRIPTION, "OmniVoice AI:\n${memory.rawText}")
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
                }
                context.startActivity(genericIntent)
                true
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Calendar Sync: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Dispatches an Intent to Google Tasks app or standard task share handler.
     */
    fun syncToGoogleTasks(context: Context, memory: InteractionMemory): Boolean {
        return try {
            // First attempt Google Tasks explicit share or open
            val title = memory.summary.ifBlank { memory.rawText.take(50) }
            val details = "OmniVoice Task:\n${memory.rawText}\nDue: ${memory.extractedDueDate?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "Flexible"}"

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "$title\n$details")
                setPackage("com.google.android.apps.tasks")
            }

            try {
                context.startActivity(sendIntent)
                true
            } catch (_: ActivityNotFoundException) {
                // If Google Tasks app is not installed, open chooser to share with any task or note app (Keep, Todoist, etc.)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, "$title\n$details")
                }
                val chooser = Intent.createChooser(shareIntent, "Add to Google Tasks / ToDo")
                context.startActivity(chooser)
                true
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Tasks Sync: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
