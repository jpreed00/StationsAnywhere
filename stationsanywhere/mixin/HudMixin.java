package stationsanywhere.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import code.window.StationsAnywhereWindow;
import game.HeadsUpDisplay;
import illuminatus.core.io.Keyboard;

/**
 * The mod's only per-frame code: a single toggle-key check.
 *
 * <p>{@code HeadsUpDisplay.update()} runs every frame in-game. We do one cheap
 * {@code Keyboard.press(int)} check here and only act (open/close the window)
 * when the key was actually pressed this frame. No other work happens per
 * frame.</p>
 *
 * <p>Mod hotkeys (all unbound by the base game's default {@code KeyMap}):
 * {@code O} = Stations Anywhere, {@code K} = Planet List, backtick = Item Spawner.
 * Use {@link Keyboard#press(int)} with a keycode (looked up at poll time) — do
 * not cache a {@code KeyboardKey} in a static field, because mixin class init
 * can run before {@code Keyboard} has finished initializing.</p>
 */
@Mixin(value = HeadsUpDisplay.class, remap = false)
public class HudMixin {

    @Inject(method = "update", at = @At("HEAD"))
    private static void stationsanywhere$pollToggle(CallbackInfo ci) {
        try {
            if (Keyboard.press(Keyboard.KEY_O.KEY_CONSTANT)) {
                StationsAnywhereWindow.toggle();
            }
        } catch (Throwable t) {
            System.out.println("[StationsAnywhere] toggle poll failed: " + t);
        }
    }
}
