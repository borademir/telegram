package tr.org.fenerbahce.telegram;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import lombok.extern.slf4j.Slf4j;
import tr.org.fenerbahce.telegram.score.services.NesineComService;

@Slf4j
public class TelegramScoresBot extends TelegramLongPollingBot {

	public static void main(String[] args) {

		try {
			log.info("creating bot instance..");
			TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
			
			TelegramScoresBot myBot = new TelegramScoresBot();
			botsApi.registerBot(myBot);
			
			log.info("bot instance registered @telegram context.");
			
			NesineComService.listenWebPage(myBot);
			
			
		} catch (Exception e) {
			log.error(e.getMessage(),e);
		}
	}

	public void onUpdateReceived(Update update) {
		if (update.hasMessage() && update.getMessage().hasText()) {
			SendMessage message = new SendMessage();
			message.setChatId(String.valueOf(update.getMessage().getChatId()));
			String text = update.getMessage().getText();
			log.info(message.getChatId());
			if(!text.startsWith("@" + getBotUsername())) {
				return;
			}
			message.setText(text + "( ipne galatasaray )");
			try {
				execute(message); // Call method to send the message
				log.info(update.getMessage().getFrom().getFirstName() + ": " + text);
			} catch (Exception e) {
				log.error(e.getLocalizedMessage(),e);
			}
		}
	}

	public String getBotUsername() {
		return "fenerscoresbot";
	}

	@Override
	public String getBotToken() {
		return "1514275648:AAESOnVdzs01OUm0gVpEjRrq7TxFJkBxzXI";
	}

}
