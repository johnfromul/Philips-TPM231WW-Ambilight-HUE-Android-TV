package local.tv.hue;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AppUpdates {
    private static final String REPO = "johnfromul/Philips-TPM231WW-Ambilight-HUE-Android-TV";
    private static final String API = "https://api.github.com/repos/" + REPO;
    private static final String DOWNLOAD = "https://github.com/" + REPO + "/releases/download/";
    private static final int LIMIT = 64 * 1024 * 1024;
    private static final Pattern VERSION = Pattern.compile(
            "^v?(\\d+(?:\\.\\d+)*)([a-z]?)(?:_.*)?$", Pattern.CASE_INSENSITIVE);
    private static boolean busy;

    static String label(Context activity, String key) {
        String[] values;
        switch (key) {
            case "check": values = new String[]{"Zkontrolovat aktualizace", "Check for updates", "Nach Updates suchen", "Rechercher des mises à jour", "Buscar actualizaciones", "Sprawdź aktualizacje"}; break;
            case "current": values = new String[]{"Vaše verze této aplikace je aktuální.", "Your version of this app is up to date.", "Ihre App-Version ist aktuell.", "Votre version de l’application est à jour.", "Tu versión de la aplicación está actualizada.", "Masz aktualną wersję aplikacji."}; break;
            case "available": values = new String[]{"Je dostupná verze %s. Chcete ji stáhnout a nainstalovat? Vaše nastavení zůstane uložené.", "Version %s is available. Download and install it? Your settings will be preserved.", "Version %s ist verfügbar. Möchten Sie sie herunterladen und installieren? Ihre Einstellungen bleiben erhalten.", "La version %s est disponible. Voulez-vous la télécharger et l’installer ? Vos réglages seront conservés.", "La versión %s está disponible. ¿Quieres descargarla e instalarla? Se conservarán tus ajustes.", "Dostępna jest wersja %s. Pobrać i zainstalować? Twoje ustawienia zostaną zachowane."}; break;
            case "install": values = new String[]{"Stáhnout a aktualizovat", "Download and update", "Herunterladen und aktualisieren", "Télécharger et mettre à jour", "Descargar y actualizar", "Pobierz i zaktualizuj"}; break;
            case "permission": values = new String[]{"Povolte této aplikaci instalaci z neznámého zdroje a potom znovu zkontrolujte aktualizace.", "Allow this app to install from this source, then check for updates again.", "Erlauben Sie Installationen aus dieser Quelle und suchen Sie erneut nach Updates.", "Autorisez l’installation depuis cette source, puis recherchez à nouveau les mises à jour.", "Permite instalar desde esta fuente y vuelve a buscar actualizaciones.", "Zezwól na instalowanie z tego źródła i ponownie sprawdź aktualizacje."}; break;
            case "checking": values = new String[]{"Kontroluji vydání na GitHubu…", "Checking GitHub releases…", "GitHub-Veröffentlichungen werden geprüft…", "Recherche de versions sur GitHub…", "Consultando las versiones en GitHub…", "Sprawdzanie wersji w GitHub…"}; break;
            case "downloading": values = new String[]{"Stahuji a ověřuji aktualizaci…", "Downloading and verifying the update…", "Das Update wird heruntergeladen und geprüft…", "Téléchargement et vérification de la mise à jour…", "Descargando y verificando la actualización…", "Pobieranie i weryfikowanie aktualizacji…"}; break;
            default: values = new String[]{"Aktualizaci se nepodařilo ověřit nebo zahájit.", "Could not verify or start the update.", "Das Update konnte nicht geprüft oder gestartet werden.", "Impossible de vérifier ou de lancer la mise à jour.", "No se pudo verificar o iniciar la actualización.", "Nie udało się zweryfikować ani rozpocząć aktualizacji."};
        }
        switch (activity.getResources().getConfiguration().getLocales().get(0).getLanguage()) {
            case "cs": return values[0];
            case "de": return values[2];
            case "fr": return values[3];
            case "es": return values[4];
            case "pl": return values[5];
            default: return values[1];
        }
    }

    static void check(Activity activity) {
        run(activity, "checking", () -> {
            PackageInfo installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            JSONObject release = json(API + "/releases/latest");
            String tag = release.getString("tag_name");
            if (!release.getBoolean("draft") && !release.getBoolean("prerelease")
                    && compare(tag, installed.versionName) > 0) {
                String status = json(API + "/compare/" + Uri.encode(tag) + "...main").getString("status");
                if ("identical".equals(status) || "ahead".equals(status)) {
                    String asset = apkAsset(release.getJSONArray("assets"));
                    return () -> new AlertDialog.Builder(activity)
                            .setMessage(String.format(label(activity, "available"), tag))
                            .setNegativeButton(Strings.get(activity, "cancel"), null)
                            .setPositiveButton(label(activity, "install"), (d, w) -> install(activity, asset))
                            .show();
                }
            }
            return () -> message(activity, "current");
        });
    }

    private static String apkAsset(JSONArray assets) throws Exception {
        for (int i = 0; i < assets.length(); i++) {
            JSONObject item = assets.getJSONObject(i);
            String name = item.getString("name");
            String url = item.getString("browser_download_url");
            if (name.matches("(?i)ambilight[-_]hue[-_].*\\.apk") && url.startsWith(DOWNLOAD)) return url;
        }
        throw new IOException("Release contains no app APK");
    }

    static int compare(String first, String second) throws IOException {
        Matcher left = VERSION.matcher(first), right = VERSION.matcher(second);
        if (!left.matches() || !right.matches()) throw new IOException("Unsupported release version");
        String[] a = left.group(1).split("\\."), b = right.group(1).split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int result = Integer.compare(i < a.length ? Integer.parseInt(a[i]) : 0,
                    i < b.length ? Integer.parseInt(b[i]) : 0);
            if (result != 0) return result;
        }
        return left.group(2).compareToIgnoreCase(right.group(2));
    }

    private static void install(Activity activity, String asset) {
        PackageManager manager = activity.getPackageManager();
        if (!manager.canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity).setMessage(label(activity, "permission"))
                    .setNegativeButton(Strings.get(activity, "cancel"), null)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        try {
                            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName())));
                        } catch (RuntimeException unavailable) {
                            message(activity, "error");
                        }
                    }).show();
            return;
        }
        run(activity, "downloading", () -> {
            File apk = File.createTempFile("hue-update-", ".apk", activity.getCacheDir());
            try {
                try (OutputStream output = new FileOutputStream(apk)) { fetch(asset, output, LIMIT); }
                int flags = PackageManager.GET_SIGNING_CERTIFICATES;
                PackageInfo installed = manager.getPackageInfo(activity.getPackageName(), flags);
                PackageInfo candidate = manager.getPackageArchiveInfo(apk.getPath(), flags);
                if (candidate == null || !installed.packageName.equals(candidate.packageName)
                        || candidate.getLongVersionCode() < installed.getLongVersionCode()
                        || compare(candidate.versionName, installed.versionName) <= 0
                        || !Arrays.equals(candidate.signingInfo.getApkContentsSigners(),
                                installed.signingInfo.getApkContentsSigners())) {
                    throw new IOException("APK identity, signature or version mismatch");
                }
                submit(activity, apk);
            } finally {
                apk.delete();
            }
            return () -> {};
        });
    }

    private static void submit(Activity activity, File apk) throws IOException {
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(activity.getPackageName());
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        }
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream input = new FileInputStream(apk);
                    OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                transfer(input, output, LIMIT);
                session.fsync(output);
            }
            Intent callback = new Intent(activity, UpdateInstallReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            session.commit(PendingIntent.getBroadcast(activity, id, callback, flags).getIntentSender());
        } catch (IOException | RuntimeException error) {
            installer.abandonSession(id);
            throw error;
        }
    }

    private static JSONObject json(String url) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        fetch(url, output, 2 * 1024 * 1024);
        return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
    }

    private static void fetch(String address, OutputStream output, long limit) throws IOException {
        URL url = new URL(address);
        for (int redirects = 0; redirects < 5; redirects++) {
            if (!"https".equals(url.getProtocol()) || !(url.getHost().equals("api.github.com")
                    || url.getHost().equals("github.com") || url.getHost().endsWith(".githubusercontent.com"))) {
                throw new IOException("Unexpected GitHub host");
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Ambilight-Hue-Android-TV");
            try {
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Missing redirect");
                    url = new URL(url, location);
                    continue;
                }
                if (code != 200) throw new IOException("GitHub HTTP " + code);
                try (InputStream input = connection.getInputStream()) { transfer(input, output, limit); }
                return;
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many redirects");
    }

    private static void transfer(InputStream input, OutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[32768];
        int read;
        while ((read = input.read(buffer)) != -1) {
            limit -= read;
            if (limit < 0) throw new IOException("Response too large");
            output.write(buffer, 0, read);
        }
    }

    private interface Work { Runnable execute() throws Exception; }

    private static void run(Activity activity, String stage, Work work) {
        if (busy) return;
        busy = true;
        AlertDialog progress = new AlertDialog.Builder(activity).setMessage(label(activity, stage))
                .setCancelable(false).show();
        new Thread(() -> {
            Runnable result;
            try { result = work.execute(); }
            catch (Exception error) {
                Diagnostics.event(Diagnostics.id(), "update.error", error.getClass().getSimpleName());
                result = () -> message(activity, "error");
            }
            Runnable show = result;
            activity.runOnUiThread(() -> {
                busy = false;
                progress.dismiss();
                if (activity.isFinishing() || activity.isDestroyed()) return;
                show.run();
            });
        }, "app-update").start();
    }

    private static void message(Activity activity, String code) {
        new AlertDialog.Builder(activity).setMessage(label(activity, code))
                .setPositiveButton(android.R.string.ok, null).show();
    }
}
