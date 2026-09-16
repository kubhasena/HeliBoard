// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.brahmic

import helium314.keyboard.keyboard.internal.keyboard_parser.BrahmicVowelKeys
import helium314.keyboard.keyboard.internal.keyboard_parser.LayoutParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrahmicVowelKeysTest {
    private fun slotsOf(layout: String) = BrahmicVowelKeys.scanSlots(
        LayoutParser.parseJsonString(File("src/main/assets/layouts/main/$layout.json").readText())
    )

    @Test
    fun hindiPairsMatrasWithTheirVowels() {
        val slots = slotsOf("hindi")
        // hindi.json puts the matra unshifted and the independent vowel on manualOrLocked
        assertEquals(BrahmicVowelSlot.UNSHIFTED, slots[0x093E]) // ा
        assertEquals(BrahmicVowelSlot.SHIFTED, slots[0x0906]) // आ
        assertEquals(BrahmicVowelSlot.UNSHIFTED, slots[0x094C]) // ौ
        assertEquals(BrahmicVowelSlot.SHIFTED, slots[0x0914]) // औ

        val paired = BrahmicVowelRemap.build(slots, dependentContext = true).pairedCodePoints
        // every vowel with a matra on this layout has both slots
        listOf(0x0906, 0x0907, 0x0908, 0x0909, 0x090A, 0x090F, 0x0910, 0x0911, 0x0913, 0x0914)
            .forEach { assertTrue(it in paired, "missing independent ${Character.toString(it)}") }
        // ऋ is not on hindi.json, only its matra ृ, so it stays unpaired and keeps being rewritten
        assertFalse(0x0943 in paired)
    }

    @Test
    fun hindiSwapsSlotsAtSyllableStart() {
        val slots = slotsOf("hindi")
        assertTrue(BrahmicVowelRemap.build(slots, dependentContext = true).isEmpty)

        val remap = BrahmicVowelRemap.build(slots, dependentContext = false)
        assertEquals("आ", remap.remapLabel("ा"))
        assertEquals("ा", remap.remapLabel("आ"))
        assertEquals("ओ", remap.remapLabel("ो"))
        assertEquals("ो", remap.remapLabel("ओ"))
        // consonants, virama and the inherent vowel are never touched
        assertEquals("क", remap.remapLabel("क"))
        assertEquals("्", remap.remapLabel("्"))
        assertEquals("अ", remap.remapLabel("अ"))
        assertEquals("ः", remap.remapLabel("ः"))
    }

    @Test
    fun hindiLoneMatraFollowsTheContextToo() {
        val slots = slotsOf("hindi")
        // ऋ is only on hindi.json as its matra ृ, so that single key has to follow the context
        assertEquals("ऋ", BrahmicVowelRemap.build(slots, dependentContext = false).remapLabel("ृ"))
        assertEquals("ृ", BrahmicVowelRemap.build(slots, dependentContext = true).remapLabel("ृ"))
    }

    @Test
    fun vowelWithAnAttachedMarkCountsAsThatVowel() {
        val slots = BrahmicVowelKeys.scanSlots(
            LayoutParser.parseSimpleString("ा"),
            languagePopups = { if (it == "ा") listOf("ां", "आं") else null },
        )
        // आं is the आ key with an anusvara riding along, so it fills the independent slot
        assertEquals(BrahmicVowelSlot.POPUP_OTHER, slots[0x0906])
        val remap = BrahmicVowelRemap.build(slots, dependentContext = false)
        assertEquals(setOf(0x0906, 0x093E), remap.pairedCodePoints)
        assertEquals("आं", remap.remapKeySpec("ां"))
        assertEquals("ां", remap.remapKeySpec("आं"))
    }

    @Test
    fun latinLayoutHasNoVowelPairs() {
        val slots = BrahmicVowelKeys.scanSlots(
            LayoutParser.parseSimpleString(File("src/main/assets/layouts/main/qwerty.txt").readText())
        )
        assertTrue(slots.isEmpty())
        assertTrue(BrahmicVowelRemap.build(slots, dependentContext = false).isEmpty)
    }

    @Test
    fun popupOnlySiblingStillCounts() {
        val slots = BrahmicVowelKeys.scanSlots(
            LayoutParser.parseJsonString("""[[{ "label": "ा", "popup": { "main": { "label": "आ" } } }]]""")
        )
        assertEquals(BrahmicVowelSlot.UNSHIFTED, slots[0x093E])
        assertEquals(BrahmicVowelSlot.POPUP_MAIN, slots[0x0906])
        assertEquals(setOf(0x0906, 0x093E), BrahmicVowelRemap.build(slots, dependentContext = true).pairedCodePoints)
    }

    @Test
    fun languagePopupSiblingStillCounts() {
        val slots = BrahmicVowelKeys.scanSlots(
            LayoutParser.parseSimpleString("ा"),
            languagePopups = { if (it == "ा") listOf("आ") else null },
        )
        assertEquals(BrahmicVowelSlot.POPUP_OTHER, slots[0x0906])
        assertEquals(setOf(0x0906, 0x093E), BrahmicVowelRemap.build(slots, dependentContext = true).pairedCodePoints)
    }
}
