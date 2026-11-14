package io.github.metaconscious.lvb.legacyfabric;

import net.fabricmc.api.ClientModInitializer;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import org.lwjgl.input.Keyboard;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Integration checks executed by a test-only mod in a fresh client process. */
public final class ClientSmokeTest implements ClientModInitializer {
    private KeyBinding binding;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::verify);
    }

    private void verify(MinecraftClient client) {
        try {
            require(!LegacyFabricClient.barriersVisible(), "Visibility must start hidden");
            require(client.world == null && client.player == null, "Smoke test must start outside a world");
            if (binding == null) {
                new LegacyFabricClient().onInitializeClient();
                int count = 0;
                for (KeyBinding key : client.options.allKeys) {
                    if ("key.toggle_barrier_visibility".equals(key.getTranslationKey())) {
                        binding = key;
                        count++;
                    }
                }
                require(count == 1, "Exactly one toggle control must be registered");
                require(binding.getDefaultCode() == Keyboard.KEY_B, "Default control must be B");
                require("key.category.legacy_visible_barriers".equals(binding.getCategory()), "Control category changed");
                require(Blocks.BARRIER.getBlockType() == -1, "Hidden barrier render type changed");
                require(Blocks.BARRIER.getRenderLayerType() == RenderLayer.TRANSLUCENT, "Barrier must be translucent");
                Class.forName("net.minecraft.client.particle.BarrierParticle");
                Class.forName("net.minecraft.client.BlockStateMapper");
                KeyBinding.onKeyPressed(binding.getCode());
                return;
            }
            require(!binding.wasPressed(), "Queued input outside a world must be consumed");
            Path report = Paths.get(System.getProperty("lvb.smoke.report"));
            Files.createDirectories(report.getParent());
            Files.write(report, ("PASS: Java " + System.getProperty("java.version")
                    + "; client initialization; one B control; out-of-world input; barrier Mixins\n")
                    .getBytes(StandardCharsets.UTF_8));
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
