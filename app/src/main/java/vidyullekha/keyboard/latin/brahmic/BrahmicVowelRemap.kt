// SPDX-License-Identifier: GPL-3.0-only
package vidyullekha.keyboard.latin.brahmic

/**
 * Puts the independent vowel or the matra on a keyboard's vowel keys, following the cursor context.
 *
 * When a layout offers both forms of a vowel, the two slots swap: the one that is easier to reach
 * carries the form the context asks for, the other carries the opposite form so a dedicated key
 * for it stays available. When a layout offers only one of the forms, that single key always
 * carries the contextual form.
 *
 * Marks that ride along with a vowel on the same key, as in आं, stay untouched, so such a key keeps
 * behaving like the plain vowel and just types its extra code points along with it.
 *
 * Slot ranks come from [BrahmicVowelSlot]; lower is easier to reach.
 */
class BrahmicVowelRemap private constructor(
    private val map: Map<Int, Int>,
    private val inverse: Map<Int, Int>,
    /** Both forms of every paired vowel. The rewriter must leave these alone, the keys are exact. */
    @JvmField val pairedCodePoints: Set<Int>,
) {
    @JvmField
    val isEmpty: Boolean = map.isEmpty()

    fun remapCodePoint(codePoint: Int): Int = map[codePoint] ?: codePoint

    /** Remaps the vowel [label] starts with, keeping whatever marks follow it. */
    fun remapLabel(label: String): String = replaceLeadingVowel(label, map)

    /**
     * Turns a remapped label back into the one the layout wrote, for looking up data that is keyed
     * by layout labels, such as the language popup keys of a subtype.
     */
    fun originalLabel(label: String): String = replaceLeadingVowel(label, inverse)

    /**
     * Remaps a popup key spec. Specs carrying an explicit output different from their label are
     * left alone, because the layout meant something other than typing the label.
     */
    fun remapKeySpec(spec: String): String {
        if (isEmpty || spec.isEmpty() || spec.startsWith("!")) return spec
        val separator = spec.indexOf('|')
        if (separator < 0) return remapLabel(spec)
        val label = spec.substring(0, separator)
        if (label != spec.substring(separator + 1)) return spec
        val newLabel = remapLabel(label)
        return if (newLabel == label) spec else "$newLabel|$newLabel"
    }

    private fun replaceLeadingVowel(label: String, table: Map<Int, Int>): String {
        if (table.isEmpty() || label.isEmpty()) return label
        val vowel = BrahmicScripts.leadingVowel(label)
        if (vowel < 0) return label
        val newVowel = table[vowel] ?: return label
        val consumed = BrahmicScripts.leadingVowelLength(label)
        return Character.toString(newVowel) + label.substring(consumed)
    }

    companion object {
        @JvmField
        val NONE = BrahmicVowelRemap(emptyMap(), emptyMap(), emptySet())

        /**
         * @param slots easiest slot rank found on the keyboard for each vowel code point
         * @param dependentContext whether the cursor sits where a matra belongs
         */
        @JvmStatic
        fun build(slots: Map<Int, Int>, dependentContext: Boolean): BrahmicVowelRemap {
            if (slots.isEmpty()) return NONE
            val map = HashMap<Int, Int>()
            val inverse = HashMap<Int, Int>()
            val paired = HashSet<Int>()
            val seen = HashSet<Int>()
            for (cp in slots.keys) {
                val family = BrahmicScripts.forCodePoint(cp)?.familyOf(cp) ?: continue
                val (independent, dependent) = family
                if (!seen.add(independent)) continue
                val independentSlot = slots[independent]
                val dependentSlot = slots[dependent]
                val contextual = if (dependentContext) dependent else independent
                if (independentSlot == null || dependentSlot == null) {
                    if (independentSlot == null && dependentSlot == null) continue
                    // only one key for this vowel, so it shows the contextual form and the rewriter
                    // keeps converting for it, which lands on the same character
                    val lone = if (independentSlot != null) independent else dependent
                    if (lone != contextual) put(map, inverse, lone, contextual)
                    continue
                }
                paired.add(independent)
                paired.add(dependent)
                val easy = if (dependentSlot <= independentSlot) dependent else independent
                if (easy == contextual) continue // layout already matches the context
                val hard = if (easy == dependent) independent else dependent
                put(map, inverse, easy, contextual)
                put(map, inverse, hard, if (contextual == dependent) independent else dependent)
            }
            if (map.isEmpty() && paired.isEmpty()) return NONE
            return BrahmicVowelRemap(map, inverse, paired)
        }

        private fun put(map: MutableMap<Int, Int>, inverse: MutableMap<Int, Int>, from: Int, to: Int) {
            map[from] = to
            inverse[to] = from
        }
    }
}

/** How hard a key is to reach, easiest first. */
object BrahmicVowelSlot {
    const val UNSHIFTED = 0
    const val SHIFTED = 1
    const val POPUP_MAIN = 2
    const val POPUP_OTHER = 3
}
