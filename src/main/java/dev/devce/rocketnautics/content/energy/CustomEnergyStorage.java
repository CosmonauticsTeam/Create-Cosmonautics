package dev.devce.rocketnautics.content.energy;

import net.neoforged.neoforge.energy.EnergyStorage;

public class CustomEnergyStorage extends EnergyStorage {

    public CustomEnergyStorage(int capacity) {
        super(capacity);
    }

    public CustomEnergyStorage(int capacity, int maxTransfer) {
        super(capacity, maxTransfer);
    }

    public CustomEnergyStorage(int capacity, int maxReceive, int maxExtract) {
        super(capacity, maxReceive, maxExtract);
    }

    public void setEnergy(int energy) {
        this.energy = Math.max(0, Math.min(energy, this.capacity));
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
        this.energy = Math.min(this.energy, this.capacity);
    }

    public void setMaxReceive(int maxReceive) {
        this.maxReceive = maxReceive;
    }

    public void setMaxExtract(int maxExtract) {
        this.maxExtract = maxExtract;
    }

    public int generateEnergyInternal(int amount) {
        int received = Math.min(this.capacity - this.energy, amount);
        this.energy += received;
        return received;
    }

    public int extractEnergyInternal(int amount) {
        int extracted = Math.min(this.energy, amount);
        this.energy -= extracted;
        return extracted;
    }
}
