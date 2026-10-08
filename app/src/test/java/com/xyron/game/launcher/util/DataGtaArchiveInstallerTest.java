package com.xyron.game.launcher.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DataGtaArchiveInstallerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void installsGameAssetsButPreservesUserSettingsAndSkipsToolFolders() throws Exception {
        File archive = createZip(
                new String[]{"SAMP/main.scm", "TEXT/AMERICAN.GXT", "SAMP/settings.ini", "SAMP/cef.log", "XyronHost/logs/host.log", "install_datagta.py"},
                new String[]{"new script", "indonesian text", "archive settings", "archive log", "host log", "not a game file"}
        );
        File target = temporaryFolder.newFolder("game");
        write(new File(target, "SAMP/main.scm"), "old script");
        write(new File(target, "SAMP/settings.ini"), "user settings");

        DataGtaArchiveInstaller.InstallResult result = DataGtaArchiveInstaller.install(
                archive, target, sha256(archive), 1, null
        );

        assertEquals(2, result.installedFiles);
        assertEquals("new script", read(new File(target, "SAMP/main.scm")));
        assertEquals("indonesian text", read(new File(target, "TEXT/AMERICAN.GXT")));
        assertEquals("user settings", read(new File(target, "SAMP/settings.ini")));
        assertFalse(new File(target, "XyronHost").exists());
        assertNotNull(result.backupDirectory);
        assertEquals("old script", read(new File(result.backupDirectory, "SAMP/main.scm")));
    }

    @Test
    public void rejectsChecksumMismatchBeforeWritingFiles() throws Exception {
        File archive = createZip(new String[]{"SAMP/main.scm"}, new String[]{"payload"});
        File target = temporaryFolder.newFolder("game");

        try {
            DataGtaArchiveInstaller.install(archive, target, repeat('0', 64), 1, null);
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("SHA-256"));
            assertFalse(new File(target, "SAMP/main.scm").exists());
            return;
        }
        throw new AssertionError("Expected checksum mismatch to be rejected");
    }

    @Test
    public void rejectsPathTraversal() throws Exception {
        File archive = createZip(new String[]{"SAMP/../../outside.txt"}, new String[]{"payload"});
        File target = temporaryFolder.newFolder("game");

        try {
            DataGtaArchiveInstaller.install(archive, target, sha256(archive), 1, null);
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Path traversal"));
            assertFalse(new File(temporaryFolder.getRoot(), "outside.txt").exists());
            return;
        }
        throw new AssertionError("Expected path traversal to be rejected");
    }

    @Test
    public void installsOnlyTheDetectedTextureFormat() throws Exception {
        File archive = createZip(
                new String[]{"texdb/gta3/gta3.dxt.dat", "texdb/gta3/gta3.etc.dat", "texdb/gta3/gta3.pvr.dat"},
                new String[]{"dxt", "etc", "pvr"}
        );
        File target = temporaryFolder.newFolder("gpu-game");

        DataGtaArchiveInstaller.install(archive, target, sha256(archive), 1, null);

        assertTrue(new File(target, "texdb/gta3/gta3.dxt.dat").isFile());
        assertFalse(new File(target, "texdb/gta3/gta3.etc.dat").exists());
        assertFalse(new File(target, "texdb/gta3/gta3.pvr.dat").exists());
    }

    private File createZip(String[] names, String[] contents) throws Exception {
        File result = new File(temporaryFolder.getRoot(), "fixture-" + System.nanoTime() + ".zip");
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(result))) {
            for (int i = 0; i < names.length; i++) {
                output.putNextEntry(new ZipEntry(names[i]));
                output.write(contents[i].getBytes("UTF-8"));
                output.closeEntry();
            }
        }
        return result;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }

    private static void write(File file, String value) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes("UTF-8"));
        }
    }

    private static String read(File file) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
        return output.toString("UTF-8");
    }
}
