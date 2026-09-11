package stationsanywhere.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import code.store.PermitStore;
import game.world.Sector;
import menu.StationWindow;

/**
 * Keeps player station handling fully functional in permitted special zones.
 *
 * <p>{@code StationWindow.updateReclaimPanel()} disables the claim/repair
 * controls in special zones via
 * {@code disableClaimingOrRepair = getCurrentSector().isSpecialZone()}. Since
 * this mod lets the player deploy stations in those sectors, we also let them
 * claim and repair there: the redirect reports the sector as not-special when it
 * has been permitted, otherwise it returns the real value (vanilla unchanged).</p>
 *
 * <p>This is the only other gameplay path (besides the deploy gate) that keys off
 * {@code isSpecialZone}; platform persistence/recovery has no special-zone logic,
 * so already-deployed stations are never auto-removed.</p>
 */
@Mixin(value = StationWindow.class, remap = false)
public class StationWindowMixin {

    @Redirect(
        method = "updateReclaimPanel",
        at = @At(value = "INVOKE", target = "Lgame/world/Sector;isSpecialZone()Z")
    )
    private boolean stationsanywhere$reclaimSpecialZone(Sector sector) {
        if (sector != null && PermitStore.isEnabled(sector)) {
            return false;
        }
        return sector.isSpecialZone();
    }
}
