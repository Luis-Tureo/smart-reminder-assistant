import Foundation
import sharedKit

struct ReminderItemModel: Identifiable, Equatable {
    let id: Int
    let title: String
    let detail: String
    let scheduledDate: String
    let scheduledTime: String
    let recurrenceLabel: String?
    let isCompleted: Bool
    let isUrgent: Bool

    init(item: IosReminderListItem) {
        id = Int(item.id)
        title = item.title
        detail = item.detail
        scheduledDate = item.scheduledDate
        scheduledTime = item.scheduledTime
        recurrenceLabel = item.recurrenceLabel
        isCompleted = item.isCompleted
        isUrgent = item.isUrgent
    }
}
