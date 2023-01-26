package io.github.penguin_spy.onarail.mixin;

import io.github.penguin_spy.onarail.Linkable;
import io.github.penguin_spy.onarail.OnARail;
import io.github.penguin_spy.onarail.TrainState;
import io.github.penguin_spy.onarail.Util;
import io.github.penguin_spy.onarail.gui.FurnaceMinecartGUI;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PiglinBrain;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.qsl.item.content.registry.api.ItemContentRegistries;
import org.quiltmc.qsl.item.setting.api.RecipeRemainderLogicHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(FurnaceMinecartEntity.class)
public abstract class MixinFurnaceMinecartEntity extends MixinAbstractMinecartEntity implements SidedInventory, Linkable {
	protected MixinFurnaceMinecartEntity(EntityType<?> entityType, World world) {
		super(entityType, world);
	}

	@Shadow	private int fuel;
	@Shadow	protected boolean isLit() { return false; }
	@Shadow @SuppressWarnings("unused")	protected void setLit(boolean lit) { }

	private static final int[] SLOT_ORDER = {3, 0, 1, 2}; // pattern, then 3x fuel slots (for fuel/byproducts)
	private static final double FURNACE_FUEL_FACTOR = 9.0 / 4;

	private DefaultedList<ItemStack> inventory;
	private boolean shouldTryRefuel = false;

	// real constructor
	@Inject(method="<init>*", at = @At("RETURN"))
	void onConstructed(CallbackInfo ci) {
		this.inventory = DefaultedList.ofSize(4, ItemStack.EMPTY);	// 3x fuel, pattern
		this.trainState = new TrainState();
		OnARail.LOGGER.info("constructed furnace minecart");
	}

/* --- Inventory methods --- */
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
		System.out.printf("<%s> marked dirty!%n", this.getCustomName());
		if(this.fuel == 0) this.shouldTryRefuel = true;
	}
	public boolean canPlayerUse(PlayerEntity player) {
		return !this.isRemoved() && this.getPos().isInRange(player.getPos(), 8.0);
	}
	public boolean isValid(int slot, ItemStack stack) {
		// banner slot must be empty, fuel slots don't care
		return slot != 3 || this.inventory.get(3).isEmpty();
	}

/* --- SidedInventory methods --- */
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

/* --- AbstractMinecartEntity methods --- */

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
		this.trainState.writeCustomDataToNbt(nbt);
	}
	@Inject(method="readCustomDataFromNbt(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
	protected void readCustomDataFromNbt(NbtCompound nbt, CallbackInfo ci) {
		Inventories.readNbt(nbt, this.inventory);
		this.trainState.readCustomDataFromNbt(nbt);
	}

/* --- FurnaceMinecartEntity methods --- */

	/**
	 * @reason don't need any of the default behavior, easier to overwrite than try to disable
	 * @author Penguin_Spy
	 */
	@Overwrite
	public void tick() {
		// update trainState before running the AbstractMinecartEntity.tick()
		if (!this.world.isClient()) {

			// if this furnace is active (moving)
			if (!shouldStop()) {
				this.setLit(true);
				this.fuel--;
				this.shouldTryRefuel = this.fuel <= 0;

				double targetSpeed = trainState.getTargetSpeedValue();
				if(targetSpeed != -1) {
					double currentSpeed = trainState.getCurrentSpeed();
					RailShape railShape = this.getRailShape();

					// determine speed based on the conditions of the furnace minecart (should eventually base this on all carts' status)
					if (railShape != null && railShape.isAscending()) {
						if (Util.isTravelingUphill(this.travelDirection, railShape)) {
							//this.setCustomName(Text.literal(this.getCustomName() + " up"));
							targetSpeed *= 0.7;
						} else {
							//this.setCustomName(Text.literal(this.getCustomName() + " down"));
							targetSpeed *= 0.6;    // once i fix trains staying together downhill, this should instead increase the speed
							// also, the minecraft moveOnRail() code that pushes minecarts down ascending rails messes with this
							//  and with the catch-up & slow-down code in applyAcceleration()
						}
					}
					if (this.isTouchingWater()) {
						//this.setCustomName(Text.literal(this.getCustomName() + " water"));
						targetSpeed *= 0.5;
					}

					// accelerate at 0.2m/s²
					if (currentSpeed < targetSpeed - 0.01) {
						trainState.setCurrentSpeed(currentSpeed + 0.2);
					} else if (currentSpeed > targetSpeed + 0.01) {
						trainState.setCurrentSpeed(currentSpeed - 0.2);
					} else { // close enough, just snap to the target speed
						trainState.setCurrentSpeed(targetSpeed);
					}
				}

			} else { // the train is currently stopped/stopping
				this.setLit(false);
				double currentSpeed = trainState.getCurrentSpeed();
				if(currentSpeed > 0.01) {
					trainState.setCurrentSpeed(currentSpeed - 0.5);
				} else {
					trainState.setCurrentSpeed(0.0);
				}
			}

			this.setCustomName(Text.literal(Double.toString(trainState.getCurrentSpeed())));

			// try to consume fuel from each slot in order
			if(this.shouldTryRefuel) {
				for(int i = 0; i <= 2; i++) {
					ItemStack fuelStack = this.inventory.get(i);
					if(fuelStack.isEmpty()) continue;

					Item fuelItem = fuelStack.getItem();
					Optional<Integer> fuelTime = ItemContentRegistries.FUEL_TIMES.get(fuelItem);

					if(fuelTime.isPresent()) {
						this.fuel += fuelTime.get() * FURNACE_FUEL_FACTOR;
						fuelStack.decrement(1);
						if(fuelStack.isEmpty() && fuelItem.hasRecipeRemainder()) {
							ItemStack remainderStack = RecipeRemainderLogicHandler.getRemainder(fuelStack, null);
							this.inventory.set(i, remainderStack);
						}
						break;
					}
					this.shouldTryRefuel = false; // regardless of if successful
				}
			}
		}

		super.tick();

		// client-side, only for singleplayer!
		if (this.isLit() && this.random.nextInt(4) == 0) {
			this.world.addParticle(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.8, this.getZ(), 0.0, 0.0, 0.0);
		}

	}

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

	public boolean shouldStop() {
		if(this.fuel <= 0) { return true; }

		BlockState state = this.getRailBlockState();
		if(state == null) { return true; }

		if(state.isOf(Blocks.ACTIVATOR_RAIL)) {
			return state.get(PoweredRailBlock.POWERED);
		}

		return false; // has fuel, is on a rail, is not on a powered activator rail
	}

/* --- Linkable methods --- */

	@Override
	public TrainState getTrainState() {
		return this.trainState;
	}

	@Override
	public boolean isFurnace() {
		return true;
	}
}
