package me.nathanfallet.pokemonmaprandomizer.randomizer

import me.nathanfallet.pokemonmaprandomizer.loader.BlockData
import me.nathanfallet.pokemonmaprandomizer.loader.FlagAlias
import me.nathanfallet.pokemonmaprandomizer.model.Block
import me.nathanfallet.pokemonmaprandomizer.model.Warp

/**
 * Core map-randomization algorithm.
 *
 * Mirrors the C++ `RandomizeMap()` function in Gen4/Randomizer.cpp (lines 356–507).
 *
 * State is intentionally mutable and local to each [randomize] call so the
 * object is safe to call multiple times with different seeds.
 */
class MapRandomizer(private val data: BlockData) {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Attempts to produce a beatable randomized warp layout for [seed].
     *
     * @return [RandomizationResult] on success, or `null` if the seed yields an
     *         unbeatable game (caller should retry with seed+1).
     */
    fun randomize(seed: Long): RandomizationResult? {
        // Deep-copy mutable state so we don't corrupt data for retries
        val needsConnections = data.needsConnections.toMutableList()
        val unusedWarps = data.unusedWarps.toMutableList()
        val usedWarps = mutableListOf<Warp>()
        val unusedBlocks = data.unusedBlocks.toMutableList()
        val garbageRooms = data.garbageRooms.toMutableList()
        val requiredDeadEnds = data.requiredDeadEnds.toMutableList()
        val progression = data.progression.map { it.copy() }.toMutableList()
        val otherFlags = data.otherFlags.map { it.copy() }.toMutableList()
        val mainFlags = data.mainFlags.toList()
        val firstList = mutableListOf<Pair<Block, Warp>>()

        // Also reset newWarp on all warps
        data.unusedWarps.forEach { it.newWarp = null }
        data.garbageRooms.forEach { it.newWarp = null }
        data.requiredDeadEnds.forEach { it.newWarp = null }

        val rng = KotlinRandom(seed)
        unusedWarps.shuffle(rng.random)

        // ---- 8b. Main connection loop ----------------------------------------

        while (needsConnections.isNotEmpty()) {
            needsConnections.shuffle(rng.random)

            val warp = unusedWarps.last()
            val neededWarp = needsConnections.last()

            connectWarps(warp, neededWarp)

            usedWarps.add(warp); usedWarps.add(neededWarp)
            unusedWarps.removeLast()
            needsConnections.removeLast()

            // Remove from the other list if present
            unusedWarps.removeIfPresent(neededWarp)
            needsConnections.removeIfPresent(warp)

            // If we just discovered a new block, open it up
            val warpBlock = warp.block
            val blockIdx = unusedBlocks.indexOf(warpBlock)
            if (warpBlock != null && blockIdx != -1) {
                unusedBlocks.removeAt(blockIdx)
                for (w in warpBlock) {
                    if (unusedWarps.contains(w)) {
                        needsConnections.add(w)
                    } else {
                        firstList.add(warpBlock to w)
                    }
                }
            }

            // If we run out of pending connections but still have unvisited blocks,
            // insert a new block into the existing warp chain.
            while (needsConnections.isEmpty() && unusedBlocks.isNotEmpty()) {
                unusedBlocks.shuffle(rng.random)
                usedWarps.shuffle(rng.random)

                val insertionPoint = usedWarps.last()
                if (insertionPoint == insertionPoint.newWarp?.original) continue

                val blk = unusedBlocks.last()
                blk.shuffle(rng.random)

                insertWarps(blk[0], blk[1], insertionPoint)

                // Record which warp is the "first" (nearest to center) for this block
                val first = when {
                    insertionPoint.block == null -> blk[1]
                    insertionPoint == getFirst(insertionPoint.block!!, firstList) -> blk[1]
                    else -> blk[0]
                }
                firstList.add(blk to first)

                usedWarps.add(blk[0]); usedWarps.add(blk[1])
                unusedWarps.removeIfPresent(blk[0])
                unusedWarps.removeIfPresent(blk[1])
                for (c in 2 until blk.size) needsConnections.add(blk[c])
                unusedBlocks.removeLast()
            }
        }

        // ---- 8c. Dead-end handling -------------------------------------------

        for (red in requiredDeadEnds) {
            if (!unusedWarps.contains(red)) continue
            garbageRooms.shuffle(rng.random)
            for (grn in garbageRooms) {
                if (!usedWarps.contains(grn)) continue
                swapConnections(red, grn, unusedWarps, usedWarps)
                break
            }
        }

        // ---- 8d. Flag alias resolution (blu files) --------------------------

        for (t in 0 until 20) {
            otherFlags.shuffle(rng.random)
            for (fa in otherFlags) {
                fa.set = false
                fa.equiv.clear()
                fa.equiv.addAll(fa.def)

                var found = false
                for (f in 0..mainFlags.size) {
                    val reachable = fa.checks.all { check ->
                        checkPath(data.startingPoint, check, fa.equiv, usedWarps)
                    }
                    if (reachable) {
                        found = true; break
                    }
                    if (f < mainFlags.size) fa.equiv.add(mainFlags[f])
                }
                fa.set = true
            }
        }

        // ---- 8e. Progression check ------------------------------------------

        while (progression.isNotEmpty()) {
            garbageRooms.shuffle(rng.random)
            val step = progression.last()
            val target = step.warp ?: run { progression.removeLast(); continue }

            if (!checkPath(data.startingPoint, target, step.flags, usedWarps, otherFlags)) {
                var swapped = false
                for (grn in garbageRooms) {
                    if (!usedWarps.contains(grn)) continue
                    val pivotBlock = target.block
                    val pivot = if (pivotBlock == null) target else getFirst(pivotBlock, firstList)
                    swapConnections(grn, pivot, unusedWarps, usedWarps)
                    if (checkPath(data.startingPoint, target, step.flags, usedWarps, otherFlags)) {
                        swapped = true; break
                    }
                    // Swap back — path still unreachable
                    swapConnections(grn, pivot, unusedWarps, usedWarps)
                }
                if (!swapped) return null
            }
            progression.removeLast()
        }

        return buildResult(data.unusedWarps + data.garbageRooms + data.requiredDeadEnds)
    }

    // -------------------------------------------------------------------------
    // Warp connection helpers
    // -------------------------------------------------------------------------

    private fun connectWarps(a: Warp, b: Warp) {
        a.newWarp = b.original
        b.newWarp = a.original
    }

    private fun insertWarps(a: Warp, b: Warp, insertionPoint: Warp) {
        val warp2 = insertionPoint.newWarp?.original ?: return
        connectWarps(a, insertionPoint)
        connectWarps(b, warp2)
    }

    private fun swapConnections(
        a: Warp,
        b: Warp,
        unusedWarps: MutableList<Warp>,
        usedWarps: MutableList<Warp>,
    ) {
        when {
            a.newWarp == null && b.newWarp != null -> {
                connectWarps(a, b.newWarp!!.original!!)
                b.newWarp = null
                usedWarps.removeIfPresent(b); usedWarps.add(a)
                unusedWarps.removeIfPresent(a); unusedWarps.add(b)
            }

            a.newWarp != null && b.newWarp == null -> {
                swapConnections(b, a, unusedWarps, usedWarps)
            }

            a.newWarp != null && b.newWarp != null -> {
                val ptr = a.newWarp!!.original!!
                connectWarps(a, b.newWarp!!.original!!)
                connectWarps(b, ptr)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Path checking (BFS through warp graph)
    // -------------------------------------------------------------------------

    private fun checkPath(
        from: Warp,
        to: Warp,
        flags: List<String>,
        usedWarps: List<Warp>,
        otherFlags: List<FlagAlias> = emptyList(),
    ): Boolean {
        val checked = mutableListOf<Warp>()
        return checkPathRec(from, to, checked, flags, otherFlags) ||
                checkPathRec(from.newWarp?.original ?: return false, to, checked, flags, otherFlags)
    }

    private fun checkPathRec(
        from: Warp?,
        to: Warp,
        checked: MutableList<Warp>,
        flags: List<String>,
        otherFlags: List<FlagAlias>,
    ): Boolean {
        if (from == null) return false
        if (from == to) return true
        checked.add(from)

        for (con in from.connections) {
            if (con.warp in checked) continue
            if (to in checked) return true
            if (!checkFlags(flags, con.locks, otherFlags)) continue

            val via = con.warp
            if (checkPathRec(via, to, checked, flags, otherFlags)) return true
            if (checkPathRec(via.newWarp?.original, to, checked, flags, otherFlags)) return true
        }
        return false
    }

    private fun checkFlags(
        haveFlags: List<String>,
        required: List<String>,
        otherFlags: List<FlagAlias>,
    ): Boolean {
        val target = required.toMutableList()
        var i = 0
        while (i < target.size) {
            val flag = target[i]
            val aliasIdx = otherFlags.indexOfFirst { it.flag == flag }
            if (aliasIdx != -1) {
                val fa = otherFlags[aliasIdx]
                if (!fa.set) return false
                target.removeAt(i)
                target.addAll(fa.equiv)
                continue
            }
            if (flag !in haveFlags) return false
            i++
        }
        return true
    }

    // -------------------------------------------------------------------------
    // Block helpers
    // -------------------------------------------------------------------------

    private fun getFirst(block: Block, firstList: List<Pair<Block, Warp>>): Warp {
        return firstList.firstOrNull { it.first === block }?.second ?: block[0]
    }

    // -------------------------------------------------------------------------
    // Result builder
    // -------------------------------------------------------------------------

    private fun buildResult(allWarps: List<Warp>): RandomizationResult {
        val log = buildString {
            for (w in allWarps) {
                if (w.newWarp != null) {
                    appendLine("${w.warpID} <-> ${w.newWarp!!.original?.warpID}")
                }
            }
        }
        return RandomizationResult(allWarps, log)
    }
}
