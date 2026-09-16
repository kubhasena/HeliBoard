// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.brahmic

enum class BrahmicInputMode {
    GLYPHIC,
    CONTEXTUAL,
    PHONETIC;

    val usesContextualVowels get() = this != GLYPHIC
    val usesPhoneticVirama get() = this == PHONETIC
    val usesPhoneticDelete get() = this == PHONETIC

    fun cycle() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromInt(value: Int) = entries.getOrElse(value) { GLYPHIC }
    }
}
