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

    private var shouldStayListening: Bool = false
    private var isPausedForTts: Bool = false
    private var isPausedForTransition: Bool = false
    private var lastSpokenMatchTime: TimeInterval = 0

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
        if isListening || shouldStayListening {
            stopListening()
        } else {
            startListening()
        }
    }

    public func startListening() {
        shouldStayListening = true
        errorMessage = nil
        guard !isSpeakingTts && !isPausedForTransition else { return }

        startRecognitionCycle()
    }

    public func stopListening() {
        shouldStayListening = false
        stopRecognitionCycle()
    }

    public func pauseForCardTransition() {
        isPausedForTransition = true
        stopRecognitionCycle()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { [weak self] in
            guard let self = self else { return }
            self.isPausedForTransition = false
            if self.shouldStayListening && !self.isSpeakingTts {
                self.startRecognitionCycle()
            }
        }
    }

    private func startRecognitionCycle() {
        guard !isListening, !isSpeakingTts, !isPausedForTransition else { return }

        // Cancel any pending task
        recognitionTask?.cancel()
        recognitionTask = nil

        let localeIdentifier = expectNativeAnswer ? "cs-CZ" : activeLanguage.speechLocaleTag
        speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: localeIdentifier))

        guard let speechRecognizer = speechRecognizer, speechRecognizer.isAvailable else {
            errorMessage = "Rozpoznávání řeči není v tomto jazyce dostupné."
            return
        }

        recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
        guard let recognitionRequest = recognitionRequest else { return }
        recognitionRequest.shouldReportPartialResults = true

        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)

        // Install audio tap for RMS volume equalizer
        inputNode.removeTap(onBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { [weak self] buffer, _ in
            self?.recognitionRequest?.append(buffer)
            self?.calculateRmsAudioLevel(buffer: buffer)
        }

        do {
            audioEngine.prepare()
            try audioEngine.start()
            isListening = true
        } catch {
            errorMessage = "Chyba spuštění mikrofonu: \(error.localizedDescription)"
            isListening = false
            return
        }

        recognitionTask = speechRecognizer.recognitionTask(with: recognitionRequest) { [weak self] result, error in
            guard let self = self else { return }

            if let result = result {
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
                // Auto-restart if user still wants to listen and not paused
                if self.shouldStayListening && !self.isSpeakingTts && !self.isPausedForTransition {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                        self.startRecognitionCycle()
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
        // Normalize roughly between 0.0 and 1.0 with soft exponential curve
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
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate * 0.95 // slightly relaxed speed for clear pronunciation
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

            // Resume listening if user had mic active
            if self.shouldStayListening && !self.isPausedForTransition {
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
