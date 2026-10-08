package com.xyron.game.launcher.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Safely installs the game-data portion of CRMP.zip into the app's game-data folder. */
public final class DataGtaArchiveInstaller {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final Set<String> GAME_ROOTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "anim", "audio", "data", "fonts", "models", "SAMP", "TEXT", "texdb"
    )));
    private static final Set<String> GAME_ROOT_FILES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "CINFO.BIN", "stream.ini"
    )));
    private static final Set<String> USER_FILES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "settings.ini", "settings.json", "server.ini", "favorites.json",
            "gtasatelem.set", "gta_sa.set", "gtasamp10.b", "samp_log.txt",
            "svlog.txt", "samplog.txt", "sampvoice.txt"
    )));

    private DataGtaArchiveInstaller() {
    }

    public interface ProgressListener {
        void onProgress(String label, long currentBytes, long totalBytes);
    }

    public static final class InstallResult {
        public final int installedFiles;
        public final int skippedFiles;
        public final long installedBytes;
        public final File backupDirectory;

        InstallResult(int installedFiles, int skippedFiles, long installedBytes, File backupDirectory) {
            this.installedFiles = installedFiles;
            this.skippedFiles = skippedFiles;
            this.installedBytes = installedBytes;
            this.backupDirectory = backupDirectory;
        }
    }

    public static InstallResult install(
            File archive,
            File targetDirectory,
            String expectedSha256,
            int gpuType,
            ProgressListener progressListener
    ) throws IOException {
        if (archive == null || !archive.isFile()) {
            throw new IOException("CRMP.zip tidak ditemukan.");
        }
        if (targetDirectory == null) {
            throw new IOException("Folder data game tidak tersedia.");
        }
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            throw new IOException("Tidak dapat membuat folder data game.");
        }
        if (!targetDirectory.isDirectory()) {
            throw new IOException("Tujuan pemasangan bukan folder.");
        }

        String expected = normalizeSha256(expectedSha256);
        if (expected.length() != 64) {
            throw new IOException("SHA-256 CRMP.zip tidak dikonfigurasi dengan benar.");
        }
        verifySha256(archive, expected, progressListener);

        File root = targetDirectory.getCanonicalFile();
        String rootPath = root.getPath();
        if (rootPath.equals(new File(rootPath).getParent())) {
            throw new IOException("Folder tujuan pemasangan tidak aman.");
        }

        ArrayList<ZipEntry> installEntries = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        long totalBytes = 0L;
        int skippedFiles = 0;
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String relativePath = normalizeArchivePath(entry.getName());
                if (relativePath == null || entry.isDirectory()) {
                    continue;
                }
                if (!isInstallableGamePath(relativePath, gpuType) || isUserOwnedFile(relativePath)) {
                    skippedFiles++;
                    continue;
                }
                if (!seenPaths.add(relativePath)) {
                    throw new IOException("Arsip memuat jalur duplikat: " + relativePath);
                }
                if (entry.getSize() < 0L) {
                    throw new IOException("Ukuran entri ZIP tidak diketahui: " + relativePath);
                }
                totalBytes += entry.getSize();
                installEntries.add(entry);
            }

            if (installEntries.isEmpty()) {
                throw new IOException("Arsip tidak memuat aset game yang dapat dipasang.");
            }

            File backupRoot = new File(root, ".game-data-backup-" + System.currentTimeMillis());
            ArrayList<File> installed = new ArrayList<>();
            ArrayList<Backup> backups = new ArrayList<>();
            boolean backupCreated = false;
            long copiedBytes = 0L;
            int installedCount = 0;
            try {
                for (ZipEntry entry : installEntries) {
                    String relativePath = normalizeArchivePath(entry.getName());
                    File destination = new File(root, relativePath);
                    File canonicalDestination = destination.getCanonicalFile();
                    if (!isWithinRoot(rootPath, canonicalDestination.getPath())) {
                        throw new IOException("Jalur tujuan keluar dari folder game: " + relativePath);
                    }
                    File parent = destination.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Tidak dapat membuat folder: " + parent);
                    }
                    if (destination.exists() && !destination.isFile()) {
                        throw new IOException("File tujuan bertabrakan dengan folder: " + relativePath);
                    }

                    File temporary = new File(parent, destination.getName() + ".datagta-tmp-" + System.nanoTime());
                    CRC32 crc = new CRC32();
                    long entryBytes = 0L;
                    long lastUpdate = 0L;
                    try (InputStream input = new BufferedInputStream(zip.getInputStream(entry));
                         OutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                            crc.update(buffer, 0, count);
                            entryBytes += count;
                            copiedBytes += count;
                            long now = System.currentTimeMillis();
                            if (progressListener != null && now - lastUpdate >= 150L) {
                                progressListener.onProgress("Mengekstrak " + relativePath, copiedBytes, totalBytes);
                                lastUpdate = now;
                            }
                        }
                    } catch (IOException e) {
                        temporary.delete();
                        throw e;
                    }

                    if (entryBytes != entry.getSize() || (entry.getCrc() >= 0L && crc.getValue() != entry.getCrc())) {
                        temporary.delete();
                        throw new IOException("Pemeriksaan isi ZIP gagal: " + relativePath);
                    }

                    if (destination.exists()) {
                        File saved = new File(backupRoot, relativePath);
                        File savedParent = saved.getParentFile();
                        if (savedParent != null && !savedParent.isDirectory() && !savedParent.mkdirs()) {
                            temporary.delete();
                            throw new IOException("Tidak dapat membuat folder cadangan.");
                        }
                        if (!moveFile(destination, saved)) {
                            temporary.delete();
                            throw new IOException("Tidak dapat mencadangkan file lama: " + relativePath);
                        }
                        backupCreated = true;
                        backups.add(new Backup(destination, saved));
                    }

                    if (!moveFile(temporary, destination)) {
                        temporary.delete();
                        throw new IOException("Tidak dapat memasang file: " + relativePath);
                    }
                    installed.add(destination);
                    installedCount++;
                    if (progressListener != null) {
                        progressListener.onProgress("Mengekstrak " + relativePath, copiedBytes, totalBytes);
                    }
                }
            } catch (IOException e) {
                rollback(installed, backups);
                throw e;
            }

            if (!backupCreated && backupRoot.exists()) {
                deleteRecursively(backupRoot);
            }
            if (progressListener != null) {
                progressListener.onProgress("Pemasangan selesai", totalBytes, totalBytes);
            }
            return new InstallResult(installedCount, skippedFiles, totalBytes, backupCreated ? backupRoot : null);
        }
    }

    private static void verifySha256(File archive, String expected, ProgressListener listener) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 tidak tersedia pada perangkat.", e);
        }
        long total = archive.length();
        long processed = 0L;
        long lastUpdate = 0L;
        try (InputStream input = new BufferedInputStream(new FileInputStream(archive))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
                processed += count;
                long now = System.currentTimeMillis();
                if (listener != null && now - lastUpdate >= 150L) {
                    listener.onProgress("Memverifikasi SHA-256", processed, total);
                    lastUpdate = now;
                }
            }
        }
        String actual = toHex(digest.digest());
        if (!actual.equals(expected)) {
            throw new IOException("SHA-256 CRMP.zip tidak cocok. Unduh ulang arsip sebelum memasang.");
        }
        if (listener != null) {
            listener.onProgress("SHA-256 cocok", total, total);
        }
    }

    private static String safeRelativePath(String rawName) throws IOException {
        if (rawName == null || rawName.isEmpty() || rawName.indexOf('\\') >= 0 || rawName.indexOf('\0') >= 0) {
            throw new IOException("Nama file ZIP tidak aman.");
        }
        if (rawName.startsWith("/") || rawName.matches("^[A-Za-z]:.*")) {
            throw new IOException("Jalur absolut tidak diizinkan dalam ZIP: " + rawName);
        }
        String[] parts = rawName.split("/", -1);
        ArrayList<String> safeParts = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (".".equals(part) || "..".equals(part) || part.indexOf(':') >= 0) {
                throw new IOException("Path traversal tidak diizinkan dalam ZIP: " + rawName);
            }
            safeParts.add(part);
        }
        if (safeParts.isEmpty()) {
            return null;
        }
        StringBuilder path = new StringBuilder();
        for (String part : safeParts) {
            if (path.length() > 0) {
                path.append('/');
            }
            path.append(part);
        }
        return path.toString();
    }

    private static String normalizeArchivePath(String rawName) throws IOException {
        String path = safeRelativePath(rawName);
        if (path == null || "files".equals(path)) {
            return null;
        }
        return path.startsWith("files/") ? path.substring("files/".length()) : path;
    }

    private static boolean isInstallableGamePath(String path, int gpuType) {
        int slash = path.indexOf('/');
        String root = slash < 0 ? path : path.substring(0, slash);
        if (!GAME_ROOTS.contains(root) && !GAME_ROOT_FILES.contains(root)) {
            return false;
        }
        String lowerPath = path.toLowerCase(Locale.US);
        if (lowerPath.contains("player") || lowerPath.contains("playerhi")
                || lowerPath.contains("menu") || lowerPath.contains("samp")) {
            return true;
        }
        if (lowerPath.contains(".dxt.") && gpuType != 1) {
            return false;
        }
        if (lowerPath.contains(".etc.") && gpuType != 2) {
            return false;
        }
        return !lowerPath.contains(".pvr.") || gpuType == 3;
    }

    private static boolean isUserOwnedFile(String path) {
        String lowerPath = path.toLowerCase(Locale.US);
        String baseName = lowerPath.substring(lowerPath.lastIndexOf('/') + 1);
        return baseName.endsWith(".log") || USER_FILES.contains(baseName);
    }

    private static boolean isWithinRoot(String root, String path) {
        return path.equals(root) || path.startsWith(root + File.separator);
    }

    private static String normalizeSha256(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (normalized.startsWith("sha256:")) {
            normalized = normalized.substring("sha256:".length());
        }
        return normalized;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static boolean moveFile(File source, File destination) {
        if (source.renameTo(destination)) {
            return true;
        }
        try (InputStream input = new BufferedInputStream(new FileInputStream(source));
             OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            if (!source.delete()) {
                destination.delete();
                return false;
            }
            return true;
        } catch (IOException e) {
            destination.delete();
            return false;
        }
    }

    private static void rollback(List<File> installed, List<Backup> backups) {
        for (int i = installed.size() - 1; i >= 0; i--) {
            installed.get(i).delete();
        }
        for (int i = backups.size() - 1; i >= 0; i--) {
            Backup backup = backups.get(i);
            File parent = backup.original.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            moveFile(backup.saved, backup.original);
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private static final class Backup {
        final File original;
        final File saved;

        Backup(File original, File saved) {
            this.original = original;
            this.saved = saved;
        }
    }
}
