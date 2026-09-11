package code.store;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import game.world.Galaxy;
import game.world.Sector;

/**
 * Persistent record of which restricted (special-zone) sectors the player has
 * granted a station-deploy permit to.
 *
 * <p>The set is keyed by the sector's grid coordinates ({@code sectorX_sectorY}),
 * which are stable across sessions and world-gen (unlike a sector's live object
 * contents). Data is stored in a small text file per world, one key per line,
 * kept in {@code stationsanywhere_data/} next to the game executable. Writes are
 * flushed immediately (write-through) whenever a permit is toggled, and the file
 * for the active world is (re)loaded lazily whenever the world changes, so no
 * hook into the game's own save system is required.</p>
 *
 * <p>All methods are defensive: any I/O failure is logged and swallowed rather
 * than propagated, so a corrupt or unwritable data file never crashes the game
 * or blocks a deploy.</p>
 */
public final class PermitStore {

    private static final String DATA_DIR = "stationsanywhere_data";

    private static final Set<String> ENABLED = new HashSet<String>();

    /** The world filename currently loaded into {@link #ENABLED} (null = none). */
    private static String loadedWorld = null;

    private PermitStore() {
    }

    /** Stable identity for a sector: its grid coordinates. */
    public static String keyFor(int sectorX, int sectorY) {
        return sectorX + "_" + sectorY;
    }

    public static String keyFor(Sector sector) {
        return keyFor(sector.getSectorX(), sector.getSectorY());
    }

    public static synchronized boolean isEnabled(Sector sector) {
        if (sector == null) {
            return false;
        }
        try {
            ensureLoaded();
            return ENABLED.contains(keyFor(sector));
        } catch (Throwable t) {
            // Never let a store failure block the vanilla deploy path.
            System.out.println("[StationsAnywhere] isEnabled failed: " + t);
            return false;
        }
    }

    public static synchronized boolean isEnabled(int sectorX, int sectorY) {
        try {
            ensureLoaded();
            return ENABLED.contains(keyFor(sectorX, sectorY));
        } catch (Throwable t) {
            System.out.println("[StationsAnywhere] isEnabled failed: " + t);
            return false;
        }
    }

    /** Enable or disable the station-deploy permit for a sector, then flush. */
    public static synchronized void setEnabled(int sectorX, int sectorY, boolean enabled) {
        try {
            ensureLoaded();
            String key = keyFor(sectorX, sectorY);
            boolean changed;
            if (enabled) {
                changed = ENABLED.add(key);
            } else {
                changed = ENABLED.remove(key);
            }
            if (changed) {
                save();
            }
        } catch (Throwable t) {
            System.out.println("[StationsAnywhere] setEnabled failed: " + t);
        }
    }

    /** Flip the permit for a sector and return the new state. */
    public static synchronized boolean toggle(int sectorX, int sectorY) {
        boolean now = !isEnabled(sectorX, sectorY);
        setEnabled(sectorX, sectorY, now);
        return now;
    }

    // --- persistence -------------------------------------------------------

    private static void ensureLoaded() {
        String world = Galaxy.currentWorldFilename;
        if (world == null || world.length() == 0) {
            world = "__default__";
        }
        if (!world.equals(loadedWorld)) {
            load(world);
        }
    }

    private static File fileFor(String world) {
        File dir = new File(DATA_DIR);
        return new File(dir, safeFileName(world) + ".txt");
    }

    private static String safeFileName(String world) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < world.length(); i++) {
            char c = world.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        if (sb.length() == 0) {
            sb.append("world");
        }
        return sb.toString();
    }

    private static void load(String world) {
        ENABLED.clear();
        loadedWorld = world;
        File f = fileFor(world);
        if (!f.exists()) {
            return;
        }
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null) {
                String key = line.trim();
                if (key.length() == 0) {
                    continue;
                }
                ENABLED.add(key);
            }
        } catch (Throwable t) {
            // Corrupt or unreadable file: start fresh rather than crash the game.
            System.out.println("[StationsAnywhere] Failed to load permit data: " + t);
        } finally {
            closeQuietly(in);
        }
    }

    private static void save() {
        if (loadedWorld == null) {
            return;
        }
        File f = fileFor(loadedWorld);
        BufferedWriter out = null;
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
            for (String key : ENABLED) {
                out.write(key);
                out.newLine();
            }
        } catch (Throwable t) {
            System.out.println("[StationsAnywhere] Failed to save permit data: " + t);
        } finally {
            closeQuietly(out);
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
