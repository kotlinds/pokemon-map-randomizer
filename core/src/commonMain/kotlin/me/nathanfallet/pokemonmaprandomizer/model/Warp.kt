package me.nathanfallet.pokemonmaprandomizer.model

/**
 * Represents one warp (exit/entrance) in the game world.
 *
 * Mirrors the C++ WARP struct from Gen4/Randomizer.h.
 * Fields are mutable because the randomization algorithm wires them up after construction.
 */
class Warp(
    val warpID: String,
    val warpTo: String,
    val anchor: Short,
    val otherID: String,
) {
    /** Binary map-header ID read from WarpDictionary.csv. */
    var binaryID: Short = 0

    /** The block this warp belongs to; null for dead-end warps. */
    var block: Block? = null

    /**
     * The *paired* warp on the other side of this door in the original game.
     * Set to `GetWarpByID(otherID)` after all warps are loaded.
     */
    var original: Warp? = null

    /** After randomization: the new destination warp that this exit leads to. */
    var newWarp: Warp? = null

    /** Adjacent warps reachable from here (built from .blk flag-sequence files). */
    val connections: MutableList<Connection> = mutableListOf()

    override fun toString() = warpID
}
