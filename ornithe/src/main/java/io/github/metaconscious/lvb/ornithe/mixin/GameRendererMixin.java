package io.github.metaconscious.lvb.ornithe.mixin;

import io.github.metaconscious.lvb.ornithe.BarrierRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Unique
    private final BarrierRenderer lvb$barriers = new BarrierRenderer();

    @Inject(method = "render(IFJ)V",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/render/block/BlockLayer;TRANSLUCENT:Lnet/minecraft/client/render/block/BlockLayer;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 0, shift = At.Shift.AFTER), require = 1, allow = 1)
    private void lvb$renderBarriers(int pass, float tickDelta, long limitTime, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        lvb$barriers.render(client, client.getCamera(), tickDelta);
    }
}
