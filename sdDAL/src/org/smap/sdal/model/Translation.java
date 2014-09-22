package org.smap.sdal.model;

public class Translation {
	private int id;
	private int surveyId;
	private String language = "eng";
	private String textId;
	private String type;
	private String value;
	private boolean enabled;
	
	// Getters
	public int getId() {return id;}; 
	public int getSurveyId() {return surveyId;}; 
	public String getLanguage() {return language;}; 
	public String getTextId() {return textId;};
	public String getType() {return type;};
	public String getValue() {return value;};
	public boolean getEnabled() { return enabled;};
	
	// Setters
	public void setId(int v) { id = v;};
	public void setSurveyId(int v) { surveyId = v;};
	public void setLanguage(String v) { language = v;};
	public void setTextId(String v) { textId = v;};
	public void setType(String v) { type = v;};
	public void setValue(String v) { value = v;};
	public void setEnabled(boolean v) { enabled = v;};
	

}
