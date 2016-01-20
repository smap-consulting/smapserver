package taskModel;

import java.sql.Timestamp;

public class Task {
	public int id;
	public String type;
	public String title;
	public String pid;				// Project id
	public String url;
	public String form_id;
	public String form_version;
	public String initial_data;
	public String update_id;
	public Timestamp scheduled_at;
	public boolean repeat;
	public String address;			// Key value pairs representing an unstructured address
}
