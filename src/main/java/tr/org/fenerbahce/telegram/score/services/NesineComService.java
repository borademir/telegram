package tr.org.fenerbahce.telegram.score.services;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import io.github.bonigarcia.wdm.WebDriverManager;
import io.github.bonigarcia.wdm.config.DriverManagerType;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import tr.org.fenerbahce.telegram.TelegramScoresBot;

@Slf4j
@UtilityClass
public class NesineComService {
	
	private static final String NESINE_LIVE_MATCH_URL_FORMAT = "https://ls.nesine.com/api/v1/LiveScore/GetIddaaResultObjectList?BetType=1&OnlyLive=1&Date=%s&League=&FilterType=init&_=1610290917049";

	public static void main(String[] args) throws IOException, InterruptedException {
		
		log.info("starting..");
		listenWebPage(null);

	}

	public static List<NesineComMatch> retrieveMatchList() throws IOException {
		
		SimpleDateFormat nesineDateFormat = new SimpleDateFormat("yyyy-MM-dd");
				
		String nesineFormattedUrl = String.format(NESINE_LIVE_MATCH_URL_FORMAT, nesineDateFormat.format(new Date()));
		
		log.info("Retrieve live matches via {}" , nesineFormattedUrl);
		
		Document document = Jsoup.connect(nesineFormattedUrl).get();
		
		String jsonResponse = document.text();
		
		JSONObject root = new JSONObject(jsonResponse);
		
		JSONArray resultsArray = root.getJSONArray("result");
		
		Iterator<Object> resultIt = resultsArray.iterator();
		
		List<NesineComMatch> matchList = new ArrayList<>();
		while(resultIt.hasNext()) {
			JSONObject matchJson = (JSONObject) resultIt.next();
			
			NesineComMatch nm = new NesineComMatch();
			nm.setBid(matchJson.getLong("BID"));
			nm.setHomeTeam(matchJson.getString("HT"));
			nm.setAwayTeam(matchJson.getString("AT"));
			nm.setLeague(matchJson.getString("L"));
			
			String currentScore = "0-0";
			
			if(!matchJson.isNull("ME")) {
				JSONArray matchEvents = matchJson.getJSONArray("ME");
				if(matchEvents.length() > 0) {
					JSONObject lastEvent = (JSONObject) matchEvents.get(matchEvents.length()-1);
					if(!lastEvent.isNull("CV")) {
						currentScore = lastEvent.getString("CV");
					}
				}
			}
			
			nm.setCurrentScore(currentScore);
			
			matchList.add(nm);
			
			log.info(currentScore + " - "  + matchJson.toString());
		}
		log.info(matchList.size() + " matches");
		return matchList;
	}


	@SuppressWarnings("deprecation")
	public static void listenWebPage(TelegramScoresBot myBot) throws InterruptedException, IOException {
		
		log.info("Listening nesine.com..");
		List<NesineComMatch> matchList = retrieveMatchList();
		WebDriverManager.getInstance(DriverManagerType.CHROME).setup();
		
        LoggingPreferences loggingprefs = new LoggingPreferences();
        loggingprefs.enable(LogType.PERFORMANCE, Level.ALL);

        
		DesiredCapabilities cap = new DesiredCapabilities();
        cap.setCapability(CapabilityType.LOGGING_PREFS, loggingprefs);

        WebDriver driver = new ChromeDriver(cap);
        driver.navigate().to("https://www.nesine.com/iddaa/canli-skor/futbol");
        
        while(true) { // NOSONAR
        	LogEntries logEntries = driver.manage().logs().get(LogType.PERFORMANCE);
        	if(Objects.isNull(logEntries) || logEntries.getAll().isEmpty()) {
        		Thread.sleep(1000);
        		continue;
        	}
        	parseLogs(logEntries,matchList,myBot);
        	
        }

	}

	private static void parseLogs(LogEntries logEntries, List<NesineComMatch> matchList, TelegramScoresBot myBot) {
		logEntries.forEach(entry->{
            JSONObject messageJSON = new JSONObject(entry.getMessage());
            String method = messageJSON.getJSONObject("message").getString("method");
            if(method.equalsIgnoreCase("Network.webSocketFrameReceived")){
                String payload = messageJSON.getJSONObject("message").getJSONObject("params").getJSONObject("response").getString("payloadData");
                if(payload.indexOf('[') > -1) {
                	payload = payload.substring(payload.indexOf('['));
                }
				processPayload(payload,matchList,myBot);
            }
        });
	}

	private static void processPayload(String payload, List<NesineComMatch> matchList, TelegramScoresBot myBot) {
		try {
			if(!payload.startsWith("[\"Football\",[")) {
				return;
			}
			
			payload = payload.substring(payload.indexOf('{'),payload.lastIndexOf('}') + 1);
			JSONObject root = new JSONObject(payload);
			
			int mt = root.getInt("MT");
			
			if(mt != 5) {
				return;
			}
			
			JSONObject detail = root.getJSONObject("M");
			long bid = detail.getLong("BID");
			
			NesineComMatch nesineComMatch = findMatchDetail(bid,matchList);
			String summary = nesineComMatch.getHomeTeam() + " - " + nesineComMatch.getAwayTeam() + System.lineSeparator();
			if(detail.isNull("CV") || detail.getString("CV") == null) {
				return;
			}
			
			summary += detail.getString("CV") + " Dakika " + detail.getString("M") +System.lineSeparator();
			
			if(!detail.isNull("P")) {
				summary += detail.getString("P");
			}
			
			
			summary = "GOOOOOL \n" + summary;
			
			log.info(summary);
			
			if(Objects.isNull(myBot)) {
				return;
			}
			
			SendMessage message = new SendMessage();
			message.setChatId("-1001318890509"); // -474688149
			message.setText(summary);
			myBot.execute(message);
		
		} catch (Exception e) {
			log.error("ERR :" + e.getMessage() + "@"+ payload);
		}
	}

	private static NesineComMatch findMatchDetail(long bid, List<NesineComMatch> matchList) throws IOException {
		
		Optional<NesineComMatch> matchDetail = matchList.stream().filter(md -> md.getBid().equals(bid)).findFirst();
		if(matchDetail.isPresent()) {
			return matchDetail.get();
		}
		
		matchList.clear();
		matchList.addAll(retrieveMatchList());
		
		return findMatchDetail(bid, matchList);
	}

}
