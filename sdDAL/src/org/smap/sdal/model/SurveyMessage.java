package org.smap.sdal.model;

public class SurveyMessage {
	public int id;				// Survey Id
	public int linkedSurveyId;	// Id of a survey form which this survey gets its data
	
	public SurveyMessage(int id) {
		this.id = id;
	}
}
