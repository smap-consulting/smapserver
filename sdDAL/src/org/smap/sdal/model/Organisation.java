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
	public int id;
	public String name;
	public String company_name;
	public String company_address;
	public String company_email;
	public String company_phone;
	public boolean allow_email;
	public boolean allow_facebook;
	public boolean allow_twitter;
	public boolean can_edit;
	public boolean ft_delete_submitted;
	public boolean ft_send_trail;
	public boolean ft_sync_incomplete;
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
	
	public String getAdminEmail() {
		
		String email = null;
		if(admin_email != null && admin_email.trim().length() > 0) {
			email = admin_email;
		} else if(company_email != null && company_email.trim().length() > 0) {
			email = company_email;
		} else {
			email = email_user;
		}
		
		return email;
	}
}
