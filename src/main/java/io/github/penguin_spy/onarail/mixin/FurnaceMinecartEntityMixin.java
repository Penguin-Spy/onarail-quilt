package io.github.penguin_spy.onarail.mixin;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.SimpleGui;
import io.github.penguin_spy.onarail.OnARail;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceMinecartEntity.class)
public abstract class FurnaceMinecartEntityMixin {

	private SimpleInventory inventory;

	//FIXME: this doesn't catch all constructors, only the one that runs on world load (placing a minecart during gameplay doesn't call this)
	@Inject(method="<init>", at = @At("RETURN"))
	void onConstructed(CallbackInfo ci) {
		this.inventory = new SimpleInventory(5); // chunk_fuel, 3x fuel, pattern
	}

	//TODO: serialize/deserialize inventory to nbt

	// This is an overwrite because the original functionality of interact()ing a furnace minecart is to consume coal/charcoal from the player's hand
	//  and then "push" the furnace minecart, and that's it.
	// None of that functionality is required by this mod; we are completely replacing it, so we just @Overwrite the interact method.
	@Overwrite
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if(!(player instanceof ServerPlayerEntity)) {
			return ActionResult.CONSUME;
		}

		if(player.getStackInHand(hand).isOf(Items.CHAIN)) {
			// linking
			OnARail.LOGGER.info("use chain on furnace minecart");
			return ActionResult.CONSUME;
		} else {
			try {
				SimpleGui gui = new SimpleGui(ScreenHandlerType.GENERIC_9X2, (ServerPlayerEntity) player, false) {
					public boolean onClick(int index, ClickType type, SlotActionType action, GuiElementInterface element) {
						this.player.sendMessage(Text.literal(type.toString()), false);

						return super.onClick(index, type, action, element);
					}

					//TODO: onTick() checks if furnace minecart is still valid (still exists & is nearby in same dimension)
				};

				// Connect GUI to inventory
				// TODO: filtering what items go in?
				gui.setSlotRedirect(3, new Slot(inventory, 0, 0, 0));
				gui.setSlotRedirect(4, new Slot(inventory, 1, 0, 0));
				gui.setSlotRedirect(5, new Slot(inventory, 2, 0, 0));
				gui.setSlotRedirect(1, new Slot(inventory, 3, 0, 0));
				gui.setSlotRedirect(7, new Slot(inventory, 4, 0, 0));

				// Static gui elements
				gui.setSlot(0, Items.WHITE_STAINED_GLASS_PANE.getDefaultStack());
				gui.setSlot(2, Items.WHITE_STAINED_GLASS_PANE.getDefaultStack());
				gui.setSlot(6, Items.WHITE_STAINED_GLASS_PANE.getDefaultStack());
				gui.setSlot(8, Items.WHITE_STAINED_GLASS_PANE.getDefaultStack());
				gui.setSlot(9, Items.WHITE_STAINED_GLASS_PANE.getDefaultStack());
				gui.setSlot(10, new GuiElementBuilder(Items.END_PORTAL_FRAME)
						.setName(Text.translatable("container.onarail.furnace_minecart.chunk_fuel")));
				gui.setSlot(11, Items.WHITE_STAINED_GLASS_PANE.getDefaultStack());
				gui.setSlot(12, Items.WHITE_STAINED_GLASS_PANE.getDefaultStack());
				gui.setSlot(13, new GuiElementBuilder(Items.FURNACE)	// should be set to this furnace minecart's type
						.setName(Text.translatable("container.onarail.furnace_minecart.fuel")));
				gui.setSlot(14, Items.WHITE_STAINED_GLASS_PANE.getDefaultStack());
				gui.setSlot(15, Items.WHITE_STAINED_GLASS_PANE.getDefaultStack());
				gui.setSlot(16, new GuiElementBuilder(Items.LOOM)
						.setName(Text.translatable("container.onarail.furnace_minecart.pattern")));
				gui.setSlot(17, Items.WHITE_STAINED_GLASS_PANE.getDefaultStack());

				gui.setTitle(Text.translatable("entity.minecraft.furnace_minecart"));
				gui.open();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return ActionResult.SUCCESS;
		}
	}
}
