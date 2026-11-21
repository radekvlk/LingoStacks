//
//  AudioVisualizerView.swift
//  LingoStacks
//
//  Visual audio equalizer reacting to real-time RMS microphone amplitude
//

import SwiftUI

public struct AudioVisualizerView: View {
    @ObservedObject var speechController = SpeechSessionController.shared
    let barCount: Int = 5
    let tintColor: Color

    public init(tintColor: Color = Color(red: 0.20, green: 0.78, blue: 0.35)) {
        self.tintColor = tintColor
    }

    public var body: some View {
        HStack(spacing: 3) {
            ForEach(0..<barCount, id: \.self) { index in
                BarView(
                    index: index,
                    level: speechController.audioLevel,
                    isListening: speechController.isListening,
                    tintColor: tintColor
                )
            }
        }
        .frame(height: 24)
    }
}

private struct BarView: View {
    let index: Int
    let level: Float
    let isListening: Bool
    let tintColor: Color

    // Multipliers for varying height of individual bars
    private var heightMultiplier: CGFloat {
        switch index {
        case 0: return 0.5
        case 1: return 0.8
        case 2: return 1.0
        case 3: return 0.85
        default: return 0.55
        }
    }

    private var targetHeight: CGFloat {
        guard isListening else { return 4 }
        let baseHeight: CGFloat = 6
        let dynamic = CGFloat(level) * 18 * heightMultiplier
        return max(4, min(24, baseHeight + dynamic))
    }

    var body: some View {
        RoundedRectangle(cornerRadius: 2)
            .fill(isListening ? tintColor : Color.secondary.opacity(0.3))
            .frame(width: 3, height: targetHeight)
            .animation(.interactiveSpring(response: 0.15, dampingFraction: 0.6), value: targetHeight)
    }
}
