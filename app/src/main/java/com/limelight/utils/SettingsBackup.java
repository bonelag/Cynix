package com.limelight.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.limelight.GameMenu;
import com.limelight.binding.input.virtual_controller.GamepadLayoutManager;
import com.limelight.binding.input.virtual_controller.keyboard.KeyboardLayoutManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unified, section-selectable configuration backup for Cynix.
 *
 * <p>One .json file can carry up to four independent sections, each only emitted
 * when the caller asks for it (export) or detected from the file (import):
 * <ul>
 *   <li><b>Basic settings</b> &mdash; the default {@link SharedPreferences} store.
 *       Values are stored in a typed envelope {@code {"type","value"}} so that
 *       Boolean/Int/Float/Long/String/StringSet round-trip without being coerced
 *       to String (the legacy single-layout importer in StreamSettings does
 *       putString-only and would corrupt the basic store).</li>
 *   <li><b>Gamepad layouts</b> &mdash; all per-app gamepad layouts (meta + each
 *       layout's element-config file).</li>
 *   <li><b>Keyboard layouts</b> &mdash; all per-app special-key layouts.</li>
 *   <li><b>Special keys</b> &mdash; the raw custom-shortcut JSON stored in
 *       {@code specialPrefs/special_key}.</li>
 * </ul>
 *
 * <p>Import is <b>Replace</b>: each selected section present in the file is wiped
 * locally and then loaded from the file. For layouts, IDs are regenerated
 * (via the layout managers' own {@code createLayout}) so a backup restores
 * cleanly across devices; only names, content, and the active-layout identity
 * are preserved.
 */
public final class SettingsBackup {

    /** Schema version. Bump if the on-disk format changes incompatibly. */
    private static final int SCHEMA = 1;

    private static final String K_SCHEMA = "schema";
    private static final String K_APP = "app";
    private static final String K_CREATED_AT = "createdAt";
    private static final String K_BASIC = "basic";
    private static final String K_GAMEPAD = "gamepad";
    private static final String K_KEYBOARD = "keyboard";
    private static final String K_SPECIAL = "specialKeys";

    private static final String K_TYPE = "type";
    private static final String K_VALUE = "value";

    private static final String K_ACTIVE = "active";
    private static final String K_LAYOUTS = "layouts";
    private static final String K_LAYOUT_ID = "id";
    private static final String K_LAYOUT_NAME = "name";
    private static final String K_LAYOUT_PREFS = "prefs";

    private static final String T_BOOLEAN = "boolean";
    private static final String T_INT = "int";
    private static final String T_FLOAT = "float";
    private static final String T_LONG = "long";
    private static final String T_STRING = "string";
    private static final String T_STRING_SET = "stringSet";

    private SettingsBackup() {}

    // ----------------------------------------------------------------------
    // Export
    // ----------------------------------------------------------------------

    /**
     * Build the export payload as a pretty-printed JSON string. Only requested
     * sections are emitted; the others are omitted entirely.
     */
    public static String exportToString(Context ctx,
                                        boolean exportBasic,
                                        boolean exportGamepad,
                                        boolean exportKeyboard,
                                        boolean exportSpecial) {
        JSONObject root = new JSONObject();
        try {
            root.put(K_SCHEMA, SCHEMA);
            root.put(K_APP, "Cynix");
            root.put(K_CREATED_AT, System.currentTimeMillis());

            if (exportBasic) {
                root.put(K_BASIC, dumpBasic(ctx));
            }
            if (exportGamepad) {
                root.put(K_GAMEPAD, dumpLayouts(ctx, new GamepadLayoutManager(ctx)));
            }
            if (exportKeyboard) {
                root.put(K_KEYBOARD, dumpLayouts(ctx, new KeyboardLayoutManager(ctx)));
            }
            if (exportSpecial) {
                String raw = ctx.getSharedPreferences(GameMenu.PREF_NAME, Activity.MODE_PRIVATE)
                        .getString(GameMenu.KEY_NAME, "");
                // Keep the key present so import always has a target, even if empty.
                root.put(K_SPECIAL, raw != null ? raw : "");
            }
        } catch (JSONException e) {
            throw new RuntimeException("Failed to serialize configuration", e);
        }
        return root.toString();
    }

    private static JSONObject dumpBasic(Context ctx) throws JSONException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        Map<String, ?> all = prefs.getAll();
        JSONObject out = new JSONObject();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            JSONObject entry = encodeTyped(e.getValue());
            if (entry != null) {
                out.put(e.getKey(), entry);
            }
        }
        return out;
    }

    /**
     * Shared layout-dump for gamepad and keyboard managers (identical storage shape).
     */
    private static JSONObject dumpLayouts(Context ctx,
                                         Object manager) throws JSONException {
        List<String> ids;
        String active;
        if (manager instanceof GamepadLayoutManager) {
            ids = ((GamepadLayoutManager) manager).getLayoutIds();
            active = ((GamepadLayoutManager) manager).getActiveLayoutId();
        } else {
            ids = ((KeyboardLayoutManager) manager).getLayoutIds();
            active = ((KeyboardLayoutManager) manager).getActiveLayoutId();
        }

        JSONObject section = new JSONObject();
        section.put(K_ACTIVE, active);
        JSONArray layouts = new JSONArray();
        for (String id : ids) {
            String name = (manager instanceof GamepadLayoutManager)
                    ? ((GamepadLayoutManager) manager).getLayoutName(id)
                    : ((KeyboardLayoutManager) manager).getLayoutName(id);

            String prefName = (manager instanceof GamepadLayoutManager)
                    ? GamepadLayoutManager.getLayoutPreferenceName(id)
                    : KeyboardLayoutManager.getLayoutPreferenceName(id);

            SharedPreferences lp = ctx.getSharedPreferences(prefName, Activity.MODE_PRIVATE);
            JSONObject layoutObj = new JSONObject();
            layoutObj.put(K_LAYOUT_ID, id);
            layoutObj.put(K_LAYOUT_NAME, name);
            layoutObj.put(K_LAYOUT_PREFS, dumpStringPrefs(lp.getAll()));
            layouts.put(layoutObj);
        }
        section.put(K_LAYOUTS, layouts);
        return section;
    }

    private static JSONObject dumpStringPrefs(Map<String, ?> all) throws JSONException {
        JSONObject out = new JSONObject();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String) {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    /**
     * Wrap a single preference value in a {@code {"type","value"}} envelope.
     * Returns {@code null} for unsupported types.
     */
    private static JSONObject encodeTyped(Object v) throws JSONException {
        JSONObject entry = new JSONObject();
        if (v instanceof Boolean) {
            entry.put(K_TYPE, T_BOOLEAN);
            entry.put(K_VALUE, (Boolean) v);
        } else if (v instanceof Float) {
            entry.put(K_TYPE, T_FLOAT);
            entry.put(K_VALUE, ((Number) v).doubleValue());
        } else if (v instanceof Integer) {
            entry.put(K_TYPE, T_INT);
            entry.put(K_VALUE, (Integer) v);
        } else if (v instanceof Long) {
            entry.put(K_TYPE, T_LONG);
            entry.put(K_VALUE, ((Long) v).doubleValue());
        } else if (v instanceof String) {
            entry.put(K_TYPE, T_STRING);
            entry.put(K_VALUE, (String) v);
        } else if (v instanceof Set) {
            entry.put(K_TYPE, T_STRING_SET);
            JSONArray arr = new JSONArray();
            for (Object s : (Set<?>) v) {
                arr.put(s != null ? s.toString() : "");
            }
            entry.put(K_VALUE, arr);
        } else {
            return null;
        }
        return entry;
    }

    // ----------------------------------------------------------------------
    // Section detection
    // ----------------------------------------------------------------------

    /**
     * Inspect a backup file's JSON to learn which sections are present.
     * Drives the "sections to restore" multi-select in the import UI.
     *
     * @return a four-element array: {@code [basic, gamepad, keyboard, special]}.
     */
    public static boolean[] detectSections(String json) {
        boolean[] out = new boolean[]{false, false, false, false};
        if (json == null || json.trim().isEmpty()) {
            return out;
        }
        try {
            JSONObject root = new JSONObject(json);
            out[0] = root.has(K_BASIC) && !root.isNull(K_BASIC);
            out[1] = root.has(K_GAMEPAD) && !root.isNull(K_GAMEPAD);
            out[2] = root.has(K_KEYBOARD) && !root.isNull(K_KEYBOARD);
            out[3] = root.has(K_SPECIAL) && !root.isNull(K_SPECIAL);
        } catch (JSONException ignored) {
            // Not valid JSON — caller treats all sections as absent.
        }
        return out;
    }

    // ----------------------------------------------------------------------
    // Import (Replace)
    // ----------------------------------------------------------------------

    /**
     * Apply a backup file, replacing each selected section present in the file.
     * Sections not selected are left untouched. Must be called off the main
     * thread (it edits multiple SharedPreferences files).
     */
    public static void applyImport(Context ctx, String json,
                                   boolean basic, boolean gamepad,
                                   boolean keyboard, boolean special) throws JSONException {
        if (json == null) {
            json = "";
        }
        if (json.trim().isEmpty()) {
            return;
        }
        JSONObject root = new JSONObject(json);

        if (basic && root.has(K_BASIC)) {
            importBasicReplace(ctx, root.getJSONObject(K_BASIC));
        }
        if (gamepad && root.has(K_GAMEPAD)) {
            importLayoutsReplace(ctx, new GamepadLayoutManager(ctx),
                    root.getJSONObject(K_GAMEPAD));
        }
        if (keyboard && root.has(K_KEYBOARD)) {
            importLayoutsReplace(ctx, new KeyboardLayoutManager(ctx),
                    root.getJSONObject(K_KEYBOARD));
        }
        if (special && root.has(K_SPECIAL)) {
            String raw = root.optString(K_SPECIAL, "");
            ctx.getSharedPreferences(GameMenu.PREF_NAME, Activity.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .putString(GameMenu.KEY_NAME, raw)
                    .apply();
        }
    }

    private static void importBasicReplace(Context ctx, JSONObject section) throws JSONException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor ed = prefs.edit();
        ed.clear();
        Iterator<String> it = section.keys();
        while (it.hasNext()) {
            String key = it.next();
            JSONObject entry = section.getJSONObject(key);
            writeTypedEntry(ed, key, entry);
        }
        ed.apply();
    }

    /**
     * Replace all layouts of one manager with the layouts in the section.
     * Existing meta + every layout data file is wiped, the manager is
     * reconstructed (default layout re-created), then each file layout is
     * restored with a fresh UUID (only names + content + active identity are
     * preserved). The built-in {@code default} layout always survives.
     */
    private static void importLayoutsReplace(Context ctx,
                                             Object manager,
                                             JSONObject section) throws JSONException {
        // 1. Wipe existing meta + every layout data file.
        if (manager instanceof GamepadLayoutManager) {
            GamepadLayoutManager gpm = (GamepadLayoutManager) manager;
            for (String id : gpm.getLayoutIds()) {
                ctx.getSharedPreferences(GamepadLayoutManager.getLayoutPreferenceName(id),
                                Activity.MODE_PRIVATE)
                        .edit().clear().apply();
            }
            gpm.clearAll();
        } else {
            KeyboardLayoutManager kbm = (KeyboardLayoutManager) manager;
            for (String id : kbm.getLayoutIds()) {
                ctx.getSharedPreferences(KeyboardLayoutManager.getLayoutPreferenceName(id),
                                Activity.MODE_PRIVATE)
                        .edit().clear().apply();
            }
            kbm.clearAll();
        }

        // 2. Reconstruct manager so the default entry exists again.
        Object fresh = (manager instanceof GamepadLayoutManager)
                ? new GamepadLayoutManager(ctx)
                : new KeyboardLayoutManager(ctx);

        String fileActive = section.optString(K_ACTIVE, "");
        JSONArray layouts = section.optJSONArray(K_LAYOUTS);
        if (layouts == null) {
            return;
        }

        // old file-layout id -> live id after restore
        Map<String, String> idMap = new HashMap<>();

        for (int i = 0; i < layouts.length(); i++) {
            JSONObject layoutObj = layouts.getJSONObject(i);
            String fileId = layoutObj.optString(K_LAYOUT_ID, "");
            String name = layoutObj.optString(K_LAYOUT_NAME, fileId);

            JSONObject prefsObj = layoutObj.optJSONObject(K_LAYOUT_PREFS);
            if (prefsObj == null) {
                prefsObj = new JSONObject();
            }

            String liveId;
            if (GamepadLayoutManager.DEFAULT_LAYOUT_ID.equals(fileId)) {
                liveId = GamepadLayoutManager.DEFAULT_LAYOUT_ID;
                if (fresh instanceof GamepadLayoutManager) {
                    ((GamepadLayoutManager) fresh).renameLayout(liveId, name);
                } else {
                    ((KeyboardLayoutManager) fresh).renameLayout(liveId, name);
                }
            } else {
                if (fresh instanceof GamepadLayoutManager) {
                    liveId = ((GamepadLayoutManager) fresh).createLayout(name);
                } else {
                    liveId = ((KeyboardLayoutManager) fresh).createLayout(name);
                }
            }
            idMap.put(fileId, liveId);

            // Write element-config prefs (strings only).
            String prefName = (fresh instanceof GamepadLayoutManager)
                    ? GamepadLayoutManager.getLayoutPreferenceName(liveId)
                    : KeyboardLayoutManager.getLayoutPreferenceName(liveId);
            SharedPreferences.Editor led = ctx.getSharedPreferences(prefName, Activity.MODE_PRIVATE).edit();
            Iterator<String> pks = prefsObj.keys();
            while (pks.hasNext()) {
                String pk = pks.next();
                Object val = prefsObj.opt(pk);
                if (val instanceof String) {
                    led.putString(pk, (String) val);
                }
            }
            led.apply();
        }

        // 3. Restore active layout identity (default if the file's active is gone).
        String liveActive = idMap.getOrDefault(fileActive,
                (fresh instanceof GamepadLayoutManager)
                        ? ((GamepadLayoutManager) fresh).getActiveLayoutId()
                        : ((KeyboardLayoutManager) fresh).getActiveLayoutId());
        if (fresh instanceof GamepadLayoutManager) {
            ((GamepadLayoutManager) fresh).setActiveLayoutId(liveActive);
        } else {
            ((KeyboardLayoutManager) fresh).setActiveLayoutId(liveActive);
        }
    }

    /**
     * Inverse of {@link #encodeTyped}: dispatch on the {@code type} tag to the
     * correctly-typed {@link SharedPreferences.Editor} setter. Returns false
     * (and skips) for unknown types.
     */
    private static boolean writeTypedEntry(SharedPreferences.Editor ed, String key, JSONObject entry) {
        String type = entry.optString(K_TYPE, "");
        try {
            switch (type) {
                case T_BOOLEAN:
                    ed.putBoolean(key, entry.getBoolean(K_VALUE));
                    return true;
                case T_INT:
                    ed.putInt(key, entry.getInt(K_VALUE));
                    return true;
                case T_FLOAT:
                    ed.putFloat(key, (float) entry.getDouble(K_VALUE));
                    return true;
                case T_LONG:
                    ed.putLong(key, (long) entry.getDouble(K_VALUE));
                    return true;
                case T_STRING:
                    ed.putString(key, entry.getString(K_VALUE));
                    return true;
                case T_STRING_SET: {
                    JSONArray arr = entry.optJSONArray(K_VALUE);
                    Set<String> set = new HashSet<>();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            set.add(arr.getString(i));
                        }
                    }
                    ed.putStringSet(key, set);
                    return true;
                }
                default:
                    return false;
            }
        } catch (JSONException e) {
            return false;
        }
    }
}
