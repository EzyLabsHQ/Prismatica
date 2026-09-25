import java.lang.reflect.Method;

/** Prints what WorldTheme.mixMany actually returns, isolated from the spring. */
public class MixProbe {
    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("io.github.ezylabshq.prismatica.ui.WorldTheme");
        Method m = c.getDeclaredMethod("mixMany", int.class, int.class, int.class, float.class, float.class);
        m.setAccessible(true);

        int sky = 0x78A7FF;
        int grass = 0x91BD59;
        int water = 0x3F76E4;

        System.out.printf("mixMany(plains, noon) = %06X%n", (Integer) m.invoke(null, sky, grass, water, 1.0f, 0.0f));
        System.out.printf("mixMany(plains, night)= %06X%n", (Integer) m.invoke(null, sky, grass, water, 0.0f, 0.0f));
        System.out.printf("mixMany(nether, noon) = %06X%n", (Integer) m.invoke(null, 0x3A0A0A, 0x6A2B1A, 0xB04020, 1.0f, 0.0f));

        Method mix = c.getDeclaredMethod("mix", int.class, int.class, float.class);
        mix.setAccessible(true);
        int base = (Integer) mix.invoke(null, sky, grass, 0.38f);
        int withWater = (Integer) mix.invoke(null, base, water, 0.14f);
        System.out.printf("base=%06X withWater=%06X%n", base, withWater);

        Method hsb = c.getDeclaredMethod("rgbToHsb", int.class, int.class, int.class);
        hsb.setAccessible(true);
        float[] v = (float[]) hsb.invoke(null, (withWater >> 16) & 0xFF, (withWater >> 8) & 0xFF, withWater & 0xFF);
        System.out.printf("h=%.4f s=%.4f v=%.4f%n", v[0], v[1], v[2]);

        Method back = c.getDeclaredMethod("hsbToRgb", float.class, float.class, float.class);
        back.setAccessible(true);
        int[] rgb = (int[]) back.invoke(null, v[0], v[1], 0.84f);
        System.out.printf("hsbToRgb -> %d,%d,%d%n", rgb[0], rgb[1], rgb[2]);
    }
}
