package dev.mcbookshelf.ward.mixin;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;

import dev.mcbookshelf.ward.Ward;

/**
 * Detaches region-format storage (chunks, POI, entities) from the disk in daemon mode.
 *
 * <p>Daemon worlds only live in memory: each run starts from a fresh world and nothing it does is
 * meant to persist. All three region storages funnel their I/O through these methods — writes
 * include the ones the vanilla shutdown path issues after force-resetting {@code noSave} while
 * draining the chunk maps, and blocking reads (loads and structure scans alike) both guarantees
 * the fresh world and keeps the read path from creating empty region files on the way (region
 * files open in rw mode).
 */
@Mixin(IOWorker.class)
public class IOWorkerMixin {
	@Inject(
			method = "store(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;",
			at = @At("HEAD"),
			cancellable = true)
	private void skipStore(ChunkPos pos, Supplier<CompoundTag> supplier, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		if (Ward.ENABLED) {
			cir.setReturnValue(CompletableFuture.completedFuture(null));
		}
	}

	@Inject(method = "loadAsync", at = @At("HEAD"), cancellable = true)
	private void skipLoad(ChunkPos pos, CallbackInfoReturnable<CompletableFuture<Optional<CompoundTag>>> cir) {
		if (Ward.ENABLED) {
			cir.setReturnValue(CompletableFuture.completedFuture(Optional.empty()));
		}
	}

	@Inject(method = "scanChunk", at = @At("HEAD"), cancellable = true)
	private void skipScan(ChunkPos pos, StreamTagVisitor visitor, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		if (Ward.ENABLED) {
			cir.setReturnValue(CompletableFuture.completedFuture(null));
		}
	}
}
