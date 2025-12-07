package ru.tereegor.whitelist.bukkit.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.tereegor.whitelist.bukkit.WhitelistPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {
    
    private final YamlConfiguration messages;
    private final YamlConfiguration colors;
    private final String prefix;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    
    private final Map<String, String> colorMap = new HashMap<>();
    private final Map<String, String> templateMap = new HashMap<>();
    
    public MessageManager(WhitelistPlugin plugin, String language) {
        File colorsFile = new File(plugin.getDataFolder(), "colors.yml");
        if (!colorsFile.exists()) {
            plugin.saveResource("colors.yml", false);
        }
        
        if (colorsFile.exists()) {
            colors = YamlConfiguration.loadConfiguration(colorsFile);
        } else {
            InputStream stream = plugin.getResource("colors.yml");
            if (stream != null) {
                colors = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
            } else {
                colors = new YamlConfiguration();
            }
        }
        
        preprocessColors();
        
        String fileName = "messages_" + language + ".yml";
        File file = new File(plugin.getDataFolder(), fileName);
        
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        
        if (file.exists()) {
            messages = YamlConfiguration.loadConfiguration(file);
        } else {
            InputStream stream = plugin.getResource("messages_ru.yml");
            if (stream != null) {
                messages = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
            } else {
                messages = new YamlConfiguration();
            }
        }
        
        this.prefix = processColors(messages.getString("prefix", "<gray>[<gold>WhitelistTG<gray>] "));
    }
    
    private void preprocessColors() {
        if (colors.contains("colors")) {
            var colorsSection = colors.getConfigurationSection("colors");
            if (colorsSection != null) {
                for (String key : colorsSection.getKeys(false)) {
                    String colorValue = colorsSection.getString(key);
                    if (colorValue != null) {
                        colorMap.put(key, colorValue);
                    }
                }
            }
        }
        
        if (colors.contains("templates")) {
            var templatesSection = colors.getConfigurationSection("templates");
            if (templatesSection != null) {
                for (String key : templatesSection.getKeys(false)) {
                    String template = templatesSection.getString(key);
                    if (template != null) {
                        String processed = replaceColorsOnly(template);
                        templateMap.put(key, processed);
                    }
                }
            }
        }
    }
    
    private String replaceColorsOnly(String text) {
        if (text == null) return "";
        String result = text;
        for (Map.Entry<String, String> entry : colorMap.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return result;
    }
    
    private String processColors(String text) {
        if (text == null) return "";
        
        String result = text;
        for (Map.Entry<String, String> entry : colorMap.entrySet()) {
            String key = entry.getKey();
            result = result.replace("<" + key + ">", entry.getValue());
            result = result.replace("</" + key + ">", "");
        }
        
        for (Map.Entry<String, String> entry : templateMap.entrySet()) {
            String key = entry.getKey();
            result = result.replace("<" + key + ">", entry.getValue());
            result = result.replace("</" + key + ">", "");
        }
        
        return result;
    }
    
    public String getRaw(String key) {
        String message = messages.getString(key, "<red>Missing: " + key);
        return processColors(message);
    }
    
    public Component getComponent(String key) {
        return miniMessage.deserialize(prefix + getRaw(key));
    }
    
    public Component getComponent(String key, TagResolver... resolvers) {
        return miniMessage.deserialize(prefix + getRaw(key), resolvers);
    }
    
    public Component getComponentNoPrefix(String key) {
        return miniMessage.deserialize(getRaw(key));
    }
    
    public Component getComponentNoPrefix(String key, TagResolver... resolvers) {
        return miniMessage.deserialize(getRaw(key), resolvers);
    }
    
    public void send(CommandSender sender, String key) {
        sendMultiline(sender, getComponent(key));
    }
    
    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sendMultiline(sender, getComponent(key, resolvers));
    }
    
    public void sendNoPrefix(CommandSender sender, String key) {
        sendMultiline(sender, getComponentNoPrefix(key));
    }
    
    public void sendNoPrefix(CommandSender sender, String key, TagResolver... resolvers) {
        sendMultiline(sender, getComponentNoPrefix(key, resolvers));
    }
    
    private void sendMultiline(CommandSender sender, Component component) {
        String text = miniMessage.serialize(component);
        if (text.contains("\n") || text.contains("\\n")) {
            text = text.replace("\\n", "\n");
            String[] lines = text.split("\n", -1);
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    sender.sendMessage(miniMessage.deserialize(line));
                }
            }
        } else {
            sender.sendMessage(component);
        }
    }
    
    public void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(miniMessage.deserialize(prefix + processColors(message)));
    }
    
    public static TagResolver placeholders(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length - 1; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return TagResolver.resolver(
                map.entrySet().stream()
                        .map(e -> Placeholder.parsed(e.getKey(), e.getValue()))
                        .toArray(TagResolver[]::new)
        );
    }
    
    public static TagResolver placeholder(String key, String value) {
        return Placeholder.parsed(key, value);
    }
    
    public Component processAndDeserialize(String text, TagResolver... resolvers) {
        String processed = processColors(text);
        return miniMessage.deserialize(processed, resolvers);
    }
    
    public String getRawTelegram(String key, TagResolver... resolvers) {
        String raw = getRaw(key);
        if (resolvers.length > 0) {
            Component component = miniMessage.deserialize(raw, resolvers);
            raw = miniMessage.serialize(component);
        }
        return convertMiniMessageToHtml(raw);
    }
    
    private String convertMiniMessageToHtml(String text) {
        if (text == null) return "";
        String result = text;
        result = result.replace("<strong>", "<b>").replace("</strong>", "</b>");
        result = result.replace("<em>", "<i>").replace("</em>", "</i>");
        result = result.replaceAll("&lt;code&gt;", "<code>").replaceAll("&lt;/code&gt;", "</code>");
        result = result.replaceAll("&lt;b&gt;", "<b>").replaceAll("&lt;/b&gt;", "</b>");
        result = result.replaceAll("&lt;i&gt;", "<i>").replaceAll("&lt;/i&gt;", "</i>");
        result = result.replaceAll("<[^>/]+>", "");
        result = result.replace("&amp;", "&");
        return result;
    }
}
