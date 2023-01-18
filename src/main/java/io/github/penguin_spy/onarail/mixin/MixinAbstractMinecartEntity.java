package io.github.penguin_spy.onarail.mixin;

import io.github.penguin_spy.onarail.Linkable;
import io.github.penguin_spy.onarail.Util;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
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

	protected Linkable parentMinecart;
	private UUID parentUuid;
	private Linkable childMinecart;
	private UUID childUuid;

	private Direction travelDirection = Direction.NORTH;

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

	private void dropLinkItem() {
		this.dropStack(Items.CHAIN.getDefaultStack(), 0.5F);
		this.playLinkSound(false);
	}

	public void playLinkSound(boolean connecting) {
		if(connecting) {
			this.playSound(SoundEvents.BLOCK_CHAIN_PLACE);
		} else {
			this.playSound(SoundEvents.BLOCK_CHAIN_BREAK);
		}
	}

	// todo: check if this this is laggy/inefficient
	//  passing the call up the train for every minecart every tick might be a bit much,
	//  but my only reference point is datapacks, idk how efficient Java is
	// an alternative implementation would be to get the locomotive minecart once (via chained getLocomotive()s )
	//  and then directly reference that each call (don't need to serialize tho, can just obtain & re-cache it when necessary)
	public boolean isPowered() {
		return this.parentMinecart != null && parentMinecart.isPowered();
	}

	public boolean isInTrain() {
		return this.parentMinecart != null || this.isFurnace();
	}

	// this *might* in very rare, specific circumstances be able to be called recursively and cause a stack overflow,
	// but it shouldn't because after being called once on a minecart all subsequent calls should do nothing.
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

	public Linkable getParent() {
		validateLinks();
		return this.parentMinecart;
	}
	public void setParent(@NotNull Linkable minecart) {
		this.parentMinecart = minecart;
		this.parentUuid = minecart.getUuid();
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

	/* AbstractMinecartEntity methods */

	@Inject(method="writeCustomDataToNbt(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
	protected void writeCustomDataToNbt(NbtCompound nbt, CallbackInfo ci) {
		NbtCompound onARailNbt = new NbtCompound();
		if(parentUuid != null) {
			onARailNbt.putUuid("parentUUID", parentUuid);
		}
		if(childUuid != null) {
			onARailNbt.putUuid("childUUID", childUuid);
		}
		onARailNbt.putInt("direction", travelDirection.getId());
		nbt.put("onarail", onARailNbt);
	}
	@Inject(method="readCustomDataFromNbt(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
	protected void readCustomDataFromNbt(NbtCompound nbt, CallbackInfo ci) {
		if(nbt.contains("onarail")) {
			NbtCompound onARailNbt = nbt.getCompound("onarail");
			if(onARailNbt.contains("parentUUID")) {
				parentUuid = onARailNbt.getUuid("parentUUID");
			}
			if(onARailNbt.contains("childUUID")) {
				childUuid = onARailNbt.getUuid("childUUID");
			}
			if(onARailNbt.contains("direction")) {
				travelDirection = Direction.byId(onARailNbt.getInt("direction"));
			}
		}
	}

	@Inject(method = "tick()V", at = @At("TAIL"))
	public void tick(CallbackInfo ci) {
		validateLinks();
	}


	@Inject(method = "isPushable()Z", at = @At("TAIL"), cancellable = true)
	public void isPushable(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(!this.isInTrain());
	}
	/*@Inject(method = "collidesWith(Lnet/minecraft/entity/Entity;)Z", at = @At("TAIL"), cancellable = true)
	public void collidesWith(Entity other, CallbackInfoReturnable<Boolean> cir) {
		if(this.isInTrain()) cir.setReturnValue(false);
	}*/

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
			cir.setReturnValue((this.isTouchingWater() ? 4.0 : 12.0) / 20.0);
			cir.cancel();
		}
	}

	protected void applyAcceleration() {
		BlockState state = this.getBlockStateAtPos();
		BlockState state_below = this.world.getBlockState(this.getBlockPos().down());
		if (state_below.isIn(BlockTags.RAILS)) {
			state = state_below;
		}

		if (AbstractRailBlock.isRail(state)) {
			RailShape railShape = state.get(((AbstractRailBlock)state.getBlock()).getShapeProperty());
			travelDirection = Util.alignDirWithRail(travelDirection, railShape);

			if(this.isPowered()) {
				double dynamicVelocityMultiplier = 0.4;

				// have child minecarts speed up or slow down to maintain the correct distance from the locomotive
				if (!this.isFurnace()) {
					float distToParent = this.getParent().distanceTo(this);

					if (distToParent > Util.MINECART_LINK_RANGE) {
						parentMinecart.removeChild();
					} else if (distToParent > 1.65) {
						dynamicVelocityMultiplier += 0.05 + (0.5 * (distToParent - 1.65));
					} else if (distToParent < 1.6) {
						dynamicVelocityMultiplier -= 0.05;
					}

					if (this.hasPassengers()) {    // account for moveOnRail's reduction
						dynamicVelocityMultiplier /= 0.75;
					}
				}

				this.setCustomName(Text.literal(railShape.name()));
				// reduce velocity when going uphill/downhill, and when in water
				if(railShape.isAscending()) {
					if(Util.isTravelingUphill(travelDirection, railShape)) {
						this.setCustomName(Text.literal(this.getCustomName() + " up"));
						dynamicVelocityMultiplier *= 0.7;
					} else {
						this.setCustomName(Text.literal(this.getCustomName() + " down"));
						dynamicVelocityMultiplier *= 0.6;
					}
				}
				if (this.isTouchingWater()) {
					this.setCustomName(Text.literal(this.getCustomName() + " water"));
					dynamicVelocityMultiplier *= 0.95;
				}

				this.setVelocity(Vec3d.of(travelDirection.getVector())
								.multiply(dynamicVelocityMultiplier));

			} else { // if not powered
				this.setVelocity(Vec3d.ZERO);
			}
		}
	}

}
