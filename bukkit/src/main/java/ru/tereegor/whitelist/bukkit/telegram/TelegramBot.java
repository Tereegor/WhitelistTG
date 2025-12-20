package ru.tereegor.whitelist.bukkit.telegram;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.tereegor.whitelist.bukkit.WhitelistPlugin;
import ru.tereegor.whitelist.bukkit.manager.MessageManager;
import ru.tereegor.whitelist.common.model.PlayerLink;
import ru.tereegor.whitelist.common.model.RegistrationCode;

import java.util.List;
import java.util.Optional;

import static ru.tereegor.whitelist.bukkit.manager.MessageManager.placeholder;

public class TelegramBot extends TelegramLongPollingBot {

    private final WhitelistPlugin plugin;
    private final MessageManager messageManager;
    private final String botUsername;
    private DefaultBotSession botSession;

    public TelegramBot(WhitelistPlugin plugin) {
        super(plugin.getPluginConfig().getTelegramToken());
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
        this.botUsername = plugin.getPluginConfig().getTelegramUsername();
    }

    public void start() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            this.botSession = (DefaultBotSession) botsApi.registerBot(this);
            log("Telegram bot started successfully!");
        } catch (TelegramApiException e) {
            logError("Failed to start Telegram bot: " + e.getMessage());
            logError("Check your telegram.token and telegram.username in config.yml");
            debugPrint(e);
        } catch (Exception e) {
            logError("Unexpected error starting Telegram bot: " + e.getMessage());
            debugPrint(e);
        }
    }

    public void stop() {
        if (botSession != null && botSession.isRunning()) {
            try {
                botSession.stop();
                log("Telegram bot stopped successfully!");
            } catch (Exception e) {
                plugin.getLogger().warning("Error stopping Telegram bot: " + e.getMessage());
                debugPrint(e);
            }
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update);
            } else if (update.hasCallbackQuery()) {
                handleCallback(update);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing Telegram update: " + e.getMessage());
            debugPrint(e);
        }
    }

    private void handleMessage(Update update) {
        String text = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        String username = update.getMessage().getFrom().getUserName();
        Long userId = update.getMessage().getFrom().getId();

        if (text.startsWith("/start")) {
            sendRulesMessage(chatId);
        } else if (text.startsWith("/code")) {
            handleCodeCommand(chatId, userId, username);
        } else if (text.startsWith("/help")) {
            sendHelpMessage(chatId);
        }
    }

    private void handleCallback(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();
        String username = update.getCallbackQuery().getFrom().getUserName();

        if ("accept_rules".equals(callbackData)) {
            generateAndSendCode(chatId, userId, username);
        }
    }

    private void sendRulesMessage(Long chatId) {
        String rules = plugin.getPluginConfig().getRules();

        InlineKeyboardButton acceptButton = new InlineKeyboardButton();
        acceptButton.setText(messageManager.getRaw("telegram.accept-button"));
        acceptButton.setCallbackData("accept_rules");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(acceptButton)));

        sendHtmlMessage(chatId, rules, markup);
    }

    private void generateAndSendCode(Long chatId, Long userId, String username) {
        plugin.getWhitelistManager().getLinkByTelegram(userId)
                .thenCompose(optLink -> {
                    if (optLink.isPresent()) {
                        sendAlreadyLinkedMessage(chatId, optLink.get());
                        return java.util.concurrent.CompletableFuture.completedFuture(Optional.<RegistrationCode>empty());
                    }
                    return checkExistingCodeOrGenerate(chatId, userId, username);
                })
                .exceptionally(e -> {
                    sendText(chatId, messageManager.getRaw("telegram.code-generation-error"));
                    logError("Code generation error: " + e.getMessage());
                    debugPrint(e);
                    return Optional.empty();
                });
    }
    
    private java.util.concurrent.CompletableFuture<Optional<RegistrationCode>> checkExistingCodeOrGenerate(
            Long chatId, Long userId, String username) {
        
        return plugin.getWhitelistManager().getActiveCode(userId)
                .thenCompose(optCode -> {
                    if (optCode.isPresent()) {
                        sendCodeMessage(chatId, optCode.get().getCode());
                        return java.util.concurrent.CompletableFuture.completedFuture(optCode);
                    }
                    return generateNewCode(chatId, userId, username);
                });
    }
    
    private java.util.concurrent.CompletableFuture<Optional<RegistrationCode>> generateNewCode(
            Long chatId, Long userId, String username) {
        
        return plugin.getWhitelistManager().generateCode(userId, username)
                .thenApply(code -> {
                    debug("Generated code: %s for telegramId: %d, expires: %s"
                            .formatted(code.getCode(), userId, code.getExpiresAt()));
                    sendCodeMessage(chatId, code.getCode());
                    return Optional.of(code);
                });
    }
    
    private void sendAlreadyLinkedMessage(Long chatId, PlayerLink link) {
        String linkedMessage = messageManager.getRawTelegram("telegram.already-linked",
                placeholder("player", link.getPlayerName()));
        sendText(chatId, linkedMessage);
    }

    private void sendCodeMessage(Long chatId, String code) {
        String message = messageManager.getRawTelegram("telegram.code-message",
                placeholder("code", code),
                placeholder("minutes", String.valueOf(plugin.getPluginConfig().getCodeExpirationMinutes())));

        sendHtmlMessage(chatId, message, null);
    }

    private void handleCodeCommand(Long chatId, Long userId, String username) {
        plugin.getWhitelistManager().getLinkByTelegram(userId)
                .thenAccept(optLink -> {
                    if (optLink.isPresent()) {
                        sendAlreadyLinkedMessage(chatId, optLink.get());
                        return;
                    }
                    sendExistingOrNoCode(chatId, userId);
                });
    }
    
    private void sendExistingOrNoCode(Long chatId, Long userId) {
        plugin.getWhitelistManager().getActiveCode(userId)
                .thenAccept(optCode -> {
                    if (optCode.isPresent()) {
                        sendCodeMessage(chatId, optCode.get().getCode());
                    } else {
                        sendText(chatId, messageManager.getRaw("telegram.no-active-code"));
                    }
                });
    }

    private void sendHelpMessage(Long chatId) {
        String help = messageManager.getRawTelegram("telegram.help-message",
                placeholder("server", plugin.getPluginConfig().getServerDisplayName().replace("&", "")));
        sendText(chatId, help);
    }

    private void sendHtmlMessage(Long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("HTML");
        if (markup != null) {
            message.setReplyMarkup(markup);
        }

        try {
            execute(message);
        } catch (TelegramApiException e) {
            plugin.getLogger().warning("Failed to send HTML message: " + e.getMessage());
            debugPrint(e);
        }
    }

    private void sendText(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            plugin.getLogger().warning("Failed to send message: " + e.getMessage());
        }
    }
    
    private void log(String message) {
        plugin.getLogger().info(message);
    }
    
    private void logError(String message) {
        plugin.getLogger().severe(message);
    }
    
    private void debug(String message) {
        if (plugin.getPluginConfig().isDebug()) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }
    
    private void debugPrint(Throwable e) {
        if (plugin.getPluginConfig().isDebug()) {
            e.printStackTrace();
        }
    }
}
