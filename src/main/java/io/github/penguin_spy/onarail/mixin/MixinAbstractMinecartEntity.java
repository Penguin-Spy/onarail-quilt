package io.github.penguin_spy.onarail.mixin;

import io.github.penguin_spy.onarail.Linkable;
import io.github.penguin_spy.onarail.TrainState;
import io.github.penguin_spy.onarail.Util;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(AbstractMinecartEntity.class)
public abstract class MixinAbstractMinecartEntity extends Entity implements Linkable {
	public MixinAbstractMinecartEntity(EntityType<?> entityType, World world) {
		super(entityType, world);
	}
	@Shadow public void dropItems(DamageSource damageSource) {}
	@Shadow public void applySlowdown() {}
	@Shadow public double getMaxOffRailSpeed() { throw new AssertionError(); }

	private static final String ON_A_RAIL_TAG = "onarail";
	private static final String PARENT_UUID_TAG = "parentUUID";
	private static final String CHILD_UUID_TAG = "childUUID";
	private static final String DIRECTION_TAG = "direction";

	protected Linkable parentMinecart;
	private UUID parentUuid;
	private Linkable childMinecart;
	private UUID childUuid;

	private BlockState railBlockState;

	protected Direction travelDirection = Direction.NORTH;
	protected TrainState trainState;

	private void dropLinkItem() {
		this.dropStack(Items.CHAIN.getDefaultStack(), 0.5F);
		this.playLinkSound(false);
	}

	// this shouldn't be called recursively because after being called once on a minecart all subsequent calls should do nothing.
	private void validateLinks() {
		if(this.parentMinecart == null) {
			if(this.parentUuid != null) {
				Entity parentEntity = ((ServerWorld) this.world).getEntity(this.parentUuid);
				if (parentEntity instanceof Linkable parentLinkable) {
					if (parentLinkable.isChildUuid(this.uuid)) {
						this.parentMinecart = parentLinkable;
					}
				}
				if (this.parentMinecart == null) { // if it's still null, we had an invalid link
					this.parentUuid = null;
				}
			}
		} else if(this.parentMinecart.isRemoved()) {
			this.removeParent();
		}

		if(this.childMinecart == null) {
			if(this.childUuid != null) {
				Entity childEntity = ((ServerWorld) this.world).getEntity(this.childUuid);
				if (childEntity instanceof Linkable childLinkable) {
					if (childLinkable.isParentUuid(this.uuid)) {
						this.childMinecart = childLinkable;
					}
				}
				if (this.childMinecart == null) { // if it's still null, we had an invalid link
					this.childUuid = null;
					dropLinkItem();    // only drop in one direction, otherwise if both sides do actually exist it would duplicate the chain item
					// that should only happen when going through dimensions (which this mod will handle later), but whatever
				}
			}
		} else if(this.childMinecart.isRemoved()) {
			this.removeChild();
		}
	}

	private void validateTrainState() {
		if(this.trainState == null && this.isInTrain()) {
			this.getTrainState();
		}
	}

	// called once, before the first tick() and after deserialization
	protected void firstUpdate() {
		validateLinks();
		validateTrainState();
	}

/* --- Linkable methods --- */

	public Linkable getParent() {
		validateLinks();
		return this.parentMinecart;
	}
	public void setParent(@NotNull Linkable minecart) {
		this.parentMinecart = minecart;
		this.parentUuid = minecart.getUuid();
		validateTrainState();
	}
	public Linkable getChild() {
		validateLinks();
		return this.childMinecart;
	}
	public void setChild(@NotNull Linkable minecart) {
		this.childMinecart = minecart;
		this.childUuid = minecart.getUuid();
	}

	public void removeParent() {
		if(this.parentMinecart != null) {
			this.removeChild();
			this.parentMinecart = null;
			this.parentUuid = null;
			this.trainState = null;
		}
	}
	public boolean isParentUuid(UUID parentUuid) {
		return parentUuid.equals(this.parentUuid);
	}
	public boolean hasChild() {
		return this.getChild() != null;
	}
	public void removeChild() {
		if(this.childMinecart != null) {
			this.childMinecart.removeParent();	// notify the child cart that this cart isn't their parent anymore
			this.childMinecart.removeChild();	// recursive call down the train to decouple all carts
			this.childMinecart = null;
			this.childUuid = null;
			dropLinkItem();
		}
	}
	public boolean isChildUuid(UUID childUuid) {
		return childUuid.equals(this.childUuid);
	}

	public void playLinkSound(boolean connecting) {
		if(connecting) {
			this.playSound(SoundEvents.BLOCK_CHAIN_PLACE);
		} else {
			this.playSound(SoundEvents.BLOCK_CHAIN_BREAK);
		}
	}

	public boolean isInTrain() {
		return this.parentMinecart != null || this.isFurnace();
	}

	public TrainState getTrainState() {
		// get it from the parent if we don't have it (and save it for future reference)
		if (this.trainState == null) {
			this.trainState = this.parentMinecart.getTrainState();
		}
		return this.trainState;
	}

	/**
	 * Gets the block state at this minecart's position, or the one below it if that's not a rail block,
	 * or {@code null} if neither are a rail.
	 * @return the BlockState (guaranteed to be an AbstractRailBlock), or null if the minecart is not on a rail currently.
	 */
	@Nullable
	public BlockState getRailBlockState() {
		if(railBlockState != null) { return railBlockState; }

		BlockState state = this.world.getBlockState(this.getBlockPos());
		if (AbstractRailBlock.isRail(state)) { railBlockState = state; return state; }

		state = this.world.getBlockState(this.getBlockPos().down());
		if (AbstractRailBlock.isRail(state)) { railBlockState = state; return state; }

		return null;
	}

	@Nullable
	public RailShape getRailShape() {
		BlockState state = this.getRailBlockState();

		if (state != null) {
			return state.get(((AbstractRailBlock) state.getBlock()).getShapeProperty());
		}

		return null;
	}

	public double horizontalDistanceTo(Entity entity) {
		double dx = (this.getX() - entity.getX());
		double dz = (this.getZ() - entity.getZ());
		return Math.sqrt(dx * dx + dz * dz);
	}

	/* --- AbstractMinecartEntity methods --- */

	@Override
	public ActionResult interact(PlayerEntity eitherPlayer, Hand hand) {
		if(eitherPlayer instanceof ServerPlayerEntity player) {
			return Util.tryLink(this, player, hand);
		} else {
			return ActionResult.PASS;
		}
	}
	@Override
	public void remove(Entity.RemovalReason reason) {
		this.removeChild();	// this isn't deleting the child like Entity#remove does, it's just disconnecting the link
		super.remove(reason);
	}

	@Inject(method="writeCustomDataToNbt(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
	protected void writeCustomDataToNbt(NbtCompound nbt, CallbackInfo ci) {
		NbtCompound onARailNbt = new NbtCompound();
		if(this.parentUuid != null) {
			onARailNbt.putUuid(PARENT_UUID_TAG, this.parentUuid);
		}
		if(this.childUuid != null) {
			onARailNbt.putUuid(CHILD_UUID_TAG, this.childUuid);
		}
		onARailNbt.putInt(DIRECTION_TAG, this.travelDirection.getId());
		nbt.put(ON_A_RAIL_TAG, onARailNbt);
	}
	@Inject(method="readCustomDataFromNbt(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
	protected void readCustomDataFromNbt(NbtCompound nbt, CallbackInfo ci) {
		if(nbt.contains(ON_A_RAIL_TAG)) {
			NbtCompound onARailNbt = nbt.getCompound(ON_A_RAIL_TAG);
			if(onARailNbt.contains(PARENT_UUID_TAG)) {
				this.parentUuid = onARailNbt.getUuid(PARENT_UUID_TAG);
			}
			if(onARailNbt.contains(CHILD_UUID_TAG)) {
				this.childUuid = onARailNbt.getUuid(CHILD_UUID_TAG);
			}
			if(onARailNbt.contains(DIRECTION_TAG)) {
				this.travelDirection = Direction.byId(onARailNbt.getInt(DIRECTION_TAG));
			}
		}
	}

	@Inject(method = "tick()V", at = @At("HEAD"))
	public void tickStart(CallbackInfo ci) {
		if(this.firstUpdate) { firstUpdate(); }
		else { validateLinks(); } // for detecting when parent/child is removed
	}
	@Inject(method = "tick()V", at = @At("TAIL"))
	public void tickEnd(CallbackInfo ci) {
		this.railBlockState = null;
	}


	@Inject(method = "isPushable()Z", at = @At("TAIL"), cancellable = true)
	public void isPushable(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(!this.isInTrain());
	}

	@Inject(method = "applySlowdown()V", at = @At("HEAD"), cancellable = true)
	protected void applySlowdown(CallbackInfo ci) {
		// only modify behavior if we're part of a train
		if(this.isInTrain()) {
			this.applyAcceleration();
			ci.cancel();
		}
	}

	@Inject(method = "getMaxOffRailSpeed()D", at = @At("HEAD"), cancellable = true)
	protected void getMaxOffRailSpeed(CallbackInfoReturnable<Double> cir) {
		if(this.isInTrain()) {
			cir.setReturnValue(100.0); // temp!!!!
			//cir.setReturnValue((this.isTouchingWater() ? 4.0 : 12.0) / 20.0);
			cir.cancel();
		}
	}

	protected void applyAcceleration() {
		RailShape railShape = this.getRailShape();

		if(railShape != null) {
			this.travelDirection = Util.alignDirWithRail(this.travelDirection, railShape);

			double currentSpeed = this.trainState.getCurrentSpeed();
			if(currentSpeed > 0) {

				// have child minecarts speed up or slow down to maintain the correct distance from the locomotive
				if (!this.isFurnace()) {
					double distToParent = this.getParent().horizontalDistanceTo(this);

					if (distToParent > Util.MINECART_LINK_RANGE) {
						this.parentMinecart.removeChild();
					} else if (distToParent > 1.65) {
						currentSpeed += 1 + (10 * (distToParent - 1.65));
					} else if (distToParent < 1.6) {
						currentSpeed -= 2;
					}

					if (this.hasPassengers()) {    // account for moveOnRail's reduction
						currentSpeed /= 0.75;
					}
					this.setCustomName(Text.literal(railShape.name()));
				}

				this.setVelocity(Vec3d.of(this.travelDirection.getVector())
								.multiply(currentSpeed / 20));

			} else {
				this.setVelocity(Vec3d.ZERO);
			}
		}
	}

}
