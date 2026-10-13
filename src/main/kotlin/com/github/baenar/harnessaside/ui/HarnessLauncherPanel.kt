package com.github.baenar.harnessaside.ui

import com.github.baenar.harnessaside.core.Harness
import com.github.baenar.harnessaside.core.HarnessRegistry
import com.intellij.icons.AllIcons
import com.intellij.util.IconUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Empty-state panel displayed in the tool window when no harness session is currently active.
 *
 * Presents a list of supported harnesses from [HarnessRegistry] allowing the user to launch one.
 */
class HarnessLauncherPanel(
    private val project: Project,
    private val toolWindow: ToolWindow,
) : JBPanel<HarnessLauncherPanel>(BorderLayout()) {

    init {
        border = JBUI.Borders.empty(16)
        background = UIUtil.getPanelBackground()

        val contentBox = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        // Header section
        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT

            val titleLabel = JBLabel("Harness Aside").apply {
                font = JBFont.h3().asBold()
            }
            val subtitleLabel = JBLabel("Select an AI coding harness to launch in the side terminal:").apply {
                foreground = UIUtil.getContextHelpForeground()
                font = JBFont.regular()
            }

            add(titleLabel)
            add(Box.createVerticalStrut(JBUI.scale(4)))
            add(subtitleLabel)
            add(Box.createVerticalStrut(JBUI.scale(16)))
        }
        contentBox.add(headerPanel)

        // List of supported harnesses
        val listPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
        }

        for (harness in HarnessRegistry.registered) {
            listPanel.add(createHarnessCard(harness))
            listPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
        }

        contentBox.add(listPanel)
        contentBox.add(Box.createVerticalGlue())

        val scrollPane = JBScrollPane(contentBox).apply {
            border = BorderFactory.createEmptyBorder()
            isOpaque = false
            viewport.isOpaque = false
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        add(scrollPane, BorderLayout.CENTER)
    }

    private fun createHarnessCard(harness: Harness): JPanel {
        val defaultBorder = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border(), 1, true),
            JBUI.Borders.empty(12)
        )
        val hoverBorder = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBUI.CurrentTheme.Focus.focusColor(), 1, true),
            JBUI.Borders.empty(12)
        )

        val card = JPanel(BorderLayout(JBUI.scale(12), 0)).apply {
            border = defaultBorder
            background = UIUtil.getTextFieldBackground()
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(64))
            alignmentX = Component.LEFT_ALIGNMENT
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        // Icon (sized consistently to match the default harness icon)
        val rawIcon = harness.icon ?: AllIcons.Nodes.Console
        val targetSize = HarnessRegistry.default.icon?.iconWidth ?: JBUI.scale(20)
        val icon = if (rawIcon.iconWidth > targetSize || rawIcon.iconHeight > targetSize) {
            IconUtil.resizeSquared(rawIcon, targetSize)
        } else {
            rawIcon
        }
        val iconLabel = JBLabel(icon)
        card.add(iconLabel, BorderLayout.WEST)

        // Details (Display Name & Executable)
            val textPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false

            val nameLabel = JBLabel(harness.displayName).apply {
                font = JBFont.label().asBold()
            }
            val cmdLabel = JBLabel("Runs `${harness.executable}`").apply {
                foreground = UIUtil.getContextHelpForeground()
                font = JBFont.small()
            }

            add(nameLabel)
            add(Box.createVerticalStrut(JBUI.scale(2)))
            add(cmdLabel)
        }
        card.add(textPanel, BorderLayout.CENTER)

        // Start Button
        val launchBtn = JButton("Start").apply {
            font = JBFont.small().asBold()
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                launchHarness(harness)
            }
        }
        card.add(launchBtn, BorderLayout.EAST)

        // Clicking anywhere on card launches harness
        card.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                launchHarness(harness)
            }

            override fun mouseEntered(e: MouseEvent) {
                card.border = hoverBorder
            }

            override fun mouseExited(e: MouseEvent) {
                card.border = defaultBorder
            }
        })

        return card
    }

    private fun launchHarness(harness: Harness) {
        harness.openSession(project, toolWindow)
    }
}
