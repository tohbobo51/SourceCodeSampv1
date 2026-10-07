package com.xyron.game.launcher.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.xyron.game.R;
import com.xyron.game.launcher.MainActivity;
import com.xyron.game.launcher.util.ButtonAnimator;
import com.xyron.game.launcher.util.HostShellEngine;
import com.xyron.game.launcher.util.LocalHostManager;
import com.xyron.game.launcher.util.PinggyTunnelManager;
import com.xyron.game.launcher.util.TermuxHostBridge;

public class HostFragment extends Fragment {
    private static final long AUTO_REFRESH_INTERVAL_MS = 1200L;
    private static final String ACCESS_LOCAL = "local";
    private static final String ACCESS_LAN = "lan";
    private static final String ACCESS_REMOTE = "remote";
    private TextView statusBadge;
    private TextView statusTitle;
    private TextView statusBody;
    private TextView workspaceValue;
    private TextView loopbackValue;
    private TextView hostActionNote;
    private TextView hostJoinInfo;
    private TextView localAddressValue;
    private TextView lanAddressValue;
    private TextView remoteAccessValue;
    private TextView bootButton;
    private TextView stopButton;
    private TextView remoteTunnelButton;
    private View rootView;
    private volatile boolean hostActionInFlight;
    private volatile boolean remoteTunnelActionInFlight;
    private String selectedAccessMode = ACCESS_LOCAL;
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded() || rootView == null) {
                return;
            }
            refreshState();
            rootView.removeCallbacks(this);
            rootView.postDelayed(this, AUTO_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_host, container, false);
        rootView = root;

        statusBadge = root.findViewById(R.id.host_status_badge);
        statusTitle = root.findViewById(R.id.host_status_title);
        statusBody = root.findViewById(R.id.host_status_body);
        workspaceValue = root.findViewById(R.id.host_workspace_value);
        loopbackValue = root.findViewById(R.id.host_loopback_value);
        hostActionNote = root.findViewById(R.id.host_action_note);
        hostJoinInfo = root.findViewById(R.id.host_join_info);
        localAddressValue = root.findViewById(R.id.host_local_address_value);
        lanAddressValue = root.findViewById(R.id.host_lan_address_value);
        remoteAccessValue = root.findViewById(R.id.host_remote_access_value);
        bootButton = root.findViewById(R.id.button_boot_host);
        stopButton = root.findViewById(R.id.button_stop_local_host);
        remoteTunnelButton = root.findViewById(R.id.button_host_pinggy);

        bindAction(bootButton, this::bootLocalHostRuntime);
        bindAction(stopButton, this::stopLocalHostRuntime);
        bindAction(root.findViewById(R.id.host_local_access_option), this::useSameDeviceAccess);
        bindAction(root.findViewById(R.id.host_lan_access_option), this::useLanAccess);
        bindAction(root.findViewById(R.id.host_remote_access_option), this::startRemoteTunnel);
        bindAction(root.findViewById(R.id.button_host_copy_lan), this::useSameDeviceAccess);
        bindAction(root.findViewById(R.id.button_host_refresh_join), this::useLanAccess);
        bindAction(remoteTunnelButton, this::startRemoteTunnel);
        bindAction(root.findViewById(R.id.button_open_server_files), this::openFilesTab);

        refreshState();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshState();
        startAutoRefresh();
    }

    @Override
    public void onPause() {
        stopAutoRefresh();
        super.onPause();
    }

    private void bindAction(View button, Runnable action) {
        if (button == null || getContext() == null) {
            return;
        }
        button.setOnTouchListener(new ButtonAnimator(getContext(), button));
        button.setOnClickListener(v -> {
            if (!button.isEnabled()) {
                return;
            }
            action.run();
        });
    }

    private void bootLocalHostRuntime() {
        runHostAction(true);
    }

    private void stopLocalHostRuntime() {
        runHostAction(false);
    }

    private void runHostAction(boolean start) {
        if (getContext() == null) {
            return;
        }
        if (hostActionInFlight) {
            return;
        }

        boolean hostRunning = HostShellEngine.isHostRunning(requireContext());
        boolean hostStarting = HostShellEngine.isHostStarting(requireContext());
        if (start && (hostRunning || hostStarting)) {
            Toast.makeText(requireContext(), "Host sudah aktif atau sedang memulai.", Toast.LENGTH_SHORT).show();
            refreshState();
            return;
        }
        if (!start && !hostRunning && !hostStarting) {
            Toast.makeText(requireContext(), "Host sudah dimatikan.", Toast.LENGTH_SHORT).show();
            refreshState();
            return;
        }

        hostActionInFlight = true;
        refreshButtons(hostRunning, HostShellEngine.isHostReady(requireContext()), hostStarting);
        final Context appContext = requireContext().getApplicationContext();

        new Thread(() -> {
            HostShellEngine.CommandResult result = start
                    ? HostShellEngine.bootHost(appContext)
                    : HostShellEngine.execute(appContext, "host stop");
            if (!start) {
                PinggyTunnelManager.stopTunnel(appContext);
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    hostActionInFlight = false;
                    Toast.makeText(requireContext(), extractToastMessage(result), Toast.LENGTH_LONG).show();
                    refreshState();
                });
            }
        }, start ? "xyron-host-boot" : "xyron-host-stop").start();
    }

    private String extractToastMessage(HostShellEngine.CommandResult result) {
        if (result == null || TextUtils.isEmpty(result.output)) {
            return "Operasi host selesai.";
        }

        String output = result.output.trim();
        String[] lines = output.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || "Fluxo rapido do host".equalsIgnoreCase(line)
                    || "Proses penyiapan host".equalsIgnoreCase(line)) {
                continue;
            }
            String normalized = line.toLowerCase(java.util.Locale.US);
            if (normalized.contains("falha")
                    || normalized.contains("erro")
                    || normalized.contains("processo saiu")
                    || normalized.contains("proses keluar")
                    || normalized.contains("keluar segera")
                    || normalized.contains("gagal")
                    || normalized.contains("tidak berhasil")
                    || normalized.contains("tidak dapat")
                    || normalized.contains("tidak bisa")
                    || normalized.contains("nao foi possivel")
                    || normalized.contains("servidor pronto")
                    || normalized.contains("server siap di")) {
                return line;
            }
        }
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!line.isEmpty() && !"Fluxo rapido do host".equalsIgnoreCase(line)
                    && !"Proses penyiapan host".equalsIgnoreCase(line)) {
                return line;
            }
        }
        return output;
    }

    private void useSameDeviceAccess() {
        runAccessHostAction(ACCESS_LOCAL, false);
    }

    private void useLanAccess() {
        runAccessHostAction(ACCESS_LAN, true);
    }

    private void runAccessHostAction(String accessMode, boolean copyLanAfterStart) {
        if (getContext() == null) {
            return;
        }
        if (hostActionInFlight || remoteTunnelActionInFlight) {
            Toast.makeText(getContext(), "Tunggu aksi saat ini selesai.", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedAccessMode = accessMode;
        hostActionInFlight = true;
        refreshButtons(
                HostShellEngine.isHostRunning(requireContext()),
                HostShellEngine.isHostReady(requireContext()),
                HostShellEngine.isHostStarting(requireContext())
        );

        final Context appContext = requireContext().getApplicationContext();
        new Thread(() -> {
            PinggyTunnelManager.stopTunnel(appContext);

            boolean hostAlreadyAvailable = HostShellEngine.isHostReady(appContext)
                    || HostShellEngine.isHostRunning(appContext)
                    || HostShellEngine.isHostStarting(appContext);
            HostShellEngine.CommandResult result = hostAlreadyAvailable
                    ? HostShellEngine.CommandResult.success("Host lokal sudah menyala.", false, true)
                    : HostShellEngine.bootHost(appContext);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    hostActionInFlight = false;
                    if (result != null && result.success) {
                        if (copyLanAfterStart) {
                            copyLanAddress();
                        } else {
                            Toast.makeText(
                                    requireContext(),
                                    "Perangkat yang sama dipilih: " + LocalHostManager.getLoopbackAddress(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    } else {
                        Toast.makeText(requireContext(), extractToastMessage(result), Toast.LENGTH_LONG).show();
                    }
                    refreshState();
                });
            }
        }, ACCESS_LAN.equals(accessMode) ? "xyron-host-lan-mode" : "xyron-host-local-mode").start();
    }

    private void refreshJoinInfo() {
        if (getContext() == null) {
            return;
        }
        LocalHostManager.prepareSharedWorkspace(requireContext());
        String loopback = LocalHostManager.getLoopbackAddress();
        String lanIp = LocalHostManager.getBestLanAddress();
        String lanAddress = TextUtils.isEmpty(lanIp)
                ? "Hubungkan ke Wi-Fi atau hotspot"
                : lanIp + ":" + LocalHostManager.LOCAL_PORT;
        PinggyTunnelManager.TunnelState tunnelState = PinggyTunnelManager.getState(requireContext());
        boolean tunnelRunning = PinggyTunnelManager.isTunnelRunning(requireContext());
        boolean internalTunnelSupported = PinggyTunnelManager.isInternalTunnelSupported();
        boolean termuxReady = TermuxHostBridge.isTermuxInstalled(requireContext());
        String remoteMode;
        if (tunnelRunning && !TextUtils.isEmpty(tunnelState.publicUrl)) {
            remoteMode = tunnelState.publicUrl;
        } else if (tunnelRunning || tunnelState.isStarting()) {
            remoteMode = "Terowongan internal sedang dibuka lewat APK";
        } else if (tunnelState.isError()) {
            remoteMode = "Terowongan internal gagal. Ketuk untuk mencoba lagi";
        } else if (internalTunnelSupported) {
            remoteMode = "Siap di APK, tanpa Termux";
        } else if (termuxReady) {
            remoteMode = "Alternatif melalui Termux tersedia";
        } else {
            remoteMode = "Gunakan router atau Termux pada perangkat tanpa ARM64";
        }

        if (localAddressValue != null) {
            localAddressValue.setText(loopback);
        }
        if (lanAddressValue != null) {
            lanAddressValue.setText(lanAddress);
        }
        if (remoteAccessValue != null) {
            remoteAccessValue.setText(remoteMode);
        }
        if (hostJoinInfo != null) {
            StringBuilder helper = new StringBuilder();
            String effectiveMode = tunnelRunning ? ACCESS_REMOTE : selectedAccessMode;
            if (ACCESS_LAN.equals(effectiveMode)) {
                helper.append("Opsi yang dipilih: jaringan yang sama\n");
                helper.append("Bagikan: ").append(lanAddress).append("\n");
                helper.append("Tidak ada terowongan jarak jauh. Host hanya dapat diakses pada port 7777.");
            } else if (ACCESS_REMOTE.equals(effectiveMode)) {
                helper.append("Opsi yang dipilih: akses jarak jauh\n");
                if (tunnelRunning && !TextUtils.isEmpty(tunnelState.publicUrl)) {
                    helper.append("Bagikan: ").append(tunnelState.publicUrl).append("\n");
                    helper.append("Format siap untuk SA-MP: IP numerik + port.");
                } else if (internalTunnelSupported) {
                    helper.append("Ketuk Akses jarak jauh untuk membuka Pinggy UDP lewat APK.");
                } else {
                    helper.append("Buka port UDP 7777 di router atau gunakan alternatif melalui Termux.");
                }
            } else {
                helper.append("Opsi yang dipilih: perangkat yang sama\n");
                helper.append("Gunakan di ponsel Anda: ").append(loopback).append("\n");
                helper.append("Tanpa terowongan jarak jauh. Cocok untuk pengujian di perangkat sendiri.");
            }
            hostJoinInfo.setText(helper.toString());
        }
    }

    private void copyLanAddress() {
        if (getContext() == null) {
            return;
        }

        String lanIp = LocalHostManager.getBestLanAddress();
        if (TextUtils.isEmpty(lanIp)) {
            Toast.makeText(getContext(), "Hubungkan perangkat ke Wi-Fi atau hotspot untuk menghasilkan IP LAN.", Toast.LENGTH_SHORT).show();
            return;
        }

        String address = lanIp + ":" + LocalHostManager.LOCAL_PORT;
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("xyron-host-lan", address));
            Toast.makeText(getContext(), "Alamat LAN disalin: " + address, Toast.LENGTH_SHORT).show();
        }
    }

    private void startRemoteTunnel() {
        if (getContext() == null) {
            return;
        }

        selectedAccessMode = ACCESS_REMOTE;
        if (remoteTunnelActionInFlight) {
            Toast.makeText(getContext(), "Terowongan remote sedang dibuka.", Toast.LENGTH_SHORT).show();
            return;
        }

        PinggyTunnelManager.TunnelState tunnelState = PinggyTunnelManager.getState(requireContext());
        if (PinggyTunnelManager.isTunnelRunning(requireContext()) && !TextUtils.isEmpty(tunnelState.publicUrl)) {
            copyRemoteTunnelAddress();
            showRemoteAccessDialog(true);
            return;
        }

        final Context appContext = requireContext().getApplicationContext();
        remoteTunnelActionInFlight = true;
        refreshButtons(
                HostShellEngine.isHostRunning(requireContext()),
                HostShellEngine.isHostReady(requireContext()),
                HostShellEngine.isHostStarting(requireContext())
        );

        new Thread(() -> {
            PinggyTunnelManager.LaunchStatus tunnelStatus;
            if (!HostShellEngine.isHostReady(appContext)
                    && !HostShellEngine.isHostRunning(appContext)
                    && !HostShellEngine.isHostStarting(appContext)) {
                HostShellEngine.CommandResult bootResult = HostShellEngine.bootHost(appContext);
                if (bootResult == null || !bootResult.success) {
                    String message = extractToastMessage(bootResult);
                    tunnelStatus = PinggyTunnelManager.LaunchStatus.failure(
                            "Sebelum membuka terowongan, host perlu dinyalakan." + message
                    );
                } else {
                    tunnelStatus = PinggyTunnelManager.startTunnel(appContext);
                }
            } else {
                tunnelStatus = PinggyTunnelManager.startTunnel(appContext);
            }

            if (!tunnelStatus.success
                    && !PinggyTunnelManager.isInternalTunnelSupported()
                    && TermuxHostBridge.isTermuxInstalled(appContext)) {
                TermuxHostBridge.LaunchStatus termuxStatus =
                        TermuxHostBridge.prepareAndStartPinggyUdp(appContext);
                tunnelStatus = termuxStatus.success
                        ? PinggyTunnelManager.LaunchStatus.success(termuxStatus.message, "")
                        : PinggyTunnelManager.LaunchStatus.failure(termuxStatus.message);
            }

            PinggyTunnelManager.LaunchStatus finalStatus = tunnelStatus;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    remoteTunnelActionInFlight = false;
                    Toast.makeText(requireContext(), finalStatus.message, Toast.LENGTH_LONG).show();
                    refreshState();
                    showRemoteAccessDialog(finalStatus.success);
                });
            }
        }, "xyron-remote-tunnel").start();
    }

    private void copyRemoteTunnelAddress() {
        if (getContext() == null) {
            return;
        }

        String publicUrl = PinggyTunnelManager.getPublicUrl(requireContext());
        if (TextUtils.isEmpty(publicUrl)) {
            Toast.makeText(getContext(), "Alamat publik belum muncul.", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("xyron-host-tunnel", publicUrl));
            Toast.makeText(getContext(), "Terowongan disalin: " + publicUrl, Toast.LENGTH_SHORT).show();
        }
    }

    private void showRemoteAccessDialog(boolean tunnelStarted) {
        if (getContext() == null) {
            return;
        }

        PinggyTunnelManager.TunnelState tunnelState = PinggyTunnelManager.getState(requireContext());
        boolean internalTunnelSupported = PinggyTunnelManager.isInternalTunnelSupported();
        boolean termuxReady = TermuxHostBridge.isTermuxInstalled(requireContext());
        StringBuilder message = new StringBuilder();
        message.append(LocalHostManager.buildJoinInfo());
        message.append("\n\n");
        if (!TextUtils.isEmpty(tunnelState.publicUrl)) {
            message.append("Terowongan jarak jauh online lewat APK:\n");
            message.append(tunnelState.publicUrl);
            message.append("\n\nBagikan alamat ini dengan orang yang akan masuk melalui internet.");
        } else if (PinggyTunnelManager.isTunnelRunning(requireContext()) || tunnelState.isStarting()) {
            message.append("Mesin internal APK sedang membuka Pinggy UDP. Begitu alamat publik muncul, ia akan berada di panel ini.");
        } else if (tunnelState.isError()) {
            message.append(tunnelState.note);
            message.append("\n\nKetuk Buka terowongan jarak jauh untuk mencoba lagi.");
        } else if (internalTunnelSupported) {
            message.append(tunnelStarted
                    ? "Launcher mencoba membuka terowongan jarak jauh lewat APK. Jika jaringan SIM tidak stabil, ketuk lagi."
                    : "APK ini sudah memiliki mesin internal untuk membuka Pinggy UDP tanpa Termux.");
        } else if (termuxReady) {
            message.append("Perangkat ini belum mengizinkan ARM64 untuk mesin internal. Fallback lewat Termux masih dapat membuka Pinggy UDP.");
        } else {
            message.append("Perangkat ini belum mengizinkan ARM64 untuk mesin internal. Untuk internet, buka UDP 7777 di router atau gunakan Termux sebagai fallback.");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle("Akses jarak jauh")
                .setMessage(message.toString())
                .setNegativeButton("Tutup", null)
                .setNeutralButton("Salin IP LAN", (dialog, which) -> copyLanAddress());

        if (!TextUtils.isEmpty(tunnelState.publicUrl)) {
            builder.setPositiveButton("Salin terowongan", (dialog, which) -> copyRemoteTunnelAddress());
        } else if (termuxReady || internalTunnelSupported) {
            builder.setPositiveButton("Buka berkas", (dialog, which) -> openFilesTab());
        }

        builder.show();
    }

    private void openFilesTab() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openTab(MainActivity.TAB_HOST_FILES);
        }
    }

    private void refreshState() {
        if (getContext() == null) {
            return;
        }

        LocalHostManager.HostState state = LocalHostManager.getState(requireContext());
        boolean hostRunning = HostShellEngine.isHostRunning(requireContext());
        boolean hostReady = HostShellEngine.isHostReady(requireContext());
        boolean hostStarting = HostShellEngine.isHostStarting(requireContext());
        boolean hostErrored = HostShellEngine.isHostErrored(requireContext());
        String runtimeMessage = HostShellEngine.getHostStatusMessage(requireContext());
        if (workspaceValue != null) {
            String sharedPath = LocalHostManager.getSharedWorkspacePath();
            workspaceValue.setText(state.workspacePrepared
                    ? state.workspacePath + "\nUnduhan: " + sharedPath
                    : "Basis lokal belum disiapkan.");
        }

        if (loopbackValue != null) {
            String loopbackLabel;
            if (state.loopbackSelected) {
                loopbackLabel = state.loopbackAddress + " ativo no launcher";
            } else if (state.loopbackSaved) {
                loopbackLabel = state.loopbackAddress + " tersimpan, tapi tidak aktif";
            } else {
                loopbackLabel = state.loopbackAddress + " belum disimpan";
            }
            loopbackValue.setText(loopbackLabel);
        }

        if (statusTitle != null) {
            if (hostReady) {
                statusTitle.setText("Server lokal online");
            } else if (hostRunning || hostStarting) {
                statusTitle.setText("Runtime lokal sedang berjalan");
            } else if (hostErrored) {
                statusTitle.setText("Gagal menyalakan host");
            } else if (!state.workspacePrepared) {
                statusTitle.setText("Host lokal dalam persiapan");
            } else if (HostShellEngine.hasRuntimeCandidate(requireContext())) {
                statusTitle.setText("Runtime lokal ditemukan");
            } else {
                statusTitle.setText("Basis lokal siap");
            }
        }

        if (statusBadge != null) {
            if (hostReady) {
                statusBadge.setText("ONLINE");
            } else if (hostRunning || hostStarting) {
                statusBadge.setText("MEMULAI");
            } else if (hostErrored) {
                statusBadge.setText("GALAT");
            } else if (HostShellEngine.hasRuntimeCandidate(requireContext())) {
                statusBadge.setText("SIAP");
            } else {
                statusBadge.setText("BASIS");
            }
        }

        if (statusBody != null) {
            if (hostReady) {
                statusBody.setText("Host sudah berjalan di port 7777 dan sudah bisa menerima pemain lokal. Jika ingin mengundang orang dari luar jaringan, buka terowongan jarak jauh.");
            } else if (hostRunning || hostStarting) {
                if (TextUtils.isEmpty(runtimeMessage)) {
                    statusBody.setText("Runtime telah dimulai dan sedang menyelesaikan proses naiknya server lokal sekarang.");
                } else {
                    statusBody.setText(runtimeMessage);
                }
            } else if (hostErrored) {
                statusBody.setText(TextUtils.isEmpty(runtimeMessage)
                        ? "Host tidak berhasil dimulai. Ketuk Nyalakan host untuk mencoba menyiapkan basis lagi."
                        : runtimeMessage);
            } else if (!state.workspacePrepared) {
                statusBody.setText("Ketuk Nyalakan host untuk menyiapkan basis, mengaktifkan loopback dan menaikkan server lokal secara otomatis.");
            } else if (HostShellEngine.hasRuntimeCandidate(requireContext())) {
                statusBody.setText("Basis sudah dibuat dan runtime ARM ada di paket. Launcher sudah bisa menaikkan semuanya dengan satu ketukan.");
            } else {
                statusBody.setText("Struktur lokal sudah ada, tetapi build ini belum menyertakan runtime ARM yang kompatibel untuk menjalankan host.");
            }
        }

        if (hostActionNote != null) {
            if (hostActionInFlight) {
                hostActionNote.setText("Tunggu aksi saat ini selesai sebelum mengetuk lagi.");
            } else if (hostReady) {
                hostActionNote.setText("Host online. Tombol Nyalakan akan terkunci sampai Anda mematikan server.");
            } else if (hostRunning || hostStarting) {
                hostActionNote.setText("Server sudah menerima perintah start. Tunggu layar berubah menjadi status online.");
            } else if (hostErrored) {
                hostActionNote.setText("Upaya terakhir gagal. Ketuk Nyalakan host untuk memperbaiki tautan internal dan coba lagi.");
            } else {
                hostActionNote.setText("Alur cepat: nyalakan host, salin LAN ke jaringan yang sama dan gunakan terowongan jarak jauh untuk menguji lewat internet.");
            }
        }

        refreshButtons(hostRunning, hostReady, hostStarting);
        refreshJoinInfo();
    }

    private void startAutoRefresh() {
        if (rootView == null) {
            return;
        }
        rootView.removeCallbacks(autoRefreshRunnable);
        rootView.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL_MS);
    }

    private void stopAutoRefresh() {
        if (rootView != null) {
            rootView.removeCallbacks(autoRefreshRunnable);
        }
    }

    private void refreshButtons(boolean hostRunning, boolean hostReady, boolean hostStarting) {
        boolean hostBusyOrOnline = hostRunning || hostReady || hostStarting;
        boolean canStart = !hostActionInFlight && !hostBusyOrOnline;
        boolean canStop = !hostActionInFlight && hostBusyOrOnline;

        if (bootButton != null) {
            bootButton.setEnabled(canStart);
            bootButton.setAlpha(canStart ? 1f : 0.48f);
            if (hostActionInFlight && !hostBusyOrOnline) {
                bootButton.setText("Menyalakan host...");
            } else if (hostReady) {
                bootButton.setText("Host online");
            } else if (hostStarting || hostRunning) {
                bootButton.setText("Menyalakan host...");
            } else {
                bootButton.setText("Nyalakan host");
            }
        }

        if (stopButton != null) {
            stopButton.setEnabled(canStop);
            stopButton.setAlpha(canStop ? 1f : 0.48f);
            if (hostActionInFlight && hostBusyOrOnline) {
                stopButton.setText("Mematikan...");
            } else {
                stopButton.setText("Matikan host");
            }
        }

        if (remoteTunnelButton != null && getContext() != null) {
            PinggyTunnelManager.TunnelState tunnelState = PinggyTunnelManager.getState(requireContext());
            boolean tunnelRunning = PinggyTunnelManager.isTunnelRunning(requireContext());
            remoteTunnelButton.setEnabled(!remoteTunnelActionInFlight);
            remoteTunnelButton.setAlpha(remoteTunnelActionInFlight ? 0.56f : 1f);
            if (remoteTunnelActionInFlight) {
                remoteTunnelButton.setText("Membuka terowongan...");
            } else if (tunnelRunning && !TextUtils.isEmpty(tunnelState.publicUrl)) {
                remoteTunnelButton.setText("Salin jarak jauh");
            } else if (tunnelRunning || tunnelState.isStarting()) {
                remoteTunnelButton.setText("Membuka...");
            } else {
                remoteTunnelButton.setText("Akses jarak jauh");
            }
        }
    }
}
