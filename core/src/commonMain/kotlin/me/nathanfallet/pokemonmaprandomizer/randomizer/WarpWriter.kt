package me.nathanfallet.pokemonmaprandomizer.randomizer

import me.nathanfallet.pokemonmaprandomizer.model.Game
import me.nathanfallet.pokemonmaprandomizer.model.Warp

/**
 * Writes randomized warp destinations back into binary event-data files.
 *
 * ── Gen4 / HGSS (Gen4/Randomizer.cpp SetWarps) ──────────────────────────────
 *   [4 bytes]  num_bg_structs
 *   [N × 0x14] bg structs
 *   [4 bytes]  num_obj_structs
 *   [N × 0x20] obj structs
 *   [4 bytes]  num_warps
 *   [N × 0x0C] warp entries:
 *     +0  [4 bytes] position (ignored)
 *     +4  [2 bytes] map_header_id  ← overwritten with newWarp.binaryID
 *     +6  [2 bytes] anchor_point   ← overwritten with newWarp.anchor
 *     +8  [4 bytes] script / extras (ignored)
 *   total = 0x0C per warp
 *
 * ── Gen5 / BW2 (Gen5/Randomizer.cpp SetWarps) ───────────────────────────────
 *   [8-byte header]
 *     byte 0x4: num_bg_structs
 *     byte 0x5: num_obj_structs
 *     byte 0x6: num_warps
 *   pos = 8 + num_bg * 0x14 + num_obj * 0x24
 *   [N × 0x14] warp entries:
 *     +0  [2 bytes] map_header_id  ← overwritten with newWarp.binaryID
 *     +2  [2 bytes] anchor_point   ← overwritten with newWarp.anchor
 *     +4  [16 bytes] extras (ignored)
 *   total = 0x14 per warp
 */
object WarpWriter {

    /**
     * Applies randomized warp destinations to a single event-data binary.
     *
     * @param eventData  Raw bytes of one file from the event_data NARC.
     * @param warps      All warps with [Warp.newWarp] set by [MapRandomizer].
     * @param game       Which game the ROM is — determines binary layout.
     * @return           Modified copy of [eventData].
     */
    fun write(eventData: ByteArray, warps: List<Warp>, game: Game): ByteArray {
        val bin = eventData.copyOf()
        val warpsByBinId: Map<Pair<Short, Short>, Warp> =
            warps.associateBy { it.binaryID to it.anchor }

        fun u8(pos: Int): Int = bin[pos].toInt() and 0xFF
        fun u32(pos: Int): Int =
            (bin[pos].toInt() and 0xFF) or
                    ((bin[pos + 1].toInt() and 0xFF) shl 8) or
                    ((bin[pos + 2].toInt() and 0xFF) shl 16) or
                    ((bin[pos + 3].toInt() and 0xFF) shl 24)

        fun u16(pos: Int): Short =
            ((bin[pos].toInt() and 0xFF) or ((bin[pos + 1].toInt() and 0xFF) shl 8)).toShort()

        fun writeShort(offset: Int, value: Short) {
            bin[offset] = (value.toInt() and 0xFF).toByte()
            bin[offset + 1] = ((value.toInt() ushr 8) and 0xFF).toByte()
        }

        return when (game) {
            Game.HG, Game.SS -> writeHgss(bin, warpsByBinId, ::u32, ::u16, ::writeShort)
            Game.B2, Game.W2 -> writeBw2(bin, warpsByBinId, ::u8, ::u16, ::writeShort)
        }
    }

    // ── HGSS ─────────────────────────────────────────────────────────────────

    private fun writeHgss(
        bin: ByteArray,
        warpsByBinId: Map<Pair<Short, Short>, Warp>,
        u32: (Int) -> Int,
        u16: (Int) -> Short,
        writeShort: (Int, Short) -> Unit,
    ): ByteArray {
        if (bin.size < 4) return bin
        var pos = 0

        // Skip bg structs: count (4B) + N × 0x14
        val numBg = u32(pos); pos += 4
        pos += numBg * 0x14

        if (pos + 4 > bin.size) return bin

        // Skip obj structs: count (4B) + N × 0x20
        val numObj = u32(pos); pos += 4
        pos += numObj * 0x20

        if (pos + 4 > bin.size) return bin

        // Warp entries — 0x0C bytes each:
        //   +0  4B position (skipped)
        //   +4  2B map_header_id
        //   +6  2B anchor
        //   +8  4B extras (skipped)
        val numWarps = u32(pos); pos += 4
        repeat(numWarps) {
            if (pos + 0x0C > bin.size) return bin

            pos += 4                              // skip position word
            val mapHeaderId = u16(pos)
            val anchor = u16(pos + 2)

            val warp = warpsByBinId[mapHeaderId to anchor]
            val dest = warp?.newWarp
            if (dest != null) {
                writeShort(pos, dest.binaryID)
                writeShort(pos + 2, dest.anchor)
            }

            pos += 8                              // advance past mapHeaderId, anchor, extras
        }

        return bin
    }

    // ── BW2 ──────────────────────────────────────────────────────────────────

    private fun writeBw2(
        bin: ByteArray,
        warpsByBinId: Map<Pair<Short, Short>, Warp>,
        u8: (Int) -> Int,
        u16: (Int) -> Short,
        writeShort: (Int, Short) -> Unit,
    ): ByteArray {
        if (bin.size < 8) return bin

        // Counts are single bytes in the 8-byte file header
        val numBg = u8(0x4)
        val numObj = u8(0x5)
        val numWarps = u8(0x6)

        // Warp block starts after the 8-byte header + bg structs (0x14 each) + obj structs (0x24 each)
        var pos = 8 + numBg * 0x14 + numObj * 0x24

        // Warp entries — 0x14 bytes each:
        //   +0  2B map_header_id
        //   +2  2B anchor
        //   +4  16B extras
        repeat(numWarps) {
            if (pos + 0x14 > bin.size) return bin

            val mapHeaderId = u16(pos)
            val anchor = u16(pos + 2)

            val warp = warpsByBinId[mapHeaderId to anchor]
            val dest = warp?.newWarp
            if (dest != null) {
                writeShort(pos, dest.binaryID)
                writeShort(pos + 2, dest.anchor)
            }

            pos += 0x14
        }

        return bin
    }
}
