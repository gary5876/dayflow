// dayflow-ocr: on-device text recognition (macOS Vision).
// Usage: dayflow-ocr <image>
// Prints one line per recognized text: x<TAB>y<TAB>width<TAB>height<TAB>text
// Coordinates are image pixels with the origin at the top-left corner.
import Foundation
import ImageIO
import Vision

guard CommandLine.arguments.count == 2 else {
    FileHandle.standardError.write("usage: dayflow-ocr <image>\n".data(using: .utf8)!)
    exit(2)
}
let url = URL(fileURLWithPath: CommandLine.arguments[1])
guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
      let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    FileHandle.standardError.write("cannot read image\n".data(using: .utf8)!)
    exit(1)
}

let request = VNRecognizeTextRequest()
request.recognitionLevel = .accurate
request.recognitionLanguages = ["ko-KR", "en-US"]
request.usesLanguageCorrection = false

do {
    try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
} catch {
    FileHandle.standardError.write("recognition failed: \(error)\n".data(using: .utf8)!)
    exit(1)
}

let width = Double(image.width), height = Double(image.height)
for observation in request.results ?? [] {
    guard let best = observation.topCandidates(1).first else { continue }
    let box = observation.boundingBox  // normalized, origin at bottom-left
    let x = Int((box.minX * width).rounded())
    let y = Int(((1 - box.maxY) * height).rounded())
    let w = Int((box.width * width).rounded())
    let h = Int((box.height * height).rounded())
    let text = best.string.replacingOccurrences(of: "\t", with: " ")
    print("\(x)\t\(y)\t\(w)\t\(h)\t\(text)")
}
