package dev.devce.rocketnautics.content.fluids;

public interface ITankPressure {
    float getPressure();

    void setPressure(float pressure);

    float getMaxPressure();

    boolean isVenting();

    void setVenting(boolean venting);

    void ventPressure(float amount);

    void addPressure(float amount);

    void triggerCatastrophicExplosion();
}
