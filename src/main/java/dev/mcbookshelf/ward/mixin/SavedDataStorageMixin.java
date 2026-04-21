package dev.mcbookshelf.ward.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.storage.SavedDataStorage;

import dev.mcbookshelf.ward.Ward;

/**
 * Drops every saved-data write (scoreboard, chunk tickets, raids, world border, ...) in daemon
 * mode: daemon worlds only live in memory. Every save path — {@code saveAndJoin} and {@code close}
 * included — funnels through this method.
 */
@Mixin(SavedDataStorage.class)
public class SavedDataStorageMixin {
	@Inject(method = "scheduleSave", at = @At("HEAD"), cancellable = true)
	private void skipSave(CallbackInfoReturnable<CompletableFuture<?>> cir) {
		if (Ward.ENABLED) {
			cir.setReturnValue(CompletableFuture.completedFuture(null));
		}
	}
}
