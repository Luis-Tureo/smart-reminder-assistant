package com.luistureo.voicereminderapp.core.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTtsFallbackNoticePolicyTest {

    @Test
    fun notifiesUserWhenRemoteTtsIsUnavailable() {
        assertTrue(
            AssistantTtsFallbackNoticePolicy.shouldNotifyUser(
                AssistantTtsFallbackReason.REMOTE_UNAVAILABLE
            )
        )
        assertEquals(
            "No hay internet, se usar\u00e1 la voz nativa del sistema.",
            AssistantTtsFallbackNoticePolicy.messageFor(
                AssistantTtsFallbackReason.REMOTE_UNAVAILABLE
            )
        )
    }

    @Test
    fun notifiesUserWhenRemotePlaybackFails() {
        assertTrue(
            AssistantTtsFallbackNoticePolicy.shouldNotifyUser(
                AssistantTtsFallbackReason.REMOTE_PLAYBACK_FAILED
            )
        )
    }

    @Test
    fun doesNotShowOfflineMessageWhenRemoteIsDisabledByConfiguration() {
        assertNull(
            AssistantTtsFallbackNoticePolicy.messageFor(
                AssistantTtsFallbackReason.REMOTE_DISABLED_OR_UNCONFIGURED
            )
        )
    }
}
