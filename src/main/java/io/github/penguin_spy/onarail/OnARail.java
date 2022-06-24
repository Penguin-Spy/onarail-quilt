package io.github.penguin_spy.onarail;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OnARail implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("On A Rail");

	@Override
	public void onInitialize(ModContainer mod) {
		LOGGER.info("Hello Quilt world from {}!", mod.metadata().name());
	}

}
