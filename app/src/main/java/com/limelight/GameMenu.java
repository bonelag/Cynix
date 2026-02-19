package com.limelight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import com.limelight.binding.input.GameInputDevice;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.virtual_controller.GamepadLayoutManager;
import com.limelight.binding.input.virtual_controller.keyboard.KeyboardLayoutManager;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.KeyConfigHelper;
import com.limelight.utils.KeyMapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provide options for ongoing Game Stream.
 * <p>
 * Shown on back action in game activity.
 */
public class GameMenu implements Game.GameMenuCallbacks {

    public static final long KEY_UP_DELAY = 25;
    private static final long TEST_GAME_FOCUS_DELAY = 10;

    public static final String PREF_NAME = "specialPrefs"; // SharedPreferences的名称

    public static final String KEY_NAME = "special_key"; // 要保存的键名称

    public static class MenuOption {
        private final String label;
        private final boolean withGameFocus;
        private final Runnable runnable;

        public MenuOption(String label, boolean withGameFocus, Runnable runnable) {
            this.label = label;
            this.withGameFocus = withGameFocus;
            this.runnable = runnable;
        }

        public MenuOption(String label, Runnable runnable) {
            this(label, false, runnable);
        }
    }

    private final Game game;
    private final Context dialogScreenContext;

    private AlertDialog currentDialog;

    public GameMenu(Game game, Context dialogScreenContext) {
        this.game = game;
        this.dialogScreenContext = dialogScreenContext;
    }

    public GameMenu(Game game) {
        this.game = game;
        this.dialogScreenContext = game;
    }

    private String getString(int id) {
        return game.getResources().getString(id);
    }


    private void sendKeys(short[] keys) {
        game.sendKeys(keys);
    }

    private void runWithGameFocus(Runnable runnable) {
        // Ensure that the Game activity is still active (not finished)
        if (game.isFinishing()) {
            return;
        }
        // Check if the game window has focus again, if not try again after delay
        if (!game.hasWindowFocus() && dialogScreenContext instanceof Game) {
            new Handler().postDelayed(() -> runWithGameFocus(runnable), TEST_GAME_FOCUS_DELAY);
            return;
        }
        // Game Activity has focus, run runnable
        runnable.run();
    }

    private void run(MenuOption option) {
        if (option.runnable == null) {
            return;
        }

        if (option.withGameFocus) {
            runWithGameFocus(option.runnable);
        } else {
            option.runnable.run();
        }
    }

    private void showMenuDialog(String title, MenuOption[] options) {
        int themeResId = game.getApplicationInfo().theme;
        Context themedContext = new ContextThemeWrapper(dialogScreenContext, themeResId);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle(title);

        final ArrayAdapter<String> actions = new ArrayAdapter<>(themedContext, android.R.layout.simple_list_item_1);

        builder.setAdapter(actions, (dialog, which) -> {
            String label = actions.getItem(which);
            for (MenuOption option : options) {
                if (label != null && label.equals(option.label)) {
                    run(option);
                    break;
                }
            }
        });

        builder.setOnCancelListener(dialog -> hideMenu());

        if (currentDialog != null) {
            currentDialog.dismiss();
        }
        currentDialog = builder.show();

        Window window = currentDialog.getWindow();

        if (window != null) {
            View decorView = window.getDecorView();
            decorView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {

                    decorView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    new Handler(Looper.getMainLooper()).post(() -> {
                        for (MenuOption option : options) {
                            actions.add(option.label);
                        }
                        actions.notifyDataSetChanged();
                    });
                }
            });
        }
    }

    private void showSpecialKeysMenu() {
        List<MenuOption> options = new ArrayList<>();

        if(!PreferenceConfiguration.readPreferences(game).disableDefaultExtraKeys){
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_esc),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_ESCAPE})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_f11),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_F11})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_f4),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_F4})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_enter),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_RETURN})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_v),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_V})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_d),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_g),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_tab),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_TAB})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_shift_tab),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_shift_left),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_LEFT})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f1),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F1})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f12),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F12})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_b),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_B})));
        }

        // Import custom shortcuts
        SharedPreferences preferences = game.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE);
        String value = preferences.getString(KEY_NAME,"");

        if(!TextUtils.isEmpty(value)){
            try {
                KeyConfigHelper.ShortcutFile shortcutFile = KeyConfigHelper.parseShortcutFile(value);
                if (shortcutFile != null && shortcutFile.data != null && !shortcutFile.data.isEmpty()) {
                    List<KeyConfigHelper.Shortcut> data = shortcutFile.data;
                    for (KeyConfigHelper.Shortcut sc : data) {
                        List<String> keys = sc.keys;
                        short[] keyCodes = new short[keys.size()];

                        for (int i = 0; i < keys.size(); i++) {
                            String code = keys.get(i);
                            int keycode;

                            if (code.startsWith("0x")) {               // literal hex value
                                keycode = Integer.parseInt(code.substring(2), 16);
                            } else if (code.startsWith("VK_")) {       // symbolic constant in KeyMapper
                                Field field = KeyMapper.class.getDeclaredField(code);
                                keycode = field.getInt(null);
                            } else {                                   // unsupported
                                throw new IllegalArgumentException("Unknown key code: " + code);
                            }
                            keyCodes[i] = (short) keycode;
                        }

                        // Whatever MenuOption looks like in your project
                        MenuOption option = new MenuOption(sc.name, () -> sendKeys(keyCodes));
                        options.add(option);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(game,getString(R.string.wrong_import_format),Toast.LENGTH_SHORT).show();
            }
        }
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.game_menu_send_keys), options.toArray(new MenuOption[options.size()]));
    }

    private void showAdvancedMenu(GameInputDevice device) {
        List<MenuOption> options = new ArrayList<>();

        options.add(new MenuOption(getString(R.string.game_menu_toggle_hud), true, game::toggleHUD));

        options.add(new MenuOption(getString(R.string.game_menu_switch_touch_sensitivity_model), true, game::switchTouchSensitivity));

        options.add(new MenuOption(getString(R.string.game_menu_upload_clipboard), true,
                () -> game.sendClipboard(true)));

        options.add(new MenuOption(getString(R.string.game_menu_fetch_clipboard), true,
                () -> game.getClipboard(0)));

        options.add(new MenuOption(getString(R.string.game_menu_server_cmd), true,
                () -> {
                    ArrayList<String> serverCmds = game.getServerCmds();
                    if (serverCmds.isEmpty()) {
                        int themeResId = game.getApplicationInfo().theme;
                        Context themedContext = new ContextThemeWrapper(dialogScreenContext, themeResId);
                        new AlertDialog.Builder(themedContext)
                                .setTitle(R.string.game_dialog_title_server_cmd_empty)
                                .setMessage(R.string.game_dialog_message_server_cmd_empty)
                                .show();
                    } else {
                        hideMenu();
                        this.showServerCmd(serverCmds);
                    }
                }));

        options.add(new MenuOption(getString(R.string.game_menu_task_manager), true, () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_ESCAPE})));

        options.add(new MenuOption(getString(R.string.game_menu_send_keys), () -> {
            hideMenu();
            showSpecialKeysMenu();
        }));

        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.game_menu_advanced), options.toArray(new MenuOption[options.size()]));
    }

    private void showServerCmd(ArrayList<String> serverCmds) {
        List<MenuOption> options = new ArrayList<>();

        AtomicInteger index = new AtomicInteger(0);
        for (String str : serverCmds) {
            final int finalI = index.getAndIncrement();
            options.add(new MenuOption("> " + str, true, () -> game.sendExecServerCmd(finalI)));
        };

        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.game_menu_server_cmd), options.toArray(new MenuOption[options.size()]));
    }

    public void showMenu(GameInputDevice device) {
        List<MenuOption> options = new ArrayList<>();

        options.add(new MenuOption(getString(R.string.game_menu_disconnect), game::disconnect));

        if (!game.isOnExternalDisplay()) {
            options.add(new MenuOption(getString(R.string.game_menu_gamepad_layouts), true, () -> {
                hideMenu();
                showGamepadLayoutMenu();
            }));
        }

        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard), true, game::toggleKeyboard));

        options.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_keyboard_model), true, game::toggleFullKeyboard));

        options.add(new MenuOption(getString(R.string.game_menu_keyboard_layouts), true, () -> {
            hideMenu();
            showKeyboardLayoutMenu();
        }));

        if (game.allowChangeMouseMode) {
            options.add(new MenuOption(getString(R.string.game_menu_select_mouse_mode), true, () -> game.selectMouseMode(dialogScreenContext)));
        }

        options.add(new MenuOption(getString(game.isZoomModeEnabled() ? R.string.game_menu_disable_zoom_mode : R.string.game_menu_enable_zoom_mode), true, game::toggleZoomMode));

        if (dialogScreenContext == game) {
            options.add(new MenuOption(getString(R.string.game_menu_rotate_screen), true, game::rotateScreen));
        }

        options.add(new MenuOption(getString(R.string.game_menu_advanced), true, () -> showAdvancedMenu(device)));

        options.add(new MenuOption(getString(R.string.game_menu_quit_session), game::quit));

        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.quick_menu_title), options.toArray(new MenuOption[options.size()]));
    }

    @Override
    public void hideMenu() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        currentDialog = null;
    }

    @Override
    public boolean isMenuOpen() {
        return currentDialog != null && currentDialog.isShowing();
    }

    private void showGamepadLayoutMenu() {
        GamepadLayoutManager layoutManager = game.getLayoutManager();
        if (layoutManager == null) {
            return;
        }

        List<MenuOption> options = new ArrayList<>();

        // Toggle gamepad visibility
        options.add(new MenuOption(getString(R.string.gamepad_layout_toggle_gamepad), true, game::toggleVirtualController));

        // List existing layouts
        List<String> layoutIds = layoutManager.getLayoutIds();
        String activeId = layoutManager.getActiveLayoutId();

        for (String layoutId : layoutIds) {
            String name = layoutManager.getLayoutName(layoutId);
            String label = layoutId.equals(activeId) ? "✓ " + name : "   " + name;
            final String lid = layoutId;
            options.add(new MenuOption(label, true, () -> {
                game.switchGamepadLayout(lid);
                Toast.makeText(game, String.format(getString(R.string.gamepad_layout_switched), layoutManager.getLayoutName(lid)), Toast.LENGTH_SHORT).show();
            }));
        }

        // Create new layout
        options.add(new MenuOption("➕ " + getString(R.string.gamepad_layout_create), () -> {
            hideMenu();
            showLayoutNameInputDialog(getString(R.string.gamepad_layout_create), "", name -> {
                String newId = layoutManager.createLayout(name);
                game.switchGamepadLayout(newId);
                Toast.makeText(game, String.format(getString(R.string.gamepad_layout_created), name), Toast.LENGTH_SHORT).show();
            });
        }));

        // Duplicate current layout
        options.add(new MenuOption("🔄 " + getString(R.string.gamepad_layout_duplicate), () -> {
            hideMenu();
            String currentName = layoutManager.getLayoutName(activeId);
            showLayoutNameInputDialog(getString(R.string.gamepad_layout_duplicate), currentName + " (2)", name -> {
                String newId = layoutManager.duplicateLayout(activeId, name);
                game.switchGamepadLayout(newId);
                Toast.makeText(game, String.format(getString(R.string.gamepad_layout_created), name), Toast.LENGTH_SHORT).show();
            });
        }));

        // Rename current layout
        options.add(new MenuOption("✏️ " + getString(R.string.gamepad_layout_rename), () -> {
            hideMenu();
            String currentName = layoutManager.getLayoutName(activeId);
            showLayoutNameInputDialog(getString(R.string.gamepad_layout_rename), currentName, name -> {
                layoutManager.renameLayout(activeId, name);
                Toast.makeText(game, String.format(getString(R.string.gamepad_layout_renamed), name), Toast.LENGTH_SHORT).show();
            });
        }));

        // Delete current layout
        if (!GamepadLayoutManager.DEFAULT_LAYOUT_ID.equals(activeId)) {
            options.add(new MenuOption("🗑️ " + getString(R.string.gamepad_layout_delete), () -> {
                hideMenu();
                String currentName = layoutManager.getLayoutName(activeId);
                showDeleteConfirmDialog(currentName, () -> {
                    layoutManager.deleteLayout(activeId);
                    game.switchGamepadLayout(layoutManager.getActiveLayoutId());
                    Toast.makeText(game, String.format(getString(R.string.gamepad_layout_deleted), currentName), Toast.LENGTH_SHORT).show();
                });
            }));
        }

        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.game_menu_gamepad_layouts), options.toArray(new MenuOption[0]));
    }

    private void showKeyboardLayoutMenu() {
        KeyboardLayoutManager layoutMgr = game.getKeyboardLayoutManager();
        if (layoutMgr == null) {
            return;
        }

        List<MenuOption> options = new ArrayList<>();

        // Toggle keyboard visibility
        options.add(new MenuOption(getString(R.string.keyboard_layout_toggle_keyboard), true, game::toggleKeyboardController));

        // List existing layouts
        List<String> layoutIds = layoutMgr.getLayoutIds();
        String activeId = layoutMgr.getActiveLayoutId();

        for (String layoutId : layoutIds) {
            String name = layoutMgr.getLayoutName(layoutId);
            String label = layoutId.equals(activeId) ? "\u2713 " + name : "   " + name;
            final String lid = layoutId;
            options.add(new MenuOption(label, true, () -> {
                game.switchKeyboardLayout(lid);
                Toast.makeText(game, String.format(getString(R.string.keyboard_layout_switched), layoutMgr.getLayoutName(lid)), Toast.LENGTH_SHORT).show();
            }));
        }

        // Create new layout
        options.add(new MenuOption("\u2795 " + getString(R.string.keyboard_layout_create), () -> {
            hideMenu();
            showLayoutNameInputDialog(getString(R.string.keyboard_layout_create), "", name -> {
                String newId = layoutMgr.createLayout(name);
                game.switchKeyboardLayout(newId);
                Toast.makeText(game, String.format(getString(R.string.keyboard_layout_created), name), Toast.LENGTH_SHORT).show();
            });
        }));

        // Duplicate current layout
        options.add(new MenuOption("\uD83D\uDD04 " + getString(R.string.keyboard_layout_duplicate), () -> {
            hideMenu();
            String currentName = layoutMgr.getLayoutName(activeId);
            showLayoutNameInputDialog(getString(R.string.keyboard_layout_duplicate), currentName + " (2)", name -> {
                String newId = layoutMgr.duplicateLayout(activeId, name);
                game.switchKeyboardLayout(newId);
                Toast.makeText(game, String.format(getString(R.string.keyboard_layout_created), name), Toast.LENGTH_SHORT).show();
            });
        }));

        // Rename current layout
        options.add(new MenuOption("\u270F\uFE0F " + getString(R.string.keyboard_layout_rename), () -> {
            hideMenu();
            String currentName = layoutMgr.getLayoutName(activeId);
            showLayoutNameInputDialog(getString(R.string.keyboard_layout_rename), currentName, name -> {
                layoutMgr.renameLayout(activeId, name);
                Toast.makeText(game, String.format(getString(R.string.keyboard_layout_renamed), name), Toast.LENGTH_SHORT).show();
            });
        }));

        // Delete current layout (only if not default)
        if (!KeyboardLayoutManager.DEFAULT_LAYOUT_ID.equals(activeId)) {
            options.add(new MenuOption("\uD83D\uDDD1\uFE0F " + getString(R.string.keyboard_layout_delete), () -> {
                hideMenu();
                String currentName = layoutMgr.getLayoutName(activeId);
                showDeleteConfirmDialog(currentName, () -> {
                    layoutMgr.deleteLayout(activeId);
                    game.switchKeyboardLayout(layoutMgr.getActiveLayoutId());
                    Toast.makeText(game, String.format(getString(R.string.keyboard_layout_deleted), currentName), Toast.LENGTH_SHORT).show();
                });
            }));
        }

        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.game_menu_keyboard_layouts), options.toArray(new MenuOption[0]));
    }

    private void showLayoutNameInputDialog(String title, String defaultValue, LayoutNameCallback callback) {
        int themeResId = game.getApplicationInfo().theme;
        Context themedContext = new ContextThemeWrapper(dialogScreenContext, themeResId);

        EditText input = new EditText(themedContext);
        input.setText(defaultValue);
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(themedContext)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(game, getString(R.string.gamepad_layout_name_empty), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    callback.onNameEntered(name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDeleteConfirmDialog(String layoutName, Runnable onConfirm) {
        int themeResId = game.getApplicationInfo().theme;
        Context themedContext = new ContextThemeWrapper(dialogScreenContext, themeResId);

        new AlertDialog.Builder(themedContext)
                .setTitle(getString(R.string.gamepad_layout_delete))
                .setMessage(String.format(getString(R.string.gamepad_layout_delete_confirm), layoutName))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> onConfirm.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private interface LayoutNameCallback {
        void onNameEntered(String name);
    }
}
