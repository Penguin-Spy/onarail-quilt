package io.github.penguin_spy.onarail;

import net.minecraft.text.Text;

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

	boolean isRemoved();

	Text getName();
	UUID getUuid();
}
