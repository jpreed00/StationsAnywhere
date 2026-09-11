package stationsanywhere.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import code.store.PermitStore;
import game.platform.Deployables;
import game.world.Sector;
import game.world.SectorRegion;

/**
 * Lifts the game's station-deploy restriction for sectors the player has
 * permitted via the Stations Anywhere window.
 *
 * <p>{@code Deployables.checkStationOrDrone(ship, item, isDrone)} rejects a
 * <em>station</em> deploy (not a platform/drone) in three sector conditions:
 * region {@code PROTECTED}, region {@code DEEP_VOID}, and
 * {@link Sector#isSpecialZone()}. We surgically redirect just those calls inside
 * that one method:</p>
 * <ul>
 *   <li>{@code getRegion()} (both the PROTECTED and DEEP_VOID checks) returns
 *       {@code NEUTRAL} when the sector is permitted, so neither region branch
 *       fires;</li>
 *   <li>{@code isSpecialZone()} returns {@code false} when permitted.</li>
 * </ul>
 *
 * <p>When a sector is <em>not</em> permitted, both redirects return the real
 * value, so vanilla behavior is completely unchanged. All other checks in the
 * method (undocked, proximity, deploy limits, energy/use-level) still run
 * normally. {@link PermitStore#isEnabled(Sector)} already swallows its own
 * failures, so this never blocks a legitimate deploy.</p>
 */
@Mixin(value = Deployables.class, remap = false)
public class DeployablesMixin {

    @Redirect(
        method = "checkStationOrDrone",
        at = @At(value = "INVOKE", target = "Lgame/world/Sector;getRegion()Lgame/world/SectorRegion;")
    )
    private static SectorRegion stationsanywhere$region(Sector sector) {
        if (sector != null && PermitStore.isEnabled(sector)) {
            return SectorRegion.NEUTRAL;
        }
        return sector.getRegion();
    }

    @Redirect(
        method = "checkStationOrDrone",
        at = @At(value = "INVOKE", target = "Lgame/world/Sector;isSpecialZone()Z")
    )
    private static boolean stationsanywhere$specialZone(Sector sector) {
        if (sector != null && PermitStore.isEnabled(sector)) {
            return false;
        }
        return sector.isSpecialZone();
    }
}
