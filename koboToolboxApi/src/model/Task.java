package model;
import java.sql.Timestamp;

/*
 * Smap extension
 */
public class Task {
	
	public String user;
	public String title;	
	public Timestamp scheduled;
	public String status;	
	
	public void setStatus(String in) {
		if(in.equals("accepted")) {
			status = "pending";
		} else if(in.equals("submitted")) {
			status = "completed";
		} else {
			status = in;
		}
	}

}
