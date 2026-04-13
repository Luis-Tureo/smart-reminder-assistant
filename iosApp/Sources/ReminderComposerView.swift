import SwiftUI

struct ReminderComposerView: View {
    @ObservedObject var viewModel: ReminderListViewModel
    @Binding var isPresented: Bool

    @State private var title = ""
    @State private var detail = ""
    @State private var selectedDate = Date()
    @State private var selectedTime = Date()
    @State private var isUrgent = false

    var body: some View {
        NavigationView {
            Form {
                Section("Contenido") {
                    TextField("Titulo opcional", text: $title)

                    TextEditor(text: $detail)
                        .frame(minHeight: 120)
                }

                Section("Programacion") {
                    DatePicker(
                        "Fecha",
                        selection: $selectedDate,
                        displayedComponents: .date
                    )

                    DatePicker(
                        "Hora",
                        selection: $selectedTime,
                        displayedComponents: .hourAndMinute
                    )

                    Toggle("Urgente", isOn: $isUrgent)
                }
            }
            .navigationTitle("Nuevo recordatorio")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancelar") {
                        isPresented = false
                    }
                }

                ToolbarItem(placement: .navigationBarTrailing) {
                    if viewModel.isSaving {
                        ProgressView()
                    } else {
                        Button("Guardar") {
                            Task {
                                let didSave = await viewModel.createReminder(
                                    title: title.trimmingCharacters(in: .whitespacesAndNewlines),
                                    detail: detail.trimmingCharacters(in: .whitespacesAndNewlines),
                                    selectedDate: selectedDate,
                                    selectedTime: selectedTime,
                                    isUrgent: isUrgent
                                )

                                if didSave {
                                    isPresented = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
