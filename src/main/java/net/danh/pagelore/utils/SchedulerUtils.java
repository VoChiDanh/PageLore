package net.danh.pagelore.utils;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Routes scheduled work through Folia's region scheduler when available.
 */
public final class SchedulerUtils {

    private SchedulerUtils() {
    }

    public interface TaskHandle {
        void cancel();
    }

    public static TaskHandle runGlobalTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        long safeDelay = Math.max(1L, delayTicks);
        long safePeriod = Math.max(1L, periodTicks);

        if (ServerVersion.isFolia()) {
            ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> runnable.run(), safeDelay, safePeriod);
            return task::cancel;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, safeDelay, safePeriod);
        return task::cancel;
    }

    public static void runEntity(Plugin plugin, Entity entity, Runnable runnable) {
        if (ServerVersion.isFolia()) {
            entity.getScheduler().run(plugin, scheduledTask -> runnable.run(), null);
            return;
        }

        runnable.run();
    }

    public static void runEntityLater(Plugin plugin, Entity entity, Runnable runnable, long delayTicks) {
        long safeDelay = Math.max(1L, delayTicks);

        if (ServerVersion.isFolia()) {
            entity.getScheduler().runDelayed(plugin, scheduledTask -> runnable.run(), null, safeDelay);
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, runnable, safeDelay);
    }
}
