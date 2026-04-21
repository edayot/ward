package dev.mcbookshelf.ward.accessor;

import net.minecraft.gametest.framework.GameTestInfo;

/**
 * Duck interface for accessing GameTestHelper internals via mixin.
 *
 * <p>This interface is implemented by GameTestHelperMixin to expose the private testInfo field so
 * test lifecycle listeners can be attached.
 */
public interface GameTestHelperAccessor {
	/**
	 * Returns the test this helper drives.
	 */
	GameTestInfo ward$getTestInfo();
}
