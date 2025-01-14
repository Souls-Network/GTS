package org.pokesplash.gts.UI.button;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.button.FlagType;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.Page;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.pokesplash.gts.Gts;
import org.pokesplash.gts.UI.PokemonListings;
import org.pokesplash.gts.enumeration.Sort;
import org.pokesplash.gts.util.Utils;

public abstract class SeePokemonListings {
    public static Button getButton() {
        return GooeyButton.builder()
                .display(Utils.parseItemId(Gts.language.getPokemonListingsButtonItem()))
                .with(DataComponents.CUSTOM_NAME,
                        Component.literal(Gts.language.getPokemonListingsButtonLabel()))
                .with(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE)
                .onClick((action) -> {
                    ServerPlayer sender = action.getPlayer();
                    Page page = new PokemonListings().getPage(Sort.NONE);
                    UIManager.openUIForcefully(sender, page);
                })
                .build();
    }
}
