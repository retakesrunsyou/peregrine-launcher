package net.peregrine.client.core;

/** Short, readable names for GLFW key codes (what every Minecraft version uses). */
public final class Keys {

    public static final int NONE = -1;
    public static final int KEY_DELETE = 261;

    private Keys() {
    }

    public static String name(int key) {
        if (key < 0) {
            return "None";
        }
        if (key >= 48 && key <= 57) {
            return String.valueOf((char) key);           // 0-9
        }
        if (key >= 65 && key <= 90) {
            return String.valueOf((char) key);           // A-Z
        }
        if (key >= 290 && key <= 314) {
            return "F" + (key - 289);                    // F1-F25
        }
        if (key >= 320 && key <= 329) {
            return "Num " + (key - 320);                 // keypad digits
        }
        switch (key) {
            case 32: return "Space";
            case 39: return "'";
            case 44: return ",";
            case 45: return "-";
            case 46: return ".";
            case 47: return "/";
            case 59: return ";";
            case 61: return "=";
            case 91: return "[";
            case 92: return "\\";
            case 93: return "]";
            case 96: return "`";
            case 257: return "Enter";
            case 258: return "Tab";
            case 260: return "Insert";
            case 261: return "Delete";
            case 262: return "Right";
            case 263: return "Left";
            case 264: return "Down";
            case 265: return "Up";
            case 266: return "Page Up";
            case 267: return "Page Down";
            case 268: return "Home";
            case 269: return "End";
            case 280: return "Caps Lock";
            case 330: return "Num .";
            case 331: return "Num /";
            case 332: return "Num *";
            case 333: return "Num -";
            case 334: return "Num +";
            case 335: return "Num Enter";
            case 340: return "L Shift";
            case 341: return "L Ctrl";
            case 342: return "L Alt";
            case 344: return "R Shift";
            case 345: return "R Ctrl";
            case 346: return "R Alt";
            default: return "Key " + key;
        }
    }
}
