// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.brahmic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrahmicInputTest {
    private val glyphic = BrahmicConfig(BrahmicInputMode.GLYPHIC)
    private val contextual = BrahmicConfig(BrahmicInputMode.CONTEXTUAL)
    private val phonetic = BrahmicConfig(BrahmicInputMode.PHONETIC)
    private val phoneticStripAyogavaha = BrahmicConfig(
        BrahmicInputMode.PHONETIC,
        ayogavahaStripVirama = true,
    )
    private val phoneticNuktaSeparate = BrahmicConfig(
        BrahmicInputMode.PHONETIC,
        nuktaPartOfConsonant = false,
    )
    private val pairedAa = setOf(0x0906, 0x093E) // आ and ा both have a key
    private val contextualPairedAa = BrahmicConfig(BrahmicInputMode.CONTEXTUAL, pairedVowels = pairedAa)
    private val phoneticPairedAa = BrahmicConfig(BrahmicInputMode.PHONETIC, pairedVowels = pairedAa)

    private fun insert(before: String, input: String, cfg: BrahmicConfig) =
        BrahmicInput.applyInsert(before, input, cfg)

    private fun delete(before: String, cfg: BrahmicConfig) =
        BrahmicInput.applyDelete(before, cfg)

    private fun apply(before: String, edit: BrahmicEdit): String {
        val cps = before.codePoints().toArray()
        val keep = cps.dropLast(edit.deleteCodePoints)
        val sb = StringBuilder()
        for (cp in keep) sb.appendCodePoint(cp)
        sb.append(edit.insert)
        return sb.toString()
    }

    private fun type(before: String, input: String, cfg: BrahmicConfig): String {
        val edit = insert(before, input, cfg) ?: return before + input
        return apply(before, edit)
    }

    @Test
    fun glyphicIsPassThrough() {
        assertNull(insert("", "क", glyphic))
        assertNull(insert("क", "आ", glyphic))
        assertNull(delete("का", glyphic))
    }

    @Test
    fun contextualIndependentAtStart() {
        assertNull(insert("", "आ", contextual))
        assertEquals("आ", type("", "आ", contextual))
    }

    @Test
    fun contextualDependentAfterLiveConsonant() {
        assertEquals("का", type("क", "आ", contextual))
        assertEquals("का", type("क", "ा", contextual))
        assertNull(insert("क", "ा", contextual))
    }

    @Test
    fun contextualInherentAStaysIndependentAfterLiveConsonant() {
        assertNull(insert("क", "अ", contextual))
        assertEquals("कअ", type("क", "अ", contextual))
    }

    @Test
    fun contextualAfterViramaDoesNotAttach() {
        assertNull(insert("क्", "आ", contextual))
        assertEquals("क्आ", type("क्", "आ", contextual))
    }

    @Test
    fun phoneticConsonantAddsVirama() {
        assertEquals("क्", type("", "क", phonetic))
        assertEquals("क्त्", type("क्", "त", phonetic))
    }

    @Test
    fun phoneticInherentAStripsVirama() {
        assertEquals("क", type("क्", "अ", phonetic))
        assertEquals("अ", type("", "अ", phonetic))
    }

    @Test
    fun phoneticVowelReplacesVirama() {
        assertEquals("का", type("क्", "आ", phonetic))
        assertEquals("का", type("क्", "ा", phonetic))
        assertEquals("कि", type("क्", "इ", phonetic))
    }

    @Test
    fun phoneticVowelAfterLiveConsonant() {
        assertEquals("का", type("क", "आ", phonetic))
    }

    @Test
    fun phoneticViramaNoOpWhenDead() {
        val edit = insert("क्", "्", phonetic)!!
        assertEquals(0, edit.deleteCodePoints)
        assertEquals("", edit.insert)
        assertEquals("क्", apply("क्", edit))
    }

    @Test
    fun phoneticNuktaGoesBeforeVirama() {
        assertEquals("क़्", type("क्", "़", phonetic))
    }

    @Test
    fun phoneticDecomposedNuktaConsonantGetsVirama() {
        // popup keys send क + ़, not the precomposed क़
        assertEquals("क़्", type("", "क़", phonetic))
        assertEquals("फ़्", type("", "फ़", phonetic))
        assertEquals("ज़्", type("", "ज़", phonetic))
        assertEquals("ड़्", type("", "ड़", phonetic))
        assertEquals("ख़्", type("", "ख़", phonetic))
        assertEquals("ग़्", type("", "ग़", phonetic))
        assertEquals("य़्", type("", "य़", phonetic))
        assertEquals("ऱ्", type("", "ऱ", phonetic))
        assertEquals("ऩ्", type("", "ऩ", phonetic))
        assertEquals("क्क़्", type("क्", "क़", phonetic))
        assertEquals("कक़", type("क", "क़", contextual))
    }

    @Test
    fun phoneticAyogavahaDefaultKeepsVirama() {
        assertNull(insert("क्", "ं", phonetic))
        assertEquals("क्ं", type("क्", "ं", phonetic))
    }

    @Test
    fun phoneticAyogavahaStripVirama() {
        assertEquals("कं", type("क्", "ं", phoneticStripAyogavaha))
        assertEquals("कः", type("क्", "ः", phoneticStripAyogavaha))
    }

    @Test
    fun phoneticDeleteMatraLeavesDead() {
        assertEquals("क्", apply("का", delete("का", phonetic)!!))
        assertEquals("क्", apply("कि", delete("कि", phonetic)!!))
        assertEquals("क्", apply("को", delete("को", phonetic)!!))
        assertEquals("क़्", apply("क़ा", delete("क़ा", phonetic)!!))
    }

    @Test
    fun phoneticDeleteFreestandingMatraDoesNotAddVirama() {
        assertEquals("", apply("ा", delete("ा", phonetic)!!))
        assertEquals("", apply("ि", delete("ि", phonetic)!!))
        assertEquals("आ", apply("आा", delete("आा", phonetic)!!))
        assertEquals("।", apply("।ा", delete("।ा", phonetic)!!))
        val nfdLoneO = "े" + "ा"
        assertEquals("", apply(nfdLoneO, delete(nfdLoneO, phonetic)!!))
    }

    @Test
    fun phoneticDeleteNfdOMatra() {
        val nfd = "क" + "े" + "ा"
        assertEquals("क्", apply(nfd, delete(nfd, phonetic)!!))
        assertEquals("क्", apply("को", delete("को", phonetic)!!))
    }

    @Test
    fun phoneticDeleteNfdIndependentO() {
        val nfd = "ए" + "ा"
        assertEquals("", apply(nfd, delete(nfd, phonetic)!!))
        assertEquals("", apply("ओ", delete("ओ", phonetic)!!))
    }

    @Test
    fun phoneticDeleteInherentAAddsVirama() {
        assertEquals("क्", apply("क", delete("क", phonetic)!!))
    }

    @Test
    fun phoneticDeleteDeadConsonant() {
        assertEquals("", apply("क्", delete("क्", phonetic)!!))
        assertEquals("क्", apply("क्त्", delete("क्त्", phonetic)!!))
    }

    @Test
    fun phoneticDeleteIndependentVowel() {
        assertEquals("", apply("अ", delete("अ", phonetic)!!))
        assertEquals("", apply("आ", delete("आ", phonetic)!!))
    }

    @Test
    fun phoneticDeleteNuktaPartOfConsonant() {
        assertEquals("क़्", apply("क़", delete("क़", phonetic)!!))
        assertEquals("", apply("क़्", delete("क़्", phonetic)!!))
    }

    @Test
    fun phoneticDeleteNuktaSeparate() {
        assertNull(delete("क़", phoneticNuktaSeparate))
        assertEquals("क्", apply("क", delete("क", phoneticNuktaSeparate)!!))
    }

    @Test
    fun phoneticNfcNuktaLetterIsConsonant() {
        assertEquals("क़्", type("", "क़", phonetic))
        assertEquals("क़्", apply("क़", delete("क़", phonetic)!!))
    }

    @Test
    fun phoneticNfcOAndNfdEaAreSameVowel() {
        assertEquals("को", type("क्", "ो", phonetic))
        assertEquals("केआ", type("के", "ा", phonetic))
        assertEquals("क्", apply("केा", delete("केा", phonetic)!!))
        assertEquals("क्", apply("को", delete("को", phonetic)!!))
    }

    @Test
    fun multiTextAlreadyEndingWithViramaDoesNotAppendAnother() {
        assertNull(insert("", "क्", phonetic))
        assertEquals("क्", type("", "क्", phonetic))
    }

    @Test
    fun nonDevanagariPassThrough() {
        assertNull(insert("", "a", phonetic))
        assertNull(insert("hello", "b", contextual))
        assertNull(delete("hello", phonetic))
    }

    @Test
    fun pairedVowelsAreTypedAsIs() {
        // both keys exist, so the keyboard decides the form and we must not second-guess it
        assertNull(insert("क", "आ", contextualPairedAa))
        assertEquals("कआ", type("क", "आ", contextualPairedAa))
        assertNull(insert("", "ा", contextualPairedAa))
        assertEquals("ा", type("", "ा", contextualPairedAa))
    }

    @Test
    fun pairedVowelsStillReplaceViramaInPhonetic() {
        assertEquals("का", type("क्", "ा", phoneticPairedAa))
        assertEquals("क्आ", type("क्", "आ", phoneticPairedAa))
    }

    @Test
    fun unpairedVowelsKeepBeingRewritten() {
        // ि has no key of its own in this layout, so इ after a consonant still becomes a matra
        assertEquals("कि", type("क", "इ", contextualPairedAa))
    }

    @Test
    fun marksAttachedToAVowelFollowItIn() {
        // आं is the आ key with an anusvara on it, so the vowel converts and the mark comes along
        assertEquals("कां", type("क", "आं", contextual))
        assertEquals("आं", type("", "आं", contextual))
        assertEquals("कां", type("क्", "आं", phonetic))
        // and a matra key with a mark still needs no rewrite where it already fits
        assertEquals("कां", type("क", "ां", contextual))
        // paired vowels are handed over in their final form, marks included
        assertEquals("कआं", type("क", "आं", contextualPairedAa))
    }

    @Test
    fun aVowelFollowingOtherLettersIsPartOfItsSyllable() {
        // the matra here belongs to क्ष, it is not the key choosing between the two vowel forms
        assertEquals("क्षा", type("", "क्षा", contextual))
        assertEquals("कक्षा", type("क", "क्षा", contextual))
    }

    @Test
    fun dependentLabelsAfterLiveOrDeadPhonetic() {
        assertEquals(true, BrahmicInput.wantsDependentVowelLabels("क", contextual))
        assertEquals(false, BrahmicInput.wantsDependentVowelLabels("क्", contextual))
        assertEquals(true, BrahmicInput.wantsDependentVowelLabels("क्", phonetic))
        assertEquals(false, BrahmicInput.wantsDependentVowelLabels("", contextual))
        assertEquals(false, BrahmicInput.wantsDependentVowelLabels("क", glyphic))
    }

    @Test
    fun bengaliContextualAndPhonetic() {
        assertEquals("কা", type("ক", "আ", contextual))
        assertEquals("ক্", type("", "ক", phonetic))
        assertEquals("কা", type("ক্", "আ", phonetic))
        assertEquals("ক্", apply("কা", delete("কা", phonetic)!!))
    }

    @Test
    fun tamilContextualAndPhonetic() {
        assertEquals("கா", type("க", "ஆ", contextual))
        assertEquals("க்", type("", "க", phonetic))
        assertEquals("கா", type("க்", "ஆ", phonetic))
        assertEquals("க்", apply("கா", delete("கா", phonetic)!!))
    }

    @Test
    fun malayalamKannadaRoundTrip() {
        assertEquals("കാ", type("ക", "ആ", contextual))
        assertEquals("ಕಾ", type("ಕ", "ಆ", contextual))
        assertEquals("ക്", type("", "ക", phonetic))
        assertEquals("ಕ್", type("", "ಕ", phonetic))
    }

    @Test
    fun granthaAndTigalariPhonetic() {
        val ka = Character.toString(0x11315) // GRANTHA LETTER KA
        val aa = Character.toString(0x11306) // GRANTHA LETTER AA
        val virama = Character.toString(0x1134D)
        assertEquals(ka + Character.toString(0x1133E), type(ka, aa, contextual))
        assertEquals(ka + virama, type("", ka, phonetic))

        val tKa = Character.toString(0x11392) // TULU-TIGALARI LETTER KA
        val tAa = Character.toString(0x11381)
        val tVirama = Character.toString(0x113CE)
        assertEquals(tKa + Character.toString(0x113B8), type(tKa, tAa, contextual))
        assertEquals(tKa + tVirama, type("", tKa, phonetic))
    }

    @Test
    fun traditionalAytamStripsViramaAndKeepsFollowingVowelIndependent() {
        assertEquals("கஃ", type("க்", "ஃ", phoneticStripAyogavaha))
        assertEquals("கஃஉ", type("கஃ", "உ", contextual))
        assertNull(insert("கஃ", "உ", contextual))
    }

    @Test
    fun modernAytamBeforeConsonantStillGetsPulli() {
        assertEquals("ஃப்", type("ஃ", "ப", phonetic))
    }

    @Test
    fun vowelAfterVedicCantillationStaysIndependent() {
        assertEquals("क॑आ", type("क॑", "आ", contextual))
        assertNull(insert("क॑", "आ", contextual))
        assertEquals("क\u1CD0आ", type("क\u1CD0", "आ", contextual))
    }

    @Test
    fun mixedScriptContextDoesNotDriveTheVowel() {
        assertEquals("கआ", type("க", "आ", contextual))
        assertNull(insert("க", "आ", contextual))
    }

    @Test
    fun twoPartBengaliMatraComposesForBackspace() {
        assertEquals("কো", type("ক", "ো", contextual))
        assertEquals("ক্", apply("কো", delete("কো", phonetic)!!))
    }
}
