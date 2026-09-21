package com.kury.spriteschat.listener;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.kury.spriteschat.SpritesChatPlugin;
import com.kury.spriteschat.config.ConfigManager;
import com.kury.spriteschat.manager.SpriteManager;
import com.kury.spriteschat.util.CrackedPlayerUtil;
import com.kury.spriteschat.util.FoliaSchedulerUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Collection;
import java.util.List;

/**
 * Lắng nghe AsyncChatEvent của Paper/Folia để thay thế icon sprites và player heads,
 * đồng thời cung cấp gợi ý chat khi gõ ":" và xử lý Tab Complete.
 */
public class ChatListener implements Listener {

    private final SpritesChatPlugin plugin;
    private final ConfigManager configManager;
    private final SpriteManager spriteManager;

    public ChatListener(SpritesChatPlugin plugin, ConfigManager configManager, SpriteManager spriteManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.spriteManager = spriteManager;
    }

    /**
     * Đồng bộ danh sách gợi ý Chat Completions (ClientboundCustomChatCompletionsPacket)
     * cho người chơi khi tham gia server. Khi người chơi gõ ":", client sẽ lập tức hiện gợi ý.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 1. Lưu skin và UUID của người chơi (hỗ trợ player crack / SkinsRestorer) vào bộ nhớ đệm
        CrackedPlayerUtil.updatePlayerCache(player);

        // 2. Gửi danh sách gợi ý emote nếu tính năng được bật
        if (!configManager.isSuggestionsEnabled() || !configManager.isCustomCompletionsEnabled()) {
            return;
        }
        // Delay 5 ticks để client hoàn tất quá trình đăng nhập và nạp giao diện chat
        FoliaSchedulerUtil.runEntity(plugin, player, () -> {
            if (player.isOnline()) {
                Collection<String> tokens = spriteManager.getAllEmoteTokens(player);
                player.setCustomChatCompletions(tokens);
            }
        }, 5L);
    }

    /**
     * Xử lý gợi ý khi người chơi nhấn phím Tab trong lúc chat hoặc gõ lệnh nhắn tin (/msg, /tell...).
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (!configManager.isSuggestionsEnabled() || !configManager.isTabCompleteEnabled()) {
            return;
        }

        String buffer = event.getBuffer();
        if (buffer == null || buffer.isEmpty()) {
            return;
        }

        // Tìm từ cuối cùng đang gõ (sau khoảng trắng cuối cùng)
        int lastSpace = buffer.lastIndexOf(' ');
        String currentWord = (lastSpace == -1) ? buffer : buffer.substring(lastSpace + 1);

        // Chỉ xử lý nếu từ hiện tại bắt đầu bằng ":" và chưa hoàn tất
        if (!currentWord.startsWith(":")) {
            return;
        }

        // Nếu đã gõ hoàn chỉnh ":token:" thì không cần gợi ý thêm
        if (currentWord.length() > 2 && currentWord.endsWith(":")) {
            return;
        }

        List<AsyncTabCompleteEvent.Completion> completions = spriteManager.getTabCompletions(event.getSender(), currentWord);
        if (!completions.isEmpty()) {
            event.completions(completions);
            event.setHandled(true);
        }
    }

    /**
     * Mức LOW: Xử lý trước cho event.message() nếu có plugin cần đọc trực tiếp message.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAsyncChatEarly(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Component originalMessage = event.message();
        Component modifiedMessage = spriteManager.processMessage(originalMessage, player);
        event.message(modifiedMessage);
    }

    /**
     * Mức MONITOR: Bọc ChatRenderer cuối cùng của server (Canvas, Folia, Paper).
     * Ngăn chặn hoàn toàn việc các plugin format chat (LPC, EssentialsChat, Canvas)
     * làm phẳng (flatten) ObjectComponent thành text dạng [item/...@items].
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChatRenderer(AsyncChatEvent event) {
        io.papermc.paper.chat.ChatRenderer currentRenderer = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component rendered = currentRenderer.render(source, sourceDisplayName, message, viewer);
            return spriteManager.processMessage(rendered, source, viewer);
        });
    }
}
