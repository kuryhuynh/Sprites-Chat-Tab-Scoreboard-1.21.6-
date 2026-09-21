package com.kury.spriteschat.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kury.spriteschat.model.HeadItem;
import com.kury.spriteschat.util.ImagePixelUtil;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Service giao tiếp với https://minecraft-heads.com/scripts/api.php
 * Tải texture skin từ Mojang và trích xuất mặt 8x8 để hiển thị chat icon / resource pack.
 */
public class MinecraftHeadsService {

    private final Plugin plugin;
    private final File cacheDir;
    private final String apiUrl;
    private final String userAgent;

    // Bộ nhớ đệm tạm thời cho kết quả tìm kiếm category
    private final Map<String, List<JsonObject>> categoryCache = new ConcurrentHashMap<>();

    public MinecraftHeadsService(Plugin plugin, String apiUrl, String userAgent, File cacheDir) {
        this.plugin = plugin;
        this.apiUrl = apiUrl != null ? apiUrl : "https://minecraft-heads.com/scripts/api.php";
        this.userAgent = userAgent != null ? userAgent : "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SpritesChat/1.0";
        this.cacheDir = cacheDir;

        if (!this.cacheDir.exists()) {
            this.cacheDir.mkdirs();
        }
    }

    /**
     * Nạp dữ liệu ảnh khuôn mặt cho HeadItem (từ cache file hoặc tải từ Mojang qua URL).
     */
    public boolean loadOrDownloadFace(HeadItem head) {
        if (head == null) return false;

        String safeKey = head.getKey().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        File cachedPng = new File(cacheDir, safeKey + ".png");

        // 1. Kiểm tra cache file PNG đã tồn tại chưa
        if (cachedPng.exists()) {
            try {
                BufferedImage face = ImageIO.read(cachedPng);
                if (face != null) {
                    head.setFaceImage(face);
                    return true;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Lỗi đọc cache ảnh head " + head.getKey() + ": " + e.getMessage());
            }
        }

        // 2. Nếu chưa có textureUrl, giải mã từ Base64 value
        if (head.getTextureUrl() == null || head.getTextureUrl().isEmpty()) {
            if (head.getValue() != null && !head.getValue().isEmpty()) {
                String textureUrl = extractTextureUrlFromBase64(head.getValue());
                if (textureUrl != null) {
                    head.setTextureUrl(textureUrl);
                }
            }
        }

        // 3. Tải skin từ Mojang CDN nếu có URL
        if (head.getTextureUrl() != null && !head.getTextureUrl().isEmpty()) {
            try {
                BufferedImage skin = downloadImage(head.getTextureUrl());
                if (skin != null) {
                    BufferedImage face = ImagePixelUtil.extractFace(skin);
                    head.setFaceImage(face);
                    // Lưu vào cache
                    ImagePixelUtil.savePng(face, cachedPng);
                    return true;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Không thể tải skin cho head " + head.getKey() + " từ " + head.getTextureUrl(), e);
            }
        }

        return false;
    }

    /**
     * Tìm kiếm và tải head từ minecraft-heads.com API theo từ khóa tìm kiếm (bất đồng bộ).
     */
    public CompletableFuture<HeadItem> fetchHeadFromApi(String query, String category) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<JsonObject> heads = fetchCategoryHeads(category != null ? category : "miscellaneous");
                if (heads == null || heads.isEmpty()) return null;

                JsonObject matched = null;
                String lowerQuery = query.toLowerCase();

                // 1. Tìm kiếm chính xác tên
                for (JsonObject item : heads) {
                    JsonElement nameElem = item.get("name");
                    if (nameElem != null && nameElem.getAsString().equalsIgnoreCase(query)) {
                        matched = item;
                        break;
                    }
                }

                // 2. Nếu không thấy, tìm kiếm chứa từ khóa
                if (matched == null) {
                    for (JsonObject item : heads) {
                        JsonElement nameElem = item.get("name");
                        if (nameElem != null && nameElem.getAsString().toLowerCase().contains(lowerQuery)) {
                            matched = item;
                            break;
                        }
                    }
                }

                if (matched == null) return null;

                String name = matched.get("name").getAsString();
                String uuidStr = matched.has("uuid") ? matched.get("uuid").getAsString() : null;
                UUID uuid = uuidStr != null ? UUID.fromString(uuidStr) : UUID.randomUUID();
                String value = matched.has("value") ? matched.get("value").getAsString() : "";
                String textureUrl = extractTextureUrlFromBase64(value);

                HeadItem headItem = new HeadItem(
                        query,
                        name,
                        uuid,
                        textureUrl,
                        value,
                        "<gold>[" + name + "]</gold>",
                        "Head lấy từ minecraft-heads.com",
                        category,
                        "spriteschat.use.heads"
                );

                loadOrDownloadFace(headItem);
                return headItem;

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Lỗi khi tìm kiếm head " + query + " từ API: " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Tải danh sách heads của một danh mục từ API v1.0.
     */
    public List<JsonObject> fetchCategoryHeads(String category) throws IOException {
        if (categoryCache.containsKey(category)) {
            return categoryCache.get(category);
        }

        String targetUrl = apiUrl + "?cat=" + category + "&tags=true";
        String jsonResponse = sendHttpGet(targetUrl);

        List<JsonObject> list = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(jsonResponse);
            if (root.isJsonArray()) {
                JsonArray array = root.getAsJsonArray();
                for (JsonElement elem : array) {
                    if (elem.isJsonObject()) {
                        list.add(elem.getAsJsonObject());
                    }
                }
            }
            categoryCache.put(category, list);
        } catch (Exception e) {
            plugin.getLogger().warning("Lỗi parse JSON danh mục " + category + ": " + e.getMessage());
        }

        return list;
    }

    /**
     * Giải mã chuỗi Base64 skin value của Minecraft để lấy URL texture của Mojang.
     */
    public static String extractTextureUrlFromBase64(String base64Value) {
        if (base64Value == null || base64Value.isEmpty()) return null;

        try {
            byte[] decoded = Base64.getDecoder().decode(base64Value);
            String jsonStr = new String(decoded, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            JsonObject textures = obj.getAsJsonObject("textures");
            if (textures != null && textures.has("SKIN")) {
                JsonObject skin = textures.getAsJsonObject("SKIN");
                if (skin != null && skin.has("url")) {
                    return skin.get("url").getAsString();
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Tải ảnh BufferedImage từ URL.
     */
    private BufferedImage downloadImage(String imageUrl) throws IOException {
        URL url = URI.create(imageUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP code " + responseCode + " khi tải ảnh từ " + imageUrl);
        }

        try (InputStream in = conn.getInputStream()) {
            return ImageIO.read(in);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Gửi HTTP GET request nhận chuỗi văn bản UTF-8.
     */
    private String sendHttpGet(String urlStr) throws IOException {
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP code " + responseCode + " từ API " + urlStr);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } finally {
            conn.disconnect();
        }

        return response.toString();
    }

    public File getCacheDir() {
        return cacheDir;
    }
}
