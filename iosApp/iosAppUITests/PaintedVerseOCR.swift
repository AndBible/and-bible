import Foundation
import Vision
import CoreGraphics

/// Pixel-only verse measurement, shared with the retained-screenshot negative control.
struct PaintedVerseMeasurement {
    let height: CGFloat
    let top: CGFloat
}

func measurePaintedLovedLine(in image: CGImage, record: (String) -> Void) throws -> PaintedVerseMeasurement? {
    let request = VNRecognizeTextRequest()
    request.recognitionLevel = .accurate
    request.recognitionLanguages = ["en-US"]
    request.usesLanguageCorrection = false
    try VNImageRequestHandler(cgImage: image).perform([request])
    let observations = (request.results ?? []).compactMap { observation -> (VNRecognizedText, CGRect)? in
        guard let text = observation.topCandidates(1).first else { return nil }
        return (text, observation.boundingBox)
    }
    // Vision can split one physical row into several observations. Group by row before
    // reading left-to-right, otherwise a preceding verse can interrupt "loved the / world".
    var rows: [[(VNRecognizedText, CGRect)]] = []
    for observation in observations.sorted(by: { $0.1.midY > $1.1.midY }) {
        if let index = rows.firstIndex(where: {
            abs($0[0].1.midY - observation.1.midY) < min($0[0].1.height, observation.1.height) * 0.5
        }) {
            rows[index].append(observation)
        } else {
            rows.append([observation])
        }
    }
    rows = rows.map { $0.sorted { $0.1.minX < $1.1.minX } }
    record(rows.map { row in
        row.map { text, box in
            "\(text.string) [height \(box.height * CGFloat(image.height)), top \((1 - box.maxY) * CGFloat(image.height))]"
        }.joined(separator: " | ")
    }.joined(separator: "\n"))
    for index in rows.indices {
        let rowText = rows[index].map { $0.0.string }.joined(separator: " ")
        let nextText = index + 1 < rows.count ? rows[index + 1].map { $0.0.string }.joined(separator: " ") : ""
        guard rowText.localizedCaseInsensitiveContains("loved"),
              (rowText + " " + nextText).localizedCaseInsensitiveContains("loved the world") else { continue }
        let phraseRows = Array(rows[index...min(index + 1, rows.count - 1)])
        var phraseTop: CGFloat?
        for row in phraseRows {
            for (text, _) in row {
                if let range = text.string.range(of: "loved", options: .caseInsensitive),
                   let word = try text.boundingBox(for: range) {
                    phraseTop = (1 - word.boundingBox.maxY) * CGFloat(image.height)
                }
            }
        }
        for row in phraseRows {
            for (text, _) in row {
                guard let range = text.string.range(of: "world", options: .caseInsensitive),
                      let word = try text.boundingBox(for: range), let phraseTop else { continue }
                // Vision's first-row box can be scaled to the superscript's font. The
                // word "world" is in the same verse but on an ordinary text-only row.
                return PaintedVerseMeasurement(height: word.boundingBox.height * CGFloat(image.height),
                                               top: phraseTop)
            }
        }
    }
    return nil
}
