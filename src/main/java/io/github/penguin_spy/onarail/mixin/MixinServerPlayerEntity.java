package io.github.penguin_spy.onarail.mixin;

import com.mojang.authlib.GameProfile;
import io.github.penguin_spy.onarail.Linkable;
import io.github.penguin_spy.onarail.Linker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity implements Linker {
	public MixinServerPlayerEntity(World world, BlockPos blockPos, float spawnAngle, GameProfile gameProfile) {
		super(world, blockPos, spawnAngle, gameProfile);
	}

	private Linkable linkingMinecart;
	private Text linkingMinecartName; // cache the name so that we don't re-compute it every tick for displaying the action bar

/* --- PlayerEntity methods --- */
	@Inject(method="playerTick()V", at = @At("HEAD"))
	public void playerTick(CallbackInfo ci) {
		if(this.isLinking()) {
			if(    this.linkingMinecart.isRemoved()
				|| this.linkingMinecart.squaredDistanceTo(this.getEyePos()) > ServerPlayNetworkHandler.MAX_INTERACTION_DISTANCE
				|| this.world != this.linkingMinecart.getWorld()
				|| !this.getMainHandStack().isOf(Items.CHAIN)
			) {
				stopLinking();
				this.sendMessage(Text.empty(), true);
			} else {
				this.sendMessage(Text.translatable("text.onarail.link.start_link", this.linkingMinecartName), true);
			}
		}
	}

/* --- Linker methods --- */
	public Linkable getLinkingMinecart() {
		return this.linkingMinecart;
	}
	public void setLinkingMinecart(Linkable minecart) {
		this.linkingMinecart = minecart;
		this.linkingMinecartName = minecart.getName();
	}

	public void stopLinking() {
		this.linkingMinecart = null;
		this.linkingMinecartName = null;
	}

	public boolean isLinking() {
		return this.linkingMinecart != null;
	}
}
