package my.mrdhameri.steambattleship;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Self-update gate for GitHub-distributed sideloaded Android APKs.
 * The game starts normally if GitHub is unavailable or no update is published.
 * A newer APK is downloaded only with user consent. Android's system installer
 * always requires an additional explicit confirmation.
 */
@SuppressWarnings("deprecation")
public final class ApkUpdateManager {
    private static final String RELEASE_API =
        "https://api.github.com/repos/mohammaddhameri-blip/stem-battleship/releases/tags/android-latest";
    private static final String APK_URL =
        "https://github.com/mohammaddhameri-blip/stem-battleship/releases/download/android-latest/STEAM-Battleship.apk";
    private static final String APK_NAME = "STEAM-Battleship.apk";
    private static final int ALLOW_INSTALL_REQUEST = 4001;
    private static final int INSTALL_REQUEST = 4002;
    private static final long MAX_APK_BYTES = 80L * 1024L * 1024L;

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean launched = new AtomicBoolean(false);
    private Runnable startGame;
    private Update available;
    private File verifiedApk;
    private ProgressDialog progress;
    private boolean installing;

    private static final class Update {
        final int code;
        final String name;
        final String url;
        final String checksum;
        final long bytes;
        Update(int code, String name, String url, String checksum, long bytes) {
            this.code = code; this.name = name; this.url = url;
            this.checksum = checksum; this.bytes = bytes;
        }
    }

    public ApkUpdateManager(Activity activity) {
        this.activity = activity;
    }

    public void checkBeforePlay(Runnable onContinue) {
        startGame = onContinue;
        new Thread(() -> {
            Update release = null;
            try {
                release = readLatestRelease();
            } catch (Exception ignored) {
                // Offline, network timeouts and API rate limits never block the game.
            }
            final Update found = release;
            main.post(() -> {
                if (activity.isFinishing() || launched.get()) return;
                if (found != null && found.code > installedVersionCode()) {
                    available = found;
                    askToUpdate();
                } else {
                    continueToGame();
                }
            });
        }, "github-apk-update-check").start();
    }

    private long installedVersionCode() {
        try {
            PackageInfo info = activity.getPackageManager()
                .getPackageInfo(activity.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (PackageManager.NameNotFoundException ex) {
            return 0;
        }
    }

    private static String findNote(String body, String key) {
        Matcher match = Pattern.compile("(?m)^" + Pattern.quote(key) + "=([^\\r\\n]+)$")
            .matcher(body == null ? "" : body);
        return match.find() ? match.group(1).trim() : "";
    }

    private static HttpURLConnection open(String url, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "STEM-Battleship-Android");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        return conn;
    }

    private static byte[] readLimited(InputStream in, int maximum) throws Exception {
        java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) != -1) {
            if (result.size() + n > maximum) throw new java.io.IOException("Release response too large");
            result.write(buf, 0, n);
        }
        return result.toByteArray();
    }

    private Update readLatestRelease() throws Exception {
        HttpURLConnection conn = open(RELEASE_API, 4500);
        try {
            if (conn.getResponseCode() != 200) return null;
            JSONObject json;
            try (InputStream input = conn.getInputStream()) {
                json = new JSONObject(new String(readLimited(input, 96_000),
                    java.nio.charset.StandardCharsets.UTF_8));
            }
            // The workflow publishes the installed APK version in these notes
            // after uploading the new file. Do not compare timestamps or labels.
            String version = findNote(json.optString("body"), "APK_VERSION_CODE");
            if (!version.matches("[0-9]{1,9}")) return null;
            int code = Integer.parseInt(version);
            String name = findNote(json.optString("body"), "APK_VERSION_NAME");
            if (name.isEmpty()) name = String.valueOf(code);
            JSONArray assets = json.optJSONArray("assets");
            if (assets == null) return null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null || !APK_NAME.equals(asset.optString("name"))) continue;
                String url = asset.optString("browser_download_url");
                // Refuse altered URLs even when release metadata changes.
                if (!APK_URL.equals(url)) return null;
                String digest = asset.optString("digest");
                if (!digest.matches("sha256:[a-fA-F0-9]{64}")) return null;
                long bytes = asset.optLong("size", 0);
                if (bytes < 100_000 || bytes > MAX_APK_BYTES) return null;
                return new Update(code, name, url, digest.substring(7).toLowerCase(Locale.ROOT), bytes);
            }
        } finally {
            conn.disconnect();
        }
        return null;
    }

    private void askToUpdate() {
        Update next = available;
        if (next == null) { continueToGame(); return; }
        new AlertDialog.Builder(activity)
            .setTitle("STEM Battleship update available")
            .setMessage("Version " + next.name + " is ready on GitHub.\n\n" +
                "Download and install the update before playing? " +
                "Android will ask you to approve the installation.")
            .setPositiveButton("Update now", (dialog, button) -> downloadUpdate(next))
            .setNegativeButton("Play current version", (dialog, button) -> continueToGame())
            .setCancelable(false)
            .show();
    }

    private void downloadUpdate(Update next) {
        progress = new ProgressDialog(activity);
        progress.setTitle("Downloading game update");
        progress.setMessage("Connecting to GitHub…");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setCancelable(false);
        progress.show();
        new Thread(() -> {
            File apk = null;
            String failure = null;
            try {
                File folder = new File(activity.getCacheDir(), "updates");
                if (!folder.exists() && !folder.mkdirs())
                    throw new java.io.IOException("Cannot create update folder");
                apk = new File(folder, APK_NAME);
                if (apk.exists() && !apk.delete())
                    throw new java.io.IOException("Cannot replace old update");
                HttpURLConnection conn = open(next.url, 12_000);
                try {
                    if (conn.getResponseCode() != 200)
                        throw new java.io.IOException("GitHub download failed");
                    long total = 0;
                    byte[] block = new byte[16384]; int size;
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    int lastPercent = -1;
                    try (InputStream input = conn.getInputStream();
                         FileOutputStream output = new FileOutputStream(apk)) {
                        while ((size = input.read(block)) >= 0) {
                            if (size == 0) continue;
                            total += size;
                            if (total > MAX_APK_BYTES || total > next.bytes)
                                throw new java.io.IOException("APK exceeds expected size");
                            output.write(block, 0, size);
                            md.update(block, 0, size);
                            int percent = (int) (total * 100L / next.bytes);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final int p = percent;
                                main.post(() -> { if (progress != null) {
                                    progress.setProgress(p);
                                    progress.setMessage(p + "% downloaded");
                                }});
                            }
                        }
                    }
                    if (total != next.bytes)
                        throw new java.io.IOException("Incomplete download");
                    byte[] digest = md.digest();
                    StringBuilder hex = new StringBuilder();
                    for (byte c : digest) hex.append(String.format(Locale.ROOT, "%02x", c & 0xff));
                    if (!next.checksum.equals(hex.toString()))
                        throw new SecurityException("APK checksum does not match GitHub");
                } finally {
                    conn.disconnect();
                }
                // Android also verifies the package signature when updating.
                PackageInfo packageInfo = activity.getPackageManager()
                    .getPackageArchiveInfo(apk.getAbsolutePath(), 0);
                if (packageInfo == null ||
                    !activity.getPackageName().equals(packageInfo.packageName) ||
                    (Build.VERSION.SDK_INT >= 28 ?
                        packageInfo.getLongVersionCode() : packageInfo.versionCode) != next.code)
                    throw new SecurityException("APK package or version mismatch");
            } catch (Exception error) {
                failure = error.getMessage() == null ? "Download failed" : error.getMessage();
                if (apk != null) apk.delete();
                apk = null;
            }
            final File readyApk = apk;
            final String error = failure;
            main.post(() -> {
                if (progress != null) { progress.dismiss(); progress = null; }
                if (activity.isFinishing() || launched.get()) return;
                if (readyApk == null) showRetry("Could not download a verified update. " + error);
                else { verifiedApk = readyApk; requestInstall(); }
            });
        }, "github-apk-download").start();
    }

    private void requestInstall() {
        if (verifiedApk == null || available == null || !verifiedApk.isFile()) {
            showRetry("The downloaded APK is no longer available.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 &&
            !activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity)
                .setTitle("Allow Android installation")
                .setMessage("To update STEM Battleship, enable 'Allow from this source' " +
                    "for the game. Return here to continue installation.")
                .setPositiveButton("Open settings", (d, which) -> {
                    try {
                        activity.startActivityForResult(new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName())),
                            ALLOW_INSTALL_REQUEST);
                    } catch (Exception ex) {
                        showRetry("Android could not open app-install permissions.");
                    }
                })
                .setNegativeButton("Play current version", (d, which) -> continueToGame())
                .setCancelable(false).show();
            return;
        }
        try {
            Uri contentUri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".updates", verifiedApk);
            Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            install.setDataAndType(contentUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            installing = true;
            activity.startActivityForResult(install, INSTALL_REQUEST);
        } catch (Exception ex) {
            installing = false;
            showRetry("Unable to start Android's APK installer.");
        }
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ALLOW_INSTALL_REQUEST) {
            if (Build.VERSION.SDK_INT < 26 ||
                activity.getPackageManager().canRequestPackageInstalls()) {
                requestInstall();
            } else {
                showRetry("Android did not grant permission to install updates from this app.");
            }
            return true;
        }
        if (requestCode == INSTALL_REQUEST) {
            installing = false;
            // Android's installer may report success before the package manager
            // shows the new version in this still-running process.
            int installStatus = data == null ? 0 :
                data.getIntExtra(Intent.EXTRA_INSTALL_RESULT, 0);
            if (resultCode == Activity.RESULT_OK ||
                installedVersionCode() >= (available == null ? Long.MAX_VALUE : available.code)) {
                main.postDelayed(() -> {
                    if (activity.isFinishing()) return;
                    Intent launch = activity.getPackageManager()
                        .getLaunchIntentForPackage(activity.getPackageName());
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        activity.startActivity(launch);
                        activity.finish();
                    } else continueToGame();
                }, 700);
            } else {
                String reason;
                if (installStatus == -7) {
                    reason = "Android rejected the update because its signing certificate " +
                        "is different from the installed app. This happened in earlier APK " +
                        "builds; back up your ship designs before reinstalling.";
                } else if (installStatus == -4) {
                    reason = "Android reports insufficient storage. Free some space and retry.";
                } else if (installStatus == -25) {
                    reason = "Android rejected a version downgrade.";
                } else if (installStatus == -2 || installStatus == -3) {
                    reason = "Android rejected the APK file. Please download a fresh copy.";
                } else if (installStatus != 0) {
                    reason = "Android rejected installation (result " + installStatus + ").";
                } else {
                    reason = "Android cancelled or blocked the installation. Confirm any " +
                        "system security prompts and verify that updates from STEM Battleship " +
                        "are allowed. If you previously installed an APK with a different " +
                        "signing certificate, Android cannot update it in place.";
                }
                showRetry(reason);
            }
            return true;
        }
        return false;
    }

    private void showRetry(String message) {
        if (launched.get() || activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
            .setTitle("Game update")
            .setMessage(message)
            .setPositiveButton(verifiedApk != null ? "Retry installation" : "Retry download",
                (d, button) -> {
                    if (verifiedApk != null && verifiedApk.isFile()) requestInstall();
                    else if (available != null) downloadUpdate(available);
                    else continueToGame();
                })
            .setNegativeButton("Play current version", (d, button) -> continueToGame())
            .setCancelable(false)
            .show();
    }

    private void continueToGame() {
        if (launched.compareAndSet(false, true) && !activity.isFinishing() && startGame != null) {
            startGame.run();
        }
    }
}
