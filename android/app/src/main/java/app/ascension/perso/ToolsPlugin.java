package app.ascension.perso;

import android.Manifest;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
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
@CapacitorPlugin(name = "Tools", permissions = { @Permission(strings = { Manifest.permission.READ_CALENDAR }, alias = "calendar"), @Permission(strings = { Manifest.permission.WRITE_CALENDAR }, alias = "calwrite") })
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
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_DISPLAY_NAME, CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.RRULE };
        try (Cursor c = getContext().getContentResolver().query(b.build(), cols, null, null, CalendarContract.Instances.BEGIN + " ASC")) {
            while (c != null && c.moveToNext()) {
                JSObject e = new JSObject();
                e.put("title", c.getString(0));
                e.put("begin", c.getLong(1));
                e.put("end", c.getLong(2));
                e.put("allDay", c.getInt(3) == 1);
                e.put("calendar", c.getString(4));
                e.put("location", c.getString(5));
                e.put("id", c.getLong(6));
                e.put("recurring", c.getString(7) != null && !c.getString(7).isEmpty());
                out.put(e);
            }
            JSObject r = new JSObject();
            r.put("events", out);
            call.resolve(r);
        } catch (Exception ex) {
            call.reject("Lecture de l'agenda impossible : " + ex.getMessage());
        }
    }

    // ---------- écriture dans l'agenda du téléphone (Google Agenda synchronisé) ----------
    @PluginMethod
    public void calWriteState(PluginCall call) {
        JSObject r = new JSObject();
        r.put("state", getPermissionState("calwrite").toString());
        call.resolve(r);
    }

    @PluginMethod
    public void requestCalWrite(PluginCall call) {
        if (getPermissionState("calwrite") == PermissionState.GRANTED) { calWriteState(call); return; }
        requestPermissionForAlias("calwrite", call, "calWritePermCallback");
    }

    @PermissionCallback
    private void calWritePermCallback(PluginCall call) {
        calWriteState(call);
    }

    /** Agenda cible : l'agenda principal Google modifiable, sinon le premier agenda modifiable visible. */
    private long targetCalendar() {
        String[] cols = { CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.IS_PRIMARY, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.VISIBLE };
        long best = -1; int bestScore = -1;
        try (Cursor c = getContext().getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, cols, null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (c.getInt(3) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue;
                int score = 0;
                if ("com.google".equals(c.getString(1))) score += 4;
                if (c.getInt(2) == 1) score += 2;
                if (c.getInt(4) == 1) score += 1;
                if (score > bestScore) { bestScore = score; best = c.getLong(0); }
            }
        } catch (Exception ignored) {}
        return best;
    }

    @PluginMethod
    public void createEvent(PluginCall call) {
        if (getPermissionState("calwrite") != PermissionState.GRANTED) { call.reject("Écriture dans l'agenda non autorisée"); return; }
        long cal = targetCalendar();
        if (cal < 0) { call.reject("Aucun agenda modifiable sur ce téléphone"); return; }
        try {
            ContentValues v = new ContentValues();
            v.put(CalendarContract.Events.CALENDAR_ID, cal);
            v.put(CalendarContract.Events.TITLE, call.getString("title", "Rendez-vous"));
            v.put(CalendarContract.Events.DTSTART, call.getLong("begin"));
            v.put(CalendarContract.Events.DTEND, call.getLong("end"));
            v.put(CalendarContract.Events.EVENT_LOCATION, call.getString("location", ""));
            v.put(CalendarContract.Events.DESCRIPTION, "Créé depuis Ascension");
            v.put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().getID());
            Uri u = getContext().getContentResolver().insert(CalendarContract.Events.CONTENT_URI, v);
            if (u == null) throw new Exception("insertion refusée");
            JSObject r = new JSObject();
            r.put("id", ContentUris.parseId(u));
            call.resolve(r);
        } catch (Exception e) { call.reject("Création impossible : " + e.getMessage()); }
    }

    @PluginMethod
    public void updateEvent(PluginCall call) {
        if (getPermissionState("calwrite") != PermissionState.GRANTED) { call.reject("Écriture dans l'agenda non autorisée"); return; }
        try {
            ContentValues v = new ContentValues();
            if (call.hasOption("title")) v.put(CalendarContract.Events.TITLE, call.getString("title"));
            if (call.hasOption("begin")) v.put(CalendarContract.Events.DTSTART, call.getLong("begin"));
            if (call.hasOption("end")) v.put(CalendarContract.Events.DTEND, call.getLong("end"));
            if (call.hasOption("location")) v.put(CalendarContract.Events.EVENT_LOCATION, call.getString("location"));
            Uri u = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, call.getLong("id"));
            int n = getContext().getContentResolver().update(u, v, null, null);
            JSObject r = new JSObject();
            r.put("updated", n);
            call.resolve(r);
        } catch (Exception e) { call.reject("Modification impossible : " + e.getMessage()); }
    }

    @PluginMethod
    public void deleteEvent(PluginCall call) {
        if (getPermissionState("calwrite") != PermissionState.GRANTED) { call.reject("Écriture dans l'agenda non autorisée"); return; }
        try {
            Uri u = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, call.getLong("id"));
            int n = getContext().getContentResolver().delete(u, null, null);
            JSObject r = new JSObject();
            r.put("deleted", n);
            call.resolve(r);
        } catch (Exception e) { call.reject("Suppression impossible : " + e.getMessage()); }
    }

    private static int authenticators() {
        return Build.VERSION.SDK_INT >= 30
                ? BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL
                : BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    }

    @PluginMethod
    public void canAuth(PluginCall call) {
        int r = BiometricManager.from(getContext()).canAuthenticate(authenticators());
        JSObject o = new JSObject();
        o.put("available", r == BiometricManager.BIOMETRIC_SUCCESS);
        o.put("code", r);
        call.resolve(o);
    }

    /** Empreinte, visage ou code du téléphone. */
    @PluginMethod
    public void authenticate(PluginCall call) {
        final String reason = call.getString("reason", "Déverrouiller Ascension");
        getActivity().runOnUiThread(() -> {
            try {
                BiometricPrompt bp = new BiometricPrompt((FragmentActivity) getActivity(), ContextCompat.getMainExecutor(getContext()),
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                                JSObject o = new JSObject();
                                o.put("ok", true);
                                call.resolve(o);
                            }

                            @Override
                            public void onAuthenticationError(int code, CharSequence msg) {
                                call.reject(String.valueOf(msg), String.valueOf(code));
                            }
                        });
                BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Ascension")
                        .setSubtitle(reason)
                        .setAllowedAuthenticators(authenticators())
                        .build();
                bp.authenticate(info);
            } catch (Exception e) {
                call.reject("Verrouillage indisponible : " + e.getMessage());
            }
        });
    }
}
