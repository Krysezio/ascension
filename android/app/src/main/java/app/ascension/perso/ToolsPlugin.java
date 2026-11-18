package app.ascension.perso;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Outils natifs : sauvegarde dans Téléchargements/Ascension et mise à jour du widget. */
@CapacitorPlugin(name = "Tools")
public class ToolsPlugin extends Plugin {

    @PluginMethod
    public void saveBackup(PluginCall call) {
        final String name = call.getString("name", "ascension-sauvegarde.json");
        final String data = call.getString("data");
        if (data == null) { call.reject("Données manquantes"); return; }
        new Thread(() -> {
            try {
                byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
                JSObject r = new JSObject();
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentResolver cr = getContext().getContentResolver();
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Ascension");
                    Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new Exception("Impossible de créer le fichier");
                    try (OutputStream o = cr.openOutputStream(uri)) { o.write(bytes); }
                    r.put("path", "Téléchargements/Ascension/" + name);
                } else {
                    File dir = new File(getContext().getExternalFilesDir(null), "sauvegardes");
                    if (!dir.exists()) dir.mkdirs();
                    File f = new File(dir, name);
                    try (OutputStream o = new FileOutputStream(f)) { o.write(bytes); }
                    r.put("path", f.getAbsolutePath());
                }
                call.resolve(r);
            } catch (Exception e) {
                call.reject("Sauvegarde impossible : " + e.getMessage());
            }
        }).start();
    }

    @PluginMethod
    public void updateWidget(PluginCall call) {
        String data = call.getString("data", "");
        getContext().getSharedPreferences(AscensionWidget.PREFS, Context.MODE_PRIVATE).edit().putString("w", data).apply();
        AscensionWidget.refreshAll(getContext());
        call.resolve();
    }
}
