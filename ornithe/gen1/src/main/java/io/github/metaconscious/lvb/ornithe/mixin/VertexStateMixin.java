package io.github.metaconscious.lvb.ornithe.mixin;

import io.github.metaconscious.lvb.ornithe.VertexOrigin;
import com.mojang.blaze3d.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BufferBuilder.State.class)
public class VertexStateMixin implements VertexOrigin {
    @Unique private double lvb$x;
    @Unique private double lvb$y;
    @Unique private double lvb$z;

    @Override
    public void lvb$setOrigin(double x, double y, double z) {
        lvb$x = x;
        lvb$y = y;
        lvb$z = z;
    }

    @Override public double lvb$originX() { return lvb$x; }
    @Override public double lvb$originY() { return lvb$y; }
    @Override public double lvb$originZ() { return lvb$z; }
}
