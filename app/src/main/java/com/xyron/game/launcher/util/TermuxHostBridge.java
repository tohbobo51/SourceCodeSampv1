package com.xyron.game.launcher.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public final class TermuxHostBridge {
    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";
    private static final String ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND";
    private static final String EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH";
    private static final String EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS";
    private static final String EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR";
    private static final String EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND";
    private static final String EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION";

    private TermuxHostBridge() {
    }

    public static boolean isTermuxInstalled(Context context) {
        if (context == null) {
            return false;
        }

        try {
            context.getPackageManager().getPackageInfo(TERMUX_PACKAGE, 0);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static LaunchStatus prepareAndStart(Context context) {
        if (context == null) {
            return LaunchStatus.failure("Konteks tidak valid.");
        }

        if (!isTermuxInstalled(context)) {
            return LaunchStatus.failure("Termux tidak terpasang di perangkat ini.");
        }

        boolean prepared = LocalHostManager.prepareSharedWorkspace(context);
        boolean selected = LocalHostManager.selectLoopbackServer(context);
        if (!prepared || !selected) {
            return LaunchStatus.failure("Tidak dapat menyiapkan paket lokal untuk Termux.");
        }

        return startSharedScript(
                context,
                LocalHostManager.getSharedTermuxScriptPath(),
                "Termux dipanggil untuk membuka host lokal. Jika ia menolak, periksa izin RUN_COMMAND dan allow-external-apps."
        );
    }

    public static LaunchStatus prepareAndStartPinggyUdp(Context context) {
        if (context == null) {
            return LaunchStatus.failure("Konteks tidak valid.");
        }

        if (!isTermuxInstalled(context)) {
            return LaunchStatus.failure("Termux tidak terpasang di perangkat ini.");
        }

        boolean prepared = LocalHostManager.prepareSharedWorkspace(context);
        if (!prepared) {
            return LaunchStatus.failure("Tidak dapat menyiapkan berkas akses jarak jauh.");
        }

        return startSharedScript(
                context,
                LocalHostManager.getSharedPinggyScriptPath(),
                "Termux dipanggil untuk membuka terowongan UDP Pinggy. Alamat publik akan muncul di layar Termux."
        );
    }

    public static Intent getTermuxLaunchIntent(Context context) {
        if (context == null) {
            return null;
        }

        PackageManager packageManager = context.getPackageManager();
        return packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE);
    }

    private static LaunchStatus startSharedScript(Context context, String scriptPath, String successMessage) {
        Intent intent = new Intent();
        intent.setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE);
        intent.setAction(ACTION_RUN_COMMAND);
        intent.putExtra(EXTRA_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash");
        intent.putExtra(EXTRA_ARGUMENTS, new String[]{
                "-lc",
                scriptPath
        });
        intent.putExtra(EXTRA_WORKDIR, "/data/data/com.termux/files/home");
        intent.putExtra(EXTRA_BACKGROUND, false);
        intent.putExtra(EXTRA_SESSION_ACTION, "0");

        try {
            context.startService(intent);
            return LaunchStatus.success(successMessage);
        } catch (SecurityException securityException) {
            return LaunchStatus.failure(
                    "Izin Termux diblokir. Berikan launcher izin tambahan untuk menjalankan perintah di Termux."
            );
        } catch (Exception exception) {
            return LaunchStatus.failure(
                    "Gagal memanggil Termux. Buka Termux, jalankan termux-setup-storage dan aktifkan allow-external-apps=true."
            );
        }
    }

    public static final class LaunchStatus {
        public final boolean success;
        public final String message;

        private LaunchStatus(boolean success, String message) {
            this.success = success;
            this.message = message == null ? "" : message;
        }

        public static LaunchStatus success(String message) {
            return new LaunchStatus(true, message);
        }

        public static LaunchStatus failure(String message) {
            return new LaunchStatus(false, message);
        }
    }
}
