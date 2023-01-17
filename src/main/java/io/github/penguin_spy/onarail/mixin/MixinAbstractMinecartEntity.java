package io.github.penguin_spy.onarail.mixin;

import com.mojang.datafixers.util.Pair;
import io.github.penguin_spy.onarail.Linkable;
import io.github.penguin_spy.onarail.Util;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

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

	private double velocityMultiplier = 0;

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
	// this *might* in very rare, specific circumstances be able to be called recursively and cause a stack overflow
	// but it shouldn't because after being called once on a minecart all subsequent calls should do nothing.
	private void validateLinks() {
		if(this.parentMinecart == null) {
			if(this.parentUuid != null) {
				Entity parentEntity = ((ServerWorld) this.world).getEntity(this.parentUuid);
				if (parentEntity instanceof Linkable parentLinkable) {
					if (parentLinkable.isChildUuid(this.uuid)) {
						this.parentMinecart = parentLinkable;
					}
			/*} else if(parentEntity != null) {
				OnARail.LOGGER.warn("{}'s parent minecart isn't Linkable: {}", this, parentEntity);*/
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
			/*} else if(childEntity != null) {
				OnARail.LOGGER.warn("{}'s parent minecart isn't Linkable: {}", this, childEntity);*/
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

	@Inject(method="writeCustomDataToNbt(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
	protected void writeCustomDataToNbt(NbtCompound nbt, CallbackInfo ci) {
		//if(parentUuid != null || childUuid != null) {
			NbtCompound onARailNbt = new NbtCompound();
			if(parentUuid != null) {
				onARailNbt.putUuid("parentUUID", parentUuid);
			}
			if(childUuid != null) {
				onARailNbt.putUuid("childUUID", childUuid);
			}
			onARailNbt.putInt("direction", travelDirection.getId());
			onARailNbt.putDouble("velocityMultiplier", velocityMultiplier);
			nbt.put("onarail", onARailNbt);
		//}
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
			if(onARailNbt.contains("velocityMultiplier")) {
				velocityMultiplier = onARailNbt.getDouble("velocityMultiplier");
			}
		}
	}

	@Inject(method = "tick()V", at = @At("TAIL"))
	public void tick(CallbackInfo ci) {
		validateLinks();
	}

	/*@Inject(method = "applySlowdown()V", at = @At("HEAD"), cancellable = true)
	protected void applySlowdownMixin(CallbackInfo ci) {
		// if we're not in a train, don't modify behavior
		if(this.parentMinecart != null || this.isFurnace()) {
			applyAcceleration();
		}
			// reimplement water slowdown, ignore passenger/chest contents slowdown (we want the whole train traveling the same speed)
			double d = 0.96;
			if(this.isTouchingWater()) {
				d *= 0.95;
			}
			this.setVelocity(this.getVelocity().multiply(d, 0, d));	// idk why y is 0 but all the original minecraft methods do it so yea

			// and then return from applySlowdown without running the original logic
			ci.cancel();
		//}
	}*/

	/*@Inject(method = "moveOnRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;getMaxOffRailSpeed()D", shift = At.Shift.AFTER),
			locals = LocalCapture.CAPTURE_FAILHARD)
	private double moveOnRail_ignorePassengers(BlockPos pos, BlockState state, CallbackInfo ci, double t) {
		t = 1;
		return t;
	}*/

	@Shadow
	private static Pair<Vec3i, Vec3i> getAdjacentRailPositionsByShape(RailShape railShape) { return new Pair<>(Vec3i.ZERO, Vec3i.ZERO); }
	@Shadow
	private void applySlowdown() {}
	@Shadow
	protected double getMaxOffRailSpeed() { return 0.0; }
	@Shadow
	private Vec3d snapPositionToRail(double d, double e, double f) { return null; }
	@Shadow
	private boolean willHitBlockAt(BlockPos pos) { return false; }


	@Overwrite
	public void moveOnRail(BlockPos pos, BlockState state) {
		this.resetFallDistance();
		double x = this.getX();		// d
		double y = this.getY();		// e
		double z = this.getZ();		// f
		Vec3d startingPosOnRail = this.snapPositionToRail(x, y, z);
		y = pos.getY();

		// set up boost booleans & check for powered rails
		boolean isBoostedByRail = false;	// bl
		boolean shouldBrake = false;		// bl2
		if (state.isOf(Blocks.POWERED_RAIL)) {
			isBoostedByRail = state.get(PoweredRailBlock.POWERED);
			shouldBrake = !isBoostedByRail;
		}

		// push minecarts down ascending rails
		double ascendingRailForce = 0.0078125;	// g
		if (this.isTouchingWater()) {
			ascendingRailForce *= 0.2;
		}

		Vec3d ourVelocity = this.getVelocity();
		RailShape railShape = state.get(((AbstractRailBlock)state.getBlock()).getShapeProperty());
		//noinspection EnhancedSwitchMigration
		switch (railShape) {
			case ASCENDING_EAST:
				this.setVelocity(ourVelocity.add(-ascendingRailForce, 0.0, 0.0));
				++y;
				break;
			case ASCENDING_WEST:
				this.setVelocity(ourVelocity.add(ascendingRailForce, 0.0, 0.0));
				++y;
				break;
			case ASCENDING_NORTH:
				this.setVelocity(ourVelocity.add(0.0, 0.0, ascendingRailForce));
				++y;
				break;
			case ASCENDING_SOUTH:
				this.setVelocity(ourVelocity.add(0.0, 0.0, -ascendingRailForce));
				++y;
		}

		// something to do with x/z directions to adjacent rails
		ourVelocity = this.getVelocity();
		Pair<Vec3i, Vec3i> pair = getAdjacentRailPositionsByShape(railShape);
		Vec3i firstAdjacentRail = pair.getFirst();
		Vec3i secondAdjacentRail = pair.getSecond();
		double dx = (secondAdjacentRail.getX() - firstAdjacentRail.getX());		// h
		double dz = (secondAdjacentRail.getZ() - firstAdjacentRail.getZ());		// i
		double d = Math.sqrt(dx * dx + dz * dz);										// j
		double k = ourVelocity.x * dx + ourVelocity.z * dz;
		if (k < 0.0) {
			dx = -dx;
			dz = -dz;
		}

		// i think this is adjusting our velocity to point along the line between the 2 adjacent rails
		double l = Math.min(2.0, ourVelocity.horizontalLength());
		ourVelocity = new Vec3d(l * dx / d, ourVelocity.y, l * dz / d);
		this.setVelocity(ourVelocity);

		// riding player driving the cart they're riding
		Entity entity = this.getFirstPassenger();
		if (entity instanceof PlayerEntity) {
			Vec3d ridingPlayerVelocity = entity.getVelocity();
			double playerVelocityLength = ridingPlayerVelocity.horizontalLengthSquared();
			double ourVelocityLength = this.getVelocity().horizontalLengthSquared();
			if (playerVelocityLength > 1.0E-4 && ourVelocityLength < 0.01) {
				this.setVelocity(this.getVelocity().add(ridingPlayerVelocity.x * 0.1, 0.0, ridingPlayerVelocity.z * 0.1));
				shouldBrake = false;
			}
		}

		// if we're on an unpowered golden rail (and not being driven by a player), slow down very fast (as fast as if we were derailed)
		if (shouldBrake) {
			double ourVelocityLength = this.getVelocity().horizontalLength();
			if (ourVelocityLength < 0.03) {
				this.setVelocity(Vec3d.ZERO);
			} else {
				this.setVelocity(this.getVelocity().multiply(0.5, 0.0, 0.5));
			}
		}

		// no idea yet but idc (gets/sets position to match rail between adjacent rail positions)
		double o = (double)pos.getX() + 0.5 + (double)firstAdjacentRail.getX() * 0.5;
		double p = (double)pos.getZ() + 0.5 + (double)firstAdjacentRail.getZ() * 0.5;
		double q = (double)pos.getX() + 0.5 + (double)secondAdjacentRail.getX() * 0.5;
		double r = (double)pos.getZ() + 0.5 + (double)secondAdjacentRail.getZ() * 0.5;
		double h2 = q - o;
		double i2 = r - p;
		double s;
		double t;
		double u;
		if (h2 == 0.0) {
			s = z - (double)pos.getZ();
		} else if (i2 == 0.0) {
			s = x - (double)pos.getX();
		} else {
			t = x - o;
			u = z - p;
			s = (t * h2 + u * i2) * 2.0;
		}
		x = o + h2 * s;
		z = p + i2 * s;
		this.setPosition(x, y, z);

		// apply movement velocity & position
		double passengerVelocityMultiplier = this.hasPassengers() ? 0.75 : 1.0;  // minecarts with passengers move slower (but have higher momentum/lose speed slower as per this.applySlowdown() )
		double maxSpeed = this.getMaxOffRailSpeed();
		ourVelocity = this.getVelocity();
		this.move(MovementType.SELF, new Vec3d(MathHelper.clamp(passengerVelocityMultiplier * ourVelocity.x, -maxSpeed, maxSpeed), 0.0, MathHelper.clamp(passengerVelocityMultiplier * ourVelocity.z, -maxSpeed, maxSpeed)));
		// snaps minecart Y to adjacent rail position if we're in the position of said adjacent rail
		if (firstAdjacentRail.getY() != 0 && MathHelper.floor(this.getX()) - pos.getX() == firstAdjacentRail.getX() && MathHelper.floor(this.getZ()) - pos.getZ() == firstAdjacentRail.getZ()) {
			this.setPosition(this.getX(), this.getY() + (double)firstAdjacentRail.getY(), this.getZ());
		} else if (secondAdjacentRail.getY() != 0 && MathHelper.floor(this.getX()) - pos.getX() == secondAdjacentRail.getX() && MathHelper.floor(this.getZ()) - pos.getZ() == secondAdjacentRail.getZ()) {
			this.setPosition(this.getX(), this.getY() + (double)secondAdjacentRail.getY(), this.getZ());
		}

		this.applySlowdown();    // separate call so that subclasses can override with their respective behavior (and then we undo that :troll:

		// scale X/Z velocity when there is a change in Y block pos
		Vec3d adjustedPosOnRail = this.snapPositionToRail(this.getX(), this.getY(), this.getZ());
		if (adjustedPosOnRail != null && startingPosOnRail != null) {
			double dy = (startingPosOnRail.y - adjustedPosOnRail.y) * 0.05;
			ourVelocity = this.getVelocity();
			double ourVelocityLength = ourVelocity.horizontalLength();
			if (ourVelocityLength > 0.0) {
				this.setVelocity(ourVelocity.multiply((ourVelocityLength + dy) / ourVelocityLength, 1.0, (ourVelocityLength + dy) / ourVelocityLength));
			}

			this.setPosition(this.getX(), adjustedPosOnRail.y, this.getZ());
		}

		// scale X/Z velocity when there is a change in X or Z block pos
		int floorX = MathHelper.floor(this.getX());
		int floorY = MathHelper.floor(this.getZ());
		if (floorX != pos.getX() || floorY != pos.getZ()) {
			ourVelocity = this.getVelocity();
			double ourVelocityLength = ourVelocity.horizontalLength();
			this.setVelocity(ourVelocityLength * (double)(floorX - pos.getX()), ourVelocity.y, ourVelocityLength * (double)(floorY - pos.getZ()));
		}

		// do powered_rail stuff
		if (isBoostedByRail) {
			ourVelocity = this.getVelocity();
			double ourVelocityLength = ourVelocity.horizontalLength();
			if (ourVelocityLength > 0.01) {
				// reduce X/Z velocity
				this.setVelocity(ourVelocity.add(ourVelocity.x / ourVelocityLength * 0.06, 0.0, ourVelocity.z / ourVelocityLength * 0.06));
			} else {
				ourVelocity = this.getVelocity();
				double xVel = ourVelocity.x;
				double zVel = ourVelocity.z;
				if (railShape == RailShape.EAST_WEST) {	// positive X is east
					if (this.willHitBlockAt(pos.west())) { // bounce away with velocity (on powered rail)
						xVel = 0.02;
					} else if (this.willHitBlockAt(pos.east())) {
						xVel = -0.02;
					}
				} else {
					if (railShape != RailShape.NORTH_SOUTH) {
						return;
					}

					if (this.willHitBlockAt(pos.north())) {	// bounce away with velocity (on powered rail)
						zVel = 0.02;
					} else if (this.willHitBlockAt(pos.south())) {
						zVel = -0.02;
					}
				}

				this.setVelocity(xVel, ourVelocity.y, zVel);
			}
		}

	}

	protected void applyAcceleration() {
		BlockState state = this.getBlockStateAtPos();

		if (AbstractRailBlock.isRail(state)) {
			RailShape railShape = state.get(((AbstractRailBlock)state.getBlock()).getShapeProperty());
			travelDirection = Util.alignDirWithRail(travelDirection, railShape);
			// debug!!
			//this.setCustomName(Text.literal(this.travelDirection.toString()));

			if(this.isPowered()) {
				double dynamicVelocityMultiplier = 0.4;
				if(this.velocityMultiplier > 0) {
					dynamicVelocityMultiplier = this.velocityMultiplier;
				}

				if (this.isFurnace()) {
					// debug!!
					this.setCustomName(Text.literal(this.travelDirection.toString()));
				} else {
					float distToParent = this.getParent().distanceTo(this);
					// debug!!
					this.setCustomName(Text.literal(String.format("%.2f", distToParent)));

					//if(this.velocityMultiplier == 0) {
					if (distToParent > Util.MINECART_LINK_RANGE) {
						parentMinecart.removeChild();
					} else if (distToParent > 1.65) {
						dynamicVelocityMultiplier += 0.05;
					} else if (distToParent < 1.6) {
						dynamicVelocityMultiplier -= 0.05;
					}
					//}
				}

				this.setVelocity(
						Vec3d.of(travelDirection.getVector())
								.multiply(dynamicVelocityMultiplier)
				);
			} else { // if not powered
				this.setVelocity(Vec3d.ZERO);
			}
		}}

	/*@Inject(method = "moveOnRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState)V", at = @At("TAIL"))
	protected void moveOnRail(BlockPos pos, BlockState state, CallbackInfo ci) {

	}*/
}
