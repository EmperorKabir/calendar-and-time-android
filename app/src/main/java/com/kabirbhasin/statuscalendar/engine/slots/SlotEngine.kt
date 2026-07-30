package com.kabirbhasin.statuscalendar.engine.slots

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.kabirbhasin.statuscalendar.core.format.RenderedDisplay
import com.kabirbhasin.statuscalendar.engine.DisplayEngine

/**
 * Drives the companion slot apps. Android allocates one status bar icon slot
 * per package, so each installed companion adds a genuine in-bar icon. The text
 * is split across whichever companions are present, left to right.
 */
class SlotEngine(private val context: Context) : DisplayEngine {

    companion object {
        const val ACTION_SHOW = "com.kabirbhasin.statuscalendar.SHOW_SLOT"
        const val ACTION_CLEAR = "com.kabirbhasin.statuscalendar.CLEAR_SLOT"
        val SLOT_PACKAGES = listOf(
            "com.kabirbhasin.statuscalendar.slot1",
            "com.kabirbhasin.statuscalendar.slot2",
            "com.kabirbhasin.statuscalendar.slot3"
        )
    }

    private var active = false
    private var lastChunks: List<String> = emptyList()

    // Probing the package manager three times per tick cost three binder calls and
    // three thrown exceptions a second. Installs are rare, so the answer is cached
    // and only recomputed when the engine is started.
    private var cachedSlots: List<String>? = null

    fun installedSlots(): List<String> =
        cachedSlots ?: SLOT_PACKAGES.filter { isInstalled(it) }.also { cachedSlots = it }

    /** Call when packages may have changed, such as when the settings screen opens. */
    fun refreshInstalled() {
        cachedSlots = null
    }

    private fun isInstalled(pkg: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    }.getOrDefault(false)

    override fun start() {
        active = true
        refreshInstalled()
    }

    override fun stop() {
        active = false
        lastChunks = emptyList()
        SLOT_PACKAGES.forEach { pkg ->
            context.sendBroadcast(
                Intent(ACTION_CLEAR).setPackage(pkg)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            )
        }
    }

    override fun render(display: RenderedDisplay) {
        if (!active) return
        val slots = installedSlots()
        if (slots.isEmpty()) return

        val chunks = split(display, slots.size)
        if (chunks == lastChunks) return
        lastChunks = chunks

        slots.forEachIndexed { index, pkg ->
            val intent = Intent(ACTION_SHOW).setPackage(pkg)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            if (index == 0 && display.stackTop != null) {
                intent.putExtra("top", display.stackTop)
                intent.putExtra("bottom", display.stackBottom)
            } else {
                intent.putExtra("text", chunks.getOrElse(index) { "" })
            }
            context.sendBroadcast(intent)
        }
    }

    /**
     * Each slot is one small square, so the text is divided by meaning rather than by
     * word count: the first slot carries the weekday and date as a calendar, the rest
     * take one element each. Chopping a sentence across slots produced text too small
     * to read.
     */
    private fun split(display: RenderedDisplay, slots: Int): List<String> {
        if (display.stackTop != null) {
            return List(slots) { if (it == 0) "${display.stackTop} ${display.stackBottom}" else "" }
        }
        val pieces = display.line
            .split(",", "·", "|", " - ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (pieces.isEmpty()) return List(slots) { "" }
        return List(slots) { index -> pieces.getOrElse(index) { "" } }
    }
}
