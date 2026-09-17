// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.brahmic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrahmicVowelRemapTest {
    private val aa = 0x0906 // आ
    private val matraAa = 0x093E // ा
    private val i = 0x0907 // इ
    private val matraI = 0x093F // ि

    /** Hindi: matra unshifted, independent vowel on shift. */
    private val hindiAaSlots = mapOf(matraAa to BrahmicVowelSlot.UNSHIFTED, aa to BrahmicVowelSlot.SHIFTED)

    @Test
    fun dependentContextLeavesHindiAlone() {
        val remap = BrahmicVowelRemap.build(hindiAaSlots, dependentContext = true)
        assertTrue(remap.isEmpty)
        assertEquals(setOf(aa, matraAa), remap.pairedCodePoints)
    }

    @Test
    fun independentContextSwapsBothSlots() {
        val remap = BrahmicVowelRemap.build(hindiAaSlots, dependentContext = false)
        assertFalse(remap.isEmpty)
        assertEquals(aa, remap.remapCodePoint(matraAa))
        assertEquals(matraAa, remap.remapCodePoint(aa))
        assertEquals("आ", remap.remapLabel("ा"))
        assertEquals("ा", remap.remapLabel("आ"))
    }

    @Test
    fun independentOnTheEasierSlotSwapsTheOtherWay() {
        val slots = mapOf(aa to BrahmicVowelSlot.UNSHIFTED, matraAa to BrahmicVowelSlot.POPUP_MAIN)
        val independent = BrahmicVowelRemap.build(slots, dependentContext = false)
        assertTrue(independent.isEmpty) // labels already match, but both forms exist
        assertEquals(setOf(aa, matraAa), independent.pairedCodePoints)
        val remap = BrahmicVowelRemap.build(slots, dependentContext = true)
        assertEquals(matraAa, remap.remapCodePoint(aa))
        assertEquals(aa, remap.remapCodePoint(matraAa))
    }

    @Test
    fun lonelyFormFollowsContextWithoutBeingPaired() {
        val slots = mapOf(matraAa to BrahmicVowelSlot.UNSHIFTED, i to BrahmicVowelSlot.SHIFTED)

        val independentContext = BrahmicVowelRemap.build(slots, dependentContext = false)
        assertEquals(aa, independentContext.remapCodePoint(matraAa)) // lone matra shows its vowel
        assertEquals(i, independentContext.remapCodePoint(i)) // lone vowel is already contextual
        // the rewriter has to keep converting for lone keys, so they must not be reported as paired
        assertTrue(independentContext.pairedCodePoints.isEmpty())

        val dependentContext = BrahmicVowelRemap.build(slots, dependentContext = true)
        assertEquals(matraAa, dependentContext.remapCodePoint(matraAa))
        assertEquals(matraI, dependentContext.remapCodePoint(i)) // lone vowel shows its matra
        assertTrue(dependentContext.pairedCodePoints.isEmpty())
    }

    @Test
    fun onlyPairedFamiliesAreReportedAsPaired() {
        val slots = mapOf(
            matraAa to BrahmicVowelSlot.UNSHIFTED, aa to BrahmicVowelSlot.SHIFTED,
            matraI to BrahmicVowelSlot.UNSHIFTED,
        )
        val remap = BrahmicVowelRemap.build(slots, dependentContext = false)
        assertEquals(setOf(aa, matraAa), remap.pairedCodePoints)
        assertEquals(i, remap.remapCodePoint(matraI)) // still remapped, just not paired
    }

    @Test
    fun marksRidingAlongWithAVowelAreKept() {
        val remap = BrahmicVowelRemap.build(hindiAaSlots, dependentContext = false)
        assertEquals("आं", remap.remapLabel("ां"))
        assertEquals("ाँ", remap.remapLabel("आँ"))
        assertEquals("आं|आं", remap.remapKeySpec("ां|ां"))
        // a vowel that follows something else belongs to a syllable, not to the key
        assertEquals("क्षा", remap.remapLabel("क्षा"))
        assertEquals("ज्ञ", remap.remapLabel("ज्ञ"))
    }

    @Test
    fun originalLabelUndoesTheRemapForLabelsTheLayoutHas() {
        val slots = hindiAaSlots + mapOf(matraI to BrahmicVowelSlot.UNSHIFTED) // ि is lone here
        val remap = BrahmicVowelRemap.build(slots, dependentContext = false)
        listOf("ा", "आ", "ां", "आँ", "ि", "क", "ः").forEach {
            assertEquals(it, remap.originalLabel(remap.remapLabel(it)), "round trip of $it")
        }
    }

    @Test
    fun keySpecsKeepTheirOutput() {
        val remap = BrahmicVowelRemap.build(hindiAaSlots, dependentContext = false)
        assertEquals("आ|आ", remap.remapKeySpec("ा|ा"))
        assertEquals("!icon/settings_key|!code/key_settings", remap.remapKeySpec("!icon/settings_key|!code/key_settings"))
        assertEquals("ा|आ", remap.remapKeySpec("ा|आ")) // explicit differing output, not ours to change
        assertEquals("क", remap.remapKeySpec("क"))
    }

    @Test
    fun noneIsInert() {
        assertTrue(BrahmicVowelRemap.NONE.isEmpty)
        assertEquals("ा", BrahmicVowelRemap.NONE.remapLabel("ा"))
        assertEquals(matraAa, BrahmicVowelRemap.NONE.remapCodePoint(matraAa))
    }

    @Test
    fun twoPartBengaliLabelCountsAsOneVowel() {
        val o = 0x0993 // ও
        val matraO = 0x09CB // ো
        val slots = mapOf(matraO to BrahmicVowelSlot.UNSHIFTED, o to BrahmicVowelSlot.SHIFTED)
        val remap = BrahmicVowelRemap.build(slots, dependentContext = false)
        assertEquals("ও", remap.remapLabel("ো")) // ে + া is the decomposed matra ো
        assertEquals("ো", remap.remapLabel("ও"))
    }
}
