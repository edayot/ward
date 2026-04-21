package dev.mcbookshelf.ward.accessor;

import java.util.function.Predicate;

import net.minecraft.resources.ResourceKey;

/**
 * Duck interface for accessing MappedRegistry internals via mixin.
 *
 * <p>This interface is implemented by MappedRegistryMixin to expose methods for unfreezing the
 * registry and clearing specific entries.
 */
public interface MappedRegistryAccessor<T> {
	/**
	 * Unfreezes the registry to allow modifications.
	 */
	void ward$unfreeze();

	/**
	 * Clears all entries matching the given predicate.
	 */
	void ward$clearByPredicate(Predicate<ResourceKey<T>> predicate);
}
