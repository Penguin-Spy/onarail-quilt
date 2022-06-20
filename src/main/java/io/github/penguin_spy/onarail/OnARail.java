package io.github.penguin_spy.onarail;

import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OnARail implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod name as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger("On A Rail");

	@Override
	public void onInitialize(ModContainer mod) {
		LOGGER.info("Hello Quilt world from {}!", mod.metadata().name());
	}

	/**
	 * Attempts to start or finish coupling the specified minecart.<br>
	 *
	 * @param minecart	the minecart that was interacted with
	 * @param player	the player who is trying to couple
	 * @param hand		which hand was used to interact
	 * @return			{@link ActionResult#SUCCESS} when coupling succeeded,<br>
	 * 					{@link ActionResult#FAIL} when coupling failed,<br>
	 * 					{@link ActionResult#PASS} when the player was not holding a chain.
	 */
	public static ActionResult tryLink(Linkable minecart, ServerPlayerEntity player, Hand hand) {
		if(player.getStackInHand(hand).isOf(Items.CHAIN)) {

			if(player.isSneaking() && minecart.hasChild()) {
				player.sendMessage(Text.translatable("onarail.link.unlinked", minecart.getChild().getName()), true);
				minecart.removeChild();
				return ActionResult.SUCCESS;
			}

			final Linker linker = ((Linker) player);

			if(!linker.isLinking()) {
				// furnace minecarts start a train, other minecarts must be part of a train already to be coupled from
				if(minecart.getParent() == null && !(minecart instanceof FurnaceMinecartEntity)) {
					player.sendMessage(Text.translatable("onarail.link.cant_link_parent"), true);
					linker.stopLinking();
					return ActionResult.FAIL;
				}

				// there can't be any minecarts coupled behind this one
				if(minecart.getChild() != null) {
					player.sendMessage(Text.translatable("onarail.link.already_linked_parent"), true);
					linker.stopLinking();
					return ActionResult.FAIL;
				}

				// if we got here, this minecart is valid to be coupled from
				linker.setLinkingMinecart(minecart);

			} else { // we're completing a couple
				final Linkable parentMinecart = linker.getLinkingMinecart();

				if(minecart.getParent() != null) {
					player.sendMessage(Text.translatable("onarail.link.already_linked_child"), true);
					linker.stopLinking();
					return ActionResult.FAIL;
				}

				if(minecart instanceof FurnaceMinecartEntity) {
					player.sendMessage(Text.translatable("onarail.link.cant_link_child"), true);
					linker.stopLinking();
					return ActionResult.FAIL;
				}

				// if we got here, this minecart is valid to be coupled to
				minecart.setParent(parentMinecart);	// link this to upstream cart
				parentMinecart.setChild(minecart);	// link upstream cart to this
				linker.stopLinking();

				player.getStackInHand(hand).decrement(1);	// consume chain item
				player.sendMessage(Text.translatable("onarail.link.linked",
						parentMinecart.getName(), minecart.getName()), true);
			}
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}
}
