package com.kury.spriteschat.hook;

import com.kury.spriteschat.SpritesChatPlugin;
import com.kury.spriteschat.manager.SpriteManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Tích hợp trực tiếp với plugin TAB (NEZNAMY) thông qua TabAPI.
 * Đăng ký các placeholder nội bộ của TAB:
 * - %spriteschat_prefix% : Tự động cập nhật rank prefix kèm icon
 * - %spriteschat_suffix% : Tự động cập nhật rank suffix kèm icon
 * Giúp plugin TAB hiển thị icon hoàn hảo ngay cả khi server không dùng PlaceholderAPI.
 */
public final class TabHook {

    private TabHook() {}

    public static void register(SpritesChatPlugin plugin, SpriteManager spriteManager) {
        if (!Bukkit.getPluginManager().isPluginEnabled("TAB")) {
            return;
        }

        try {
            Class<?> tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Object tabApi = tabApiClass.getMethod("getInstance").invoke(null);
            if (tabApi == null) return;

            Object placeholderManager = tabApiClass.getMethod("getPlaceholderManager").invoke(tabApi);
            if (placeholderManager == null) return;

            Class<?> phManagerClass = placeholderManager.getClass();

            // Tìm method registerPlayerPlaceholder(String, int, Function)
            Method registerPlayerPh = null;
            for (Method m : phManagerClass.getMethods()) {
                if ("registerPlayerPlaceholder".equals(m.getName()) && m.getParameterCount() == 3) {
                    registerPlayerPh = m;
                    break;
                }
            }

            if (registerPlayerPh != null) {
                // 1. Đăng ký %spriteschat_prefix% (refresh mỗi 1000ms)
                Function<Object, Object> prefixSupplier = tabPlayer -> {
                    try {
                        UUID uuid = (UUID) tabPlayer.getClass().getMethod("getUniqueId").invoke(tabPlayer);
                        Player player = Bukkit.getPlayer(uuid);
                        return LuckPermsHook.getTabPrefix(player, spriteManager);
                    } catch (Throwable t) {
                        return "";
                    }
                };
                registerPlayerPh.invoke(placeholderManager, "%spriteschat_prefix%", 1000, prefixSupplier);

                // 2. Đăng ký %spriteschat_suffix% (refresh mỗi 1000ms)
                Function<Object, Object> suffixSupplier = tabPlayer -> {
                    try {
                        UUID uuid = (UUID) tabPlayer.getClass().getMethod("getUniqueId").invoke(tabPlayer);
                        Player player = Bukkit.getPlayer(uuid);
                        return LuckPermsHook.getTabSuffix(player, spriteManager);
                    } catch (Throwable t) {
                        return "";
                    }
                };
                registerPlayerPh.invoke(placeholderManager, "%spriteschat_suffix%", 1000, suffixSupplier);

                plugin.getLogger().info("Đã tích hợp thành công với plugin TAB (NEZNAMY) thông qua TabAPI!");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Không thể đăng ký trực tiếp vào TabAPI: " + t.getMessage());
        }
    }
}