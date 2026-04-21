package dev.mcbookshelf.ward.mixin;

import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestBatchListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Makes user forceloads survive a test run.
 *
 * <p>Placing a test structure forceloads the chunks it intersects, and the runner cleans that up
 * by clearing <em>every</em> forced chunk of the level when a batch completes — wiping the
 * forceloads data packs made at load time or during tests (machines, lazily initialized utility
 * chunks, ...) along with its own. Instead, the live set minus the finished batch's structure
 * chunks is snapshotted right before each wipe (batch listeners fire first) and restored at the
 * next batch start, together with the pre-run set: only the chunks the structures forced for
 * themselves stay cleared.
 */
@Mixin(GameTestRunner.class)
public class GameTestRunnerMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Unique
	private LongSet ward$preRunForced = LongSets.EMPTY_SET;
	@Unique
	private LongSet ward$keptForced = LongSets.EMPTY_SET;

	@Inject(method = "start", at = @At("HEAD"))
	private void snapshotForcedChunks(CallbackInfo info) {
		this.ward$preRunForced = new LongArraySet(this.level.getForceLoadedChunks());
		this.ward$keptForced = this.ward$preRunForced;

		// Batch listeners are notified before the runner clears: the last look
		// at what the run kept forced
		((GameTestRunner) (Object) this).addListener(new GameTestBatchListener() {
			@Override
			public void testBatchStarting(GameTestBatch batch) {
			}

			@Override
			public void testBatchFinished(GameTestBatch batch) {
				GameTestRunnerMixin owner = GameTestRunnerMixin.this;
				LongSet forced = new LongArraySet(owner.level.getForceLoadedChunks());
				batch.gameTestInfos().forEach(test -> test.getTestInstanceBlockEntity()
						.getStructureBoundingBox()
						.intersectingChunks()
						.forEach(pos -> forced.remove(pos.pack())));
				owner.ward$keptForced = forced;
			}
		});
	}

	@Inject(method = "runBatch", at = @At("HEAD"))
	private void restoreForcedChunks(int batchIndex, CallbackInfo info) {
		// The previous batch end cleared every forced chunk; user forceloads
		// come back, including pre-existing ones under a structure chunk
		this.ward$keptForced.forEach(pos -> this.level.setChunkForced(ChunkPos.getX(pos), ChunkPos.getZ(pos), true));
		this.ward$preRunForced.forEach(pos -> this.level.setChunkForced(ChunkPos.getX(pos), ChunkPos.getZ(pos), true));
	}
}
