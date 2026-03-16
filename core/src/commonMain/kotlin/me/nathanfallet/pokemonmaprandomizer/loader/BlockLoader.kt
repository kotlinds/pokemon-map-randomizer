package me.nathanfallet.pokemonmaprandomizer.loader

import me.nathanfallet.pokemonmaprandomizer.loader.BlockLoader.load
import me.nathanfallet.pokemonmaprandomizer.model.Block
import me.nathanfallet.pokemonmaprandomizer.model.Connection
import me.nathanfallet.pokemonmaprandomizer.model.Warp

/**
 * Loads all block-constraint files (.blk, .grn, .red, .pnk, .blu) from the
 * resource folders and wires up the warp graph for the randomization algorithm.
 *
 * Call [load] with a map of "filename" → "file content" for all relevant block
 * files, together with the already-loaded [warps] list.
 */
object BlockLoader {

    /**
     * @param blockFiles    Map of filename (e.g. "NewBark.blk") to file content.
     *                      The filename must retain its extension.
     * @param warps         Pre-loaded warp list from [WarpLoader].
     * @param startingKey   Substring of the .blk filename that identifies the
     *                      starting-point block ("NewBark" for HGSS, "AspertiaCity" for BW2).
     * @return              Fully wired [BlockData] ready for [MapRandomizer].
     */
    /**
     * @param clearRedBlockRef  Whether to set [Warp.block] = null for .red warps.
     *                          Gen4 (HGSS) C++ does this; Gen5 (BW2) C++ does NOT.
     */
    fun load(
        blockFiles: Map<String, String>,
        warps: List<Warp>,
        startingKey: String,
        clearRedBlockRef: Boolean = true,
    ): BlockData {
        val byId: Map<String, Warp> = warps.associateBy { it.warpID }

        val unusedBlocks = mutableListOf<Block>()
        val unusedWarps = mutableListOf<Warp>()
        val garbageRooms = mutableListOf<Warp>()
        val requiredDeadEnds = mutableListOf<Warp>()
        val needsConnections = mutableListOf<Warp>()
        val progression = mutableListOf<ProgressionStep>()
        val otherFlags = mutableListOf<FlagAlias>()
        val mainFlags = mutableListOf<String>()
        var startingPoint: Warp? = null

        for ((filename, content) in blockFiles) {
            val ext = filename.substringAfterLast('.', "")
            val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }

            when (ext) {
                "blk" -> {
                    val block: Block = mutableListOf()
                    for ((idx, line) in lines.withIndex()) {
                        val ptr = byId[line] ?: continue
                        readSpecialBlockInput(ptr, lines, idx, +1, byId)
                        readSpecialBlockInput(ptr, lines, idx, -1, byId)
                        block.add(ptr)
                        ptr.block = block
                        unusedWarps.add(ptr)
                    }
                    if (filename.contains(startingKey)) {
                        startingPoint = block.firstOrNull()
                        needsConnections.addAll(block)
                    } else {
                        if (block.isNotEmpty()) unusedBlocks.add(block)
                    }
                }

                "grn" -> {
                    for (line in lines) {
                        val ptr = byId[line] ?: continue
                        ptr.block = null
                        garbageRooms.add(ptr)
                        unusedWarps.add(ptr)
                    }
                }

                "red" -> {
                    for (line in lines) {
                        val ptr = byId[line] ?: continue
                        if (clearRedBlockRef) ptr.block = null
                        requiredDeadEnds.add(ptr)
                        unusedWarps.add(ptr)
                    }
                }

                "pnk" -> {
                    val flagList = mutableListOf<String>()
                    for (line in lines) {
                        if (line.contains("FLAG_")) {
                            flagList.add(line)
                            if (line !in mainFlags) mainFlags.add(line)
                        } else {
                            val ptr = byId[line]
                            progression.add(ProgressionStep(ptr, flagList.toList()))
                        }
                    }
                }

                "blu" -> {
                    if (lines.isEmpty()) continue
                    val flag = lines[0]
                    val def = mutableListOf<String>()
                    val checks = mutableListOf<Warp>()
                    for (line in lines.drop(1)) {
                        if (line.contains("FLAG_")) def.add(line)
                        else byId[line]?.let { checks.add(it) }
                    }
                    otherFlags.add(FlagAlias(flag, def.toList(), checks.toList()))
                }
            }
        }

        return BlockData(
            startingPoint = checkNotNull(startingPoint) { "Starting block '$startingKey' not found" },
            unusedBlocks = unusedBlocks,
            unusedWarps = unusedWarps,
            garbageRooms = garbageRooms,
            requiredDeadEnds = requiredDeadEnds,
            needsConnections = needsConnections,
            progression = progression,
            otherFlags = otherFlags,
            mainFlags = mainFlags,
        )
    }

    /**
     * Reads connections from a .blk file in one direction (forward or backward)
     * from [start], collecting FLAG_ requirements as locks until a LEDGE marker
     * or the end of the list is reached.
     */
    private fun readSpecialBlockInput(
        pivot: Warp,
        lines: List<String>,
        start: Int,
        dir: Int,
        byId: Map<String, Warp>,
    ) {
        val flagList = mutableListOf<String>()
        var c = start + dir
        while (if (dir == 1) c < lines.size else c >= 0) {
            val line = lines[c]
            val ptr = byId[line]
            when {
                ptr != null -> {
                    pivot.connections.add(Connection(ptr, flagList.toList()))
                }

                line.contains("FLAG_") -> {
                    flagList.add(line)
                }

                dir == 1 && line.contains("LEDGE_UP") -> return
                dir == -1 && line.contains("LEDGE_DOWN") -> return
            }
            c += dir
        }
    }
}
