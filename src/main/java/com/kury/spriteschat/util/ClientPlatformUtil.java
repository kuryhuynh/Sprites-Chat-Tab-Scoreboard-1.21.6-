package com.kury.spriteschat.util;

import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Tiện ích nhận diện nền tảng (Platform) và phiên bản client của người chơi:
 * - Hỗ trợ phát hiện người chơi Bedrock Edition (kết nối qua Geyser / Floodgate)
 * - Hỗ trợ phát hiện phiên bản protocol Minecraft của client (qua ViaVersion / ProtocolLib)
 * - Giúp hệ thống chuyển đổi định dạng tin nhắn sang ':item:' khi client không hỗ trợ Native ObjectComponent (< 1.21.6 hoặc Bedrock).
 */
public final class ClientPlatformUtil {

    private static boolean floodgateChecked = false;
    private static Method floodgateGetInstanceMethod;
    private static Method floodgateIsPlayerMethod;

    private static boolean geyserChecked = false;
    private static Method geyserApiMethod;
    private static Method geyserIsBedrockMethod;

    private static boolean viaChecked = false;
    private static Method viaGetApiMethod;
    private static Method viaGetPlayerVersionMethod;

    // Dành cho Unit Test
    private static Predicate<UUID> bedrockTester = null;
    private static Function<UUID, Integer> protocolTester = null;

    private ClientPlatformUtil() {}

    /**
     * Thiết lập hàm kiểm tra Bedrock tùy chỉnh (dùng cho Unit Test hoặc Mock).
     */
    public static void setBedrockTester(Predicate<UUID> tester) {
        bedrockTester = tester;
    }

    /**
     * Thiết lập hàm kiểm tra Protocol Version tùy chỉnh (dùng cho Unit Test hoặc Mock).
     */
    public static void setProtocolTester(Function<UUID, Integer> tester) {
        protocolTester = tester;
    }

    /**
     * Xóa các tester giả lập sau khi chạy Unit Test xong.
     */
    public static void resetTesters() {
        bedrockTester = null;
        protocolTester = null;
    }

    /**
     * Kiểm tra xem người chơi có phải đang chơi Minecraft Bedrock Edition (Geyser / Floodgate) hay không.
     */
    public static boolean isBedrock(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();

        if (bedrockTester != null) {
            try {
                if (bedrockTester.test(uuid)) return true;
            } catch (Throwable ignored) {}
        }

        // 1. Kiểm tra Floodgate API
        if (!floodgateChecked) {
            floodgateChecked = true;
            try {
                Class<?> fgClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateGetInstanceMethod = fgClass.getMethod("getInstance");
                floodgateIsPlayerMethod = fgClass.getMethod("isFloodgatePlayer", UUID.class);
            } catch (Throwable ignored) {}
        }
        if (floodgateGetInstanceMethod != null && floodgateIsPlayerMethod != null) {
            try {
                Object instance = floodgateGetInstanceMethod.invoke(null);
                if (instance != null) {
                    Boolean isFg = (Boolean) floodgateIsPlayerMethod.invoke(instance, uuid);
                    if (isFg != null && isFg) return true;
                }
            } catch (Throwable ignored) {}
        }

        // 2. Kiểm tra Geyser API
        if (!geyserChecked) {
            geyserChecked = true;
            try {
                Class<?> gClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                geyserApiMethod = gClass.getMethod("api");
                geyserIsBedrockMethod = gClass.getMethod("isBedrockPlayer", UUID.class);
            } catch (Throwable ignored) {}
        }
        if (geyserApiMethod != null && geyserIsBedrockMethod != null) {
            try {
                Object api = geyserApiMethod.invoke(null);
                if (api != null) {
                    Boolean isBedrock = (Boolean) geyserIsBedrockMethod.invoke(api, uuid);
                    if (isBedrock != null && isBedrock) return true;
                }
            } catch (Throwable ignored) {}
        }

        // 3. Kiểm tra tiền tố tên người chơi mặc định của Floodgate (. hoặc *)
        String name = player.getName();
        return name != null && (name.startsWith(".") || name.startsWith("*"));
    }

    /**
     * Lấy protocol version của client người chơi thông qua ViaVersion.
     * Trả về -1 nếu không thể xác định hoặc không có ViaVersion.
     */
    public static int getClientProtocolVersion(Player player) {
        if (player == null) return -1;
        UUID uuid = player.getUniqueId();

        if (protocolTester != null) {
            try {
                Integer v = protocolTester.apply(uuid);
                if (v != null) return v;
            } catch (Throwable ignored) {}
        }

        if (!viaChecked) {
            viaChecked = true;
            try {
                Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
                viaGetApiMethod = viaClass.getMethod("getAPI");
                Class<?> viaApiClass = Class.forName("com.viaversion.viaversion.api.ViaAPI");
                viaGetPlayerVersionMethod = viaApiClass.getMethod("getPlayerVersion", UUID.class);
            } catch (Throwable ignored) {}
        }
        if (viaGetApiMethod != null && viaGetPlayerVersionMethod != null) {
            try {
                Object viaApi = viaGetApiMethod.invoke(null);
                if (viaApi != null) {
                    Integer version = (Integer) viaGetPlayerVersionMethod.invoke(viaApi, uuid);
                    if (version != null && version > 0) {
                        return version;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return -1;
    }

    /**
     * Kiểm tra xem người xem (viewer) có thuộc diện cần hiển thị định dạng Legacy ':item:' hay không.
     * Áp dụng khi:
     * 1. Viewer là Bedrock (Geyser/Floodgate)
     * 2. Viewer là Java có phiên bản protocol < minProtocolVersion (mặc định 771 = 1.21.6)
     * 3. Viewer không phải là Player (ví dụ Console / Server Log) để tránh in ra các chuỗi thô rác.
     */
    public static boolean isLegacyOrBedrock(Audience viewer, int minProtocolVersion) {
        if (viewer == null) return false;
        if (viewer instanceof Player player) {
            return isLegacyOrBedrock(player, minProtocolVersion);
        }
        // Console / Logging viewers
        return true;
    }

    /**
     * Kiểm tra người chơi cụ thể có thuộc diện hiển thị định dạng Legacy ':item:' hay không.
     */
    public static boolean isLegacyOrBedrock(Player player, int minProtocolVersion) {
        if (player == null) return false;
        if (isBedrock(player)) return true;

        int protocol = getClientProtocolVersion(player);
        return protocol > 0 && protocol < minProtocolVersion;
    }
}
