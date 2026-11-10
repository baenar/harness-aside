package com.github.baenar.harnessaside.core

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** The two gear-menu alert options, against the platform's real notification bus. */
class AlertOptionsTest : BasePlatformTestCase() {

    private val manager get() = project.service<HarnessStatusManager>()
    private var sounds = 0
    private var notifications = 0

    override fun setUp() {
        super.setUp()
        manager.playSound = { sounds++ }
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
            manager.onStatusChanged(HarnessStatus.THINKING) // forgets any interrupt
            manager.onStatusChanged(HarnessStatus.IDLE)
        } finally {
            super.tearDown()
        }
    }

    fun `test with the input option on a wait plays one sound and shows one notification`() {
        HarnessStatusManager.soundOnInput = true

        manager.onStatusChanged(HarnessStatus.WAITING)
        manager.onStatusChanged(HarnessStatus.WAITING) // Claude reports one dialog through two hooks

        assertEquals(1, sounds)
        assertEquals(1, notifications)
    }

    fun `test with the options off nothing makes a sound`() {
        manager.onStatusChanged(HarnessStatus.WAITING)
        manager.onStatusChanged(HarnessStatus.DONE)

        assertEquals(0, sounds)
    }

    fun `test with the done option on a finished turn plays a sound and shows a notification`() {
        HarnessStatusManager.soundWhenDone = true

        manager.onStatusChanged(HarnessStatus.THINKING)
        manager.onStatusChanged(HarnessStatus.DONE)

        assertEquals(1, sounds)
        assertEquals(1, notifications)
    }

    fun `test an interrupted turn stays silent and the next one alerts`() {
        HarnessStatusManager.soundWhenDone = true

        manager.onStatusChanged(HarnessStatus.THINKING)
        manager.onInterrupt()
        manager.onStatusChanged(HarnessStatus.DONE)
        assertEquals(0, sounds)

        manager.onStatusChanged(HarnessStatus.THINKING)
        manager.onStatusChanged(HarnessStatus.DONE)
        assertEquals(1, sounds)
    }
}
