package com.kury.spriteschat.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catalog of Minecraft native sprites and atlas resolver.
 * Backed by Minecraft's Material registry and valid-icon-sprites.txt.
 * Supports Minecraft 1.21+ / Paper / Folia native Adventure ObjectComponent.
 */
public final class IconCatalog {

    public static final Key ATLAS_BLOCKS = Key.key("minecraft", "blocks");
    public static final Key ATLAS_ITEMS = Key.key("minecraft", "items");

    private static final String VALID_SPRITES_RESOURCE = "/icons/valid-icon-sprites.txt";
    private static volatile Set<String> validSprites;
    private static final Map<String, Component> CACHE = new ConcurrentHashMap<>();

    private IconCatalog() {}

    public static Set<String> getValidSprites() {
        Set<String> local = validSprites;
        if (local == null) {
            synchronized (IconCatalog.class) {
                local = validSprites;
                if (local == null) {
                    local = loadValidSprites();
                    validSprites = local;
                }
            }
        }
        return local;
    }

    private static Set<String> loadValidSprites() {
        Set<String> set = new HashSet<>(4096);
        try (InputStream in = IconCatalog.class.getResourceAsStream(VALID_SPRITES_RESOURCE)) {
            if (in == null) return set;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        set.add(trimmed);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return set;
    }

    /**
     * Resolves the correct atlas (minecraft:items or minecraft:blocks) for a sprite key.
     */
    public static Key resolveAtlas(Key spriteKey) {
        if (spriteKey == null) return ATLAS_BLOCKS;
        String val = spriteKey.value().toLowerCase(Locale.ROOT);
        if (val.startsWith("item/")) {
            return ATLAS_ITEMS;
        } else if (val.startsWith("block/")) {
            return ATLAS_BLOCKS;
        }

        Set<String> valid = getValidSprites();
        if (valid.contains("item/" + val)) {
            return ATLAS_ITEMS;
        } else if (valid.contains("block/" + val)) {
            return ATLAS_BLOCKS;
        }

        return ATLAS_ITEMS;
    }

    /**
     * Creates a Native Minecraft 1.21 ObjectComponent sprite with the proper atlas.
     */
    public static Component createObjectSprite(@Nullable Key atlas, Key spriteKey) {
        Key safeAtlas = (atlas != null) ? atlas : resolveAtlas(spriteKey);
        return Component.object(ObjectContents.sprite(safeAtlas, spriteKey));
    }

    /**
     * Resolves sprite for a Bukkit Material natively.
     */
    public static @Nullable Component spriteFor(@Nullable Material m) {
        if (m == null || m.isLegacy() || m == Material.AIR) return null;

        Component cached = CACHE.get(m.getKey().toString());
        if (cached != null) return cached;

        NamespacedKey nk = m.getKey();
        String id = nk.getKey();
        Set<String> valid = getValidSprites();

        Key atlas = null;
        Key spriteKey = null;

        if (valid.contains("item/" + id)) {
            atlas = ATLAS_ITEMS;
            spriteKey = Key.key(nk.getNamespace(), "item/" + id);
        } else if (valid.contains("block/" + id)) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id);
        } else if (valid.contains("block/" + id + "_side")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_side");
        } else if (valid.contains("block/" + id + "_top")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_top");
        } else if (valid.contains("block/" + id + "_front")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_front");
        } else if (valid.contains("block/" + id + "_top_active")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_top_active");
        } else if (valid.contains("block/" + id + "_side_active")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_side_active");
        } else if (valid.contains("block/" + id + "_side_on")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_side_on");
        } else if (valid.contains("block/" + id + "_bottom")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_bottom");
        } else if (valid.contains("item/" + id + "_00")) {
            atlas = ATLAS_ITEMS;
            spriteKey = Key.key(nk.getNamespace(), "item/" + id + "_00");
        } else if (m.isBlock() && valid.contains("block/" + id)) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id);
        } else if ("trial_spawner".equals(id)) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/trial_spawner_top_active");
        } else if ("vault".equals(id)) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/vault_top");
        } else if ("crossbow".equals(id)) {
            atlas = ATLAS_ITEMS;
            spriteKey = Key.key(nk.getNamespace(), "item/crossbow_standby");
        } else if ("enchanted_golden_apple".equals(id)) {
            atlas = ATLAS_ITEMS;
            spriteKey = Key.key(nk.getNamespace(), "item/golden_apple");
        } else if ("tnt".equals(id)) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/tnt_side");
        } else if ("respawn_anchor".equals(id)) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/respawn_anchor_top");
        } else if (id.endsWith("_bed")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_head_up");
        }

        if (spriteKey != null && atlas != null) {
            Component built = Component.object(ObjectContents.sprite(atlas, spriteKey));
            CACHE.put(m.getKey().toString(), built);
            return built;
        }

        return null;
    }

    public static String displayName(Material m) {
        String[] parts = m.getKey().getKey().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    /**
     * Trả về định dạng thẻ sprite tương thích với plugin TAB: <sprite:"ATLAS":"SPRITE">
     */
    public static String getTabSprite(Material m) {
        if (m == null || m.isLegacy() || m == Material.AIR) return null;
        NamespacedKey nk = m.getKey();
        String id = nk.getKey();
        Set<String> valid = getValidSprites();

        Key atlas = ATLAS_ITEMS;
        Key spriteKey = null;

        if (valid.contains("item/" + id)) {
            atlas = ATLAS_ITEMS;
            spriteKey = Key.key(nk.getNamespace(), "item/" + id);
        } else if (valid.contains("block/" + id)) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id);
        } else if (valid.contains("block/" + id + "_side")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_side");
        } else if (valid.contains("block/" + id + "_top")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_top");
        } else if (valid.contains("block/" + id + "_front")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_front");
        } else if (valid.contains("block/" + id + "_top_active")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_top_active");
        } else if (valid.contains("block/" + id + "_side_active")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_side_active");
        } else if (valid.contains("block/" + id + "_side_on")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_side_on");
        } else if (valid.contains("block/" + id + "_bottom")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_bottom");
        } else if (valid.contains("item/" + id + "_00")) {
            atlas = ATLAS_ITEMS;
            spriteKey = Key.key(nk.getNamespace(), "item/" + id + "_00");
        } else if (m.isBlock() && valid.contains("block/" + id)) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id);
        } else if ("trial_spawner".equals(id)) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/trial_spawner_top_active");
        } else if ("vault".equals(id)) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/vault_top");
        } else if ("crossbow".equals(id)) {
            atlas = ATLAS_ITEMS;
            spriteKey = Key.key(nk.getNamespace(), "item/crossbow_standby");
        } else if ("enchanted_golden_apple".equals(id)) {
            atlas = ATLAS_ITEMS;
            spriteKey = Key.key(nk.getNamespace(), "item/golden_apple");
        } else if ("tnt".equals(id)) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/tnt_side");
        } else if ("respawn_anchor".equals(id)) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/respawn_anchor_top");
        } else if (id.endsWith("_bed")) {
            atlas = ATLAS_BLOCKS;
            spriteKey = Key.key(nk.getNamespace(), "block/" + id + "_head_up");
        } else {
            atlas = ATLAS_ITEMS;
            spriteKey = Key.key(nk.getNamespace(), "item/" + id);
        }

        return "<sprite:\"" + atlas.asString() + "\":\"" + spriteKey.asString() + "\">";
    }
}
