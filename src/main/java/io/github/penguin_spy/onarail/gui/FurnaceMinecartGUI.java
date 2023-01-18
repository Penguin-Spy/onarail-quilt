package io.github.penguin_spy.onarail.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.quiltmc.qsl.item.content.registry.api.ItemContentRegistries;

public class FurnaceMinecartGUI extends SimpleGui {
	private final Inventory furnaceMinecart;

	/**
	 * Constructs a new furnace minecart gui for the supplied player.
	 *
	 * @param player            the player to server this gui to
	 * @param furnaceMinecart	the furnace minecart to use as the inventory
	 */
	public FurnaceMinecartGUI(ServerPlayerEntity player, Inventory furnaceMinecart) {
		super(ScreenHandlerType.GENERIC_9X2, player, false);
		this.furnaceMinecart = furnaceMinecart;

		// Connect GUI to inventory
		this.setSlotRedirect(2, new FurnaceMinecartGUI.FuelSlot(furnaceMinecart, 0));
		this.setSlotRedirect(3, new FurnaceMinecartGUI.FuelSlot(furnaceMinecart, 1));
		this.setSlotRedirect(4, new FurnaceMinecartGUI.FuelSlot(furnaceMinecart, 2));
		this.setSlotRedirect(7, new FurnaceMinecartGUI.PatternSlot(furnaceMinecart, 3));

		// Static gui elements
		GuiElementBuilder background = new GuiElementBuilder(Items.WHITE_STAINED_GLASS_PANE)
				.setName(Text.empty());
		this.setSlot(0, background);
		this.setSlot(1, background);
		this.setSlot(5, background);
		this.setSlot(6, background);
		this.setSlot(8, background);
		this.setSlot(9, background);
		this.setSlot(10, background);
		this.setSlot(11, background);
		this.setSlot(12, new GuiElementBuilder(Items.FURNACE)	// should be set to this furnace minecart's type
				.setName(Text.translatable("text.onarail.furnace_minecart.fuel")));
		this.setSlot(13, background);
		this.setSlot(14, background);
		this.setSlot(15, background);
		this.setSlot(16, new GuiElementBuilder(Items.LOOM)
				.setName(Text.translatable("text.onarail.furnace_minecart.pattern")));
		this.setSlot(17, background);

		this.setTitle(Text.translatable("entity.minecraft.furnace_minecart"));
	}

	@Override
	public void onTick() {
		super.onTick();
		if(!furnaceMinecart.canPlayerUse(this.player)) this.close();
	}

	public static class FuelSlot extends Slot {
		public FuelSlot(Inventory inventory, int index) {
			super(inventory, index, 0, 0);
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return matches(stack);
		}

		public static boolean matches(ItemStack stack) {
			Item item = stack.getItem();
			return ItemContentRegistries.FUEL_TIMES.get(item).isPresent()
					&& !PatternSlot.matches(stack); // any fuel except for banners
		}
	}

	public static class PatternSlot extends Slot {
		public PatternSlot(Inventory inventory, int index) {
			super(inventory, index, 0, 0);
		}

		@Override
		public int getMaxItemCount() {
			return 1;
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return matches(stack);
		}

		public static boolean matches(ItemStack stack) {
			return stack.getItem() instanceof BannerItem;
		}
	}
}
