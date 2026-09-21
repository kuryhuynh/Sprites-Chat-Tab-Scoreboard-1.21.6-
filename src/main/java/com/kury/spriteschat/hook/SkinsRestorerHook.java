package com.kury.spriteschat.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Hook an toàn kết nối với SkinsRestorer qua reflection.
 * Tương thích với cả SkinsRestorer v14 lẫn v15+ mà không yêu cầu dependency lúc biên dịch.
 */
public final class SkinsRestorerHook {

    private static Boolean hasSkinsRestorer = null;

    private SkinsRestorerHook() {}

    public static boolean isAvailable() {
        if (hasSkinsRestorer == null) {
            try {
                Plugin sr = Bukkit.getPluginManager().getPlugin("SkinsRestorer");
                hasSkinsRestorer = (sr != null && sr.isEnabled());
            } catch (Throwable ignored) {
                hasSkinsRestorer = false;
            }
        }
        return hasSkinsRestorer;
    }

    /**
     * Lấy chuỗi texture Base64 từ SkinsRestorer cho UUID hoặc tên người chơi crack.
     */
    public static String getSkinTexture(UUID uuid, String playerName) {
        if (!isAvailable()) return null;
        try {
            // 1. Thử SkinsRestorer v15+ (SkinsRestorerProvider)
            try {
                Class<?> providerClass = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
                Object sr = providerClass.getMethod("get").invoke(null);
                if (sr != null) {
                    Method getPlayerStorage = sr.getClass().getMethod("getPlayerStorage");
                    Object playerStorage = getPlayerStorage.invoke(sr);
                    if (playerStorage != null) {
                        Method getSkinForPlayer = playerStorage.getClass().getMethod("getSkinForPlayer", UUID.class, String.class);
                        Object opt = getSkinForPlayer.invoke(playerStorage, uuid, playerName);
                        if (opt instanceof Optional<?> optional && optional.isPresent()) {
                            Object prop = optional.get();
                            Method getValue = prop.getClass().getMethod("getValue");
                            return (String) getValue.invoke(prop);
                        }
                    }
                }
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {}

            // 2. Thử SkinsRestorer v14 (SkinsRestorerAPI)
            try {
                Class<?> apiClass = Class.forName("net.skinsrestorer.api.SkinsRestorerAPI");
                Object api = apiClass.getMethod("getApi").invoke(null);
                if (api != null) {
                    Method getSkinProperty = api.getClass().getMethod("getSkinProperty", String.class);
                    Object prop = getSkinProperty.invoke(api, playerName);
                    if (prop != null) {
                        Method getValue = prop.getClass().getMethod("getValue");
                        return (String) getValue.invoke(prop);
                    }
                }
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {}

        } catch (Throwable ignored) {}
        return null;
    }
}
