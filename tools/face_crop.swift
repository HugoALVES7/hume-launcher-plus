// Fichier ajouté dans le fork Hume Launcher+ : recadrage des avatars sur le visage.
// Square, face-centred avatar crop (Apple Vision face detection), composited on a gradient so
// transparent renders get a backdrop. Used by tools/fetch_avatars.py.
//   swift tools/face_crop.swift IN OUT SIZE TOP_HEX BOTTOM_HEX [cx,cy,side]   (override as fractions
//   of the image width/height/shorter side, origin top-left)
import AppKit
import CoreImage
import Foundation
import Vision

let a = CommandLine.arguments
guard a.count >= 6, let src = NSImage(contentsOfFile: a[1]),
      let cg = src.cgImage(forProposedRect: nil, context: nil, hints: nil) else { print("bad input"); exit(1) }
let size = Int(a[3])!
func color(_ h: String) -> CGColor {
    let v = Int(h.dropFirst(), radix: 16)!
    return CGColor(red: CGFloat((v >> 16) & 255) / 255, green: CGFloat((v >> 8) & 255) / 255, blue: CGFloat(v & 255) / 255, alpha: 1)
}
let W = CGFloat(cg.width), H = CGFloat(cg.height)

// Crop square in image pixels, origin top-left.
var crop = CGRect.zero
var how = "face"
if a.count >= 7 {
    let f = a[6].split(separator: ",").map { CGFloat(Double($0)!) }
    let side = f[2] * min(W, H)
    crop = CGRect(x: f[0] * W - side / 2, y: f[1] * H - side / 2, width: side, height: side)
    how = "manual"
} else {
    let req = VNDetectFaceRectanglesRequest()
    try? VNImageRequestHandler(cgImage: cg, options: [:]).perform([req])
    if let face = (req.results ?? []).max(by: { $0.boundingBox.height < $1.boundingBox.height }) {
        let b = face.boundingBox
        let fh = b.height * H, fw = b.width * W
        let side = max(fh, fw) * 2.2
        let cx = b.midX * W, cy = (1 - b.midY) * H + side * 0.06
        crop = CGRect(x: cx - side / 2, y: cy - side / 2, width: side, height: side)
    } else {
        // No face found: the top of the opaque area (renders) or of the picture.
        how = "top"
        let side = min(W, H) * 0.5
        crop = CGRect(x: W / 2 - side / 2, y: H * 0.02, width: side, height: side)
    }
}
// Keep inside the picture where possible (shrink when bigger).
let maxSide = min(W, H)
if crop.width > maxSide { crop = crop.insetBy(dx: (crop.width - maxSide) / 2, dy: (crop.width - maxSide) / 2) }
crop.origin.x = min(max(0, crop.origin.x), W - crop.width)
crop.origin.y = min(max(0, crop.origin.y), H - crop.height)

let cs = CGColorSpaceCreateDeviceRGB()
let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0, space: cs,
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
let grad = CGGradient(colorsSpace: cs, colors: [color(a[4]), color(a[5])] as CFArray, locations: [0, 1])!
ctx.drawLinearGradient(grad, start: CGPoint(x: 0, y: CGFloat(size)), end: CGPoint(x: 0, y: 0), options: [])
ctx.interpolationQuality = .high
let k = CGFloat(size) / crop.width
// CG origin is bottom-left.
ctx.draw(cg, in: CGRect(x: -crop.origin.x * k, y: -(H - crop.maxY) * k, width: W * k, height: H * k))
let out = ctx.makeImage()!
let rep = NSBitmapImageRep(cgImage: out)
try! rep.representation(using: .jpeg, properties: [.compressionFactor: 0.9])!.write(to: URL(fileURLWithPath: a[2]))
print(how, Int(crop.width), "px of", Int(W), "x", Int(H))
