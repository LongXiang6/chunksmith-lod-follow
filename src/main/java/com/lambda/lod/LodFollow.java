package com.lambda.lod;

import com.kishku7.chunksmith.Chunksmith;
import com.kishku7.chunksmith.ChunksmithProvider;
import com.kishku7.chunksmith.GenerationTask;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("lambda_lod_follow")
public final class LodFollow {
    private static final Logger LOG = LoggerFactory.getLogger("lambda_lod_follow");
    private static final int CHECK_INTERVAL = 20;
    private static final int ENTER_DELAY = 60;
    private static final int RECENTER_INTERVAL = 30 * 20;
    private static final int MOVE_CHUNKS = 16;
    private static final int JUMP_CHUNKS = 32;
    private static final int RADIUS_CHUNKS = 1024;
    private final boolean voxyPresent = ModList.get().isLoaded("roxy") && ModList.get().isLoaded("voxy");
    private final Map<ResourceKey<Level>, ChunkPos> completed = new HashMap<>();
    private MinecraftServer worldServer;
    private OwnedTask active;
    private ResourceKey<Level> dimension;
    private ChunkPos observedCenter;
    private int nextAttemptAt;
    private int nextRecenterAt;

    public LodFollow() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (!voxyPresent || server.isDedicatedServer() || tick % CHECK_INTERVAL != 0
                || !ChunksmithProvider.isLoaded()) {
            return;
        }
        if (worldServer != server) {
            worldServer = server;
            completed.clear();
            active = null;
            dimension = null;
            nextAttemptAt = 0;
            observedCenter = null;
        }
        ServerPlayer owner = findOwner(server);
        Chunksmith chunksmith = ChunksmithProvider.get();
        if (active != null && !active.isCurrent(chunksmith)) {
            if (!active.task().isStopping() && active.task().isCancelled()) {
                completed.put(active.dimension(), active.center());
            }
            active = null;
        }
        ResourceKey<Level> current = owner == null ? null : owner.level().dimension();
        if (!Objects.equals(dimension, current)) {
            stopDispatch(chunksmith, current == null ? "owner left" : "dimension changed");
            dimension = current;
            observedCenter = null;
            nextAttemptAt = tick + ENTER_DELAY;
        }
        if (owner == null) return;
        ChunkPos center = owner.chunkPosition();
        boolean jumped = observedCenter != null && center.getChessboardDistance(observedCenter) >= JUMP_CHUNKS;
        observedCenter = center;
        if (active != null) {
            if (!active.dimension().equals(current) || active.task().isStopping()) return;
            int distance = center.getChessboardDistance(active.center());
            if (chunksmith.getGenerationTasks().size() > 1 || !chunksmith.getTrimTasks().isEmpty()) {
                stopDispatch(chunksmith, "other Chunksmith task");
            // Only a jump since the previous sample can bypass the re-center interval.
            } else if (distance >= MOVE_CHUNKS && (jumped || tick >= nextRecenterAt)) {
                stopDispatch(chunksmith, "player moved");
            }
            return;
        }

        if (tick < nextAttemptAt || center.equals(completed.get(current))
                || !chunksmith.getGenerationTasks().isEmpty() || !chunksmith.getTrimTasks().isEmpty()) {
            return;
        }
        // The API accepts block units and visits the disc in concentric rings.
        double radius = RADIUS_CHUNKS * 16.0;
        String world = current.location().toString();
        if (!chunksmith.getApi().startTask(world, "circle", center.getMiddleBlockX(),
                center.getMiddleBlockZ(), radius, radius, "concentric")) {
            nextAttemptAt = tick + ENTER_DELAY;
            return;
        }
        GenerationTask task = chunksmith.getGenerationTasks().get(world);
        if (task == null) return;
        active = new OwnedTask(current, world, center, task);
        nextRecenterAt = tick + RECENTER_INTERVAL;
        var selection = task.getSelection();
        LOG.info("Chunksmith LOD follow started dimension={} center={},{} radius_chunks={}"
                        + " radius_blocks={}", world, selection.centerChunkX(), selection.centerChunkZ(),
                selection.radiusChunksX(), selection.radiusX());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (worldServer != null && event.getEntity() instanceof ServerPlayer player && player.getServer() == worldServer
                && worldServer.isSingleplayerOwner(player.getGameProfile())) {
            if (ChunksmithProvider.isLoaded()) stopDispatch(ChunksmithProvider.get(), "owner logout");
            dimension = null;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void stopping(ServerStoppingEvent event) {
        if (event.getServer() == worldServer) {
            if (ChunksmithProvider.isLoaded()) stopDispatch(ChunksmithProvider.get(), "world exit");
            completed.clear();
            active = null;
            worldServer = null;
            dimension = null;
        }
    }

    private static ServerPlayer findOwner(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (server.isSingleplayerOwner(player.getGameProfile())) return player;
        }
        return null;
    }

    private void stopDispatch(Chunksmith chunksmith, String reason) {
        if (active == null || !active.isCurrent(chunksmith) || active.task().isStopping()) return;
        // Keep the task registered while Chunksmith drains in-flight work. The next task
        // reuses the normal chunk files and the Voxy index for its own dimension.
        active.task().stop(true);
        LOG.info("Chunksmith LOD follow draining dimension={} reason={} processed_chunks={}",
                active.world(), reason, active.task().getCount());
    }

    private record OwnedTask(ResourceKey<Level> dimension, String world, ChunkPos center, GenerationTask task) {
        boolean isCurrent(Chunksmith chunksmith) {
            return chunksmith.getGenerationTasks().get(world) == task;
        }
    }
}
