//
//  StudyReminderScheduler.swift
//  LingoStacks
//
//  Ported from Android StudyReminderScheduler (v1.0.15 build 121)
//

import Foundation
import UserNotifications

public final class StudyReminderScheduler {
    public static let shared = StudyReminderScheduler()

    private let reminderIdentifier = "lingostacks_daily_study_reminder"

    public func requestPermission(completion: @escaping (Bool) -> Void) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            DispatchQueue.main.async {
                completion(granted)
            }
        }
    }

    public func scheduleDailyReminder(hour: Int = 19, minute: Int = 0, isCzech: Bool = true) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [reminderIdentifier])

        let content = UNMutableNotificationContent()
        content.title = isCzech ? "Čas na procvičení slovíček! 🗣️" : "Time for daily vocabulary practice! 🗣️"
        content.body = isCzech ?
            "Udržte si svou denní sérii a splňte dnešní cíl v LingoStacks." :
            "Keep your daily streak going and finish today's goal in LingoStacks."
        content.sound = .default

        var dateComponents = DateComponents()
        dateComponents.hour = hour
        dateComponents.minute = minute

        let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: true)
        let request = UNNotificationRequest(identifier: reminderIdentifier, content: content, trigger: trigger)

        center.add(request) { error in
            if let error = error {
                print("⚠️ [StudyReminderScheduler] Failed to schedule reminder: \(error)")
            } else {
                print("✓ [StudyReminderScheduler] Daily reminder scheduled for \(hour):\(String(format: "%02d", minute))")
            }
        }
    }

    public func cancelReminder() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [reminderIdentifier])
    }
}
