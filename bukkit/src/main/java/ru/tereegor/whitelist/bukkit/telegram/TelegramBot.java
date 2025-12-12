package ru.tereegor.whitelist.bukkit.telegram;

import java.util.ArrayList;
import java.util.List;

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
import ru.tereegor.whitelist.common.model.RegistrationCode;

public class TelegramBot extends TelegramLongPollingBot {

    private final WhitelistPlugin plugin;
    private final MessageManager messageManager;
    private final String botUsername;
    private TelegramBotsApi botsApi;
    private DefaultBotSession botSession;

    public TelegramBot(WhitelistPlugin plugin) {
        super(plugin.getPluginConfig().getTelegramToken());
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
        this.botUsername = plugin.getPluginConfig().getTelegramUsername();
    }

    public void start() {
        try {
            this.botsApi = new TelegramBotsApi(DefaultBotSession.class);
            this.botSession = (DefaultBotSession) botsApi.registerBot(this);
            plugin.getLogger().info("Telegram bot started successfully!");
        } catch (TelegramApiException e) {
            plugin.getLogger().severe("Failed to start Telegram bot: " + e.getMessage());
            if (plugin.getPluginConfig().isDebug()) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        try {
            if (botSession != null && botSession.isRunning()) {
                botSession.stop();
                plugin.getLogger().info("Telegram bot stopped successfully!");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error stopping Telegram bot: " + e.getMessage());
            if (plugin.getPluginConfig().isDebug()) {
                e.printStackTrace();
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
            if (plugin.getPluginConfig().isDebug()) {
                e.printStackTrace();
            }
        }
    }

    private void handleMessage(Update update) {
        String text = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        String username = update.getMessage().getFrom().getUserName();

        if (text.startsWith("/start")) {
            sendRulesMessage(chatId);
        } else if (text.startsWith("/code")) {
            sendExistingCode(chatId, username);
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

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(acceptButton);

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(rules);
        message.setParseMode("HTML");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            plugin.getLogger().warning("Failed to send rules message: " + e.getMessage());
        }
    }

    private void generateAndSendCode(Long chatId, Long userId, String username) {
        plugin.getWhitelistManager().getLinkByTelegram(userId).thenAccept(optLink -> {
            if (optLink.isPresent()) {
                String linkedMessage = messageManager.getRawTelegram("telegram.already-linked",
                        MessageManager.placeholder("player", optLink.get().getPlayerName()));
                sendText(chatId, linkedMessage);
                return;
            }

            plugin.getWhitelistManager().getActiveCode(userId).thenAccept(optCode -> {
                if (optCode.isPresent()) {
                    RegistrationCode existingCode = optCode.get();
                    sendCodeMessage(chatId, existingCode.getCode());
                    return;
                }

                plugin.getWhitelistManager().generateCode(userId, username).thenAccept(code -> {
                    sendCodeMessage(chatId, code.getCode());
                }).exceptionally(e -> {
                    sendText(chatId, messageManager.getRaw("telegram.code-generation-error"));
                    plugin.getLogger().warning("Code generation error: " + e.getMessage());
                    if (plugin.getPluginConfig().isDebug()) {
                        e.printStackTrace();
                    }
                    return null;
                });
            });
        });
    }

    private void sendCodeMessage(Long chatId, String code) {
        String message = messageManager.getRawTelegram("telegram.code-message",
                MessageManager.placeholder("code", code),
                MessageManager.placeholder("minutes", String.valueOf(
                        plugin.getPluginConfig().getCodeExpirationMinutes())));

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText(message);
        sendMessage.setParseMode("HTML");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            plugin.getLogger().warning("Failed to send code message: " + e.getMessage());
            if (plugin.getPluginConfig().isDebug()) {
                e.printStackTrace();
            }
        }
    }

    private void sendExistingCode(Long chatId, String username) {
        Long userId = chatId;
        plugin.getWhitelistManager().getLinkByTelegram(userId).thenAccept(optLink -> {
            if (optLink.isPresent()) {
                String linkedMessage = messageManager.getRawTelegram("telegram.already-linked",
                        MessageManager.placeholder("player", optLink.get().getPlayerName()));
                sendText(chatId, linkedMessage);
                return;
            }

            Long telegramId = optLink.map(link -> link.getTelegramId()).orElse(userId);
            plugin.getWhitelistManager().getActiveCode(telegramId).thenAccept(optCode -> {
                if (optCode.isPresent()) {
                    sendCodeMessage(chatId, optCode.get().getCode());
                } else {
                    sendText(chatId, messageManager.getRaw("telegram.no-active-code"));
                }
            });
        });
    }

    private void sendHelpMessage(Long chatId) {
        String help = messageManager.getRawTelegram("telegram.help-message",
                MessageManager.placeholder("server",
                        plugin.getPluginConfig().getServerDisplayName().replace("&", "")));

        sendText(chatId, help);
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

}
