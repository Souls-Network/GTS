package org.pokesplash.gts.command.subcommand;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.pokesplash.gts.Gts;
import org.pokesplash.gts.Listing.ItemListing;
import org.pokesplash.gts.Listing.Listing;
import org.pokesplash.gts.Listing.PokemonListing;
import org.pokesplash.gts.api.GtsAPI;
import org.pokesplash.gts.config.ItemPrices;
import org.pokesplash.gts.util.Subcommand;
import org.pokesplash.gts.util.Utils;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class List extends Subcommand {

	public List() {
		super("§9Usage:\n§3- gts sell <pokemon/item>");
	}

	/**
	 * Method used to add to the base command for this subcommand.
	 * @return source to complete the command.
	 */
	@Override
	public LiteralCommandNode<CommandSourceStack> build() {
		return Commands.literal("sell")
				.executes(this::showUsage)
				.then(Commands.literal("pokemon")
						.executes(this::showPokemonUsage)
						.then(Commands.argument("slot", IntegerArgumentType.integer())
								.suggests((ctx, builder) -> {
									for (int x=0; x<6; x++) {
										builder.suggest(x + 1);
									}
									return builder.buildFuture();
								})
								.executes(this::showPokemonUsage)
								.then(Commands.argument("price", FloatArgumentType.floatArg())
										.suggests((ctx, builder) -> {

											for (double price : Gts.config.getAllPokemonPrices()) {
												if (price > 0) {
													builder.suggest((int) price);
												}
											}

											return builder.buildFuture();
										})
										.executes(this::run))))
				.then(Commands.literal("item")
						.executes(this::showItemUsage)
						.then(Commands.argument("price", FloatArgumentType.floatArg())
								.suggests((ctx, builder) -> {
									for (int i = 1; i <= 11; i++) {
										builder.suggest(i * 100);
									}
									return builder.buildFuture();
								})
								.executes(this::showItemUsage)
								.then(Commands.argument("amount", IntegerArgumentType.integer())
										.suggests((ctx, builder) -> {
											for (int i = 0; i <= 64; i++) {
												builder.suggest(i + 1);
											}
											return builder.buildFuture();
										})
										.executes(this::run))))
				.build();
	}

	/**
	 * Method to perform the logic when the command is executed.
	 * @param context the source of the command.
	 * @return integer to complete command.
	 */
	@Override
	public int run(CommandContext<CommandSourceStack> context) {
		if (!context.getSource().isPlayer()) {
			context.getSource().sendSystemMessage(Component.literal(
					"This command must be ran by a player."
			));
			return 1;
		}

		if (!Gts.timeouts.hasTimeoutExpired(context.getSource().getPlayer().getUUID())) {

			long endTime = Gts.timeouts.getTimeout(context.getSource().getPlayer().getUUID());

			context.getSource().sendSystemMessage(Component.literal(
					"§cYou have been timed out for " +
							Utils.parseLongDate(endTime - new Date().getTime())
			));
			return 1;
		}

		try {
			PokemonBattle battle =
					Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(context.getSource().getPlayer());

			if (battle != null) {
				context.getSource().sendSystemMessage(Component.literal(
						"§cYou can not list to GTS while in a battle."
				));
				return 1;
			}

			int totalPokemonListings =
					Gts.listings.getPokemonListingsByPlayer(context.getSource().getPlayer().getUUID()).size();
			int totalItemListings = Gts.listings.getItemListingsByPlayer(
					context.getSource().getPlayer().getUUID()).size();

			java.util.List<Listing> expiredListings = Gts.listings.getExpiredListingsOfPlayer(
					context.getSource().getPlayer().getUUID());

			int totalExpiredListings = expiredListings == null ? 0 : expiredListings.size();

			if (totalPokemonListings + totalItemListings +
					totalExpiredListings >=
					Gts.config.getMaxListingsPerPlayer()) {
				context.getSource().sendSystemMessage(Component.literal(
						Utils.formatPlaceholders(Gts.language.getMaximumListings(), 0, null,
								context.getSource().getPlayer().getDisplayName().getString(), null)));
				return 1;
			}

			if (context.getInput().contains("pokemon")) {
				return runPokemon(context);
			} else {
				return runItem(context);
			}

		} catch (Exception e) {
			context.getSource().sendSystemMessage(Component.literal("§cSomething went wrong."));
			e.printStackTrace();
		}

		return 1;
	}

	public int runPokemon(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();

		int slot = IntegerArgumentType.getInteger(context, "slot") - 1;
		double price = FloatArgumentType.getFloat(context, "price");

		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		Pokemon pokemon = party.get(slot);

		// If no Pokemon in slot, send message to user.
		if (pokemon == null) {
			context.getSource().sendSystemMessage(Component.literal(Utils.formatPlaceholders(Gts.language.getNoPokemonInSlot(),
					0, null, player.getDisplayName().getString(), null)));
			return 1;
		}

		if (!pokemon.getTradeable()) {
			context.getSource().sendSystemMessage(Component.literal(
					"§cThis Pokemon is not tradeable."));
			return 1;
		}

		// Get the pokemons max ivs IVs
		AtomicInteger totalMaxIvs = new AtomicInteger();
		pokemon.getIvs().forEach((stat) -> {
			if (stat.getValue() == 31) {
				totalMaxIvs.addAndGet(1);
			}
		});

		double minPrice = 0;

		// Adds minimum price based on total full IVs.
		switch (totalMaxIvs.get()) {
			case 1:
				minPrice += Gts.config.getMinPrice1IV();
				break;
			case 2:
				minPrice += Gts.config.getMinPrice2IV();
				break;
			case 3:
				minPrice += Gts.config.getMinPrice3IV();
				break;
			case 4:
				minPrice += Gts.config.getMinPrice4IV();
				break;
			case 5:
				minPrice += Gts.config.getMinPrice5IV();
				break;
			case 6:
				minPrice += Gts.config.getMinPrice6IV();
				break;
		}

		// If HA, add the minimum price.
		if (Utils.isHA(pokemon)) {
			minPrice += Gts.config.getMinPriceHA();
		}

		// If Legendary, add the minimum price.
		if (pokemon.isLegendary()) {
			minPrice += Gts.config.getMinPriceLegendary();
		}

		// If Ultrabeast, add the minimum price.
		if (pokemon.isUltraBeast()) {
			minPrice += Gts.config.getMinPriceUltrabeast();
		}

		// If less than min price, cancel the command.
		if (price < minPrice) {
			context.getSource().sendSystemMessage(Component.literal(Utils.formatPlaceholders(Gts.language.getMinimumListingPrice(),
					minPrice, pokemon.getDisplayName().getString(), player.getDisplayName().getString(), null)));
			return 1;
		}

		// If the price is above the maximum price, cancel the command.
		if (price > Gts.config.getMaximumPrice()) {
			context.getSource().sendSystemMessage(Component.literal(Utils.formatPlaceholders(Gts.language.getMaximumListingPrice(),
					minPrice, pokemon.getDisplayName().getString(), player.getDisplayName().getString(), null)));
			return 1;
		}

		java.util.List<String> bannedPokemon = Gts.config.getBannedPokemon();

		// Checks the pokemon isn't banned.
		for (String bannedMon : bannedPokemon) {
			if (bannedMon.equalsIgnoreCase(pokemon.getSpecies().getName())) {
				context.getSource().sendSystemMessage(Component.literal(
						Utils.formatPlaceholders(Gts.language.getBannedPokemon(),
						0, pokemon.getSpecies().getName(), player.getDisplayName().getString(),
								null)));
				return 1;
			}
		}

		PokemonListing listing = new PokemonListing(player.getUUID(), player.getName().getString(), price, pokemon);

		boolean success = GtsAPI.addListing(listing, player, slot);

		if (success) {
			context.getSource().sendSystemMessage(Component.literal(Utils.formatPlaceholders(Gts.language.getListingSuccess(),
					minPrice, pokemon.getDisplayName().getString(), player.getDisplayName().getString(), null)));


		} else {
			context.getSource().sendSystemMessage(Component.literal(Utils.formatPlaceholders(Gts.language.getListingFail(),
					minPrice, pokemon.getDisplayName().getString(), player.getDisplayName().getString(), null)));


		}

		return 1;
	}

	public int runItem(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		int amount = IntegerArgumentType.getInteger(context, "amount");
		double price = FloatArgumentType.getFloat(context, "price");

		java.util.List<ItemPrices> minPrices = Gts.config.getCustomItemPrices();
		java.util.List<String> bannedItems = Gts.config.getBannedItems();

		// Checks there's an item in the players hand
		try {
			ItemStack item = context.getSource().getPlayer().getMainHandItem();

			// If they aren't holding an item. Message them
			if (item == null) {
				context.getSource().sendSystemMessage(Component.literal(Utils.formatPlaceholders(Gts.language.getNoItemInHand(),
						0, null, player.getDisplayName().getString(), null)));
				return 1;
			}

			// Checks the amount isn't 0.
			if (amount <= 0) {
				context.getSource().sendSystemMessage(Component.literal(Utils.formatPlaceholders(Gts.language.getZeroItemAmount(),
						0, item.getDisplayName().getString(), player.getDisplayName().getString(), null)));
				return 1;
			}

			// Checks the item isn't banned.
			for (String bannedItem : bannedItems) {
				ItemStack banned = Utils.parseItemId(bannedItem);
				if (banned.getItem().equals(item.getItem()) &&
						ItemStack.isSameItemSameComponents(banned, item)) {
					context.getSource().sendSystemMessage(Component.literal(Utils.formatPlaceholders(Gts.language.getBannedItem(),
							0, item.getDisplayName().getString(), player.getDisplayName().getString(), null)));
					return 1;
				}
			}

			double minPrice = 0;

			// Checks for a minimum price.
			for (ItemPrices minItem : minPrices) {
				ItemStack min = Utils.parseItemId(minItem.getItem_name());

				if (min.getItem().equals(item.getItem()) &&
						ItemStack.isSameItemSameComponents(min, item)) {
					minPrice += minItem.getMin_price();
					break;
				}
			}

			// If less than min price, cancel the command.
			if (price < minPrice) {
				context.getSource().sendSystemMessage(Component.literal(Utils.formatPlaceholders(Gts.language.getMinimumListingPrice(),
						minPrice, item.getDisplayName().getString(), player.getDisplayName().getString(), null)));
				return 1;
			}

			// If the price is above the maximum price, cancel the command.
			if (price > Gts.config.getMaximumPrice()) {
				context.getSource().sendSystemMessage(Component.literal(Utils.formatPlaceholders(Gts.language.getMaximumListingPrice(),
						minPrice, item.getDisplayName().getString(), player.getDisplayName().getString(), null)));
				return 1;
			}

			// Check there are enough items in the players inventory.
			if (item.getCount() < amount) {
				context.getSource().sendSystemMessage(Component.literal(Utils.formatPlaceholders(Gts.language.getInsufficientItems(),
						minPrice, item.getDisplayName().getString(), player.getDisplayName().getString(), null)));
				return 1;


			}

			ItemStack listingItem = item.copy();
			listingItem.setCount(amount);

			ItemListing listing = new ItemListing(player.getUUID(), player.getName().getString(), price,
					listingItem);

			boolean success = GtsAPI.addListing(player, listing);

			if (success) {
				context.getSource().sendSystemMessage(Component.literal(Utils.formatPlaceholders(Gts.language.getListingSuccess(),
						minPrice, listing.getListingName(), player.getDisplayName().getString(), null)));


			} else {
				context.getSource().sendSystemMessage(Component.literal(Utils.formatPlaceholders(Gts.language.getListingFail(),
						minPrice, listing.getListingName(), player.getDisplayName().getString(), null)));


			}
			return 1;


		} catch (NullPointerException e) {
			context.getSource().sendSystemMessage(Component.literal(Utils.formatPlaceholders(Gts.language.getItemIdNotFound(),
					0, null, player.getDisplayName().getString(), null)));
			Gts.LOGGER.error("Couldn't find Item ID\n Stacktrace: ");
			e.printStackTrace();
			return 1;


		}
	}

	public int showPokemonUsage(CommandContext<CommandSourceStack> context) {
		String usage = "§9Usage:\n§3- gts sell pokemon <slot> <price>";
		context.getSource().sendSystemMessage(Component.literal(Utils.formatMessage(usage, context.getSource().isPlayer())));
		return 1;
	}

	public int showItemUsage(CommandContext<CommandSourceStack> context) {
		String usage = "§9Usage:\n§3- gts sell item <price> <quantity>";
		context.getSource().sendSystemMessage(Component.literal(Utils.formatMessage(usage, context.getSource().isPlayer())));
		return 1;
	}

}
