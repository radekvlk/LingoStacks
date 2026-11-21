//
//  FlashcardSessionView.swift
//  LingoStacks
//
//  Complete Cupertino Glass flashcard session with TopBar metrics, LiveSpeechBanner, 3D stack, WordMemoryInsights, and auto-expanding BottomControlBar
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

    private var nextWord: VocabWord? {
        guard currentIndex + 1 < sessionWords.count else { return nil }
        return sessionWords[currentIndex + 1]
    }

    private var thirdWord: VocabWord? {
        guard currentIndex + 2 < sessionWords.count else { return nil }
        return sessionWords[currentIndex + 2]
    }

    private var isCurrentReversed: Bool {
        switch progressManager.studyDirection {
        case .nativeToForeign: return false
        case .foreignToNative: return true
        case .mixed: return currentIndex % 2 == 1
        }
    }

    private var currentSrsStat: WordSrsStat? {
        guard let word = currentWord else { return nil }
        return progressManager.wordSrsMap[word.id]
    }

    public var body: some View {
        ZStack {
            // Dark luxury background
            Color(red: 0.06, green: 0.07, blue: 0.11)
                .ignoresSafeArea()

            VStack(spacing: 8) {
                // MARK: - 1. Apple Session TopBar (nad kartou)
                topBarView
                    .padding(.horizontal, 16)
                    .padding(.top, 8)

                // MARK: - 2. Live Speech Banner (nad kartou)
                liveSpeechBannerView
                    .padding(.horizontal, 16)

                Spacer(minLength: 2)

                // MARK: - 3. 3D Stack Flashcard Area
                if let word = currentWord {
                    cardStackView(word: word)
                        .padding(.horizontal, 16)
                }

                Spacer(minLength: 2)

                // MARK: - 4. Word Memory Insights Card (pod kartou)
                if let word = currentWord {
                    wordMemoryInsightsCard(word: word)
                        .padding(.horizontal, 16)
                }

                Spacer(minLength: 4)

                // MARK: - 5. Bottom Control Bar (pod kartou)
                bottomControls
                    .padding(.bottom, 20)
            }
        }
        .onAppear {
            setupSession()
        }
        .onDisappear {
            speechController.stopListening()
        }
    }

    // MARK: - 1. Top Bar View (AppleSessionTopBar)

    private var topBarView: some View {
        VStack(spacing: 8) {
            // First row: Deck title + CEFR badge ... Close button
            HStack(spacing: 8) {
                Text(deck.emoji)
                    .font(.system(size: 20))

                Text(deck.localizedTitle(isCzech: true))
                    .font(.headline)
                    .foregroundColor(.white)
                    .lineLimit(1)

                Text(deck.level.label)
                    .font(.caption2.bold())
                    .foregroundColor(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(deck.level.accentColor)
                    .clipShape(Capsule())

                Spacer()

                Button {
                    speechController.stopListening()
                    onDismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.secondary)
                        .frame(width: 28, height: 28)
                        .background(Color.white.opacity(0.12))
                        .clipShape(Circle())
                }
            }

            // Second row: Lesson progress ... Daily goal & streak
            HStack {
                Text("Lekce: \(currentIndex + 1) / \(sessionWords.count) (zbývá \(max(0, sessionWords.count - currentIndex - 1)))")
                    .font(.caption.bold())
                    .foregroundColor(.blue)

                Spacer()

                HStack(spacing: 4) {
                    Text("Dnes: \(progressManager.todayCompletedWords)/\(progressManager.dailyGoalWords)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text("🔥 \(progressManager.streakDays)")
                        .font(.caption.bold())
                        .foregroundColor(.orange)
                }
            }

            // Progress bar
            ProgressView(value: Double(currentIndex + 1), total: Double(max(1, sessionWords.count)))
                .tint(.blue)
                .scaleEffect(x: 1, y: 0.6, anchor: .center)
        }
        .padding(12)
        .cupertinoGlass(cornerRadius: 18)
    }

    // MARK: - 2. Live Speech Banner

    private var liveSpeechBannerView: some View {
        HStack(spacing: 10) {
            // Glowing status dot or equalizer
            if speechController.isMicEnabled {
                Circle()
                    .fill(Color.green)
                    .frame(width: 8, height: 8)
                    .shadow(color: .green, radius: 4)

                AudioVisualizerView()
            } else {
                Circle()
                    .fill(Color.gray)
                    .frame(width: 8, height: 8)
            }

            // Text status / partial recognition
            if speechController.isMicEnabled {
                if !speechController.partialTranscript.isEmpty {
                    Text(speechController.partialTranscript)
                        .font(.caption.bold())
                        .foregroundColor(.white)
                        .lineLimit(1)
                } else {
                    Text(speechController.isListening ? "Poslouchám výslovnost..." : "Mikrofon připraven...")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            } else {
                Text("Mikrofon je vypnutý (použijte tlačítka nebo swipe)")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            // Direction badge
            Text(isCurrentReversed ? "🇨🇿 CZ" : "\(progressManager.selectedLanguage.flagEmoji) \(progressManager.selectedLanguage.code.uppercased())")
                .font(.caption2.bold())
                .foregroundColor(.secondary)
                .padding(.horizontal, 6)
                .padding(.vertical, 3)
                .background(Color.white.opacity(0.08))
                .clipShape(Capsule())
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(height: 38)
        .cupertinoGlass(cornerRadius: 14)
    }

    // MARK: - 3. 3D Card Stack Area

    @ViewBuilder
    private func cardStackView(word: VocabWord) -> some View {
        ZStack {
            // 3rd card in background (depth)
            if thirdWord != nil {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(Color(white: 0.12))
                    .frame(height: 290)
                    .scaleEffect(0.90)
                    .offset(y: 20)
                    .opacity(0.4)
            }

            // 2nd card in background (depth)
            if nextWord != nil {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(Color(white: 0.16))
                    .frame(height: 290)
                    .scaleEffect(0.95)
                    .offset(y: 10)
                    .opacity(0.7)
            }

            // Active Card (Front)
            activeCardContent(word: word)
        }
        .frame(height: 310)
    }

    @ViewBuilder
    private func activeCardContent(word: VocabWord) -> some View {
        let rotation = Double(dragOffset.width / 22)
        let promptText = isCurrentReversed ? word.foreign : word.czech
        let targetText = isCurrentReversed ? word.czech : word.foreign

        ZStack {
            VStack(spacing: 12) {
                // Card Header: Emoji + Speaker
                HStack {
                    Text(word.emoji)
                        .font(.system(size: 34))

                    Spacer()

                    Button {
                        let text = isCurrentReversed ? word.czech : word.foreign
                        let tag = isCurrentReversed ? "cs-CZ" : progressManager.selectedLanguage.speechLocaleTag
                        speechController.speak(text: text, languageTag: tag)
                    } label: {
                        Image(systemName: "speaker.wave.2.fill")
                            .font(.headline)
                            .foregroundColor(.blue)
                            .padding(8)
                            .background(Color.blue.opacity(0.15))
                            .clipShape(Circle())
                    }
                }

                Spacer()

                // Main Prompt Text
                Text(promptText)
                    .font(.system(size: 32, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .minimumScaleFactor(0.7)
                    .padding(.horizontal, 8)

                Text(isCurrentReversed ? "Přeložte do češtiny" : "Vyslovte v jazyce: \(progressManager.selectedLanguage.localizedTitle(isCzech: true))")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                Spacer()

                // Expandable Hint
                VStack(spacing: 6) {
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
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(Color.yellow.opacity(0.18))
                        .clipShape(Capsule())
                    }

                    if isShowingHint {
                        VStack(spacing: 4) {
                            Text(targetText)
                                .font(.headline.bold())
                                .foregroundColor(.green)

                            if !word.phonetic.isEmpty {
                                Text(word.phonetic)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .transition(.opacity)
                    }
                }
            }
            .padding(18)
            .frame(maxWidth: .infinity)
            .frame(height: 290)
            .background(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(Color(white: 0.15))
                    .overlay(
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .stroke(swipeBorderColor, lineWidth: 2)
                    )
            )
            .shadow(color: Color.black.opacity(0.35), radius: 16, x: 0, y: 8)
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
            .padding(.horizontal, 14)
            .padding(.vertical, 6)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(color, lineWidth: 2)
            )
            .rotationEffect(.degrees(alignment == .topLeading ? -15 : 15))
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: alignment)
            .padding(24)
    }

    // MARK: - 4. Word Memory Insights Card (pod kartou)

    private func wordMemoryInsightsCard(word: VocabWord) -> some View {
        let box = currentSrsStat?.box ?? 0
        let statusTitle: String = {
            if currentSrsStat == nil { return "Nové slovíčko" }
            if box == 0 { return "K procvičení" }
            if box >= 4 { return "V dlouhodobé paměti ✓" }
            return "Upevňuje se • Box \(box)/5"
        }()

        let statusColor: Color = {
            if currentSrsStat == nil { return .blue }
            if box == 0 { return .red }
            if box >= 4 { return .green }
            return .orange
        }()

        return VStack(spacing: 8) {
            HStack {
                HStack(spacing: 6) {
                    Text("🧠")
                        .font(.subheadline)
                    Text(statusTitle)
                        .font(.caption.bold())
                        .foregroundColor(statusColor)
                }

                Spacer()

                // 5 Leitner level segment bars
                HStack(spacing: 3) {
                    ForEach(1...5, id: \.self) { b in
                        RoundedRectangle(cornerRadius: 1.5)
                            .fill(b <= box ? statusColor : Color.white.opacity(0.15))
                            .frame(width: 14, height: 4)
                    }
                }
            }

            // Example sentence if available
            if !word.exampleSentence.isEmpty {
                HStack(alignment: .center, spacing: 6) {
                    Text("\"\(word.exampleSentence)\"")
                        .font(.caption.italic())
                        .foregroundColor(.secondary)
                        .lineLimit(2)

                    Spacer()

                    Button {
                        speechController.speak(
                            text: word.exampleSentence,
                            languageTag: progressManager.selectedLanguage.speechLocaleTag
                        )
                    } label: {
                        Image(systemName: "speaker.wave.1.fill")
                            .font(.caption)
                            .foregroundColor(.blue)
                            .padding(4)
                    }
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .cupertinoGlass(cornerRadius: 16)
    }

    // MARK: - 5. Bottom Controls Bar

    private var bottomControls: some View {
        HStack(spacing: 20) {
            // When Mic is OFF: reveal [✕ Nevím] on left
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

            // Big Center Microphone Button (Toggles Mic)
            Button {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.75)) {
                    speechController.toggleListening()
                }
            } label: {
                ZStack {
                    Circle()
                        .fill(speechController.isMicEnabled ? Color.green : Color(white: 0.25))
                        .frame(width: 72, height: 72)
                        .shadow(
                            color: speechController.isMicEnabled ? Color.green.opacity(0.4) : Color.clear,
                            radius: 12
                        )

                    Image(systemName: speechController.isMicEnabled ? "waveform" : "mic.slash.fill")
                        .font(.title2.bold())
                        .foregroundColor(.white)
                }
            }

            // When Mic is OFF: reveal [✓ Vím] on right
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

    // MARK: - Advance & Gestures Logic

    private func setupSession() {
        sessionWords = progressManager.prioritizeWordsForSession(words: deck.words, targetCount: 10)
        prepareActiveCard()

        speechController.onWordMatched = { [self] word, transcript, method in
            advanceCard(isCorrect: true, method: method, transcript: transcript)
        }
        speechController.onTransitionReady = { [self] in
        }

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
        let threshold: CGFloat = 100
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
