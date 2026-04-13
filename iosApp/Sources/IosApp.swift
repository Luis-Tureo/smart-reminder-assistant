import SwiftUI

@main
struct IosApp: App {
    @StateObject private var viewModel = ReminderListViewModel()

    var body: some Scene {
        WindowGroup {
            ContentView(viewModel: viewModel)
        }
    }
}
