import SwiftUI

/// The design system is now mostly Apple's, not ours.
///
/// Jakob's Law: people spend nearly all their time in other apps, so the safest
/// interface is the one that already behaves like Settings, Mail and Messages.
/// Everything below is either a system colour, a native control, or the small
/// amount of glue that has no system equivalent.

/// Semantic names over raw hues, so intent survives a palette change. These are Apple's
/// system colours: already dynamic for light/dark and already contrast-checked. Declared
/// on ShapeStyle so they work bare in `.foregroundStyle(.emergency)` the way `.red` does,
/// and still spell out as `Color.emergency` where a concrete Color is needed.
extension ShapeStyle where Self == Color {
    static var emergency: Color { .red }
    static var assist: Color { .blue }
    static var resolved: Color { .green }
    static var pending: Color { .orange }
}

/// Goal-Gradient Effect: showing how much of event setup is left keeps people moving
/// through it. Three steps, and the bar fills as they go.
struct StepHeader: View {
    var step: Int
    var total = 3
    var title: String
    var subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 6) {
                ForEach(1...total, id: \.self) { index in
                    Capsule()
                        .fill(index <= step ? Color.accentColor : Color.secondary.opacity(0.25))
                        .frame(height: 5)
                }
            }
            .animation(.smooth, value: step)

            Text("Step \(step) of \(total)")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(title)
                .font(.largeTitle.bold())
            Text(subtitle)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .textCase(nil)
        .listRowInsets(EdgeInsets(top: 8, leading: 20, bottom: 16, trailing: 20))
        .listRowBackground(Color.clear)
    }
}

/// The one action a screen exists for.
///
/// Fitts's Law: a target this size, pinned within thumb reach, is quick to hit under
/// stress. Von Restorff: only the emergency variant is red, so it is the one thing on
/// screen that cannot be confused with anything else.
struct PrimaryAction: View {
    var title: String
    var systemImage: String
    var tint: Color = .accentColor
    var role: ButtonRole?
    var action: () -> Void

    var body: some View {
        Button(role: role, action: action) {
            Label(title, systemImage: systemImage)
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)
        }
        .buttonStyle(.glassProminent)
        .controlSize(.extraLarge)
        .tint(tint)
    }
}

/// Secondary action: same size and position language, glass instead of solid, so the
/// hierarchy reads without a second colour.
struct SecondaryAction: View {
    var title: String
    var systemImage: String
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: systemImage)
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)
        }
        .buttonStyle(.glass)
        .controlSize(.extraLarge)
    }
}

/// A status word carried by colour and shape rather than a sentence.
/// Law of Similarity: the same pill means the same kind of thing everywhere.
struct StatusPill: View {
    var text: String
    var tint: Color
    var systemImage: String?

    var body: some View {
        Label {
            Text(text)
        } icon: {
            if let systemImage { Image(systemName: systemImage) }
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(tint)
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .glassEffect(.regular.tint(tint.opacity(0.18)), in: .capsule)
    }
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
