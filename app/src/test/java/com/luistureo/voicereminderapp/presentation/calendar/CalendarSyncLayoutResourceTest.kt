package com.luistureo.voicereminderapp.presentation.calendar

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSyncLayoutResourceTest {

    @Test
    fun calendarSyncPanelHasTwoProviderButtonsAndNoPopupRecommendationText() {
        val layout = activityCalendarLayoutFile().readText()

        assertTrue(layout.contains("@+id/btnCalendarGoogleSync"))
        assertTrue(layout.contains("@+id/btnCalendarMicrosoftSync"))
        assertTrue(layout.contains("@+id/tvCalendarSyncStatus"))
        assertTrue(layout.contains("@+id/tvCalendarSyncInlineNotice"))
        assertFalse(layout.contains("@+id/tvCalendarGoogleProviderState"))
        assertFalse(layout.contains("@+id/tvCalendarMicrosoftProviderState"))
        assertTrue(layout.contains("@+id/containerCalendarSyncError"))
        assertFalse(layout.contains("@+id/btnCalendarSyncRetry"))
        assertFalse(layout.contains("@+id/tvCalendarSyncInfo"))
        assertFalse(layout.contains("@+id/tvCalendarGoogleStatus"))
        assertFalse(layout.contains("calendar_duplicate_alert_recommendation"))
        assertTrue(
            sourceFile("app/src/main/res/values/strings.xml")
                .readText()
                .contains("La cita se elimin&#243; de los calendarios conectados.")
        )
    }

    @Test
    fun compactCardDefinesRequiredCompactTitles() {
        val strings = sourceFile("app/src/main/res/values/strings.xml").readText()

        assertTrue(strings.contains("Sin calendarios conectados"))
        assertTrue(strings.contains("Google conectado"))
        assertTrue(strings.contains("Google pausado"))
        assertTrue(strings.contains("Microsoft conectado"))
        assertTrue(strings.contains("Microsoft pausado"))
        assertTrue(strings.contains("Google y Microsoft conectados"))
    }

    @Test
    fun cardDefinesConnectActivatePauseAndCodedErrorsWithoutRetry() {
        val strings = sourceFile("app/src/main/res/values/strings.xml").readText()

        assertTrue(strings.contains("Conectar con Google"))
        assertTrue(strings.contains("Pausar calendario Google"))
        assertTrue(strings.contains("Activar calendario Google"))
        assertTrue(strings.contains("Conectar con Microsoft"))
        assertTrue(strings.contains("Pausar calendario Microsoft"))
        assertTrue(strings.contains("Activar calendario Microsoft"))
        assertTrue(strings.contains("Desconectar de Google"))
        assertTrue(strings.contains("Desconectar de Microsoft"))
        assertFalse(strings.contains("Desactivar Google"))
        assertFalse(strings.contains("Desactivar Microsoft"))
        assertTrue(strings.contains("GOOGLE_AUTH_BAD_AUTHENTICATION"))
        assertTrue(strings.contains("GOOGLE_CALENDAR_API_429"))
        assertTrue(strings.contains("GOOGLE_SYNC_IO_EXCEPTION"))
        assertTrue(strings.contains("MICROSOFT_GRAPH_401"))
        assertTrue(strings.contains("MICROSOFT_AUTH_MSAL_CLIENT_ERROR"))
        assertFalse(strings.contains("calendar_sync_retry"))
    }

    @Test
    fun providerButtonsUseFullWidthAccessibleTouchTargets() {
        val layout = activityCalendarLayoutFile().readText()
        val google = layout.substringAfter("@+id/btnCalendarGoogleSync")
            .substringBefore("/>")
        val microsoft = layout.substringAfter("@+id/btnCalendarMicrosoftSync")
            .substringBefore("/>")

        assertTrue(google.contains("android:layout_width=\"match_parent\""))
        assertTrue(microsoft.contains("android:layout_width=\"match_parent\""))
        assertTrue(google.contains("android:minHeight=\"48dp\""))
        assertTrue(microsoft.contains("android:minHeight=\"48dp\""))
        assertTrue(google.contains("android:textColor=\"@color/calendar_title_text\""))
        assertTrue(microsoft.contains("android:textColor=\"@color/calendar_title_text\""))
        assertTrue(google.contains("app:strokeColor=\"@color/calendar_title_text\""))
        assertTrue(microsoft.contains("app:strokeColor=\"@color/calendar_title_text\""))
    }

    @Test
    fun disconnectActionsRemainRedStackedAndVisuallySeparated() {
        val layout = activityCalendarLayoutFile().readText()
        val disconnect = layout.substringAfter(
            "@+id/containerCalendarDisconnectActions"
        ).substringBefore("</com.google.android.material.card.MaterialCardView>")

        assertTrue(disconnect.contains("android:layout_marginTop=\"10dp\""))
        assertTrue(disconnect.contains("android:orientation=\"vertical\""))
        assertTrue(disconnect.contains("@+id/btnCalendarGoogleDisconnect"))
        assertTrue(disconnect.contains("@+id/btnCalendarMicrosoftDisconnect"))
        assertTrue(disconnect.contains("android:minHeight=\"48dp\""))
        assertTrue(disconnect.contains("android:textColor=\"@color/reminder_delete_red\""))
        assertTrue(disconnect.contains("app:strokeColor=\"@color/reminder_delete_red\""))
    }

    @Test
    fun statusTitleCanWrapWithoutTruncation() {
        val layout = activityCalendarLayoutFile().readText()
        val title = layout.substringAfter("@+id/tvCalendarSyncStatus")
            .substringBefore("/>")

        assertFalse(title.contains("android:ellipsize"))
        assertFalse(title.contains("android:maxLines=\"1\""))
    }

    @Test
    fun meetingCardKeepsCompactVisualOrderAndConditionalJoinAction() {
        val layout = sourceFile("app/src/main/res/layout/item_calendar_detail.xml").readText()
        val title = layout.indexOf("@+id/tvCalendarDetailTitle")
        val description = layout.indexOf("@+id/tvCalendarDetailDescription")
        val time = layout.indexOf("@+id/tvCalendarDetailTime")
        val category = layout.indexOf("@+id/tvCalendarDetailType")
        val provider = layout.indexOf("@+id/tvCalendarDetailProvider")
        val join = layout.indexOf("@+id/btnCalendarOpenMeeting")

        assertTrue(title < time)
        assertTrue(time < category)
        assertTrue(category < description)
        assertTrue(description < provider)
        assertTrue(provider < join)
        assertTrue(layout.substring(join).contains("android:visibility=\"gone\""))
        assertTrue(layout.substring(join).contains("@drawable/ic_meeting_video"))
        assertTrue(layout.substring(join).contains("@color/primary_blue"))
    }

    @Test
    fun meetingCardUsesWrapContentWithoutReservedEmptySpace() {
        val layout = sourceFile("app/src/main/res/layout/item_calendar_detail.xml").readText()
        val activity = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                    "CalendarActivity.kt"
        ).readText()

        assertTrue(
            layout.substringAfter("<com.google.android.material.card.MaterialCardView")
                .substringBefore(">").contains("android:layout_height=\"wrap_content\"")
        )
        assertFalse(layout.contains("<Space"))
        assertFalse(layout.contains("spaceCalendarDetailActions"))
        assertFalse(layout.contains("android:minHeight"))
        assertTrue(layout.contains("android:paddingHorizontal=\"16dp\""))
        assertTrue(layout.contains("android:paddingVertical=\"15dp\""))
        assertTrue(layout.contains("android:layout_marginTop=\"8dp\""))
        assertTrue(layout.contains("android:layout_marginTop=\"7dp\""))
        assertTrue(layout.contains("android:layout_marginTop=\"10dp\""))
        assertTrue(activity.contains("descriptionView.isVisible = renderedDescription.isNotBlank()"))
        assertTrue(activity.contains("actionsContainer.isVisible = canOpenMeeting ||"))
        assertTrue(activity.contains("syncGoogleButton.isVisible"))
        assertTrue(activity.contains("syncMicrosoftButton.isVisible"))
    }

    @Test
    fun optionalCardSectionsAreGoneAndMetadataWrapsOnNarrowScreens() {
        val layout = sourceFile("app/src/main/res/layout/item_calendar_detail.xml").readText()
        val optionalIds = listOf(
            "tvCalendarDetailDescription",
            "tvCalendarDetailScheduleConflict",
            "tvCalendarDetailProvider",
            "tvCalendarDetailExternalNote",
            "containerCalendarDetailActions",
            "btnCalendarReactivate",
            "btnCalendarSyncGoogle",
            "btnCalendarSyncMicrosoft",
            "btnCalendarOpenMeeting"
        )

        optionalIds.forEach { id ->
            val element = layout.substringAfter("@+id/$id").substringBefore("/>")
            assertTrue("$id debe usar GONE", element.contains("android:visibility=\"gone\""))
            assertFalse("$id no debe usar INVISIBLE", element.contains("invisible", true))
        }
        assertTrue(layout.contains("com.google.android.material.chip.ChipGroup"))
        assertTrue(layout.contains("app:singleLine=\"false\""))
        assertTrue(layout.contains("app:chipSpacingHorizontal=\"6dp\""))
        val title = layout.substringAfter("@+id/tvCalendarDetailTitle").substringBefore("/>")
        val provider = layout.substringAfter("@+id/tvCalendarDetailProvider")
            .substringBefore("/>")
        assertTrue(title.contains("android:layout_width=\"0dp\""))
        assertTrue(title.contains("android:layout_weight=\"1\""))
        assertTrue(provider.contains("android:layout_width=\"match_parent\""))
        assertFalse(provider.contains("android:maxLines"))
        assertFalse(provider.contains("android:ellipsize"))
        assertTrue(
            layout.substringAfter("@+id/btnCalendarOpenMeeting")
                .substringBefore("/>")
                .contains("android:layout_width=\"match_parent\"")
        )
    }

    @Test
    fun appCreatedCalendarCardsOpenManualEditorWithReminderId() {
        val activity = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                    "CalendarActivity.kt"
        ).readText()
        val viewModel = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                    "CalendarViewModel.kt"
        ).readText()
        val strings = sourceFile("app/src/main/res/values/strings.xml").readText()

        assertTrue(activity.contains("detailView.isClickable = detail.canEdit"))
        assertTrue(activity.contains("openReminderEditor(detail)"))
        assertTrue(activity.contains("ManualReminderActivity.EXTRA_REMINDER_ID"))
        assertTrue(viewModel.contains("originProvider == CalendarProvider.APP"))
        assertTrue(viewModel.contains("!isOnlineMeeting"))
        assertTrue(strings.contains("Sincronizar con:"))
    }

    private fun activityCalendarLayoutFile(): File {
        return sourceFile("app/src/main/res/layout/activity_calendar.xml")
    }

    private fun sourceFile(projectPath: String): File {
        val fromRoot = File(projectPath)
        if (fromRoot.exists()) return fromRoot
        return File(projectPath.removePrefix("app/"))
    }
}
