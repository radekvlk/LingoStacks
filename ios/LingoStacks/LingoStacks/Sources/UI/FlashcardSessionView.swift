//
//  FlashcardSessionView.swift
//  LingoStacks
//
//  Interactive Cupertino Glass flashcard session with gestures and real-time speech recognition
//

import SwiftUI

public struct FlashcardSessionView: View {
    let deck: VocabDeck
    var onSessionFinished: ([CardAttemptResult]) -> Void
    var onDismiss: () -> Void

    @ObservedObject var progressManager = ProgressManager.shared
    @ObservedObject var speechController = SpeechSessionController.shared

    @State private var sessionWords: [VocabWord] = []
    @State private var currentIndex: Int = 0
    @State private var attemptResults: [CardAttemptResult] = []

    // Card Swipe State
    @State private var dragOffset: CGSize = .zero
    @State private var isShowingHint: Bool = false
    @State private var isCardAdvanceInProgress: Bool = false

    public init(
        deck: VocabDeck,
        onSessionFinished: @escaping ([CardAttemptResult]) -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.deck = deck
        self.onSessionFinished = onSessionFinished
        self.onDismiss = onDismiss
    }

    private var currentWord: VocabWord? {
        guard currentIndex < sessionWords.count else { return nil }
        return sessionWords[currentIndex]
    }

    private var isCurrentReversed: Bool {
        switch progressManager.studyDirection {
        case .nativeToForeign: return false
        case .foreignToNative: return true
        case .mixed: return currentIndex % 2 == 1
        }
    }

    public var body: some View {
        ZStack {
            // Dark elegant background
            Color(red: 0.07, green: 0.08, blue: 0.12)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // MARK: - Top Bar
                topBar
                    .padding(.horizontal, 20)
                    .padding(.top, 12)

                Spacer()

                // MARK: - Flashcard
                if let word = currentWord {
                    cardView(word: word)
                        .padding(.horizontal, 20)
                }

                Spacer()

                // MARK: - Speech Equalizer & Recognition Status
                speechStatusView
                    .padding(.bottom, 16)

                // MARK: - Bottom Controls
                bottomControls
                    .padding(.horizontal, 24)
                    .padding(.bottom, 28)
            }
        }
        .onAppear {
            setupSession()
        }
        .onDisappear {
            speechController.stopListening()
        }
    }

    // MARK: - Top Bar

    private var topBar: some View {
        HStack {
            Button {
                speechController.stopListening()
                onDismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.headline.bold())
                    .foregroundColor(.secondary)
                    .padding(10)
                    .cupertinoGlass(cornerRadius: 14)
            }

            Spacer()

            // Direction Pill
            Text(progressManager.studyDirection.shortLabel(language: progressManager.selectedLanguage))
                .font(.caption2.bold())
                .foregroundColor(.secondary)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .cupertinoGlass(cornerRadius: 10)

            Spacer()

            // Progress Counter
            Text("\(min(currentIndex + 1, sessionWords.count)) / \(sessionWords.count)")
                .font(.subheadline.bold())
                .foregroundColor(.primary)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .cupertinoGlass(cornerRadius: 12)
        }
    }

    // MARK: - Card View with Gestures

    @ViewBuilder
    private func cardView(word: VocabWord) -> some View {
        let rotation = Double(dragOffset.width / 20)
        let promptText = isCurrentReversed ? word.foreign : word.czech
        let targetText = isCurrentReversed ? word.czech : word.foreign

        ZStack {
            VStack(spacing: 16) {
                // Card Header: Emoji & Audio Button
                HStack {
                    Text(word.emoji)
                        .font(.system(size: 40))

                    Spacer()

                    Button {
                        let text = isCurrentReversed ? word.czech : word.foreign
                        let tag = isCurrentReversed ? "cs-CZ" : progressManager.selectedLanguage.speechLocaleTag
                        speechController.speak(text: text, languageTag: tag)
                    } label: {
                        Image(systemName: "speaker.wave.2.fill")
                            .font(.title3)
                            .foregroundColor(.blue)
                            .padding(10)
                            .background(Color.blue.opacity(0.12))
                            .clipShape(Circle())
                    }
                }

                Spacer()

                // Main Prompt Text
                Text(promptText)
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .minimumScaleFactor(0.7)
                    .padding(.horizontal, 12)

                // Subtitle / Target Language indicator
                Text(isCurrentReversed ? "Přeložte do češtiny" : "Vyslovte v jazyce: \(progressManager.selectedLanguage.localizedTitle(isCzech: true))")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                Spacer()

                // Expandable Hint Section
                VStack(spacing: 8) {
                    Button {
                        withAnimation(.spring(response: 0.3)) {
                            isShowingHint.toggle()
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: isShowingHint ? "chevron.up" : "lightbulb.fill")
                            Text(isShowingHint ? "Skrýt nápovědu" : "Zobrazit nápovědu")
                        }
                        .font(.footnote.bold())
                        .foregroundColor(.yellow)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.yellow.opacity(0.15))
                        .clipShape(Capsule())
                    }

                    if isShowingHint {
                        VStack(spacing: 6) {
                            Text(targetText)
                                .font(.headline)
                                .foregroundColor(.green)

                            if !word.phonetic.isEmpty {
                                Text(word.phonetic)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }

                            if !word.exampleSentence.isEmpty {
                                Text("\"\(word.exampleSentence)\"")
                                    .font(.caption.italic())
                                    .foregroundColor(.secondary)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal, 16)
                            }
                        }
                        .padding(.top, 4)
                        .transition(.opacity.combined(with: .move(edge: .bottom)))
                    }
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .frame(height: 380)
            .background(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(.ultraThinMaterial)
                    .overlay(
                        RoundedRectangle(cornerRadius: 28, style: .continuous)
                            .stroke(swipeBorderColor, lineWidth: 2)
                    )
            )
            .shadow(color: Color.black.opacity(0.25), radius: 20, x: 0, y: 10)
            .offset(dragOffset)
            .rotationEffect(.degrees(rotation))
            .gesture(
                DragGesture()
                    .onChanged { gesture in
                        dragOffset = gesture.translation
                    }
                    .onEnded { gesture in
                        handleDragEnd(gesture: gesture)
                    }
            )

            // Swipe indicator badges
            if dragOffset.width > 50 {
                badgeOverlay(text: "VÍM", color: .green, alignment: .topLeading)
            } else if dragOffset.width < -50 {
                badgeOverlay(text: "NEVÍM", color: .red, alignment: .topTrailing)
            }
        }
    }

    private var swipeBorderColor: Color {
        if dragOffset.width > 60 { return .green.opacity(0.8) }
        if dragOffset.width < -60 { return .red.opacity(0.8) }
        return Color.white.opacity(0.15)
    }

    private func badgeOverlay(text: String, color: Color, alignment: Alignment) -> some View {
        Text(text)
            .font(.title3.bold())
            .foregroundColor(color)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(color, lineWidth: 2)
            )
            .rotationEffect(.degrees(alignment == .topLeading ? -15 : 15))
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: alignment)
            .padding(32)
    }

    // MARK: - Speech Equalizer & Status

    private var speechStatusView: some View {
        VStack(spacing: 8) {
            if speechController.isMicEnabled {
                AudioVisualizerView()
            }

            if !speechController.partialTranscript.isEmpty && speechController.isMicEnabled {
                Text(speechController.partialTranscript)
                    .font(.subheadline)
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 4)
                    .background(Color.white.opacity(0.1))
                    .clipShape(Capsule())
            }
        }
        .frame(height: 36)
    }

    // MARK: - Bottom Action Controls (Animated Capsule)

    private var bottomControls: some View {
        HStack(spacing: 16) {
            // When Mic is OFF: reveal [✕ Nevím] button on the left
            if !speechController.isMicEnabled {
                Button {
                    advanceCard(isCorrect: false, method: .swipedDontKnow)
                } label: {
                    Image(systemName: "xmark")
                        .font(.title2.bold())
                        .foregroundColor(.red)
                        .frame(width: 54, height: 54)
                        .background(Color.red.opacity(0.18))
                        .clipShape(Circle())
                }
                .transition(.scale.combined(with: .opacity))
            }

            // Big Center Microphone Button
            Button {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.75)) {
                    speechController.toggleListening()
                }
            } label: {
                ZStack {
                    Circle()
                        .fill(speechController.isMicEnabled ? Color.blue : Color(white: 0.25))
                        .frame(width: 72, height: 72)
                        .shadow(
                            color: speechController.isMicEnabled ? Color.blue.opacity(0.4) : Color.clear,
                            radius: 12
                        )

                    Image(systemName: speechController.isMicEnabled ? "mic.fill" : "mic.slash.fill")
                        .font(.title2.bold())
                        .foregroundColor(.white)
                }
            }

            // When Mic is OFF: reveal [✓ Vím] button on the right
            if !speechController.isMicEnabled {
                Button {
                    advanceCard(isCorrect: true, method: .swipedKnow)
                } label: {
                    Image(systemName: "checkmark")
                        .font(.title2.bold())
                        .foregroundColor(.green)
                        .frame(width: 54, height: 54)
                        .background(Color.green.opacity(0.18))
                        .clipShape(Circle())
                }
                .transition(.scale.combined(with: .opacity))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(
            Capsule()
                .fill(.ultraThinMaterial)
                .overlay(
                    Capsule()
                        .stroke(Color.white.opacity(0.15), lineWidth: 1)
                )
        )
        .animation(.spring(response: 0.35, dampingFraction: 0.75), value: speechController.isMicEnabled)
    }

    // MARK: - Session Setup & Advance Logic

    private func setupSession() {
        sessionWords = progressManager.prioritizeWordsForSession(words: deck.words, targetCount: 10)
        prepareActiveCard()

        // Bind SpeechController match callback
        speechController.onWordMatched = { [self] word, transcript, method in
            advanceCard(isCorrect: true, method: method, transcript: transcript)
        }
        speechController.onTransitionReady = { [self] in
            // Card was advanced, start next listen
        }

        // Auto-start listening on session open
        speechController.requestPermissions { granted in
            if granted && speechController.isMicEnabled {
                speechController.startListening()
            }
        }
    }

    private func prepareActiveCard() {
        guard let word = currentWord else { return }
        isShowingHint = false
        dragOffset = .zero

        speechController.currentTargetWord = word
        speechController.expectNativeAnswer = isCurrentReversed
        speechController.activeLanguage = progressManager.selectedLanguage
    }

    private func handleDragEnd(gesture: DragGesture.Value) {
        let threshold: CGFloat = 110
        if gesture.translation.width > threshold {
            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                dragOffset = CGSize(width: 500, height: gesture.translation.height)
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                advanceCard(isCorrect: true, method: .swipedKnow)
            }
        } else if gesture.translation.width < -threshold {
            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                dragOffset = CGSize(width: -500, height: gesture.translation.height)
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                advanceCard(isCorrect: false, method: .swipedDontKnow)
            }
        } else {
            withAnimation(.spring(response: 0.35, dampingFraction: 0.6)) {
                dragOffset = .zero
            }
        }
    }

    private func advanceCard(isCorrect: Bool, method: AnswerMethod, transcript: String? = nil) {
        guard !isCardAdvanceInProgress, let word = currentWord else { return }
        isCardAdvanceInProgress = true

        let result = CardAttemptResult(
            word: word,
            isCorrect: isCorrect,
            method: method,
            spokenTranscript: transcript,
            wasReversed: isCurrentReversed
        )
        attemptResults.append(result)
        progressManager.recordAttempt(result: result)

        speechController.pauseForCardTransition()

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
            isCardAdvanceInProgress = false
            if currentIndex + 1 < sessionWords.count {
                currentIndex += 1
                prepareActiveCard()
            } else {
                speechController.stopListening()
                progressManager.completeSession()
                onSessionFinished(attemptResults)
            }
        }
    }
}
