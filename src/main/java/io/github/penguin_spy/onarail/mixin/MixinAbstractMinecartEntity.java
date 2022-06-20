package io.github.penguin_spy.onarail.mixin;

import io.github.penguin_spy.onarail.Linkable;
import io.github.penguin_spy.onarail.OnARail;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(AbstractMinecartEntity.class)
public abstract class MixinAbstractMinecartEntity extends Entity implements Linkable {
	public MixinAbstractMinecartEntity(EntityType<?> entityType, World world) {
		super(entityType, world);
	}

	private Linkable parentMinecart;
	private UUID parentUuid;
	private Linkable childMinecart;
	private UUID childUuid;

	@Override
	public ActionResult interact(PlayerEntity eitherPlayer, Hand hand) {
		if(eitherPlayer instanceof ServerPlayerEntity player) {
			return OnARail.tryLink(this, player, hand);
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
		if(parentUuid != null || childUuid != null) {
			NbtCompound onARailNbt = new NbtCompound();
			if(parentUuid != null) {
				onARailNbt.putUuid("parentUUID", parentUuid);
			}
			if(childUuid != null) {
				onARailNbt.putUuid("childUUID", childUuid);
			}
			nbt.put("onarail", onARailNbt);
		}
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
		}
	}

	@Inject(method = "tick()V", at = @At("TAIL"))
	public void tick(CallbackInfo ci) {
		validateLinks();
	}
}
