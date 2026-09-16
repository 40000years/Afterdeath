package com.example.voidscape.guide;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class FloodgateGuideForm {
    private FloodgateGuideForm() {}

    public static boolean sendPage(VoidscapePlugin plugin, Player player, int pageIndex) {
        List<GuidePage> pages = GuideData.PAGES;
        if (pageIndex < 0) pageIndex = 0;
        if (pageIndex >= pages.size()) pageIndex = pages.size() - 1;

        GuidePage page = pages.get(pageIndex);
        int finalPageIndex = pageIndex;
        int totalPages = pages.size();

        SimpleForm.Builder builder = SimpleForm.builder();
        builder.title("§1§lคู่มือ Evergarden §8[§9" + (finalPageIndex + 1) + "§8/§9" + totalPages + "§8]");
        // Bedrock simple forms default to light text. Keep ordinary paragraphs
        // dark and reserve bright colours for the deliberately tagged highlights.
        builder.content("§0" + page.content().replace("§r", "§r§0"));

        List<Consumer<Player>> buttonActions = new ArrayList<>();

        // Next page button
        if (finalPageIndex < totalPages - 1) {
            builder.button("➡️ หน้าถัดไป (" + (finalPageIndex + 2) + "/" + totalPages + ")");
            buttonActions.add(p -> BedrockGuideService.openGuide(plugin, p, finalPageIndex + 1));
        }

        // Previous page button
        if (finalPageIndex > 0) {
            builder.button("⬅️ หน้าก่อนหน้า (" + finalPageIndex + "/" + totalPages + ")");
            buttonActions.add(p -> BedrockGuideService.openGuide(plugin, p, finalPageIndex - 1));
        }

        // Table of Contents button
        builder.button("📑 สารบัญหัวข้อ (เลือกหน้า)");
        buttonActions.add(p -> BedrockGuideService.openIndex(plugin, p, finalPageIndex));

        // Close button
        builder.button("❌ ปิดคู่มือ");
        buttonActions.add(p -> {});

        builder.validResultHandler(response -> {
            int buttonId = response.clickedButtonId();
            if (buttonId >= 0 && buttonId < buttonActions.size()) {
                Consumer<Player> action = buttonActions.get(buttonId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    action.accept(player);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
                });
            }
        });

        return FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
    }

    public static boolean sendIndex(VoidscapePlugin plugin, Player player, int returnPageIndex) {
        List<GuidePage> pages = GuideData.PAGES;
        SimpleForm.Builder builder = SimpleForm.builder();
        builder.title("§1§lสารบัญคู่มือ Evergarden");
        builder.content("§0เลือกหัวข้อที่ต้องการอ่านเพื่อเปิดหน้านั้นได้ทันที:\n§8(หน้าที่กำลังอ่านอยู่: §bหน้า " + (returnPageIndex + 1) + "§8)");

        List<Consumer<Player>> buttonActions = new ArrayList<>();

        for (int i = 0; i < pages.size(); i++) {
            int targetPage = i;
            String prefix = (i == returnPageIndex) ? "§6▶ " : "§9";
            builder.button(prefix + (i + 1) + ". " + pages.get(i).title());
            buttonActions.add(p -> BedrockGuideService.openGuide(plugin, p, targetPage));
        }

        builder.button("⬅️ กลับไปหน้าที่อ่านค้างไว้ (หน้า " + (returnPageIndex + 1) + ")");
        buttonActions.add(p -> BedrockGuideService.openGuide(plugin, p, returnPageIndex));

        builder.button("❌ ปิด");
        buttonActions.add(p -> {});

        builder.validResultHandler(response -> {
            int buttonId = response.clickedButtonId();
            if (buttonId >= 0 && buttonId < buttonActions.size()) {
                Consumer<Player> action = buttonActions.get(buttonId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    action.accept(player);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
                });
            }
        });

        return FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
    }
}
