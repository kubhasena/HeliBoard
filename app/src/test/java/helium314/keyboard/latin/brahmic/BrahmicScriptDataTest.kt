// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.brahmic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class BrahmicScriptDataTest {
    @Test
    fun checkedInTableMatchesUcdPlusAddenda() {
        val ucd = UcdIndic.load()
        for (script in BrahmicScripts.all) {
            val expected = ucd.derive(script.name, script.blocks)
            assertEquals(expected.independent.toSet(), script.independentVowels.toSet(), "${script.name} independent")
            assertEquals(expected.dependent.toSet(), script.dependentVowels.toSet(), "${script.name} dependent")
            assertEquals(expected.consonants.toSet(), script.consonants.toSet(), "${script.name} consonants")
            assertEquals(expected.viramas.toSet(), script.viramas.toSet(), "${script.name} viramas")
            assertEquals(expected.nukta, script.nukta, "${script.name} nukta")
            assertEquals(expected.inherent, script.inherentVowel, "${script.name} inherent")
            assertEquals(expected.bindu.toSet(), script.binduVisarga.toSet(), "${script.name} bindu/visarga")
            assertEquals(expected.families, pairsOf(script.families), "${script.name} families")
            assertEquals(expected.twoPart.toSet(), triplesOf(script.twoPartVowels).toSet(), "${script.name} two-part")
            assertEquals(expected.viramaToInsert, script.viramaToInsert, "${script.name} viramaToInsert")
        }
    }

    @Test
    fun classificationSetsAreDisjoint() {
        for (script in BrahmicScripts.all) {
            val seen = HashMap<Int, String>()
            fun add(cps: IntArray, label: String) {
                for (cp in cps) {
                    val prev = seen.put(cp, label)
                    if (prev != null) fail("${script.name}: ${cp.toString(16)} in $prev and $label")
                }
            }
            add(script.independentVowels, "independent")
            add(script.dependentVowels, "dependent")
            add(script.consonants, "consonant")
            add(script.viramas, "virama")
            if (script.nukta >= 0) add(intArrayOf(script.nukta), "nukta")
            add(script.binduVisarga, "bindu/visarga")
        }
    }

    @Test
    fun lengthMarksAreDependentWithoutAFamily() {
        val ucd = UcdIndic.load()
        val lengthMarks = ucd.names.filter { it.value.contains("LENGTH MARK") }.keys
        assertTrue(lengthMarks.isNotEmpty())
        for (cp in lengthMarks) {
            val script = BrahmicScripts.forCodePoint(cp) ?: continue
            assertTrue(script.isDependentVowel(cp), "${cp.toString(16)} should be a dependent vowel")
            assertEquals(null, script.familyOf(cp), "${cp.toString(16)} must not form a family")
        }
    }

    @Test
    fun vedicMarksDoNotResolveToAScript() {
        listOf(0x1CD0, 0x1CE0, 0x1CF4, 0x1CF8).forEach { cp ->
            assertEquals(null, BrahmicScripts.forCodePoint(cp), "${cp.toString(16)} should stay unresolved")
        }
    }

    private fun pairsOf(a: IntArray) = (a.indices step 2).map { a[it] to a[it + 1] }
    private fun triplesOf(a: IntArray) = (a.indices step 3).map { Triple(a[it], a[it + 1], a[it + 2]) }
}

private class Derived(
    val independent: List<Int>,
    val dependent: List<Int>,
    val consonants: List<Int>,
    val viramas: List<Int>,
    val nukta: Int,
    val inherent: Int,
    val bindu: List<Int>,
    val families: List<Pair<Int, Int>>,
    val twoPart: List<Triple<Int, Int, Int>>,
    val viramaToInsert: Int,
)

private class UcdIndic(
    val cats: Map<Int, String>,
    val names: Map<Int, String>,
    val decomps: Map<Int, List<Int>>,
) {
    private val consonantCats = setOf(
        "Consonant", "Consonant_Dead", "Consonant_Placeholder", "Consonant_With_Stacker",
        "Consonant_Preceding_Repha", "Consonant_Succeeding_Repha", "Consonant_Subjoined",
        "Consonant_Head_Letter",
    )
    private val viramaCats = setOf("Virama", "Pure_Killer", "Invisible_Stacker")
    private val binduCats = setOf("Bindu", "Visarga")
    private val tamilAddendumFamilies = listOf(
        0x0B8B to 0x0BC3, 0x0BE0 to 0x0BC4, 0x0B8C to 0x0BE2, 0x0BE1 to 0x0BE3,
        0x0B8D to 0x0BC5, 0x0B91 to 0x0BC9,
    )
    private val tamilAddendumConsonants = listOf(
        0x0B96, 0x0B97, 0x0B98, 0x0B9B, 0x0B9D,
        0x0BA0, 0x0BA1, 0x0BA2, 0x0BA5, 0x0BA6, 0x0BA7,
        0x0BAB, 0x0BAC, 0x0BAD,
    )
    private val tamilAytam = 0x0B83
    private val tamilAnunasika = 0x0B81
    private val devanagariVisualTwoPart = listOf(
        Triple(0x0947, 0x093E, 0x094B),
        Triple(0x0948, 0x093E, 0x094C),
        Triple(0x090F, 0x093E, 0x0913),
        Triple(0x0910, 0x093E, 0x0914),
    )

        fun derive(scriptName: String, blocks: IntArray): Derived {
            fun inScript(cp: Int): Boolean {
                var i = 0
                while (i < blocks.size) {
                    if (cp in blocks[i]..blocks[i + 1]) return true
                    i += 2
                }
                return false
            }
            fun collect(wanted: Set<String>) = cats.filter { (cp, cat) -> inScript(cp) && cat in wanted }.keys.sorted()

            var independent = collect(setOf("Vowel_Independent"))
            var dependent = collect(setOf("Vowel_Dependent"))
            var consonants = collect(consonantCats)
            val viramas = collect(viramaCats)
            val nuktas = collect(setOf("Nukta"))
            var bindu = collect(binduCats)
            val nukta = nuktas.firstOrNull() ?: -1
            val inherent = names.entries.firstOrNull { (cp, name) ->
                inScript(cp) && cats[cp] == "Vowel_Independent" && name.endsWith(" LETTER A")
            }?.key ?: -1

            val letterRe = Regex("^([A-Z0-9 -]+) LETTER (.+)$")
            val signRe = Regex("^([A-Z0-9 -]+) VOWEL SIGN (.+)$")
            val letters = HashMap<String, Int>()
            val signs = HashMap<String, Int>()
            for ((cp, name) in names) {
                if (!inScript(cp)) continue
                letterRe.matchEntire(name)?.let {
                    if (cats[cp] == "Vowel_Independent") letters[it.groupValues[2]] = cp
                }
                signRe.matchEntire(name)?.let {
                    if (cats[cp] == "Vowel_Dependent") signs[it.groupValues[2]] = cp
                }
            }
            var families = letters.mapNotNull { (v, ind) -> signs[v]?.let { ind to it } }.sortedBy { it.first }

            var twoPart = decomps.mapNotNull { (cp, parts) ->
                if (parts.size != 2 || !inScript(cp)) return@mapNotNull null
                if (cats[cp] != "Vowel_Dependent" && cats[cp] != "Vowel_Independent") return@mapNotNull null
                Triple(parts[0], parts[1], cp)
            }

            if (scriptName == "Devanagari") twoPart = twoPart + devanagariVisualTwoPart
            if (scriptName == "Tamil") {
                independent = (independent + tamilAddendumFamilies.map { it.first }).distinct().sorted()
                dependent = (dependent + tamilAddendumFamilies.map { it.second }).distinct().sorted()
                consonants = (consonants + tamilAddendumConsonants).distinct().sorted()
                bindu = (bindu + tamilAnunasika + tamilAytam).distinct().sorted()
                families = families + tamilAddendumFamilies
            }

            val viramaToInsert = viramas.firstOrNull { cp ->
                val n = names[cp] ?: return@firstOrNull false
                n.endsWith("SIGN VIRAMA") && "LOOPED" !in n && "CONJOINER" !in n
            } ?: viramas.firstOrNull { cats[it] == "Virama" } ?: viramas.firstOrNull() ?: -1

            return Derived(
                independent, dependent, consonants, viramas, nukta, inherent, bindu,
                families, twoPart, viramaToInsert,
            )
        }

        companion object {
            fun load(): UcdIndic {
                val cats = HashMap<Int, String>()
                for (line in read("IndicSyllabicCategory-indic.txt")) {
                    if (line.startsWith("#") || line.isBlank()) continue
                    val (field, rest) = line.split(";", limit = 2)
                    val cat = rest.substringBefore("#").trim()
                    for (cp in parseRange(field.trim())) cats[cp] = cat
                }
                val names = HashMap<Int, String>()
                val decomps = HashMap<Int, List<Int>>()
                for (line in read("UnicodeData-indic.txt")) {
                    if (line.startsWith("#") || line.isBlank()) continue
                    val f = line.split(";")
                    val cp = f[0].toInt(16)
                    names[cp] = f[1]
                    if (f[5].isNotEmpty() && !f[5].startsWith("<")) {
                        decomps[cp] = f[5].split(" ").map { it.toInt(16) }
                    }
                }
                return UcdIndic(cats, names, decomps)
            }

            private fun read(name: String): List<String> {
                val fromClasspath = BrahmicScriptDataTest::class.java.classLoader?.getResource("unicode/$name")
                val file = when {
                    fromClasspath != null -> File(fromClasspath.toURI())
                    else -> File("src/test/resources/unicode/$name")
                }
                return file.readLines()
            }

            private fun parseRange(field: String): List<Int> {
                return if (".." in field) {
                    val (a, b) = field.split("..")
                    (a.toInt(16)..b.toInt(16)).toList()
                } else listOf(field.toInt(16))
            }
        }
    }
