package dev.lanboost.android;

import android.content.*;
import dev.lanboost.core.*;
import java.util.*;
import org.json.*;

/** Persistent per-profile catalogs and status; legacy user data is migrated only in memory. */
public final class NodeStore {
  private NodeStore() {}

  public static List<Candidate> read(Context context, Profile p) {
    SharedPreferences prefs = AppState.prefs(context);
    return decode(
        prefs.getString("nodes." + p.name(), "[]"),
        prefs.getString("url." + p.name(), p.url),
        prefs.getLong("tested." + p.name(), 0));
  }

  static List<Candidate> decode(String json, String legacyUrl, long legacyTime) {
    List<Candidate> nodes = new ArrayList<>();
    try {
      JSONArray arr = new JSONArray(json);
      for (int i = 0; i < arr.length(); i++) {
        try {
          JSONObject o = arr.getJSONObject(i);
          Candidate c = new Candidate(o.getString("ip"), o.optString("label"));
          c.mbps = o.optDouble("mbps", 0);
          c.latencyMs = o.optLong("latency", -1);
          c.bytes = o.optLong("bytes", 0);
          c.status = o.optString("status", "待测速");
          c.origin = o.optString("origin", c.label.startsWith("当前 DNS") ? "dns" : "saved");
          c.throughputAttempted = o.optBoolean("attempted", c.mbps > 0 || c.bytes > 0);
          c.testedUrl = o.optString("testedUrl", c.throughputAttempted ? legacyUrl : "");
          c.measuredAt = o.optLong("measuredAt", c.throughputAttempted ? legacyTime : 0);
          nodes.add(c);
        } catch (JSONException | IllegalArgumentException ignored) {
        }
      }
    } catch (JSONException ignored) {
    }
    return nodes;
  }

  static String encode(List<Candidate> nodes) {
    JSONArray arr = new JSONArray();
    for (Candidate c : nodes) {
      try {
        JSONObject o = new JSONObject();
        o.put("ip", c.ip);
        o.put("label", c.label);
        o.put("origin", c.origin);
        o.put("mbps", c.mbps);
        o.put("latency", c.latencyMs);
        o.put("bytes", c.bytes);
        o.put("status", c.status);
        o.put("attempted", c.throughputAttempted);
        o.put("testedUrl", c.testedUrl);
        o.put("measuredAt", c.measuredAt);
        arr.put(o);
      } catch (JSONException ignored) {
      }
    }
    return arr.toString();
  }

  public static void save(Context context, Profile p, List<Candidate> nodes) {
    AppState.prefs(context).edit().putString("nodes." + p.name(), encode(nodes)).apply();
  }

  public static List<Candidate> catalog(Context context, Profile p) {
    return decode(AppState.prefs(context).getString("catalog." + p.name(), "[]"), "", 0);
  }

  public static void success(
      Context context, Profile p, NodeCatalogFetcher.Result result, long now) {
    List<Candidate> merged =
        NodeLists.mergeCatalog(read(context, p), result.nodes, AppState.selected(context, p));
    AppState.prefs(context)
        .edit()
        .putString("nodes." + p.name(), encode(merged))
        .putString("catalog." + p.name(), encode(result.nodes))
        .putString("catalogSource." + p.name(), result.source)
        .putLong("catalogTime." + p.name(), now)
        .putString("fetchError." + p.name(), "")
        .putLong("fetchAttempt." + p.name(), now)
        .apply();
  }

  /** Preserve or restore the last complete catalog; never downgrade it to a DNS sample. */
  public static boolean failure(Context context, Profile p, String error, long now) {
    List<Candidate> cached = catalog(context, p);
    SharedPreferences.Editor edit =
        AppState.prefs(context)
            .edit()
            .putString("fetchError." + p.name(), error)
            .putLong("fetchAttempt." + p.name(), now);
    if (!cached.isEmpty())
      edit.putString(
          "nodes." + p.name(),
          encode(NodeLists.mergeCatalog(read(context, p), cached, AppState.selected(context, p))));
    edit.apply();
    return !cached.isEmpty();
  }

  public static void add(Context context, Profile p, List<Candidate> additions, String origin) {
    save(context, p, NodeLists.add(read(context, p), additions, origin));
  }

  public static String error(Context context, Profile p) {
    return AppState.prefs(context).getString("fetchError." + p.name(), "");
  }
}
