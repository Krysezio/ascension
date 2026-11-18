package app.ascension.perso;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Widget écran d'accueil : socle du jour, série, phase. Les données sont écrites par l'appli. */
public class AscensionWidget extends AppWidgetProvider {
    static final String PREFS = "AscensionWidget";
    private static final int[] ROWS = {R.id.w_l1, R.id.w_l2, R.id.w_l3, R.id.w_l4, R.id.w_l5};

    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, AscensionWidget.class));
        for (int id : ids) update(c, m, id);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
    }

    static void update(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_ascension);
        for (int r : ROWS) v.setViewVisibility(r, View.GONE);
        String json = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("w", null);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(new Date());
        try {
            JSONObject o = (json == null || json.isEmpty()) ? null : new JSONObject(json);
            if (o == null) {
                v.setTextViewText(R.id.w_head, "ASCENSION");
                v.setTextViewText(R.id.w_streak, "");
                v.setTextViewText(R.id.w_sub, "Ouvre l'appli pour démarrer");
            } else if (!today.equals(o.optString("date"))) {
                v.setTextViewText(R.id.w_head, o.optString("head", "ASCENSION"));
                v.setTextViewText(R.id.w_streak, o.optString("streak", ""));
                v.setTextViewText(R.id.w_sub, "Nouvelle journée : ouvre Ascension");
            } else {
                v.setTextViewText(R.id.w_head, o.optString("head", "ASCENSION"));
                v.setTextViewText(R.id.w_streak, o.optString("streak", ""));
                v.setTextViewText(R.id.w_sub, o.optString("sub", ""));
                JSONArray items = o.optJSONArray("core");
                for (int i = 0; items != null && i < items.length() && i < ROWS.length; i++) {
                    JSONObject it = items.getJSONObject(i);
                    boolean done = it.optBoolean("d");
                    v.setViewVisibility(ROWS[i], View.VISIBLE);
                    v.setTextViewText(ROWS[i], (done ? "✓  " : "○  ") + it.optString("l"));
                    v.setTextColor(ROWS[i], done ? 0xFF8C7F62 : 0xFFF4EFE2);
                }
            }
        } catch (Exception e) {
            v.setTextViewText(R.id.w_sub, "Ouvre Ascension");
        }
        Intent i = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (i != null) {
            PendingIntent pi = PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.w_root, pi);
        }
        m.updateAppWidget(id, v);
    }
}
