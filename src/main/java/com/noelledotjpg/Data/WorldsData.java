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

    // --- Save parsing ---

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

    /**
     * Virtual filesystem layout (confirmed from binary analysis).
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

    /**
     * Attempts to locate a world thumbnail, trying in order:
     * <ol>
     *   <li>Known sidecar filenames next to {@code saveData.ms}</li>
     *   <li>JPEG embedded inside the save file itself</li>
     *   <li>{@code /img/unknown.png} from the classpath as a fallback</li>
     * </ol>
     */
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

    /**
     * Searches the decompressed save blob for an embedded JPEG thumbnail.
     *
     * <p>On Xbox One builds, thumbnails are managed by the platform's
     * StorageManager and are not guaranteed to be present inside
     * {@code saveData.ms}. Returns {@code null} when none is found.
     */
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
}
