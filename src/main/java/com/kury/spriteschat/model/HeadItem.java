package com.kury.spriteschat.model;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class HeadItem {

    private final String key;
    private final String name;
    private final UUID uuid;
    private String textureUrl;
    private String value;
    private final String displayName;
    private final String description;
    private final String category;
    private final String permission;
    private final String fallbackSprite;
    private BufferedImage faceImage;

    public HeadItem(String key, String name, UUID uuid, String textureUrl, String value,
                    String displayName, String description, String category, String permission) {
        this(key, name, uuid, textureUrl, value, displayName, description, category, permission, null);
    }

    public HeadItem(String key, String name, UUID uuid, String textureUrl, String value,
                    String displayName, String description, String category, String permission,
                    String fallbackSprite) {
        this.key = key;
        this.name = name;
        this.uuid = uuid;
        this.textureUrl = (textureUrl != null && textureUrl.startsWith("http://textures.minecraft.net/"))
                ? textureUrl.replace("http://textures.minecraft.net/", "https://textures.minecraft.net/")
                : textureUrl;
        this.value = ensureHttpsTextureValue(value);
        this.displayName = displayName != null ? displayName : key;
        this.description = description != null ? description : "";
        this.category = category != null ? category : "miscellaneous";
        this.permission = permission != null ? permission : "spriteschat.use.heads";
        this.fallbackSprite = fallbackSprite;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getTextureUrl() {
        return textureUrl;
    }

    public void setTextureUrl(String textureUrl) {
        this.textureUrl = (textureUrl != null && textureUrl.startsWith("http://textures.minecraft.net/"))
                ? textureUrl.replace("http://textures.minecraft.net/", "https://textures.minecraft.net/")
                : textureUrl;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = ensureHttpsTextureValue(value);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getPermission() {
        return permission;
    }

    public String getFallbackSprite() {
        return fallbackSprite;
    }

    public BufferedImage getFaceImage() {
        return faceImage;
    }

    public void setFaceImage(BufferedImage faceImage) {
        this.faceImage = faceImage;
    }

    /**
     * Đảm bảo chuỗi Base64 Skin Texture luôn sử dụng giao thức an toàn HTTPS (https://textures.minecraft.net/).
     * Khắc phục triệt để lỗi client Minecraft 1.20+ chặn hoặc từ chối nạp texture HTTP không an toàn.
     */
    public static String ensureHttpsTextureValue(String base64) {
        if (base64 == null || base64.isEmpty()) return base64;
        try {
            String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            if (decoded.contains("http://textures.minecraft.net/")) {
                decoded = decoded.replace("http://textures.minecraft.net/", "https://textures.minecraft.net/");
                return Base64.getEncoder().encodeToString(decoded.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
        return base64;
    }

    /**
     * Chuẩn hóa tên player name tuân theo chuẩn Minecraft ([a-zA-Z0-9_]{1,16}).
     */
    public static String sanitizePlayerName(String input) {
        if (input == null) return null;
        String safe = input.replaceAll("[^a-zA-Z0-9_]", "");
        if (safe.isEmpty()) return null;
        if (safe.length() > 16) {
            safe = safe.substring(0, 16);
        }
        return safe;
    }

    /**
     * Tạo Adventure ObjectComponent Native của Minecraft 1.21 (PlayerHeadObjectContents).
     * Render trực tiếp player head của lá cờ Việt Nam trong chat mà KHÔNG CẦN Resource Pack!
     */
    public Component toComponent(boolean enableHover, boolean enableClick, String clickAction) {
        String safeValue = ensureHttpsTextureValue(value);

        PlayerHeadObjectContents.Builder builder = ObjectContents.playerHead()
                .hat(true);

        // Tạo UUID chuẩn cho custom head
        UUID headUuid = this.uuid;
        if (headUuid == null && safeValue != null && !safeValue.isEmpty()) {
            headUuid = UUID.nameUUIDFromBytes(("VN_HEAD_" + safeValue).getBytes(StandardCharsets.UTF_8));
        }
        if (headUuid != null) {
            builder.id(headUuid);
        }

        // Gán texture profile property dạng HTTPS
        if (safeValue != null && !safeValue.isEmpty()) {
            builder.profileProperty(PlayerHeadObjectContents.property("textures", safeValue));
        }

        // Gán tên định danh (được sanitize) để nếu chat formatter làm phẳng thành text,
        // nó sẽ trở thành [Name head] thay vì [unknown player head], giúp SpriteManager dễ dàng khôi phục.
        String safeName = sanitizePlayerName(this.name != null && !this.name.isEmpty() ? this.name : this.key);
        if (safeName != null) {
            builder.name(safeName);
        }

        Component comp = Component.object(builder.build());

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
