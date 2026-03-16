package me.nathanfallet.pokemonmaprandomizer.loader

import me.nathanfallet.pokemonmaprandomizer.model.Warp

/**
 * Parses WarpDictionary.csv / WarpDictionaryBW2.csv and stamps [Warp.binaryID]
 * onto all matching warps in [warps].
 *
 * CSV format (first row is header, skipped):
 *   MapHeader, ID
 *
 * MapHeader is the same as a warp's [Warp.warpTo] field.
 * ID is the binary map-header index used in the ROM's event-data files.
 */
object WarpDictionaryLoader {

    /**
     * Parses the dictionary and applies binary IDs to [warps] in-place.
     *
     * @param csvContent Raw text content of WarpDictionary.csv or WarpDictionaryBW2.csv.
     * @param warps      All warps loaded by [WarpLoader]; their [Warp.binaryID] is updated.
     * @return Map from map-header string to binary ID (for reference / testing).
     */
    fun loadAndApply(csvContent: String, warps: List<Warp>): Map<String, Short> {
        val lines = csvContent.lines()
        val dict = mutableMapOf<String, Short>()

        for (line in lines.drop(1)) {
            val trimmed = line.trim().trimStart('\uFEFF')
            if (trimmed.isBlank()) continue
            val parts = trimmed.split(",")
            if (parts.size < 2) continue

            val mapHeader = parts[0].trim()
            val id = parts[1].trim().toShortOrNull() ?: continue
            dict[mapHeader] = id
        }

        for (w in warps) {
            dict[w.warpTo]?.let { w.binaryID = it }
        }

        return dict
    }
}
