import SwiftUI

struct ReminderRowView: View {
    let reminder: ReminderItemModel

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: reminder.isCompleted ? "checkmark.circle.fill" : "circle")
                .foregroundStyle(reminder.isCompleted ? Color.green : Color.secondary)
                .font(.title3)

            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Text(reminder.title)
                        .font(.headline)
                        .strikethrough(reminder.isCompleted, color: .secondary)

                    if reminder.isUrgent {
                        Text("Urgente")
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.orange.opacity(0.18))
                            .foregroundStyle(Color.orange)
                            .clipShape(Capsule())
                    }
                }

                Text(reminder.detail)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)

                Text("\(reminder.scheduledDate) - \(reminder.scheduledTime)")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                if let recurrenceLabel = reminder.recurrenceLabel, !recurrenceLabel.isEmpty {
                    Text(recurrenceLabel)
                        .font(.caption)
                        .foregroundStyle(.blue)
                }
            }
        }
        .padding(.vertical, 4)
    }
}
