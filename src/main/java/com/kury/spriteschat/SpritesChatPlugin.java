package com.kury.spriteschat;

import com.kury.spriteschat.api.MinecraftHeadsService;
import com.kury.spriteschat.command.SpritesCommand;
import com.kury.spriteschat.config.ConfigManager;
import com.kury.spriteschat.listener.ChatListener;
import com.kury.spriteschat.manager.SpriteManager;
import com.kury.spriteschat.util.CrackedPlayerUtil;
import com.kury.spriteschat.util.FoliaSchedulerUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public class SpritesChatPlugin extends JavaPlugin {

    private static SpritesChatPlugin instance;

    private ConfigManager configManager;
    private MinecraftHeadsService headsService;
    private SpriteManager spriteManager;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("==================================================");
        getLogger().info("  SpritesChat v" + getDescription().getVersion() + " (Folia 1.21.11)");
        getLogger().info("  Chế độ: NATIVE 1.21 OBJECT COMPONENT");
        getLogger().info("  Server đa luồng Folia: " + (FoliaSchedulerUtil.isFolia() ? "CÓ [Đã kích hoạt]" : "KHÔNG (Paper/Spigot)"));
        getLogger().info("==================================================");

        // 1. Nạp cấu hình
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfigurations();

        // 2. Khởi tạo MinecraftHeadsService và Cache Skins
        File cacheDir = new File(getDataFolder(), configManager.getCacheDirPath());
        CrackedPlayerUtil.setCacheDirectory(cacheDir);
        this.headsService = new MinecraftHeadsService(
                this,
                configManager.getApiUrl(),
                configManager.getUserAgent(),
                cacheDir
        );

        // 3. Khởi tạo SpriteManager
        this.spriteManager = new SpriteManager(this, configManager, headsService);
        this.spriteManager.reload();

        // 4. Đăng ký Listener chat
        getServer().getPluginManager().registerEvents(new ChatListener(this, configManager, spriteManager), this);

        // 5. Đăng ký Command (hỗ trợ cả Bukkit PluginCommand cho PlugMan lẫn Paper BasicCommand)
        SpritesCommand spritesCommand = new SpritesCommand(this, configManager, spriteManager, headsService);
        org.bukkit.command.PluginCommand pluginCmd = getCommand("sprites");
        if (pluginCmd != null) {
            pluginCmd.setExecutor(spritesCommand);
            pluginCmd.setTabCompleter(spritesCommand);
        } else {
            registerCommand(
                    "sprites",
                    "Lệnh chính của SpritesChat",
                    List.of("sprite", "schat", "sc"),
                    spritesCommand
            );
        }

        // 6. Đồng bộ gợi ý chat cho người chơi đang online (nếu server reload)
        syncAllPlayerCompletions();

        // 7. Đăng ký Hook PlaceholderAPI và TAB
        registerHooks();

        getLogger().info("Plugin SpritesChat đã sẵn sàng hoạt động!");
    }

    private void registerHooks() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
                Class<?> expansionClass = Class.forName("com.kury.spriteschat.hook.SpritesChatExpansion");
                Object expansion = expansionClass.getConstructor(SpritesChatPlugin.class, SpriteManager.class)
                        .newInstance(this, spriteManager);
                expansionClass.getMethod("register").invoke(expansion);
                getLogger().info("Đã đăng ký thành công PlaceholderAPI Expansion (%spriteschat_...%)!");
            } catch (Throwable t) {
                getLogger().warning("Không thể kích hoạt PlaceholderAPI Hook: " + t.getMessage());
            }
        }
        if (getServer().getPluginManager().isPluginEnabled("TAB")) {
            try {
                com.kury.spriteschat.hook.TabHook.register(this, spriteManager);
            } catch (Throwable t) {
                getLogger().warning("Không thể kích hoạt TAB Hook: " + t.getMessage());
            }
        }
    }

    /**
     * Đồng bộ danh sách gợi ý Chat Completions cho tất cả người chơi đang online trên server.
     * Chạy an toàn đa luồng trên EntityScheduler của từng Player trên Folia.
     */
    public void syncAllPlayerCompletions() {
        if (configManager == null || !configManager.isSuggestionsEnabled() || !configManager.isCustomCompletionsEnabled()) {
            return;
        }
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            FoliaSchedulerUtil.runEntity(this, player, () -> {
                if (player.isOnline()) {
                    java.util.Collection<String> tokens = spriteManager.getAllEmoteTokens(player);
                    player.setCustomChatCompletions(tokens);
                }
            }, 1L);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("SpritesChat đang tắt và dọn dẹp bộ nhớ...");
        instance = null;
    }

    public static SpritesChatPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MinecraftHeadsService getHeadsService() {
        return headsService;
    }

    public SpriteManager getSpriteManager() {
        return spriteManager;
    }
}
