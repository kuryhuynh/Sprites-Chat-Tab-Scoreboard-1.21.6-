package com.kury.spriteschat.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kury.spriteschat.api.MinecraftHeadsService;
import com.kury.spriteschat.hook.SkinsRestorerHook;
import com.kury.spriteschat.model.HeadItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiện ích quản lý và phân giải UUID và Skin cho người chơi Minecraft:
 * - Hỗ trợ đầy đủ người chơi crack (Offline-mode) trong server
 * - Hỗ trợ đầy đủ tài khoản Premium ngoài server (Notch, Dream, MumboJumbo...) qua Mojang API / Crafthead CDN
 * - Tích hợp bộ nhớ đệm RAM và lưu trữ đĩa (cache/skins/) để đạt tốc độ tức thì và hoạt động mượt mà trên Folia.
 */
public final class CrackedPlayerUtil {

    private static final Map<String, PlayerSkinProfile> PROFILE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> SKIN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, UUID> UUID_CACHE = new ConcurrentHashMap<>();
    private static File skinsCacheDir = null;

    // Texture mặc định của Steve (HTTPS)
    public static final String STEVE_TEXTURE_BASE64 =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzQ2NjU0NzZhMDhjOGEyNDg5ZTIyM2Q2YTY2ZmJmZTY4MTk0NDgwNDE1YTc3Y2M1NzZjOTY1ZTY0YWU1NCJ9fX0=";

    // Texture mặc định của Alex (HTTPS)
    public static final String ALEX_TEXTURE_BASE64 =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzk5ZjE4N2E1Y2MxNTM3MjM3ZWVjNWRhN2M4ZDliZTc5NGM0ODk3ZjFmMDc0ZDI4OTA2MzcxY2Y3ZDcyMmUwZGEifX19";

    private CrackedPlayerUtil() {}

    public static void setCacheDirectory(File baseDir) {
        if (baseDir != null) {
            skinsCacheDir = new File(baseDir, "skins");
            if (!skinsCacheDir.exists()) {
                skinsCacheDir.mkdirs();
            }
        }
    }

    /**
     * Thông tin đầy đủ về skin và UUID của một người chơi (cả Premium lẫn Crack).
     */
    public static class PlayerSkinProfile {
        private final String name;
        private final UUID uuid;
        private final String textureBase64;
        private final String textureUrl;
        private final boolean premium;

        public PlayerSkinProfile(String name, UUID uuid, String textureBase64, String textureUrl, boolean premium) {
            this.name = name;
            this.uuid = uuid;
            this.textureBase64 = textureBase64;
            this.textureUrl = textureUrl;
            this.premium = premium;
        }

        public String getName() {
            return name;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getTextureBase64() {
            return textureBase64;
        }

        public String getTextureUrl() {
            return textureUrl;
        }

        public boolean isPremium() {
            return premium;
        }
    }

    /**
     * Phân giải đầy đủ thông tin PlayerSkinProfile cho bất kỳ tên người chơi nào.
     * Thứ tự ưu tiên:
     * 1. RAM Cache (PROFILE_CACHE)
     * 2. Disk Cache (cache/skins/<name>.json)
     * 3. Người chơi đang online trên server (trích xuất trực tiếp PlayerProfile)
     * 4. SkinsRestorer API (nếu server có cài đặt)
     * 5. Paper OfflinePlayer profile cache (usercache.json)
     * 6. Truy vấn tài khoản Premium ngoài server qua Crafthead / Mojang API
     * 7. Fallback người chơi crack không có skin (UUID v3 MD5 + Steve/Alex)
     */
    public static PlayerSkinProfile resolvePlayerProfile(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            UUID defaultUuid = UUID.randomUUID();
            return new PlayerSkinProfile("Steve", defaultUuid, STEVE_TEXTURE_BASE64, null, false);
        }
        String lower = playerName.toLowerCase(Locale.ROOT);

        // 1. Kiểm tra RAM Cache
        PlayerSkinProfile cached = PROFILE_CACHE.get(lower);
        if (cached != null) {
            return cached;
        }

        // 2. Kiểm tra Disk Cache (cache/skins/)
        PlayerSkinProfile disk = loadFromDiskCache(lower);
        if (disk != null) {
            cacheProfileInMemory(lower, disk);
            return disk;
        }

        // 3. Người chơi đang ONLINE trên server (lấy skin profile đang mặc)
        try {
            Player online = Bukkit.getPlayerExact(playerName);
            if (online != null) {
                String tex = extractTextureFromProfile(online.getPlayerProfile());
                if (tex != null && !tex.isEmpty()) {
                    String safeTex = HeadItem.ensureHttpsTextureValue(tex);
                    String texUrl = MinecraftHeadsService.extractTextureUrlFromBase64(safeTex);
                    PlayerSkinProfile profile = new PlayerSkinProfile(online.getName(), online.getUniqueId(), safeTex, texUrl, true);
                    saveToCache(lower, profile);
                    return profile;
                }
            }
        } catch (Throwable ignored) {}

        // 4. Kiểm tra SkinsRestorer hook
        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
        String srSkin = SkinsRestorerHook.getSkinTexture(offlineUuid, playerName);
        if (srSkin != null && !srSkin.isEmpty()) {
            String safeTex = HeadItem.ensureHttpsTextureValue(srSkin);
            String texUrl = MinecraftHeadsService.extractTextureUrlFromBase64(safeTex);
            PlayerSkinProfile profile = new PlayerSkinProfile(playerName, offlineUuid, safeTex, texUrl, false);
            saveToCache(lower, profile);
            return profile;
        }

        // 5. Kiểm tra Paper OfflinePlayer profile cache
        try {
            OfflinePlayer off = Bukkit.getOfflinePlayer(playerName);
            if (off != null) {
                String tex = extractTextureFromProfile(off.getPlayerProfile());
                if (tex != null && !tex.isEmpty()) {
                    String safeTex = HeadItem.ensureHttpsTextureValue(tex);
                    String texUrl = MinecraftHeadsService.extractTextureUrlFromBase64(safeTex);
                    UUID u = off.getUniqueId();
                    PlayerSkinProfile profile = new PlayerSkinProfile(playerName, u != null ? u : offlineUuid, safeTex, texUrl, true);
                    saveToCache(lower, profile);
                    return profile;
                }
            }
        } catch (Throwable ignored) {}

        // 6. TRUY VẤN TÀI KHOẢN PREMIUM MOJANG NGOÀI SERVER (Acc Premium ngoài server như Notch, Dream, MumboJumbo)
        PlayerSkinProfile premium = fetchMojangProfile(playerName);
        if (premium != null) {
            saveToCache(lower, premium);
            return premium;
        }

        // 7. Fallback cho người chơi crack không có skin trên server và không phải acc premium
        String fallbackTex = getDefaultSkinTexture(offlineUuid);
        PlayerSkinProfile crackedFallback = new PlayerSkinProfile(playerName, offlineUuid, fallbackTex, null, false);
        cacheProfileInMemory(lower, crackedFallback);
        return crackedFallback;
    }

    /**
     * Truy vấn skin và UUID chính chủ của tài khoản Premium từ Mojang API / Crafthead CDN.
     */
    public static PlayerSkinProfile fetchMojangProfile(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;

        // 1. Kênh Crafthead CDN (Rất nhanh, Cloudflare cache, trả về UUID + Base64 textures trong 1 request)
        try {
            URI uri = new URI("https://crafthead.net/profile/" + playerName);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SpritesChat/1.0");
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            int code = conn.getResponseCode();
            if (code == 200) {
                try (InputStreamReader isr = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(isr).getAsJsonObject();
                    if (json.has("id") && json.has("properties")) {
                        String rawId = json.get("id").getAsString();
                        String realName = json.has("name") ? json.get("name").getAsString() : playerName;
                        UUID uuid = parseUUID(rawId);
                        JsonArray props = json.getAsJsonArray("properties");
                        String textureBase64 = null;
                        for (JsonElement el : props) {
                            JsonObject prop = el.getAsJsonObject();
                            if ("textures".equalsIgnoreCase(prop.get("name").getAsString())) {
                                textureBase64 = prop.get("value").getAsString();
                                break;
                            }
                        }
                        if (uuid != null && textureBase64 != null && !textureBase64.isEmpty()) {
                            String safeTex = HeadItem.ensureHttpsTextureValue(textureBase64);
                            String texUrl = MinecraftHeadsService.extractTextureUrlFromBase64(safeTex);
                            return new PlayerSkinProfile(realName, uuid, safeTex, texUrl, true);
                        }
                    }
                }
            } else if (code == 204 || code == 404) {
                // Đã xác nhận không tồn tại acc premium này
                return null;
            }
        } catch (Throwable ignored) {}

        // 2. Kênh dự phòng Mojang Official Session Server (api.mojang.com -> sessionserver.mojang.com)
        try {
            URI userUri = new URI("https://api.mojang.com/users/profiles/minecraft/" + playerName);
            HttpURLConnection userConn = (HttpURLConnection) userUri.toURL().openConnection();
            userConn.setRequestMethod("GET");
            userConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SpritesChat/1.0");
            userConn.setConnectTimeout(2500);
            userConn.setReadTimeout(2500);
            int userCode = userConn.getResponseCode();
            if (userCode == 200) {
                String rawId = null;
                String realName = playerName;
                try (InputStreamReader isr = new InputStreamReader(userConn.getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject userJson = JsonParser.parseReader(isr).getAsJsonObject();
                    rawId = userJson.get("id").getAsString();
                    if (userJson.has("name")) {
                        realName = userJson.get("name").getAsString();
                    }
                }
                if (rawId != null) {
                    UUID uuid = parseUUID(rawId);
                    URI sessUri = new URI("https://sessionserver.mojang.com/session/minecraft/profile/" + rawId);
                    HttpURLConnection sessConn = (HttpURLConnection) sessUri.toURL().openConnection();
                    sessConn.setRequestMethod("GET");
                    sessConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SpritesChat/1.0");
                    sessConn.setConnectTimeout(2500);
                    sessConn.setReadTimeout(2500);
                    if (sessConn.getResponseCode() == 200) {
                        try (InputStreamReader sessIsr = new InputStreamReader(sessConn.getInputStream(), StandardCharsets.UTF_8)) {
                            JsonObject sessJson = JsonParser.parseReader(sessIsr).getAsJsonObject();
                            JsonArray props = sessJson.getAsJsonArray("properties");
                            String textureBase64 = null;
                            for (JsonElement el : props) {
                                JsonObject prop = el.getAsJsonObject();
                                if ("textures".equalsIgnoreCase(prop.get("name").getAsString())) {
                                    textureBase64 = prop.get("value").getAsString();
                                    break;
                                }
                            }
                            if (uuid != null && textureBase64 != null && !textureBase64.isEmpty()) {
                                String safeTex = HeadItem.ensureHttpsTextureValue(textureBase64);
                                String texUrl = MinecraftHeadsService.extractTextureUrlFromBase64(safeTex);
                                return new PlayerSkinProfile(realName, uuid, safeTex, texUrl, true);
                            }
                        }
                    }
                }
            } else if (userCode == 204 || userCode == 404) {
                return null;
            }
        } catch (Throwable ignored) {}

        // 3. Kênh dự phòng Paper PlayerProfile complete
        try {
            com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(playerName);
            if (profile.complete(true)) {
                String tex = extractTextureFromProfile(profile);
                if (tex != null && !tex.isEmpty()) {
                    String safeTex = HeadItem.ensureHttpsTextureValue(tex);
                    String texUrl = MinecraftHeadsService.extractTextureUrlFromBase64(safeTex);
                    UUID u = profile.getId();
                    if (u != null) {
                        return new PlayerSkinProfile(profile.getName() != null ? profile.getName() : playerName, u, safeTex, texUrl, true);
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Chuyển đổi chuỗi UUID (có hoặc không có dấu gạch ngang) sang đối tượng java.util.UUID.
     */
    public static UUID parseUUID(String str) {
        if (str == null || str.isEmpty()) return null;
        try {
            if (str.contains("-")) {
                return UUID.fromString(str);
            }
            if (str.length() == 32) {
                String dashed = str.substring(0, 8) + "-" +
                        str.substring(8, 12) + "-" +
                        str.substring(12, 16) + "-" +
                        str.substring(16, 20) + "-" +
                        str.substring(20);
                return UUID.fromString(dashed);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void cacheProfileInMemory(String lower, PlayerSkinProfile profile) {
        PROFILE_CACHE.put(lower, profile);
        SKIN_CACHE.put(lower, profile.getTextureBase64());
        UUID_CACHE.put(lower, profile.getUuid());
    }

    private static void saveToCache(String lower, PlayerSkinProfile profile) {
        cacheProfileInMemory(lower, profile);

        // Lưu vào file đĩa nếu là acc premium (để dùng lâu dài không cần gọi mạng lại)
        if (profile.isPremium() && skinsCacheDir != null) {
            try {
                File file = new File(skinsCacheDir, lower + ".json");
                JsonObject json = new JsonObject();
                json.addProperty("name", profile.getName());
                json.addProperty("uuid", profile.getUuid().toString());
                json.addProperty("texture", profile.getTextureBase64());
                if (profile.getTextureUrl() != null) {
                    json.addProperty("url", profile.getTextureUrl());
                }
                json.addProperty("premium", profile.isPremium());
                try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                    writer.write(json.toString());
                }
            } catch (Throwable ignored) {}
        }
    }

    private static PlayerSkinProfile loadFromDiskCache(String lower) {
        if (skinsCacheDir == null || !skinsCacheDir.exists()) return null;
        File file = new File(skinsCacheDir, lower + ".json");
        if (!file.exists()) return null;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            String name = json.get("name").getAsString();
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            String tex = json.get("texture").getAsString();
            String url = json.has("url") ? json.get("url").getAsString() : null;
            boolean premium = json.has("premium") && json.get("premium").getAsBoolean();
            return new PlayerSkinProfile(name, uuid, tex, url, premium);
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Lấy UUID cho người chơi (trả về UUID Mojang thật nếu là acc premium, hoặc UUID offline v3 MD5 nếu là acc crack).
     */
    public static UUID getCrackedUUID(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return UUID.randomUUID();
        }
        return resolvePlayerProfile(playerName).getUuid();
    }

    /**
     * Lấy chuỗi texture Base64 của người chơi (hỗ trợ cả acc premium lẫn crack).
     */
    public static String getCrackedPlayerSkinTexture(String playerName) {
        if (playerName == null || playerName.isEmpty()) return STEVE_TEXTURE_BASE64;
        return resolvePlayerProfile(playerName).getTextureBase64();
    }

    /**
     * Lấy URL ảnh skin (https://textures.minecraft.net/texture/...) từ tên người chơi.
     */
    public static String getCrackedPlayerTextureUrl(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;
        PlayerSkinProfile profile = resolvePlayerProfile(playerName);
        if (profile.getTextureUrl() != null) return profile.getTextureUrl();
        return MinecraftHeadsService.extractTextureUrlFromBase64(profile.getTextureBase64());
    }

    /**
     * Trích xuất property "textures" từ Paper/Canvas PlayerProfile.
     */
    public static String extractTextureFromProfile(Object profileObj) {
        if (profileObj == null) return null;
        try {
            if (profileObj instanceof com.destroystokyo.paper.profile.PlayerProfile profile) {
                for (com.destroystokyo.paper.profile.ProfileProperty prop : profile.getProperties()) {
                    if ("textures".equalsIgnoreCase(prop.getName())) {
                        return prop.getValue();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Lấy texture mặc định (Steve/Alex) dựa theo hash của UUID theo đúng quy chuẩn vanilla Minecraft:
     * Odd hashCode -> Alex (slim), Even hashCode -> Steve (classic).
     */
    public static String getDefaultSkinTexture(UUID uuid) {
        if (uuid != null && (uuid.hashCode() & 1) != 0) {
            return ALEX_TEXTURE_BASE64;
        }
        return STEVE_TEXTURE_BASE64;
    }

    /**
     * Cập nhật thông tin skin vào bộ nhớ đệm khi người chơi đăng nhập.
     */
    public static void updatePlayerCache(Player player) {
        if (player == null) return;
        try {
            String name = player.getName();
            String lower = name.toLowerCase(Locale.ROOT);
            String tex = extractTextureFromProfile(player.getPlayerProfile());
            if (tex != null && !tex.isEmpty()) {
                String safeTex = HeadItem.ensureHttpsTextureValue(tex);
                String texUrl = MinecraftHeadsService.extractTextureUrlFromBase64(safeTex);
                PlayerSkinProfile profile = new PlayerSkinProfile(name, player.getUniqueId(), safeTex, texUrl, true);
                saveToCache(lower, profile);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Lưu trữ thủ công texture base64 cho một người chơi.
     */
    public static void cachePlayerSkin(String playerName, String textureBase64) {
        if (playerName != null && textureBase64 != null) {
            String safeTex = HeadItem.ensureHttpsTextureValue(textureBase64);
            String texUrl = MinecraftHeadsService.extractTextureUrlFromBase64(safeTex);
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
            PlayerSkinProfile profile = new PlayerSkinProfile(playerName, offlineUuid, safeTex, texUrl, true);
            saveToCache(playerName.toLowerCase(Locale.ROOT), profile);
        }
    }

    /**
     * Xóa cache của một người chơi (hữu ích khi người chơi đổi skin bằng /skin).
     */
    public static void invalidateCache(String playerName) {
        if (playerName != null) {
            String lower = playerName.toLowerCase(Locale.ROOT);
            PROFILE_CACHE.remove(lower);
            SKIN_CACHE.remove(lower);
            UUID_CACHE.remove(lower);
            if (skinsCacheDir != null) {
                File file = new File(skinsCacheDir, lower + ".json");
                if (file.exists()) {
                    file.delete();
                }
            }
        }
    }

    /**
     * Dựng Adventure Component Player Head native hiển thị chuẩn 100% skin trong chat.
     * Hỗ trợ cả người chơi online trong server, người chơi crack và acc premium ngoài server (Notch, Dream...).
     */
    public static Component buildPlayerHead(String playerName, boolean hover, boolean click, String clickAction) {
        PlayerSkinProfile profile = resolvePlayerProfile(playerName);
        String safeName = HeadItem.sanitizePlayerName(profile.getName());
        String finalName = (safeName != null) ? safeName : profile.getName();

        PlayerHeadObjectContents.Builder builder = ObjectContents.playerHead()
                .hat(true)
                .name(finalName)
                .id(profile.getUuid())
                .profileProperty(PlayerHeadObjectContents.property("textures", profile.getTextureBase64()));

        Component comp = Component.object(builder.build());

        if (hover) {
            String hoverText = "<yellow><b>[" + profile.getName() + "]</b></yellow>";
            comp = comp.hoverEvent(HoverEvent.showText(MiniMessage.miniMessage().deserialize(hoverText)));
        }

        if (click) {
            if ("COPY_TO_CLIPBOARD".equalsIgnoreCase(clickAction)) {
                comp = comp.clickEvent(ClickEvent.copyToClipboard(":" + profile.getName() + ":"));
            } else {
                comp = comp.clickEvent(ClickEvent.suggestCommand(":" + profile.getName() + ":"));
            }
        }

        return comp;
    }

    /**
     * Giữ nguyên phương thức buildCrackedPlayerHead để đảm bảo tương thích ngược 100%.
     */
    public static Component buildCrackedPlayerHead(String playerName, boolean hover, boolean click, String clickAction) {
        return buildPlayerHead(playerName, hover, click, clickAction);
    }
}
