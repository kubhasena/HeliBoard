// SPDX-License-Identifier: GPL-3.0-only
package vidyullekha.keyboard.latin.brahmic

data class BrahmicConfig(
    val mode: BrahmicInputMode,
    val ayogavahaStripVirama: Boolean = false,
    val nuktaPartOfConsonant: Boolean = true,
    /**
     * Vowels the keyboard hands over in their final form because it offers a key for each of the
     * two forms. Insertion of these is never rewritten, so the anti-contextual key keeps working.
     */
    val pairedVowels: Set<Int> = emptySet(),
) {
    companion object {
        @JvmStatic
        fun from(
            mode: Int,
            ayogavahaStripVirama: Boolean,
            nuktaPartOfConsonant: Boolean,
            pairedVowels: Set<Int>,
        ) = BrahmicConfig(BrahmicInputMode.fromInt(mode), ayogavahaStripVirama, nuktaPartOfConsonant, pairedVowels)
    }
}

data class BrahmicEdit(
    @JvmField val deleteCodePoints: Int,
    @JvmField val insert: String,
)

/**
 * Runtime state shared between the keyboard and the rewriter. KeyboardId reads [dependentVowels]
 * when building, and the keyboard publishes [pairedVowels] once it knows the active layout.
 */
object BrahmicUiState {
    @JvmField
    var dependentVowels: Boolean = false

    /** Vowels the active alphabet layout offers in both forms, see [BrahmicConfig.pairedVowels]. */
    @JvmField
    var pairedVowels: Set<Int> = emptySet()

    @JvmStatic
    fun clearPairedVowels() {
        pairedVowels = emptySet()
    }

    /** @return true if the value changed */
    @JvmStatic
    fun setDependentVowels(value: Boolean): Boolean {
        if (dependentVowels == value) return false
        dependentVowels = value
        return true
    }
}

private sealed class Trailing {
    data class LiveConsonant(val codePoints: Int, val script: BrahmicScript) : Trailing()
    data class DeadConsonant(val codePoints: Int, val script: BrahmicScript) : Trailing()
    data class DependentVowel(val codePoints: Int, val attachedToConsonant: Boolean, val script: BrahmicScript) : Trailing()
    data class IndependentVowel(val codePoints: Int, val script: BrahmicScript) : Trailing()
    data object Other : Trailing()
}

/**
 * Pure Brahmic insert/delete rewriter. No Android types.
 * Returns null from [applyInsert] when the original key event should be left unchanged.
 */
object BrahmicInput {
    @JvmStatic
    fun applyInsert(textBefore: CharSequence, input: String, cfg: BrahmicConfig): BrahmicEdit? {
        if (cfg.mode == BrahmicInputMode.GLYPHIC) return null
        if (input.isEmpty()) return null
        if (!inputContainsIndic(input)) return null

        // Apply each code point in turn. A popup like क़ is a consonant plus nukta, so the
        // consonant must pick up a phonetic virama and the nukta then slides in before it.
        var text = textBefore.toString()
        val original = text
        val cps = codePoints(input)
        for (cp in cps) {
            val one = applyOne(text, cp, cfg)
            text = if (one != null) applyEdit(text, one) else text + Character.toString(cp)
        }
        if (text == original + input) return null
        return diff(original, text)
    }

    @JvmStatic
    fun applyDelete(textBefore: CharSequence, cfg: BrahmicConfig): BrahmicEdit? {
        if (!cfg.mode.usesPhoneticDelete) return null
        if (textBefore.isEmpty()) return null
        return when (val unit = trailingUnit(textBefore, cfg)) {
            is Trailing.DependentVowel -> if (unit.attachedToConsonant) {
                BrahmicEdit(unit.codePoints, viramaString(unit.script))
            } else {
                BrahmicEdit(unit.codePoints, "")
            }
            is Trailing.IndependentVowel -> BrahmicEdit(unit.codePoints, "")
            is Trailing.DeadConsonant -> BrahmicEdit(unit.codePoints, "")
            is Trailing.LiveConsonant -> BrahmicEdit(0, viramaString(unit.script))
            Trailing.Other -> null
        }
    }

    @JvmStatic
    fun wantsDependentVowelLabels(textBefore: CharSequence, cfg: BrahmicConfig): Boolean {
        if (!cfg.mode.usesContextualVowels) return false
        return when (trailingUnit(textBefore, cfg)) {
            is Trailing.LiveConsonant -> true
            is Trailing.DeadConsonant -> cfg.mode.usesPhoneticVirama
            else -> false
        }
    }

    private fun applyOne(before: String, cp: Int, cfg: BrahmicConfig): BrahmicEdit? {
        val table = BrahmicScripts.forCodePoint(cp) ?: return null

        val unit = trailingUnit(before, cfg, table)

        if (table.isVirama(cp)) {
            return if (cfg.mode.usesPhoneticVirama && unit is Trailing.DeadConsonant) {
                BrahmicEdit(0, "") // already dead; consume the key
            } else {
                null
            }
        }

        if (table.isNukta(cp)) {
            if (cfg.mode.usesPhoneticVirama && unit is Trailing.DeadConsonant) {
                return BrahmicEdit(1, nuktaViramaString(table))
            }
            return null
        }

        if (table.isAyogavaha(cp)) {
            if (cfg.mode.usesPhoneticVirama && cfg.ayogavahaStripVirama && unit is Trailing.DeadConsonant) {
                return BrahmicEdit(1, Character.toString(cp))
            }
            return null
        }

        if (table.isConsonant(cp)) {
            return if (cfg.mode.usesPhoneticVirama) {
                BrahmicEdit(0, Character.toString(cp) + viramaString(table))
            } else {
                null
            }
        }

        val independent = table.toIndependent(cp)
        val dependent = table.toDependent(cp)
        if (independent == null && dependent == null) return null

        val wantsDependent = when (unit) {
            is Trailing.LiveConsonant -> cfg.mode.usesContextualVowels
            is Trailing.DeadConsonant -> cfg.mode.usesPhoneticVirama
            else -> false
        }

        if (table.isInherentA(cp) || (independent == table.inherentVowel && dependent == null)) {
            if (cfg.mode.usesPhoneticVirama && unit is Trailing.DeadConsonant) {
                return BrahmicEdit(1, "")
            }
            return null
        }

        // The keyboard has a key for each form of this vowel and already picked one, so keep the
        // form that was pressed. Only replacing the phonetic virama still applies, that is
        // mechanical rather than a choice between the two forms.
        if (cp in cfg.pairedVowels) {
            return if (table.isDependentVowel(cp) && cfg.mode.usesPhoneticVirama && unit is Trailing.DeadConsonant)
                BrahmicEdit(1, Character.toString(cp))
            else null
        }

        if (wantsDependent) {
            val matraCp = dependent ?: table.toDependent(independent!!) ?: return null
            val matra = Character.toString(matraCp)
            return if (unit is Trailing.DeadConsonant) {
                BrahmicEdit(1, matra)
            } else if (cp == matraCp) {
                null
            } else {
                BrahmicEdit(0, matra)
            }
        }

        val indCp = independent ?: table.toIndependent(cp) ?: return null
        return if (cp == indCp) null else BrahmicEdit(0, Character.toString(indCp))
    }

    private fun trailingUnit(
        text: CharSequence,
        cfg: BrahmicConfig,
        typed: BrahmicScript? = null,
    ): Trailing {
        val cps = codePoints(text)
        var i = cps.lastIndex
        while (i >= 0 && BrahmicScripts.isJoinControl(cps[i])) i--
        if (i < 0) return Trailing.Other

        val last = cps[i]
        val table = BrahmicScripts.forCodePoint(last) ?: return Trailing.Other
        if (typed != null && table !== typed) return Trailing.Other

        if (i >= 1 && table.isIndependentTwoPart(cps[i - 1], last)) {
            return Trailing.IndependentVowel(2, table)
        }
        if (table.isDependentVowel(last)) {
            val len = table.twoPartDependentLength(cps, i)
            return Trailing.DependentVowel(len, matraAttachedToConsonant(cps, i - len, cfg, table), table)
        }
        if (table.isIndependentVowel(last)) {
            return Trailing.IndependentVowel(1, table)
        }
        if (table.isAyogavaha(last)) return Trailing.Other

        if (table.isVirama(last)) {
            var j = i - 1
            if (j >= 0 && table.isNukta(cps[j]) && cfg.nuktaPartOfConsonant) j--
            if (j >= 0 && table.isConsonant(cps[j])) {
                return Trailing.DeadConsonant(i - j + 1, table)
            }
            return Trailing.Other
        }

        if (table.isNukta(last) && cfg.nuktaPartOfConsonant) {
            if (i >= 1 && table.isConsonant(cps[i - 1])) {
                return Trailing.LiveConsonant(2, table)
            }
            return Trailing.Other
        }

        if (table.isConsonant(last)) return Trailing.LiveConsonant(1, table)
        return Trailing.Other
    }

    private fun matraAttachedToConsonant(
        cps: IntArray,
        lastBeforeMatra: Int,
        cfg: BrahmicConfig,
        table: BrahmicScript,
    ): Boolean {
        var j = lastBeforeMatra
        while (j >= 0 && BrahmicScripts.isJoinControl(cps[j])) j--
        if (j < 0) return false
        if (table.isNukta(cps[j]) && cfg.nuktaPartOfConsonant) {
            return j >= 1 && table.isConsonant(cps[j - 1])
        }
        return table.isConsonant(cps[j])
    }

    private fun inputContainsIndic(input: String): Boolean {
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            if (BrahmicScripts.forCodePoint(cp) != null) return true
            i += Character.charCount(cp)
        }
        return false
    }

    private fun codePoints(text: CharSequence): IntArray {
        val count = Character.codePointCount(text, 0, text.length)
        val out = IntArray(count)
        var i = 0
        var n = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            out[n++] = cp
            i += Character.charCount(cp)
        }
        return out
    }

    private fun join(cps: List<Int>): String {
        if (cps.isEmpty()) return ""
        val sb = StringBuilder()
        for (cp in cps) sb.appendCodePoint(cp)
        return sb.toString()
    }

    private fun applyEdit(text: String, edit: BrahmicEdit): String {
        val cps = codePoints(text)
        return join(cps.dropLast(edit.deleteCodePoints)) + edit.insert
    }

    private fun diff(before: String, after: String): BrahmicEdit {
        val b = codePoints(before)
        val a = codePoints(after)
        var i = 0
        while (i < b.size && i < a.size && b[i] == a[i]) i++
        return BrahmicEdit(b.size - i, join(a.drop(i)))
    }

    private fun viramaString(script: BrahmicScript) =
        if (script.viramaToInsert < 0) "" else Character.toString(script.viramaToInsert)

    private fun nuktaViramaString(script: BrahmicScript): String {
        val sb = StringBuilder(2)
        if (script.nukta >= 0) sb.appendCodePoint(script.nukta)
        if (script.viramaToInsert >= 0) sb.appendCodePoint(script.viramaToInsert)
        return sb.toString()
    }
}
