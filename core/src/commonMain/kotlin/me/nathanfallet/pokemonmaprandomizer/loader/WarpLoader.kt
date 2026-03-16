package me.nathanfallet.pokemonmaprandomizer.loader

import me.nathanfallet.pokemonmaprandomizer.model.Warp

/**
 * Parses Warps.csv / WarpsBW2.csv into a list of [Warp] objects.
 *
 * CSV format (first row is header, skipped):
 *   WarpID, MapID, Anchor, Other
 *
 * After loading, each warp's [Warp.original] and [Warp.binaryID] fields are
 * filled by [WarpDictionaryLoader] / cross-linking within [link].
 */
object WarpLoader {

    /**
     * @param csvContent Raw text content of Warps.csv or WarpsBW2.csv.
     * @return Ordered list of parsed warps (same order as the CSV rows).
     */
    fun load(csvContent: String): List<Warp> {
        val lines = csvContent.lines()
        val warps = mutableListOf<Warp>()

        for (line in lines.drop(1)) {
            val trimmed = line.trim().trimStart('\uFEFF') // strip optional BOM
            if (trimmed.isBlank() || trimmed.startsWith("STOP")) break
            val parts = trimmed.split(",")
            if (parts.size < 4) continue

            val warpID = parts[0].trim()
            val mapID = parts[1].trim()
            val anchor = parts[2].trim().toShortOrNull() ?: continue
            val other = parts[3].trim()

            warps.add(Warp(warpID, mapID, anchor, other))
        }

        // Cross-link `original` pointers
        val byId = warps.associateBy { it.warpID }
        for (w in warps) {
            w.original = byId[w.otherID]
            if (w.original == null) {
                // Non-fatal — some warps legitimately have no defined pair
            }
        }

        return warps
    }
}
