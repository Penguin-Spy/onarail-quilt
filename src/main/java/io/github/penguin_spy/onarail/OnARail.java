package io.github.penguin_spy.onarail;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.*;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;
import org.quiltmc.qsl.command.api.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.literal;


public class OnARail implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod name as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger("On A Rail");

	@Override
	public void onInitialize(ModContainer mod) {
		LOGGER.info("Hello Quilt world from {}!", mod.metadata().name());


		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("test").executes(OnARail::test)
			);
		});
	}


	private static int test(CommandContext<ServerCommandSource> objectCommandContext) {
		try {
			ServerPlayerEntity player = objectCommandContext.getSource().getPlayer();
			SimpleGui gui = new SimpleGui(ScreenHandlerType.GENERIC_3X3, player, false) {
				@Override
				public boolean onClick(int index, ClickType type, SlotActionType action, GuiElementInterface element) {
					this.player.sendMessage(Text.literal(type.toString()), false);

					return super.onClick(index, type, action, element);
				}

				@Override
				public void onTick() {
					this.setSlot(0, new GuiElementBuilder(Items.ARROW).setCount((int) (player.world.getTime() % 127)));
					super.onTick();
				}
			};

			gui.setTitle(Text.literal("Nice"));
			gui.setSlot(0, new GuiElementBuilder(Items.ARROW).setCount(100));
			gui.setSlot(1, new AnimatedGuiElement(new ItemStack[]{
					Items.NETHERITE_PICKAXE.getDefaultStack(),
					Items.DIAMOND_PICKAXE.getDefaultStack(),
					Items.GOLDEN_PICKAXE.getDefaultStack(),
					Items.IRON_PICKAXE.getDefaultStack(),
					Items.STONE_PICKAXE.getDefaultStack(),
					Items.WOODEN_PICKAXE.getDefaultStack()
			}, 10, false, (x, y, z) -> {
			}));

			gui.setSlot(2, new AnimatedGuiElementBuilder()
					.setItem(Items.NETHERITE_AXE).setDamage(150).saveItemStack()
					.setItem(Items.DIAMOND_AXE).setDamage(150).unbreakable().saveItemStack()
					.setItem(Items.GOLDEN_AXE).glow().saveItemStack()
					.setItem(Items.IRON_AXE).enchant(Enchantments.AQUA_AFFINITY, 1).hideFlags().saveItemStack()
					.setItem(Items.STONE_AXE).saveItemStack()
					.setItem(Items.WOODEN_AXE).saveItemStack()
					.setInterval(10).setRandom(true)
			);

			for (int x = 3; x < gui.getSize(); x++) {
				ItemStack itemStack = Items.STONE.getDefaultStack();
				itemStack.setCount(x);
				gui.setSlot(x, new GuiElement(itemStack, (index, clickType, actionType) -> {
				}));
			}

			gui.setSlot(5, new GuiElementBuilder(Items.PLAYER_HEAD)
					.setSkullOwner(
							"ewogICJ0aW1lc3RhbXAiIDogMTYxOTk3MDIyMjQzOCwKICAicHJvZmlsZUlkIiA6ICI2OTBkMDM2OGM2NTE0OGM5ODZjMzEwN2FjMmRjNjFlYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJ5emZyXzciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDI0OGVhYTQxNGNjZjA1NmJhOTY5ZTdkODAxZmI2YTkyNzhkMGZlYWUxOGUyMTczNTZjYzhhOTQ2NTY0MzU1ZiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
							null, null)
					.setName(Text.literal("Battery"))
					.glow()
			);

			gui.setSlot(6, new GuiElementBuilder(Items.PLAYER_HEAD)
					.setSkullOwner(new GameProfile(UUID.fromString("f5a216d9-d660-4996-8d0f-d49053677676"), "patbox"), player.server)
					.setName(Text.literal("Patbox's Head"))
					.glow()
			);

			gui.setSlot(7, new GuiElementBuilder()
					.setItem(Items.BARRIER)
					.glow()
					.setName(Text.literal("Bye")
							.setStyle(Style.EMPTY.withItalic(false).withBold(true)))
					.addLoreLine(Text.literal("Some lore"))
					.addLoreLine(Text.literal("More lore").formatted(Formatting.RED))
					.setCount(3)
					.setCallback((index, clickType, actionType) -> gui.close())
			);

			gui.setSlot(8, new GuiElementBuilder()
					.setItem(Items.TNT)
					.glow()
					.setName(Text.literal("Test :)")
							.setStyle(Style.EMPTY.withItalic(false).withBold(true)))
					.addLoreLine(Text.literal("Some lore"))
					.addLoreLine(Text.literal("More lore").formatted(Formatting.RED))
					.setCount(1)
					.setCallback((index, clickType, actionType) -> {
						player.sendMessage(Text.literal("derg "), false);
						ItemStack item = gui.getSlot(index).getItemStack();
						if (clickType == ClickType.MOUSE_LEFT) {
							item.setCount(item.getCount() == 1 ? item.getCount() : item.getCount() - 1);
						} else if (clickType == ClickType.MOUSE_RIGHT) {
							item.setCount(item.getCount() + 1);
						}
						((GuiElement) gui.getSlot(index)).setItemStack(item);

						if (item.getCount() <= player.getEnderChestInventory().size()) {
							gui.setSlotRedirect(4, new Slot(player.getEnderChestInventory(), item.getCount() - 1, 0, 0));
						}
					})
			);
			gui.setSlotRedirect(4, new Slot(player.getEnderChestInventory(), 0, 0, 0));

			gui.open();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}


}
