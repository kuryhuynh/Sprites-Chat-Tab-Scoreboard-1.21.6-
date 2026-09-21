package com.kury.spriteschat.model;

import com.kury.spriteschat.util.IconCatalog;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.object.ObjectContents;

public class SpriteItem {

    private final String key;
    private final Key atlas;
    private final Key spriteKey;
    private final String displayName;
    private final String description;
    private final String permission;

    public SpriteItem(String key, Key atlas, Key spriteKey, String displayName, String description, String permission) {
        this.key = key;
        this.spriteKey = spriteKey;
        this.atlas = (atlas != null) ? atlas : IconCatalog.resolveAtlas(spriteKey);
        this.displayName = displayName != null ? displayName : key;
        this.description = description != null ? description : "";
        this.permission = permission != null ? permission : "spriteschat.use";
    }

    public SpriteItem(String key, Key spriteKey, String displayName, String description, String permission) {
        this(key, null, spriteKey, displayName, description, permission);
    }

    public String getKey() {
        return key;
    }

    public Key getAtlas() {
        return atlas;
    }

    public Key getSpriteKey() {
        return spriteKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getPermission() {
        return permission;
    }

    /**
     * Tạo Adventure ObjectComponent Native của Minecraft 1.21 (SpriteObjectContents).
     * Render trực tiếp sprite item (với atlas chính xác minecraft:items hoặc minecraft:blocks)
     * trong chat mà KHÔNG CẦN Resource Pack!
     */
    public Component toComponent(boolean enableHover, boolean enableClick, String clickAction) {
        Component comp = Component.object(ObjectContents.sprite(atlas, spriteKey));

        if (enableHover) {
            comp = comp.hoverEvent(HoverEvent.showText(MiniMessage.miniMessage().deserialize(displayName)));
        }

        if (enableClick) {
            if ("COPY_TO_CLIPBOARD".equalsIgnoreCase(clickAction)) {
                comp = comp.clickEvent(ClickEvent.copyToClipboard(":" + key + ":"));
            } else {
                comp = comp.clickEvent(ClickEvent.suggestCommand(":" + key + ":"));
            }
        }

        return comp;
    }
}
