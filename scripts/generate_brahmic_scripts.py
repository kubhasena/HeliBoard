#!/usr/bin/env python3
"""One-shot generator for BrahmicScripts.kt and trimmed UCD excerpts.

The Kotlin unit test re-derives the same data from the excerpts and asserts
equality, so this script is not part of the build.
"""
from __future__ import annotations

import os
import re
from collections import defaultdict
from pathlib import Path

BLOCKS = [
    (0x0900, 0x097F),
    (0x0980, 0x09FF),
    (0x0A00, 0x0A7F),
    (0x0A80, 0x0AFF),
    (0x0B00, 0x0B7F),
    (0x0B80, 0x0BFF),
    (0x0C00, 0x0C7F),
    (0x0C80, 0x0CFF),
    (0x0D00, 0x0D7F),
    (0x1CD0, 0x1CFF),
    (0xA8E0, 0xA8FF),
    (0x11000, 0x1107F),
    (0x11180, 0x111DF),
    (0x11300, 0x1137F),
    (0x11380, 0x113FF),
    (0x11680, 0x116CF),
    (0x11800, 0x1184F),
    (0x11B00, 0x11B5F),
    (0x11B60, 0x11B7F),
]

# script-specific blocks only; Vedic Extensions are Inherited and must not resolve
SCRIPTS = [
    ("Devanagari", [(0x0900, 0x097F), (0xA8E0, 0xA8FF), (0x11B00, 0x11B5F)]),
    ("Bengali", [(0x0980, 0x09FF)]),
    ("Gurmukhi", [(0x0A00, 0x0A7F)]),
    ("Gujarati", [(0x0A80, 0x0AFF)]),
    ("Oriya", [(0x0B00, 0x0B7F)]),
    ("Tamil", [(0x0B80, 0x0BFF)]),
    ("Telugu", [(0x0C00, 0x0C7F)]),
    ("Kannada", [(0x0C80, 0x0CFF)]),
    ("Malayalam", [(0x0D00, 0x0D7F)]),
    ("Brahmi", [(0x11000, 0x1107F)]),
    ("Sharada", [(0x11180, 0x111DF), (0x11B60, 0x11B7F)]),
    ("Grantha", [(0x11300, 0x1137F)]),
    ("Tulu-Tigalari", [(0x11380, 0x113FF)]),
    ("Takri", [(0x11680, 0x116CF)]),
    ("Dogra", [(0x11800, 0x1184F)]),
]

CONSONANT_CATS = {
    "Consonant",
    "Consonant_Dead",
    "Consonant_Placeholder",
    "Consonant_With_Stacker",
    "Consonant_Preceding_Repha",
    "Consonant_Succeeding_Repha",
    "Consonant_Subjoined",
    "Consonant_Head_Letter",
}
VIRAMA_CATS = {"Virama", "Pure_Killer", "Invisible_Stacker"}
BINDU_CATS = {"Bindu", "Visarga"}

# Devanagari visual two-part sequences that have no canonical decomposition
DEVANAGARI_TWO_PART = [
    (0x0947, 0x093E, 0x094B),  # े ा -> ो
    (0x0948, 0x093E, 0x094C),  # ै ा -> ौ
    (0x090F, 0x093E, 0x0913),  # ए ा -> ओ
    (0x0910, 0x093E, 0x0914),  # ऐ ा -> औ
]

# Extended Tamil: unassigned parallel slots treated as the vowels/consonants that would be there
TAMIL_ADDENDUM_FAMILIES = [
    (0x0B8B, 0x0BC3),  # vocalic R
    (0x0BE0, 0x0BC4),  # vocalic RR
    (0x0B8C, 0x0BE2),  # vocalic L
    (0x0BE1, 0x0BE3),  # vocalic LL
    (0x0B8D, 0x0BC5),  # candra E
    (0x0B91, 0x0BC9),  # candra O
]
TAMIL_ADDENDUM_INDEPENDENT = [ind for ind, _ in TAMIL_ADDENDUM_FAMILIES]
TAMIL_ADDENDUM_DEPENDENT = [dep for _, dep in TAMIL_ADDENDUM_FAMILIES]
TAMIL_ADDENDUM_CONSONANTS = (
    list(range(0x0B96, 0x0B99))
    + [0x0B9B, 0x0B9D]
    + list(range(0x0BA0, 0x0BA3))
    + list(range(0x0BA5, 0x0BA8))
    + list(range(0x0BAB, 0x0BAE))
)
TAMIL_ADDENDUM_BINDU = [0x0B81]  # unassigned anunasika slot
TAMIL_AYTAM = 0x0B83  # Modifying_Letter, treated as visarga


def in_blocks(cp: int) -> bool:
    return any(lo <= cp <= hi for lo, hi in BLOCKS)


def script_of(cp: int) -> str | None:
    for name, ranges in SCRIPTS:
        if any(lo <= cp <= hi for lo, hi in ranges):
            return name
    return None


def parse_range(field: str) -> list[int]:
    field = field.strip()
    if ".." in field:
        a, b = field.split("..")
        return list(range(int(a, 16), int(b, 16) + 1))
    return [int(field, 16)]


def hexcp(cp: int) -> str:
    return f"0x{cp:04X}" if cp <= 0xFFFF else f"0x{cp:05X}"


def ints_literal(cps: list[int], indent: str = "            ") -> str:
    if not cps:
        return "intArrayOf()"
    parts = [hexcp(c) for c in cps]
    lines = []
    row = []
    for p in parts:
        row.append(p)
        if len(row) == 8:
            lines.append(indent + ", ".join(row) + ",")
            row = []
    if row:
        lines.append(indent + ", ".join(row) + ",")
    body = "\n".join(lines)
    return "intArrayOf(\n" + body + "\n        )"


def ranges_literal(ranges: list[tuple[int, int]]) -> str:
    flat = []
    for lo, hi in ranges:
        flat.extend([lo, hi])
    return "intArrayOf(" + ", ".join(hexcp(x) for x in flat) + ")"


def main() -> None:
    repo = Path(__file__).resolve().parents[1]
    src_ud = Path(os.environ.get("TEMP", "/tmp")) / "UnicodeData.txt"
    src_isc = Path(os.environ.get("TEMP", "/tmp")) / "IndicSyllabicCategory.txt"
    out_dir = repo / "app" / "src" / "test" / "resources" / "unicode"
    out_dir.mkdir(parents=True, exist_ok=True)

    names: dict[int, str] = {}
    decomps: dict[int, list[int]] = {}
    ud_lines = [
        "# Excerpt of UnicodeData.txt covering Indian Brahmic blocks used by BrahmicScriptDataTest.\n",
        "# © Unicode, Inc. For terms of use, see https://www.unicode.org/terms_of_use.html\n",
    ]
    with src_ud.open(encoding="utf-8") as f:
        header_note = True
        for line in f:
            if not line.strip():
                continue
            fields = line.rstrip("\n").split(";")
            cp = int(fields[0], 16)
            if not in_blocks(cp):
                continue
            names[cp] = fields[1]
            if fields[5] and not fields[5].startswith("<"):
                decomps[cp] = [int(x, 16) for x in fields[5].split()]
            ud_lines.append(line)
    (out_dir / "UnicodeData-indic.txt").write_text("".join(ud_lines), encoding="utf-8")

    cats: dict[int, str] = {}
    isc_out = []
    with src_isc.open(encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                if line.startswith("#") and (
                    "Copyright" in line
                    or "Unicode" in line
                    or line.startswith("# Indic")
                    or line.startswith("# Date")
                    or line.startswith("# File")
                    or "terms of use" in line.lower()
                    or line.startswith("# ================================================")
                    or line.startswith("# Property:")
                    or line.startswith("# @missing")
                    or line.startswith("# Format")
                    or "Unicode Character Database" in line
                    or "For documentation" in line
                    or line.startswith("#\n")
                    or line.startswith("# \n")
                    or "All rights reserved" in line
                    or "Clause 1" in line
                    or line.startswith("# Indic_Syllabic")
                ):
                    isc_out.append(line)
                continue
            field, rest = line.split(";", 1)
            cat = rest.split("#", 1)[0].strip()
            kept = []
            for cp in parse_range(field):
                if in_blocks(cp):
                    cats[cp] = cat
                    kept.append(cp)
            if not kept:
                continue
            isc_out.append(line if len(kept) == len(parse_range(field)) else line)
    # rewrite ISC keeping only in-block code points, preserving comments at top
    isc_filtered = []
    with src_isc.open(encoding="utf-8") as f:
        in_header = True
        for line in f:
            if in_header:
                isc_filtered.append(line)
                if line.startswith("# @missing") or (line.startswith("#") and "No_Block" in line):
                    continue
                if line.startswith("#") or not line.strip():
                    if line.startswith("# ================================================"):
                        pass
                    continue
                else:
                    in_header = False
            if line.startswith("#") or not line.strip():
                if line.startswith("# Indic_Syllabic_Category="):
                    isc_filtered.append(line)
                continue
            field, rest = line.split(";", 1)
            cat = rest.split("#", 1)[0].strip()
            kept = [cp for cp in parse_range(field) if in_blocks(cp)]
            if not kept:
                continue
            comment = ""
            if "#" in rest:
                comment = " #" + rest.split("#", 1)[1].rstrip("\n")
            if len(kept) == 1:
                isc_filtered.append(f"{kept[0]:04X} ; {cat}{comment}\n" if kept[0] <= 0xFFFF else f"{kept[0]:05X} ; {cat}{comment}\n")
            else:
                a, b = kept[0], kept[-1]
                # collapse only if contiguous original range fully kept as one span
                spans = []
                start = prev = kept[0]
                for cp in kept[1:]:
                    if cp == prev + 1:
                        prev = cp
                    else:
                        spans.append((start, prev))
                        start = prev = cp
                spans.append((start, prev))
                for a, b in spans:
                    if a == b:
                        fmt = f"{a:04X}" if a <= 0xFFFF else f"{a:05X}"
                        isc_filtered.append(f"{fmt} ; {cat}{comment}\n")
                    else:
                        fa = f"{a:04X}" if a <= 0xFFFF else f"{a:05X}"
                        fb = f"{b:04X}" if b <= 0xFFFF else f"{b:05X}"
                        isc_filtered.append(f"{fa}..{fb} ; {cat}{comment}\n")
    (out_dir / "IndicSyllabicCategory-indic.txt").write_text("".join(isc_filtered), encoding="utf-8")

    by_script: dict[str, dict[str, list[int]]] = {
        name: defaultdict(list) for name, _ in SCRIPTS
    }
    for cp, cat in cats.items():
        s = script_of(cp)
        if s is None:
            continue
        by_script[s][cat].append(cp)

    def collect(s: str, *wanted: str) -> list[int]:
        out = []
        for cat in wanted:
            out.extend(by_script[s].get(cat, []))
        return sorted(set(out))

    # families by joining LETTER <V> to VOWEL SIGN <V>
    letter_re = re.compile(r"^([A-Z0-9 -]+) LETTER (.+)$")
    sign_re = re.compile(r"^([A-Z0-9 -]+) VOWEL SIGN (.+)$")
    # also AU LENGTH MARK etc. are vowel dependent but not families

    letters: dict[str, dict[str, int]] = defaultdict(dict)  # script -> vowel name -> cp
    signs: dict[str, dict[str, int]] = defaultdict(dict)
    for cp, name in names.items():
        s = script_of(cp)
        if s is None:
            continue
        m = letter_re.match(name)
        if m and cats.get(cp) == "Vowel_Independent":
            letters[s][m.group(2)] = cp
            continue
        m = sign_re.match(name)
        if m and cats.get(cp) == "Vowel_Dependent":
            signs[s][m.group(2)] = cp

    families: dict[str, list[tuple[int, int]]] = {}
    for s, _ in SCRIPTS:
        pairs = []
        for vname, ind in letters[s].items():
            dep = signs[s].get(vname)
            if dep is not None:
                pairs.append((ind, dep))
        pairs.sort()
        families[s] = pairs

    two_part: dict[str, list[tuple[int, int, int]]] = defaultdict(list)
    for cp, parts in decomps.items():
        if len(parts) != 2:
            continue
        s = script_of(cp)
        if s is None:
            continue
        if cats.get(cp) not in ("Vowel_Dependent", "Vowel_Independent"):
            continue
        two_part[s].append((parts[0], parts[1], cp))
    two_part["Devanagari"].extend(DEVANAGARI_TWO_PART)
    # unique
    for s in list(two_part):
        seen = set()
        uniq = []
        for t in two_part[s]:
            if t not in seen:
                seen.add(t)
                uniq.append(t)
        two_part[s] = uniq

    def virama_to_insert(s: str, viramas: list[int]) -> int:
        if not viramas:
            return -1
        for cp in viramas:
            name = names.get(cp, "")
            if name.endswith("SIGN VIRAMA") and "LOOPED" not in name and "CONJOINER" not in name:
                return cp
        # Brahmi uses SIGN VIRAMA too; fallback first virama category
        for cp in viramas:
            if cats.get(cp) == "Virama":
                return cp
        return viramas[0]

    def nukta_of(s: str) -> int:
        nuktas = by_script[s].get("Nukta", [])
        return nuktas[0] if nuktas else -1

    def inherent_of(s: str) -> int:
        for cp, name in names.items():
            if script_of(cp) != s:
                continue
            if cats.get(cp) != "Vowel_Independent":
                continue
            if name.endswith(" LETTER A"):
                return cp
        return -1

    # apply Tamil addenda
    tamil_indep = collect("Tamil", "Vowel_Independent") + TAMIL_ADDENDUM_INDEPENDENT
    tamil_dep = collect("Tamil", "Vowel_Dependent") + TAMIL_ADDENDUM_DEPENDENT
    tamil_cons = collect("Tamil", *CONSONANT_CATS) + TAMIL_ADDENDUM_CONSONANTS
    tamil_bindu = collect("Tamil", *BINDU_CATS) + TAMIL_ADDENDUM_BINDU + [TAMIL_AYTAM]
    tamil_fams = families["Tamil"] + TAMIL_ADDENDUM_FAMILIES

    kt = []
    kt.append("// SPDX-License-Identifier: GPL-3.0-only")
    kt.append("package vidyullekha.keyboard.latin.brahmic")
    kt.append("")
    kt.append("/**")
    kt.append(" * Per-script Brahmic classification, derived from Unicode Indic_Syllabic_Category")
    kt.append(" * and character names. Vedic Extensions are Inherited and do not resolve here.")
    kt.append(" */")
    kt.append("class BrahmicScript(")
    kt.append("    val name: String,")
    kt.append("    internal val blocks: IntArray,")
    kt.append("    val viramaToInsert: Int,")
    kt.append("    internal val viramas: IntArray,")
    kt.append("    val nukta: Int,")
    kt.append("    val inherentVowel: Int,")
    kt.append("    internal val binduVisarga: IntArray,")
    kt.append("    internal val consonants: IntArray,")
    kt.append("    internal val independentVowels: IntArray,")
    kt.append("    internal val dependentVowels: IntArray,")
    kt.append("    internal val families: IntArray,")
    kt.append("    internal val twoPartVowels: IntArray,")
    kt.append(") {")
    kt.append("    private val independentToDependent = HashMap<Int, Int>()")
    kt.append("    private val dependentToIndependent = HashMap<Int, Int>()")
    kt.append("    private val twoPartComposed = HashMap<Long, Int>()")
    kt.append("")
    kt.append("    init {")
    kt.append("        var i = 0")
    kt.append("        while (i < families.size) {")
    kt.append("            independentToDependent[families[i]] = families[i + 1]")
    kt.append("            dependentToIndependent[families[i + 1]] = families[i]")
    kt.append("            i += 2")
    kt.append("        }")
    kt.append("        i = 0")
    kt.append("        while (i < twoPartVowels.size) {")
    kt.append("            val key = twoPartKey(twoPartVowels[i], twoPartVowels[i + 1])")
    kt.append("            twoPartComposed[key] = twoPartVowels[i + 2]")
    kt.append("            i += 3")
    kt.append("        }")
    kt.append("    }")
    kt.append("")
    kt.append("    fun contains(codePoint: Int): Boolean {")
    kt.append("        var i = 0")
    kt.append("        while (i < blocks.size) {")
    kt.append("            if (codePoint in blocks[i]..blocks[i + 1]) return true")
    kt.append("            i += 2")
    kt.append("        }")
    kt.append("        return false")
    kt.append("    }")
    kt.append("")
    kt.append("    fun isVirama(codePoint: Int) = codePoint in viramas")
    kt.append("    fun isNukta(codePoint: Int) = nukta >= 0 && codePoint == nukta")
    kt.append("    fun isAyogavaha(codePoint: Int) = codePoint in binduVisarga")
    kt.append("    fun isConsonant(codePoint: Int) = codePoint in consonants")
    kt.append("    fun isIndependentVowel(codePoint: Int) = codePoint in independentVowels")
    kt.append("    fun isDependentVowel(codePoint: Int) = codePoint in dependentVowels")
    kt.append("    fun isVowel(codePoint: Int) = isIndependentVowel(codePoint) || isDependentVowel(codePoint)")
    kt.append("    fun isInherentA(codePoint: Int) = inherentVowel >= 0 && codePoint == inherentVowel")
    kt.append("    fun hasNukta() = nukta >= 0")
    kt.append("    fun hasVirama() = viramaToInsert >= 0")
    kt.append("")
    kt.append("    fun toDependent(codePoint: Int): Int? {")
    kt.append("        if (isDependentVowel(codePoint)) return codePoint")
    kt.append("        return independentToDependent[codePoint]")
    kt.append("    }")
    kt.append("")
    kt.append("    fun toIndependent(codePoint: Int): Int? {")
    kt.append("        if (isIndependentVowel(codePoint)) return codePoint")
    kt.append("        return dependentToIndependent[codePoint]")
    kt.append("    }")
    kt.append("")
    kt.append("    fun familyOf(codePoint: Int): Pair<Int, Int>? {")
    kt.append("        val independent = independentToDependent[codePoint]")
    kt.append("        if (independent != null) return codePoint to independent")
    kt.append("        val dependent = dependentToIndependent[codePoint]")
    kt.append("        if (dependent != null) return dependent to codePoint")
    kt.append("        return null")
    kt.append("    }")
    kt.append("")
    kt.append("    fun nfcTwoPart(first: Int, second: Int): Int? =")
    kt.append("        twoPartComposed[twoPartKey(first, second)]")
    kt.append("")
    kt.append("    fun twoPartDependentLength(cps: IntArray, lastIndex: Int): Int {")
    kt.append("        if (lastIndex < 1) return 1")
    kt.append("        val composed = nfcTwoPart(cps[lastIndex - 1], cps[lastIndex]) ?: return 1")
    kt.append("        return if (isDependentVowel(composed)) 2 else 1")
    kt.append("    }")
    kt.append("")
    kt.append("    fun isIndependentTwoPart(first: Int, second: Int): Boolean {")
    kt.append("        val composed = nfcTwoPart(first, second) ?: return false")
    kt.append("        return isIndependentVowel(composed)")
    kt.append("    }")
    kt.append("")
    kt.append("    fun classificationSets(): List<IntArray> = listOf(")
    kt.append("        independentVowels, dependentVowels, consonants, viramas,")
    kt.append("        if (nukta >= 0) intArrayOf(nukta) else intArrayOf(),")
    kt.append("        binduVisarga,")
    kt.append("    )")
    kt.append("")
    kt.append("    companion object {")
    kt.append("        private fun twoPartKey(first: Int, second: Int) =")
    kt.append("            (first.toLong() shl 32) or (second.toLong() and 0xFFFFFFFFL)")
    kt.append("    }")
    kt.append("}")
    kt.append("")
    kt.append("object BrahmicScripts {")
    kt.append("    const val ZWNJ = 0x200C")
    kt.append("    const val ZWJ = 0x200D")
    kt.append("")
    kt.append("    fun isJoinControl(codePoint: Int) = codePoint == ZWJ || codePoint == ZWNJ")
    kt.append("")

    script_vals = []
    for s, ranges in SCRIPTS:
        viramas = collect(s, *VIRAMA_CATS)
        nuktas = nukta_of(s)
        inherent = inherent_of(s)
        bindu = collect(s, *BINDU_CATS)
        cons = collect(s, *CONSONANT_CATS)
        indep = collect(s, "Vowel_Independent")
        dep = collect(s, "Vowel_Dependent")
        fams = families[s]
        twos = two_part.get(s, [])

        if s == "Tamil":
            bindu = sorted(set(tamil_bindu))
            cons = sorted(set(tamil_cons))
            indep = sorted(set(tamil_indep))
            dep = sorted(set(tamil_dep))
            fams = tamil_fams

        ident = s.replace("-", "")
        script_vals.append(ident)

        if s == "Tamil":
            kt.append("    // Extended Tamil: unassigned parallel slots treated as the vowels and")
            kt.append("    // consonants that would be there (L2/10-256R). 0B83 aytam is visarga.")
            kt.append("    // 0B81 is the unassigned anunasika slot.")

        def fam_array(pairs):
            flat = []
            for a, b in pairs:
                flat.extend([a, b])
            return flat

        def two_array(triples):
            flat = []
            for a, b, c in triples:
                flat.extend([a, b, c])
            return flat

        kt.append(f"    val {ident} = BrahmicScript(")
        kt.append(f"        name = \"{s}\",")
        kt.append(f"        blocks = {ranges_literal(ranges)},")
        kt.append(f"        viramaToInsert = {hexcp(virama_to_insert(s, viramas)) if viramas else '-1'},")
        kt.append(f"        viramas = {ints_literal(viramas)},")
        kt.append(f"        nukta = {hexcp(nuktas) if nuktas >= 0 else '-1'},")
        kt.append(f"        inherentVowel = {hexcp(inherent) if inherent >= 0 else '-1'},")
        kt.append(f"        binduVisarga = {ints_literal(bindu)},")
        kt.append(f"        consonants = {ints_literal(cons)},")
        kt.append(f"        independentVowels = {ints_literal(indep)},")
        kt.append(f"        dependentVowels = {ints_literal(dep)},")
        kt.append(f"        families = {ints_literal(fam_array(fams))},")
        kt.append(f"        twoPartVowels = {ints_literal(two_array(twos))},")
        kt.append("    )")
        kt.append("")

    kt.append("    internal val all = arrayOf(")
    kt.append("        " + ", ".join(script_vals))
    kt.append("    )")
    kt.append("")
    kt.append("    /** Script owning [codePoint], or null for Vedic/Inherited/unknown. */")
    kt.append("    @JvmStatic")
    kt.append("    fun forCodePoint(codePoint: Int): BrahmicScript? {")
    kt.append("        for (script in all) if (script.contains(codePoint)) return script")
    kt.append("        return null")
    kt.append("    }")
    kt.append("")
    kt.append("    fun isVowel(codePoint: Int) = forCodePoint(codePoint)?.isVowel(codePoint) == true")
    kt.append("")
    kt.append("    /**")
    kt.append("     * The vowel a key label stands for. Marks may ride along after it, as in आं or াঁ.")
    kt.append("     * A decomposed two-part matra such as ে + া counts as one vowel.")
    kt.append("     *")
    kt.append("     * @return the (composed, if two-part) code point, or -1")
    kt.append("     */")
    kt.append("    fun leadingVowel(label: String): Int {")
    kt.append("        if (label.isEmpty()) return -1")
    kt.append("        val first = label.codePointAt(0)")
    kt.append("        val script = forCodePoint(first) ?: return -1")
    kt.append("        if (!script.isVowel(first)) return -1")
    kt.append("        var i = Character.charCount(first)")
    kt.append("        if (i < label.length) {")
    kt.append("            val second = label.codePointAt(i)")
    kt.append("            val composed = script.nfcTwoPart(first, second)")
    kt.append("            if (composed != null && script.isVowel(composed)) {")
    kt.append("                i += Character.charCount(second)")
    kt.append("                while (i < label.length) {")
    kt.append("                    val cp = label.codePointAt(i)")
    kt.append("                    if (isVowel(cp)) return -1")
    kt.append("                    i += Character.charCount(cp)")
    kt.append("                }")
    kt.append("                return composed")
    kt.append("            }")
    kt.append("        }")
    kt.append("        while (i < label.length) {")
    kt.append("            val cp = label.codePointAt(i)")
    kt.append("            if (isVowel(cp)) return -1")
    kt.append("            i += Character.charCount(cp)")
    kt.append("        }")
    kt.append("        return first")
    kt.append("    }")
    kt.append("")
    kt.append("    fun leadingVowelLength(label: String): Int {")
    kt.append("        if (label.isEmpty()) return 0")
    kt.append("        val first = label.codePointAt(0)")
    kt.append("        val script = forCodePoint(first) ?: return 0")
    kt.append("        if (!script.isVowel(first)) return 0")
    kt.append("        val firstLen = Character.charCount(first)")
    kt.append("        if (firstLen < label.length) {")
    kt.append("            val second = label.codePointAt(firstLen)")
    kt.append("            val composed = script.nfcTwoPart(first, second)")
    kt.append("            if (composed != null && script.isVowel(composed)) {")
    kt.append("                return firstLen + Character.charCount(second)")
    kt.append("            }")
    kt.append("        }")
    kt.append("        return firstLen")
    kt.append("    }")
    kt.append("}")
    kt.append("")

    out_kt = repo / "app" / "src" / "main" / "java" / "vidyullekha" / "keyboard" / "latin" / "brahmic" / "BrahmicScripts.kt"
    out_kt.write_text("\n".join(kt), encoding="utf-8")

    # dump a small summary for sanity
    print("Wrote", out_kt)
    print("UnicodeData lines", len(ud_lines))
    print("ISC lines", len(isc_filtered))
    for s, _ in SCRIPTS:
        fams = tamil_fams if s == "Tamil" else families[s]
        print(f"  {s}: families={len(fams)} viramas={collect(s, *VIRAMA_CATS)} nukta={nukta_of(s)} A={inherent_of(s)}")


if __name__ == "__main__":
    main()
