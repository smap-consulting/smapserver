package org.smap.sdal.model;

import java.util.ArrayList;

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

public class User {
	public int id;
	public String ident;
	public String name;
	public String settings;		// JSON for customer configurable settings
	public String language;
	public String email;
	public String password;
	public boolean allow_email;
	public boolean allow_facebook;
	public boolean allow_twitter;
	public boolean can_edit;
	public boolean ft_send_trail;
	public int current_project_id;
	public int current_survey_id;
	public String organisation_name;
	public String company_name;
	public ArrayList<UserGroup> groups;
	public ArrayList<Project> projects;
	public boolean keepProjects;
	public boolean sendEmail;
	
}
