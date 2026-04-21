package dev.mcbookshelf.ward.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;

import dev.mcbookshelf.ward.accessor.GameTestHelperAccessor;

@Mixin(GameTestHelper.class)
public abstract class GameTestHelperMixin implements GameTestHelperAccessor {
	@Shadow
	@Final
	private GameTestInfo testInfo;

	@Override
	@Unique
	public GameTestInfo ward$getTestInfo() {
		return this.testInfo;
	}
}
