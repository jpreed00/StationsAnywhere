package stationsanywhere.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import code.window.StationsAnywhereWindow;
import game.world.Galaxy;

/**
 * Refreshes the window's row list whenever the player enters a new sector, so a
 * special zone that just became explored updates its name/coords from masked to
 * revealed.
 *
 * <p>Event-driven: {@code Galaxy.setCurrentSector} fires once per sector
 * transition, so we hook it instead of polling {@code getCurrentSector()} every
 * frame. The rebuild is a no-op when the window is closed and is idempotent, so
 * matching both {@code setCurrentSector} overloads is harmless.</p>
 */
@Mixin(value = Galaxy.class, remap = false)
public class SectorChangeMixin {

    @Inject(method = "setCurrentSector", at = @At("TAIL"))
    private static void stationsanywhere$onSectorChanged(CallbackInfo ci) {
        try {
            StationsAnywhereWindow.onSectorChanged();
        } catch (Throwable t) {
            System.out.println("[StationsAnywhere] sector-change refresh failed: " + t);
        }
    }
}
