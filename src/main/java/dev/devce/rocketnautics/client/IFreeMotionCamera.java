package dev.devce.rocketnautics.client;

import org.joml.Vector3f;

public interface IFreeMotionCamera {
    float rocketnautics$getZRot();
    void rocketnautics$setZRot(float zRot);
    void rocketnautics$setRotation(float yRot, float xRot, float zRot);

    Vector3f rocketnautics$getRotation();
}
