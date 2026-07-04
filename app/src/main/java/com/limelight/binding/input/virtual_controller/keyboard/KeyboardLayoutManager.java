package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages multiple keyboard layouts for special keys (CRUD operations).
 * Each layout is stored in its own SharedPreferences file.
 * Metadata (layout IDs, names, active layout) is stored in a separate SharedPreferences.
 */
public class KeyboardLayoutManager {

    private static final String META_PREFERENCE = "keyboard_layouts_meta";
    private static final String LAYOUT_PREFERENCE_PREFIX = "keyboard_layout_";
    private static final String KEY_LAYOUT_IDS = "layout_ids";
    private static final String KEY_ACTIVE_LAYOUT_ID = "active_layout_id";
    private static final String KEY_LAYOUT_NAME_PREFIX = "layout_name_";
    private static final String KEY_MIGRATED = "migrated_from_legacy";

    public static final String DEFAULT_LAYOUT_ID = "default";

    private final Context context;

    public KeyboardLayoutManager(Context context) {
        this.context = context;
        ensureDefaultLayoutExists();
    }

    /**
     * Ensures that the default layout exists in metadata.
     */
    private void ensureDefaultLayoutExists() {
        SharedPreferences meta = getMetaPreferences();
        String idsJson = meta.getString(KEY_LAYOUT_IDS, null);

        if (idsJson == null) {
            JSONArray ids = new JSONArray();
            ids.put(DEFAULT_LAYOUT_ID);

            meta.edit()
                    .putString(KEY_LAYOUT_IDS, ids.toString())
                    .putString(KEY_LAYOUT_NAME_PREFIX + DEFAULT_LAYOUT_ID, getDefaultLayoutName())
                    .putString(KEY_ACTIVE_LAYOUT_ID, DEFAULT_LAYOUT_ID)
                    .apply();
        }
    }

    private String getDefaultLayoutName() {
        try {
            return context.getString(
                    context.getResources().getIdentifier(
                            "keyboard_layout_default_name", "string", context.getPackageName()));
        } catch (Exception e) {
            return "Default";
        }
    }

    private SharedPreferences getMetaPreferences() {
        return context.getSharedPreferences(META_PREFERENCE, Activity.MODE_PRIVATE);
    }

    /**
     * Returns the SharedPreferences name for the given layout ID.
     */
    public static String getLayoutPreferenceName(String layoutId) {
        return LAYOUT_PREFERENCE_PREFIX + layoutId;
    }

    /**
     * Returns all layout IDs in order.
     */
    public List<String> getLayoutIds() {
        SharedPreferences meta = getMetaPreferences();
        String idsJson = meta.getString(KEY_LAYOUT_IDS, "[]");

        List<String> ids = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(idsJson);
            for (int i = 0; i < arr.length(); i++) {
                ids.add(arr.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            ids.add(DEFAULT_LAYOUT_ID);
        }
        return ids;
    }

    /**
     * Returns the display name for the given layout.
     */
    public String getLayoutName(String layoutId) {
        SharedPreferences meta = getMetaPreferences();
        return meta.getString(KEY_LAYOUT_NAME_PREFIX + layoutId, layoutId);
    }

    /**
     * Returns the currently active layout ID.
     */
    public String getActiveLayoutId() {
        SharedPreferences meta = getMetaPreferences();
        return meta.getString(KEY_ACTIVE_LAYOUT_ID, DEFAULT_LAYOUT_ID);
    }

    /**
     * Sets the active layout ID.
     */
    public void setActiveLayoutId(String layoutId) {
        getMetaPreferences().edit()
                .putString(KEY_ACTIVE_LAYOUT_ID, layoutId)
                .apply();
    }

    /**
     * Creates a new layout with the given name.
     * Returns the new layout ID.
     */
    public String createLayout(String name) {
        String newId = UUID.randomUUID().toString().substring(0, 8);

        SharedPreferences meta = getMetaPreferences();
        List<String> ids = getLayoutIds();
        ids.add(newId);

        JSONArray arr = new JSONArray(ids);
        meta.edit()
                .putString(KEY_LAYOUT_IDS, arr.toString())
                .putString(KEY_LAYOUT_NAME_PREFIX + newId, name)
                .apply();

        return newId;
    }

    /**
     * Duplicates an existing layout with a new name.
     * Returns the new layout ID.
     */
    public String duplicateLayout(String sourceId, String newName) {
        String newId = createLayout(newName);

        SharedPreferences sourcePref = context.getSharedPreferences(
                getLayoutPreferenceName(sourceId), Activity.MODE_PRIVATE);
        SharedPreferences.Editor destEditor = context.getSharedPreferences(
                getLayoutPreferenceName(newId), Activity.MODE_PRIVATE).edit();

        Map<String, ?> allEntries = sourcePref.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            if (entry.getValue() instanceof String) {
                destEditor.putString(entry.getKey(), (String) entry.getValue());
            }
        }
        destEditor.apply();

        return newId;
    }

    /**
     * Renames an existing layout.
     */
    public void renameLayout(String layoutId, String newName) {
        getMetaPreferences().edit()
                .putString(KEY_LAYOUT_NAME_PREFIX + layoutId, newName)
                .apply();
    }

    /**
     * Deletes a layout. Cannot delete the default layout.
     * Returns true if the layout was deleted.
     */
    public boolean deleteLayout(String layoutId) {
        if (DEFAULT_LAYOUT_ID.equals(layoutId)) {
            return false;
        }

        SharedPreferences meta = getMetaPreferences();
        List<String> ids = getLayoutIds();
        ids.remove(layoutId);

        JSONArray arr = new JSONArray(ids);
        SharedPreferences.Editor editor = meta.edit();
        editor.putString(KEY_LAYOUT_IDS, arr.toString());
        editor.remove(KEY_LAYOUT_NAME_PREFIX + layoutId);

        // If the deleted layout was active, switch to default
        if (layoutId.equals(getActiveLayoutId())) {
            editor.putString(KEY_ACTIVE_LAYOUT_ID, DEFAULT_LAYOUT_ID);
        }

        editor.apply();

        // Clear the layout's data
        context.getSharedPreferences(getLayoutPreferenceName(layoutId), Activity.MODE_PRIVATE)
                .edit().clear().apply();

        return true;
    }

    /**
     * Wipes <b>all</b> keyboard layout metadata (and resets the migrate flag),
     * leaving zero layouts registered. The next {@link KeyboardLayoutManager}
     * construction will re-create the {@link #DEFAULT_LAYOUT_ID} entry.
     *
     * <p>Callers are responsible for clearing the per-layout data
     * ({@link #getLayoutPreferenceName(String)}) beforehand &mdash;
     * {@code SettingsBackup} does this as part of a Replace-import.
     */
    public void clearAll() {
        getMetaPreferences().edit().clear().apply();
    }

    /**
     * Migrates legacy layout data from SharedPreferences("OSC_Keyboard") to the default layout.
     * This is a one-time operation.
     */
    public void migrateFromLegacy(Context context) {
        SharedPreferences meta = getMetaPreferences();
        if (meta.getBoolean(KEY_MIGRATED, false)) {
            return;
        }

        // Read legacy preference name
        String legacyName = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(context)
                .getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                        KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);

        SharedPreferences legacyPref = context.getSharedPreferences(legacyName, Activity.MODE_PRIVATE);
        Map<String, ?> legacyEntries = legacyPref.getAll();

        if (!legacyEntries.isEmpty()) {
            SharedPreferences.Editor destEditor = context.getSharedPreferences(
                    getLayoutPreferenceName(DEFAULT_LAYOUT_ID), Activity.MODE_PRIVATE).edit();

            for (Map.Entry<String, ?> entry : legacyEntries.entrySet()) {
                if (entry.getValue() instanceof String) {
                    destEditor.putString(entry.getKey(), (String) entry.getValue());
                }
            }
            destEditor.apply();
        }

        meta.edit().putBoolean(KEY_MIGRATED, true).apply();
    }
}
