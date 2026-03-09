package com.noelledotjpg.TabContent;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;
import java.util.zip.InflaterInputStream;

public class WorldsData {

    private String name = "Unknown World";
    private String gamemode = "Unknown";
    private String seed = "Unknown";
    private String size = "0 MB";
    private String created = "Unknown";
    private Path folder;

    public WorldsData(Path folder) {
        this.folder = folder;
        parseWorld(folder);
        populateFileInfo(folder);
    }

    private void parseWorld(Path folder) {
        try {
            Path saveFile = folder.resolve("saveData.ms");
            if (!Files.exists(saveFile)) return;

            byte[] raw = Files.readAllBytes(saveFile);
            if (raw.length <= 8) return;

            ByteBuffer header = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            int compressed = header.getInt();
            int decompressedSize = header.getInt();

            byte[] data = Arrays.copyOfRange(raw, 8, raw.length);

            byte[] decompressed;
            if (compressed != 0) {
                InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(data));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inflater.read(buffer)) != -1) out.write(buffer, 0, read);
                decompressed = out.toByteArray();
            } else {
                decompressed = data;
            }

            // Convert the entire decompressed block to UTF-8 text
            String text = new String(decompressed, "UTF-8");

            // Look for the values by key
            name = extractValue(text, "LevelName", name);
            gamemode = extractValue(text, "GameMode", gamemode);
            seed = extractValue(text, "RandomSeed", seed);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String extractValue(String text, String key, String defaultValue) {
        int idx = text.indexOf(key);
        if (idx == -1) return defaultValue;

        int start = idx + key.length();
        while (start < text.length() && (text.charAt(start) == '\0' || Character.isWhitespace(text.charAt(start)))) start++;

        int end = start;
        while (end < text.length() && text.charAt(end) >= 32 && text.charAt(end) <= 126) end++;

        return (end > start) ? text.substring(start, end) : defaultValue;
    }

    private void parseLevelData(byte[] data) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            readCompound(buf, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int readCompound(ByteBuffer buf, int offset) throws Exception {
        int pos = offset;
        while (pos < buf.capacity()) {
            byte tag = buf.get(pos++);
            if (tag == 0) break; // TAG_END

            int nameLen = buf.getShort(pos) & 0xFFFF;
            pos += 2;
            byte[] nameBytes = new byte[nameLen * 2];
            buf.position(pos);
            buf.get(nameBytes);
            String tagName = new String(nameBytes, "UTF-16LE");
            pos += nameLen * 2;

            switch (tag) {
                case 1: // TAG_BYTE
                    pos += 1;
                    break;
                case 3: // TAG_INT
                    int intVal = buf.getInt(pos);
                    pos += 4;
                    if (tagName.equals("GameType")) gamemode = String.valueOf(intVal);
                    if (tagName.equals("XZSize")) seed = String.valueOf(intVal);
                    break;
                case 4: // TAG_LONG
                    long longVal = buf.getLong(pos);
                    pos += 8;
                    if (tagName.equals("RandomSeed")) seed = String.valueOf(longVal);
                    break;
                case 8: // TAG_STRING
                    int strLen = buf.getShort(pos) & 0xFFFF;
                    pos += 2;
                    byte[] strBytes = new byte[strLen * 2];
                    buf.position(pos);
                    buf.get(strBytes);
                    String strVal = new String(strBytes, "UTF-16LE");
                    pos += strLen * 2;
                    if (tagName.equals("LevelName")) name = strVal;
                    break;
                case 10: // TAG_COMPOUND
                    pos = readCompound(buf, pos);
                    break;
                default:
                    throw new Exception("Unknown tag type: " + tag);
            }
        }
        return pos;
    }

    private List<FileEntry> parseHeaderTable(byte[] decompressed) {
        List<FileEntry> files = new ArrayList<>();
        try {
            ByteBuffer buf = ByteBuffer.wrap(decompressed).order(ByteOrder.LITTLE_ENDIAN);
            int numFiles = buf.getInt(0);
            int offset = 4;

            for (int i = 0; i < numFiles; i++) {
                int nameLen = buf.getInt(offset);
                offset += 4;
                byte[] nameBytes = new byte[nameLen * 2];
                buf.position(offset);
                buf.get(nameBytes);
                offset += nameLen * 2;
                String name = new String(nameBytes, "UTF-16LE");

                int start = buf.getInt(offset);
                offset += 4;
                int length = buf.getInt(offset);
                offset += 4;

                files.add(new FileEntry(name, start, length));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return files;
    }

    private void populateFileInfo(Path folder) {
        try {
            Path saveFile = folder.resolve("saveData.ms");
            if (Files.exists(saveFile)) {
                long sizeBytes = Files.size(saveFile);
                size = String.format("%.2f MB", sizeBytes / 1024.0 / 1024.0);

                created = Files.getAttribute(folder, "creationTime").toString();
            }
        } catch (Exception e) {
            size = "0 MB";
            created = "Unknown";
        }
    }

    public String getName() { return name; }
    public String getGamemode() { return gamemode; }
    public String getSeed() { return seed; }
    public String getSize() { return size; }
    public String getCreated() { return created; }
    public Path getFolder() { return folder; }

    private static class FileEntry {
        String name;
        int startOffset;
        int length;
        FileEntry(String name, int startOffset, int length) {
            this.name = name;
            this.startOffset = startOffset;
            this.length = length;
        }
    }
}