//
//  FlashcardSessionView.swift
//  LingoStacks
//
//  Complete 1:1 Apple Cupertino Glass flashcard session matching Android FlashcardSessionScreen.kt
//  - Dynamic Light & Dark Mode palette
//  - TopBar with Lesson & Daily Metrics + Progress Bar + Close button
//  - LiveSpeechBanner with animated mic dot & audio equalizer
//  - 3D Flashcard with CardCornerToolbarCapsule, Emoji tile, Prompt & Word typography
//  - Revealed translation slot with audio pronounce & phonetic
//  - WordMemoryInsightsCard with Leitner 5-box indicators & example sentence
//  - Symmetrical auto-expanding BottomControlBar (compact mic when listening; expands to [✕] [🎤] [✓] when mic is off)
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

    // Card Swipe & Animation States
    @State private var dragOffset: CGSize = .zero
    @State private var isShowingHint: Bool = false
    @State private var isCardTransitioning: Bool = false
    @State private var cardFeedback: CardFeedbackType = .neutral
    @State private var cardScale: CGFloat = 1.0
    @State private var cardOpacity: Double = 1.0

    // Mic Breathing Animation
    @State private var isBreathing: Bool = false

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
            // Dynamic semantic background for Light & Dark mode
            Color(uiColor: .systemGroupedBackground)
                .ignoresSafeArea()

            VStack(spacing: 8) {
                // MARK: - 1. Apple Session TopBar
                topBarView
                    .padding(.horizontal, 16)
                    .padding(.top, 6)

                // MARK: - 2. Live Speech Banner
                liveSpeechBannerView
                    .padding(.horizontal, 16)

                Spacer(minLength: 4)

                // MARK: - 3. 3D Stack Flashcard Area
                if let word = currentWord {
                    cardStackView(word: word)
                        .padding(.horizontal, 16)
                }

                Spacer(minLength: 4)

                // MARK: - 4. Word Memory Insights Card
                if let word = currentWord {
                    wordMemoryInsightsCard(word: word)
                        .padding(.horizontal, 16)
                }

                Spacer(minLength: 10)

                // MARK: - 5. Bottom Control Bar (Symmetrically Expanding Capsule)
                bottomControlsView
                    .padding(.bottom, 16)
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
            // Row 1: Deck Title + Level Badge ... Close Button
            HStack(spacing: 8) {
                Text(deck.emoji)
                    .font(.system(size: 20))
                    .frame(width: 28, height: 28)
                    .background(deck.level.accentColor.opacity(0.14))
                    .clipShape(Circle())

                Text(deck.localizedTitle(isCzech: true))
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.primary)
                    .lineLimit(1)

                Text(deck.level.label)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(deck.level.accentColor)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(deck.level.accentColor.opacity(0.14))
                    .clipShape(Capsule())

                Spacer()

                Button {
                    speechController.stopListening()
                    onDismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.secondary)
                        .frame(width: 28, height: 28)
                        .background(Color(uiColor: .tertiarySystemGroupedBackground))
                        .clipShape(Circle())
                }
            }

            // Row 2: Dual Progress Metrics (Lesson on Left, Daily Goal + Streak on Right)
            HStack {
                Text("Lekce: \(currentIndex + 1) / \(sessionWords.count) (zbývá \(max(0, sessionWords.count - currentIndex - 1)))")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(LingoColors.appleBlue)

                Spacer()

                HStack(spacing: 6) {
                    Text("Dnes: \(progressManager.todayCompletedWords)/\(progressManager.dailyGoalWords)")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)

                    Text("🔥 \(progressManager.streakDays)")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(LingoColors.appleOrange)
                }
            }

            // Row 3: Thin Smooth Progress Bar
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color(uiColor: .separator).opacity(0.35))
                        .frame(height: 4)

                    Capsule()
                        .fill(LingoColors.appleBlue)
                        .frame(
                            width: max(8, geo.size.width * CGFloat(currentIndex + 1) / CGFloat(max(1, sessionWords.count))),
                            height: 4
                        )
                        .animation(.spring(response: 0.35), value: currentIndex)
                }
            }
            .frame(height: 4)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Color(uiColor: .secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(Color(uiColor: .separator).opacity(0.5), lineWidth: 0.8)
        )
        .shadow(color: Color.black.opacity(0.04), radius: 8, x: 0, y: 3)
    }

    // MARK: - 2. Live Speech Banner

    private var liveSpeechBannerView: some View {
        HStack(spacing: 10) {
            // Animated Status Dot or Equalizer
            if speechController.isMicEnabled {
                Circle()
                    .fill(LingoColors.appleGreen)
                    .frame(width: 8, height: 8)
                    .shadow(color: LingoColors.appleGreen.opacity(0.8), radius: 4)
                    .scaleEffect(isBreathing ? 1.25 : 0.9)
                    .animation(
                        .easeInOut(duration: 0.9).repeatForever(autoreverses: true),
                        value: isBreathing
                    )

                AudioVisualizerView()
                    .frame(height: 14)
            } else {
                Circle()
                    .fill(Color.secondary.opacity(0.5))
                    .frame(width: 8, height: 8)
            }

            // Transcript or Status Text
            if speechController.isMicEnabled {
                if !speechController.partialTranscript.isEmpty {
                    Text("„\(speechController.partialTranscript)“")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(LingoColors.appleBlue)
                        .lineLimit(1)
                } else {
                    Text(speechController.isListening ? "Poslouchám výslovnost..." : "Mikrofon připraven...")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                }
            } else {
                Text("Mikrofon vypnut (použijte tlačítka nebo swipe)")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }

            Spacer()

            // Target Language Pill
            Text(isCurrentReversed ? "🇨🇿 CZ" : "\(progressManager.selectedLanguage.flagEmoji) \(progressManager.selectedLanguage.code.uppercased())")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.secondary)
                .padding(.horizontal, 7)
                .padding(.vertical, 3)
                .background(Color(uiColor: .tertiarySystemGroupedBackground))
                .clipShape(Capsule())
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(height: 38)
        .background(Color(uiColor: .secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color(uiColor: .separator).opacity(0.5), lineWidth: 0.8)
        )
        .onAppear {
            isBreathing = true
        }
    }

    // MARK: - 3. 3D Card Stack Area

    @ViewBuilder
    private func cardStackView(word: VocabWord) -> some View {
        ZStack {
            // 3rd card in background (depth)
            if thirdWord != nil {
                RoundedRectangle(cornerRadius: 30, style: .continuous)
                    .fill(Color(uiColor: .secondarySystemGroupedBackground).opacity(0.5))
                    .frame(height: 295)
                    .scaleEffect(0.90)
                    .offset(y: 18)
                    .shadow(color: Color.black.opacity(0.04), radius: 8, y: 4)
            }

            // 2nd card in background (depth)
            if nextWord != nil {
                RoundedRectangle(cornerRadius: 30, style: .continuous)
                    .fill(Color(uiColor: .secondarySystemGroupedBackground).opacity(0.75))
                    .frame(height: 295)
                    .scaleEffect(0.95)
                    .offset(y: 9)
                    .shadow(color: Color.black.opacity(0.05), radius: 10, y: 5)
            }

            // Active Card (Front)
            activeCardContent(word: word)
        }
        .frame(height: 315)
    }

    @ViewBuilder
    private func activeCardContent(word: VocabWord) -> some View {
        let rotation = Double(dragOffset.width / 24)
        let promptText = isCurrentReversed ? word.foreign : word.czech
        let targetText = isCurrentReversed ? word.czech : word.foreign

        ZStack {
            VStack(spacing: 10) {
                // Top Row: Level / Swipe Badges + Corner Toolbar Capsule
                HStack(alignment: .center) {
                    // Left: Level badge or Swipe feedback
                    if dragOffset.width < -60 || cardFeedback == .wrong {
                        Text("NEVÍM")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(LingoColors.appleRed)
                            .clipShape(Capsule())
                    } else if dragOffset.width > 60 || cardFeedback == .correct {
                        Text("VÍM")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(LingoColors.appleGreen)
                            .clipShape(Capsule())
                    } else {
                        let box = currentSrsStat?.box ?? 0
                        let badgeText = box > 0 ? "\(deck.level.label) • Box \(box)/5" : deck.level.label
                        Text(badgeText)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(deck.level.accentColor)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 4)
                            .background(deck.level.accentColor.opacity(0.12))
                            .clipShape(Capsule())
                    }

                    Spacer()

                    // Right: CardCornerToolbarCapsule (Eye, Speaker, Sound)
                    cardCornerToolbarCapsule
                }

                Spacer(minLength: 2)

                // Middle: Emoji Glass Tile
                ZStack {
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(Color(uiColor: .tertiarySystemGroupedBackground))
                        .frame(width: 58, height: 58)
                        .overlay(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .stroke(Color(uiColor: .separator).opacity(0.4), lineWidth: 0.8)
                        )

                    Text(word.emoji)
                        .font(.system(size: 30))
                }

                // Prompt Header (Small bold caps in Apple Blue)
                Text(isCurrentReversed ? "PŘELOŽTE DO ČEŠTINY" : "VYSLOVTE V JAZYCE: \(progressManager.selectedLanguage.localizedTitle(isCzech: true).uppercased())")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(LingoColors.appleBlue)
                    .tracking(0.8)

                // Main Prompt Word (Large extra-bold typography)
                Text(promptText)
                    .font(.system(size: 27, weight: .heavy, design: .rounded))
                    .foregroundColor(.primary)
                    .multilineTextAlignment(.center)
                    .minimumScaleFactor(0.65)
                    .lineLimit(2)
                    .padding(.horizontal, 8)

                if isCurrentReversed && !word.phonetic.isEmpty {
                    Text(word.phonetic)
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                }

                Spacer(minLength: 2)

                // Bottom Slot: Dedicated Hint Pill OR Revealed Translation Card
                if !isShowingHint {
                    Button {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) {
                            isShowingHint = true
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "lightbulb.fill")
                                .font(.system(size: 13))
                                .foregroundColor(LingoColors.appleOrange)
                            Text("Zobrazit nápovědu")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.primary)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color(uiColor: .tertiarySystemGroupedBackground))
                        .clipShape(Capsule())
                        .overlay(
                            Capsule().stroke(Color(uiColor: .separator).opacity(0.6), lineWidth: 0.6)
                        )
                    }
                    .buttonStyle(.plain)
                } else {
                    // Revealed translation card with TTS speaker
                    VStack(spacing: 4) {
                        HStack(spacing: 8) {
                            Text(targetText)
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(LingoColors.appleBlue)

                            Button {
                                let tag = isCurrentReversed ? "cs-CZ" : progressManager.selectedLanguage.speechLocaleTag
                                speechController.speak(text: targetText, languageTag: tag)
                            } label: {
                                Image(systemName: "speaker.wave.2.fill")
                                    .font(.system(size: 14))
                                    .foregroundColor(LingoColors.appleBlue)
                                    .frame(width: 26, height: 26)
                                    .background(LingoColors.appleBlue.opacity(0.14))
                                    .clipShape(Circle())
                            }
                        }

                        if !isCurrentReversed && !word.phonetic.isEmpty {
                            Text(word.phonetic)
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                        }

                        if !word.exampleSentence.isEmpty {
                            Text("„\(word.exampleSentence)“")
                                .font(.system(size: 11).italic())
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity)
                    .background(Color(uiColor: .tertiarySystemGroupedBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity)
            .frame(height: 295)
            .background(cardBackgroundColor)
            .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 30, style: .continuous)
                    .stroke(cardBorderColor, lineWidth: cardBorderWidth)
            )
            .shadow(color: Color.black.opacity(0.08), radius: 14, x: 0, y: 6)
            .scaleEffect(cardScale)
            .opacity(cardOpacity)
            .offset(dragOffset)
            .rotationEffect(.degrees(rotation))
            .gesture(
                DragGesture()
                    .onChanged { gesture in
                        guard !isCardTransitioning else { return }
                        dragOffset = gesture.translation
                    }
                    .onEnded { gesture in
                        handleDragEnd(gesture: gesture)
                    }
            )
        }
    }

    // MARK: - Card Corner Toolbar Capsule (1:1 with Android)

    private var cardCornerToolbarCapsule: some View {
        HStack(spacing: 8) {
            // Eye: Toggle pauseCardWhileSpeaking
            Button {
                progressManager.pauseCardWhileSpeaking.toggle()
            } label: {
                Image(systemName: progressManager.pauseCardWhileSpeaking ? "eye.fill" : "eye.slash")
                    .font(.system(size: 12))
                    .foregroundColor(progressManager.pauseCardWhileSpeaking ? LingoColors.appleBlue : .secondary.opacity(0.5))
            }

            // Speaker: Toggle autoPronounce
            Button {
                progressManager.autoPronounce.toggle()
            } label: {
                Image(systemName: progressManager.autoPronounce ? "speaker.wave.2.fill" : "speaker.slash")
                    .font(.system(size: 12))
                    .foregroundColor(progressManager.autoPronounce ? LingoColors.appleBlue : .secondary.opacity(0.5))
            }

            // Quick Pronounce Active Word
            Button {
                guard let word = currentWord else { return }
                let text = isCurrentReversed ? word.czech : word.foreign
                let tag = isCurrentReversed ? "cs-CZ" : progressManager.selectedLanguage.speechLocaleTag
                speechController.speak(text: text, languageTag: tag)
            } label: {
                Image(systemName: "play.circle.fill")
                    .font(.system(size: 13))
                    .foregroundColor(LingoColors.appleBlue)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color(uiColor: .tertiarySystemGroupedBackground))
        .clipShape(Capsule())
        .overlay(
            Capsule().stroke(Color(uiColor: .separator).opacity(0.5), lineWidth: 0.6)
        )
    }

    private var cardBackgroundColor: Color {
        switch cardFeedback {
        case .correct:
            return LingoColors.appleGreen.opacity(0.12)
        case .wrong:
            return LingoColors.appleRed.opacity(0.12)
        case .neutral:
            return Color(uiColor: .secondarySystemGroupedBackground)
        }
    }

    private var cardBorderColor: Color {
        if dragOffset.width > 60 || cardFeedback == .correct {
            return LingoColors.appleGreen
        }
        if dragOffset.width < -60 || cardFeedback == .wrong {
            return LingoColors.appleRed
        }
        if speechController.isMicEnabled && speechController.isListening {
            return LingoColors.appleBlue.opacity(0.5)
        }
        return Color(uiColor: .separator).opacity(0.6)
    }

    private var cardBorderWidth: CGFloat {
        if abs(dragOffset.width) > 60 || cardFeedback != .neutral {
            return 2.5
        }
        return 1.0
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
            if currentSrsStat == nil { return LingoColors.appleBlue }
            if box == 0 { return LingoColors.appleRed }
            if box >= 4 { return LingoColors.appleGreen }
            return LingoColors.appleOrange
        }()

        return VStack(spacing: 6) {
            HStack {
                HStack(spacing: 6) {
                    Text("🧠")
                        .font(.system(size: 13))
                    Text(statusTitle)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(statusColor)
                }

                Spacer()

                // 5 Leitner level segment bars
                HStack(spacing: 3) {
                    ForEach(1...5, id: \.self) { b in
                        RoundedRectangle(cornerRadius: 1.5)
                            .fill(b <= box ? statusColor : Color(uiColor: .separator).opacity(0.4))
                            .frame(width: 14, height: 4)
                    }
                }
            }

            // Example sentence if available
            if !word.exampleSentence.isEmpty {
                HStack(alignment: .center, spacing: 6) {
                    Text("„\(word.exampleSentence)“")
                        .font(.system(size: 12).italic())
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
                            .font(.system(size: 12))
                            .foregroundColor(LingoColors.appleBlue)
                            .frame(width: 24, height: 24)
                            .background(LingoColors.appleBlue.opacity(0.12))
                            .clipShape(Circle())
                    }
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(Color(uiColor: .secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color(uiColor: .separator).opacity(0.5), lineWidth: 0.8)
        )
    }

    // MARK: - 5. Bottom Controls Bar (Auto-Expanding Symmetrical Capsule)

    private var bottomControlsView: some View {
        HStack(spacing: 0) {
            // Left Button: [✕ Nevím] - Only visible when mic is OFF
            if !speechController.isMicEnabled {
                Button {
                    triggerCardResolution(isCorrect: false, method: .swipedDontKnow)
                } label: {
                    ZStack {
                        Circle()
                            .fill(LingoColors.appleRed.opacity(0.14))
                            .frame(width: 48, height: 48)

                        Image(systemName: "xmark")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(LingoColors.appleRed)
                    }
                }
                .transition(.scale.combined(with: .opacity))
                .padding(.trailing, 16)
            }

            // Center Button: Big Microphone Toggle
            Button {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.72)) {
                    speechController.toggleListening()
                }
            } label: {
                ZStack {
                    // Pulsating ring when listening
                    if speechController.isMicEnabled {
                        Circle()
                            .stroke(LingoColors.appleBlue.opacity(0.35), lineWidth: 3)
                            .frame(width: 66, height: 66)
                            .scaleEffect(isBreathing ? 1.15 : 1.0)
                            .animation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true), value: isBreathing)
                    }

                    Circle()
                        .fill(
                            speechController.isMicEnabled ?
                                LinearGradient(colors: [LingoColors.appleBlue, LingoColors.appleIndigo], startPoint: .topLeading, endPoint: .bottomTrailing) :
                                LinearGradient(colors: [Color(uiColor: .tertiaryLabel), Color(uiColor: .secondaryLabel)], startPoint: .topLeading, endPoint: .bottomTrailing)
                        )
                        .frame(width: 54, height: 54)
                        .shadow(
                            color: speechController.isMicEnabled ? LingoColors.appleBlue.opacity(0.35) : Color.clear,
                            radius: 10,
                            y: 4
                        )

                    Image(systemName: speechController.isMicEnabled ? "mic.fill" : "mic.slash.fill")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundColor(.white)
                }
            }

            // Right Button: [✓ Vím] - Only visible when mic is OFF
            if !speechController.isMicEnabled {
                Button {
                    triggerCardResolution(isCorrect: true, method: .swipedKnow)
                } label: {
                    ZStack {
                        Circle()
                            .fill(LingoColors.appleGreen.opacity(0.14))
                            .frame(width: 48, height: 48)

                        Image(systemName: "checkmark")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(LingoColors.appleGreen)
                    }
                }
                .transition(.scale.combined(with: .opacity))
                .padding(.leading, 16)
            }
        }
        .padding(.horizontal, speechController.isMicEnabled ? 8 : 14)
        .padding(.vertical, 7)
        .background(Color(uiColor: .secondarySystemGroupedBackground).opacity(0.95))
        .clipShape(Capsule())
        .overlay(
            Capsule().stroke(Color(uiColor: .separator).opacity(0.65), lineWidth: 0.8)
        )
        .shadow(color: Color.black.opacity(0.08), radius: 10, y: 5)
        .animation(.spring(response: 0.35, dampingFraction: 0.72), value: speechController.isMicEnabled)
    }

    // MARK: - Card Transition & Gesture Logic

    private func setupSession() {
        sessionWords = progressManager.prioritizeWordsForSession(words: deck.words, targetCount: 10)
        prepareActiveCard()

        speechController.onWordMatched = { [self] word, transcript, method in
            triggerCardResolution(isCorrect: true, method: method, transcript: transcript)
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
        cardFeedback = .neutral
        cardScale = 1.0
        cardOpacity = 1.0
        isCardTransitioning = false

        speechController.currentTargetWord = word
        speechController.expectNativeAnswer = isCurrentReversed
        speechController.activeLanguage = progressManager.selectedLanguage
    }

    private func handleDragEnd(gesture: DragGesture.Value) {
        let threshold: CGFloat = 85
        if gesture.translation.width > threshold {
            triggerCardResolution(isCorrect: true, method: .swipedKnow)
        } else if gesture.translation.width < -threshold {
            triggerCardResolution(isCorrect: false, method: .swipedDontKnow)
        } else {
            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                dragOffset = .zero
            }
        }
    }

    private func triggerCardResolution(
        isCorrect: Bool,
        method: AnswerMethod,
        transcript: String? = nil
    ) {
        guard !isCardTransitioning, let word = currentWord else { return }
        isCardTransitioning = true

        cardFeedback = isCorrect ? .correct : .wrong

        if speechController.isMicEnabled {
            speechController.pauseForCardTransition()
        }

        let attempt = CardAttemptResult(
            word: word,
            isCorrect: isCorrect && !isShowingHint,
            method: method,
            spokenTranscript: transcript,
            wasReversed: isCurrentReversed
        )
        attemptResults.append(attempt)
        progressManager.recordCardAttempt(result: attempt, usedHint: isShowingHint, isReversed: isCurrentReversed)

        // Pronounce if needed
        if !isCurrentReversed && (!isCorrect || progressManager.autoPronounce) {
            speechController.speak(text: word.foreign, languageTag: progressManager.selectedLanguage.speechLocaleTag)
        }

        // Smooth exit glide
        let targetX: CGFloat = isCorrect ? 500 : -500
        withAnimation(.easeIn(duration: 0.22)) {
            dragOffset = CGSize(width: targetX, height: 0)
            cardOpacity = 0.0
            cardScale = 0.94
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.24) {
            advanceToNextWord()
        }
    }

    private func advanceToNextWord() {
        if currentIndex + 1 < sessionWords.count {
            currentIndex += 1
            prepareActiveCard()
        } else {
            speechController.stopListening()
            progressManager.recordSessionCompleted(deckId: deck.id)
            onSessionFinished(attemptResults)
        }
    }
}

private enum CardFeedbackType {
    case neutral
    case correct
    case wrong
}
