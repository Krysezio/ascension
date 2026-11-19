package app.ascension.perso;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.provider.CalendarContract;
import com.getcapacitor.JSArray;
import com.getcapacitor.PermissionState;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
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
@CapacitorPlugin(name = "Tools", permissions = { @Permission(strings = { Manifest.permission.READ_CALENDAR }, alias = "calendar") })
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

    @PluginMethod
    public void calendarState(PluginCall call) {
        JSObject r = new JSObject();
        r.put("state", getPermissionState("calendar").toString());
        call.resolve(r);
    }

    @PluginMethod
    public void requestCalendar(PluginCall call) {
        if (getPermissionState("calendar") == PermissionState.GRANTED) { calendarState(call); return; }
        requestPermissionForAlias("calendar", call, "calendarPermCallback");
    }

    @PermissionCallback
    private void calendarPermCallback(PluginCall call) {
        calendarState(call);
    }

    /** Événements de l'agenda du téléphone (Google Agenda synchronisé compris) entre start et end (ms). */
    @PluginMethod
    public void calendarEvents(PluginCall call) {
        if (getPermissionState("calendar") != PermissionState.GRANTED) { call.reject("Accès à l'agenda non autorisé"); return; }
        long start = call.getLong("start", System.currentTimeMillis());
        long end = call.getLong("end", start + 86400000L);
        JSArray out = new JSArray();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, start);
        ContentUris.appendId(b, end);
        String[] cols = { CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_DISPLAY_NAME, CalendarContract.Instances.EVENT_LOCATION };
        try (Cursor c = getContext().getContentResolver().query(b.build(), cols, null, null, CalendarContract.Instances.BEGIN + " ASC")) {
            while (c != null && c.moveToNext()) {
                JSObject e = new JSObject();
                e.put("title", c.getString(0));
                e.put("begin", c.getLong(1));
                e.put("end", c.getLong(2));
                e.put("allDay", c.getInt(3) == 1);
                e.put("calendar", c.getString(4));
                e.put("location", c.getString(5));
                out.put(e);
            }
            JSObject r = new JSObject();
            r.put("events", out);
            call.resolve(r);
        } catch (Exception ex) {
            call.reject("Lecture de l'agenda impossible : " + ex.getMessage());
        }
    }
}
