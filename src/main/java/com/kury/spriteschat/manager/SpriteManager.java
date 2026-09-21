package com.kury.spriteschat.manager;

import com.kury.spriteschat.api.MinecraftHeadsService;
import com.kury.spriteschat.config.ConfigManager;
import com.kury.spriteschat.model.HeadItem;
import com.kury.spriteschat.model.SpriteItem;
import com.kury.spriteschat.util.ClientPlatformUtil;
import com.kury.spriteschat.util.CrackedPlayerUtil;
import com.kury.spriteschat.util.FoliaSchedulerUtil;
import com.kury.spriteschat.util.IconCatalog;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import net.kyori.adventure.text.object.SpriteObjectContents;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quản lý tra cứu và thay thế sprite/head trong tin nhắn chat Adventure (Native 1.21 ObjectComponent).
 */
public class SpriteManager {

    // Hỗ trợ cả cú pháp :token: lẫn :head:player: hoặc :player:player: hoặc :block/spawner:
    private static final Pattern SPRITE_PATTERN = Pattern.compile(":([a-zA-Z0-9_/-]+(?::[a-zA-Z0-9_/-]+)?):");

    // Hỗ trợ khôi phục các sprite bị làm phẳng (flattened) bởi Canvas / chat formatters: [block/spawner], [item/diamond], [item/diamond@items], [block/tnt_side@blocks]
    private static final Pattern FLATTENED_SPRITE_PATTERN = Pattern.compile("\\[([a-zA-Z0-9_/-]+@[a-zA-Z0-9_]+|(?:block|item)/[a-zA-Z0-9_/-]+)\\]");

    // Hỗ trợ khôi phục các player heads bị làm phẳng: [Notch head], [VietNam head], hoặc [unknown player head]
    private static final Pattern FLATTENED_HEAD_PATTERN = Pattern.compile("\\[([a-zA-Z0-9_]+|unknown player)\\s+head\\]", Pattern.CASE_INSENSITIVE);

    // Hỗ trợ chuyển đổi thẻ MiniMessage sprite chưa parse: <sprite:'minecraft:items':'minecraft:item/diamond'>
    private static final Pattern MINIMESSAGE_SPRITE_PATTERN = Pattern.compile("<sprite:['\"]?([a-zA-Z0-9_:]+)['\"]?:['\"]?([a-zA-Z0-9_/:]+)['\"]?>");

    // Hỗ trợ khôi phục thẻ <head_texture:HASH> bị lọt vào chat từ prefix hoặc TAB
    private static final Pattern HEAD_TEXTURE_PATTERN = Pattern.compile("<head_texture:([a-zA-Z0-9]+)>");

    // Hỗ trợ khôi phục thẻ <head:NAME> hoặc <head:name:NAME> hoặc <head:uuid:UUID>
    private static final Pattern HEAD_TAG_PATTERN = Pattern.compile("<head:(?:name:|uuid:)?([a-zA-Z0-9_-]+)>");

    // Bảng từ viết tắt / tóm tắt thông dụng cho icon sprites (ví dụ :pearl:, :crystal:, :gap:, :totem:)
    public static final Map<String, String> SHORTHANDS = new HashMap<>();
    static {
        SHORTHANDS.put("pearl", "ender_pearl");
        SHORTHANDS.put("ngoc_ender", "ender_pearl");
        SHORTHANDS.put("ngocender", "ender_pearl");
        SHORTHANDS.put("crystal", "end_crystal");
        SHORTHANDS.put("totem", "totem_of_undying");
        SHORTHANDS.put("gap", "golden_apple");
        SHORTHANDS.put("gapple", "golden_apple");
        SHORTHANDS.put("taovang", "golden_apple");
        SHORTHANDS.put("tao_vang", "golden_apple");
        SHORTHANDS.put("egap", "enchanted_golden_apple");
        SHORTHANDS.put("notch", "enchanted_golden_apple");
        SHORTHANDS.put("notch_apple", "enchanted_golden_apple");
        SHORTHANDS.put("taonotch", "enchanted_golden_apple");
        SHORTHANDS.put("sword", "netherite_sword");
        SHORTHANDS.put("kiem", "netherite_sword");
        SHORTHANDS.put("diasword", "diamond_sword");
        SHORTHANDS.put("kiemkc", "diamond_sword");
        SHORTHANDS.put("pick", "netherite_pickaxe");
        SHORTHANDS.put("pickaxe", "netherite_pickaxe");
        SHORTHANDS.put("cup", "netherite_pickaxe");
        SHORTHANDS.put("diapick", "diamond_pickaxe");
        SHORTHANDS.put("diapickaxe", "diamond_pickaxe");
        SHORTHANDS.put("cupkc", "diamond_pickaxe");
        SHORTHANDS.put("axe", "netherite_axe");
        SHORTHANDS.put("riu", "netherite_axe");
        SHORTHANDS.put("diaaxe", "diamond_axe");
        SHORTHANDS.put("shovel", "netherite_shovel");
        SHORTHANDS.put("xeng", "netherite_shovel");
        SHORTHANDS.put("hoe", "netherite_hoe");
        SHORTHANDS.put("cuoc", "netherite_hoe");
        SHORTHANDS.put("helmet", "netherite_helmet");
        SHORTHANDS.put("chestplate", "netherite_chestplate");
        SHORTHANDS.put("leggings", "netherite_leggings");
        SHORTHANDS.put("boots", "netherite_boots");
        SHORTHANDS.put("mace", "mace");
        SHORTHANDS.put("chuy", "mace");
        SHORTHANDS.put("wind", "wind_charge");
        SHORTHANDS.put("charge", "wind_charge");
        SHORTHANDS.put("core", "heavy_core");
        SHORTHANDS.put("breeze", "breeze_rod");
        SHORTHANDS.put("blaze", "blaze_rod");
        SHORTHANDS.put("rod", "fishing_rod");
        SHORTHANDS.put("fishrod", "fishing_rod");
        SHORTHANDS.put("bow", "bow");
        SHORTHANDS.put("cung", "bow");
        SHORTHANDS.put("crossbow", "crossbow");
        SHORTHANDS.put("no", "crossbow");
        SHORTHANDS.put("trident", "trident");
        SHORTHANDS.put("dinhba", "trident");
        SHORTHANDS.put("shield", "shield");
        SHORTHANDS.put("khien", "shield");
        SHORTHANDS.put("elytra", "elytra");
        SHORTHANDS.put("canh", "elytra");
        SHORTHANDS.put("rocket", "firework_rocket");
        SHORTHANDS.put("firework", "firework_rocket");
        SHORTHANDS.put("phaohoa", "firework_rocket");
        SHORTHANDS.put("dia", "diamond");
        SHORTHANDS.put("kc", "diamond");
        SHORTHANDS.put("em", "emerald");
        SHORTHANDS.put("gold", "gold_ingot");
        SHORTHANDS.put("iron", "iron_ingot");
        SHORTHANDS.put("sat", "iron_ingot");
        SHORTHANDS.put("copper", "copper_ingot");
        SHORTHANDS.put("netherite", "netherite_ingot");
        SHORTHANDS.put("xp", "experience_bottle");
        SHORTHANDS.put("exp", "experience_bottle");
        SHORTHANDS.put("chaixp", "experience_bottle");
        SHORTHANDS.put("chorus", "chorus_fruit");
        SHORTHANDS.put("star", "nether_star");
        SHORTHANDS.put("beacon", "beacon");
        SHORTHANDS.put("tnt", "tnt");
        SHORTHANDS.put("anchor", "respawn_anchor");
        SHORTHANDS.put("web", "cobweb");
        SHORTHANDS.put("key", "trial_key");
        SHORTHANDS.put("ominous_key", "ominous_trial_key");
        SHORTHANDS.put("ominous", "ominous_bottle");
        SHORTHANDS.put("bad_omen", "ominous_bottle");
        SHORTHANDS.put("shulker", "shulker_box");
        SHORTHANDS.put("box", "shulker_box");
        SHORTHANDS.put("apple", "apple");
        SHORTHANDS.put("bread", "bread");
        SHORTHANDS.put("pot", "potion");
        SHORTHANDS.put("splash", "splash_potion");
        SHORTHANDS.put("lingering", "lingering_potion");
        SHORTHANDS.put("dc", "discord");
        SHORTHANDS.put("disc", "discord");
    }

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final MinecraftHeadsService headsService;

    private final Map<String, SpriteItem> sprites = new ConcurrentHashMap<>();
    private final Map<String, HeadItem> heads = new ConcurrentHashMap<>();
    private final Map<String, HeadItem> headsByTextureHash = new ConcurrentHashMap<>();

    public SpriteManager(Plugin plugin, ConfigManager configManager, MinecraftHeadsService headsService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.headsService = headsService;
    }

    /**
     * Tải và nạp toàn bộ sprite & head vào bộ nhớ.
     */
    public void reload() {
        sprites.clear();
        heads.clear();
        headsByTextureHash.clear();

        // 1. Nạp sprites từ config
        sprites.putAll(configManager.getSpritesMap());

        // 2. Nạp heads từ config (bao gồm cả aliases và tra cứu hash texture)
        for (Map.Entry<String, HeadItem> entry : configManager.getHeadsMap().entrySet()) {
            HeadItem head = entry.getValue();
            heads.put(entry.getKey().toLowerCase(Locale.ROOT), head);
            String url = head.getTextureUrl();
            if (url != null && url.contains("/texture/")) {
                String hash = url.substring(url.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                headsByTextureHash.put(hash, head);
            }
        }

        plugin.getLogger().info("Đã nạp " + sprites.size() + " sprites và " + heads.size() + " player heads (" + headsByTextureHash.size() + " texture hashes).");
    }

    /**
     * Định dạng chuỗi văn bản cho Bedrock và client Java < 1.21.6 theo cấu hình legacy-compatibility.format.
     * Mặc định là ":{item}:" (ví dụ: :diamond:, :vn:, :Notch:).
     */
    public String formatLegacyItemString(String key) {
        String template = configManager.getLegacyFormat();
        if (template == null || template.isEmpty()) {
            template = ":{item}:";
        }
        if (template.contains("{item}")) {
            return template.replace("{item}", key);
        } else if (template.contains("{key}")) {
            return template.replace("{key}", key);
        } else if (template.contains("{name}")) {
            return template.replace("{name}", key);
        } else if (":item:".equals(template)) {
            return ":" + key + ":";
        } else {
            return ":" + key + ":";
        }
    }

    /**
     * Tạo Component định dạng cho Bedrock và client Java < 1.21.6 theo cấu hình legacy-compatibility.format.
     * Mặc định là ":{item}:" (ví dụ: :diamond:, :vn:, :Notch:).
     */
    public Component formatLegacyItem(String key, String displayName, boolean hover, boolean click, String clickAction) {
        String text = formatLegacyItemString(key);

        Component comp;
        try {
            comp = MiniMessage.miniMessage().deserialize(text);
        } catch (Throwable t) {
            comp = Component.text(text);
        }

        if (hover && displayName != null && !displayName.isEmpty()) {
            try {
                Component hoverComp = MiniMessage.miniMessage().deserialize(displayName);
                comp = comp.hoverEvent(HoverEvent.showText(hoverComp));
            } catch (Throwable t) {
                comp = comp.hoverEvent(HoverEvent.showText(Component.text(displayName)));
            }
        }

        if (click) {
            if ("COPY_TO_CLIPBOARD".equalsIgnoreCase(clickAction)) {
                comp = comp.clickEvent(ClickEvent.copyToClipboard(":" + key + ":"));
            } else {
                comp = comp.clickEvent(ClickEvent.suggestCommand(":" + key + ":"));
            }
        }

        return comp;
    }

    /**
     * Chuyển đổi toàn bộ ObjectComponent trong cây Component thành định dạng Legacy ':{item}:'
     * phục vụ cho các client Bedrock và client Java < 1.21.6.
     */
    public Component convertObjectComponentsToLegacy(Component comp, boolean hover, boolean click, String clickAction) {
        if (comp == null) return null;

        Component result = comp;
        if (comp instanceof ObjectComponent obj) {
            String itemKey = null;
            String displayName = null;
            if (obj.contents() instanceof SpriteObjectContents sprite) {
                String path = sprite.sprite().value();
                if (path.startsWith("item/")) path = path.substring(5);
                else if (path.startsWith("block/")) path = path.substring(6);
                itemKey = path;
                displayName = "[" + itemKey + "]";
            } else if (obj.contents() instanceof PlayerHeadObjectContents head) {
                itemKey = head.name();
                if (itemKey == null || itemKey.isEmpty()) {
                    itemKey = "player";
                }
                displayName = "[" + itemKey + "]";
            }
            if (itemKey != null) {
                result = formatLegacyItem(itemKey, displayName, hover, click, clickAction);
            }
        }

        List<Component> children = result.children();
        if (!children.isEmpty()) {
            List<Component> newChildren = new ArrayList<>(children.size());
            for (Component child : children) {
                newChildren.add(convertObjectComponentsToLegacy(child, hover, click, clickAction));
            }
            result = result.children(newChildren);
        }

        return result;
    }

    /**
     * Xử lý và thay thế các placeholder :token: trong tin nhắn chat của người chơi (dành cho client mặc định).
     */
    public Component processMessage(Component original, Player player) {
        return processMessage(original, player, null);
    }

    /**
     * Xử lý và thay thế các placeholder :token: trong tin nhắn chat theo từng đối tượng người xem (viewer):
     * - Client Java >= 1.21.6: Hiển thị Native ObjectComponent 1.21
     * - Client Bedrock (Geyser) & Java < 1.21.6: Hiển thị định dạng legacy :item:
     */
    public Component processMessage(Component original, Player player, Audience viewer) {
        boolean hover = configManager.isEnableHover();
        boolean click = configManager.isEnableClickSuggest();
        String clickAction = configManager.getClickAction();

        boolean isLegacy = configManager.isLegacyCompatibilityEnabled()
                && ClientPlatformUtil.isLegacyOrBedrock(viewer, configManager.getMinProtocolVersion());

        Component sourceComp = isLegacy ? convertObjectComponentsToLegacy(original, hover, click, clickAction) : original;

        // 1. Quét và thay thế các placeholder :token: thông thường
        Component result = sourceComp.replaceText(TextReplacementConfig.builder()
                .match(SPRITE_PATTERN)
                .replacement((matchResult, builder) -> {
                    String fullMatch = matchResult.group(0); // ví dụ ":end_crystal:" hoặc ":VietNam:" hoặc ":pearl:"
                    String rawKey = matchResult.group(1);    // ví dụ "end_crystal", "VietNam", "pearl", "head:Kury"
                    String lowerKey = rawKey.toLowerCase(Locale.ROOT);

                    // Kiểm tra xem có tiền tố head: hoặc player: không
                    String targetPlayerName = null;
                    if (lowerKey.startsWith("head:") || lowerKey.startsWith("player:")) {
                        targetPlayerName = rawKey.substring(rawKey.indexOf(':') + 1);
                    }

                    // 1. Kiểm tra Sprite thông thường (như :end_crystal:, :ender_pearl:)
                    SpriteItem sprite = sprites.get(lowerKey);
                    if (sprite != null) {
                        if (player == null || player.hasPermission(sprite.getPermission())) {
                            if (isLegacy) {
                                return formatLegacyItem(sprite.getKey(), sprite.getDisplayName(), hover, click, clickAction);
                            }
                            return sprite.toComponent(hover, click, clickAction);
                        } else {
                            return builder.content(fullMatch);
                        }
                    }

                    // 2. Kiểm tra Player Head cấu hình trước (như :VietNam:, :vn:, :japan:, :usa:)
                    HeadItem head = heads.get(lowerKey);
                    if (head != null) {
                        if (player == null || player.hasPermission(head.getPermission())) {
                            if (isLegacy) {
                                return formatLegacyItem(head.getKey(), head.getDisplayName(), hover, click, clickAction);
                            }
                            return head.toComponent(hover, click, clickAction);
                        } else {
                            return builder.content(fullMatch);
                        }
                    }

                    // 3. Kiểm tra từ viết tắt / tóm tắt (Shorthand Aliases ví dụ :pearl: -> ender_pearl, :crystal: -> end_crystal)
                    String shorthandTarget = SHORTHANDS.get(lowerKey);
                    if (shorthandTarget != null) {
                        SpriteItem targetSprite = sprites.get(shorthandTarget);
                        if (targetSprite != null) {
                            if (player == null || player.hasPermission(targetSprite.getPermission())) {
                                if (isLegacy) {
                                    return formatLegacyItem(lowerKey, targetSprite.getDisplayName(), hover, click, clickAction);
                                }
                                return targetSprite.toComponent(hover, click, clickAction);
                            } else {
                                return builder.content(fullMatch);
                            }
                        }
                        HeadItem targetHead = heads.get(shorthandTarget);
                        if (targetHead != null) {
                            if (player == null || player.hasPermission(targetHead.getPermission())) {
                                if (isLegacy) {
                                    return formatLegacyItem(lowerKey, targetHead.getDisplayName(), hover, click, clickAction);
                                }
                                return targetHead.toComponent(hover, click, clickAction);
                            } else {
                                return builder.content(fullMatch);
                            }
                        }
                        Material targetMat = Material.matchMaterial(shorthandTarget);
                        if (targetMat != null) {
                            if (player == null || player.hasPermission("spriteschat.use")) {
                                if (isLegacy) {
                                    return formatLegacyItem(lowerKey, IconCatalog.displayName(targetMat), hover, click, clickAction);
                                }
                                Component matSprite = IconCatalog.spriteFor(targetMat);
                                if (matSprite != null) {
                                    if (hover) {
                                        String display = IconCatalog.displayName(targetMat);
                                        Component hoverComp = MiniMessage.miniMessage().deserialize(
                                                "<light_purple><b>[" + display + "]</b></light_purple>"
                                        );
                                        matSprite = matSprite.hoverEvent(HoverEvent.showText(hoverComp));
                                    }
                                    if (click) {
                                        if ("COPY_TO_CLIPBOARD".equalsIgnoreCase(clickAction)) {
                                            matSprite = matSprite.clickEvent(ClickEvent.copyToClipboard(":" + lowerKey + ":"));
                                        } else {
                                            matSprite = matSprite.clickEvent(ClickEvent.suggestCommand(":" + lowerKey + ":"));
                                        }
                                    }
                                    return matSprite;
                                }
                            } else {
                                return builder.content(fullMatch);
                            }
                        }
                    }

                    // 4. Kiểm tra Material chuẩn của Minecraft tự động qua IconCatalog (như :diamond_sword:, :netherite_sword:)
                    Material mat = Material.matchMaterial(lowerKey);
                    if (mat != null) {
                        if (player == null || player.hasPermission("spriteschat.use")) {
                            if (isLegacy) {
                                return formatLegacyItem(lowerKey, IconCatalog.displayName(mat), hover, click, clickAction);
                            }
                            Component matSprite = IconCatalog.spriteFor(mat);
                            if (matSprite != null) {
                                if (hover) {
                                    String display = IconCatalog.displayName(mat);
                                    Component hoverComp = MiniMessage.miniMessage().deserialize(
                                            "<light_purple><b>[" + display + "]</b></light_purple>"
                                    );
                                    matSprite = matSprite.hoverEvent(HoverEvent.showText(hoverComp));
                                }
                                if (click) {
                                    if ("COPY_TO_CLIPBOARD".equalsIgnoreCase(clickAction)) {
                                        matSprite = matSprite.clickEvent(ClickEvent.copyToClipboard(":" + lowerKey + ":"));
                                    } else {
                                        matSprite = matSprite.clickEvent(ClickEvent.suggestCommand(":" + lowerKey + ":"));
                                    }
                                }
                                return matSprite;
                            }
                        } else {
                            return builder.content(fullMatch);
                        }
                    }

                    // 5. Kiểm tra Player Head theo tên người chơi (:nameplayer: hoặc :head:nameplayer:)
                    if (configManager.isPlayerHeadsEnabled()) {
                        String candidateName = (targetPlayerName != null) ? targetPlayerName : rawKey;
                        if (candidateName.matches("^[a-zA-Z0-9_]{3,16}$")) {
                            Player onlinePlayer = findOnlinePlayer(candidateName);
                            boolean isAllMode = "ALL".equalsIgnoreCase(configManager.getPlayerHeadsMode());
                            if (onlinePlayer != null || isAllMode) {
                                String perm = configManager.getPlayerHeadsPermission();
                                if (player == null || player.hasPermission(perm) || player.hasPermission("spriteschat.use")) {
                                    if (isLegacy) {
                                        return formatLegacyItem(candidateName, "[" + candidateName + "]", hover, click, clickAction);
                                    }
                                    return resolvePlayerHead(candidateName, hover, click, clickAction);
                                } else {
                                    return builder.content(fullMatch);
                                }
                            }
                        }
                    }

                    // 6. Nếu chưa có và bật auto-fetch từ minecraft-heads.com (tiền tố head_xxx)
                    if (configManager.isAutoFetch() && lowerKey.startsWith("head_")) {
                        String searchName = lowerKey.substring(5);
                        triggerAsyncFetch(searchName, player);
                    }

                    return builder.content(fullMatch);
                })
                .build());

        // 2. Tự phục hồi các sprite bị Canvas hoặc chat formatter làm phẳng thành text dạng [item/diamond@items] hoặc [block/tnt_side@blocks]
        result = result.replaceText(TextReplacementConfig.builder()
                .match(FLATTENED_SPRITE_PATTERN)
                .replacement((matchResult, builder) -> {
                    String fullMatch = matchResult.group(0);
                    String raw = matchResult.group(1);
                    String path;
                    String atlas;
                    if (raw.contains("@")) {
                        path = raw.substring(0, raw.indexOf('@'));
                        atlas = raw.substring(raw.indexOf('@') + 1);
                    } else {
                        path = raw;
                        atlas = IconCatalog.resolveAtlas(net.kyori.adventure.key.Key.key("minecraft", path)).value();
                    }

                    String shortKey = path;
                    if (shortKey.contains(":")) {
                        shortKey = shortKey.substring(shortKey.indexOf(':') + 1);
                    }
                    if (shortKey.startsWith("item/")) {
                        shortKey = shortKey.substring(5);
                    } else if (shortKey.startsWith("block/")) {
                        shortKey = shortKey.substring(6);
                    }
                    String lowerKey = shortKey.toLowerCase(Locale.ROOT);

                    if (isLegacy) {
                        return formatLegacyItem(lowerKey, Character.toUpperCase(lowerKey.charAt(0)) + lowerKey.substring(1).replace('_', ' '), hover, click, clickAction);
                    }

                    SpriteItem sprite = sprites.get(lowerKey);
                    if (sprite != null) {
                        if (player == null || player.hasPermission(sprite.getPermission())) {
                            return sprite.toComponent(hover, click, clickAction);
                        } else {
                            return builder.content(fullMatch);
                        }
                    }

                    Material mat = Material.matchMaterial(lowerKey);
                    if (mat != null) {
                        Component matSprite = IconCatalog.spriteFor(mat);
                        if (matSprite != null) {
                            if (player == null || player.hasPermission("spriteschat.use")) {
                                if (hover) {
                                    String display = IconCatalog.displayName(mat);
                                    matSprite = matSprite.hoverEvent(HoverEvent.showText(MiniMessage.miniMessage().deserialize(
                                            "<light_purple><b>[" + display + "]</b></light_purple>"
                                    )));
                                }
                                if (click) {
                                    if ("COPY_TO_CLIPBOARD".equalsIgnoreCase(clickAction)) {
                                        matSprite = matSprite.clickEvent(ClickEvent.copyToClipboard(":" + lowerKey + ":"));
                                    } else {
                                        matSprite = matSprite.clickEvent(ClickEvent.suggestCommand(":" + lowerKey + ":"));
                                    }
                                }
                                return matSprite;
                            } else {
                                return builder.content(fullMatch);
                            }
                        }
                    }

                    if (player == null || player.hasPermission("spriteschat.use")) {
                        net.kyori.adventure.key.Key atlasKey = atlas.contains(":") ? net.kyori.adventure.key.Key.key(atlas) : net.kyori.adventure.key.Key.key("minecraft", atlas);
                        net.kyori.adventure.key.Key spriteKey = path.contains(":") ? net.kyori.adventure.key.Key.key(path) : net.kyori.adventure.key.Key.key("minecraft", path);
                        Component comp = Component.object(ObjectContents.sprite(atlasKey, spriteKey));
                        if (hover) {
                            String display = Character.toUpperCase(lowerKey.charAt(0)) + lowerKey.substring(1).replace('_', ' ');
                            comp = comp.hoverEvent(HoverEvent.showText(MiniMessage.miniMessage().deserialize(
                                    "<light_purple><b>[" + display + "]</b></light_purple>"
                            )));
                        }
                        if (click) {
                            if ("COPY_TO_CLIPBOARD".equalsIgnoreCase(clickAction)) {
                                comp = comp.clickEvent(ClickEvent.copyToClipboard(":" + lowerKey + ":"));
                            } else {
                                comp = comp.clickEvent(ClickEvent.suggestCommand(":" + lowerKey + ":"));
                            }
                        }
                        return comp;
                    }
                    return builder.content(fullMatch);
                })
                .build());

        // 3. Tự phục hồi player heads bị làm phẳng: [Notch head], [VietNam head] hoặc [unknown player head]
        result = result.replaceText(TextReplacementConfig.builder()
                .match(FLATTENED_HEAD_PATTERN)
                .replacement((matchResult, builder) -> {
                    String fullMatch = matchResult.group(0);
                    String headName = matchResult.group(1);
                    String lowerKey = headName.toLowerCase(Locale.ROOT);

                    if (isLegacy) {
                        return formatLegacyItem(headName, "[" + headName + "]", hover, click, clickAction);
                    }

                    // Phục hồi [unknown player head] thành head của người chơi đang chat hoặc Steve
                    if ("unknown player".equalsIgnoreCase(headName)) {
                        if (player != null) {
                            return resolvePlayerHead(player.getName(), hover, click, clickAction);
                        } else {
                            return CrackedPlayerUtil.buildCrackedPlayerHead("Steve", hover, click, clickAction);
                        }
                    }

                    HeadItem head = heads.get(lowerKey);
                    if (head != null) {
                        if (player == null || player.hasPermission(head.getPermission())) {
                            return head.toComponent(hover, click, clickAction);
                        } else {
                            return builder.content(fullMatch);
                        }
                    }

                    if (configManager.isPlayerHeadsEnabled() && headName.matches("^[a-zA-Z0-9_]{3,16}$")) {
                        String perm = configManager.getPlayerHeadsPermission();
                        if (player == null || player.hasPermission(perm) || player.hasPermission("spriteschat.use")) {
                            return resolvePlayerHead(headName, hover, click, clickAction);
                        }
                    }
                    return builder.content(fullMatch);
                })
                .build());

        // 4. Chuyển đổi thẻ MiniMessage sprite chưa parse dạng <sprite:'minecraft:items':'minecraft:item/diamond'>
        result = result.replaceText(TextReplacementConfig.builder()
                .match(MINIMESSAGE_SPRITE_PATTERN)
                .replacement((matchResult, builder) -> {
                    String fullMatch = matchResult.group(0);
                    String atlasRaw = matchResult.group(1).replace("'", "").replace("\"", "");
                    String pathRaw = matchResult.group(2).replace("'", "").replace("\"", "");

                    String shortKey = pathRaw;
                    if (shortKey.contains(":")) {
                        shortKey = shortKey.substring(shortKey.indexOf(':') + 1);
                    }
                    if (shortKey.startsWith("item/")) {
                        shortKey = shortKey.substring(5);
                    } else if (shortKey.startsWith("block/")) {
                        shortKey = shortKey.substring(6);
                    }
                    String lowerKey = shortKey.toLowerCase(Locale.ROOT);

                    if (isLegacy) {
                        return formatLegacyItem(lowerKey, Character.toUpperCase(lowerKey.charAt(0)) + lowerKey.substring(1).replace('_', ' '), hover, click, clickAction);
                    }

                    SpriteItem sprite = sprites.get(lowerKey);
                    if (sprite != null) {
                        if (player == null || player.hasPermission(sprite.getPermission())) {
                            return sprite.toComponent(hover, click, clickAction);
                        } else {
                            return builder.content(fullMatch);
                        }
                    }

                    if (player == null || player.hasPermission("spriteschat.use")) {
                        net.kyori.adventure.key.Key atlasKey = atlasRaw.contains(":") ? net.kyori.adventure.key.Key.key(atlasRaw) : net.kyori.adventure.key.Key.key("minecraft", atlasRaw);
                        net.kyori.adventure.key.Key spriteKey = pathRaw.contains(":") ? net.kyori.adventure.key.Key.key(pathRaw) : net.kyori.adventure.key.Key.key("minecraft", pathRaw);
                        Component comp = Component.object(ObjectContents.sprite(atlasKey, spriteKey));
                        if (hover) {
                            String display = Character.toUpperCase(lowerKey.charAt(0)) + lowerKey.substring(1).replace('_', ' ');
                            comp = comp.hoverEvent(HoverEvent.showText(MiniMessage.miniMessage().deserialize(
                                    "<light_purple><b>[" + display + "]</b></light_purple>"
                            )));
                        }
                        if (click) {
                            if ("COPY_TO_CLIPBOARD".equalsIgnoreCase(clickAction)) {
                                comp = comp.clickEvent(ClickEvent.copyToClipboard(":" + lowerKey + ":"));
                            } else {
                                comp = comp.clickEvent(ClickEvent.suggestCommand(":" + lowerKey + ":"));
                            }
                        }
                        return comp;
                    }
                    return builder.content(fullMatch);
                })
                .build());

        // 5. Chuyển đổi thẻ <head_texture:HASH> bị lọt vào chat từ TAB hoặc LuckPerms prefix
        result = result.replaceText(TextReplacementConfig.builder()
                .match(HEAD_TEXTURE_PATTERN)
                .replacement((matchResult, builder) -> {
                    String fullMatch = matchResult.group(0);
                    String hash = matchResult.group(1);
                    String lowerHash = hash.toLowerCase(Locale.ROOT);

                    HeadItem head = headsByTextureHash.get(lowerHash);
                    if (isLegacy) {
                        return formatLegacyItem(head != null ? head.getKey() : "head", "[head]", hover, click, clickAction);
                    }

                    if (head != null) {
                        if (player == null || player.hasPermission(head.getPermission())) {
                            return head.toComponent(hover, click, clickAction);
                        } else {
                            return builder.content(fullMatch);
                        }
                    }

                    // Nếu không có trong heads.yml, tự động tạo Native Player Head với texture hash Mojang
                    try {
                        String json = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + hash + "\"}}}";
                        String base64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
                        UUID headUuid = UUID.nameUUIDFromBytes(("HEAD_TEX_" + hash).getBytes(StandardCharsets.UTF_8));
                        PlayerHeadObjectContents.Builder headBuilder = ObjectContents.playerHead()
                                .hat(true)
                                .id(headUuid)
                                .profileProperty(PlayerHeadObjectContents.property("textures", base64));
                        Component comp = Component.object(headBuilder.build());
                        if (hover) {
                            comp = comp.hoverEvent(HoverEvent.showText(Component.text("[" + hash.substring(0, Math.min(8, hash.length())) + "]")));
                        }
                        return comp;
                    } catch (Throwable t) {
                        return builder.content(fullMatch);
                    }
                })
                .build());

        // 6. Chuyển đổi thẻ <head:NAME> hoặc <head:name:NAME> hoặc <head:uuid:UUID> bị lọt vào chat
        result = result.replaceText(TextReplacementConfig.builder()
                .match(HEAD_TAG_PATTERN)
                .replacement((matchResult, builder) -> {
                    String fullMatch = matchResult.group(0);
                    String name = matchResult.group(1);
                    String lowerName = name.toLowerCase(Locale.ROOT);

                    if (isLegacy) {
                        return formatLegacyItem(name, "[" + name + "]", hover, click, clickAction);
                    }

                    HeadItem head = heads.get(lowerName);
                    if (head != null) {
                        if (player == null || player.hasPermission(head.getPermission())) {
                            return head.toComponent(hover, click, clickAction);
                        } else {
                            return builder.content(fullMatch);
                        }
                    }

                    if (configManager.isPlayerHeadsEnabled() && name.matches("^[a-zA-Z0-9_]{3,16}$")) {
                        String perm = configManager.getPlayerHeadsPermission();
                        if (player == null || player.hasPermission(perm) || player.hasPermission("spriteschat.use")) {
                            return resolvePlayerHead(name, hover, click, clickAction);
                        }
                    }

                    return builder.content(fullMatch);
                })
                .build());

        if (isLegacy) {
            result = convertObjectComponentsToLegacy(result, hover, click, clickAction);
        }

        return result;
    }

    /**
     * Tạo Adventure Component hiển thị Native Player Head của Minecraft 1.21 theo tên người chơi.
     */
    public Component resolvePlayerHead(String playerName, boolean hover, boolean click, String clickAction) {
        Player onlinePlayer = findOnlinePlayer(playerName);
        String exactName = (onlinePlayer != null) ? onlinePlayer.getName() : playerName;

        return CrackedPlayerUtil.buildCrackedPlayerHead(exactName, hover, click, clickAction);
    }

    private Player findOnlinePlayer(String name) {
        try {
            Player exact = Bukkit.getPlayerExact(name);
            if (exact != null) return exact;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(name)) {
                    return p;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void triggerAsyncFetch(String searchName, Player player) {
        FoliaSchedulerUtil.runAsync(plugin, () -> {
            headsService.fetchHeadFromApi(searchName, "miscellaneous").thenAccept(newHead -> {
                if (newHead != null) {
                    heads.put(searchName.toLowerCase(Locale.ROOT), newHead);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(Component.text("§aĐã tải xong head §e:" + searchName + ":§a từ minecraft-heads.com!"));
                    }
                }
            });
        });
    }

    public void addHead(HeadItem head) {
        heads.put(head.getKey().toLowerCase(Locale.ROOT), head);
    }

    public SpriteItem getSprite(String key) {
        return sprites.get(key.toLowerCase(Locale.ROOT));
    }

    public HeadItem getHead(String key) {
        return heads.get(key.toLowerCase(Locale.ROOT));
    }

    public Collection<SpriteItem> getAllSprites() {
        return sprites.values();
    }

    public Collection<HeadItem> getAllHeads() {
        return heads.values();
    }

    /**
     * Tổng hợp tất cả các token emote dạng :token: mà người chơi có quyền sử dụng.
     * Dùng để gửi vào ClientboundCustomChatCompletionsPacket hoặc gợi ý Tab.
     */
    public Set<String> getAllEmoteTokens(Player player) {
        Set<String> tokens = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // 1. Sprites từ config (bao gồm cả aliases)
        for (Map.Entry<String, SpriteItem> entry : sprites.entrySet()) {
            if (player == null || player.hasPermission(entry.getValue().getPermission())) {
                tokens.add(":" + entry.getKey() + ":");
            }
        }

        // 2. Heads từ config (bao gồm cả aliases)
        for (Map.Entry<String, HeadItem> entry : heads.entrySet()) {
            if (player == null || player.hasPermission(entry.getValue().getPermission())) {
                tokens.add(":" + entry.getKey() + ":");
            }
        }

        // 3. Shorthand aliases (:pearl:, :crystal:, :gap:, :totem:, v.v.)
        if (player == null || player.hasPermission("spriteschat.use")) {
            for (String shorthand : SHORTHANDS.keySet()) {
                tokens.add(":" + shorthand + ":");
            }
        }

        // 4. Online players (:TênPlayer:)
        if (configManager.isPlayerHeadsEnabled()) {
            String perm = configManager.getPlayerHeadsPermission();
            if (player == null || player.hasPermission(perm) || player.hasPermission("spriteschat.use")) {
                try {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        tokens.add(":" + p.getName() + ":");
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 5. Native Material items từ IconCatalog
        if (player == null || player.hasPermission("spriteschat.use")) {
            for (Material mat : Material.values()) {
                if (mat.isLegacy() || mat.isAir()) continue;
                if (mat.isItem()) {
                    tokens.add(":" + mat.name().toLowerCase(Locale.ROOT) + ":");
                }
            }
        }

        return tokens;
    }

    /**
     * Lấy danh sách Completion khi người chơi gõ dấu ":" và nhấn Tab hoặc chờ gợi ý.
     */
    public List<AsyncTabCompleteEvent.Completion> getTabCompletions(CommandSender sender, String prefix) {
        if (prefix == null || !prefix.startsWith(":")) {
            return Collections.emptyList();
        }

        Player player = (sender instanceof Player p) ? p : null;
        Set<String> allTokens = getAllEmoteTokens(player);
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        int maxLimit = configManager.getMaxTabCompletions();
        if (maxLimit <= 0) maxLimit = 30;

        List<AsyncTabCompleteEvent.Completion> result = new ArrayList<>();
        for (String token : allTokens) {
            if (token.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                Component tooltip = resolveEmoteTooltip(token);
                if (tooltip != null) {
                    result.add(AsyncTabCompleteEvent.Completion.completion(token, tooltip));
                } else {
                    result.add(AsyncTabCompleteEvent.Completion.completion(token));
                }
                if (result.size() >= maxLimit) {
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Tạo tooltip hiển thị tên tiếng Việt hoặc tên hiển thị của emote trong danh sách gợi ý Tab.
     */
    public Component resolveEmoteTooltip(String token) {
        if (token == null || token.length() < 2 || !token.startsWith(":") || !token.endsWith(":")) {
            return null;
        }
        String key = token.substring(1, token.length() - 1);
        String lowerKey = key.toLowerCase(Locale.ROOT);

        // 1. Kiểm tra Sprite
        SpriteItem sprite = sprites.get(lowerKey);
        if (sprite != null) {
            try {
                return MiniMessage.miniMessage().deserialize(sprite.getDisplayName());
            } catch (Throwable ignored) {
                return Component.text(sprite.getDisplayName());
            }
        }

        // 2. Kiểm tra Head
        HeadItem head = heads.get(lowerKey);
        if (head != null) {
            try {
                return MiniMessage.miniMessage().deserialize(head.getDisplayName());
            } catch (Throwable ignored) {
                return Component.text(head.getDisplayName());
            }
        }

        // 3. Kiểm tra Shorthand
        String shorthandTarget = SHORTHANDS.get(lowerKey);
        if (shorthandTarget != null) {
            SpriteItem targetSprite = sprites.get(shorthandTarget);
            if (targetSprite != null) {
                try {
                    return MiniMessage.miniMessage().deserialize(targetSprite.getDisplayName());
                } catch (Throwable ignored) {
                    return Component.text(targetSprite.getDisplayName());
                }
            }
            HeadItem targetHead = heads.get(shorthandTarget);
            if (targetHead != null) {
                try {
                    return MiniMessage.miniMessage().deserialize(targetHead.getDisplayName());
                } catch (Throwable ignored) {
                    return Component.text(targetHead.getDisplayName());
                }
            }
            Material targetMat = Material.matchMaterial(shorthandTarget);
            if (targetMat != null) {
                return Component.text("[" + IconCatalog.displayName(targetMat) + "]");
            }
        }

        // 4. Kiểm tra Material
        Material mat = Material.matchMaterial(lowerKey);
        if (mat != null) {
            return Component.text("[" + IconCatalog.displayName(mat) + "]");
        }

        // 5. Kiểm tra Player Online
        Player online = findOnlinePlayer(key);
        if (online != null) {
            return Component.text("[" + online.getName() + "]");
        }

        return null;
    }

    /**
     * Chuyển đổi một key emote (ví dụ "diamond", "VietNam", "pearl", "Kury") thành mã thẻ hiển thị của plugin TAB:
     * - Sprite vật phẩm/khối: <sprite:"minecraft:items":"minecraft:item/diamond">
     * - Head texture: <head_texture:TEXTURE_HASH>
     * - Player head: <head:name:PlayerName>
     */
    public String getTabTokenString(String key, Player player) {
        if (key == null || key.isEmpty()) return null;
        if (key.startsWith(":")) key = key.substring(1);
        if (key.endsWith(":")) key = key.substring(0, key.length() - 1);
        if (key.isEmpty()) return null;

        boolean isLegacy = configManager.isLegacyCompatibilityEnabled()
                && configManager.isLegacyApplyToPlaceholders()
                && ClientPlatformUtil.isLegacyOrBedrock(player, configManager.getMinProtocolVersion());

        String lowerKey = key.toLowerCase(Locale.ROOT);

        // 1. Kiểm tra Sprite
        SpriteItem sprite = sprites.get(lowerKey);
        if (sprite != null) {
            if (player == null || player.hasPermission(sprite.getPermission())) {
                if (isLegacy) return formatLegacyItemString(key);
                String atlasStr = (sprite.getAtlas() != null) ? sprite.getAtlas().asString() : IconCatalog.resolveAtlas(sprite.getSpriteKey()).asString();
                String spriteStr = sprite.getSpriteKey().asString();
                return "<sprite:\"" + atlasStr + "\":\"" + spriteStr + "\">";
            }
            return null;
        }

        // 2. Kiểm tra Head từ cấu hình
        HeadItem head = heads.get(lowerKey);
        if (head != null) {
            if (player == null || player.hasPermission(head.getPermission())) {
                if (isLegacy) return formatLegacyItemString(key);
                return formatHeadForTab(head);
            }
            return null;
        }

        // 3. Kiểm tra Shorthand
        String shorthandTarget = SHORTHANDS.get(lowerKey);
        if (shorthandTarget != null) {
            SpriteItem targetSprite = sprites.get(shorthandTarget);
            if (targetSprite != null) {
                if (player == null || player.hasPermission(targetSprite.getPermission())) {
                    if (isLegacy) return formatLegacyItemString(key);
                    String atlasStr = (targetSprite.getAtlas() != null) ? targetSprite.getAtlas().asString() : IconCatalog.resolveAtlas(targetSprite.getSpriteKey()).asString();
                    String spriteStr = targetSprite.getSpriteKey().asString();
                    return "<sprite:\"" + atlasStr + "\":\"" + spriteStr + "\">";
                }
                return null;
            }
            HeadItem targetHead = heads.get(shorthandTarget);
            if (targetHead != null) {
                if (player == null || player.hasPermission(targetHead.getPermission())) {
                    if (isLegacy) return formatLegacyItemString(key);
                    return formatHeadForTab(targetHead);
                }
                return null;
            }
            Material targetMat = Material.matchMaterial(shorthandTarget);
            if (targetMat != null && (player == null || player.hasPermission("spriteschat.use"))) {
                if (isLegacy) return formatLegacyItemString(key);
                return IconCatalog.getTabSprite(targetMat);
            }
        }

        // 4. Kiểm tra Material
        Material mat = Material.matchMaterial(lowerKey);
        if (mat != null && (player == null || player.hasPermission("spriteschat.use"))) {
            if (isLegacy) return formatLegacyItemString(key);
            return IconCatalog.getTabSprite(mat);
        }

        // 5. Kiểm tra Player Head (:nameplayer: hoặc :head:nameplayer: hoặc :player:nameplayer:)
        String candidateName = key;
        if (lowerKey.startsWith("head:") || lowerKey.startsWith("player:")) {
            candidateName = key.substring(key.indexOf(':') + 1);
        }
        if (configManager.isPlayerHeadsEnabled() && candidateName.matches("^[a-zA-Z0-9_]{3,16}$")) {
            String perm = configManager.getPlayerHeadsPermission();
            if (player == null || player.hasPermission(perm) || player.hasPermission("spriteschat.use")) {
                if (isLegacy) return formatLegacyItemString(candidateName);
                String format = configManager.getTabHeadFormat();
                if ("HEAD_TEXTURE".equalsIgnoreCase(format)) {
                    CrackedPlayerUtil.PlayerSkinProfile profile = CrackedPlayerUtil.resolvePlayerProfile(candidateName);
                    if (profile != null && profile.getTextureUrl() != null && profile.getTextureUrl().contains("/texture/")) {
                        String hash = profile.getTextureUrl().substring(profile.getTextureUrl().lastIndexOf('/') + 1);
                        return "<head_texture:" + hash + ">";
                    }
                } else if ("HEAD_UUID".equalsIgnoreCase(format) || "UUID".equalsIgnoreCase(format)) {
                    CrackedPlayerUtil.PlayerSkinProfile profile = CrackedPlayerUtil.resolvePlayerProfile(candidateName);
                    if (profile != null && profile.getUuid() != null) {
                        return "<head:" + profile.getUuid().toString() + ">";
                    }
                }
                return "<head:" + candidateName + ">";
            }
        }

        return null;
    }

    /**
     * Định dạng thẻ head cho TAB dựa theo cấu hình tab-integration.head-format:
     * - HEAD_TEXTURE: <head_texture:hash> (Yêu cầu minimessage-support: true trong config TAB)
     * - HEAD_NAME: <head:Name> (Cú pháp cơ bản của TAB)
     * - HEAD_UUID: <head:UUID> (Cú pháp UUID của TAB)
     * - SPRITE_FALLBACK: <sprite:"atlas":"path"> (Hoạt động hoàn hảo cả trên Scoreboard!)
     */
    public String formatHeadForTab(HeadItem head) {
        if (head == null) return null;
        String format = configManager.getTabHeadFormat();
        if (format == null) format = "HEAD_TEXTURE";

        // 1. Chế độ SPRITE_FALLBACK (Dùng icon sprite thay thế cho Scoreboard)
        if ("SPRITE_FALLBACK".equalsIgnoreCase(format) || "SPRITE".equalsIgnoreCase(format)) {
            if (head.getFallbackSprite() != null && !head.getFallbackSprite().isEmpty()) {
                String fallback = head.getFallbackSprite();
                net.kyori.adventure.key.Key spriteKey = fallback.contains(":") ? net.kyori.adventure.key.Key.key(fallback) : net.kyori.adventure.key.Key.key("minecraft", fallback);
                String atlas = IconCatalog.resolveAtlas(spriteKey).asString();
                return "<sprite:\"" + atlas + "\":\"" + fallback + "\">";
            }
        }

        // 2. Chế độ HEAD_NAME: <head:Name>
        if ("HEAD_NAME".equalsIgnoreCase(format) || "NAME".equalsIgnoreCase(format)) {
            return "<head:" + head.getName() + ">";
        }

        // 3. Chế độ HEAD_UUID: <head:UUID>
        if ("HEAD_UUID".equalsIgnoreCase(format) || "UUID".equalsIgnoreCase(format)) {
            if (head.getUuid() != null) {
                return "<head:" + head.getUuid().toString() + ">";
            }
        }

        // 4. Mặc định HEAD_TEXTURE: <head_texture:hash>
        String textureUrl = head.getTextureUrl();
        if (textureUrl != null && textureUrl.contains("/texture/")) {
            String hash = textureUrl.substring(textureUrl.lastIndexOf('/') + 1);
            return "<head_texture:" + hash + ">";
        }

        // Fallback nếu không có texture URL
        if (configManager.isTabFallbackToSprite() && head.getFallbackSprite() != null && !head.getFallbackSprite().isEmpty()) {
            String fallback = head.getFallbackSprite();
            net.kyori.adventure.key.Key spriteKey = fallback.contains(":") ? net.kyori.adventure.key.Key.key(fallback) : net.kyori.adventure.key.Key.key("minecraft", fallback);
            String atlas = IconCatalog.resolveAtlas(spriteKey).asString();
            return "<sprite:\"" + atlas + "\":\"" + fallback + "\">";
        }

        if (head.getUuid() != null) {
            return "<head:" + head.getUuid().toString() + ">";
        }
        return "<head:" + head.getName() + ">";
    }

    /**
     * Lấy thẻ Atlas Sprite thay thế (fallback) cho head hoặc sprite.
     * Rất hữu ích cho Scoreboard sidebar nơi Minecraft không hỗ trợ player skull.
     */
    public String getTabSpriteFallback(String key, Player player) {
        if (key == null || key.isEmpty()) return null;
        if (key.startsWith(":")) key = key.substring(1);
        if (key.endsWith(":")) key = key.substring(0, key.length() - 1);
        if (key.isEmpty()) return null;

        boolean isLegacy = configManager.isLegacyCompatibilityEnabled()
                && configManager.isLegacyApplyToPlaceholders()
                && ClientPlatformUtil.isLegacyOrBedrock(player, configManager.getMinProtocolVersion());

        String lowerKey = key.toLowerCase(Locale.ROOT);

        // 1. Kiểm tra Sprite thông thường
        SpriteItem sprite = sprites.get(lowerKey);
        if (sprite != null) {
            if (player == null || player.hasPermission(sprite.getPermission())) {
                if (isLegacy) return formatLegacyItemString(key);
                String atlasStr = (sprite.getAtlas() != null) ? sprite.getAtlas().asString() : IconCatalog.resolveAtlas(sprite.getSpriteKey()).asString();
                return "<sprite:\"" + atlasStr + "\":\"" + sprite.getSpriteKey().asString() + "\">";
            }
            return null;
        }

        // 2. Kiểm tra Head có fallback_sprite
        HeadItem head = heads.get(lowerKey);
        if (head != null) {
            if (player == null || player.hasPermission(head.getPermission())) {
                if (isLegacy) return formatLegacyItemString(key);
                if (head.getFallbackSprite() != null && !head.getFallbackSprite().isEmpty()) {
                    String fallback = head.getFallbackSprite();
                    net.kyori.adventure.key.Key spriteKey = fallback.contains(":") ? net.kyori.adventure.key.Key.key(fallback) : net.kyori.adventure.key.Key.key("minecraft", fallback);
                    String atlas = IconCatalog.resolveAtlas(spriteKey).asString();
                    return "<sprite:\"" + atlas + "\":\"" + fallback + "\">";
                }
            }
            return null;
        }

        // 3. Shorthand
        String shorthandTarget = SHORTHANDS.get(lowerKey);
        if (shorthandTarget != null) {
            return getTabSpriteFallback(shorthandTarget, player);
        }

        // 4. Material
        Material mat = Material.matchMaterial(lowerKey);
        if (mat != null && (player == null || player.hasPermission("spriteschat.use"))) {
            if (isLegacy) return formatLegacyItemString(key);
            return IconCatalog.getTabSprite(mat);
        }

        return null;
    }

    /**
     * Chuyển đổi mọi placeholder :token: trong văn bản sang định dạng hiển thị của plugin TAB.
     * Thích hợp cho TAB Scoreboard, Tablist header/footer, và LuckPerms prefix/suffix.
     * Ví dụ: "&a[VIP :diamond:] " -> "&a[VIP <sprite:\"minecraft:items\":\"minecraft:item/diamond\">] "
     */
    public String convertToTabString(String text, Player player) {
        if (text == null || text.isEmpty() || !text.contains(":")) {
            return text;
        }

        Matcher matcher = SPRITE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String fullMatch = matcher.group(0); // e.g. ":diamond:"
            String rawKey = matcher.group(1);    // e.g. "diamond"
            String tabTag = getTabTokenString(rawKey, player);
            if (tabTag != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(tabTag));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(fullMatch));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
