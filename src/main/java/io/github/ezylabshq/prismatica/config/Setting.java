package io.github.ezylabshq.prismatica.config;

/**
 * One configurable value.
 *
 * <p>Deliberately not a boolean. The earlier module list only had on/off flags,
 * which cannot express "how much" or "which one", so the accent colour, the corner
 * radius, the backdrop style and the watermark contents were all hardcoded.
 *
 * <p>A setting knows its own bounds and how to step, so the interface does not
 * have to special case any of them: click advances, right click goes back.
 *
 * <p>Labels and hints are stored as translation keys and resolved through
 * {@link Lang} on read, not translated once at registration. Resolving at
 * registration would freeze whatever language happened to be active while the
 * class loaded, and the player changes the game language at runtime.
 */
public final class Setting {

    public enum Kind {
        /** On or off. */
        BOOL,
        /** Whole number inside [min, max]. */
        INT,
        /** Decimal inside [fmin, fmax]. */
        FLOAT,
        /** One of a fixed set of labels, optionally carrying a payload. */
        CHOICE
    }

    /** Translation key for the row label, without a ".name" or ".hint" suffix. */
    public final String labelKey;
    public final Kind kind;

    public boolean boolValue;
    public int intValue;
    public float floatValue;
    public int choice;

    public final int min;
    public final int max;
    public final int step;
    public final float fmin;
    public final float fmax;
    public final float fstep;
    public final String[] choices;
    public final int[] choiceValues;

    private Setting(String labelKey, Kind kind,
                    int min, int max, int step, float fmin, float fmax, float fstep,
                    String[] choices, int[] choiceValues) {
        this.labelKey = labelKey;
        this.kind = kind;
        this.min = min;
        this.max = max;
        this.step = step;
        this.fmin = fmin;
        this.fmax = fmax;
        this.fstep = fstep;
        this.choices = choices;
        this.choiceValues = choiceValues;
    }

    public static Setting bool(String labelKey, boolean value) {
        Setting s = new Setting(labelKey, Kind.BOOL, 0, 0, 0, 0f, 0f, 0f, null, null);
        s.boolValue = value;
        return s;
    }

    public static Setting integer(String labelKey, int value, int min, int max, int step) {
        Setting s = new Setting(labelKey, Kind.INT, min, max, step, 0f, 0f, 0f, null, null);
        s.intValue = value;
        return s;
    }

    public static Setting decimal(String labelKey, float value, float min, float max, float step) {
        Setting s = new Setting(labelKey, Kind.FLOAT, 0, 0, 0, min, max, step, null, null);
        s.floatValue = value;
        return s;
    }

    public static Setting choice(String labelKey, int value, String[] choices) {
        return choice(labelKey, value, choices, null);
    }

    /** A choice that also carries a number, used by the accent presets. */
    public static Setting choice(String labelKey, int value, String[] choices, int[] values) {
        Setting s = new Setting(labelKey, Kind.CHOICE, 0, 0, 0, 0f, 0f, 0f, choices, values);
        s.choice = value;
        return s;
    }

    /** Resolved on read, so a language change shows up without a restart. */
    public String label() {
        return Lang.t(labelKey + ".name");
    }

    /** Resolved on read; empty when the key has no hint. */
    public String hint() {
        return Lang.t(labelKey + ".hint");
    }

    public boolean get() {
        return boolValue;
    }

    public int asInt() {
        return intValue;
    }

    public float asFloat() {
        return floatValue;
    }

    /** The payload of the selected choice, or -1 when the choice carries none. */
    public int asChoiceValue() {
        if (choiceValues == null || choice < 0 || choice >= choiceValues.length) {
            return -1;
        }
        return choiceValues[choice];
    }

    public String display() {
        switch (kind) {
            case BOOL:
                return boolValue ? Lang.on() : Lang.off();
            case INT:
                return Integer.toString(intValue);
            case FLOAT:
                // Two decimals: one is enough for a speed, and a third makes
                // the row overflow at small GUI scales.
                return String.format(java.util.Locale.ROOT, "%.2f", floatValue);
            case CHOICE:
                if (choices == null || choice < 0 || choice >= choices.length) {
                    return "?";
                }
                return choices[choice];
            default:
                return "";
        }
    }

    /**
     * Steps the value.
     *
     * @param forward true to increase or advance, false to go back. A boolean
     *                ignores it and flips, because "set it to true" is not a
     *                step: a one way switch that latches on can be turned on but
     *                never off.
     * @return true when the value actually changed, so the caller only saves
     *         on a real edit
     */
    public boolean advance(boolean forward) {
        switch (kind) {
            case BOOL:
                boolValue = !boolValue;
                return true;
            case INT: {
                int next = forward ? intValue + step : intValue - step;
                if (next < min) {
                    next = max;
                }
                if (next > max) {
                    next = min;
                }
                if (next == intValue) {
                    return false;
                }
                intValue = next;
                return true;
            }
            case FLOAT: {
                float next = floatValue + (forward ? fstep : -fstep);
                if (next < fmin) {
                    next = fmin;
                }
                if (next > fmax) {
                    next = fmax;
                }
                // Compare on the rounded value so a step never reads as a no-op
                // because of float noise.
                if (Math.round(next * 100f) == Math.round(floatValue * 100f)) {
                    return false;
                }
                floatValue = next;
                return true;
            }
            case CHOICE: {
                if (choices == null || choices.length == 0) {
                    return false;
                }
                int next = forward ? choice + 1 : choice - 1;
                if (next < 0) {
                    next = choices.length - 1;
                }
                if (next >= choices.length) {
                    next = 0;
                }
                if (next == choice) {
                    return false;
                }
                choice = next;
                return true;
            }
            default:
                return false;
        }
    }
}
