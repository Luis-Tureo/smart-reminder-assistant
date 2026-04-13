import Combine
import Foundation
import sharedKit

@MainActor
final class ReminderListViewModel: ObservableObject {
    @Published private(set) var reminders: [ReminderItemModel] = []
    @Published private(set) var isLoading = false
    @Published private(set) var isSaving = false
    @Published var errorMessage: String?

    private let controller = IosReminderAppController()
    private let calendar = Calendar(identifier: .gregorian)
    private var hasLoadedOnce = false

    func loadRemindersIfNeeded() {
        guard !hasLoadedOnce else { return }
        loadReminders()
    }

    func loadReminders() {
        isLoading = true

        Task {
            defer { isLoading = false }

            do {
                let items = try await controller.fetchReminders()
                reminders = mapItems(items)
                hasLoadedOnce = true
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func createReminder(
        title: String,
        detail: String,
        selectedDate: Date,
        selectedTime: Date,
        isUrgent: Bool
    ) async -> Bool {
        isSaving = true
        defer { isSaving = false }

        let dateInput = buildDateInput(from: selectedDate)
        let timeInput = buildTimeInput(from: selectedTime)
        let validation = controller.validateDraft(detail: detail, date: dateInput, time: timeInput)

        guard validation.isValid else {
            errorMessage = validation.errorMessage ?? "No fue posible guardar el recordatorio."
            return false
        }

        do {
            _ = try await controller.saveReminder(
                title: title.isEmpty ? nil : title,
                detail: detail,
                date: dateInput,
                time: timeInput,
                isUrgent: isUrgent
            )
            loadReminders()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func toggleReminderCompleted(_ reminder: ReminderItemModel) {
        Task {
            do {
                let updatedItem = try await controller.setReminderCompleted(
                    reminderId: Int32(reminder.id),
                    isCompleted: !reminder.isCompleted
                )

                if let updatedItem {
                    applyUpdatedReminder(ReminderItemModel(item: updatedItem))
                } else {
                    loadReminders()
                }
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func deleteReminder(_ reminder: ReminderItemModel) {
        Task {
            do {
                try await controller.deleteReminder(reminderId: Int32(reminder.id))
                reminders.removeAll { $0.id == reminder.id }
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func dismissError() {
        errorMessage = nil
    }

    private func buildDateInput(from date: Date) -> String {
        let components = calendar.dateComponents([.day, .month, .year], from: date)
        return controller.buildDateInput(
            day: Int32(components.day ?? 1),
            month: Int32(components.month ?? 1),
            year: Int32(components.year ?? 1970)
        )
    }

    private func buildTimeInput(from date: Date) -> String {
        let components = calendar.dateComponents([.hour, .minute], from: date)
        return controller.buildTimeInput(
            hour: Int32(components.hour ?? 0),
            minute: Int32(components.minute ?? 0)
        )
    }

    private func applyUpdatedReminder(_ reminder: ReminderItemModel) {
        if let index = reminders.firstIndex(where: { $0.id == reminder.id }) {
            reminders[index] = reminder
            reminders.sort {
                if $0.isCompleted != $1.isCompleted {
                    return !$0.isCompleted && $1.isCompleted
                }

                if $0.scheduledDate != $1.scheduledDate {
                    return $0.scheduledDate < $1.scheduledDate
                }

                if $0.scheduledTime != $1.scheduledTime {
                    return $0.scheduledTime < $1.scheduledTime
                }

                return $0.title < $1.title
            }
        } else {
            loadReminders()
        }
    }

    private func mapItems(_ items: Any) -> [ReminderItemModel] {
        if let typedItems = items as? [IosReminderListItem] {
            return typedItems.map(ReminderItemModel.init)
        }

        if let bridgedItems = items as? NSArray {
            return bridgedItems.compactMap { element in
                (element as? IosReminderListItem).map(ReminderItemModel.init)
            }
        }

        if let bridgedItems = items as? [Any] {
            return bridgedItems.compactMap { element in
                (element as? IosReminderListItem).map(ReminderItemModel.init)
            }
        }

        return []
    }
}
