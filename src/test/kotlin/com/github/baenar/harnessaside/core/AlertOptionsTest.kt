package com.github.baenar.harnessaside.core

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** The two gear-menu alert options, against the platform's real notification bus. */
class AlertOptionsTest : BasePlatformTestCase() {

    private val status by lazy { SessionStatus(project, showIcon = { _, _ -> }, isSeen = { false }) }
    private val sound = SessionStatus.playSound
    private var sounds = 0
    private var notifications = 0

    override fun setUp() {
        super.setUp()
        SessionStatus.playSound = { sounds++ }
        project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                notifications++
            }
        })
    }

    override fun tearDown() {
        try {
            HarnessStatusManager.soundOnInput = false
            HarnessStatusManager.soundWhenDone = false
            SessionStatus.playSound = sound
            Disposer.dispose(status)
        } finally {
            super.tearDown()
        }
    }

    fun `test with the input option on a wait plays one sound and shows one notification`() {
        HarnessStatusManager.soundOnInput = true

        status.onStatusChanged(HarnessStatus.WAITING)
        status.onStatusChanged(HarnessStatus.WAITING) // Claude reports one dialog through two hooks

        assertEquals(1, sounds)
        assertEquals(1, notifications)
    }

    fun `test with the options off nothing makes a sound`() {
        status.onStatusChanged(HarnessStatus.WAITING)
        status.onStatusChanged(HarnessStatus.DONE)

        assertEquals(0, sounds)
    }

    fun `test with the done option on a finished turn plays a sound and shows a notification`() {
        HarnessStatusManager.soundWhenDone = true

        status.onStatusChanged(HarnessStatus.THINKING)
        status.onStatusChanged(HarnessStatus.DONE)

        assertEquals(1, sounds)
        assertEquals(1, notifications)
    }

    fun `test an interrupted turn stays silent and the next one alerts`() {
        HarnessStatusManager.soundWhenDone = true

        status.onStatusChanged(HarnessStatus.THINKING)
        status.onInterrupt()
        status.onStatusChanged(HarnessStatus.DONE)
        assertEquals(0, sounds)

        status.onStatusChanged(HarnessStatus.THINKING)
        status.onStatusChanged(HarnessStatus.DONE)
        assertEquals(1, sounds)
    }
}
