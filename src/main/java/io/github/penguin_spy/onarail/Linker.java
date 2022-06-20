package io.github.penguin_spy.onarail;

/**
 * Represents methods of an entity that can link entities together. Implemented by ServerPlayerEntity.
 */
public interface Linker {
	Linkable getLinkingMinecart();
	void setLinkingMinecart(Linkable minecart);

	void stopLinking();
	boolean isLinking();
}
