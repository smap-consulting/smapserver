package taskModel;

import java.util.Date;

public class Task {
	public int id;
	public String type;
	public String title;
	public String url;
	public String form_id;
	public String form_version;
	public String initial_data;
	public String assignment_mode;
	public boolean gps;
	public boolean camera;
	public boolean barcode;
	public boolean rfid;
	public int repeats;
	public int priority;
	public Date scheduled_at;
	public Date from_date;
	public Date due_date;
	public Date created_date;
	public String address;			// Key value pairs representing an unstructured address
}
