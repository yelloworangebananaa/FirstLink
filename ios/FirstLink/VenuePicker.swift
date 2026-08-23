import SwiftUI
import MapKit

/// Venue search backed by the same completer Maps itself uses.
///
/// Maps.app cannot hand a chosen place back to another app, so "pick it in Maps" is
/// built here out of MapKit rather than by launching Maps: search-as-you-type from
/// MKLocalSearchCompleter, resolved to a real place with MKLocalSearch.
final class VenueSearch: NSObject, ObservableObject, MKLocalSearchCompleterDelegate {

    @Published var query = "" {
        didSet { completer.queryFragment = query }
    }
    @Published private(set) var results: [MKLocalSearchCompletion] = []

    private let completer = MKLocalSearchCompleter()

    override init() {
        super.init()
        completer.delegate = self
        completer.resultTypes = [.pointOfInterest, .address]
    }

    func completerDidUpdateResults(_ completer: MKLocalSearchCompleter) {
        results = completer.results
    }

    func completer(_ completer: MKLocalSearchCompleter, didFailWithError error: Error) {
        results = []
    }

    /// Turns a suggestion into a real place with a coordinate.
    func resolve(_ completion: MKLocalSearchCompletion) async -> MKMapItem? {
        let response = try? await MKLocalSearch(request: .init(completion: completion)).start()
        return response?.mapItems.first
    }
}

/// Sheet for choosing the event venue on a map.
///
/// Paradox of the Active User: people type before they read, so the search field takes
/// focus immediately and the map fills in behind the results rather than asking for a
/// pin drop first.
struct VenuePicker: View {
    @Binding var venue: String
    @Environment(\.dismiss) private var dismiss

    @StateObject private var search = VenueSearch()
    @State private var camera: MapCameraPosition = .automatic
    @State private var picked: MKMapItem?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Map(position: $camera) {
                    if let picked {
                        Marker(picked.name ?? "Venue", coordinate: picked.location.coordinate)
                            .tint(.assist)
                    }
                }
                .frame(height: 240)
                .overlay(alignment: .bottom) {
                    if let picked {
                        // Peak-End: confirm the choice in place, so the last thing seen
                        // before dismissing is the venue that was actually selected.
                        VStack(spacing: 2) {
                            Text(picked.name ?? "Venue").font(.headline)
                            if let address = picked.address?.shortAddress {
                                Text(address).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .glassEffect(.regular, in: .capsule)
                        .padding(.bottom, 12)
                    }
                }

                List {
                    ForEach(search.results, id: \.self) { result in
                        Button {
                            Task { await choose(result) }
                        } label: {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(result.title)
                                    .font(.body)
                                    .foregroundStyle(.primary)
                                if !result.subtitle.isEmpty {
                                    Text(result.subtitle)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(.rect)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .listStyle(.plain)
                .overlay {
                    if search.results.isEmpty {
                        ContentUnavailableView {
                            Label(search.query.isEmpty ? "Find your venue" : "No places found",
                                  systemImage: "mappin.and.ellipse")
                        } description: {
                            Text(search.query.isEmpty
                                 ? "Search for the building, park or address where the event happens."
                                 : "Try a different name or address.")
                        } actions: {
                            // Postel's Law: a private hall may not be in Maps at all, so
                            // whatever was typed is still an acceptable venue name.
                            if !search.query.trimmed.isEmpty {
                                Button("Use \"\(search.query.trimmed)\"") {
                                    venue = search.query.trimmed
                                    dismiss()
                                }
                            }
                        }
                    }
                }
            }
            .searchable(text: $search.query, prompt: "Search for a venue")
            .navigationTitle("Choose Venue")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Use This") { confirm() }
                        .disabled(picked == nil)
                }
            }
            .sensoryFeedback(.selection, trigger: picked?.name)
        }
    }

    private func choose(_ completion: MKLocalSearchCompletion) async {
        guard let item = await search.resolve(completion) else { return }
        picked = item
        camera = .region(MKCoordinateRegion(center: item.location.coordinate,
                                            latitudinalMeters: 600,
                                            longitudinalMeters: 600))
    }

    private func confirm() {
        guard let picked else { return }
        venue = picked.name ?? picked.address?.shortAddress ?? ""
        dismiss()
    }
}
