import io.github.ezylabshq.prismatica.ui.Theme;
import io.github.ezylabshq.prismatica.ui.WorldTheme;

/**
 * Offline check that the world driven palette actually moves.
 *
 * <p>Feeds synthetic worlds (biome colours x time of day x weather) into the
 * sampler and prints the resulting accent, so the reactive theme can be
 * verified without launching the game.
 */
public class ThemeProbe {

    public static void main(String[] args) {
        System.out.printf("%-22s %-9s %-9s %-9s %s%n", "scene", "target", "current", "panel", "settled?");
        Scene[] scenes = {
                new Scene("plains / noon", 0x78A7FF, 0x91BD59, 0x3F76E4, 6000, 0f),
                new Scene("plains / sunset", 0x78A7FF, 0x91BD59, 0x3F76E4, 12000, 0f),
                new Scene("plains / midnight", 0x78A7FF, 0x91BD59, 0x3F76E4, 18000, 0f),
                new Scene("plains / rain", 0x78A7FF, 0x91BD59, 0x3F76E4, 6000, 1f),
                new Scene("desert / noon", 0xB4D9FF, 0xBFB755, 0x32A598, 6000, 0f),
                new Scene("swamp / noon", 0x6A9FB5, 0x6A7039, 0x3F76E4, 6000, 0f),
                new Scene("ice spikes / noon", 0x80A3FF, 0x80B497, 0x3D57D6, 6000, 0f),
                new Scene("nether-ish / noon", 0x3A0A0A, 0x6A2B1A, 0xB04020, 6000, 0f),
        };

        int lastScene = -1;
        for (Scene scene : scenes) {
            apply(scene, 0f);
            int before = Theme.targetAccent();
            for (int i = 0; i < 600; i++) { // 10 seconds: let the spring settle
                apply(scene, 1f / 60f);
            }
            int after = Theme.targetAccent();
            if (before != after) {
                System.out.println("  !! target drifted during scene: "
                        + String.format("%06X -> %06X", before, after));
            }
            lastScene++;
            boolean settled = channelDiff(Theme.accent(), Theme.targetAccent()) <= 2;
            System.out.printf("%-22s %-9s %-9s %-9s %s%n",
                    scene.name, hex(Theme.targetAccent()), hex(Theme.accent()),
                    hex(Theme.panel()), settled ? "yes" : "NO");
        }
    }

    private static void apply(Scene scene, float dt) {
        WorldTheme.override(scene.sky, scene.grass, scene.water, scene.dayTime, scene.rain, true);
        WorldTheme.sample(dt);
        Theme.tick(dt);
    }

    private static int channelDiff(int a, int b) {
        int worst = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            worst = Math.max(worst, Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF)));
        }
        return worst;
    }

    private static String hex(int rgb) {
        return String.format("%02X%02X%02X", (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    private static final class Scene {
        final String name;
        final int sky;
        final int grass;
        final int water;
        final long dayTime;
        final float rain;

        Scene(String name, int sky, int grass, int water, long dayTime, float rain) {
            this.name = name;
            this.sky = sky;
            this.grass = grass;
            this.water = water;
            this.dayTime = dayTime;
            this.rain = rain;
        }
    }
}
