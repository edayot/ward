package dev.mcbookshelf.ward;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ServicesKeySet;

import net.minecraft.server.Services;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.server.players.UserNameToIdResolver;

/**
 * Offline stand-ins for the account services a MinecraftServer requires. The test server never
 * talks to Mojang; every profile resolves locally.
 */
final class WardServices {
	static final Services OFFLINE = new Services(
			(MinecraftSessionService) null,
			ServicesKeySet.EMPTY,
			(GameProfileRepository) null,
			new OfflineUserNameToIdResolver(),
			new OfflineProfileResolver());

	private WardServices() {
	}

	private static class OfflineUserNameToIdResolver implements UserNameToIdResolver {
		private final Set<NameAndId> savedIds = new HashSet<>();

		public void add(final NameAndId nameAndId) {
			this.savedIds.add(nameAndId);
		}

		public Optional<NameAndId> get(final String name) {
			return this.savedIds
					.stream()
					.filter((e) -> e.name().equals(name))
					.findFirst()
					.or(() -> Optional.of(NameAndId.createOffline(name)));
		}

		public Optional<NameAndId> get(final UUID id) {
			return this.savedIds
					.stream()
					.filter((e) -> e.id().equals(id))
					.findFirst();
		}

		public void resolveOfflineUsers(final boolean value) {
		}

		public void save() {
		}
	}

	private static class OfflineProfileResolver implements ProfileResolver {
		public Optional<GameProfile> fetchByName(final String name) {
			return Optional.empty();
		}

		public Optional<GameProfile> fetchById(final UUID id) {
			return Optional.empty();
		}
	}
}
