package com.luistureo.voicereminderapp.core.calendar.microsoft

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.CalendarProviderSyncState
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.repository.ReminderRepository
import com.luistureo.voicereminderapp.core.calendar.unified.UnifiedCalendarCardDisplayPolicy
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrosoftCalendarSynchronizerTest {

    @Test
    fun importsPrimaryCalendarEventIntoRoomCache() = runBlocking {
        val repository = FakeReminderRepository()
        val event = MicrosoftCalendarEvent(
            id = "microsoft-event-1",
            title = "Reunion de proyecto",
            detail = "Revision semanal",
            startAtEpochMillis = 1_900_000_000_000L,
            isAllDay = false,
            meetingUrl = "https://teams.microsoft.com/l/meetup-join/test"
        )
        val synchronizer = MicrosoftCalendarSynchronizer(
            gateway = FakeMicrosoftCalendarGateway(events = listOf(event)),
            reminderRepository = repository
        )

        val summary = synchronizer.importEvents(Instant.EPOCH, Instant.MAX)
        val imported = repository.getAllReminders().single()

        assertEquals(1, summary.importedCount)
        assertEquals(CalendarProvider.MICROSOFT_CALENDAR, imported.originProvider)
        assertEquals(
            "microsoft-event-1",
            imported.externalIdsByProvider[CalendarProvider.MICROSOFT_CALENDAR]
        )
        assertEquals(
            CalendarProviderSyncState.SYNCED,
            imported.providerSyncStates[CalendarProvider.MICROSOFT_CALENDAR]
        )
        assertEquals(
            "https://teams.microsoft.com/l/meetup-join/test",
            imported.meetingUrlsByProvider[CalendarProvider.MICROSOFT_CALENDAR]
        )
        assertEquals("https://teams.microsoft.com/l/meetup-join/test", imported.meetingUrl)
        assertFalse(CalendarProvider.GOOGLE_CALENDAR in imported.pendingCreateProviders)
    }

    @Test
    fun disconnectedMicrosoftGatewayReturnsSafelyWithoutApiCall() = runBlocking {
        val synchronizer = MicrosoftCalendarSynchronizer(
            gateway = FakeMicrosoftCalendarGateway(connected = false),
            reminderRepository = FakeReminderRepository()
        )

        val summary = synchronizer.importEvents(Instant.EPOCH, Instant.MAX)

        assertEquals(0, summary.importedCount)
        assertTrue(summary.isConfigured)
    }

    @Test
    fun unchangedMicrosoftEventIsNotImportedTwice() = runBlocking {
        val repository = FakeReminderRepository()
        val event = MicrosoftCalendarEvent(
            id = "microsoft-event-1",
            title = "Reunion",
            detail = "Revision",
            startAtEpochMillis = 1_900_000_000_000L,
            isAllDay = false,
            updatedAtEpochMillis = 200L
        )
        val synchronizer = MicrosoftCalendarSynchronizer(
            gateway = FakeMicrosoftCalendarGateway(events = listOf(event)),
            reminderRepository = repository
        )

        synchronizer.importEvents(Instant.EPOCH, Instant.MAX)
        val secondSummary = synchronizer.importEvents(Instant.EPOCH, Instant.MAX)

        assertEquals(0, secondSummary.importedCount)
        assertEquals(1, repository.getAllReminders().size)
    }

    @Test
    fun microsoftEditUpdatesAppWithoutQueuingGoogleUpdate() = runBlocking {
        val repository = FakeReminderRepository()
        repository.insertReminder(
            Reminder(
                title = "Titulo anterior",
                detail = "Detalle anterior",
                scheduledAtEpochMillis = 1_900_000_000_000L,
                googleCalendarEventId = "google-event-1",
                googleCalendarLastSyncAtEpochMillis = 100L,
                microsoftCalendarLastSyncAtEpochMillis = 100L,
                externalIdsByProvider = mapOf(
                    CalendarProvider.GOOGLE_CALENDAR to "google-event-1",
                    CalendarProvider.MICROSOFT_CALENDAR to "microsoft-event-1"
                )
            )
        )
        val event = MicrosoftCalendarEvent(
            id = "microsoft-event-1",
            title = "Titulo actualizado",
            detail = "Detalle actualizado",
            startAtEpochMillis = 1_900_000_100_000L,
            isAllDay = false,
            updatedAtEpochMillis = 200L
        )
        val synchronizer = MicrosoftCalendarSynchronizer(
            gateway = FakeMicrosoftCalendarGateway(events = listOf(event)),
            reminderRepository = repository
        )

        synchronizer.importEvents(Instant.EPOCH, Instant.MAX)
        val imported = repository.getAllReminders().single()

        assertEquals("Titulo actualizado", imported.title)
        assertEquals("Cita editada desde Microsoft Calendar", imported.externalEditNote)
        assertFalse(CalendarProvider.GOOGLE_CALENDAR in imported.pendingUpdateProviders)
    }

    @Test
    fun microsoftSuccessKeepsExistingGoogleLinkOnUnifiedCard() = runBlocking {
        val synchronizer = MicrosoftCalendarSynchronizer(
            gateway = FakeMicrosoftCalendarGateway(),
            reminderRepository = FakeReminderRepository()
        )
        val reminder = Reminder(
            title = "Reunion",
            detail = "Revision",
            scheduledAtEpochMillis = 1_900_000_000_000L,
            externalIdsByProvider = mapOf(CalendarProvider.GOOGLE_CALENDAR to "google-event-1"),
            syncedProviders = setOf(CalendarProvider.APP, CalendarProvider.GOOGLE_CALENDAR),
            pendingCreateProviders = setOf(CalendarProvider.MICROSOFT_CALENDAR)
        )

        val result = synchronizer.syncSavedReminder(reminder)

        assertTrue(CalendarProvider.GOOGLE_CALENDAR in result.syncedProviders)
        assertTrue(CalendarProvider.MICROSOFT_CALENDAR in result.syncedProviders)
        assertFalse(CalendarProvider.MICROSOFT_CALENDAR in result.pendingCreateProviders)
    }

    @Test
    fun microsoftImportKeepsGoogleMeetingLinkWhenGoogleIsOrigin() = runBlocking {
        val repository = FakeReminderRepository()
        val googleMeetingUrl = "https://meet.google.com/abc-defg-hij"
        repository.insertReminder(
            Reminder(
                title = "Reunion",
                detail = "Revision",
                scheduledAtEpochMillis = 1_900_000_000_000L,
                originProvider = CalendarProvider.GOOGLE_CALENDAR,
                microsoftCalendarLastSyncAtEpochMillis = 100L,
                externalIdsByProvider = mapOf(
                    CalendarProvider.MICROSOFT_CALENDAR to "microsoft-event-1"
                ),
                meetingUrl = googleMeetingUrl,
                meetingUrlsByProvider = mapOf(
                    CalendarProvider.GOOGLE_CALENDAR to googleMeetingUrl
                )
            )
        )
        val synchronizer = MicrosoftCalendarSynchronizer(
            gateway = FakeMicrosoftCalendarGateway(
                events = listOf(
                    MicrosoftCalendarEvent(
                        id = "microsoft-event-1",
                        title = "Reunion",
                        detail = "Revision",
                        startAtEpochMillis = 1_900_000_000_000L,
                        isAllDay = false,
                        meetingUrl = "https://teams.microsoft.com/l/meetup-join/test",
                        updatedAtEpochMillis = 200L
                    )
                )
            ),
            reminderRepository = repository
        )

        synchronizer.importEvents(Instant.EPOCH, Instant.MAX)
        val imported = repository.getAllReminders().single()

        assertEquals(googleMeetingUrl, imported.meetingUrl)
        assertTrue(
            CalendarProvider.MICROSOFT_CALENDAR in imported.meetingUrlsByProvider.keys
        )
    }

    @Test
    fun managedMicrosoftCopyMergesIntoGoogleOriginCardAndKeepsMetadata() = runBlocking {
        val repository = FakeReminderRepository()
        val meetUrl = "https://meet.google.com/abc-defg-hij"
        repository.insertReminder(
            Reminder(
                title = "Reunion",
                detail = "Revision",
                scheduledAtEpochMillis = 1_900_000_000_000L,
                googleCalendarEventId = "google-event-1",
                externalIdsByProvider = mapOf(
                    CalendarProvider.GOOGLE_CALENDAR to "google-event-1"
                ),
                originProvider = CalendarProvider.GOOGLE_CALENDAR,
                syncedProviders = setOf(CalendarProvider.APP, CalendarProvider.GOOGLE_CALENDAR),
                meetingUrl = meetUrl,
                meetingProvider = CalendarProvider.GOOGLE_CALENDAR,
                isOnlineMeeting = true,
                meetingUrlsByProvider = mapOf(CalendarProvider.GOOGLE_CALENDAR to meetUrl)
            )
        )
        val synchronizer = MicrosoftCalendarSynchronizer(
            FakeMicrosoftCalendarGateway(
                events = listOf(
                    MicrosoftCalendarEvent(
                        id = "microsoft-event-1",
                        title = "Reunion",
                        detail = "Revision",
                        startAtEpochMillis = 1_900_000_000_000L,
                        isAllDay = false,
                        originProviderHint = CalendarProvider.GOOGLE_CALENDAR,
                        isManagedCopy = true,
                        localIdHint = 1
                    )
                )
            ),
            repository
        )

        synchronizer.importEvents(Instant.EPOCH, Instant.MAX)
        val merged = repository.getAllReminders().single()
        val providerLines = UnifiedCalendarCardDisplayPolicy.buildProviderLines(merged)

        assertEquals(CalendarProvider.GOOGLE_CALENDAR, merged.originProvider)
        assertEquals(meetUrl, merged.meetingUrl)
        assertEquals("google-event-1", merged.googleCalendarEventId)
        assertEquals("microsoft-event-1", merged.microsoftCalendarEventId)
        assertEquals(2, merged.externalIdsByProvider.size)
        assertTrue(providerLines.contains("Origen: Google Calendar"))
        assertTrue(
            providerLines.contains(
                "Sincronizado con: Microsoft Calendar y Smart Reminder Assistant"
            )
        )
    }
}

private class FakeMicrosoftCalendarGateway(
    private val connected: Boolean = true,
    private val events: List<MicrosoftCalendarEvent> = emptyList()
) : MicrosoftCalendarGateway {
    override val isConfigured: Boolean = true
    override val isConnected: Boolean = connected

    override suspend fun upsertReminder(reminder: Reminder): String = "remote-event"

    override suspend fun deleteEvent(eventId: String) = Unit

    override suspend fun listEvents(
        timeMin: Instant,
        timeMax: Instant
    ): List<MicrosoftCalendarEvent> = events
}

private class FakeReminderRepository : ReminderRepository {
    private val reminders = mutableListOf<Reminder>()
    private var nextId = 1

    override suspend fun insertReminder(reminder: Reminder): Int {
        val id = nextId++
        reminders += reminder.copy(id = id)
        return id
    }

    override suspend fun getAllReminders(): List<Reminder> = reminders.toList()

    override suspend fun getReminderById(reminderId: Int): Reminder? {
        return reminders.firstOrNull { it.id == reminderId }
    }

    override suspend fun deleteReminder(reminder: Reminder) {
        reminders.removeAll { it.id == reminder.id }
    }

    override suspend fun updateReminder(reminder: Reminder) {
        val index = reminders.indexOfFirst { it.id == reminder.id }
        if (index >= 0) reminders[index] = reminder
    }
}
