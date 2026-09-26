package io.github.ezylabshq.prismatica.config;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Language of the Prismatica interface, taken from the game's own setting.
 *
 * <p>Follows whatever the player picked in Options rather than carrying a
 * second, independent language option: a client that can disagree with the game
 * about the language is a support problem, and the game's list already covers
 * every language the player can be reading.
 *
 * <p>{@link #detect()} is called each time the interface is opened, not once at
 * startup, so changing the language in Options takes effect on the next open
 * without restarting the game.
 *
 * <p>Two things are deliberately not translated: the mod name, and the words on
 * the switches. A switch reads ON and OFF in every client, and translating a
 * two letter token into something longer is what makes a settings row overflow.
 */
public final class Lang {

    public static final String FALLBACK = "en_us";

    /** Russian, in both the ru_ru and ru_ru_old form Minecraft 1.16.5 can report. */
    private static final String RU = "ru_ru";

    private static String code = FALLBACK;
    private static boolean detected;

    private static final Map<String, String> EN = new HashMap<>();
    private static final Map<String, String> RU_WORDS = new HashMap<>();

    private Lang() {
    }

    // ---------------------------------------------------------------------
    // Vocabulary. Kept as one table keyed by language so the two stay in step:
    // a key present in EN but missing in RU silently falls back to English,
    // which is better than an empty label, and the fallback is visible in the
    // source rather than in a report.
    // ---------------------------------------------------------------------

    private static void put(String key, String en, String ru) {
        EN.put(key, en);
        RU_WORDS.put(key, ru);
    }

    static {
        // Chrome
        put("chrome.wordmark.subtitle", "visual client for 1.16.5", "визуальный клиент для 1.16.5");
        put("chrome.screen.options", "OPTIONS", "НАСТРОЙКИ");
        put("chrome.screen.worlds", "WORLDS", "МИРЫ");
        put("chrome.screen.servers", "SERVERS", "СЕРВЕРЫ");
        put("chrome.screen.paused", "PAUSED", "ПАУЗА");
        put("chrome.sub.singleplayer", "singleplayer", "одиночная игра");
        put("chrome.sub.multiplayer", "multiplayer", "сетевая игра");
        put("chrome.sub.video", "video, controls, audio", "графика, управление, звук");
        put("chrome.sub.game", "game", "игра");

        // Interface
        put("category.ui", "INTERFACE", "ИНТЕРФЕЙС");
        put("category.render", "RENDER", "РЕНДЕР");
        put("category.hud", "HUD", "HUD");
        put("category.settings", "SETTINGS", "НАСТРОЙКИ");

        // Modules
        put("module.rounded.name", "Rounded Corners", "Скруглённые углы");
        put("module.rounded.hint", "Soft 9px radius", "Мягкое скругление");
        put("module.smooth.name", "Smooth Animations", "Плавные анимации");
        put("module.smooth.hint", "Eased transitions", "Плавные переходы");
        put("module.momentum.name", "Scroll Momentum", "Инерция прокрутки");
        put("module.momentum.hint", "Inertial scrolling", "Плавная прокрутка");
        put("module.border.name", "Accent Border", "Акцентная рамка");
        put("module.border.hint", "1px accent outline", "Контур в 1 пиксель");
        put("module.watermark.name", "Watermark", "Ватермарка");
        put("module.watermark.hint", "Corner info line", "Строка в углу");

        // Settings, grouped the same way the sidebar groups them
        put("setting.reactive.name", "Reactive theme", "Тема из мира");
        put("setting.reactive.hint", "Follow the world you are in", "Следует за миром");
        put("setting.accent.name", "Accent", "Акцент");
        put("setting.accent.hint", "Used when the theme is not reactive", "Когда тема не из мира");
        put("setting.speed.name", "React speed", "Скорость реакции");
        put("setting.speed.hint", "How fast the palette follows", "Как быстро меняется палитра");

        put("setting.rounded.name", "Rounded corners", "Скруглённые углы");
        put("setting.rounded.hint", "Soften every panel edge", "Смягчить все панели");
        put("setting.radius.name", "Corner radius", "Радиус скругления");
        put("setting.radius.hint", "Pixels, 0 to 14", "Пиксели, от 0 до 14");
        put("setting.accentBorder.name", "Accent border", "Акцентная рамка");
        put("setting.accentBorder.hint", "Outline panels in the accent", "Обводка панелей акцентом");

        put("setting.backdrop.name", "Backdrop", "Фон");
        put("setting.backdrop.hint", "Menu background style", "Стиль фона меню");
        put("setting.backdropOpacity.name", "Backdrop opacity", "Прозрачность фона");
        put("setting.backdropOpacity.hint", "0 is transparent, 1 is solid", "0 — прозрачно, 1 — плотно");
        put("setting.header.name", "Header strip", "Шапка");
        put("setting.header.hint", "Name of the screen you are in", "Название текущего экрана");
        put("setting.headerFont.name", "Header font", "Шрифт шапки");
        put("setting.headerFont.hint", "Small pixel caps, or the vanilla one", "Пиксельный или обычный");
        put("setting.buttons.name", "Style menu buttons", "Стиль кнопок");
        put("setting.buttons.hint", "Apply the look to menu buttons", "Применить стиль к кнопкам");
        put("setting.wordmark.name", "Main menu wordmark", "Логотип в главном меню");
        put("setting.wordmark.hint", "Title on the main menu", "Название в главном меню");

        put("setting.watermark.name", "Watermark", "Ватермарка");
        put("setting.watermark.hint", "Corner info line", "Строка в углу");
        put("setting.fps.name", "Show fps", "Показывать FPS");
        put("setting.fps.hint", "Frames per second in the watermark", "Кадров в секунду");
        put("setting.biome.name", "Show biome", "Показывать биом");
        put("setting.biome.hint", "Current biome in the watermark", "Текущий биом");
        put("setting.coords.name", "Show coords", "Показывать координаты");
        put("setting.coords.hint", "Position in the watermark", "Позиция игрока");
        put("setting.corner.name", "Watermark corner", "Угол ватермарки");
        put("setting.corner.hint", "Which corner it sits in", "В каком углу показывать");

        put("setting.smooth.name", "Smooth animations", "Плавные анимации");
        put("setting.smooth.hint", "Ease every transition", "Плавные переходы");
        put("setting.momentum.name", "Scroll momentum", "Инерция прокрутки");
        put("setting.momentum.hint", "Inertial list scrolling", "Плавная прокрутка списка");
        put("setting.hover.name", "Hover speed", "Скорость наведения");
        put("setting.hover.hint", "How fast buttons light up", "Как быстро кнопки подсвечиваются");

        // Switch and value vocabulary that does get translated
        put("value.on", "ON", "ВКЛ");
        put("value.off", "OFF", "ВЫКЛ");
        put("hint.click", "LMB / RMB", "ЛКМ / ПКМ");
        put("key.open", "Open Prismatica menu", "Открыть меню Prismatica");
        put("key.openAlt", "Open Prismatica menu (alt)", "Открыть меню Prismatica (доп.)");
        put("key.category", "Prismatica", "Prismatica");
    }

    /**
     * Reads the game's selected language.
     *
     * <p>The chain is {@code Minecraft.getLanguageManager().getSelected().getCode()}.
     * Written against the official 1.16.5 names, which ForgeGradle remaps to
     * {@code func_135016_M()} and {@code func_135041_c()} in the built jar;
     * calling the SRG names here would not compile.
     *
     * <p>Safe to call before the resource reload: if no language is loaded yet
     * the fallback is kept, and the next call will pick the real one up.
     */
    public static void detect() {
        detected = true;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return;
            }
            net.minecraft.client.resources.LanguageManager manager = mc.getLanguageManager();
            if (manager == null) {
                return;
            }
            net.minecraft.client.resources.Language language = manager.getSelected();
            if (language == null) {
                return;
            }
            String selected = language.getCode();
            if (selected == null || selected.isEmpty()) {
                return;
            }
            // "ru_ru" and "ru_ru_old" both mean Russian here; everything else is
            // only distinguished from English, which the fallback covers.
            code = selected.toLowerCase(Locale.ROOT).startsWith("ru_") ? RU : selected.toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            // Language loading races the first frame sometimes. Falling back is
            // correct here; throwing would take the menu down.
            code = FALLBACK;
        }
    }

    public static String code() {
        if (!detected) {
            detect();
        }
        return code;
    }

    public static boolean isRussian() {
        return RU.equals(code());
    }

    /**
     * True when the text can be drawn in the 3x5 pixel font.
     *
     * <p>That font only covers printable ASCII, so a Russian label has to be
     * drawn with the vanilla renderer instead. Guessing this per string is what
     * would leave holes in a heading, so the answer comes from the font itself.
     */
    public static boolean microCapable(String s) {
        if (s == null || s.isEmpty()) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toUpperCase(s.charAt(i));
            if (c < 32 || c > 126) {
                return false;
            }
        }
        return true;
    }

    /**
     * The translation for {@code key}, or the key itself if there is none.
     *
     * <p>Returning the key rather than an empty string is deliberate: a missing
     * translation is then visible as {@code setting.foo.name} in the interface
     * and easy to spot, instead of a blank row.
     */
    public static String t(String key) {
        if (key == null) {
            return "";
        }
        if (!detected) {
            detect();
        }
        String value = isRussian() ? RU_WORDS.get(key) : EN.get(key);
        if (value == null) {
            value = EN.get(key);
        }
        return value != null ? value : key;
    }

    public static String on() {
        return t("value.on");
    }

    public static String off() {
        return t("value.off");
    }
}
