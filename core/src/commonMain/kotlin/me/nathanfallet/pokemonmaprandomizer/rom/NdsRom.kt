package me.nathanfallet.pokemonmaprandomizer.rom

/**
 * Parses, mutates, and repacks a Nintendo DS ROM image.
 *
 * NDS ROM binary layout (little-endian):
 *   0x000 – 0x1FF  header (512 bytes)
 *   arm9RomOffset   ARM9 binary
 *   arm7RomOffset   ARM7 binary
 *   fntOffset       File Name Table
 *   fatOffset       File Allocation Table (8 bytes per file: start u32, end u32)
 *   arm9OvlOffset   ARM9 overlay table (32 bytes per entry)
 *   arm7OvlOffset   ARM7 overlay table (32 bytes per entry)
 *   iconOffset      Icon/title banner
 *   (file data at offsets recorded in FAT)
 */
class NdsRom private constructor(
    /** Raw 512-byte ROM header; offset fields are updated during [pack]. */
    val rawHeader: ByteArray,
    val arm9: ByteArray,
    val arm7: ByteArray,
    /** Raw bytes of the ARM9 overlay table (32 bytes × N entries). */
    val arm9OverlayTable: ByteArray,
    /** Raw bytes of the ARM7 overlay table (32 bytes × N entries). */
    val arm7OverlayTable: ByteArray,
    /** icon/title banner block. */
    val banner: ByteArray,
    /** All files in the ROM filesystem, keyed by virtual path (e.g. "a/0/3/2"). */
    val files: Map<String, ByteArray>,
    // ---- internal state needed for round-trip ----
    private val fntBytes: ByteArray,
    /** Maps every virtual path to its original FAT file-ID. */
    private val pathToFileId: Map<String, Int>,
    /** FAT entries for file IDs that are NOT part of the virtual filesystem
     *  (e.g. overlay files are stored as FAT entries but accessed via the overlay table). */
    private val otherFatEntries: Map<Int, ByteArray>,
) {

    // -------------------------------------------------------------------------
    // Public accessors
    // -------------------------------------------------------------------------

    val gameCode: String get() = rawHeader.decodeToString(0x0C, 0x10)
    val gameTitle: String get() = rawHeader.decodeToString(0x00, 0x0C).trimEnd('\u0000')

    // -------------------------------------------------------------------------
    // Copy-and-modify helpers
    // -------------------------------------------------------------------------

    fun withArm9(data: ByteArray) = copy(arm9 = data)
    fun withFile(path: String, data: ByteArray) = copy(files = files + (path to data))
    fun withFiles(updates: Map<String, ByteArray>) = copy(files = files + updates)

    // -------------------------------------------------------------------------
    // Pack — rebuild the ROM from in-memory state
    // -------------------------------------------------------------------------

    /**
     * Serialises the (possibly modified) ROM back to a byte array.
     *
     * Layout:
     *   0x000  header (512 B)
     *   …      ARM9 (aligned to 4 B, but placed at original or first available ≥ 0x200 position)
     *   …      ARM7
     *   …      FNT
     *   …      FAT  (rebuilt with new file offsets)
     *   …      ARM9 overlay table
     *   …      ARM7 overlay table
     *   …      banner
     *   …      file data (in file-ID order, each 4-byte aligned)
     */
    fun pack(): ByteArray {
        // Build a sorted list of all file IDs
        val allFileIds: List<Int> = (pathToFileId.values + otherFatEntries.keys).distinct().sorted()
        val fileById: Map<Int, ByteArray> = buildMap {
            for ((path, id) in pathToFileId) put(id, files[path]!!)
            putAll(otherFatEntries)
        }

        // We'll fill in sections sequentially, keeping a running cursor
        val out = mutableListOf<ByteArray>()

        fun align4(current: Int): Int = (current + 3) and -4
        fun appendPadded(bytes: ByteArray): Int {
            out.add(bytes)
            val padLen = align4(bytes.size) - bytes.size
            if (padLen > 0) out.add(ByteArray(padLen))
            return align4(bytes.size)
        }

        // ---- header placeholder (updated at the end) ----
        val headerPlaceholder = rawHeader.copyOf()
        out.add(headerPlaceholder)   // index 0, 512 bytes

        // ---- ARM9 ----
        val arm9Start = 0x200   // conventional start
        repeat(arm9Start - 0x200) { }   // header already adds 512
        val arm9Section = ByteArray(arm9Start - 0x200) + arm9

        // Simpler: track absolute cursor
        data class Section(val offset: Int, val data: ByteArray)

        val sections = mutableListOf<Section>()
        var cursor = 0

        fun place(bytes: ByteArray): Int {
            val off = cursor
            sections.add(Section(off, bytes))
            cursor = align4(off + bytes.size)
            return off
        }

        // header
        val hdrOff = place(headerPlaceholder); check(hdrOff == 0)

        // ARM9 must be at least at 0x200
        if (cursor < 0x200) {
            val gap = ByteArray(0x200 - cursor)
            place(gap)
        }
        val newArm9Off = place(arm9)
        val newArm7Off = place(arm7)
        val newFntOff = place(fntBytes)
        // FAT — we'll rebuild after laying out file data
        val fatSize = allFileIds.size * 8
        val fatPlaceholder = ByteArray(fatSize)
        val newFatOff = place(fatPlaceholder)

        val newArm9OvlOff = if (arm9OverlayTable.isNotEmpty()) place(arm9OverlayTable) else 0
        val newArm7OvlOff = if (arm7OverlayTable.isNotEmpty()) place(arm7OverlayTable) else 0
        val newBannerOff = place(banner)

        // file data
        val newFileOffsets = mutableMapOf<Int, Int>() // fileId -> start offset
        for (id in allFileIds) {
            val data = fileById[id] ?: ByteArray(0)
            newFileOffsets[id] = place(data)
        }

        // Build FAT bytes
        val fatBytes = ByteArray(fatSize)
        for ((idx, id) in allFileIds.withIndex()) {
            val data = fileById[id] ?: ByteArray(0)
            val start = newFileOffsets[id]!!
            val end = start + data.size
            writeU32(fatBytes, idx * 8, start.toLong())
            writeU32(fatBytes, idx * 8 + 4, end.toLong())
        }

        // Update header (GBATEK offsets)
        val newHeader = rawHeader.copyOf()
        writeU32(newHeader, 0x020, newArm9Off.toLong())
        writeU32(newHeader, 0x02C, arm9.size.toLong())       // ARM9 size
        writeU32(newHeader, 0x030, newArm7Off.toLong())
        writeU32(newHeader, 0x040, newFntOff.toLong())
        writeU32(newHeader, 0x044, fntBytes.size.toLong())   // FNT size unchanged
        writeU32(newHeader, 0x048, newFatOff.toLong())
        writeU32(newHeader, 0x04C, fatSize.toLong())         // FAT size = allFileIds*8
        if (arm9OverlayTable.isNotEmpty()) writeU32(newHeader, 0x050, newArm9OvlOff.toLong())
        if (arm7OverlayTable.isNotEmpty()) writeU32(newHeader, 0x058, newArm7OvlOff.toLong())
        writeU32(newHeader, 0x068, newBannerOff.toLong())
        writeU32(newHeader, 0x080, cursor.toLong())          // total used ROM size

        // Assemble output
        val result = ByteArray(cursor)
        for (sec in sections) {
            when {
                sec.offset == 0 -> newHeader.copyInto(result, 0)     // header
                sec.data === fatPlaceholder -> fatBytes.copyInto(result, sec.offset)
                else -> sec.data.copyInto(result, sec.offset)
            }
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Internal copy helper
    // -------------------------------------------------------------------------

    private fun copy(
        arm9: ByteArray = this.arm9,
        files: Map<String, ByteArray> = this.files,
    ) = NdsRom(
        rawHeader, arm9, arm7,
        arm9OverlayTable, arm7OverlayTable,
        banner, files, fntBytes, pathToFileId, otherFatEntries,
    )

    // -------------------------------------------------------------------------
    // Companion — parse
    // -------------------------------------------------------------------------

    companion object {

        /** Parse an NDS ROM image into an [NdsRom] instance. */
        fun parse(data: ByteArray): NdsRom {
            val header = data.copyOfRange(0, 0x200)

            fun u32(offset: Int) = readU32(data, offset).toInt()
            fun u16(offset: Int) = ((data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8))

            val arm9Off = u32(0x020);
            val arm9Size = u32(0x02C)
            val arm7Off = u32(0x030);
            val arm7Size = u32(0x03C)
            val fntOff = u32(0x040);
            val fntSize = u32(0x044)
            val fatOff = u32(0x048);
            val fatSize = u32(0x04C)
            val ovl9Off = u32(0x050);
            val ovl9Size = u32(0x054)
            val ovl7Off = u32(0x058);
            val ovl7Size = u32(0x05C)
            val bannerOff = u32(0x068)

            val arm9 = data.copyOfRange(arm9Off, arm9Off + arm9Size)
            val arm7 = data.copyOfRange(arm7Off, arm7Off + arm7Size)
            val fntBytes = data.copyOfRange(fntOff, fntOff + fntSize)
            val arm9OvlTable = if (ovl9Size > 0) data.copyOfRange(ovl9Off, ovl9Off + ovl9Size) else ByteArray(0)
            val arm7OvlTable = if (ovl7Size > 0) data.copyOfRange(ovl7Off, ovl7Off + ovl7Size) else ByteArray(0)

            // Banner size varies by DS type; 0xA00 covers DSi+NTR, 0x840 for standard NTR
            val bannerSize = estimateBannerSize(data, bannerOff)
            val banner = if (bannerOff > 0) data.copyOfRange(bannerOff, bannerOff + bannerSize) else ByteArray(0)

            // Collect overlay file IDs so we exclude them from the VFS
            val overlayFileIds = mutableSetOf<Int>()
            val ovlEntrySize = 32
            repeat(ovl9Size / ovlEntrySize) { i ->
                overlayFileIds.add(readU32(arm9OvlTable, i * ovlEntrySize + 24).toInt())
            }
            repeat(ovl7Size / ovlEntrySize) { i ->
                overlayFileIds.add(readU32(arm7OvlTable, i * ovlEntrySize + 24).toInt())
            }

            // Parse FAT
            val numFiles = fatSize / 8
            fun fileData(id: Int): ByteArray {
                val start = u32(fatOff + id * 8)
                val end = u32(fatOff + id * 8 + 4)
                return if (start >= end) ByteArray(0) else data.copyOfRange(start, end)
            }

            // Parse FNT → virtual paths
            val pathToFileId = mutableMapOf<String, Int>()
            parseFnt(fntBytes, pathToFileId)

            val files: Map<String, ByteArray> = pathToFileId.mapValues { (_, id) -> fileData(id) }

            // Collect "other" FAT entries (overlays + any unreferenced files)
            val referencedIds = pathToFileId.values.toSet()
            val otherFatEntries = (0 until numFiles)
                .filter { it !in referencedIds }
                .associateWith { fileData(it) }

            return NdsRom(
                header, arm9, arm7,
                arm9OvlTable, arm7OvlTable,
                banner, files, fntBytes, pathToFileId, otherFatEntries,
            )
        }

        // ---- FNT parser ----

        private fun parseFnt(fnt: ByteArray, out: MutableMap<String, Int>) {
            fun u16(off: Int) = ((fnt[off].toInt() and 0xFF) or ((fnt[off + 1].toInt() and 0xFF) shl 8))

            // Number of directories from root entry field [6..7]
            // val numDirs = u16(6)  -- not needed directly

            fun parseDir(dirId: Int, prefix: String) {
                val dirIndex = dirId and 0x0FFF
                val entryBase = dirIndex * 8
                val subtableOff = readU32(fnt, entryBase).toInt()
                var firstFileId = u16(entryBase + 4)

                var pos = subtableOff
                var fileId = firstFileId

                while (pos < fnt.size) {
                    val typLen = fnt[pos++].toInt() and 0xFF
                    if (typLen == 0) break

                    val nameLen = typLen and 0x7F
                    val isDir = (typLen and 0x80) != 0

                    if (pos + nameLen > fnt.size) break
                    val name = fnt.decodeToString(pos, pos + nameLen)
                    pos += nameLen

                    val fullPath = if (prefix.isEmpty()) name else "$prefix/$name"

                    if (isDir) {
                        if (pos + 2 > fnt.size) break
                        val subDirId = u16(pos); pos += 2
                        parseDir(subDirId, fullPath)
                    } else {
                        out[fullPath] = fileId++
                    }
                }
            }

            parseDir(0xF000, "")
        }

        // ---- Banner size heuristic ----

        private fun estimateBannerSize(data: ByteArray, bannerOff: Int): Int {
            if (bannerOff <= 0 || bannerOff >= data.size) return 0
            val version = ((data[bannerOff].toInt() and 0xFF) or ((data[bannerOff + 1].toInt() and 0xFF) shl 8))
            return when {
                version >= 0x0103 -> 0x1240
                version >= 0x0002 -> 0x0940
                else -> 0x0840
            }
        }

        // ---- Byte I/O helpers ----

        internal fun readU32(buf: ByteArray, offset: Int): Long =
            (buf[offset].toLong() and 0xFF) or
                    ((buf[offset + 1].toLong() and 0xFF) shl 8) or
                    ((buf[offset + 2].toLong() and 0xFF) shl 16) or
                    ((buf[offset + 3].toLong() and 0xFF) shl 24)

        internal fun writeU32(buf: ByteArray, offset: Int, value: Long) {
            buf[offset] = (value and 0xFF).toByte()
            buf[offset + 1] = (value.ushr(8) and 0xFF).toByte()
            buf[offset + 2] = (value.ushr(16) and 0xFF).toByte()
            buf[offset + 3] = (value.ushr(24) and 0xFF).toByte()
        }
    }
}
