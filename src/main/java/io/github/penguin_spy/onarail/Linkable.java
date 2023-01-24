package io.github.penguin_spy.onarail;

import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;


/**
 * Represents methods of an entity that can be linked to/from. Implemented by AbstractMinecartEntity.
 */
public interface Linkable {

	Linkable getParent();
	void setParent(Linkable minecart);
	Linkable getChild();
	void setChild(Linkable minecart);

	void removeParent();
	boolean isParentUuid(UUID parentUuid);
	boolean hasChild();
	void removeChild();
	boolean isChildUuid(UUID childUuid);

	void playLinkSound(boolean connecting);
	default boolean isFurnace() {
		return false;
	}
	boolean isInTrain();

	TrainState getTrainState();

	boolean isRemoved();
	float distanceTo(Entity entity);
	double squaredDistanceTo(Vec3d vector);
	World getWorld();

	Text getName();
	UUID getUuid();
}
