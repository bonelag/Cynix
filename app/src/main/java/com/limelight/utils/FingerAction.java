package com.limelight.utils;

import com.limelight.Game;
import com.limelight.binding.input.KeyboardTranslator;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tuỳ chọn hành động khi tap nhanh 3/4/5 ngón trong khi stream.
 * Id lowercase lưu trong SharedPreferences ("finger_3_action" vân vân),
 * giá trị giữ đồng bộ với string-array finger_action_values trong res/values/arrays.xml.
 *
 * ponytail: tập phím cố định, không đọc file import special_key của user.
 * Upgrade: nạp shortcut tuỳ chỉnh qua KeyConfigHelper vào enum khi user muốn gán shortcut-import làm gesture.
 */
public enum FingerAction {
    NONE,
    SHOW_GAME_MENU,
    TOGGLE_KEYBOARD,
    TOGGLE_FULL_KEYBOARD,
    TOGGLE_HUD,
    TOGGLE_FLOATING_BUTTON,
    TOGGLE_ZOOM_MODE,
    TOGGLE_VIRTUAL_CONTROLLER,
    ROTATE_SCREEN,
    SEND_CLIPBOARD,
    FETCH_CLIPBOARD,
    TASK_MANAGER,
    DISCONNECT,
    QUIT,
    SHOW_SPECIAL_KEYS,
    KEY_ESC,
    KEY_F11,
    KEY_ALT_F4,
    KEY_ALT_ENTER,
    KEY_CTRL_V,
    KEY_WIN,
    KEY_WIN_D,
    KEY_WIN_G,
    KEY_CTRL_ALT_TAB,
    KEY_SHIFT_TAB,
    KEY_WIN_SHIFT_LEFT;

    private static final Map<String, FingerAction> BY_ID = new HashMap<>();
    static {
        for (FingerAction a : values()) {
            BY_ID.put(a.name().toLowerCase(Locale.ROOT), a);
        }
    }

    public static FingerAction fromPref(String id, FingerAction def) {
        if (id == null) return def;
        FingerAction a = BY_ID.get(id);
        return a != null ? a : def;
    }

    /** Thực thi action trong context của Game activity. */
    public void run(Game game) {
        switch (this) {
            case NONE:
                break;
            case SHOW_GAME_MENU:
                game.showGameMenu(null);
                break;
            case TOGGLE_KEYBOARD:
                game.toggleKeyboard();
                break;
            case TOGGLE_FULL_KEYBOARD:
                game.toggleFullKeyboard();
                break;
            case TOGGLE_HUD:
                game.toggleHUD();
                break;
            case TOGGLE_FLOATING_BUTTON:
                game.toggleFloatingButtonVisibility();
                break;
            case TOGGLE_ZOOM_MODE:
                game.toggleZoomMode();
                break;
            case TOGGLE_VIRTUAL_CONTROLLER:
                game.toggleVirtualController();
                break;
            case ROTATE_SCREEN:
                game.rotateScreen();
                break;
            case SEND_CLIPBOARD:
                game.sendClipboard(true);
                break;
            case FETCH_CLIPBOARD:
                game.getClipboard(0);
                break;
            case TASK_MANAGER:
                game.sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_ESCAPE});
                break;
            case DISCONNECT:
                game.disconnect();
                break;
            case QUIT:
                game.quit();
                break;
            case SHOW_SPECIAL_KEYS:
                game.showSpecialKeysMenu();
                break;
            case KEY_ESC:
                game.sendKeys(new short[]{KeyboardTranslator.VK_ESCAPE});
                break;
            case KEY_F11:
                game.sendKeys(new short[]{KeyboardTranslator.VK_F11});
                break;
            case KEY_ALT_F4:
                game.sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_F4});
                break;
            case KEY_ALT_ENTER:
                game.sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_RETURN});
                break;
            case KEY_CTRL_V:
                game.sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_V});
                break;
            case KEY_WIN:
                game.sendKeys(new short[]{KeyboardTranslator.VK_LWIN});
                break;
            case KEY_WIN_D:
                game.sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D});
                break;
            case KEY_WIN_G:
                game.sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G});
                break;
            case KEY_CTRL_ALT_TAB:
                game.sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_TAB});
                break;
            case KEY_SHIFT_TAB:
                game.sendKeys(new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB});
                break;
            case KEY_WIN_SHIFT_LEFT:
                game.sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_LEFT});
                break;
        }
    }
}
