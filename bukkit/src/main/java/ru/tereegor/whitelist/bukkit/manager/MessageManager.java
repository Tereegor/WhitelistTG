package ru.tereegor.whitelist.bukkit.manager;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageManager {
    
    private static final Pattern CODE_PATTERN = Pattern.compile("\\b([A-Z0-9]{3}-[A-Z0-9]{3})\\b");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    
    private final YamlConfiguration messages;
    @Getter private final String prefix;
    private final Map<String, String> colorMap = new HashMap<>();
    private final Map<String, String> templateMap = new HashMap<>();
    
    public MessageManager(WhitelistPlugin plugin, String language) {
        YamlConfiguration colors = loadConfig(plugin, "colors.yml");
        preprocessColors(colors);
        
        String fileName = "messages_" + language + ".yml";
        this.messages = loadConfig(plugin, fileName, "messages_ru.yml");
        this.prefix = processColors(messages.getString("prefix", "<gray>[<gold>WhitelistTG<gray>] "));
    }
    
    private YamlConfiguration loadConfig(WhitelistPlugin plugin, String fileName) {
        return loadConfig(plugin, fileName, fileName);
    }
    
    private YamlConfiguration loadConfig(WhitelistPlugin plugin, String fileName, String fallback) {
        File file = new File(plugin.getDataFolder(), fileName);
        
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }
        
        InputStream stream = plugin.getResource(fallback);
        return stream != null 
                ? YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8))
                : new YamlConfiguration();
    }
    
    private void preprocessColors(YamlConfiguration colors) {
        loadSection(colors, "colors", colorMap);
        
        var templatesSection = colors.getConfigurationSection("templates");
        if (templatesSection != null) {
            templatesSection.getKeys(false).forEach(key -> {
                String template = templatesSection.getString(key);
                if (template != null) {
                    templateMap.put(key, replaceColors(template));
                }
            });
        }
    }
    
    private void loadSection(YamlConfiguration config, String section, Map<String, String> target) {
        var configSection = config.getConfigurationSection(section);
        if (configSection != null) {
            configSection.getKeys(false).forEach(key -> {
                String value = configSection.getString(key);
                if (value != null) {
                    target.put(key, value);
                }
            });
        }
    }
    
    private String replaceColors(String text) {
        if (text == null) return "";
        String result = text;
        for (var entry : colorMap.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return result;
    }
    
    private String processColors(String text) {
        if (text == null) return "";
        
        String result = text;
        for (var entry : colorMap.entrySet()) {
            String key = entry.getKey();
            result = result.replace("<" + key + ">", entry.getValue())
                           .replace("</" + key + ">", "");
        }
        
        for (var entry : templateMap.entrySet()) {
            String key = entry.getKey();
            result = result.replace("<" + key + ">", entry.getValue())
                           .replace("</" + key + ">", "");
        }
        
        return result;
    }
    
    public String getRaw(String key) {
        return processColors(messages.getString(key, "<red>Missing: " + key));
    }
    
    public Component getComponent(String key) {
        return MINI_MESSAGE.deserialize(prefix + getRaw(key));
    }
    
    public Component getComponent(String key, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(prefix + getRaw(key), resolvers);
    }
    
    public Component getComponentNoPrefix(String key) {
        return MINI_MESSAGE.deserialize(getRaw(key));
    }
    
    public Component getComponentNoPrefix(String key, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(getRaw(key), resolvers);
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
        String text = MINI_MESSAGE.serialize(component);
        
        if (!text.contains("\n") && !text.contains("\\n")) {
            sender.sendMessage(component);
            return;
        }
        
        text.replace("\\n", "\n").lines()
                .filter(line -> !line.trim().isEmpty())
                .map(MINI_MESSAGE::deserialize)
                .forEach(sender::sendMessage);
    }
    
    public void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(MINI_MESSAGE.deserialize(prefix + processColors(message)));
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
        return MINI_MESSAGE.deserialize(processColors(text), resolvers);
    }
    
    public String getRawTelegram(String key, TagResolver... resolvers) {
        String processed = getRaw(key);
        
        Component component = resolvers.length > 0 
                ? MINI_MESSAGE.deserialize(processed, resolvers)
                : MINI_MESSAGE.deserialize(processed);
        
        String plainText = componentToPlainText(component);
        
        return "telegram.code-message".equals(key) 
                ? formatCodeMessageForTelegram(plainText)
                : escapeHtml(plainText);
    }
    
    private String formatCodeMessageForTelegram(String plainText) {
        if (plainText == null) return "";
        
        Matcher matcher = CODE_PATTERN.matcher(plainText);
        Map<String, String> codeMarkers = new HashMap<>();
        StringBuffer sb = new StringBuffer();
        int index = 0;
        
        while (matcher.find()) {
            String code = matcher.group(1);
            String marker = "\uE000" + index++;
            codeMarkers.put(marker, code);
            matcher.appendReplacement(sb, marker);
        }
        matcher.appendTail(sb);
        
        String result = escapeHtml(sb.toString());
        
        for (var entry : codeMarkers.entrySet()) {
            result = result.replace(entry.getKey(), "<code>" + escapeHtml(entry.getValue()) + "</code>");
        }
        
        return result;
    }
    
    private String componentToPlainText(Component component) {
        if (component == null) return "";
        
        StringBuilder builder = new StringBuilder();
        buildText(component, builder);
        return builder.toString();
    }
    
    private void buildText(Component component, StringBuilder builder) {
        if (component instanceof TextComponent textComponent) {
            String content = textComponent.content();
            if (content != null && !content.isEmpty()) {
                builder.append(content);
            }
        }
        
        component.children().forEach(child -> buildText(child, builder));
    }
    
    private String escapeHtml(String text) {
        return text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
