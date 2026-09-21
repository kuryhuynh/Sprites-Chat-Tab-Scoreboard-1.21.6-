package com.kury.spriteschat.hook;

import com.kury.spriteschat.manager.SpriteManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Tích hợp an toàn (qua reflection và PAPI) để đọc rank prefix/suffix của LuckPerms
 * và tự động chuyển đổi các biểu tượng :token: sang định dạng thẻ của plugin TAB.
 */
public final class LuckPermsHook {

    private LuckPermsHook() {}

    /**
     * Lấy prefix gốc của người chơi từ LuckPerms (hoặc PAPI / Vault fallback).
     */
    public static String getRawPrefix(Player player) {
        if (player == null) return "";

        // 1. Thử dùng LuckPerms API trực tiếp qua reflection
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                Object lp = providerClass.getMethod("get").invoke(null);
                Object userManager = lp.getClass().getMethod("getUserManager").invoke(lp);
                Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUniqueId());
                if (user != null) {
                    Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
                    Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
                    Object prefix = metaData.getClass().getMethod("getPrefix").invoke(metaData);
                    if (prefix != null && !prefix.toString().isEmpty()) {
                        return prefix.toString();
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. Thử dùng PlaceholderAPI fallback (%luckperms_prefix%) qua reflection
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Method method = papiClass.getMethod("setPlaceholders", Player.class, String.class);
                Object res = method.invoke(null, player, "%luckperms_prefix%");
                if (res != null) {
                    String papi = res.toString();
                    if (!papi.isEmpty() && !papi.equals("%luckperms_prefix%")) {
                        return papi;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return "";
    }

    /**
     * Lấy suffix gốc của người chơi từ LuckPerms (hoặc PAPI fallback).
     */
    public static String getRawSuffix(Player player) {
        if (player == null) return "";

        // 1. Thử dùng LuckPerms API trực tiếp qua reflection
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                Object lp = providerClass.getMethod("get").invoke(null);
                Object userManager = lp.getClass().getMethod("getUserManager").invoke(lp);
                Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUniqueId());
                if (user != null) {
                    Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
                    Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
                    Object suffix = metaData.getClass().getMethod("getSuffix").invoke(metaData);
                    if (suffix != null && !suffix.toString().isEmpty()) {
                        return suffix.toString();
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. Thử dùng PlaceholderAPI fallback (%luckperms_suffix%) qua reflection
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Method method = papiClass.getMethod("setPlaceholders", Player.class, String.class);
                Object res = method.invoke(null, player, "%luckperms_suffix%");
                if (res != null) {
                    String papi = res.toString();
                    if (!papi.isEmpty() && !papi.equals("%luckperms_suffix%")) {
                        return papi;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return "";
    }

    /**
     * Lấy rank prefix từ LuckPerms và tự động chuyển đổi tất cả icon :token:
     * sang định dạng thẻ của plugin TAB (<sprite:...>, <head_texture:...>).
     */
    public static String getTabPrefix(Player player, SpriteManager spriteManager) {
        String raw = getRawPrefix(player);
        if (raw == null || raw.isEmpty()) return "";
        return (spriteManager != null) ? spriteManager.convertToTabString(raw, player) : raw;
    }

    /**
     * Lấy rank suffix từ LuckPerms và tự động chuyển đổi tất cả icon :token:
     * sang định dạng thẻ của plugin TAB.
     */
    public static String getTabSuffix(Player player, SpriteManager spriteManager) {
        String raw = getRawSuffix(player);
        if (raw == null || raw.isEmpty()) return "";
        return (spriteManager != null) ? spriteManager.convertToTabString(raw, player) : raw;
    }
}