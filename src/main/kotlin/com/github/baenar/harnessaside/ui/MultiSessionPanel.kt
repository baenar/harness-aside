package com.github.baenar.harnessaside.ui

import com.github.baenar.harnessaside.core.HarnessRegistry
import com.github.baenar.harnessaside.core.HarnessStatus
import com.github.baenar.harnessaside.core.HarnessTerminalService
import com.github.baenar.harnessaside.core.SessionStatus
import com.github.baenar.harnessaside.manul.Manul
import com.intellij.icons.AllIcons
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.UUID
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ToolTipManager
import kotlin.random.Random

/**
 * Multi-session mode: a scrollable strip of tabs above the selected tab's content. A tab shows the launcher
 * until a harness is picked, then that harness's terminal. Each tab's manul comes from its own seed and shows
 * that session's status. Tabs come back after an IDE restart, and a restored tab starts its terminal the first
 * time it is selected, so a restart doesn't start every harness at once.
 */
class MultiSessionPanel(private val project: Project, private val toolWindow: ToolWindow) : JPanel(BorderLayout()), Disposable {

    private val state = project.service<HarnessTabsState>().saved
    private val tabs = mutableListOf<Tab>()
    private val strip = JPanel().apply { layout = BoxLayout(this, BoxLayout.X_AXIS) }
    private val cards = CardLayout()
    private val body = JPanel(cards)
    private var selected: Tab? = null

    /** Called with the header text whenever the selected tab changes what it runs. */
    var onTitleChanged: (String) -> Unit = {}

    val title: String get() = selected?.harness?.let { "running ${it.displayName}" } ?: "Launcher"
    val selectedWidget: TerminalWidget? get() = selected?.widget
    val hasRunningSessions: Boolean get() = tabs.any { it.widget != null }

    init {
        strip.border = JBUI.Borders.empty(GAP)
        strip.add(JButton(AllIcons.General.Add).apply {
            toolTipText = "New tab"
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusable = false
            addActionListener {
                select(add(newTab()))
                persist()
            }
        })
        add(JBScrollPane(strip, JBScrollPane.VERTICAL_SCROLLBAR_NEVER, JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED).apply {
            border = JBUI.Borders.customLineBottom(JBColor.border())
        }, BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)

        val selectedIndex = state.selected
        state.tabs.toList().forEach(::add)
        if (tabs.isEmpty()) add(newTab())
        select(tabs[selectedIndex.coerceIn(tabs.indices)])
    }

    private fun newTab() = HarnessTabsState.SavedTab().apply { seed = Random.nextInt() }

    private fun add(saved: HarnessTabsState.SavedTab): Tab {
        val tab = Tab(saved)
        tabs += tab
        strip.add(tab.card, strip.componentCount - 1)
        strip.add(Box.createHorizontalStrut(JBUI.scale(GAP)), strip.componentCount - 1)
        // A restored tab gets its terminal once selected; until then there is nothing to show.
        show(tab, if (tab.harness == null) launcher(tab) else JPanel())
        strip.revalidate()
        return tab
    }

    private fun launcher(tab: Tab) = HarnessLauncherPanel { harness ->
        tab.saved.harness = harness.id
        tab.saved.session = UUID.randomUUID().toString()
        tab.card.repaint()
        persist()
        onTitleChanged(title)
        start(tab)
    }

    private fun show(tab: Tab, content: JComponent) {
        body.remove(tab.content)
        tab.content = content
        body.add(content, tab.key)
        if (selected === tab) cards.show(body, tab.key)
    }

    private fun select(tab: Tab) {
        selected = tab
        cards.show(body, tab.key)
        tabs.forEach { it.card.repaint() }
        tab.card.scrollRectToVisible(Rectangle(tab.card.size))
        if (tab.harness != null && tab.widget == null) start(tab)
        tab.widget?.requestFocus()
        persist(save = false)
        onTitleChanged(title)
    }

    private fun start(tab: Tab) {
        val harness = tab.harness ?: return
        project.service<HarnessTerminalService>().launch(harness, tab.saved.session, tab.status) { widget, _ ->
            tab.widget = widget
            show(tab, widget.component)
            if (selected === tab) widget.requestFocus()
        }
    }

    /** Ends the tab's session, which kills its pty. The last tab gives way to a fresh launcher. */
    private fun close(tab: Tab) {
        val index = tabs.indexOf(tab)
        tabs.remove(tab)
        Disposer.dispose(tab.status)
        strip.remove(strip.getComponentZOrder(tab.card) + 1) // the gap after it
        strip.remove(tab.card)
        body.remove(tab.content)
        if (tabs.isEmpty()) add(newTab())
        if (selected === tab) select(tabs[index.coerceAtMost(tabs.lastIndex)])
        persist()
        strip.revalidate()
        strip.repaint()
    }

    /**
     * Copies the tabs into [state]. A change to which tabs exist or what they run is saved right away, so it
     * survives an IDE restart; just switching tabs waits for the IDE's next save.
     */
    private fun persist(save: Boolean = true) {
        state.tabs = tabs.mapTo(mutableListOf()) { it.saved }
        state.selected = tabs.indexOf(selected).coerceAtLeast(0)
        if (save) SaveAndSyncHandler.getInstance().scheduleProjectSave(project)
    }

    override fun dispose() {
        tabs.forEach { Disposer.dispose(it.status) }
    }

    private inner class Tab(val saved: HarnessTabsState.SavedTab) {
        val key = UUID.randomUUID().toString()
        val manul = Manul(saved.seed)
        val card = TabCard(this)
        var content: JComponent = JPanel()
        var widget: TerminalWidget? = null
        val harness get() = HarnessRegistry.find(saved.harness)

        /** Disposing it ends the session too. */
        val status = SessionStatus(
            project,
            showIcon = { state, frame ->
                card.cat = manul.icon(state, frame, CAT_SIZE)
                card.repaint()
            },
            isSeen = { toolWindow.isVisible && selected === this },
            notificationIcon = manul.icon(HarnessStatus.IDLE, 0, 16),
        )
    }

    /** The tab in the strip: its manul, and, while hovered, "–" to close it. Hovering it names the harness. */
    private inner class TabCard(private val tab: Tab) : JComponent() {
        var cat: Icon = tab.manul.icon(HarnessStatus.IDLE, 0, CAT_SIZE)
        private var hover = false

        init {
            JBUI.size(CARD_WIDTH, CARD_HEIGHT).let { preferredSize = it; minimumSize = it; maximumSize = it }
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            ToolTipManager.sharedInstance().registerComponent(this)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = if (closeArea().contains(e.point)) close(tab) else select(tab)

                override fun mouseEntered(e: MouseEvent) {
                    hover = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    hover = false
                    repaint()
                }
            })
        }

        override fun getToolTipText(event: MouseEvent): String =
            if (closeArea().contains(event.point)) "Close tab" else tab.harness?.displayName ?: "Launcher"

        private fun closeArea() = JBUI.scale(CORNER).let { Rectangle(width - it, 0, it, it) }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val background = when {
                    selected === tab -> JBUI.CurrentTheme.ActionButton.pressedBackground()
                    hover -> JBUI.CurrentTheme.ActionButton.hoverBackground()
                    else -> null
                }
                background?.let {
                    g2.color = it
                    JBUI.scale(8).let { arc -> g2.fillRoundRect(0, 0, width, height, arc, arc) }
                }
                cat.paintIcon(this, g2, (width - cat.iconWidth) / 2, (height - cat.iconHeight) / 2)
                if (hover) {
                    val minus = AllIcons.General.Remove
                    val area = closeArea()
                    minus.paintIcon(this, g2, area.x + (area.width - minus.iconWidth) / 2, area.y + (area.height - minus.iconHeight) / 2)
                }
            } finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        const val GAP = 4
        const val CARD_WIDTH = 64
        const val CARD_HEIGHT = 48
        const val CAT_SIZE = 40
        const val CORNER = 14
    }
}
