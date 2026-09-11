package code.window;

import code.store.PermitStore;
import game.world.Sector;

/**
 * A single row in the {@link StationsAnywhereWindow} table: a snapshot of one
 * restricted (special-zone) sector plus cached display strings.
 *
 * <p>Sectors are identified by their stable grid coordinates. Undiscovered
 * sectors ({@code !explored}) are masked: their name and coordinates are hidden
 * behind question marks and the row is drawn greyed-out and is not clickable, so
 * a permit can only be granted once the player has actually found the zone.</p>
 */
public class StationsAnywhereRow {

    public final int sectorX;
    public final int sectorY;
    public final boolean explored;

    /** Display name (real when explored, "??????" when not). */
    public final String name;
    /** Coordinates string (real when explored, "(?? / ??)" when not). */
    public final String coords;
    public final String region;
    public final String type;

    /**
     * Grouping key for keeping a "family" of related zones together in the list
     * (e.g. the parent "The Bastion" and its "The Bastion Wall"). Derived from the
     * first two words of the real name, so a shared parent prefix groups them.
     * Empty for undiscovered zones (they are pooled at the bottom, unsorted by
     * family since their names are hidden).
     */
    public final String familyKey;

    /** Squared distance from the galactic origin (0, 0); stable sort primary. */
    public final long distSq;

    /** Whether a station-deploy permit is currently granted for this sector. */
    public boolean enabled;

    public StationsAnywhereRow(Sector sector) {
        this.sectorX = safeX(sector);
        this.sectorY = safeY(sector);
        this.explored = isExplored(sector);
        this.distSq = (long) sectorX * sectorX + (long) sectorY * sectorY;

        if (this.explored) {
            this.name = safeName(sector);
            this.coords = "(" + sectorX + " / " + sectorY + ")";
            this.region = safeRegion(sector);
            this.type = safeType(sector);
            this.familyKey = familyKeyOf(this.name);
        } else {
            this.name = "??????";
            this.coords = "(?? / ??)";
            this.region = "??????";
            this.type = "??????";
            this.familyKey = "";
        }

        this.enabled = PermitStore.isEnabled(sectorX, sectorY);
    }

    /** Re-read the permit flag from the store (cheap). */
    public void refreshEnabled() {
        this.enabled = PermitStore.isEnabled(sectorX, sectorY);
    }

    /**
     * Derive a family grouping key from a zone name using its first two words, so
     * a parent and its children group together (e.g. "The Bastion" and "The
     * Bastion Wall" both key to "the bastion"). Single-word names key to
     * themselves.
     */
    private static String familyKeyOf(String name) {
        if (name == null) {
            return "";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 0) {
            return "";
        }
        if (parts.length == 1) {
            return parts[0].toLowerCase();
        }
        return (parts[0] + " " + parts[1]).toLowerCase();
    }

    private static int safeX(Sector sector) {
        try {
            return sector.getSectorX();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int safeY(Sector sector) {
        try {
            return sector.getSectorY();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean isExplored(Sector sector) {
        try {
            return sector.isExplored();
        } catch (Throwable t) {
            return false;
        }
    }

    private static String safeName(Sector sector) {
        try {
            String n = sector.getName();
            if (n != null && n.trim().length() > 0) {
                return n.trim();
            }
        } catch (Throwable ignored) {
        }
        // Fall back to a descriptive label if the sector carries no proper name.
        try {
            String t = sector.getTypeName();
            if (t != null && t.trim().length() > 0) {
                return t.trim();
            }
        } catch (Throwable ignored) {
        }
        return "Special Zone";
    }

    private static String safeRegion(Sector sector) {
        try {
            String r = sector.getRegionName();
            if (r != null && r.trim().length() > 0) {
                return r.trim();
            }
        } catch (Throwable ignored) {
        }
        return "-";
    }

    private static String safeType(Sector sector) {
        try {
            String t = sector.getTypeName();
            if (t != null && t.trim().length() > 0) {
                return t.trim();
            }
        } catch (Throwable ignored) {
        }
        return "-";
    }
}
