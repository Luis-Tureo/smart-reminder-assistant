import SwiftUI

struct ContentView: View {
    @ObservedObject var viewModel: ReminderListViewModel
    @State private var isComposerPresented = false

    var body: some View {
        NavigationView {
            Group {
                if viewModel.isLoading && viewModel.reminders.isEmpty {
                    ProgressView("Cargando recordatorios...")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if viewModel.reminders.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "calendar.badge.plus")
                            .font(.system(size: 42))
                            .foregroundStyle(.blue)

                        Text("No hay recordatorios guardados")
                            .font(.headline)

                        Text("Crea uno manualmente para validar el flujo iOS sobre la capa shared.")
                            .font(.subheadline)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 24)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding()
                } else {
                    List {
                        ForEach(viewModel.reminders) { reminder in
                            Button {
                                viewModel.toggleReminderCompleted(reminder)
                            } label: {
                                ReminderRowView(reminder: reminder)
                            }
                            .buttonStyle(.plain)
                            .swipeActions {
                                Button(role: .destructive) {
                                    viewModel.deleteReminder(reminder)
                                } label: {
                                    Label("Eliminar", systemImage: "trash")
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("Recordatorios")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        viewModel.loadReminders()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }

                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        isComposerPresented = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
        }
        .task {
            viewModel.loadRemindersIfNeeded()
        }
        .sheet(isPresented: $isComposerPresented) {
            ReminderComposerView(
                viewModel: viewModel,
                isPresented: $isComposerPresented
            )
        }
        .alert(
            "No fue posible completar la accion.",
            isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { isPresented in
                    if !isPresented {
                        viewModel.dismissError()
                    }
                }
            )
        ) {
            Button("Entendido", role: .cancel) {
                viewModel.dismissError()
            }
        } message: {
            Text(viewModel.errorMessage ?? "Intenta nuevamente.")
        }
    }
}
