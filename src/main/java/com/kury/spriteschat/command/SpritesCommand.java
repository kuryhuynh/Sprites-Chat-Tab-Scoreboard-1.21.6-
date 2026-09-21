package com.kury.spriteschat.command;

import com.kury.spriteschat.SpritesChatPlugin;
import com.kury.spriteschat.api.MinecraftHeadsService;
import com.kury.spriteschat.config.ConfigManager;
import com.kury.spriteschat.manager.SpriteManager;
import com.kury.spriteschat.model.HeadItem;
import com.kury.spriteschat.model.SpriteItem;
import com.kury.spriteschat.util.CrackedPlayerUtil;
import com.kury.spriteschat.util.FoliaSchedulerUtil;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Lệnh SpritesCommand hỗ trợ cả Paper BasicCommand lẫn Bukkit CommandExecutor / TabCompleter
 * giúp tương thích hoàn toàn với PlugMan, Bukkit, Paper và Folia.
 */
public class SpritesCommand implements BasicCommand, CommandExecutor, TabCompleter {

    private final SpritesChatPlugin plugin;
    private final ConfigManager configManager;
    private final SpriteManager spriteManager;
    private final MinecraftHeadsService headsService;

    public SpritesCommand(SpritesChatPlugin plugin, ConfigManager configManager,
                          SpriteManager spriteManager, MinecraftHeadsService headsService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.spriteManager = spriteManager;
        this.headsService = headsService;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list" -> handleList(sender, args);
            case "reload" -> handleReload(sender);
            case "head" -> handleHeadFetch(sender, args);
            default -> sendHelp(sender);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gradient:#38ef7d:#11998e><b>[ SpritesChat v1.0.0 (Folia 1.21.11) ]</b></gradient><newline>" +
                "<gray>»</gray> <yellow>/sprites list [trang]</yellow> <dark_gray>-</dark_gray> <white>Xem danh sách sprites & heads</white><newline>" +
                "<gray>»</gray> <yellow>/sprites reload</yellow> <dark_gray>-</dark_gray> <white>Tải lại plugin và cấu hình</white><newline>" +
                "<gray>»</gray> <yellow>/sprites head <tên> [category]</yellow> <dark_gray>-</dark_gray> <white>Tải head từ minecraft-heads.com</white>"
        ));
    }

    private void handleList(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length > 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {}
        }

        List<Component> items = new ArrayList<>();

        // Thêm Sprites
        for (SpriteItem sprite : spriteManager.getAllSprites()) {
            Component preview = sprite.toComponent(true, true, "SUGGEST_COMMAND");
            items.add(Component.text("  ")
                    .append(preview)
                    .append(Component.text(" §f:" + sprite.getKey() + ": ")
                    .append(Component.text("§7- " + sprite.getDisplayName()))));
        }

        // Thêm Heads
        for (HeadItem head : spriteManager.getAllHeads()) {
            Component preview = head.toComponent(true, true, "SUGGEST_COMMAND");
            items.add(Component.text("  ")
                    .append(preview)
                    .append(Component.text(" §f:" + head.getKey() + ": ")
                    .append(Component.text("§7- " + head.getDisplayName()))));
        }

        int pageSize = 8;
        int totalPages = (int) Math.ceil((double) items.size() / pageSize);
        if (totalPages == 0) totalPages = 1;
        if (page > totalPages) page = totalPages;

        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gradient:#38ef7d:#11998e><b>=== [ Danh Sách Sprites & Heads (" + page + "/" + totalPages + ") ] ===</b></gradient>"
        ));

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, items.size());
        for (int i = start; i < end; i++) {
            sender.sendMessage(items.get(i));
        }

        Component nav = Component.text("§7Trang: ");
        if (page > 1) {
            nav = nav.append(Component.text("§e[« Trang trước] ")
                    .clickEvent(ClickEvent.runCommand("/sprites list " + (page - 1)))
                    .hoverEvent(HoverEvent.showText(Component.text("§aXem trang " + (page - 1)))));
        }
        if (page < totalPages) {
            nav = nav.append(Component.text("§e[Trang sau »]")
                    .clickEvent(ClickEvent.runCommand("/sprites list " + (page + 1)))
                    .hoverEvent(HoverEvent.showText(Component.text("§aXem trang " + (page + 1)))));
        }
        sender.sendMessage(nav);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("spriteschat.admin")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    configManager.getConfig().getString("messages.no-permission", "<red>Không có quyền!</red>")
            ));
            return;
        }

        configManager.loadConfigurations();
        spriteManager.reload();
        plugin.syncAllPlayerCompletions();

        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                configManager.getConfig().getString("messages.reloaded", "<green>Đã reload thành công!</green>")
        ));
    }

    private void handleHeadFetch(CommandSender sender, String[] args) {
        if (!sender.hasPermission("spriteschat.admin")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    configManager.getConfig().getString("messages.no-permission", "<red>Không có quyền!</red>")
            ));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("§cCú pháp: /sprites head <tên> [category]"));
            return;
        }

        String headName = args[1];
        String category = args.length > 2 ? args[2] : "miscellaneous";

        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                configManager.getConfig().getString("messages.head-fetching", "<yellow>Đang tải head {name}...</yellow>")
                        .replace("{name}", headName)
        ));

        FoliaSchedulerUtil.runAsync(plugin, () -> {
            // 1. Kiểm tra xem có phải tài khoản Minecraft Premium ngoài server hoặc người chơi có skin thực không
            CrackedPlayerUtil.PlayerSkinProfile playerProfile = CrackedPlayerUtil.resolvePlayerProfile(headName);
            if (playerProfile != null && playerProfile.isPremium()) {
                HeadItem headItem = new HeadItem(
                        headName.toLowerCase(Locale.ROOT),
                        playerProfile.getName(),
                        playerProfile.getUuid(),
                        playerProfile.getTextureUrl(),
                        playerProfile.getTextureBase64(),
                        "<gold>[" + playerProfile.getName() + "]</gold>",
                        "Head lấy từ Mojang (Tài khoản Premium)",
                        category,
                        "spriteschat.use.heads"
                );
                spriteManager.addHead(headItem);
                String msg = configManager.getConfig().getString("messages.head-fetched", "<green>Đã tải xong head {name} (Tài khoản Mojang Premium)!</green>")
                        .replace("{name}", playerProfile.getName());
                sender.sendMessage(MiniMessage.miniMessage().deserialize(msg));

                // Gửi thử nghiệm preview
                Component preview = headItem.toComponent(true, true, "SUGGEST_COMMAND");
                sender.sendMessage(Component.text("§7Xem trước: ").append(preview).append(Component.text(" §f:" + headItem.getKey() + ":")));
                return;
            }

            // 2. Fallback sang minecraft-heads.com API
            headsService.fetchHeadFromApi(headName, category).thenAccept(headItem -> {
                if (headItem != null) {
                    spriteManager.addHead(headItem);
                    String msg = configManager.getConfig().getString("messages.head-fetched", "<green>Đã tải xong head {name}!</green>")
                            .replace("{name}", headItem.getName());
                    sender.sendMessage(MiniMessage.miniMessage().deserialize(msg));

                    // Gửi thử nghiệm preview
                    Component preview = headItem.toComponent(true, true, "SUGGEST_COMMAND");
                    sender.sendMessage(Component.text("§7Xem trước: ").append(preview).append(Component.text(" §f:" + headItem.getKey() + ":")));
                } else {
                    String msg = configManager.getConfig().getString("messages.head-failed", "<red>Không tìm thấy head {name}!</red>")
                            .replace("{name}", headName);
                    sender.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                }
            });
        });
    }

    @Override
    public Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        if (args.length == 0 || args.length == 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase() : "";
            List<String> subs = Arrays.asList("list", "reload", "head", "help");
            return subs.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("head")) {
            return Arrays.asList("Vietnam", "Earth", "Cat", "Sword", "Heart").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("head")) {
            return Arrays.asList("alphabet", "animals", "blocks", "decoration", "food-drinks", "humans", "humanoid", "miscellaneous", "monsters", "plants")
                    .stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public String permission() {
        return "spriteschat.use";
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> handleList(sender, args);
            case "reload" -> handleReload(sender);
            case "head" -> handleHeadFetch(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 0 || args.length == 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            List<String> subs = Arrays.asList("list", "reload", "head", "help");
            return subs.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("head")) {
            return Arrays.asList("Vietnam", "Earth", "Cat", "Sword", "Heart").stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("head")) {
            return Arrays.asList("alphabet", "animals", "blocks", "decoration", "food-drinks", "humans", "humanoid", "miscellaneous", "monsters", "plants")
                    .stream().filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
