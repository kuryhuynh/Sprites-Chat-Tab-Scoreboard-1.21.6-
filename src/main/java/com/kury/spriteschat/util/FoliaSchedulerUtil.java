package com.kury.spriteschat.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Tiện ích lập lịch an toàn cho cả server Folia và Paper/Spigot.
 */
public final class FoliaSchedulerUtil {

    private static final boolean IS_FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
            try {
                // Kiểm tra method getAsyncScheduler của Folia
                Bukkit.class.getMethod("getAsyncScheduler");
                folia = true;
            } catch (NoSuchMethodException ignored2) {
                folia = false;
            }
        }
        IS_FOLIA = folia;
    }

    private FoliaSchedulerUtil() {}

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    /**
     * Chạy tác vụ bất đồng bộ (Async task).
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Chạy tác vụ bất đồng bộ có độ trễ (Async delayed task).
     * @param delayTicks Số tick delay (1 tick = 50ms)
     */
    public static void runAsyncDelayed(Plugin plugin, Runnable task, long delayTicks) {
        long delayMillis = delayTicks * 50L;
        if (IS_FOLIA) {
            Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayMillis, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }

    /**
     * Chạy tác vụ trên Global Region (dành cho các thao tác chung của server).
     */
    public static void runGlobal(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Chạy tác vụ trên scheduler của một Entity (ví dụ Player) an toàn trên Folia và Paper/Spigot.
     * @param plugin Plugin sở hữu
     * @param entity Thực thể cần lập lịch
     * @param task Công việc cần chạy
     * @param delayTicks Số tick delay (0L để chạy ngay)
     */
    public static void runEntity(Plugin plugin, org.bukkit.entity.Entity entity, Runnable task, long delayTicks) {
        if (entity == null) return;
        if (IS_FOLIA) {
            if (delayTicks <= 0) {
                entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
            } else {
                entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
            }
        } else {
            if (delayTicks <= 0) {
                Bukkit.getScheduler().runTask(plugin, task);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            }
        }
    }
}
