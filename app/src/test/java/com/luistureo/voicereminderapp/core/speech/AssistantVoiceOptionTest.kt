package com.luistureo.voicereminderapp.core.speech

import org.junit.Assert.assertNull
import org.junit.Test

class AssistantVoiceOptionTest {

    @Test
    fun localPhoneVoiceDoesNotUseRemoteBackendVoice() {
        assertNull(AssistantVoiceOption.LOCAL_PHONE.backendVoiceValue)
    }

    @Test
    fun cloudVoiceIsConfiguredOutsideUserSelectableOptions() {
        assertNull(AssistantVoiceOption.default.backendVoiceValue)
    }
}
