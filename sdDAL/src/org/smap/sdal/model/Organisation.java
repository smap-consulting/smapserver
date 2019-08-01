package org.smap.sdal.model;

/*
This file is part of SMAP.

SMAP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SMAP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

 */

public class Organisation {	
	public static String DEFAULT_NAVBAR_COLOR = "#2c3c28";
	
	public int id;
	public String name;
	public String company_name;
	public String company_address;
	public String company_email;
	public String company_phone;
	public boolean allow_email;
	public boolean allow_facebook;
	public boolean allow_twitter;
	public boolean can_edit = true;
	public boolean can_notify = true;
	public boolean can_use_api = true;
	public boolean can_submit = true;
	public boolean email_task = false;
	public boolean can_sms = false;
	public String changed_by;
	public String changed_ts;
	public String admin_email;
	public String smtp_host;
	public String email_domain;
	public String email_user;
	public String email_password;
	public int email_port;
	public String default_email_content;
	public String website;
	public String locale;
	public String timeZone;
	public String server_description;
	public int e_id;		// Enterprise id
	public WebformOptions webform;
	public AppearanceOptions appearance = new AppearanceOptions();

	public String getAdminEmail() {

		String email = null;
		if (admin_email != null && admin_email.trim().length() > 0) {
			email = admin_email;
		} else if (company_email != null && company_email.trim().length() > 0) {
			email = company_email;
		} else {
			email = email_user;
		}

		return email;
	}
	
	// Support old versions of FT that use a boolean value for ft_send_wifi
	static public boolean get_ft_send_wifi_cell(String send) {
		boolean v = false;
		if(send != null && send.equals("cell")) {
			v = true;
		}
		return v;	
	}
	
	// Support old versions of FT  that use a boolean value for ft_send_wifi
	static public boolean get_ft_send_wifi(String send) {
		boolean v = false;
		if(send != null && send.equals("wifi")) {
			v = true;
		}
		return v;
	}
	
	// Support old versions of FT  that use a boolean value for ft_delete_submitted
	static public boolean get_ft_delete_submitted(String del) {
		boolean v = false;
		if(del != null && del.equals("on")) {
			v = true;
		}
		return v;
	}
}
