package com.kury.spriteschat.hook;

import com.kury.spriteschat.SpritesChatPlugin;
import com.kury.spriteschat.manager.SpriteManager;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI Expansion cho SpritesChat.
 * Cung cấp các placeholder hỗ trợ đưa icon vào TAB, Scoreboard và Rank của LuckPerms:
 * - %spriteschat_prefix% : Lấy rank prefix từ LuckPerms và tự động chuyển icon thành mã TAB
 * - %spriteschat_suffix% : Lấy rank suffix từ LuckPerms và chuyển icon thành mã TAB
 * - %spriteschat_parse_<text>% : Chuyển đổi bất kỳ văn bản/placeholder nào có icon :token: sang mã TAB
 * - %spriteschat_<token>% : Lấy trực tiếp mã icon (ví dụ %spriteschat_diamond%, %spriteschat_vn%)
 * - %spriteschat_player% : Lấy đầu người chơi hiện tại (<head:%player%>)
 * - %spriteschat_player_<tên>% : Lấy đầu người chơi theo tên (<head:name:Tên>)
 */
public class SpritesChatExpansion extends PlaceholderExpansion {

    private final SpritesChatPlugin plugin;
    private final SpriteManager spriteManager;

    public SpritesChatExpansion(SpritesChatPlugin plugin, SpriteManager spriteManager) {
        this.plugin = plugin;
        this.spriteManager = spriteManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "spriteschat";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Kury";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        Player player = (offlinePlayer != null && offlinePlayer.isOnline()) ? offlinePlayer.getPlayer() : null;

        // 1. Rank Prefix / Suffix từ LuckPerms đã parse sang TAB syntax
        if ("prefix".equalsIgnoreCase(params) || "lp_prefix".equalsIgnoreCase(params) || "rank".equalsIgnoreCase(params)) {
            return LuckPermsHook.getTabPrefix(player, spriteManager);
        }
        if ("suffix".equalsIgnoreCase(params) || "lp_suffix".equalsIgnoreCase(params)) {
            return LuckPermsHook.getTabSuffix(player, spriteManager);
        }

        // 2. Chuyển đổi chuỗi tùy ý (%spriteschat_parse_<văn bản>%)
        if (params.toLowerCase().startsWith("parse_")) {
            String toParse = params.substring(6);
            // Giải quyết placeholder lồng nhau (nested) nếu có
            if (toParse.contains("%")) {
                toParse = PlaceholderAPI.setPlaceholders(player, toParse);
            }
            return spriteManager.convertToTabString(toParse, player);
        }

        // 3. Đầu người chơi (<head:%player%> hoặc <head:Kury>)
        if ("player".equalsIgnoreCase(params) || "head".equalsIgnoreCase(params)) {
            return (player != null) ? "<head:" + player.getName() + ">" : "<head:%player%>";
        }

        // 4. Lấy riêng Atlas Sprite cho Scoreboard (%spriteschat_sprite_vn%, %spriteschat_sprite_discord%)
        if (params.toLowerCase().startsWith("sprite_")) {
            String target = params.substring(7);
            String spriteTag = spriteManager.getTabSpriteFallback(target, player);
            if (spriteTag != null) {
                return spriteTag;
            }
        }

        // 5. Thẻ Player Head theo tên hoặc custom head (%spriteschat_player_Notch%, %spriteschat_head_Kury%)
        if (params.toLowerCase().startsWith("player_")) {
            String targetName = params.substring(7);
            return "<head:" + targetName + ">";
        }
        if (params.toLowerCase().startsWith("head_")) {
            String targetName = params.substring(5);
            String headTag = spriteManager.getTabTokenString(targetName, player);
            if (headTag != null) {
                return headTag;
            }
            return "<head:" + targetName + ">";
        }

        // 6. Lấy thẻ hiển thị theo định dạng TAB (%spriteschat_tab_vn%, %spriteschat_tab_discord%)
        if (params.toLowerCase().startsWith("tab_")) {
            String target = params.substring(4);
            return spriteManager.getTabTokenString(target, player);
        }

        // 7. Lấy trực tiếp icon theo tên token (ví dụ: %spriteschat_diamond%, %spriteschat_vn%, %spriteschat_VietNam%)
        String tabTag = spriteManager.getTabTokenString(params, player);
        if (tabTag != null) {
            return tabTag;
        }

        return null;
    }
}