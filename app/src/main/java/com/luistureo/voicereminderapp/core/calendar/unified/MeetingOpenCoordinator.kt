package com.luistureo.voicereminderapp.core.calendar.unified

object MeetingOpenCoordinator {
    enum class Result { APP_OPENED, BROWSER_OPENED, INVALID_URL, NO_HANDLER }

    fun open(
        url: String,
        providerAppAvailable: Boolean,
        openProviderApp: () -> Unit,
        openBrowser: () -> Unit
    ): Result {
        if (!MeetingUrlPolicy.isSupportedMeetingUrl(url)) return Result.INVALID_URL
        if (providerAppAvailable && runCatching(openProviderApp).isSuccess) {
            return Result.APP_OPENED
        }
        return if (runCatching(openBrowser).isSuccess) Result.BROWSER_OPENED else Result.NO_HANDLER
    }
}
