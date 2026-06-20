package com.luistureo.voicereminderapp.domain.model

enum class CalendarProviderSyncState {
    NOT_CONNECTED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    SYNCED,
    FAILED
}
