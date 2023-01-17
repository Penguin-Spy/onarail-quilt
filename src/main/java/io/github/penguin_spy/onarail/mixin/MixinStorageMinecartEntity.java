package io.github.penguin_spy.onarail.mixin;

import io.github.penguin_spy.onarail.Linkable;
import io.github.penguin_spy.onarail.Util;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StorageMinecartEntity.class)
public abstract class MixinStorageMinecartEntity extends MixinAbstractMinecartEntity implements Linkable {
	protected MixinStorageMinecartEntity(EntityType<?> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "interact(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", at = @At("HEAD"), cancellable = true)
	public void interact(PlayerEntity eitherPlayer, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if(eitherPlayer instanceof ServerPlayerEntity player) {
			ActionResult result = Util.tryLink(this, player, hand);
			if (result != ActionResult.PASS) {
				cir.setReturnValue(result);
			}
		}
	}

	@Inject(method = "applySlowdown()V", at = @At("HEAD"))
	protected void applySlowdown(CallbackInfo ci) {
		// if we're not in a train, don't modify behavior
		if(parentMinecart != null) applyAcceleration();
	}
}
