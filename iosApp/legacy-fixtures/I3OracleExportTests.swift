// I3OracleExportTests.swift
//
// CI-only oracle exporter for the I3 legacy migration. Runs on the PINNED Swift app revision only
// (it uses `PageManagerBibleBookEncodingMigrationService`, which does not exist in 1.0.1). The workflow
// copies this file into the legacy checkout at Sources/BibleCore/Tests/BibleCoreTests/.
//
// For every directory below `I3_FIXTURE_IN` that holds an `AndBible.store` (a generated fixture, laid
// out as <app>/<ios>/<scenario>/), the test:
//   1. installs the store files into the simulator's Application Support directory, which is where
//      the app opens them (`ModelConfiguration("AndBible")` / `("LocalStore")` without a URL),
//   2. records the Core Data model fingerprint (`NSStoreModelVersionHashes`) and per-entity row
//      counts of the untouched store,
//   3. runs the startup repair exactly in the order of `AndBibleApp.init`:
//      `DataMigration.migrateIfNeeded()`, container load, `PageManagerBibleBookEncodingMigrationService`,
//      `PersistedOrdinalTrustMigrationService`,
//   4. writes the oracle outputs to `I3_FIXTURE_OUT/<app>/<ios>/<scenario>/oracle/`:
//        AndBibleDatabaseBackup.abdb.zip   AndroidDatabaseBackupService.exportArchiveFile
//        ai_settings.sqlite3               RemoteSyncAISettingsDatabaseWriter.writeFullDatabase
//        report.json                       row counts, model hashes digest, repair counts, errors
//
// The stores under `I3_FIXTURE_IN` are never modified; they are copied first.

import CoreData
import CryptoKit
import Foundation
import SQLite3
import SwiftData
import XCTest
@testable import BibleCore

@MainActor
final class I3OracleExportTests: XCTestCase {
    func testExportOracleForEveryGeneratedStore() throws {
        let environment = ProcessInfo.processInfo.environment
        let inputPath = try XCTUnwrap(environment["I3_FIXTURE_IN"], "I3_FIXTURE_IN is not set")
        let outputPath = try XCTUnwrap(environment["I3_FIXTURE_OUT"], "I3_FIXTURE_OUT is not set")
        let inputRoot = URL(fileURLWithPath: inputPath, isDirectory: true)
        let outputRoot = URL(fileURLWithPath: outputPath, isDirectory: true)

        let fixtures = try Self.fixtureDirectories(under: inputRoot)
        XCTAssertFalse(fixtures.isEmpty, "No AndBible.store found below \(inputRoot.path)")
        for fixture in fixtures {
            let relative = String(fixture.path.dropFirst(inputRoot.path.count))
                .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            let destination = outputRoot.appendingPathComponent(relative, isDirectory: true)
                .appendingPathComponent("oracle", isDirectory: true)
            do {
                try export(fixture: fixture, relativeName: relative, to: destination)
            } catch {
                XCTFail("\(relative): oracle export failed: \(error)")
            }
        }
    }

    // MARK: Per fixture

    private func export(fixture: URL, relativeName: String, to destination: URL) throws {
        let fileManager = FileManager.default
        try? fileManager.removeItem(at: destination)
        try fileManager.createDirectory(at: destination, withIntermediateDirectories: true)

        // 1. Install the stores where the app looks for them.
        let applicationSupport = try Self.cleanApplicationSupport()
        for name in try fileManager.contentsOfDirectory(atPath: fixture.path)
        where name.hasPrefix("AndBible.store") || name.hasPrefix("LocalStore.store") {
            try fileManager.copyItem(
                at: fixture.appendingPathComponent(name),
                to: applicationSupport.appendingPathComponent(name)
            )
        }

        // 2. Fingerprint and row counts of the untouched stores.
        var report: [String: Any] = ["fixture": relativeName]
        var errors: [String] = []
        var hashDigests: [String: String] = [:]
        var rowCounts: [String: Int] = [:]
        for storeName in ["AndBible", "LocalStore"] {
            let storeURL = applicationSupport.appendingPathComponent("\(storeName).store")
            guard fileManager.fileExists(atPath: storeURL.path) else { continue }
            hashDigests[storeName] = try Self.modelHashDigest(ofStoreAt: storeURL)
            for (entity, count) in try Self.entityRowCounts(ofStoreAt: storeURL) {
                rowCounts["\(storeName).\(entity)"] = count
            }
        }
        report["storeModelVersionHashes"] = hashDigests
        report["fingerprint"] = Self.sha256Hex(
            Data(hashDigests.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }.joined(separator: "\n").utf8)
        )
        report["rowCounts"] = rowCounts

        // 3. Startup repair in AndBibleApp.init order (AndBibleApp.swift:605-623).
        DataMigration.migrateIfNeeded()
        let container = try Self.makeContainer()
        let context = container.mainContext
        let settingsStore = SettingsStore(modelContext: context)
        let encodingReport = try PageManagerBibleBookEncodingMigrationService(modelContext: context).migrate()
        let trustReport = try PersistedOrdinalTrustMigrationService(
            modelContext: context,
            settingsStore: settingsStore
        ).migrate()
        report["repairs"] = [
            "pageManagerBibleBookEncoding": String(describing: encodingReport),
            "persistedOrdinalTrust": [
                "repairedBookmarkCount": trustReport.repairedBookmarkCount,
                "repairedMemorizedVerseCount": trustReport.repairedMemorizedVerseCount,
                "repairedMemorizationTargetCount": trustReport.repairedMemorizationTargetCount,
                "pendingModuleCount": trustReport.pendingModuleCount,
                "unresolvedCount": trustReport.unresolvedCount,
                "didChangePersistence": trustReport.didChangePersistence,
            ] as [String: Any],
        ] as [String: Any]
        try context.save()

        // 4a. Android database backup (preserved Android AI database included when present).
        let scratch = fileManager.temporaryDirectory
            .appendingPathComponent("i3-oracle-\(UUID().uuidString)", isDirectory: true)
        try fileManager.createDirectory(at: scratch, withIntermediateDirectories: true)
        defer { try? fileManager.removeItem(at: scratch) }
        let preservedRoot = scratch.appendingPathComponent("PreservedDatabases", isDirectory: true)
        let fixturePreserved = fixture.appendingPathComponent("PreservedDatabases", isDirectory: true)
        if fileManager.fileExists(atPath: fixturePreserved.path) {
            try fileManager.copyItem(at: fixturePreserved, to: preservedRoot)
        } else {
            try fileManager.createDirectory(at: preservedRoot, withIntermediateDirectories: true)
        }
        let backupService = AndroidDatabaseBackupService(
            temporaryDirectory: scratch,
            repositorySourceManager: RepositorySourceManager(
                basePath: scratch.appendingPathComponent("repositories", isDirectory: true).path
            ),
            preservedDatabaseStore: AndroidDatabaseBackupPreservedDatabaseStore(rootDirectory: preservedRoot)
        )
        do {
            let export = try backupService.exportArchiveFile(modelContext: context, settingsStore: settingsStore)
            try fileManager.copyItem(
                at: export.fileURL,
                to: destination.appendingPathComponent("AndBibleDatabaseBackup.abdb.zip")
            )
            try? fileManager.removeItem(at: export.fileURL)
            report["backupCategories"] = export.categories.map(\.rawValue)
            report["backupEntryCount"] = export.entryCount
        } catch {
            errors.append("android database backup export: \(error)")
        }

        // 4b. AI settings database from the Swift rows (credential-free). The strict snapshot throws on
        // stores that have no AI singleton (for example `fresh`), which is recorded, not fatal.
        do {
            let snapshot = try RemoteSyncAISettingsSnapshotService(credentialForProvider: { _ in nil })
                .snapshotCurrentStateStrict(modelContext: context, settingsStore: settingsStore)
            let written = try RemoteSyncAISettingsDatabaseWriter().writeFullDatabase(
                at: destination.appendingPathComponent("ai_settings.sqlite3"),
                snapshot: snapshot
            )
            report["aiSettingsWriteReport"] = String(describing: written)
        } catch {
            report["aiSettingsSkipped"] = "\(error)"
        }

        report["errors"] = errors
        let data = try JSONSerialization.data(
            withJSONObject: report,
            options: [.prettyPrinted, .sortedKeys]
        )
        try data.write(to: destination.appendingPathComponent("report.json"), options: .atomic)
        XCTAssertTrue(errors.isEmpty, "\(relativeName): \(errors)")
    }

    // MARK: Helpers

    /// Same schemas and names as `AndBibleApp.loadModelContainer`, local mode, default (Application Support) URLs.
    private static func makeContainer() throws -> ModelContainer {
        let cloudModels = BibleCoreBaseModelRegistration.cloudModels
            + AIModelRegistration.cloudSyncableModels
        let localModels = BibleCoreBaseModelRegistration.localModels
            + AIModelRegistration.localOnlyModels
        let schema = Schema(cloudModels + localModels)
        let cloudConfig = ModelConfiguration(
            "AndBible", schema: Schema(cloudModels), isStoredInMemoryOnly: false, cloudKitDatabase: .none
        )
        let localConfig = ModelConfiguration(
            "LocalStore", schema: Schema(localModels), isStoredInMemoryOnly: false, cloudKitDatabase: .none
        )
        return try ModelContainer(for: schema, configurations: [cloudConfig, localConfig])
    }

    /// Directories (at any depth) that contain an `AndBible.store` file, in a stable order.
    private static func fixtureDirectories(under root: URL) throws -> [URL] {
        guard let enumerator = FileManager.default.enumerator(
            at: root, includingPropertiesForKeys: [.isDirectoryKey]
        ) else { return [] }
        var directories: [URL] = []
        for case let url as URL in enumerator where url.lastPathComponent == "AndBible.store" {
            directories.append(url.deletingLastPathComponent())
        }
        return directories.sorted { $0.path < $1.path }
    }

    /// Application Support directory with every previous `*.store*` file removed.
    private static func cleanApplicationSupport() throws -> URL {
        let fileManager = FileManager.default
        let directory = try XCTUnwrap(
            fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
        )
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        for name in try fileManager.contentsOfDirectory(atPath: directory.path) where name.contains(".store") {
            try fileManager.removeItem(at: directory.appendingPathComponent(name))
        }
        return directory
    }

    /// SHA-256 over the sorted `entity:hex(hash)` lines of the store's `NSStoreModelVersionHashes`.
    private static func modelHashDigest(ofStoreAt url: URL) throws -> String {
        let metadata = try NSPersistentStoreCoordinator.metadataForPersistentStore(
            ofType: NSSQLiteStoreType, at: url, options: nil
        )
        let hashes = try XCTUnwrap(
            metadata[NSStoreModelVersionHashesKey] as? [String: Data],
            "no NSStoreModelVersionHashes in \(url.lastPathComponent)"
        )
        let lines = hashes.sorted { $0.key < $1.key }.map { name, hash in
            "\(name):" + hash.map { String(format: "%02x", $0) }.joined()
        }
        return sha256Hex(Data(lines.joined(separator: "\n").utf8))
    }

    private static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    /// Row count of every entity table (`Z<ENTITYNAME>`), named through Core Data's `Z_PRIMARYKEY`.
    private static func entityRowCounts(ofStoreAt url: URL) throws -> [String: Int] {
        var database: OpaquePointer?
        guard sqlite3_open_v2(url.path, &database, SQLITE_OPEN_READONLY, nil) == SQLITE_OK, let database else {
            throw NSError(domain: "I3Oracle", code: 1, userInfo: [NSLocalizedDescriptionKey: "cannot open \(url.path)"])
        }
        defer { sqlite3_close(database) }

        func scalarRows(_ sql: String, _ read: (OpaquePointer) -> Void) {
            var statement: OpaquePointer?
            guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK, let statement else { return }
            defer { sqlite3_finalize(statement) }
            while sqlite3_step(statement) == SQLITE_ROW { read(statement) }
        }

        var names: [String] = []
        scalarRows("SELECT Z_NAME FROM Z_PRIMARYKEY ORDER BY Z_ENT") { statement in
            if let text = sqlite3_column_text(statement, 0) { names.append(String(cString: text)) }
        }
        var counts: [String: Int] = [:]
        for name in names {
            let table = "Z" + name.uppercased()
            scalarRows("SELECT COUNT(*) FROM \"\(table)\"") { statement in
                counts[name] = Int(sqlite3_column_int64(statement, 0))
            }
        }
        return counts
    }
}
