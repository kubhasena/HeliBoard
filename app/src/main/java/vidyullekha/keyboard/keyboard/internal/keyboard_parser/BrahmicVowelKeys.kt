// SPDX-License-Identifier: GPL-3.0-only
package vidyullekha.keyboard.keyboard.internal.keyboard_parser

import android.content.Context
import vidyullekha.keyboard.keyboard.internal.KeyboardParams
import vidyullekha.keyboard.keyboard.internal.keyboard_parser.floris.AbstractKeyData
import vidyullekha.keyboard.keyboard.internal.keyboard_parser.floris.CaseSelector
import vidyullekha.keyboard.keyboard.internal.keyboard_parser.floris.CharWidthSelector
import vidyullekha.keyboard.keyboard.internal.keyboard_parser.floris.KanaSelector
import vidyullekha.keyboard.keyboard.internal.keyboard_parser.floris.KeyData
import vidyullekha.keyboard.keyboard.internal.keyboard_parser.floris.KeyboardStateSelector
import vidyullekha.keyboard.keyboard.internal.keyboard_parser.floris.LayoutDirectionSelector
import vidyullekha.keyboard.keyboard.internal.keyboard_parser.floris.MultiTextKeyData
import vidyullekha.keyboard.keyboard.internal.keyboard_parser.floris.PopupSet
import vidyullekha.keyboard.keyboard.internal.keyboard_parser.floris.ShiftStateSelector
import vidyullekha.keyboard.keyboard.internal.keyboard_parser.floris.SimplePopups
import vidyullekha.keyboard.keyboard.internal.keyboard_parser.floris.VariationSelector
import vidyullekha.keyboard.latin.brahmic.BrahmicScripts
import vidyullekha.keyboard.latin.brahmic.BrahmicVowelRemap
import vidyullekha.keyboard.latin.brahmic.BrahmicVowelSlot

/**
 * Finds which Brahmic vowels a main layout offers and how hard each one is to reach, so that
 * [BrahmicVowelRemap] can decide which slot of a vowel pair follows the cursor context.
 *
 * The scan runs on the layout before [AbstractKeyData.compute], so branches for all shift states
 * are still visible at once. Language popups count too, since a matra reachable only through them
 * is still a usable sibling key.
 */
object BrahmicVowelKeys {

    fun remapFor(params: KeyboardParams, context: Context): BrahmicVowelRemap {
        if (!params.mId.element.isAlphabet) return BrahmicVowelRemap.NONE
        val dependentContext = params.mId.brahmicDependentVowels ?: return BrahmicVowelRemap.NONE
        return BrahmicVowelRemap.build(LayoutParser.brahmicVowelSlots(params, context), dependentContext)
    }

    /**
     * Vowels the layout offers in both independent and matra form. Used by the rewriter so the
     * anti-contextual key can type an orphaned matra even when labels are not swapping.
     */
    fun pairsFor(params: KeyboardParams, context: Context): Set<Int> {
        if (!params.mId.element.isAlphabet) return emptySet()
        return BrahmicVowelRemap.build(LayoutParser.brahmicVowelSlots(params, context), false).pairedCodePoints
    }

    /** @return easiest slot rank per vowel code point, see [BrahmicVowelSlot] */
    fun scanSlots(
        rows: List<List<AbstractKeyData>>,
        languagePopups: (String) -> Collection<String>? = { null },
        priorityPopups: (String) -> Collection<String>? = { null },
    ): Map<Int, Int> = Scanner(languagePopups, priorityPopups).run {
        rows.forEach { row -> row.forEach { scan(it, BrahmicVowelSlot.UNSHIFTED) } }
        slots
    }

    private class Scanner(
        private val languagePopups: (String) -> Collection<String>?,
        private val priorityPopups: (String) -> Collection<String>?,
    ) {
        val slots = HashMap<Int, Int>()

        fun scan(data: AbstractKeyData?, slot: Int) {
            when (data) {
                null -> return
                is ShiftStateSelector -> {
                    scan(data.unshifted, slot)
                    scan(data.default, slot)
                    val shifted = maxOf(slot, BrahmicVowelSlot.SHIFTED)
                    scan(data.shifted, shifted)
                    scan(data.shiftedManual, shifted)
                    scan(data.shiftedAutomatic, shifted)
                    scan(data.capsLock, shifted)
                    scan(data.manualOrLocked, shifted)
                }
                is CaseSelector -> {
                    scan(data.lower, slot)
                    scan(data.upper, maxOf(slot, BrahmicVowelSlot.SHIFTED))
                }
                is VariationSelector -> listOf(
                    data.default, data.normal, data.email, data.uri, data.password, data.date, data.time, data.datetime
                ).forEach { scan(it, slot) }
                is KeyboardStateSelector -> listOf(
                    data.default, data.alphabet, data.symbols, data.moreSymbols, data.dpad,
                    data.emojiKeyEnabled, data.languageKeyEnabled, data.dpadKeyEnabled, data.emojiSearchAvailable
                ).forEach { scan(it, slot) }
                is LayoutDirectionSelector -> {
                    scan(data.ltr, slot)
                    scan(data.rtl, slot)
                }
                is CharWidthSelector -> {
                    scan(data.full, slot)
                    scan(data.half, slot)
                }
                is KanaSelector -> {
                    scan(data.hira, slot)
                    scan(data.kata, slot)
                }
                    // label and codePoints are separate here, so remapping the label alone would make
                    // the key lie about what it types. Only its popups can take part.
                    is MultiTextKeyData -> scanPopups(data.popup, slot)
                is KeyData -> {
                    add(data.label, slot)
                    if (data.code > 0) add(data.code, slot)
                    scanPopups(data.popup, slot)
                    scanLanguagePopups(data.label)
                }
                else -> return
            }
        }

        private fun scanPopups(popup: PopupSet<out AbstractKeyData>, slot: Int) {
            val main = maxOf(slot, BrahmicVowelSlot.POPUP_MAIN)
            val other = maxOf(slot, BrahmicVowelSlot.POPUP_OTHER)
            if (popup is SimplePopups) {
                // the first entry is what a plain long press types
                popup.popupKeys?.forEachIndexed { i, key -> add(key, if (i == 0) main else other) }
                return
            }
            scan(popup.main, main)
            popup.relevant?.forEach { scan(it, other) }
        }

        private fun scanLanguagePopups(label: String) {
            if (label.isEmpty()) return
            priorityPopups(label)?.forEach { add(it, BrahmicVowelSlot.POPUP_MAIN) }
            languagePopups(label)?.forEach { add(it, BrahmicVowelSlot.POPUP_OTHER) }
        }

        private fun add(label: String, slot: Int) {
            if (label.isEmpty() || label.startsWith("!")) return
            val cp = BrahmicScripts.leadingVowel(label.substringBefore('|'))
            if (cp >= 0) add(cp, slot)
        }

        private fun add(codePoint: Int, slot: Int) {
            if (!BrahmicScripts.isVowel(codePoint)) return
            val known = slots[codePoint]
            if (known == null || slot < known) slots[codePoint] = slot
        }
    }
}
