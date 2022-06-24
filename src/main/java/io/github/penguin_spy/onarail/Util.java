package io.github.penguin_spy.onarail;

import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;
import static net.minecraft.util.math.Direction.*;

public class Util {
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
				minecart.playLinkSound(true);

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

				minecart.playLinkSound(true);
				player.getStackInHand(hand).decrement(1);	// consume chain item
				player.sendMessage(Text.translatable("onarail.link.linked",
						parentMinecart.getName(), minecart.getName()), true);
			}
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}

	/**
	 * Takes any direction & a rail shape and returns the direction of travel along that rail.<br>
	 * Biased towards the North-West when the given direction is perpendicular to the rail shape.
	 * @param dir The direction to align from
	 * @param railShape The shape of the rail to align to
	 * @return A direction that is aligned along the rail
	 */
	public static Direction alignDirWithRail(Direction dir, RailShape railShape) {
		switch(dir) {
			case NORTH:
				switch (railShape) {
					case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST, SOUTH_WEST -> dir = WEST;
					case SOUTH_EAST -> dir = EAST;
				}
				break;
			case EAST:
				switch (railShape) {
					case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH, NORTH_WEST -> dir = NORTH;
					case SOUTH_WEST -> dir = SOUTH;
				}
				break;
			case SOUTH:
				switch (railShape) {
					case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST, NORTH_WEST -> dir = WEST;
					case NORTH_EAST -> dir = EAST;
				}
				break;
			case WEST:
				switch (railShape) {
					case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH, NORTH_EAST -> dir = NORTH;
					case SOUTH_EAST -> dir = SOUTH;
				}
				break;
		}
		return dir;
	}
}
