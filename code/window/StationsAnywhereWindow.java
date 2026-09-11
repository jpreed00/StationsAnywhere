package code.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import code.store.PermitStore;
import game.Player;
import game.graphics.GraphicsLoader;
import game.world.Galaxy;
import game.world.Sector;
import game.world.SectorGrid;
import illuminatus.core.datastructures.Stack;
import illuminatus.core.graphics.Color;
import illuminatus.core.graphics.Draw;
import illuminatus.core.graphics.text.Text;
import illuminatus.core.io.Mouse;
import menu.components.GenericWindow;
import menu.components.HoverTip;
import menu.components.TextureButton;

/**
 * A draggable window that lists every restricted (special-zone) sector in the
 * galaxy and lets the player grant a station-deploy permit to each one via a
 * checkbox.
 *
 * <p>Discovered zones show their real name and coordinates and their checkbox is
 * clickable; undiscovered zones are masked ("??????" / "(?? / ??)"), drawn
 * greyed-out, and are not clickable until found. Permits persist per world in
 * {@link PermitStore}. Unchecking a sector only stops future deploys - it never
 * removes stations already placed.</p>
 *
 * <p>The window is a singleton: {@link #toggle()} opens or closes it. The row
 * list is rebuilt only on discrete events (open, sector-enter) since the set of
 * special zones is fixed at world generation.</p>
 */
public class StationsAnywhereWindow extends GenericWindow {

    private static final int WIDTH = 620;
    private static final int HEIGHT = 430;
    private static final int DEFAULT_X = 180;
    private static final int DEFAULT_Y = 90;

    private static final int MIN_WIDTH = 560;
    private static final int MAX_WIDTH = 1100;
    private static final int MIN_HEIGHT = 170;
    private static final int MAX_HEIGHT = 950;

    private static final int PAD = 6;
    // Matches the game's list windows: an 18px title-bar pitch with text drawn
    // flush at the top of each band and a horizontal divider 18px down.
    private static final int TITLE_H = 18;
    private static final int HEADER_Y = 19;
    private static final int HEADER_LINE = 36;
    private static final int CONTENT_TOP = 38;
    // Slim bottom band that holds the resize handle, matching the game's -17 corner.
    private static final int FOOTER_H = 18;
    private static final int ICON = 16;
    private static final int ROW_H = 16;

    // Text alignment per column.
    private static final int ALIGN_LEFT = 0;
    private static final int ALIGN_CENTER = 1;
    private static final int ALIGN_RIGHT = 2;

    // Column identity (index into the computed layout arrays).
    private static final int C_ENABLE = 0;
    private static final int C_NAME = 1;
    private static final int C_COORDS = 2;
    private static final int C_REGION = 3;
    private static final int C_TYPE = 4;
    private static final int COLS = 5;

    private static final String[] HEADERS = {"Deploy", "Name", "Sector", "Region", "Type"};
    private static final int[] ALIGN = {ALIGN_CENTER, ALIGN_LEFT, ALIGN_CENTER, ALIGN_LEFT, ALIGN_LEFT};
    // Minimum width per column. Extra window width is shared among the flexible
    // text columns (Name/Region/Type) using the weights below.
    private static final int[] MIN_W = {54, 150, 92, 110, 130};
    private static final float[] FLEX = {0f, 0.5f, 0f, 0.2f, 0.3f};

    // Computed each draw from the current window width.
    private final int[] colX = new int[COLS];
    private final int[] colW = new int[COLS];

    /** The single live instance, or null when closed. */
    public static StationsAnywhereWindow instance;

    private String title = "Stations Anywhere";
    private final ArrayList<StationsAnywhereRow> rows = new ArrayList<StationsAnywhereRow>();
    private int scrollRow = 0;

    private TextureButton exitButton;
    private TextureButton resizeButton;
    private HoverTip cellTip;

    public StationsAnywhereWindow(int x, int y) {
        super(x, y, WIDTH, HEIGHT);
        instance = this;
        try {
            this.exitButton = new TextureButton(GraphicsLoader.INTERFACE_ICONS, "", 16, 6);
        } catch (Throwable t) {
            this.exitButton = null;
        }
        try {
            this.resizeButton = new TextureButton(GraphicsLoader.WINDOW_RESIZE2, "Resize");
        } catch (Throwable t) {
            this.resizeButton = null;
        }
        try {
            this.cellTip = new HoverTip("");
        } catch (Throwable t) {
            this.cellTip = null;
        }
        setDraggable(true);
        makeActiveBringFront();
        rebuild();
    }

    // --- static entry points (called from mixins) --------------------------

    public static void toggle() {
        if (instance != null && !instance.isClosed()) {
            instance.close(false);
        } else {
            new StationsAnywhereWindow(DEFAULT_X, DEFAULT_Y);
        }
    }

    public static void onSectorChanged() {
        if (instance != null && !instance.isClosed()) {
            instance.rebuild();
        }
    }

    // --- data --------------------------------------------------------------

    /**
     * Rebuild the row list from every special-zone sector in the grid.
     *
     * <p>Sort order: discovered zones first, grouped into families (a parent and
     * its children, e.g. "The Bastion" + "The Bastion Wall"); families are ordered
     * by their nearest member's distance from the origin (0, 0) so newbie
     * near-origin zones sit at the top, and members within a family are ordered by
     * their own distance. Undiscovered zones are pooled at the bottom (ordered by
     * distance) since their names are masked and cannot be grouped.</p>
     */
    public void rebuild() {
        rows.clear();
        int discovered = 0;
        try {
            SectorGrid grid = Galaxy.sectors;
            if (grid != null) {
                Stack<Sector> all = grid.getSectors();
                while (all != null && all.hasNext()) {
                    Sector s = all.next();
                    if (s == null) {
                        continue;
                    }
                    if (!isSpecialZone(s)) {
                        continue;
                    }
                    StationsAnywhereRow row = new StationsAnywhereRow(s);
                    if (row.explored) {
                        discovered++;
                    }
                    rows.add(row);
                }
            }

            // Per-family nearest-to-origin distance (discovered rows only), so a
            // whole family sorts by how close its closest member is.
            final java.util.HashMap<String, Long> familyMinDist = new java.util.HashMap<String, Long>();
            for (int i = 0; i < rows.size(); i++) {
                StationsAnywhereRow r = rows.get(i);
                if (!r.explored) {
                    continue;
                }
                Long cur = familyMinDist.get(r.familyKey);
                if (cur == null || r.distSq < cur.longValue()) {
                    familyMinDist.put(r.familyKey, Long.valueOf(r.distSq));
                }
            }

            Collections.sort(rows, new Comparator<StationsAnywhereRow>() {
                public int compare(StationsAnywhereRow a, StationsAnywhereRow b) {
                    // Undiscovered zones are pooled at the bottom.
                    if (a.explored != b.explored) {
                        return a.explored ? -1 : 1;
                    }
                    if (!a.explored) {
                        // Both undiscovered: order by distance, then coords.
                        return byDistanceThenCoords(a, b);
                    }
                    // Both discovered: order families by their nearest member, keep
                    // a family contiguous, then order within by distance/coords.
                    long fa = familyMinDist.get(a.familyKey).longValue();
                    long fb = familyMinDist.get(b.familyKey).longValue();
                    if (fa != fb) {
                        return Long.compare(fa, fb);
                    }
                    int fam = a.familyKey.compareTo(b.familyKey);
                    if (fam != 0) {
                        return fam;
                    }
                    return byDistanceThenCoords(a, b);
                }

                private int byDistanceThenCoords(StationsAnywhereRow a, StationsAnywhereRow b) {
                    if (a.distSq != b.distSq) {
                        return Long.compare(a.distSq, b.distSq);
                    }
                    if (a.sectorX != b.sectorX) {
                        return Integer.compare(a.sectorX, b.sectorX);
                    }
                    return Integer.compare(a.sectorY, b.sectorY);
                }
            });
            this.title = "Stations Anywhere  -  Restricted Zones [" + discovered + "/" + rows.size() + " found]";
        } catch (Throwable t) {
            System.out.println("[StationsAnywhere] rebuild failed: " + t);
        }
        clampScroll();
    }

    private static boolean isSpecialZone(Sector s) {
        try {
            return s.isSpecialZone();
        } catch (Throwable t) {
            return false;
        }
    }

    private void clampScroll() {
        if (scrollRow < 0) {
            scrollRow = 0;
        }
        int max = rows.size() - 1;
        if (max < 0) {
            max = 0;
        }
        if (scrollRow > max) {
            scrollRow = max;
        }
    }

    private int contentBottom() {
        return getY() + getHeight() - FOOTER_H;
    }

    // --- lifecycle / input -------------------------------------------------

    @Override
    public void close(boolean b) {
        if (instance == this) {
            instance = null;
        }
        super.close(b);
    }

    @Override
    public void periodicUpdate() {
        // The special-zone set is fixed at world generation and the permit flags
        // are edited only through this window, so nothing needs periodic polling.
    }

    @Override
    public void update(boolean active) {
        super.update(active);

        // Resize handle (bottom-right corner), matching the game's -17 placement.
        if (resizeButton != null) {
            int hx = getX() + getWidth() - 17;
            int hy = getY() + getHeight() - 17;
            if (resizeButton.update(this, hx, hy)) {
                startResizing();
            }
        }
        if (isResizing()) {
            setWidth(clamp(originalWidth + getResizeDeltaX(), MIN_WIDTH, MAX_WIDTH));
            setHeight(clamp(originalHeight + getResizeDeltaY(), MIN_HEIGHT, MAX_HEIGHT));
            clampScroll();
        }

        if (exitButton != null) {
            boolean clicked = exitButton.update(this, getX() + getWidth() - ICON - 3, getY() + 1);
            if (clicked && isActive()) {
                close(false);
                return;
            }
        }

        if (mouseOver()) {
            int down = safeScroll(Mouse.SCROLL_DOWN);
            int up = safeScroll(Mouse.SCROLL_UP);
            if (down != 0 || up != 0) {
                scrollRow += down - up;
                clampScroll();
            }
        }

        updateCellTip();
    }

    /** Show a hover tooltip with the full text when the mouse is over a truncated
     *  Name or Type cell of a discovered row. */
    private void updateCellTip() {
        if (cellTip == null) {
            return;
        }
        boolean show = false;
        try {
            computeColumns();
            double mx = Mouse.getWindowX();
            double my = Mouse.getWindowY();
            if (isActive() && mouseOver()) {
                int hit = layoutRows(false, mx, my);
                if (hit >= 0 && hit < rows.size()) {
                    StationsAnywhereRow r = rows.get(hit);
                    if (r.explored && inColumn(mx, C_NAME) && r.name.length() > 0) {
                        cellTip.setText(r.name);
                        show = true;
                    } else if (r.explored && inColumn(mx, C_TYPE) && r.type.length() > 0) {
                        cellTip.setText(r.type);
                        show = true;
                    } else if (inColumn(mx, C_ENABLE)) {
                        if (!r.explored) {
                            cellTip.setText("Undiscovered - explore this zone to unlock its permit.");
                        } else {
                            cellTip.setText(r.enabled
                                ? "Station deploy ENABLED here (click to disable). Disabling keeps existing stations."
                                : "Click to allow deploying stations in this restricted zone.");
                        }
                        show = true;
                    }
                }
            }
            cellTip.update(show);
        } catch (Throwable ignored) {
        }
    }

    private boolean inColumn(double mx, int col) {
        return mx >= colX[col] && mx < colX[col] + colW[col];
    }

    private static int safeScroll(illuminatus.core.io.MouseButton b) {
        try {
            return b.getScrolled();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    /** Compute column x/width for the current window width. Extra space is shared
     *  among the flexible text columns (Name/Region/Type) by their weights. */
    private void computeColumns() {
        int left = getX() + PAD;
        int contentW = getWidth() - PAD * 2;
        int minTotal = 0;
        for (int i = 0; i < COLS; i++) {
            minTotal += MIN_W[i];
        }
        int extra = contentW - minTotal;
        if (extra < 0) {
            extra = 0;
        }
        int x = left;
        for (int i = 0; i < COLS; i++) {
            colX[i] = x;
            colW[i] = MIN_W[i] + Math.round(extra * FLEX[i]);
            x += colW[i];
        }
    }

    /** Draw a single table cell, honoring the column's alignment and clipping. */
    private void drawCell(String s, int col, int y) {
        int w = colW[col];
        String text = clip(s, w - 6);
        switch (ALIGN[col]) {
            case ALIGN_RIGHT:
                Text.alignHorizontalRight();
                Text.draw(text, colX[col] + w - 4, y);
                break;
            case ALIGN_CENTER:
                Text.alignHorizontalCenter();
                Text.draw(text, colX[col] + w / 2, y);
                break;
            default:
                Text.alignHorizontalLeft();
                Text.draw(text, colX[col] + 2, y);
                break;
        }
    }

    @Override
    public boolean updateLeftPressed() {
        int hit = rowAtMouse();
        if (hit >= 0 && hit < rows.size()) {
            StationsAnywhereRow r = rows.get(hit);
            // Only discovered zones are interactive; the click must land in the
            // Deploy (checkbox) column.
            if (r.explored && inColumn(Mouse.getWindowX(), C_ENABLE)) {
                boolean now = PermitStore.toggle(r.sectorX, r.sectorY);
                r.enabled = now;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean updateRightPressed() {
        return false;
    }

    @Override
    public boolean updateLeftReleased() {
        return false;
    }

    @Override
    public boolean updateRightReleased() {
        return false;
    }

    @Override
    public void refresh() {
        rebuild();
    }

    @Override
    public int windowAction1(int a) {
        return 0;
    }

    @Override
    public int windowAction2(int a) {
        return 0;
    }

    @Override
    public int windowAction3(int a) {
        return 0;
    }

    // --- drawing -----------------------------------------------------------

    @Override
    public void draw() {
        if (!Player.isAlive()) {
            return;
        }
        super.draw();

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        computeColumns();

        // Title bar
        GenericWindow.drawHorizontalSpacer(x, y + TITLE_H, w);
        Color.WHITE.use();
        if (exitButton != null) {
            exitButton.draw();
        }
        Text.alignHorizontalCenter();
        Text.alignVerticalTop();
        Color.WHITE.use();
        Text.draw(title, x + w / 2, y + 2);
        Text.resetAlignment();

        // Content background
        Color.BLACK.use(1.0f);
        Draw.filledRectangle(x + 2, y + TITLE_H + 2, x + w - 4, y + h - 4);
        Color.WHITE.use();

        drawHeader(x, y, w);

        Text.alignVerticalTop();
        if (rows.isEmpty()) {
            Color.LT_GREY.use();
            Text.alignHorizontalLeft();
            Text.draw("No restricted zones in this galaxy.", x + PAD, getY() + CONTENT_TOP);
        } else {
            layoutRows(true, 0, 0);
        }
        Text.resetAlignment();

        // Footer: a thin separator line with the resize handle in the corner.
        GenericWindow.drawHorizontalSpacer(x, y + h - FOOTER_H, w);

        Color.WHITE.use();
        if (resizeButton != null) {
            resizeButton.draw(x + w - 17, y + h - 17);
        }

        // Queue the cell tooltip (rendered on top by the global HoverTip pass).
        if (cellTip != null) {
            cellTip.draw();
        }

        Color.WHITE.use();
    }

    private void drawHeader(int x, int y, int w) {
        int hy = y + HEADER_Y;
        Color.YELLOW.use();
        Text.alignVerticalTop();
        for (int c = 0; c < COLS; c++) {
            drawCell(HEADERS[c], c, hy);
        }
        Color.WHITE.use();
        GenericWindow.drawHorizontalSpacer(x, y + HEADER_LINE, w);
    }

    /**
     * Single pass over visible rows that either draws them (render=true) or, when
     * render=false, returns the index of the row under the mouse (mx,my). Keeping
     * layout in one place ensures the click hit-test matches what is drawn.
     */
    private int layoutRows(boolean render, double mx, double my) {
        int drawY = getY() + CONTENT_TOP;
        int bottom = contentBottom();
        int hitIndex = -1;

        for (int i = scrollRow; i < rows.size(); i++) {
            if (drawY + ROW_H > bottom) {
                break;
            }
            StationsAnywhereRow r = rows.get(i);

            if (render) {
                drawRow(r, drawY);
            } else if (my >= drawY && my < drawY + ROW_H
                    && mx >= getX() && mx <= getX() + getWidth()) {
                hitIndex = i;
            }
            drawY += ROW_H;
        }
        return hitIndex;
    }

    private void drawRow(StationsAnywhereRow r, int rowY) {
        // Undiscovered zones are dimmed and masked; discovered zones are normal.
        Color textColor = r.explored ? Color.WHITE : Color.M_GREY;

        // Checkbox (Deploy column).
        drawCheckbox(colX[C_ENABLE] + colW[C_ENABLE] / 2, rowY, r.enabled, r.explored);

        textColor.use();
        if (r.explored && r.enabled) {
            Color.LIME.use();
        }
        drawCell(r.name, C_NAME, rowY);

        textColor.use();
        drawCell(r.coords, C_COORDS, rowY);
        drawCell(r.region, C_REGION, rowY);
        drawCell(r.type, C_TYPE, rowY);
        Color.WHITE.use();
    }

    /** Draw a small checkbox: outlined box, a LIME check when enabled. Undiscovered
     *  rows draw a dimmed, empty box to signal they are not yet interactive. */
    private void drawCheckbox(int cx, int rowTopY, boolean enabled, boolean interactive) {
        int box = 11;
        int left = cx - box / 2;
        int top = rowTopY + 2;
        int right = left + box;
        int bottom = top + box;

        if (interactive) {
            Color.LT_GREY.use();
        } else {
            Color.M_GREY.use(0.5f);
        }
        Draw.lineWidth(1.5f);
        Draw.line(left, top, right, top);
        Draw.line(right, top, right, bottom);
        Draw.line(right, bottom, left, bottom);
        Draw.line(left, bottom, left, top);
        Draw.resetLineWidth();

        if (enabled) {
            Color.LIME.use();
            Draw.lineWidth(2.0f);
            double my = top + box * 0.62;
            Draw.line(left + 2, top + box * 0.5, left + box * 0.42, my);
            Draw.line(left + box * 0.42, my, right - 1, top + 1);
            Draw.resetLineWidth();
        }
        // Reset the global alpha/color mutated above (see windows-ui GOTCHA).
        Color.WHITE.use(1.0f);
    }

    private int rowAtMouse() {
        double mx = Mouse.getWindowX();
        double my = Mouse.getWindowY();
        return layoutRows(false, mx, my);
    }

    private static String clip(String s, int maxPixels) {
        if (s == null) {
            return "";
        }
        try {
            if (Text.pixelLength(s) <= maxPixels) {
                return s;
            }
            String ell = "..";
            String out = s;
            while (out.length() > 1 && Text.pixelLength(out + ell) > maxPixels) {
                out = out.substring(0, out.length() - 1);
            }
            return out + ell;
        } catch (Throwable t) {
            return s;
        }
    }
}
