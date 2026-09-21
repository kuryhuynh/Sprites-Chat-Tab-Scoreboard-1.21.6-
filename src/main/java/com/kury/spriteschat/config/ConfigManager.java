package com.kury.spriteschat.config;

import com.kury.spriteschat.model.HeadItem;
import com.kury.spriteschat.model.SpriteItem;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ConfigManager {

    private final Plugin plugin;

    private FileConfiguration config;
    private FileConfiguration spritesConfig;
    private FileConfiguration headsConfig;

    private File configFile;
    private File spritesFile;
    private File headsFile;

    private boolean enableHover;
    private boolean enableClickSuggest;
    private String clickAction;
    private String apiUrl;
    private String userAgent;
    private String cacheDirPath;
    private boolean autoFetch;

    // Cài đặt hiển thị Player Head theo tên (:nameplayer:)
    private boolean playerHeadsEnabled = true;
    private String playerHeadsMode = "ALL";
    private String playerHeadsPermission = "spriteschat.use.players";

    // Cài đặt tích hợp TAB & Scoreboard
    private String tabHeadFormat = "HEAD_TEXTURE";
    private boolean tabFallbackToSprite = true;

    // Cài đặt gợi ý Chat & Tab Complete (:emote)
    private boolean suggestionsEnabled = true;
    private boolean customCompletionsEnabled = true;
    private boolean tabCompleteEnabled = true;
    private int maxTabCompletions = 30;

    // Cài đặt tương thích cho Bedrock (Geyser) & Java < 1.21.6
    private boolean legacyCompatibilityEnabled = true;
    private String legacyFormat = ":{item}:";
    private int minProtocolVersion = 771;
    private boolean legacyApplyToPlaceholders = true;

    private final Map<String, SpriteItem> spritesMap = new LinkedHashMap<>();
    private final Map<String, HeadItem> headsMap = new LinkedHashMap<>();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigurations() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        spritesFile = new File(plugin.getDataFolder(), "sprites.yml");
        headsFile = new File(plugin.getDataFolder(), "heads.yml");

        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        if (!spritesFile.exists()) {
            plugin.saveResource("sprites.yml", false);
        }
        if (!headsFile.exists()) {
            plugin.saveResource("heads.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        spritesConfig = YamlConfiguration.loadConfiguration(spritesFile);
        headsConfig = YamlConfiguration.loadConfiguration(headsFile);

        parseConfigSettings();
        checkAndUpgradeConfigurations();
        parseSprites();
        parseHeads();
    }

    private void parseConfigSettings() {
        this.enableHover = config.getBoolean("interaction.enable-hover", true);
        this.enableClickSuggest = config.getBoolean("interaction.enable-click-suggest", true);
        this.clickAction = config.getString("interaction.click-action", "SUGGEST_COMMAND");

        this.playerHeadsEnabled = config.getBoolean("player-heads.enabled", true);
        this.playerHeadsMode = config.getString("player-heads.mode", "ALL");
        this.playerHeadsPermission = config.getString("player-heads.permission", "spriteschat.use.players");

        this.suggestionsEnabled = config.getBoolean("suggestions.enabled", true);
        this.customCompletionsEnabled = config.getBoolean("suggestions.custom-completions", true);
        this.tabCompleteEnabled = config.getBoolean("suggestions.tab-complete", true);
        this.maxTabCompletions = config.getInt("suggestions.max-tab-completions", 30);

        this.apiUrl = config.getString("minecraft-heads.api-url", "https://minecraft-heads.com/scripts/api.php");
        this.userAgent = config.getString("minecraft-heads.user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SpritesChat/1.0");
        this.cacheDirPath = config.getString("minecraft-heads.cache-directory", "cache/heads");
        this.autoFetch = config.getBoolean("minecraft-heads.auto-fetch", true);

        this.tabHeadFormat = config.getString("tab-integration.head-format", "HEAD_TEXTURE");
        this.tabFallbackToSprite = config.getBoolean("tab-integration.fallback-to-sprite", true);

        this.legacyCompatibilityEnabled = config.getBoolean("legacy-compatibility.enabled", true);
        this.legacyFormat = config.getString("legacy-compatibility.format", ":{item}:");
        this.minProtocolVersion = config.getInt("legacy-compatibility.min-protocol-version", 771);
        this.legacyApplyToPlaceholders = config.getBoolean("legacy-compatibility.apply-to-placeholders", true);
    }

    private void parseSprites() {
        spritesMap.clear();
        ConfigurationSection section = spritesConfig.getConfigurationSection("sprites");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String spriteStr = section.getString(key + ".sprite", "minecraft:item/" + key);
            Key spriteKey = Key.key(spriteStr);
            String atlasStr = section.getString(key + ".atlas", null);
            Key atlas = atlasStr != null ? Key.key(atlasStr) : null;
            String displayName = section.getString(key + ".display_name", key);
            String description = section.getString(key + ".description", "");
            String permission = section.getString(key + ".permission", "spriteschat.use");

            SpriteItem spriteItem = new SpriteItem(key, atlas, spriteKey, displayName, description, permission);
            spritesMap.put(key.toLowerCase(Locale.ROOT), spriteItem);

            // Nạp các alias (tên viết tắt tóm tắt như :pearl:, :crystal:, :gap:)
            List<String> aliases = section.getStringList(key + ".aliases");
            for (String alias : aliases) {
                spritesMap.put(alias.toLowerCase(Locale.ROOT), spriteItem);
            }
        }
    }

    /**
     * Tự động kiểm tra và nâng cấp bổ sung các lá cờ mới và sprite mới từ file jar vào thư mục cấu hình trên đĩa.
     */
    private void checkAndUpgradeConfigurations() {
        try {
            // Cập nhật Cờ Việt Nam nếu bản cũ dùng hash 1c8b...
            String currentVnVal = headsConfig.getString("heads.VietNam.value", "");
            if (currentVnVal != null && currentVnVal.contains("1c8b7c7fb08ab8947812bb9d14da049ed2d51ad8cb932b392c2266b257adc2ac")) {
                headsConfig.set("heads.VietNam.uuid", "70d3bb3c-86fc-471a-9e1a-58a8d43987d8");
                headsConfig.set("heads.VietNam.texture_url", "https://textures.minecraft.net/texture/8a57b9d7dd04169478cfdb8d0b6fd0b8c82b6566bb28371ee9a7c7c1671ad0bb");
                headsConfig.set("heads.VietNam.value", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzhhNTdiOWQ3ZGQwNDE2OTQ3OGNmZGI4ZDBiNmZkMGI4YzgyYjY1NjZiYjI4MzcxZWU5YTdjN2MxNjcxYWQwYmIifX19");
                headsConfig.set("heads.VietNam.aliases", List.of("vietnam", "vn", "VN", "viet_nam", "vietnam_flag", "cờ_việt_nam"));
                try {
                    headsConfig.save(headsFile);
                    plugin.getLogger().info("Đã tự động cập nhật texture Cờ Việt Nam sang phiên bản chuẩn (Image 2)!");
                } catch (IOException ignored) {}
            }

            // Tự động sửa texture Trái Đất (Globe / traidat) nếu bản cũ dùng hash bị lỗi 404
            String currentGlobeVal = headsConfig.getString("heads.globe.value", "");
            String currentGlobeUrl = headsConfig.getString("heads.globe.texture_url", "");
            if ((currentGlobeVal != null && currentGlobeVal.contains("884e9334d580436ea1fbe44747eb1b12de2fcece19a79c94025d57b29aabfed"))
                    || (currentGlobeUrl != null && currentGlobeUrl.contains("884e9334d580436ea1fbe44747eb1b12de2fcece19a79c94025d57b29aabfed"))) {
                headsConfig.set("heads.globe.uuid", "28e6e8ad-40ab-4690-9206-507324177423");
                headsConfig.set("heads.globe.texture_url", "https://textures.minecraft.net/texture/1adbcf23e726f199339b253a5214f8831d62b97a50b0ed932c531f62c3223");
                headsConfig.set("heads.globe.value", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzFhZGJjZjIzZTcyNmYxOTkzMzliMjUzYTUyMTRmODgzMWQ2MmI5N2E1MGIwZWQ5MzJjNTMxZjYyYzMyMjMifX19");
                headsConfig.set("heads.globe.display_name", "<aqua><b>[Trái Đất]</b></aqua>");
                headsConfig.set("heads.globe.description", "");
                headsConfig.set("heads.globe.aliases", List.of("earth", "traidat", "trai_dat", "world"));
                try {
                    headsConfig.save(headsFile);
                    plugin.getLogger().info("Đã tự động cập nhật texture Trái Đất (Globe/traidat) sang phiên bản hoạt động!");
                } catch (IOException ignored) {}
            }

            // Tự động bổ sung các lá cờ mới từ jar vào heads.yml trên đĩa nếu chưa có
            try (java.io.InputStream in = plugin.getResource("heads.yml")) {
                if (in != null) {
                    YamlConfiguration defHeads = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                    ConfigurationSection defSec = defHeads.getConfigurationSection("heads");
                    if (defSec != null) {
                        boolean changed = false;
                        for (String k : defSec.getKeys(false)) {
                            if (!headsConfig.contains("heads." + k)) {
                                headsConfig.set("heads." + k, defSec.get(k));
                                changed = true;
                            }
                        }
                        if (changed) {
                            headsConfig.save(headsFile);
                            plugin.getLogger().info("Đã tự động bổ sung danh sách lá cờ các nước vào heads.yml!");
                        }
                    }
                }
            }

            // Tự động bổ sung các sprite mới (như ender_pearl) và alias vào sprites.yml trên đĩa nếu chưa có
            try (java.io.InputStream in = plugin.getResource("sprites.yml")) {
                if (in != null) {
                    YamlConfiguration defSprites = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                    ConfigurationSection defSec = defSprites.getConfigurationSection("sprites");
                    if (defSec != null) {
                        boolean changed = false;
                        for (String k : defSec.getKeys(false)) {
                            if (!spritesConfig.contains("sprites." + k)) {
                                spritesConfig.set("sprites." + k, defSec.get(k));
                                changed = true;
                            }
                        }
                        if (changed) {
                            spritesConfig.save(spritesFile);
                            plugin.getLogger().info("Đã tự động cập nhật và bổ sung các sprite mới vào sprites.yml!");
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Lỗi khi kiểm tra cập nhật cấu hình: " + e.getMessage());
        }
    }

    private void parseHeads() {
        headsMap.clear();
        ConfigurationSection section = headsConfig.getConfigurationSection("heads");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String name = section.getString(key + ".name", key);
            String uuidStr = section.getString(key + ".uuid", null);
            UUID uuid = uuidStr != null ? UUID.fromString(uuidStr) : UUID.randomUUID();
            String textureUrl = section.getString(key + ".texture_url", null);
            String value = section.getString(key + ".value", null);
            String displayName = section.getString(key + ".display_name", "<gold>[" + name + "]</gold>");
            String description = section.getString(key + ".description", "");
            String category = section.getString(key + ".category", "miscellaneous");
            String permission = section.getString(key + ".permission", "spriteschat.use.heads");
            String fallbackSprite = section.getString(key + ".fallback_sprite", null);

            HeadItem headItem = new HeadItem(key, name, uuid, textureUrl, value, displayName, description, category, permission, fallbackSprite);
            headsMap.put(key.toLowerCase(Locale.ROOT), headItem);

            // Nạp các alias (tên viết tắt như :vn:, :vietnam:)
            List<String> aliases = section.getStringList(key + ".aliases");
            for (String alias : aliases) {
                headsMap.put(alias.toLowerCase(Locale.ROOT), headItem);
            }
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public boolean isEnableHover() {
        return enableHover;
    }

    public boolean isEnableClickSuggest() {
        return enableClickSuggest;
    }

    public String getClickAction() {
        return clickAction;
    }

    public boolean isPlayerHeadsEnabled() {
        return playerHeadsEnabled;
    }

    public String getPlayerHeadsMode() {
        return playerHeadsMode;
    }

    public String getPlayerHeadsPermission() {
        return playerHeadsPermission;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getCacheDirPath() {
        return cacheDirPath;
    }

    public boolean isAutoFetch() {
        return autoFetch;
    }

    public Map<String, SpriteItem> getSpritesMap() {
        return spritesMap;
    }

    public Map<String, HeadItem> getHeadsMap() {
        return headsMap;
    }

    public boolean isSuggestionsEnabled() {
        return suggestionsEnabled;
    }

    public boolean isCustomCompletionsEnabled() {
        return customCompletionsEnabled;
    }

    public boolean isTabCompleteEnabled() {
        return tabCompleteEnabled;
    }

    public int getMaxTabCompletions() {
        return maxTabCompletions;
    }

    public String getTabHeadFormat() {
        return tabHeadFormat;
    }

    public boolean isTabFallbackToSprite() {
        return tabFallbackToSprite;
    }

    public boolean isLegacyCompatibilityEnabled() {
        return legacyCompatibilityEnabled;
    }

    public String getLegacyFormat() {
        return legacyFormat;
    }

    public int getMinProtocolVersion() {
        return minProtocolVersion;
    }

    public boolean isLegacyApplyToPlaceholders() {
        return legacyApplyToPlaceholders;
    }
}
