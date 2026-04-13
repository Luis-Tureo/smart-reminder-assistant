package com.luistureo.voicereminderapp.core.reminder

import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderDraftResponseTemplateKeyResolverTest {

    @Test
    fun resolve_mapsMissingFamiliesAndFieldsToStableKeys() {
        assertEquals(
            ReminderDraftResponseTemplateKey.REQUEST_MISSING_TEXT,
            ReminderDraftResponseTemplateKeyResolver.resolve(
                ReminderDraftResponseFamily.REQUEST_MISSING_VALUE,
                ReminderDraftField.TEXT
            )
        )
        assertEquals(
            ReminderDraftResponseTemplateKey.REQUEST_MISSING_DATE,
            ReminderDraftResponseTemplateKeyResolver.resolve(
                ReminderDraftResponseFamily.REQUEST_MISSING_VALUE,
                ReminderDraftField.DATE
            )
        )
        assertEquals(
            ReminderDraftResponseTemplateKey.REQUEST_MISSING_TIME,
            ReminderDraftResponseTemplateKeyResolver.resolve(
                ReminderDraftResponseFamily.REQUEST_MISSING_VALUE,
                ReminderDraftField.TIME
            )
        )
    }

    @Test
    fun resolve_mapsIncompleteFamiliesAndFieldsToStableKeys() {
        assertEquals(
            ReminderDraftResponseTemplateKey.CORRECT_INCOMPLETE_TEXT,
            ReminderDraftResponseTemplateKeyResolver.resolve(
                ReminderDraftResponseFamily.CORRECT_INCOMPLETE_VALUE,
                ReminderDraftField.TEXT
            )
        )
        assertEquals(
            ReminderDraftResponseTemplateKey.CORRECT_INCOMPLETE_DATE,
            ReminderDraftResponseTemplateKeyResolver.resolve(
                ReminderDraftResponseFamily.CORRECT_INCOMPLETE_VALUE,
                ReminderDraftField.DATE
            )
        )
        assertEquals(
            ReminderDraftResponseTemplateKey.CORRECT_INCOMPLETE_TIME,
            ReminderDraftResponseTemplateKeyResolver.resolve(
                ReminderDraftResponseFamily.CORRECT_INCOMPLETE_VALUE,
                ReminderDraftField.TIME
            )
        )
    }

    @Test
    fun resolve_mapsInvalidFamiliesAndFieldsToStableKeys() {
        assertEquals(
            ReminderDraftResponseTemplateKey.CORRECT_INVALID_TEXT,
            ReminderDraftResponseTemplateKeyResolver.resolve(
                ReminderDraftResponseFamily.CORRECT_INVALID_VALUE,
                ReminderDraftField.TEXT
            )
        )
        assertEquals(
            ReminderDraftResponseTemplateKey.CORRECT_INVALID_DATE,
            ReminderDraftResponseTemplateKeyResolver.resolve(
                ReminderDraftResponseFamily.CORRECT_INVALID_VALUE,
                ReminderDraftField.DATE
            )
        )
        assertEquals(
            ReminderDraftResponseTemplateKey.CORRECT_INVALID_TIME,
            ReminderDraftResponseTemplateKeyResolver.resolve(
                ReminderDraftResponseFamily.CORRECT_INVALID_VALUE,
                ReminderDraftField.TIME
            )
        )
    }

    @Test
    fun resolve_mapsConfirmationFamilyToStableKey() {
        assertEquals(
            ReminderDraftResponseTemplateKey.CONFIRMATION,
            ReminderDraftResponseTemplateKeyResolver.resolve(
                ReminderDraftResponseFamily.CONFIRMATION_SCENARIO,
                null
            )
        )
    }

    @Test
    fun resolve_mapsReadyToContinueFamilyToStableKey() {
        assertEquals(
            ReminderDraftResponseTemplateKey.READY_TO_CONTINUE,
            ReminderDraftResponseTemplateKeyResolver.resolve(
                ReminderDraftResponseFamily.READY_TO_CONTINUE_SCENARIO,
                null
            )
        )
    }
}
