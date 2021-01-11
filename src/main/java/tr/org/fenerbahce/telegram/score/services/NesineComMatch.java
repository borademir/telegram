package tr.org.fenerbahce.telegram.score.services;

import lombok.Data;

@Data
public class NesineComMatch {
	
	private Long bid;
	
	private String homeTeam;
	
	private String awayTeam;
	
	private String currentScore;
	
	private String league;
}
