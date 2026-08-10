package dev.devce.rocketnautics.api;

import org.joml.Quaternionf;

public interface FreeMotionEntity {
    boolean is6DOFEnabled();
    void set6DOFEnabled(boolean microgravity);

    boolean isAmbulant();
    void setAmbulant(boolean ambulant);

    Quaternionf getOrientation();
    void setOrientation(Quaternionf quat);

    float getMovementAcceleration();
    void setMovementAcceleration(float speed);

    float getDampenerForce();
    void setDampenerForce(float force);
}
