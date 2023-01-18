package io.github.penguin_spy.onarail.mixin;

import io.github.penguin_spy.onarail.Linkable;
import io.github.penguin_spy.onarail.Util;
import io.github.penguin_spy.onarail.gui.FurnaceMinecartGUI;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PoweredRailBlock;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FurnaceMinecartEntity.class)
public abstract class MixinFurnaceMinecartEntity extends AbstractMinecartEntity implements SidedInventory, Linkable {
	private static final int[] SLOT_ORDER = {3, 0, 1, 2}; // pattern, then 3x fuel slots (for fuel/byproducts)
	private DefaultedList<ItemStack> inventory;

	@Shadow
	private int fuel;


	// useless constructor bc mixins
	protected MixinFurnaceMinecartEntity(EntityType<?> entityType, World world) {
		super(entityType, world);
	}
	// real constructor
	@Inject(method="<init>*", at = @At("RETURN"))
	void onConstructed(CallbackInfo ci) {
		this.inventory = DefaultedList.ofSize(4, ItemStack.EMPTY);	// 3x fuel, pattern
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
		// banner slot must be empty, fuel slots don't care
		return slot != 3 || this.inventory.get(3).isEmpty();
	}

	/* SidedInventory methods */
	public int[] getAvailableSlots(Direction side) {
		return SLOT_ORDER;
	}
	public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
		return slot == 3 ? FurnaceMinecartGUI.PatternSlot.matches(stack)
						 : FurnaceMinecartGUI.FuelSlot.matches(stack);
	}
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		if (dir == Direction.DOWN && (slot >= 0 && slot <= 2)) { // pull buckets out of fuel slot
			return stack.isOf(Items.BUCKET);
		} else { // pull banner out
			return true;
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
	}
	@Inject(method="readCustomDataFromNbt(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
	protected void readCustomDataFromNbt(NbtCompound nbt, CallbackInfo ci) {
		Inventories.readNbt(nbt, this.inventory);
	}

	/* FurnaceMinecartEntity methods */

	/**
	 * Handles right-clicking a furnace minecart (open GUI or start coupling).
	 * @reason This is an @overwrite because the original functionality of interact()ing a furnace minecart is to
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
			new FurnaceMinecartGUI(player, this).open();
		}
		return ActionResult.CONSUME;
	}

	// ignore default behavior of furnace minecart's applySlowdown (normally handles acceleration, we do that in AbstractMinecartEntity instead)
	@Inject(method = "applySlowdown()V", at = @At("HEAD"), cancellable = true)
	public void applySlowdown(CallbackInfo ci) {
		super.applySlowdown(); // handles water slowdown & friction
		ci.cancel();
	}

	// remove furnace minecart's restriction on max speed
	@Inject(method = "getMaxOffRailSpeed()D", at = @At("HEAD"), cancellable = true)
	protected void getMaxOffRailSpeed(CallbackInfoReturnable<Double> cir) {
		cir.setReturnValue(super.getMaxOffRailSpeed());
	}

	public boolean isStoppedByActivatorRail() {
		BlockState state = this.world.getBlockState(this.getBlockPos());
		if(state.isOf(Blocks.ACTIVATOR_RAIL)) {
			return state.get(PoweredRailBlock.POWERED);
		}
		return false;
	}

	/* Linkable methods */
	@Override // overrides the implementation in MixinAbstractFurnaceMinecart (the IDE doesn't know that, but that's what this is doing when mixed in)
	public boolean isPowered() {
		return this.fuel > 0 && !isStoppedByActivatorRail();
	}

	public boolean isFurnace() {
		return true;
	}
}
