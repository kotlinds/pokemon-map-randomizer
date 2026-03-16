package me.nathanfallet.pokemonmaprandomizer.loader

import me.nathanfallet.pokemonmaprandomizer.model.Block
import me.nathanfallet.pokemonmaprandomizer.model.Warp

data class BlockData(
    val startingPoint: Warp,
    val unusedBlocks: MutableList<Block>,
    val unusedWarps: MutableList<Warp>,
    val garbageRooms: MutableList<Warp>,
    val requiredDeadEnds: MutableList<Warp>,
    val needsConnections: MutableList<Warp>,
    val progression: MutableList<ProgressionStep>,
    val otherFlags: MutableList<FlagAlias>,
    val mainFlags: MutableList<String>,
)
