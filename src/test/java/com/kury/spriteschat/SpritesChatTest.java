package com.kury.spriteschat;

import com.kury.spriteschat.api.MinecraftHeadsService;
import com.kury.spriteschat.model.HeadItem;
import com.kury.spriteschat.model.SpriteItem;
import com.kury.spriteschat.util.IconCatalog;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import net.kyori.adventure.text.object.SpriteObjectContents;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class SpritesChatTest {

    @Test
    public void testBase64TextureExtraction() {
        // Dữ liệu Vietnam Flag từ minecraft-heads.com
        String base64Value = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWM4YjdjN2ZiMDhhYjg5NDc4MTJiYjlkMTRkYTA0OWVkMmQ1MWFkOGNiOTMyYjM5MmMyMjY2YjI1N2FkYzJhYyJ9fX0=";
        String textureUrl = MinecraftHeadsService.extractTextureUrlFromBase64(base64Value);

        assertNotNull(textureUrl);
        assertEquals("http://textures.minecraft.net/texture/1c8b7c7fb08ab8947812bb9d14da049ed2d51ad8cb932b392c2266b257adc2ac", textureUrl);
    }

    @Test
    public void testPlaceholderRegex() {
        Pattern pattern = Pattern.compile(":([a-zA-Z0-9_]+(?::[a-zA-Z0-9_]+)?):");

        String chat = "Xin chào :VietNam: và :vn:, xem :Kury: hay :head:Steve: và cắm :end_crystal: tại đây!";
        Matcher matcher = pattern.matcher(chat);

        assertTrue(matcher.find());
        assertEquals("VietNam", matcher.group(1));

        assertTrue(matcher.find());
        assertEquals("vn", matcher.group(1));

        assertTrue(matcher.find());
        assertEquals("Kury", matcher.group(1));

        assertTrue(matcher.find());
        assertEquals("head:Steve", matcher.group(1));

        assertTrue(matcher.find());
        assertEquals("end_crystal", matcher.group(1));

        assertFalse(matcher.find());
    }

    @Test
    public void testSpriteItemNativeComponent() {
        SpriteItem sprite = new SpriteItem(
                "end_crystal",
                Key.key("minecraft", "items"),
                Key.key("minecraft", "item/end_crystal"),
                "<light_purple>[End Crystal]</light_purple>",
                "End Crystal icon",
                "spriteschat.use"
        );

        Component comp = sprite.toComponent(true, true, "SUGGEST_COMMAND");
        assertNotNull(comp);
        assertTrue(comp instanceof ObjectComponent, "Component phải là ObjectComponent native của Minecraft 1.21");

        ObjectComponent obj = (ObjectComponent) comp;
        assertTrue(obj.contents() instanceof SpriteObjectContents, "Nội dung phải là SpriteObjectContents");

        SpriteObjectContents spriteContents = (SpriteObjectContents) obj.contents();
        assertEquals(Key.key("minecraft", "item/end_crystal"), spriteContents.sprite());
        assertEquals(Key.key("minecraft", "items"), spriteContents.atlas(), "Atlas phải là minecraft:items cho item sprite");

        String json = GsonComponentSerializer.gson().serialize(comp);
        assertNotNull(json);
        assertTrue(json.contains("\"atlas\":\"minecraft:items\""), "JSON phải chứa atlas minecraft:items");
        assertTrue(json.contains("\"sprite\":\"minecraft:item/end_crystal\""), "JSON phải chứa sprite minecraft:item/end_crystal");
    }

    @Test
    public void testImage2VietnamTexture() {
        // Texture chuẩn của cờ Việt Nam trong Ảnh 2 (viền bo tròn, ngôi sao vàng tỷ lệ chuẩn ở giữa)
        String textureHash = "8a57b9d7dd04169478cfdb8d0b6fd0b8c82b6566bb28371ee9a7c7c1671ad0bb";
        String base64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzhhNTdiOWQ3ZGQwNDE2OTQ3OGNmZGI4ZDBiNmZkMGI4YzgyYjY1NjZiYjI4MzcxZWU5YTdjN2MxNjcxYWQwYmIifX19";
        UUID uuid = UUID.fromString("70d3bb3c-86fc-471a-9e1a-58a8d43987d8");

        HeadItem head = new HeadItem(
                "VietNam",
                "Vietnam",
                uuid,
                "https://textures.minecraft.net/texture/" + textureHash,
                base64,
                "<red>[Cờ Việt Nam]</red>",
                "Cờ Tổ Quốc Việt Nam",
                "miscellaneous",
                "spriteschat.use.heads"
        );

        Component comp = head.toComponent(true, true, "SUGGEST_COMMAND");
        assertNotNull(comp);
        assertTrue(comp instanceof ObjectComponent);

        ObjectComponent obj = (ObjectComponent) comp;
        assertTrue(obj.contents() instanceof PlayerHeadObjectContents);

        PlayerHeadObjectContents headContents = (PlayerHeadObjectContents) obj.contents();
        assertEquals(uuid, headContents.id());
        assertTrue(headContents.hat(), "Lớp mũ/overlay chứa hình ngôi sao ảnh 2 phải được bật");

        String json = GsonComponentSerializer.gson().serialize(comp);
        assertNotNull(json);
        assertTrue(json.contains("textures"));
        assertTrue(json.contains(base64));
    }

    @Test
    public void testPlayerHeadByName() {
        // Test tính năng :nameplayer: hiển thị đầu người chơi trực tiếp
        Component notchHead = Component.object(ObjectContents.playerHead("Notch"));
        String jsonNotch = GsonComponentSerializer.gson().serialize(notchHead);
        assertNotNull(jsonNotch);
        assertEquals("{\"hat\":true,\"player\":\"Notch\"}", jsonNotch);

        Component kuryHead = Component.object(ObjectContents.playerHead("Kury"));
        String jsonKury = GsonComponentSerializer.gson().serialize(kuryHead);
        assertNotNull(jsonKury);
        assertEquals("{\"hat\":true,\"player\":\"Kury\"}", jsonKury);
    }

    @Test
    public void testIconCatalogSpriteForMaterial() {
        Component comp = IconCatalog.spriteFor(Material.END_CRYSTAL);
        assertNotNull(comp);

        ObjectComponent obj = (ObjectComponent) comp;
        SpriteObjectContents contents = (SpriteObjectContents) obj.contents();
        assertEquals(IconCatalog.ATLAS_ITEMS, contents.atlas());
        assertEquals(Key.key("minecraft", "item/end_crystal"), contents.sprite());
    }

    @Test
    public void testHoverTooltipNoPlaceholder() {
        SpriteItem sprite = new SpriteItem(
                "end_crystal",
                Key.key("minecraft", "items"),
                Key.key("minecraft", "item/end_crystal"),
                "<light_purple>[End Crystal]</light_purple>",
                "End Crystal icon",
                "spriteschat.use"
        );

        Component spriteComp = sprite.toComponent(true, false, "SUGGEST_COMMAND");
        String spriteJson = GsonComponentSerializer.gson().serialize(spriteComp);
        assertFalse(spriteJson.contains("Placeholder:"), "Hover tooltip của SpriteItem KHÔNG ĐƯỢC chứa dòng chữ 'Placeholder:'");

        HeadItem head = new HeadItem(
                "VietNam",
                "Vietnam",
                UUID.randomUUID(),
                "https://textures.minecraft.net/texture/8a57b9d7dd04169478cfdb8d0b6fd0b8c82b6566bb28371ee9a7c7c1671ad0bb",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzhhNTdiOWQ3ZGQwNDE2OTQ3OGNmZGI4ZDBiNmZkMGI4YzgyYjY1NjZiYjI4MzcxZWU5YTdjN2MxNjcxYWQwYmIifX19",
                "<red>[Lá Cờ Việt Nam]</red>",
                "Quốc kỳ Việt Nam",
                "miscellaneous",
                "spriteschat.use.heads"
        );

        Component headComp = head.toComponent(true, false, "SUGGEST_COMMAND");
        String headJson = GsonComponentSerializer.gson().serialize(headComp);
        assertFalse(headJson.contains("Placeholder:"), "Hover tooltip của HeadItem KHÔNG ĐƯỢC chứa dòng chữ 'Placeholder:'");
    }

    @Test
    public void testShorthandMappings() {
        assertEquals("ender_pearl", com.kury.spriteschat.manager.SpriteManager.SHORTHANDS.get("pearl"));
        assertEquals("end_crystal", com.kury.spriteschat.manager.SpriteManager.SHORTHANDS.get("crystal"));
        assertEquals("totem_of_undying", com.kury.spriteschat.manager.SpriteManager.SHORTHANDS.get("totem"));
        assertEquals("golden_apple", com.kury.spriteschat.manager.SpriteManager.SHORTHANDS.get("gap"));
        assertEquals("enchanted_golden_apple", com.kury.spriteschat.manager.SpriteManager.SHORTHANDS.get("egap"));
        assertEquals("netherite_sword", com.kury.spriteschat.manager.SpriteManager.SHORTHANDS.get("sword"));
        assertEquals("wind_charge", com.kury.spriteschat.manager.SpriteManager.SHORTHANDS.get("wind"));
        assertEquals("diamond", com.kury.spriteschat.manager.SpriteManager.SHORTHANDS.get("dia"));
        assertEquals("discord", com.kury.spriteschat.manager.SpriteManager.SHORTHANDS.get("dc"));
    }

    @Test
    public void testDiscordHead() {
        HeadItem discordHead = new HeadItem(
                "discord",
                "Discord",
                UUID.fromString("de431cd1-ae1d-49f6-9339-a96daeacc32b"),
                "https://textures.minecraft.net/texture/7873c12bffb5251a0b88d5ae75c7247cb39a75ff1a81cbe4c8a39b311ddeda",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzc4NzNjMTJiZmZiNTI1MWEwYjg4ZDVhZTc1YzcyNDdjYjM5YTc1ZmYxYTgxY2JlNGM4YTM5YjMxMWRkZWRhIn19fQ==",
                "<#5865F2><b>[Discord]</b></#5865F2>",
                "Biểu tượng Discord",
                "miscellaneous",
                "spriteschat.use.heads"
        );

        Component comp = discordHead.toComponent(true, true, "SUGGEST_COMMAND");
        assertNotNull(comp);
        assertTrue(comp instanceof ObjectComponent);

        String json = GsonComponentSerializer.gson().serialize(comp);
        assertNotNull(json);
        assertTrue(json.contains("textures"));
        assertTrue(json.contains("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzc4NzNjMTJiZmZiNTI1MWEwYjg4ZDVhZTc1YzcyNDdjYjM5YTc1ZmYxYTgxY2JlNGM4YTM5YjMxMWRkZWRhIn19fQ=="));
        assertFalse(json.contains("Placeholder:"));
    }

    @Test
    public void testEarthGlobeHead() {
        HeadItem earthHead = new HeadItem(
                "traidat",
                "Earth",
                UUID.fromString("28e6e8ad-40ab-4690-9206-507324177423"),
                "https://textures.minecraft.net/texture/1adbcf23e726f199339b253a5214f8831d62b97a50b0ed932c531f62c3223",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzFhZGJjZjIzZTcyNmYxOTkzMzliMjUzYTUyMTRmODgzMWQ2MmI5N2E1MGIwZWQ5MzJjNTMxZjYyYzMyMjMifX19",
                "<aqua><b>[Trái Đất]</b></aqua>",
                "Quả địa cầu Trái Đất",
                "miscellaneous",
                "spriteschat.use.heads"
        );

        Component comp = earthHead.toComponent(true, true, "SUGGEST_COMMAND");
        assertNotNull(comp);
        assertTrue(comp instanceof ObjectComponent);

        String json = GsonComponentSerializer.gson().serialize(comp);
        assertNotNull(json);
        assertTrue(json.contains("textures"));
        // Đảm bảo không chứa dòng lore mô tả khi hover chuột
        assertFalse(json.contains("Quả địa cầu Trái Đất"), "Hover tooltip KHÔNG ĐƯỢC chứa dòng lore mô tả");
        assertFalse(json.contains("Placeholder:"), "Hover tooltip KHÔNG ĐƯỢC chứa dòng Placeholder");
        assertTrue(json.contains("[Trái Đất]"), "Hover tooltip phải hiển thị đúng tên icon");
    }

    @Test
    public void testFlattenedSpritePattern() {
        Pattern flattenedPattern = Pattern.compile("\\[([a-zA-Z0-9_/-]+)@([a-zA-Z0-9_]+)\\]");
        String chat = "Kury: [item/diamond@items] và [block/tnt_side@blocks]";
        Matcher m = flattenedPattern.matcher(chat);

        assertTrue(m.find());
        assertEquals("item/diamond", m.group(1));
        assertEquals("items", m.group(2));

        assertTrue(m.find());
        assertEquals("block/tnt_side", m.group(1));
        assertEquals("blocks", m.group(2));

        assertFalse(m.find());
    }

    @Test
    public void testFlattenedHeadPattern() {
        Pattern headPattern = Pattern.compile("\\[([a-zA-Z0-9_]+)\\s+head\\]");
        String chat = "Head test: [Notch head] và [VietNam head]";
        Matcher m = headPattern.matcher(chat);

        assertTrue(m.find());
        assertEquals("Notch", m.group(1));

        assertTrue(m.find());
        assertEquals("VietNam", m.group(1));

        assertFalse(m.find());
    }

    @Test
    public void testMiniMessageSpritePattern() {
        Pattern mmPattern = Pattern.compile("<sprite:['\"]?([a-zA-Z0-9_:]+)['\"]?:['\"]?([a-zA-Z0-9_/:]+)['\"]?>");
        String chat = "Tag: <sprite:'minecraft:items':'minecraft:item/mace'>";
        Matcher m = mmPattern.matcher(chat);

        assertTrue(m.find());
        assertEquals("minecraft:items", m.group(1));
        assertEquals("minecraft:item/mace", m.group(2));

        assertFalse(m.find());
    }

    @Test
    public void testFlattenedSpriteRestoration() {
        Pattern flattenedPattern = Pattern.compile("\\[([a-zA-Z0-9_/-]+)@([a-zA-Z0-9_]+)\\]");
        String chat = "Player: [item/diamond@items]";
        Component comp = Component.text(chat);

        Component restored = comp.replaceText(net.kyori.adventure.text.TextReplacementConfig.builder()
                .match(flattenedPattern)
                .replacement((res, b) -> {
                    String path = res.group(1);
                    String atlas = res.group(2);
                    Key a = atlas.contains(":") ? Key.key(atlas) : Key.key("minecraft", atlas);
                    Key s = path.contains(":") ? Key.key(path) : Key.key("minecraft", path);
                    return Component.object(ObjectContents.sprite(a, s));
                })
                .build());

        String json = GsonComponentSerializer.gson().serialize(restored);
        assertNotNull(json);
        assertTrue(json.contains("\"atlas\":\"minecraft:items\""));
        assertTrue(json.contains("\"sprite\":\"minecraft:item/diamond\""));
        assertFalse(json.contains("[item/diamond@items]"), "Chuỗi làm phẳng [item/diamond@items] phải được thay thế hoàn toàn");
    }

    @Test
    public void testSuggestionsTokenGenerationAndFiltering() {
        java.util.Set<String> tokens = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        tokens.add(":diamond:");
        tokens.add(":dia:");
        tokens.add(":diamond_sword:");
        tokens.add(":emerald:");
        tokens.add(":ender_pearl:");
        tokens.add(":pearl:");
        tokens.add(":end_crystal:");
        tokens.add(":VietNam:");
        tokens.add(":vn:");

        // Kiểm tra tất cả token đều được bọc bởi ":"
        for (String t : tokens) {
            assertTrue(t.startsWith(":") && t.endsWith(":") && t.length() >= 3);
        }

        // Kiểm tra lọc khi người chơi gõ ":dia"
        String prefixDia = ":dia";
        java.util.List<String> matchesDia = tokens.stream()
                .filter(t -> t.toLowerCase(java.util.Locale.ROOT).startsWith(prefixDia.toLowerCase(java.util.Locale.ROOT)))
                .toList();

        assertEquals(3, matchesDia.size());
        assertTrue(matchesDia.contains(":diamond:"));
        assertTrue(matchesDia.contains(":dia:"));
        assertTrue(matchesDia.contains(":diamond_sword:"));

        // Kiểm tra lọc khi người chơi gõ ":vn"
        String prefixVn = ":vn";
        java.util.List<String> matchesVn = tokens.stream()
                .filter(t -> t.toLowerCase(java.util.Locale.ROOT).startsWith(prefixVn.toLowerCase(java.util.Locale.ROOT)))
                .toList();

        assertEquals(1, matchesVn.size());
        assertTrue(matchesVn.contains(":vn:"));

        // Kiểm tra lọc khi người chơi chỉ mới gõ ":"
        String prefixAll = ":";
        java.util.List<String> matchesAll = tokens.stream()
                .filter(t -> t.toLowerCase(java.util.Locale.ROOT).startsWith(prefixAll))
                .toList();

        assertEquals(tokens.size(), matchesAll.size(), "Khi gõ ':' phải hiển thị toàn bộ gợi ý emote");
    }

    @Test
    public void testAsyncTabCompletionBufferParsing() {
        String[] buffers = {
                ":dia",
                "Xin chào :dia",
                "/msg Steve :dia",
                "/w Steve :vn"
        };

        for (String buffer : buffers) {
            int lastSpace = buffer.lastIndexOf(' ');
            String currentWord = (lastSpace == -1) ? buffer : buffer.substring(lastSpace + 1);
            assertTrue(currentWord.startsWith(":"), "Từ cuối cùng phải bắt đầu bằng ':' trong buffer: " + buffer);
        }

        // Test trường hợp chat bình thường không có emote
        String normalBuffer = "Xin chào các bạn";
        int lastSpace = normalBuffer.lastIndexOf(' ');
        String currentWord = (lastSpace == -1) ? normalBuffer : normalBuffer.substring(lastSpace + 1);
        assertFalse(currentWord.startsWith(":"), "Từ thông thường không được nhận diện là emote prefix");

        // Test trường hợp đã gõ xong emote
        String completedEmote = ":diamond:";
        boolean isFinished = completedEmote.length() > 2 && completedEmote.endsWith(":");
        assertTrue(isFinished, "Emote đã đóng hai dấu ':' không cần gợi ý thêm");
    }

    @Test
    public void testAsyncTabCompleteCompletionWithTooltip() {
        Component tooltip = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<gradient:#38ef7d:#11998e>[Kim Cương]</gradient>");
        com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion completion =
                com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion.completion(":diamond:", tooltip);

        assertNotNull(completion);
        assertEquals(":diamond:", completion.suggestion());
        assertNotNull(completion.tooltip());
        assertEquals(tooltip, completion.tooltip());
    }

    @Test
    public void testIconCatalogTabSprite() {
        String diamondSprite = IconCatalog.getTabSprite(Material.DIAMOND);
        assertEquals("<sprite:\"minecraft:items\":\"minecraft:item/diamond\">", diamondSprite);

        String tntSprite = IconCatalog.getTabSprite(Material.TNT);
        assertEquals("<sprite:\"minecraft:blocks\":\"minecraft:block/tnt_side\">", tntSprite);

        String swordSprite = IconCatalog.getTabSprite(Material.NETHERITE_SWORD);
        assertEquals("<sprite:\"minecraft:items\":\"minecraft:item/netherite_sword\">", swordSprite);
    }

    @Test
    public void testTabConversionFormat() {
        // Test chuyển đổi chuỗi chứa icon sang format của plugin TAB
        Pattern pattern = Pattern.compile(":([a-zA-Z0-9_]+(?::[a-zA-Z0-9_]+)?):");
        String prefixVip = "&a[VIP :diamond:] ";
        Matcher m = pattern.matcher(prefixVip);
        assertTrue(m.find());
        assertEquals("diamond", m.group(1));

        String prefixAdmin = "&c[Admin :VietNam:] ";
        Matcher m2 = pattern.matcher(prefixAdmin);
        assertTrue(m2.find());
        assertEquals("VietNam", m2.group(1));

        String textureHash = "8a57b9d7dd04169478cfdb8d0b6fd0b8c82b6566bb28371ee9a7c7c1671ad0bb";
        String expectedAdminTab = "&c[Admin <head_texture:" + textureHash + ">] ";
        String replaced = prefixAdmin.replace(":VietNam:", "<head_texture:" + textureHash + ">");
        assertEquals(expectedAdminTab, replaced);
    }

    @Test
    public void testHeadTexturePatternMatchingAndComponentCreation() {
        Pattern headTexturePattern = Pattern.compile("<head_texture:([a-zA-Z0-9]+)>");
        String message = "<head_texture:7873c12bffb5251a0b88d5ae75c7247cb39a75ff1a81cbe4c8a39b311ddeda>";
        Matcher m = headTexturePattern.matcher(message);

        assertTrue(m.find());
        String hash = m.group(1);
        assertEquals("7873c12bffb5251a0b88d5ae75c7247cb39a75ff1a81cbe4c8a39b311ddeda", hash);

        // Giả lập dựng Native Player Head từ hash nếu lọt vào chat
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + hash + "\"}}}";
        String base64 = java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID headUuid = UUID.nameUUIDFromBytes(("HEAD_TEX_" + hash).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        PlayerHeadObjectContents.Builder headBuilder = ObjectContents.playerHead()
                .hat(true)
                .id(headUuid)
                .profileProperty(PlayerHeadObjectContents.property("textures", base64));
        Component comp = Component.object(headBuilder.build());

        assertNotNull(comp);
        assertTrue(comp instanceof ObjectComponent);
        ObjectComponent obj = (ObjectComponent) comp;
        assertTrue(obj.contents() instanceof PlayerHeadObjectContents);

        String serialized = GsonComponentSerializer.gson().serialize(comp);
        assertTrue(serialized.contains("textures"));
        assertTrue(serialized.contains(base64));
    }

    @Test
    public void testHeadTagPattern() {
        Pattern headTagPattern = Pattern.compile("<head:(?:name:|uuid:)?([a-zA-Z0-9_-]+)>");

        Matcher m1 = headTagPattern.matcher("<head:Kury>");
        assertTrue(m1.find());
        assertEquals("Kury", m1.group(1));

        Matcher m2 = headTagPattern.matcher("<head:name:Notch>");
        assertTrue(m2.find());
        assertEquals("Notch", m2.group(1));

        Matcher m3 = headTagPattern.matcher("<head:uuid:70d3bb3c-86fc-471a-9e1a-58a8d43987d8>");
        assertTrue(m3.find());
        assertEquals("70d3bb3c-86fc-471a-9e1a-58a8d43987d8", m3.group(1));
    }

    @Test
    public void testFallbackSpriteInHeadItem() {
        HeadItem vnHead = new HeadItem(
                "VietNam",
                "VietNam",
                UUID.fromString("70d3bb3c-86fc-471a-9e1a-58a8d43987d8"),
                "https://textures.minecraft.net/texture/8a57b9d7dd04169478cfdb8d0b6fd0b8c82b6566bb28371ee9a7c7c1671ad0bb",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzhhNTdiOWQ3ZGQwNDE2OTQ3OGNmZGI4ZDBiNmZkMGI4YzgyYjY1NjZiYjI4MzcxZWU5YTdjN2MxNjcxYWQwYmIifX19",
                "<red><b>[Lá Cờ Việt Nam]</b></red>",
                "Cờ Việt Nam",
                "miscellaneous",
                "spriteschat.use.heads",
                "minecraft:item/red_banner"
        );

        assertEquals("minecraft:item/red_banner", vnHead.getFallbackSprite());

        // Kiểm tra tạo sprite tag thay thế cho Scoreboard
        String fallback = vnHead.getFallbackSprite();
        Key spriteKey = fallback.contains(":") ? Key.key(fallback) : Key.key("minecraft", fallback);
        String atlas = IconCatalog.resolveAtlas(spriteKey).asString();
        String spriteTag = "<sprite:\"" + atlas + "\":\"" + fallback + "\">";
        assertEquals("<sprite:\"minecraft:items\":\"minecraft:item/red_banner\">", spriteTag);
    }

    @Test
    public void testSerializationPrints() {
        Component cPlayer = Component.object(ObjectContents.playerHead("Notch"));
        System.out.println("cPlayer JSON: " + GsonComponentSerializer.gson().serialize(cPlayer));

        Component cEmptyHead = Component.object(ObjectContents.playerHead().build());
        System.out.println("cEmptyHead JSON: " + GsonComponentSerializer.gson().serialize(cEmptyHead));

        Component cSprite = Component.object(ObjectContents.sprite(Key.key("minecraft", "blocks"), Key.key("minecraft", "block/spawner")));
        System.out.println("cSprite JSON: " + GsonComponentSerializer.gson().serialize(cSprite));

        UUID testUuid = UUID.nameUUIDFromBytes("OfflinePlayer:Kury".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String sampleTex = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzFjOGI3YzcifX19";
        PlayerHeadObjectContents.Builder b = ObjectContents.playerHead()
                .hat(true)
                .name("Kury")
                .id(testUuid)
                .profileProperty(PlayerHeadObjectContents.property("textures", sampleTex));
        Component cTexHead = Component.object(b.build());
        System.out.println("cTexHead JSON: " + GsonComponentSerializer.gson().serialize(cTexHead));

        try {
            Class<?> plainClass = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            Object plain = plainClass.getMethod("plainText").invoke(null);
            java.lang.reflect.Method ser = plainClass.getMethod("serialize", Component.class);
            System.out.println("cPlayer plain: " + ser.invoke(plain, cPlayer));
            System.out.println("cEmptyHead plain: " + ser.invoke(plain, cEmptyHead));
            System.out.println("cTexHead plain: " + ser.invoke(plain, cTexHead));
            System.out.println("cSprite plain: " + ser.invoke(plain, cSprite));
        } catch (Throwable t) {
            System.out.println("Plain serializer failed: " + t);
        }
    }

    @Test
    public void testBlockSpawnerRestoration() {
        Pattern pattern = Pattern.compile("\\[([a-zA-Z0-9_/-]+@[a-zA-Z0-9_]+|(?:block|item)/[a-zA-Z0-9_/-]+)\\]");
        String chat = "Người chơi đặt: [block/spawner] tại mỏ spawner";
        Matcher m = pattern.matcher(chat);

        assertTrue(m.find(), "Pattern phải bắt được chuỗi [block/spawner]");
        String raw = m.group(1);
        assertEquals("block/spawner", raw);

        // Khôi phục sprite
        String path = raw;
        Key atlas = IconCatalog.resolveAtlas(Key.key("minecraft", path));
        assertEquals(IconCatalog.ATLAS_BLOCKS, atlas, "Atlas của block/spawner phải là minecraft:blocks");

        Component comp = Component.object(ObjectContents.sprite(atlas, Key.key("minecraft", path)));
        assertNotNull(comp);
        assertTrue(comp instanceof ObjectComponent);
        ObjectComponent obj = (ObjectComponent) comp;
        assertTrue(obj.contents() instanceof SpriteObjectContents);
        SpriteObjectContents contents = (SpriteObjectContents) obj.contents();
        assertEquals(IconCatalog.ATLAS_BLOCKS, contents.atlas());
        assertEquals(Key.key("minecraft", "block/spawner"), contents.sprite());

        String json = GsonComponentSerializer.gson().serialize(comp);
        assertTrue(json.contains("\"sprite\":\"minecraft:block/spawner\""));
    }

    @Test
    public void testItemDiamondRestorationWithoutAtlas() {
        Pattern pattern = Pattern.compile("\\[([a-zA-Z0-9_/-]+@[a-zA-Z0-9_]+|(?:block|item)/[a-zA-Z0-9_/-]+)\\]");
        String chat = "Tôi có [item/diamond] và [block/tnt_side@blocks]";
        Matcher m = pattern.matcher(chat);

        assertTrue(m.find());
        assertEquals("item/diamond", m.group(1));

        assertTrue(m.find());
        assertEquals("block/tnt_side@blocks", m.group(1));

        assertFalse(m.find());
    }

    @Test
    public void testUnknownPlayerHeadPatternAndRestoration() {
        Pattern headPattern = Pattern.compile("\\[([a-zA-Z0-9_]+|unknown player)\\s+head\\]", Pattern.CASE_INSENSITIVE);
        String chat = "Tin nhắn có: [unknown player head] và [Notch head]";
        Matcher m = headPattern.matcher(chat);

        assertTrue(m.find(), "Phải bắt được [unknown player head]");
        assertEquals("unknown player", m.group(1).toLowerCase(java.util.Locale.ROOT));

        assertTrue(m.find(), "Phải bắt được [Notch head]");
        assertEquals("Notch", m.group(1));

        assertFalse(m.find());
    }

    @Test
    public void testHeadItemSerializationDoesNotProduceUnknownPlayerHead() throws Exception {
        HeadItem vnHead = new HeadItem(
                "VietNam",
                "VietNam",
                UUID.fromString("70d3bb3c-86fc-471a-9e1a-58a8d43987d8"),
                "https://textures.minecraft.net/texture/8a57b9d7dd04169478cfdb8d0b6fd0b8c82b6566bb28371ee9a7c7c1671ad0bb",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzhhNTdiOWQ3ZGQwNDE2OTQ3OGNmZGI4ZDBiNmZkMGI4YzgyYjY1NjZiYjI4MzcxZWU5YTdjN2MxNjcxYWQwYmIifX19",
                "<red><b>[Lá Cờ Việt Nam]</b></red>",
                "Cờ Việt Nam",
                "miscellaneous",
                "spriteschat.use.heads"
        );

        Component comp = vnHead.toComponent(false, false, "SUGGEST_COMMAND");
        Class<?> plainClass = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
        Object plain = plainClass.getMethod("plainText").invoke(null);
        java.lang.reflect.Method ser = plainClass.getMethod("serialize", Component.class);
        String plainText = (String) ser.invoke(plain, comp);

        assertNotNull(plainText);
        assertNotEquals("[unknown player head]", plainText, "HeadItem KHÔNG ĐƯỢC serialize thành '[unknown player head]'");
        assertEquals("[VietNam head]", plainText, "HeadItem phải serialize thành '[VietNam head]' để khôi phục chính xác");
    }

    @Test
    public void testCrackedPlayerUUIDCalculation() {
        String playerName = "KuryPlayer";
        UUID expectedUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID actualUuid = com.kury.spriteschat.util.CrackedPlayerUtil.getCrackedUUID(playerName);

        assertEquals(expectedUuid, actualUuid, "UUID người chơi crack phải khớp chuẩn Mojang v3 MD5 ('OfflinePlayer:' + name)");
    }

    @Test
    public void testCrackedPlayerDefaultSkinSelection() {
        UUID steveUuid = UUID.fromString("00000000-0000-0000-0000-000000000000"); // hashCode chẵn
        UUID alexUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");  // hashCode lẻ

        String steveSkin = com.kury.spriteschat.util.CrackedPlayerUtil.getDefaultSkinTexture(steveUuid);
        assertNotNull(steveSkin);
        assertTrue(steveSkin.contains("eyJ0ZXh0dXJlcy"));

        String alexSkin = com.kury.spriteschat.util.CrackedPlayerUtil.getDefaultSkinTexture(alexUuid);
        assertNotNull(alexSkin);
        assertTrue(alexSkin.contains("eyJ0ZXh0dXJlcy"));
    }

    @Test
    public void testCrackedPlayerHeadComponentBuilding() {
        Component headComp = com.kury.spriteschat.util.CrackedPlayerUtil.buildCrackedPlayerHead("OfflineUser99999", true, true, "SUGGEST_COMMAND");
        assertNotNull(headComp);
        assertTrue(headComp instanceof ObjectComponent);

        ObjectComponent obj = (ObjectComponent) headComp;
        assertTrue(obj.contents() instanceof PlayerHeadObjectContents);

        PlayerHeadObjectContents contents = (PlayerHeadObjectContents) obj.contents();
        assertEquals("OfflineUser99999", contents.name());
        assertNotNull(contents.id());
        assertFalse(contents.profileProperties().isEmpty());

        String json = GsonComponentSerializer.gson().serialize(headComp);
        assertTrue(json.contains("\"name\":\"OfflineUser99999\""));
        assertTrue(json.contains("\"textures\""));
    }

    @Test
    public void testIconCatalogSpawnerMaterial() {
        Component spawnerComp = IconCatalog.spriteFor(Material.SPAWNER);
        assertNotNull(spawnerComp);
        assertTrue(spawnerComp instanceof ObjectComponent);

        ObjectComponent obj = (ObjectComponent) spawnerComp;
        SpriteObjectContents contents = (SpriteObjectContents) obj.contents();
        assertEquals(IconCatalog.ATLAS_BLOCKS, contents.atlas());
        assertEquals(Key.key("minecraft", "block/spawner"), contents.sprite());

        String tabTag = IconCatalog.getTabSprite(Material.SPAWNER);
        assertEquals("<sprite:\"minecraft:blocks\":\"minecraft:block/spawner\">", tabTag);
    }

    @Test
    public void testParseUUIDUtility() {
        String trimmed = "069a79f444e94726a5befca90e38aaf5";
        UUID uuid = com.kury.spriteschat.util.CrackedPlayerUtil.parseUUID(trimmed);
        assertNotNull(uuid);
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), uuid);
    }

    @Test
    public void testPremiumPlayerMojangProfileResolution() {
        // Notch là tài khoản Minecraft Premium nổi tiếng nhất với UUID chuẩn 069a79f4-44e9-4726-a5be-fca90e38aaf5
        com.kury.spriteschat.util.CrackedPlayerUtil.PlayerSkinProfile profile =
                com.kury.spriteschat.util.CrackedPlayerUtil.resolvePlayerProfile("Notch");
        assertNotNull(profile, "Profile của Notch không được null");
        assertEquals("Notch", profile.getName());
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), profile.getUuid());
        assertTrue(profile.isPremium(), "Notch phải được nhận diện là tài khoản Premium");
        assertNotNull(profile.getTextureBase64(), "Texture base64 của Notch không được null");
        assertNotNull(profile.getTextureUrl(), "Texture URL của Notch không được null");
        assertTrue(profile.getTextureUrl().contains("textures.minecraft.net/texture/"));
    }

    @Test
    public void testNonExistentPlayerFallbackToCracked() {
        // Tên không tồn tại trên Mojang API
        String fakeName = "NonExistentPlayer99999XYZ";
        com.kury.spriteschat.util.CrackedPlayerUtil.PlayerSkinProfile profile =
                com.kury.spriteschat.util.CrackedPlayerUtil.resolvePlayerProfile(fakeName);
        assertNotNull(profile);
        assertEquals(fakeName, profile.getName());
        assertFalse(profile.isPremium(), "Tài khoản không tồn tại phải fallback về offline crack");
        assertNotNull(profile.getTextureBase64());
    }

    private static org.bukkit.entity.Player createMockPlayer(String name, UUID uuid) {
        return (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.entity.Player.class.getClassLoader(),
                new Class<?>[]{org.bukkit.entity.Player.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return name;
                    if ("getUniqueId".equals(method.getName())) return uuid;
                    if ("hasPermission".equals(method.getName())) return true;
                    if ("isOnline".equals(method.getName())) return true;
                    return null;
                }
        );
    }

    private static org.bukkit.plugin.Plugin createMockPlugin() {
        return (org.bukkit.plugin.Plugin) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.plugin.Plugin.class.getClassLoader(),
                new Class<?>[]{org.bukkit.plugin.Plugin.class},
                (proxy, method, args) -> {
                    if ("getLogger".equals(method.getName())) return java.util.logging.Logger.getGlobal();
                    return null;
                }
        );
    }

    @Test
    public void testClientPlatformUtilBedrockDetection() {
        try {
            UUID bedrockUuid = UUID.randomUUID();
            UUID javaUuid = UUID.randomUUID();

            com.kury.spriteschat.util.ClientPlatformUtil.setBedrockTester(bedrockUuid::equals);

            org.bukkit.entity.Player bedrockPlayer = createMockPlayer("BedrockUser", bedrockUuid);
            org.bukkit.entity.Player javaPlayer = createMockPlayer("JavaUser", javaUuid);

            assertTrue(com.kury.spriteschat.util.ClientPlatformUtil.isBedrock(bedrockPlayer));
            assertFalse(com.kury.spriteschat.util.ClientPlatformUtil.isBedrock(javaPlayer));
            assertTrue(com.kury.spriteschat.util.ClientPlatformUtil.isLegacyOrBedrock(bedrockPlayer, 771));

            // Floodgate prefix check (. hoặc *)
            org.bukkit.entity.Player floodgateDot = createMockPlayer(".DotUser", UUID.randomUUID());
            org.bukkit.entity.Player floodgateStar = createMockPlayer("*StarUser", UUID.randomUUID());
            assertTrue(com.kury.spriteschat.util.ClientPlatformUtil.isBedrock(floodgateDot));
            assertTrue(com.kury.spriteschat.util.ClientPlatformUtil.isBedrock(floodgateStar));
        } finally {
            com.kury.spriteschat.util.ClientPlatformUtil.resetTesters();
        }
    }

    @Test
    public void testClientPlatformUtilProtocolDetection() {
        try {
            UUID oldJavaUuid = UUID.randomUUID();
            UUID modernJavaUuid = UUID.randomUUID();

            com.kury.spriteschat.util.ClientPlatformUtil.setBedrockTester(uuid -> false);
            com.kury.spriteschat.util.ClientPlatformUtil.setProtocolTester(uuid -> {
                if (uuid.equals(oldJavaUuid)) return 769; // 1.21.4 (< 771)
                if (uuid.equals(modernJavaUuid)) return 771; // 1.21.6 (>= 771)
                return -1;
            });

            org.bukkit.entity.Player oldPlayer = createMockPlayer("OldJava", oldJavaUuid);
            org.bukkit.entity.Player modernPlayer = createMockPlayer("ModernJava", modernJavaUuid);

            assertEquals(769, com.kury.spriteschat.util.ClientPlatformUtil.getClientProtocolVersion(oldPlayer));
            assertEquals(771, com.kury.spriteschat.util.ClientPlatformUtil.getClientProtocolVersion(modernPlayer));

            assertTrue(com.kury.spriteschat.util.ClientPlatformUtil.isLegacyOrBedrock(oldPlayer, 771));
            assertFalse(com.kury.spriteschat.util.ClientPlatformUtil.isLegacyOrBedrock(modernPlayer, 771));
        } finally {
            com.kury.spriteschat.util.ClientPlatformUtil.resetTesters();
        }
    }

    @Test
    public void testFormatLegacyItem() {
        org.bukkit.plugin.Plugin mockPlugin = createMockPlugin();
        com.kury.spriteschat.config.ConfigManager configManager = new com.kury.spriteschat.config.ConfigManager(mockPlugin);
        com.kury.spriteschat.manager.SpriteManager spriteManager = new com.kury.spriteschat.manager.SpriteManager(mockPlugin, configManager, null);

        // Mặc định format là ":{item}:"
        String legacyStr = spriteManager.formatLegacyItemString("diamond");
        assertEquals(":diamond:", legacyStr);

        Component legacyComp = spriteManager.formatLegacyItem("diamond", "[Kim Cương]", true, true, "SUGGEST_COMMAND");
        assertNotNull(legacyComp);
        assertFalse(legacyComp instanceof ObjectComponent);
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(legacyComp);
        assertEquals(":diamond:", plain);
    }

    @Test
    public void testConvertObjectComponentsToLegacy() {
        org.bukkit.plugin.Plugin mockPlugin = createMockPlugin();
        com.kury.spriteschat.config.ConfigManager configManager = new com.kury.spriteschat.config.ConfigManager(mockPlugin);
        com.kury.spriteschat.manager.SpriteManager spriteManager = new com.kury.spriteschat.manager.SpriteManager(mockPlugin, configManager, null);

        SpriteItem sprite = new SpriteItem(
                "diamond",
                Key.key("minecraft", "items"),
                Key.key("minecraft", "item/diamond"),
                "<aqua>[Kim Cương]</aqua>",
                "Kim Cương",
                "spriteschat.use"
        );
        Component nativeObj = sprite.toComponent(true, true, "SUGGEST_COMMAND");
        assertTrue(nativeObj instanceof ObjectComponent, "Bản gốc phải là ObjectComponent");

        Component converted = spriteManager.convertObjectComponentsToLegacy(nativeObj, true, true, "SUGGEST_COMMAND");
        assertNotNull(converted);
        assertFalse(converted instanceof ObjectComponent, "Sau khi convert không được là ObjectComponent");
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(converted);
        assertEquals(":diamond:", plain);
    }

    @Test
    public void testLegacyTabTokenFormatting() {
        try {
            UUID bedrockUuid = UUID.randomUUID();
            UUID modernUuid = UUID.randomUUID();

            com.kury.spriteschat.util.ClientPlatformUtil.setBedrockTester(bedrockUuid::equals);
            com.kury.spriteschat.util.ClientPlatformUtil.setProtocolTester(uuid -> uuid.equals(modernUuid) ? 771 : 769);

            org.bukkit.entity.Player bedrockPlayer = createMockPlayer("BedrockUser", bedrockUuid);
            org.bukkit.entity.Player modernPlayer = createMockPlayer("ModernUser", modernUuid);

            org.bukkit.plugin.Plugin mockPlugin = createMockPlugin();
            com.kury.spriteschat.config.ConfigManager configManager = new com.kury.spriteschat.config.ConfigManager(mockPlugin);
            com.kury.spriteschat.manager.SpriteManager spriteManager = new com.kury.spriteschat.manager.SpriteManager(mockPlugin, configManager, null);

            // Modern player nhận <sprite:...>
            String modernTab = spriteManager.getTabTokenString("diamond", modernPlayer);
            assertEquals("<sprite:\"minecraft:items\":\"minecraft:item/diamond\">", modernTab);

            // Bedrock player nhận :diamond:
            String bedrockTab = spriteManager.getTabTokenString("diamond", bedrockPlayer);
            assertEquals(":diamond:", bedrockTab);

            // convertToTabString cho Modern vs Bedrock
            String text = "VIP :diamond: Rank";
            String modernConverted = spriteManager.convertToTabString(text, modernPlayer);
            assertEquals("VIP <sprite:\"minecraft:items\":\"minecraft:item/diamond\"> Rank", modernConverted);

            String bedrockConverted = spriteManager.convertToTabString(text, bedrockPlayer);
            assertEquals("VIP :diamond: Rank", bedrockConverted);
        } finally {
            com.kury.spriteschat.util.ClientPlatformUtil.resetTesters();
        }
    }

    @Test
    public void testLegacyChatRenderingProcessMessage() {
        try {
            UUID bedrockUuid = UUID.randomUUID();
            UUID modernUuid = UUID.randomUUID();

            com.kury.spriteschat.util.ClientPlatformUtil.setBedrockTester(bedrockUuid::equals);
            com.kury.spriteschat.util.ClientPlatformUtil.setProtocolTester(uuid -> uuid.equals(modernUuid) ? 771 : 769);

            org.bukkit.entity.Player bedrockPlayer = createMockPlayer("BedrockUser", bedrockUuid);
            org.bukkit.entity.Player modernPlayer = createMockPlayer("ModernUser", modernUuid);

            org.bukkit.plugin.Plugin mockPlugin = createMockPlugin();
            com.kury.spriteschat.config.ConfigManager configManager = new com.kury.spriteschat.config.ConfigManager(mockPlugin);
            com.kury.spriteschat.manager.SpriteManager spriteManager = new com.kury.spriteschat.manager.SpriteManager(mockPlugin, configManager, null);

            Component chatMsg = Component.text("Khoe đồ :diamond: nhé");

            // Modern viewer: chứa ObjectComponent
            Component modernRendered = spriteManager.processMessage(chatMsg, modernPlayer, modernPlayer);
            boolean hasModernObj = (modernRendered instanceof ObjectComponent)
                    || modernRendered.children().stream().anyMatch(c -> c instanceof ObjectComponent);
            assertTrue(hasModernObj, "Modern Java viewer phải nhận Native ObjectComponent");

            // Bedrock viewer: không chứa ObjectComponent, giữ text :diamond:
            Component bedrockRendered = spriteManager.processMessage(chatMsg, modernPlayer, bedrockPlayer);
            boolean hasBedrockObj = (bedrockRendered instanceof ObjectComponent)
                    || bedrockRendered.children().stream().anyMatch(c -> c instanceof ObjectComponent);
            assertFalse(hasBedrockObj, "Bedrock viewer không được nhận ObjectComponent");

            String bedrockPlain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(bedrockRendered);
            assertEquals("Khoe đồ :diamond: nhé", bedrockPlain);
        } finally {
            com.kury.spriteschat.util.ClientPlatformUtil.resetTesters();
        }
    }
}
