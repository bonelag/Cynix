package com.limelight.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.GameMenu;
import com.limelight.TestLogSuppressor;
import com.limelight.binding.input.virtual_controller.GamepadLayoutManager;
import com.limelight.binding.input.virtual_controller.keyboard.KeyboardLayoutManager;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Type-safe round-trip + partial-import coverage for {@link SettingsBackup}.
 * Mirrors the conventions in {@code android_test_setup.md}: Robolectric at SDK 33,
 * shadows for the native MoonBridge and GameManager, log suppression, and direct
 * SharedPreferences manipulation (no Activity needed).
 */
@Config(sdk = {33}, shadows = {
        com.limelight.shadows.ShadowMoonBridge.class,
        com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class SettingsBackupTest {
    private Context ctx;

    @BeforeClass
    public static void suppressLogs() {
        TestLogSuppressor.install();
    }

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();

        // Wipe every section we touch so tests are hermetic.
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().clear().apply();
        ctx.getSharedPreferences(GameMenu.PREF_NAME, Activity.MODE_PRIVATE).edit().clear().apply();
        wipeAllLayouts(new GamepadLayoutManager(ctx));
        wipeAllLayouts(new KeyboardLayoutManager(ctx));
    }

    private void wipeAllLayouts(Object manager) {
        List<String> ids = (manager instanceof GamepadLayoutManager)
                ? ((GamepadLayoutManager) manager).getLayoutIds()
                : ((KeyboardLayoutManager) manager).getLayoutIds();
        for (String id : ids) {
            String name = (manager instanceof GamepadLayoutManager)
                    ? GamepadLayoutManager.getLayoutPreferenceName(id)
                    : KeyboardLayoutManager.getLayoutPreferenceName(id);
            ctx.getSharedPreferences(name, Activity.MODE_PRIVATE).edit().clear().apply();
        }
        if (manager instanceof GamepadLayoutManager) {
            ((GamepadLayoutManager) manager).clearAll();
        } else {
            ((KeyboardLayoutManager) manager).clearAll();
        }
    }

    // ------------------------------------------------------------------
    // Basic settings — type preservation across the round trip
    // ------------------------------------------------------------------

    @Test
    public void roundtrip_basic_settings_preserves_types() throws Exception {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor ed = prefs.edit();
        ed.putBoolean("checkbox_enable_hdr", true);
        ed.putInt("seekbar_osc_opacity", 90);
        ed.putFloat("some_float_value", 1.25f);
        ed.putLong("some_long_value", 9999999999L);
        ed.putString("list_fps", "60");
        ed.putStringSet("some_set", new HashSet<>(Arrays.asList("a", "b", "c")));
        ed.apply();

        String json = SettingsBackup.exportToString(ctx, true, false, false, false);

        // Wipe everything, then import.
        prefs.edit().clear().apply();
        assertTrue(prefs.getAll().isEmpty());

        SettingsBackup.applyImport(ctx, json, true, false, false, false);

        assertEquals(true, prefs.getAll().get("checkbox_enable_hdr"));
        assertEquals(Integer.class, prefs.getAll().get("seekbar_osc_opacity").getClass());
        assertEquals(90, prefs.getAll().get("seekbar_osc_opacity"));
        assertEquals(Float.class, prefs.getAll().get("some_float_value").getClass());
        assertEquals(1.25f, (Float) prefs.getAll().get("some_float_value"), 0.0001f);
        assertEquals(Long.class, prefs.getAll().get("some_long_value").getClass());
        assertEquals(9999999999L, (long) (Long) prefs.getAll().get("some_long_value"));
        assertEquals("60", prefs.getAll().get("list_fps"));
        @SuppressWarnings("unchecked")
        java.util.Set<String> set = (java.util.Set<String>) prefs.getAll().get("some_set");
        assertEquals(new HashSet<>(Arrays.asList("a", "b", "c")), set);
    }

    // ------------------------------------------------------------------
    // Gamepad layouts — names, content, and active identity survive a UUID regen
    // ------------------------------------------------------------------

    @Test
    public void roundtrip_gamepad_layouts() throws Exception {
        GamepadLayoutManager mgr = new GamepadLayoutManager(ctx);
        String fps = mgr.createLayout("FPS");
        String rpg = mgr.createLayout("RPG");

        ctx.getSharedPreferences(GamepadLayoutManager.getLayoutPreferenceName(fps),
                        Activity.MODE_PRIVATE).edit()
                .putString("element_left_stick_x", "10")
                .putString("element_left_stick_y", "20")
                .apply();
        ctx.getSharedPreferences(GamepadLayoutManager.getLayoutPreferenceName(rpg),
                        Activity.MODE_PRIVATE).edit()
                .putString("element_left_stick_x", "30")
                .apply();
        mgr.setActiveLayoutId(rpg);

        String json = SettingsBackup.exportToString(ctx, false, true, false, false);

        // Replace: wipe, then reconstruct + import.
        wipeAllLayouts(mgr);
        SettingsBackup.applyImport(ctx, json, false, true, false, false);

        GamepadLayoutManager restored = new GamepadLayoutManager(ctx);
        List<String> ids = restored.getLayoutIds();
        // default + FPS + RPG
        assertEquals(3, ids.size());
        assertTrue(ids.contains(GamepadLayoutManager.DEFAULT_LAYOUT_ID));

        // Locate FPS/RPG by name (IDs are regenerated).
        String fpsId = findLayoutIdByName(restored, "FPS", ids);
        String rpgId = findLayoutIdByName(restored, "RPG", ids);

        assertEquals("10", ctx.getSharedPreferences(
                GamepadLayoutManager.getLayoutPreferenceName(fpsId), Activity.MODE_PRIVATE)
                .getString("element_left_stick_x", null));
        assertEquals("20", ctx.getSharedPreferences(
                GamepadLayoutManager.getLayoutPreferenceName(fpsId), Activity.MODE_PRIVATE)
                .getString("element_left_stick_y", null));
        assertEquals("30", ctx.getSharedPreferences(
                GamepadLayoutManager.getLayoutPreferenceName(rpgId), Activity.MODE_PRIVATE)
                .getString("element_left_stick_x", null));

        // Active identity preserved (points at the regenerated RPG id, not the old one).
        assertEquals(rpgId, restored.getActiveLayoutId());
        assertFalse(rpg.equals(rpgId)); // IDs are freshly generated
    }

    // ------------------------------------------------------------------
    // Keyboard layouts — same shape, independent storage
    // ------------------------------------------------------------------

    @Test
    public void roundtrip_keyboard_layouts() throws Exception {
        KeyboardLayoutManager mgr = new KeyboardLayoutManager(ctx);
        String work = mgr.createLayout("Work");
        ctx.getSharedPreferences(KeyboardLayoutManager.getLayoutPreferenceName(work),
                        Activity.MODE_PRIVATE).edit()
                .putString("special_key_esc", "0x1B")
                .apply();
        mgr.setActiveLayoutId(work);

        String json = SettingsBackup.exportToString(ctx, false, false, true, false);

        wipeAllLayouts(mgr);
        SettingsBackup.applyImport(ctx, json, false, false, true, false);

        KeyboardLayoutManager restored = new KeyboardLayoutManager(ctx);
        String workId = findLayoutIdByName(restored, "Work", restored.getLayoutIds());
        assertEquals("0x1B", ctx.getSharedPreferences(
                KeyboardLayoutManager.getLayoutPreferenceName(workId), Activity.MODE_PRIVATE)
                .getString("special_key_esc", null));
        assertEquals(workId, restored.getActiveLayoutId());
    }

    // ------------------------------------------------------------------
    // Special keys round-trip
    // ------------------------------------------------------------------

    @Test
    public void roundtrip_special_keys() throws Exception {
        SharedPreferences sp = ctx.getSharedPreferences(GameMenu.PREF_NAME, Activity.MODE_PRIVATE);
        sp.edit().putString(GameMenu.KEY_NAME, "{\"data\":[{\"name\":\"Win\",\"keys\":[\"VK_LWIN\"]}]}").apply();

        String json = SettingsBackup.exportToString(ctx, false, false, false, true);
        sp.edit().clear().apply();
        assertTrue(sp.getAll().isEmpty());

        SettingsBackup.applyImport(ctx, json, false, false, false, true);
        assertEquals("{\"data\":[{\"name\":\"Win\",\"keys\":[\"VK_LWIN\"]}]}",
                sp.getString(GameMenu.KEY_NAME, null));
    }

    // ------------------------------------------------------------------
    // Partial file / section detection + non-selected sections untouched
    // ------------------------------------------------------------------

    @Test
    public void detectSections_partial_basic_only() {
        String json = SettingsBackup.exportToString(ctx, true, false, false, false);
        boolean[] sections = SettingsBackup.detectSections(json);
        assertArrayEquals(new boolean[]{true, false, false, false}, sections);
    }

    @Test
    public void detectSections_all_four() {
        // seed special keys so the export includes that section
        ctx.getSharedPreferences(GameMenu.PREF_NAME, Activity.MODE_PRIVATE)
                .edit().putString(GameMenu.KEY_NAME, "{}").apply();
        String json = SettingsBackup.exportToString(ctx, true, true, true, true);
        boolean[] sections = SettingsBackup.detectSections(json);
        assertArrayEquals(new boolean[]{true, true, true, true}, sections);
    }

    @Test
    public void import_partial_file_leaves_other_sections_untouched() throws Exception {
        // Set up a gamepad layout; build an export that contains ONLY basic settings.
        GamepadLayoutManager mgr = new GamepadLayoutManager(ctx);
        String custom = mgr.createLayout("Custom");
        ctx.getSharedPreferences(GamepadLayoutManager.getLayoutPreferenceName(custom),
                        Activity.MODE_PRIVATE).edit()
                .putString("pos_x", "42").apply();

        String json = SettingsBackup.exportToString(ctx, true, false, false, false);
        // Import only basic from the partial file → gamepad must survive untouched.
        SettingsBackup.applyImport(ctx, json, true, false, false, false);

        GamepadLayoutManager after = new GamepadLayoutManager(ctx);
        assertTrue(after.getLayoutIds().contains(custom));
        assertEquals("42", ctx.getSharedPreferences(
                GamepadLayoutManager.getLayoutPreferenceName(custom), Activity.MODE_PRIVATE)
                .getString("pos_x", null));
    }

    @Test
    public void empty_json_is_safe() throws Exception {
        // Exporting nothing, detecting, and importing nothing must not throw.
        String json = SettingsBackup.exportToString(ctx, false, false, false, false);
        assertArrayEquals(new boolean[]{false, false, false, false},
                SettingsBackup.detectSections(json));
        SettingsBackup.applyImport(ctx, json, true, true, true, true); // no-op
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String findLayoutIdByName(GamepadLayoutManager mgr, String name, List<String> ids) {
        for (String id : ids) {
            if (name.equals(mgr.getLayoutName(id))) {
                return id;
            }
        }
        throw new AssertionError("No layout named " + name + " in " + ids);
    }

    private String findLayoutIdByName(KeyboardLayoutManager mgr, String name, List<String> ids) {
        for (String id : ids) {
            if (name.equals(mgr.getLayoutName(id))) {
                return id;
            }
        }
        throw new AssertionError("No layout named " + name + " in " + ids);
    }

    @SuppressWarnings("unused")
    private java.util.Set<String> set(String... s) {
        return new HashSet<>(Arrays.asList(s));
    }

    @SuppressWarnings("unused")
    private java.util.List<String> list(String... s) {
        return s == null ? Collections.emptyList() : Arrays.asList(s);
    }
}
