package com.noelledotjpg.Data;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class WorldsData {

    private BufferedImage thumbnail = null;
    private String name     = "Unknown World";
    private String gamemode = "Unknown";
    private String seed     = "Unknown";
    private String size     = "0 MB";
    private String created  = "Unknown";
    private final Path folder;

    private static final BufferedImage UNKNOWN_THUMBNAIL = loadUnknownThumbnail();

    private static BufferedImage loadUnknownThumbnail() {
        try (InputStream is = WorldsData.class.getResourceAsStream("/img/unknown.png")) {
            if (is != null) return ImageIO.read(is);
        } catch (Exception ignored) {}
        return null;
    }

    private static final Map<Integer, String> GAMEMODE_NAMES = new HashMap<>();
    static {
        GAMEMODE_NAMES.put(0, "Survival");
        GAMEMODE_NAMES.put(1, "Creative");
        GAMEMODE_NAMES.put(2, "Adventure");
        GAMEMODE_NAMES.put(3, "Spectator");
    }

    public WorldsData(Path folder) {
        this.folder = folder;
        try {
            parse(folder.resolve("saveData.ms"));
        } catch (Exception e) {
            System.err.println("Failed to parse " + folder + ": " + e.getMessage());
        }
        populateFileInfo(folder);
    }

    // save parsing

    private void parse(Path saveFile) throws Exception {
        if (!Files.exists(saveFile)) return;
        byte[] decompressed = decompress(Files.readAllBytes(saveFile));
        VirtualFile levelDat = findLevelDat(decompressed);
        if (levelDat == null) {
            System.err.println("level.dat not found in " + saveFile);
            return;
        }
        parseLevelDat(levelDat.getBytes());
    }

    // decompression

    private static byte[] decompress(byte[] raw) throws IOException {
        if (raw.length < 8) throw new IOException("File too short");

        ByteBuffer hdr = ByteBuffer.wrap(raw, 0, 8).order(ByteOrder.LITTLE_ENDIAN);
        int saveVer    = hdr.getInt();
        int decompSize = hdr.getInt();

        if (saveVer != 0) throw new IOException("Unexpected saveVer: " + saveVer);

        try {
            byte[] out = new byte[decompSize];
            Inflater inf = new Inflater();
            inf.setInput(raw, 8, raw.length - 8);
            int n = inf.inflate(out);
            inf.end();
            if (n != decompSize)
                throw new IOException("Decompressed " + n + " of " + decompSize + " bytes");
            return out;
        } catch (DataFormatException e) {
            throw new IOException("zlib decompression failed", e);
        }
    }

    private static byte[] compress(byte[] data) {
        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION);
        def.setInput(data);
        def.finish();

        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
        byte[] buf = new byte[8192];
        while (!def.finished()) {
            int n = def.deflate(buf);
            baos.write(buf, 0, n);
        }
        def.end();

        // prepend LCE header: saveVer=0 (LE u32) + decompressedSize (LE u32)
        byte[] compressed = baos.toByteArray();
        ByteBuffer out = ByteBuffer.allocate(8 + compressed.length).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0);            // saveVer
        out.putInt(data.length);  // decompressed size
        out.put(compressed);
        return out.array();
    }

    /**
     * Virtual filesystem layout
     *
     * <p><b>Global header</b> (first 12 bytes of decompressed data):
     * <pre>
     * Offset  Type        Description
     * ------  ----------  ---------------------------
     *  [0]    uint32 LE   startOfNextData (byte offset where file table begins)
     *  [4]    uint16 LE   numFiles
     *  [6]    uint16 LE   unknown
     *  [8]    uint16 LE   unknown (9 in tested saves)
     *  [10]   uint16 LE   unknown (9)
     * </pre>
     *
     * <p><b>File table</b> (at {@code startOfNextData}, 144 bytes per entry):
     * <pre>
     * Offset  Type        Description
     * ------  ----------  ---------------------------
     *  [0]    wchar_t[64] filename, null-padded UTF-16LE (128 bytes)
     *  [128]  uint32 LE   length       - byte count of file content
     *  [132]  uint32 LE   startOffset  - byte offset into decompressed blob
     *  [136]  uint32 LE   field2       - likely last-modified timestamp
     *  [140]  uint32 LE   field3       - 0xC34 on all populated entries
     * </pre>
     */
    private static final int ENTRY_SIZE  = 144;
    private static final int FNAME_BYTES = 128;

    private static class VirtualFile {
        final String name;
        final int    startOffset;
        final int    length;
        final byte[] blob;

        VirtualFile(String name, int startOffset, int length, byte[] blob) {
            this.name        = name;
            this.startOffset = startOffset;
            this.length      = length;
            this.blob        = blob;
        }

        byte[] getBytes() {
            return Arrays.copyOfRange(blob, startOffset, startOffset + length);
        }
    }

    private static List<VirtualFile> parseFileTable(byte[] data) {
        List<VirtualFile> files = new ArrayList<>();
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        int startOfNextData = buf.getInt(0);
        int numFiles        = buf.getShort(4) & 0xFFFF;

        for (int i = 0; i < numFiles; i++) {
            int entryOff = startOfNextData + i * ENTRY_SIZE;
            if (entryOff + ENTRY_SIZE > data.length) break;

            String name     = new String(data, entryOff, FNAME_BYTES, StandardCharsets.UTF_16LE)
                    .replace("\0", "");
            int length      = buf.getInt(entryOff + FNAME_BYTES);
            int startOffset = buf.getInt(entryOff + FNAME_BYTES + 4);

            if (length > 0 && startOffset + length <= startOfNextData)
                files.add(new VirtualFile(name, startOffset, length, data));
        }
        return files;
    }

    private static VirtualFile findLevelDat(byte[] decompressed) {
        for (VirtualFile f : parseFileTable(decompressed))
            if (f.name.equals("level.dat")) return f;
        return null;
    }

    // save patching

    /**
     * Renames the world by patching the {@code LevelName} TAG_String inside
     * {@code level.dat}, then recompressing and writing {@code saveData.ms}.
     */
    public void rename(String newName) throws IOException {
        byte[] nameBytes = newName.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write((nameBytes.length >> 8) & 0xFF);
        baos.write(nameBytes.length & 0xFF);
        baos.write(nameBytes);
        patchLevelDat("LevelName", (byte) 8, baos.toByteArray(), true);
        this.name = newName;
    }

    /**
     * Changes the world's game mode by overwriting the {@code GameType} TAG_Int
     * (4 bytes, big-endian) inside {@code level.dat}.
     *
     * @param gamemodeId 0=Survival, 1=Creative, 2=Adventure, 3=Spectator
     */
    public void setGamemode(int gamemodeId) throws IOException {
        byte[] value = new byte[]{
                (byte) ((gamemodeId >> 24) & 0xFF),
                (byte) ((gamemodeId >> 16) & 0xFF),
                (byte) ((gamemodeId >> 8)  & 0xFF),
                (byte)  (gamemodeId        & 0xFF)
        };
        patchLevelDat("GameType", (byte) 3, value, false);
        this.gamemode = GAMEMODE_NAMES.getOrDefault(gamemodeId, String.valueOf(gamemodeId));
    }

    /**
     * NBT field patcher
     *
     * <p>Walks the {@code level.dat} NBT inside {@code saveData.ms}, locates
     * the first tag matching {@code targetName} and {@code targetTagType},
     * replaces its value bytes with {@code newValueBytes}, then recompresses
     * and writes the save back to disk.
     *
     * @param targetName     NBT tag name to find (e.g. "LevelName", "GameType")
     * @param targetTagType  NBT type byte (8=TAG_String, 3=TAG_Int, …)
     * @param newValueBytes  raw bytes to write as the new value (for TAG_String:
     *                       uint16-BE length prefix + UTF-8 body; for TAG_Int:
     *                       4 bytes big-endian)
     * @param variableSize   {@code true} when the old and new value may differ in
     *                       length (TAG_String); {@code false} for fixed-size tags
     */
    private void patchLevelDat(String targetName, byte targetTagType,
                               byte[] newValueBytes, boolean variableSize) throws IOException {
        Path   saveFile = folder.resolve("saveData.ms");
        byte[] blob     = decompress(Files.readAllBytes(saveFile));

        ByteBuffer buf          = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        int        tableStart   = buf.getInt(0);
        int        numFiles     = buf.getShort(4) & 0xFFFF;

        int ldEntryOff = -1;
        int ldStart    = -1;
        int ldLen      = -1;

        for (int i = 0; i < numFiles; i++) {
            int off = tableStart + i * ENTRY_SIZE;
            if (off + ENTRY_SIZE > blob.length) break;
            String fname = new String(blob, off, FNAME_BYTES, StandardCharsets.UTF_16LE)
                    .replace("\0", "");
            if (fname.equals("level.dat")) {
                ldLen      = buf.getInt(off + FNAME_BYTES);
                ldStart    = buf.getInt(off + FNAME_BYTES + 4);
                ldEntryOff = off;
                break;
            }
        }
        if (ldEntryOff < 0) throw new IOException("level.dat not found in save");

        byte[] oldNbt = Arrays.copyOfRange(blob, ldStart, ldStart + ldLen);

        // find the value range of the target tag using CountingInputStream
        CountingInputStream cs  = new CountingInputStream(new ByteArrayInputStream(oldNbt));
        DataInputStream     dis = new DataInputStream(cs);
        dis.readByte();          // root TAG_Compound
        skipNbtString(dis);      // root name

        int[] valStart = {-1};
        int[] valEnd   = {-1};
        findTagValueRange(dis, cs, targetTagType, targetName, valStart, valEnd);

        if (valStart[0] < 0)
            throw new IOException(targetName + " tag not found in level.dat");

        // splice new value bytes into NBT
        int    oldValLen = valEnd[0] - valStart[0];
        byte[] newNbt    = new byte[oldNbt.length - oldValLen + newValueBytes.length];
        System.arraycopy(oldNbt,       0,              newNbt, 0,                 valStart[0]);
        System.arraycopy(newValueBytes, 0,             newNbt, valStart[0],        newValueBytes.length);
        System.arraycopy(oldNbt,       valEnd[0],      newNbt, valStart[0] + newValueBytes.length,
                oldNbt.length - valEnd[0]);

        int sizeDelta = newNbt.length - oldNbt.length;

        // splice new NBT into blob
        byte[] newBlob = new byte[blob.length + sizeDelta];
        System.arraycopy(blob,   0,              newBlob, 0,             ldStart);
        System.arraycopy(newNbt, 0,              newBlob, ldStart,        newNbt.length);
        System.arraycopy(blob,   ldStart + ldLen, newBlob, ldStart + newNbt.length,
                blob.length - (ldStart + ldLen));

        // update level.dat length in file table if size changed
        if (sizeDelta != 0)
            ByteBuffer.wrap(newBlob).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(ldEntryOff + FNAME_BYTES, newNbt.length);

        Files.write(saveFile, compress(newBlob));
    }

    /**
     * Recursively walks a TAG_Compound, recording the byte range
     * {@code [valStart, valEnd)} of the value payload for the first tag
     * matching both {@code wantType} and {@code wantName}.
     */
    private static void findTagValueRange(DataInputStream dis, CountingInputStream cs,
                                          byte wantType, String wantName,
                                          int[] valStart, int[] valEnd) throws IOException {
        while (true) {
            byte   tag     = dis.readByte();
            if (tag == 0) return;                        // TAG_End
            String tagName = readNbtString(dis);

            if (tag == wantType && wantName.equals(tagName)) {
                valStart[0] = (int) cs.getCount();
                skipTagValue(dis, tag);                  // consume the value to find end
                valEnd[0] = (int) cs.getCount();
                return;
            }
            skipTagValue(dis, tag);                      // skip unrelated tag value
            if (valStart[0] >= 0) return;                // already found (nested)
        }
    }

    private static void skipTagValue(DataInputStream dis, byte tag) throws IOException {
        switch (tag) {
            case 1  -> dis.readByte();
            case 2  -> dis.readShort();
            case 3  -> dis.readInt();
            case 4  -> dis.readLong();
            case 5  -> dis.readFloat();
            case 6  -> dis.readDouble();
            case 7  -> dis.skipBytes(dis.readInt());
            case 8  -> dis.skipBytes(dis.readUnsignedShort());
            case 9  -> {
                byte elemTag = dis.readByte();
                int  count   = dis.readInt();
                for (int i = 0; i < count; i++) skipTagValue(dis, elemTag);
            }
            case 10 -> {                                  // TAG_Compound — skip until TAG_End
                while (true) {
                    byte inner = dis.readByte();
                    if (inner == 0) break;
                    skipNbtString(dis);
                    skipTagValue(dis, inner);
                }
            }
            case 11 -> dis.skipBytes(dis.readInt() * 4);
        }
    }

    private static class CountingInputStream extends FilterInputStream {
        private long count = 0;

        CountingInputStream(InputStream in) { super(in); }

        @Override public int read() throws IOException {
            int b = super.read();
            if (b >= 0) count++;
            return b;
        }

        @Override public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) count += n;
            return n;
        }

        @Override public long skip(long n) throws IOException {
            long s = super.skip(n);
            count += s;
            return s;
        }

        public long getCount() { return count; }
    }

    /**
     * NBT parser (confirmed: standard uncompressed NBT, UTF-8 strings).
     *
     * <p><b>Tag types used in {@code level.dat}:</b>
     * <pre>
     * ID   Tag Name        Payload
     * ---  --------------  --------------------------
     *  0   TAG_End         (none - terminates TAG_Compound)
     *  1   TAG_Byte        1 byte
     *  2   TAG_Short       2 bytes, big-endian
     *  3   TAG_Int         4 bytes, big-endian
     *  4   TAG_Long        8 bytes, big-endian
     *  5   TAG_Float       4 bytes, big-endian
     *  6   TAG_Double      8 bytes, big-endian
     *  7   TAG_Byte_Array  int32 length + n bytes
     *  8   TAG_String      uint16 length + UTF-8 bytes
     *  9   TAG_List        byte elemType + int32 count + n payloads
     * 10   TAG_Compound    repeated (tag + name + payload) until TAG_End
     * 11   TAG_Int_Array   int32 length + n * 4 bytes
     * </pre>
     *
     * @see <a href="https://wiki.vg/NBT">NBT format specification</a>
     */
    private void parseLevelDat(byte[] nbt) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(nbt));
        byte rootTag = dis.readByte();
        if (rootTag != 10) throw new IOException("Expected TAG_Compound at root, got " + rootTag);
        skipNbtString(dis);
        readCompound(dis);
    }

    private void readCompound(DataInputStream dis) throws IOException {
        while (true) {
            byte tag = dis.readByte();
            if (tag == 0) return;
            String tagName = readNbtString(dis);
            readPayload(dis, tag, tagName);
        }
    }

    private void readPayload(DataInputStream dis, byte tag, String tagName) throws IOException {
        switch (tag) {
            case 1  -> {
                byte v = dis.readByte();
                if ("hardcore".equals(tagName) && v == 1) gamemode = "Hardcore";
            }
            case 2  -> dis.readShort();
            case 3  -> {
                int v = dis.readInt();
                if ("GameType".equals(tagName))
                    gamemode = GAMEMODE_NAMES.getOrDefault(v, String.valueOf(v));
            }
            case 4  -> {
                long v = dis.readLong();
                if ("RandomSeed".equals(tagName)) seed = String.valueOf(v);
            }
            case 5  -> dis.readFloat();
            case 6  -> dis.readDouble();
            case 7  -> dis.skipBytes(dis.readInt());
            case 8  -> {
                String v = readNbtString(dis);
                if ("LevelName".equals(tagName)) name = v;
            }
            case 9  -> {
                byte elemTag = dis.readByte();
                int  count   = dis.readInt();
                for (int i = 0; i < count; i++) readPayload(dis, elemTag, "");
            }
            case 10 -> readCompound(dis);
            case 11 -> dis.skipBytes(dis.readInt() * 4);
            default -> throw new IOException("Unknown NBT tag: " + tag + " ('" + tagName + "')");
        }
    }

    private static String readNbtString(DataInputStream dis) throws IOException {
        int len = dis.readUnsignedShort();
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void skipNbtString(DataInputStream dis) throws IOException {
        dis.skipBytes(dis.readUnsignedShort());
    }

    // --- Metadata ---

    private void populateFileInfo(Path folder) {
        try {
            Path saveFile = folder.resolve("saveData.ms");
            if (Files.exists(saveFile))
                size = String.format("%.2f MB", Files.size(saveFile) / 1024.0 / 1024.0);

            FileTime ct = (FileTime) Files.getAttribute(folder, "creationTime");
            created = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(ct.toInstant());
        } catch (Exception e) {
            size    = "0 MB";
            created = "Unknown";
        }
        thumbnail = loadThumbnail(folder);
    }

    private static BufferedImage loadThumbnail(Path folder) {
        for (String candidate : new String[]{
                "thumbnail.jpg", "thumbnail.png",
                "screenshot.jpg", "screenshot.png",
                "ICON0.PNG", "thumbnail.dds"}) {
            try {
                Path p = folder.resolve(candidate);
                if (Files.exists(p)) {
                    BufferedImage img = ImageIO.read(p.toFile());
                    if (img != null) return img;
                }
            } catch (Exception ignored) {}
        }

        try {
            BufferedImage embedded = extractThumbnailFromSave(folder.resolve("saveData.ms"));
            if (embedded != null) return embedded;
        } catch (Exception ignored) {}

        return UNKNOWN_THUMBNAIL;
    }

    private static BufferedImage extractThumbnailFromSave(Path saveFile) throws Exception {
        if (!Files.exists(saveFile)) return null;

        byte[] data = decompress(Files.readAllBytes(saveFile));
        byte[] soi  = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        byte[] eoi  = {(byte) 0xFF, (byte) 0xD9};

        int idx = indexOf(data, soi, 0);
        while (idx != -1) {
            int end = indexOf(data, eoi, idx);
            if (end == -1) break;

            byte[] jpegBytes = Arrays.copyOfRange(data, idx, end + 2);
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpegBytes));
                if (img != null && img.getWidth() <= 512 && img.getHeight() <= 512)
                    return img;
            } catch (Exception ignored) {}

            idx = indexOf(data, soi, idx + 1);
        }
        return null;
    }

    private static int indexOf(byte[] data, byte[] needle, int from) {
        outer:
        for (int i = from; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++)
                if (data[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    public BufferedImage getThumbnail() { return thumbnail; }
    public String getName()             { return name; }
    public String getGamemode()         { return gamemode; }
    public String getSeed()             { return seed; }
    public String getSize()             { return size; }
    public String getCreated()          { return created; }
    public Path   getFolder()           { return folder; }


    public int getGamemodeId() {
        for (Map.Entry<Integer, String> e : GAMEMODE_NAMES.entrySet())
            if (e.getValue().equals(gamemode)) return e.getKey();
        return -1;
    }
}