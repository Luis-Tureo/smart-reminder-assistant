package com.luistureo.voicereminderapp.ios

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrence
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit
import com.luistureo.voicereminderapp.domain.model.ReminderScheduleState
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.model.ReminderType
import com.luistureo.voicereminderapp.domain.model.ReminderWeekday
import com.luistureo.voicereminderapp.domain.repository.ReminderRepository
import platform.Foundation.NSUserDefaults

class IosReminderRepository(
    private val userDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : ReminderRepository {

    override suspend fun insertReminder(reminder: Reminder): Int {
        val reminders = loadReminders().toMutableList()
        val nextId = (reminders.maxOfOrNull { it.id } ?: 0) + 1

        reminders += reminder.copy(id = nextId)
        persistReminders(reminders)

        return nextId
    }

    override suspend fun getAllReminders(): List<Reminder> {
        return loadReminders()
    }

    override suspend fun getReminderById(reminderId: Int): Reminder? {
        return loadReminders().firstOrNull { it.id == reminderId }
    }

    override suspend fun deleteReminder(reminder: Reminder) {
        val remainingReminders = loadReminders()
            .filterNot { it.id == reminder.id }

        persistReminders(remainingReminders)
    }

    override suspend fun updateReminder(reminder: Reminder) {
        val updatedReminders = loadReminders().map { existingReminder ->
            if (existingReminder.id == reminder.id) reminder else existingReminder
        }

        persistReminders(updatedReminders)
    }

    private fun loadReminders(): List<Reminder> {
        val rawStorage = userDefaults.stringForKey(STORAGE_KEY).orEmpty()
        if (rawStorage.isBlank()) return emptyList()

        return rawStorage
            .split(LINE_SEPARATOR)
            .mapNotNull(::decodeReminder)
    }

    private fun persistReminders(reminders: List<Reminder>) {
        if (reminders.isEmpty()) {
            userDefaults.removeObjectForKey(STORAGE_KEY)
            return
        }

        userDefaults.setObject(
            reminders.joinToString(separator = LINE_SEPARATOR, transform = ::encodeReminder),
            forKey = STORAGE_KEY
        )
    }

    private fun encodeReminder(reminder: Reminder): String {
        return listOf(
            reminder.id.toString(),
            escape(reminder.title),
            escape(reminder.detail),
            reminder.scheduledAtEpochMillis.toString(),
            encodeBoolean(reminder.isCompleted),
            reminder.type.name,
            encodeBoolean(reminder.isUrgent),
            reminder.source.name,
            encodeRecurrence(reminder.recurrence),
            encodeNullableLong(reminder.scheduleState.nextTriggerAtEpochMillis),
            encodeNullableLong(reminder.scheduleState.lastTriggeredAtEpochMillis),
            encodeNullableLong(reminder.scheduleState.activeAlertAtEpochMillis),
            reminder.scheduleState.activeAlertRepeatCount.toString(),
            encodeNullableLong(reminder.scheduleState.nextUrgentRepeatAtEpochMillis)
        ).joinToString(separator = FIELD_SEPARATOR.toString())
    }

    private fun decodeReminder(value: String): Reminder? {
        val fields = value.split(FIELD_SEPARATOR)
        if (fields.size != FIELD_COUNT) return null

        val id = fields[0].toIntOrNull() ?: return null
        val title = unescape(fields[1])
        val detail = unescape(fields[2])
        val scheduledAtEpochMillis = fields[3].toLongOrNull() ?: return null
        val isCompleted = decodeBoolean(fields[4])
        val type = runCatching { ReminderType.valueOf(fields[5]) }.getOrNull() ?: return null
        val isUrgent = decodeBoolean(fields[6])
        val source = runCatching { ReminderSource.valueOf(fields[7]) }.getOrNull() ?: return null
        val recurrence = decodeRecurrence(fields[8])
        val nextTriggerAtEpochMillis = decodeNullableLong(fields[9])
        val lastTriggeredAtEpochMillis = decodeNullableLong(fields[10])
        val activeAlertAtEpochMillis = decodeNullableLong(fields[11])
        val activeAlertRepeatCount = fields[12].toIntOrNull() ?: return null
        val nextUrgentRepeatAtEpochMillis = decodeNullableLong(fields[13])

        return Reminder(
            id = id,
            title = title,
            detail = detail,
            scheduledAtEpochMillis = scheduledAtEpochMillis,
            isCompleted = isCompleted,
            type = type,
            isUrgent = isUrgent,
            source = source,
            recurrence = recurrence,
            scheduleState = ReminderScheduleState(
                nextTriggerAtEpochMillis = nextTriggerAtEpochMillis,
                lastTriggeredAtEpochMillis = lastTriggeredAtEpochMillis,
                activeAlertAtEpochMillis = activeAlertAtEpochMillis,
                activeAlertRepeatCount = activeAlertRepeatCount,
                nextUrgentRepeatAtEpochMillis = nextUrgentRepeatAtEpochMillis
            )
        )
    }

    private fun encodeRecurrence(recurrence: ReminderRecurrence?): String {
        recurrence ?: return ""

        val encodedWeekdays = recurrence.weekdays
            .sortedBy { it.isoDayNumber }
            .joinToString(separator = WEEKDAY_SEPARATOR.toString()) { it.name }

        return listOf(
            recurrence.unit.name,
            recurrence.interval.toString(),
            encodedWeekdays,
            encodeBoolean(recurrence.isActive)
        ).joinToString(separator = RECURRENCE_SEPARATOR.toString())
    }

    private fun decodeRecurrence(value: String): ReminderRecurrence? {
        if (value.isBlank()) return null

        val fields = value.split(RECURRENCE_SEPARATOR)
        if (fields.size != RECURRENCE_FIELD_COUNT) return null

        val unit = runCatching { ReminderRecurrenceUnit.valueOf(fields[0]) }.getOrNull() ?: return null
        val interval = fields[1].toIntOrNull() ?: return null
        val weekdays = fields[2]
            .takeIf { it.isNotBlank() }
            ?.split(WEEKDAY_SEPARATOR)
            ?.mapNotNull { weekdayName ->
                runCatching { ReminderWeekday.valueOf(weekdayName) }.getOrNull()
            }
            ?.toSet()
            ?: emptySet()
        val isActive = decodeBoolean(fields[3])

        return ReminderRecurrence(
            unit = unit,
            interval = interval,
            weekdays = weekdays,
            isActive = isActive
        )
    }

    private fun encodeNullableLong(value: Long?): String = value?.toString().orEmpty()

    private fun decodeNullableLong(value: String): Long? = value.toLongOrNull()

    private fun encodeBoolean(value: Boolean): String = if (value) "1" else "0"

    private fun decodeBoolean(value: String): Boolean = value == "1"

    private fun escape(value: String): String {
        return buildString {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '\t' -> append("\\t")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(character)
                }
            }
        }
    }

    private fun unescape(value: String): String {
        return buildString {
            var index = 0

            while (index < value.length) {
                val currentCharacter = value[index]

                if (currentCharacter == '\\' && index + 1 < value.length) {
                    when (value[index + 1]) {
                        '\\' -> append('\\')
                        't' -> append('\t')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        else -> append(value[index + 1])
                    }
                    index += 2
                } else {
                    append(currentCharacter)
                    index += 1
                }
            }
        }
    }

    private companion object {
        private const val STORAGE_KEY = "ios_reminders_storage_v1"
        private const val FIELD_COUNT = 14
        private const val RECURRENCE_FIELD_COUNT = 4
        private const val LINE_SEPARATOR = "\n"
        private const val FIELD_SEPARATOR = '\t'
        private const val RECURRENCE_SEPARATOR = ';'
        private const val WEEKDAY_SEPARATOR = ','
    }
}
