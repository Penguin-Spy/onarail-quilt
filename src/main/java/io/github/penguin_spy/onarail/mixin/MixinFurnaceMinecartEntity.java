package io.github.penguin_spy.onarail.mixin;

import io.github.penguin_spy.onarail.Linkable;
import io.github.penguin_spy.onarail.Util;
import io.github.penguin_spy.onarail.gui.FurnaceMinecartGUI;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PiglinBrain;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceMinecartEntity.class)
public abstract class MixinFurnaceMinecartEntity extends AbstractMinecartEntity implements SidedInventory, Linkable {
	private static final int[] EXTRACT_SLOTS = {4, 0, 1, 2}; // pattern, then 3x fuel slots (for fuel byproducts)
	private static final int[] INSERT_SLOTS = {4, 0, 1, 2, 3}; // pattern, 3x fuel slots, chunk_fuel
	private DefaultedList<ItemStack> inventory;
	private Direction travelDirection = Direction.NORTH;

	@Shadow
	private int fuel;


	// useless constructor bc mixins
	protected MixinFurnaceMinecartEntity(EntityType<?> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method="<init>*", at = @At("RETURN"))
	void onConstructed(CallbackInfo ci) {
		this.inventory = DefaultedList.ofSize(5, ItemStack.EMPTY);	// chunk_fuel, 3x fuel, pattern
	}

	/* Inventory methods */
	public int size() {
		return this.inventory.size();
	}
	public boolean isEmpty() {
		for(ItemStack stack : this.inventory) {
			if(!stack.isEmpty()) return false;
		}
		return true;
	}
	public ItemStack getStack(int slot) {
		return this.inventory.get(slot);
	}
	public ItemStack removeStack(int slot, int amount) {
		return Inventories.splitStack(this.inventory, slot, amount);
	}
	public ItemStack removeStack(int slot) {
		return Inventories.removeStack(this.inventory, slot);
	}
	public void setStack(int slot, ItemStack stack) {
		this.inventory.set(slot, stack);
		if (!stack.isEmpty() && stack.getCount() > this.getMaxCountPerStack()) {
			stack.setCount(this.getMaxCountPerStack());
		}
	}
	public void markDirty() {
	}
	public boolean canPlayerUse(PlayerEntity player) {
		return !this.isRemoved() && this.getPos().isInRange(player.getPos(), 8.0);
	}
	public boolean isValid(int slot, ItemStack stack) {
		// slot 4 must be empty, all other slots don't care
		return slot != 4 || this.inventory.get(4).isEmpty();
	}

	/* SidedInventory methods */
	public int[] getAvailableSlots(Direction side) {
		if (side == Direction.DOWN) {
			return EXTRACT_SLOTS;
		} else {
			return INSERT_SLOTS;
		}
	}
	public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
		return switch (slot) {
			case 3 -> FurnaceMinecartGUI.ChunkFuelSlot.matches(stack);
			case 4 -> FurnaceMinecartGUI.PatternSlot.matches(stack);// && this.inventory.get(slot).isEmpty();
			default -> FurnaceMinecartGUI.FuelSlot.matches(stack);
		};
	}
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		if (slot == 4) {
			return true;
		} else if (dir == Direction.DOWN && (slot >= 0 && slot <= 2)) {
			return stack.isOf(Items.BUCKET);
		} else {
			return false;
		}
	}

	/* AbstractMinecartEntity methods */

	// Drop items in inventory when destroyed
	@Override
	public void dropItems(DamageSource damageSource) {
		if (this.world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
			ItemScatterer.spawn(this.world, this, this);
			if (!this.world.isClient) {
				Entity attacker = damageSource.getSource();
				if (attacker != null && attacker.getType() == EntityType.PLAYER) {
					PiglinBrain.onGuardedBlockInteracted((PlayerEntity)attacker, true);
				}
			}
		}
		super.dropItems(damageSource);
	}
	// drop inventory when deleted by a creative mode player
	@Override
	public void remove(Entity.RemovalReason reason) {
		if (!this.world.isClient && reason.shouldDestroy()) {
			ItemScatterer.spawn(this.world, this, this);
		}
		super.remove(reason);
	}

	@Inject(method="writeCustomDataToNbt(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
	protected void writeCustomDataToNbt(NbtCompound nbt, CallbackInfo ci) {
		Inventories.writeNbt(nbt, this.inventory);
		//if(nbt.contains("onarail")) {
			NbtCompound onARailNbt = nbt.getCompound("onarail");
			onARailNbt.putInt("direction", this.travelDirection.getId());
			nbt.put("onarail", onARailNbt);
		//}
	}
	@Inject(method="readCustomDataFromNbt(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
	protected void readCustomDataFromNbt(NbtCompound nbt, CallbackInfo ci) {
		Inventories.readNbt(nbt, this.inventory);
		if(nbt.contains("onarail")) {
			NbtCompound onARailNbt = nbt.getCompound("onarail");
			this.travelDirection = Direction.byId(onARailNbt.getInt("direction"));
		}
	}

	/* FurnaceMinecartEntity methods */

	/**
	 * Handles right-clicking a furnace minecart (open GUI or start coupling).
	 * @reason This is an overwrite because the original functionality of interact()ing a furnace minecart is to
	 *  		consume coal/charcoal from the player's hand and then "push" the furnace minecart.<br>
	 *  	    None of that functionality is required by this mod; we are completely replacing it, so we just @Overwrite the interact method.
	 * @author Penguin_Spy
	 */
	@Overwrite
	public ActionResult interact(PlayerEntity eitherPlayer, Hand hand) {
		if(!(eitherPlayer instanceof ServerPlayerEntity player)) {
			return ActionResult.SUCCESS;
		} else if(!canPlayerUse(eitherPlayer)) {
			return ActionResult.FAIL;
		}

		ActionResult result = Util.tryLink(this, player, hand);
		if(result != ActionResult.PASS) {
			return result;
		} else {
			try {
				FurnaceMinecartGUI gui = new FurnaceMinecartGUI(player, this);

				gui.open();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return ActionResult.CONSUME;
	}

	/**
	 * Contrary to what the name suggests, this applies the acceleration in the direction of travel of this furnace minecart.
	 * @reason Same deal here, don't need original functionality because it just dealt with pushX & pushY which we've replaced.
	 * @author Penguin_Spy
	 */
	@Overwrite
	public void applySlowdown() {
		BlockState state = this.getBlockStateAtPos();
		RailShape railShape = state.get(((AbstractRailBlock)state.getBlock()).getShapeProperty());

		this.travelDirection = Util.alignDirWithRail(this.travelDirection, railShape);
		// debug!!
		this.setCustomName(Text.literal(this.travelDirection.toString()));

		if(this.fuel > 0) {
			Vec3d velocity = new Vec3d(travelDirection.getOffsetX(), travelDirection.getOffsetY(), travelDirection.getOffsetZ());
			velocity.multiply(0.4);
			this.setVelocity(velocity);
		}

		super.applySlowdown(); // handles water slowdown & friction
	}
}
