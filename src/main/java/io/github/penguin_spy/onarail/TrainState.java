package io.github.penguin_spy.onarail;

import net.minecraft.nbt.NbtCompound;

/**
 * Stores information about the whole train, such as speed, if it's moving, etc.
 * Each minecart of a train has a reference to its trains' instance (created by the locomotive)
 */
public class TrainState {
	private static final String ON_A_RAIL_TAG = "onarail";
	private static final String TRAIN_STATE_TAG = "trainState";
	private static final String TARGET_SPEED_TAG = "targetSpeed";
	private static final String CURRENT_SPEED_TAG = "currentSpeed";

	public Speed targetSpeed;
	public double currentSpeed;
	private boolean stopped; // not serialized, determined from world state (is locomotive on activator rail)

	public TrainState() {
		this.targetSpeed = Speed.MEDIUM;
		this.currentSpeed = 0;
		this.stopped = false;
	}

	public void setStopped(boolean stopped) {
		this.stopped = stopped;
	}
	public boolean isStopped() {
		return this.stopped;
	}

	public void writeCustomDataToNbt(NbtCompound nbt) {
		NbtCompound trainStateNbt = new NbtCompound();
		trainStateNbt.putString(TARGET_SPEED_TAG, this.targetSpeed.name());
		trainStateNbt.putDouble(CURRENT_SPEED_TAG, this.currentSpeed);
		NbtCompound onARailNbt = nbt.getCompound(ON_A_RAIL_TAG);
		onARailNbt.put(TRAIN_STATE_TAG, trainStateNbt);
	}
	public void readCustomDataFromNbt(NbtCompound nbt) {
		if(nbt.contains(ON_A_RAIL_TAG)) {
			NbtCompound onARailNbt = nbt.getCompound(ON_A_RAIL_TAG);
			if(onARailNbt.contains(TRAIN_STATE_TAG)) {
				NbtCompound trainStateNbt = onARailNbt.getCompound(TRAIN_STATE_TAG);
				if(trainStateNbt.contains(TARGET_SPEED_TAG)) {
					// todo: provide better error/handle when this string isn't a real speed value
					this.targetSpeed = Speed.valueOf(trainStateNbt.getString(TARGET_SPEED_TAG));
				}
				if(trainStateNbt.contains(CURRENT_SPEED_TAG)) {
					this.currentSpeed = trainStateNbt.getDouble(CURRENT_SPEED_TAG);
				}
			}
		}
	}


	@SuppressWarnings("unused")
	public enum Speed {
		LOW(3),			// 0.15
		MEDIUM_LOW(5), 	// 0.25
		MEDIUM(8), 		// 0.4
		MEDIUM_HIGH(14),	// 0.7
		HIGH(30);			// 1.5  // needs to be like 70 m/s ish, comparable to boat on blue ice (and better than boat on normal ice)
												// irl high-speed rail can reach speeds of 300-350 km/h (83.3-97.2 m/s), although 250-270 km/h (69.4-75 m/s) is more reasonable

		private final double value;
		Speed(double metersPerSecond) { this.value = metersPerSecond / 20; }

		double getValue() { return this.value; }
	}
}

