package taskModel;

import java.util.Date;

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
	public Date scheduled_at;
	public String address;			// Key value pairs representing an unstructured address
}
