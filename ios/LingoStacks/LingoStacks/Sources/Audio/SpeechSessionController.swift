//
//  SpeechSessionController.swift
//  LingoStacks
//
//  Ported from Android SpeechHelper.kt (v1.0.15 build 121)
//

import Foundation
import Speech
import AVFoundation
import Combine
import UIKit

public final class SpeechSessionController: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    public static let shared = SpeechSessionController()

    // MARK: - Published Properties for SwiftUI

    /// User intent: whether the microphone is toggled ON or OFF
    @Published public var isMicEnabled: Bool = true {
        didSet {
            if isMicEnabled {
                startListening()
            } else {
                stopListening()
            }
        }
    }

    /// True only while the underlying recognition engine is actively streaming
    @Published public private(set) var isListening: Bool = false
    @Published public private(set) var audioLevel: Float = 0.0 // 0.0 ... 1.0 for visualizer
    @Published public private(set) var partialTranscript: String = ""
    @Published public private(set) var isSpeakingTts: Bool = false
    @Published public private(set) var permissionGranted: Bool = false
    @Published public private(set) var errorMessage: String? = nil

    // Target Word for active card matching
    public var currentTargetWord: VocabWord? = nil
    public var expectNativeAnswer: Bool = false
    public var activeLanguage: LearningLanguage = .english

    // Callbacks
    public var onWordMatched: ((VocabWord, String, AnswerMethod) -> Void)?
    public var onTransitionReady: (() -> Void)?

    // MARK: - Internal Audio & Speech Components

    private let audioEngine = AVAudioEngine()
    private var speechRecognizer: SFSpeechRecognizer?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let speechSynthesizer = AVSpeechSynthesizer()

    private var isStartingListening: Bool = false
    private var isPausedForTts: Bool = false
    private var isPausedForTransition: Bool = false
    private var lastSpokenMatchTime: TimeInterval = 0
    private var consecutiveErrors: Int = 0
    private var lastRestartAttemptTime: TimeInterval = 0

    private var ttsCompletionHandler: (() -> Void)?

    override private init() {
        super.init()
        speechSynthesizer.delegate = self
        setupAudioSession()
    }

    // MARK: - Permissions

    public func requestPermissions(completion: @escaping (Bool) -> Void) {
        SFSpeechRecognizer.requestAuthorization { authStatus in
            AVAudioSession.sharedInstance().requestRecordPermission { micGranted in
                DispatchQueue.main.async {
                    let granted = (authStatus == .authorized) && micGranted
                    self.permissionGranted = granted
                    completion(granted)
                }
            }
        }
    }

    private func setupAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth])
            try session.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            print("⚠️ [SpeechSessionController] Audio session setup error: \(error)")
        }
    }

    // MARK: - Listening Lifecycle

    public func toggleListening() {
        isMicEnabled.toggle()
    }

    public func startListening() {
        guard isMicEnabled, !isSpeakingTts, !isPausedForTransition else { return }
        errorMessage = nil
        startRecognitionCycle()
    }

    public func stopListening() {
        stopRecognitionCycle()
    }

    public func pauseForCardTransition() {
        isPausedForTransition = true
        stopRecognitionCycle()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { [weak self] in
            guard let self = self else { return }
            self.isPausedForTransition = false
            if self.isMicEnabled && !self.isSpeakingTts {
                self.startRecognitionCycle()
            }
        }
    }

    private func startRecognitionCycle() {
        guard isMicEnabled, !isListening, !isStartingListening, !isSpeakingTts, !isPausedForTransition else { return }
        isStartingListening = true

        // Clean previous tasks
        if recognitionTask != nil {
            recognitionTask?.cancel()
            recognitionTask = nil
        }
        recognitionRequest = nil

        let localeIdentifier = expectNativeAnswer ? "cs-CZ" : activeLanguage.speechLocaleTag
        speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: localeIdentifier))

        guard let speechRecognizer = speechRecognizer, speechRecognizer.isAvailable else {
            isStartingListening = false
            print("⚠️ [SpeechSessionController] SpeechRecognizer unavailable for \(localeIdentifier)")
            return
        }

        recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
        guard let recognitionRequest = recognitionRequest else {
            isStartingListening = false
            return
        }
        recognitionRequest.shouldReportPartialResults = true

        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)

        // Ensure bus format is valid
        guard recordingFormat.sampleRate > 0 else {
            isStartingListening = false
            return
        }

        inputNode.removeTap(onBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { [weak self] buffer, _ in
            self?.recognitionRequest?.append(buffer)
            self?.calculateRmsAudioLevel(buffer: buffer)
        }

        do {
            audioEngine.prepare()
            try audioEngine.start()
            isListening = true
            isStartingListening = false
            consecutiveErrors = 0
        } catch {
            isStartingListening = false
            isListening = false
            inputNode.removeTap(onBus: 0)
            print("⚠️ [SpeechSessionController] AudioEngine start failed: \(error)")
            return
        }

        recognitionTask = speechRecognizer.recognitionTask(with: recognitionRequest) { [weak self] result, error in
            guard let self = self else { return }

            if let result = result {
                self.consecutiveErrors = 0
                let candidates = [result.bestTranscription.formattedString] +
                    result.transcriptions.map { $0.formattedString }

                let latestSnippet = WordMatcher.shared.extractNormalizedTargets(
                    targetWord: VocabWord(id: "tmp", czech: "", foreign: result.bestTranscription.formattedString)
                ).first ?? result.bestTranscription.formattedString

                DispatchQueue.main.async {
                    self.partialTranscript = latestSnippet
                }

                // Match against active target card
                if let target = self.currentTargetWord {
                    if let matched = WordMatcher.shared.matches(
                        spokenCandidates: candidates,
                        targetWord: target,
                        isFinal: result.isFinal,
                        expectCzech: self.expectNativeAnswer,
                        language: self.activeLanguage
                    ) {
                        self.handleSuccessfulMatch(word: target, matchedTranscript: matched)
                    }
                }
            }

            if error != nil || (result?.isFinal ?? false) {
                self.stopRecognitionCycle()

                // Debounced graceful restart (prevents blinking loops)
                let now = Date().timeIntervalSince1970
                if self.isMicEnabled && !self.isSpeakingTts && !self.isPausedForTransition {
                    self.consecutiveErrors += 1
                    if self.consecutiveErrors <= 3 && (now - self.lastRestartAttemptTime > 0.8) {
                        self.lastRestartAttemptTime = now
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                            if self.isMicEnabled {
                                self.startRecognitionCycle()
                            }
                        }
                    }
                }
            }
        }
    }

    private func stopRecognitionCycle() {
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        recognitionTask = nil
        recognitionRequest = nil

        if audioEngine.isRunning {
            audioEngine.inputNode.removeTap(onBus: 0)
            audioEngine.stop()
        }

        DispatchQueue.main.async {
            self.isListening = false
            self.audioLevel = 0.0
            self.isStartingListening = false
        }
    }

    private func calculateRmsAudioLevel(buffer: AVAudioPCMBuffer) {
        guard let channelData = buffer.floatChannelData?[0] else { return }
        let channelDataValue = Array(UnsafeBufferPointer(start: channelData, count: Int(buffer.frameLength)))

        var sum: Float = 0
        for sample in channelDataValue {
            sum += sample * sample
        }
        let rms = sqrt(sum / Float(buffer.frameLength))
        let normalized = min(1.0, max(0.0, rms * 8.0))

        DispatchQueue.main.async {
            self.audioLevel = normalized
        }
    }

    // MARK: - Success Handling & TTS Pronunciation

    private func handleSuccessfulMatch(word: VocabWord, matchedTranscript: String) {
        let now = Date().timeIntervalSince1970
        guard now - lastSpokenMatchTime > 0.8 else { return }
        lastSpokenMatchTime = now

        // 1. Haptic feedback
        if ProgressManager.shared.hapticFeedbackEnabled {
            let generator = UINotificationFeedbackGenerator()
            generator.notificationOccurred(.success)
        }

        // 2. Stop microphone listening immediately so TTS is not recorded
        stopRecognitionCycle()

        // 3. Notify ProgressManager & UI
        DispatchQueue.main.async {
            self.onWordMatched?(word, matchedTranscript, .spokenCorrect)
        }

        // 4. Pronounce native word via TTS if enabled
        if ProgressManager.shared.autoPronounce {
            let textToSpeak = self.expectNativeAnswer ? word.czech : word.foreign
            let langTag = self.expectNativeAnswer ? "cs-CZ" : self.activeLanguage.speechLocaleTag

            speak(text: textToSpeak, languageTag: langTag) { [weak self] in
                self?.onTransitionReady?()
            }
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
                self?.onTransitionReady?()
            }
        }
    }

    public func speak(text: String, languageTag: String, completion: (() -> Void)? = nil) {
        if speechSynthesizer.isSpeaking {
            speechSynthesizer.stopSpeaking(at: .immediate)
        }

        isSpeakingTts = true
        ttsCompletionHandler = completion

        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: languageTag) ?? AVSpeechSynthesisVoice(language: "en-US")
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate * 0.95
        utterance.pitchMultiplier = 1.0

        speechSynthesizer.speak(utterance)
    }

    // MARK: - AVSpeechSynthesizerDelegate

    public func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.isSpeakingTts = false
            self.ttsCompletionHandler?()
            self.ttsCompletionHandler = nil

            if self.isMicEnabled && !self.isPausedForTransition {
                self.startRecognitionCycle()
            }
        }
    }

    public func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        DispatchQueue.main.async { [weak self] in
            self?.isSpeakingTts = false
            self?.ttsCompletionHandler = nil
        }
    }
}
