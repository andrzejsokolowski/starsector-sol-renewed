import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Renders the Sol crisis stage icons from the mod's own planet textures (Bugatti's Sol art):
 * a sphere renderer with lighting and atmosphere, a starfield, and glowing streaks for incoming fleets.
 * Usage: java tools/StageIcons.java graphics/planets <output dir>, then copy stage_*.png to graphics/icons/crisis/.
 */
public class StageIcons {
    static final int S = 256;      // render size
    static final int OUT = 64;     // icon size, like the game's own stage icons

    static float[][] r, g, b;      // the canvas, linear 0..1

    public static void main(String[] args) throws Exception {
        File planets = new File(args[0]);
        File out = new File(args[1]);
        out.mkdirs();
        BufferedImage sun = ImageIO.read(new File(planets, "sol.jpg"));
        BufferedImage pluto = ImageIO.read(new File(planets, "pluto.jpg"));
        BufferedImage charon = ImageIO.read(new File(planets, "charon.jpg"));
        BufferedImage mars = ImageIO.read(new File(planets, "mars.jpg"));
        BufferedImage luna = ImageIO.read(new File(planets, "luna.jpg"));
        BufferedImage earth = ImageIO.read(new File(planets, "earth.jpg"));
        BufferedImage clouds = ImageIO.read(new File(planets, "clouds_Earth.png"));

        // START: the Sun, remembered.
        clear(1);
        background(0.10f, 0.04f, 0.02f, 11);
        glow(128, 128, 128, 1.0f, 0.45f, 0.12f, 0.75f);
        glow(128, 128, 92, 1.0f, 0.6f, 0.25f, 0.8f);
        star(sun, 128, 128, 72);
        save(out, "stage_start.png");

        // FOOTHOLD: camps in the dark at the edge of the system.
        clear(2);
        background(0.03f, 0.03f, 0.06f, 22);
        sphere(pluto, 150, 160, 86, -0.85f, -0.5f, 0.2f, 0.2f, null, 0f);
        sphere(charon, 62, 70, 26, -0.85f, -0.5f, 0.2f, 0.6f, null, 0f);
        float[][] camps = {{60, 175}, {95, 205}, {205, 60}, {230, 95}, {40, 120}};
        for (float[] c : camps) {
            glow(c[0], c[1], 18, 1f, 0.15f, 0.05f, 0.9f);
            glow(c[0], c[1], 4, 1f, 0.7f, 0.5f, 1.2f);
        }
        save(out, "stage_foothold.png");

        // RAID: a strike force bearing down on Mars.
        clear(3);
        background(0.05f, 0.025f, 0.02f, 33);
        sphere(mars, 110, 150, 95, -0.75f, -0.35f, 0.56f, 0.1f, new float[] {0.9f, 0.55f, 0.4f}, 0.5f);
        streak(250, 10, 175, 85, 1f, 0.55f, 0.2f, 5);
        streak(230, 50, 190, 112, 1f, 0.55f, 0.2f, 4);
        streak(200, 0, 150, 70, 1f, 0.55f, 0.2f, 4);
        save(out, "stage_raid.png");

        // ASSAULT: a war fleet in the Moon's sky, Earth behind.
        clear(4);
        background(0.07f, 0.02f, 0.02f, 44);
        sphere(earth, 190, 70, 90, -0.7f, -0.2f, 0.68f, 0.35f, new float[] {0.45f, 0.65f, 1f}, 0.8f);
        sphere(clouds, 190, 70, 91, -0.7f, -0.2f, 0.68f, 0.35f, null, 0f, true);
        sphere(luna, 95, 165, 85, -0.7f, -0.2f, 0.68f, 0.0f, null, 0f);
        streak(10, 20, 70, 95, 1f, 0.45f, 0.15f, 4);
        streak(0, 70, 55, 120, 1f, 0.45f, 0.15f, 4);
        streak(250, 250, 160, 200, 1f, 0.45f, 0.15f, 5);
        streak(256, 200, 175, 170, 1f, 0.45f, 0.15f, 4);
        streak(40, 0, 95, 80, 1f, 0.45f, 0.15f, 3);
        burst(118, 120, 1f);
        burst(60, 190, 0.8f);
        save(out, "stage_assault.png");

        // RECKONING: Earth under the armada.
        clear(5);
        background(0.12f, 0.02f, 0.015f, 55);
        glow(128, 128, 128, 1f, 0.2f, 0.05f, 0.5f);
        sphere(earth, 128, 132, 92, -0.75f, -0.3f, 0.6f, 0.15f, new float[] {1f, 0.45f, 0.25f}, 1.1f);
        sphere(clouds, 128, 132, 93, -0.75f, -0.3f, 0.6f, 0.15f, null, 0f, true);
        float[][] from = {{0, 0}, {60, 0}, {256, 10}, {256, 90}, {256, 200}, {200, 256}, {90, 256}, {0, 230}, {0, 120}, {150, 0}};
        for (float[] f : from) {
            float dx = 128 - f[0], dy = 132 - f[1];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            float stop = (len - 108) / len;
            streak(f[0], f[1], f[0] + dx * stop, f[1] + dy * stop, 1f, 0.4f, 0.12f, 4);
        }
        burst(95, 105, 1f);
        burst(150, 160, 0.9f);
        burst(110, 175, 0.8f);
        burst(160, 95, 0.7f);
        save(out, "stage_reckoning.png");
    }

    // -------------------------------------------------------------- canvas

    static Random rnd;

    static void clear(long seed) {
        r = new float[S][S];
        g = new float[S][S];
        b = new float[S][S];
        rnd = new Random(seed);
    }

    static void add(int x, int y, float dr, float dg, float db) {
        if (x < 0 || y < 0 || x >= S || y >= S) return;
        r[y][x] += dr;
        g[y][x] += dg;
        b[y][x] += db;
    }

    static void set(int x, int y, float nr, float ng, float nb, float a) {
        if (x < 0 || y < 0 || x >= S || y >= S) return;
        r[y][x] = r[y][x] * (1 - a) + nr * a;
        g[y][x] = g[y][x] * (1 - a) + ng * a;
        b[y][x] = b[y][x] * (1 - a) + nb * a;
    }

    /** Dark space with a faint tinted haze and a sprinkle of stars. */
    static void background(float tr, float tg, float tb, long seed) {
        Random n = new Random(seed);
        float ox = n.nextFloat() * 10, oy = n.nextFloat() * 10;
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                float haze = (float) (0.55 + 0.45 * Math.sin(x * 0.021 + ox) * Math.cos(y * 0.017 + oy));
                float d = (float) Math.hypot(x - S / 2.0, y - S / 2.0) / S;
                float v = haze * (1.1f - d);
                r[y][x] = tr * v;
                g[y][x] = tg * v;
                b[y][x] = tb * v;
            }
        }
        for (int i = 0; i < 260; i++) {
            int x = n.nextInt(S), y = n.nextInt(S);
            float br = 0.25f + n.nextFloat() * 0.75f;
            br *= br;
            add(x, y, br, br, br * 1.05f);
            if (br > 0.6f) {
                add(x + 1, y, br * 0.4f, br * 0.4f, br * 0.4f);
                add(x, y + 1, br * 0.4f, br * 0.4f, br * 0.4f);
            }
        }
    }

    static void glow(float cx, float cy, float radius, float cr, float cg, float cb, float strength) {
        int x0 = (int) (cx - radius), x1 = (int) (cx + radius), y0 = (int) (cy - radius), y1 = (int) (cy + radius);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float d = (float) Math.hypot(x - cx, y - cy) / radius;
                if (d >= 1) continue;
                float f = (1 - d) * (1 - d) * strength;
                add(x, y, cr * f, cg * f, cb * f);
            }
        }
    }

    /** A bright head at (hx, hy) with a fading tail back to (tx, ty): a fleet burning in. */
    static void streak(float tx, float ty, float hx, float hy, float cr, float cg, float cb, float width) {
        float dx = hx - tx, dy = hy - ty;
        float len = (float) Math.hypot(dx, dy);
        int steps = (int) (len * 2);
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float x = tx + dx * t, y = ty + dy * t;
            float w = width * (0.3f + 0.7f * t);
            float a = t * t * 0.18f;
            for (int oy = (int) -w - 1; oy <= w + 1; oy++) {
                for (int ox = (int) -w - 1; ox <= w + 1; ox++) {
                    float d = (float) Math.hypot(ox, oy) / w;
                    if (d >= 1) continue;
                    float f = (1 - d) * a;
                    add((int) (x + ox), (int) (y + oy), cr * f, cg * f, cb * f);
                }
            }
        }
        glow(hx, hy, width * 3.5f, cr, cg, cb, 0.9f);
        glow(hx, hy, width * 1.2f, 1f, 0.95f, 0.85f, 1.4f);
    }

    static void burst(float cx, float cy, float s) {
        glow(cx, cy, 22 * s, 1f, 0.35f, 0.08f, 0.9f);
        glow(cx, cy, 9 * s, 1f, 0.8f, 0.45f, 1.3f);
    }

    // -------------------------------------------------------------- spheres

    static float[] sample(BufferedImage tex, float u, float v) {
        int w = tex.getWidth(), h = tex.getHeight();
        float fx = u * w - 0.5f, fy = v * h - 0.5f;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        float ax = fx - x0, ay = fy - y0;
        float[] out = new float[4];
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 2; i++) {
                int x = Math.floorMod(x0 + i, w), y = Math.min(h - 1, Math.max(0, y0 + j));
                int argb = tex.getRGB(x, y);
                float wgt = (i == 0 ? 1 - ax : ax) * (j == 0 ? 1 - ay : ay);
                out[0] += ((argb >> 16) & 255) / 255f * wgt;
                out[1] += ((argb >> 8) & 255) / 255f * wgt;
                out[2] += (argb & 255) / 255f * wgt;
                out[3] += ((argb >>> 24) & 255) / 255f * wgt;
            }
        }
        for (int k = 0; k < 3; k++) out[k] = (float) Math.pow(out[k], 2.2);
        return out;
    }

    static void sphere(BufferedImage tex, float cx, float cy, float rad, float lx, float ly, float lz, float spin,
                       float[] atmo, float atmoStrength) {
        sphere(tex, cx, cy, rad, lx, ly, lz, spin, atmo, atmoStrength, false);
    }

    /** A lit planet; with `overlay` the texture's alpha lays it over what is there (clouds). */
    static void sphere(BufferedImage tex, float cx, float cy, float rad, float lx, float ly, float lz, float spin,
                       float[] atmo, float atmoStrength, boolean overlay) {
        float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= ll; ly /= ll; lz /= ll;
        int x0 = (int) (cx - rad - 12), x1 = (int) (cx + rad + 12), y0 = (int) (cy - rad - 12), y1 = (int) (cy + rad + 12);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float nx = (x + 0.5f - cx) / rad, ny = (y + 0.5f - cy) / rad;
                float d2 = nx * nx + ny * ny;
                if (d2 > 1f) {
                    if (atmo != null && !overlay) {
                        float d = (float) Math.sqrt(d2);
                        float halo = d < 1.12f ? (1.12f - d) / 0.12f : 0f;
                        float lit = Math.max(0f, (nx * lx + ny * ly) / d * 0.8f + 0.15f);
                        float f = halo * halo * lit * atmoStrength * 0.6f;
                        add(x, y, atmo[0] * f, atmo[1] * f, atmo[2] * f);
                    }
                    continue;
                }
                float nz = (float) Math.sqrt(1f - d2);
                double lon = Math.atan2(nx, nz);
                double lat = Math.asin(-ny);
                float u = (float) (lon / (2 * Math.PI) + 0.5 + spin);
                u -= Math.floor(u);
                float v = (float) (0.5 - lat / Math.PI);
                float[] c = sample(tex, u, v);
                float diff = Math.max(0f, nx * lx + ny * ly + nz * lz);
                float shade = 0.02f + diff * 1.05f;
                float edge = d2 > 0.96f ? (1f - d2) / 0.04f : 1f;
                if (overlay) {
                    float a = c[3] * 0.85f * edge;
                    set(x, y, c[0] * shade, c[1] * shade, c[2] * shade, a);
                    continue;
                }
                float a = edge;
                float cr = c[0] * shade, cg = c[1] * shade, cb = c[2] * shade;
                if (atmo != null) {
                    float rim = (float) Math.pow(1f - nz, 2.5) * (0.2f + diff) * atmoStrength;
                    cr += atmo[0] * rim; cg += atmo[1] * rim; cb += atmo[2] * rim;
                }
                set(x, y, cr, cg, cb, a);
            }
        }
    }

    /** The Sun: full bright, darker toward the limb. */
    static void star(BufferedImage tex, float cx, float cy, float rad) {
        for (int y = (int) (cy - rad); y <= cy + rad; y++) {
            for (int x = (int) (cx - rad); x <= cx + rad; x++) {
                float nx = (x + 0.5f - cx) / rad, ny = (y + 0.5f - cy) / rad;
                float d2 = nx * nx + ny * ny;
                if (d2 > 1f) continue;
                float nz = (float) Math.sqrt(1f - d2);
                double lon = Math.atan2(nx, nz), lat = Math.asin(-ny);
                float u = (float) (lon / (2 * Math.PI) + 0.5);
                float v = (float) (0.5 - lat / Math.PI);
                float[] c = sample(tex, u, v);
                float limb = 0.55f + 0.45f * nz;
                float e = d2 > 0.97f ? (1f - d2) / 0.03f : 1f;
                float lum = (c[0] + c[1] + c[2]) / 3f;
                float k2 = (0.35f + lum * 0.9f) * limb;
                set(x, y, 1.15f * k2, 0.62f * k2 * (0.7f + 0.3f * nz), 0.22f * k2 * nz, e);
            }
        }
    }

    // ---------------------------------------------------------------- output

    /** Box-filters the 256 canvas down to 64, tone-maps and writes an opaque PNG. */
    static void save(File dir, String name) throws Exception {
        int k = S / OUT;
        BufferedImage img = new BufferedImage(OUT, OUT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < OUT; y++) {
            for (int x = 0; x < OUT; x++) {
                float sr = 0, sg = 0, sb = 0;
                for (int j = 0; j < k; j++) {
                    for (int i = 0; i < k; i++) {
                        sr += r[y * k + j][x * k + i];
                        sg += g[y * k + j][x * k + i];
                        sb += b[y * k + j][x * k + i];
                    }
                }
                float n = k * k;
                img.setRGB(x, y, (tone(sr / n) << 16) | (tone(sg / n) << 8) | tone(sb / n));
            }
        }
        ImageIO.write(img, "png", new File(dir, name));
        // A large preview for checking the picture, not shipped.
        BufferedImage big = new BufferedImage(S, S, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < S; y++) for (int x = 0; x < S; x++)
            big.setRGB(x, y, (tone(r[y][x]) << 16) | (tone(g[y][x]) << 8) | tone(b[y][x]));
        ImageIO.write(big, "png", new File(dir, "preview_" + name));
    }

    static int tone(float v) {
        float m = v / (1f + v * 0.35f);       // soft shoulder for glows
        float s = (float) Math.pow(Math.max(0f, Math.min(1f, m * 1.12f)), 1 / 2.2);
        return Math.round(s * 255f);
    }
}
