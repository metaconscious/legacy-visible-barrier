package io.github.metaconscious.lvb.legacyfabric;

import net.minecraft.entity.Entity;

/** Main-camera pass ownership shared by the caller and the terrain submission hook. */
public interface BarrierRenderPass {
    void lvb$beginBarriers(Entity camera, double tickDelta);
    void lvb$endBarriers();
}
