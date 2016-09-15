package org.smap.sdal.managers;

import java.util.logging.Logger;

import org.smap.sdal.model.Action;
import org.smap.sdal.model.TableColumn;

/*****************************************************************************

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

 ******************************************************************************/

/*
 * Manage the table that stores details on the forwarding of data onto other systems
 */
public class ActionManager {
	
	private static Logger log =
			 Logger.getLogger(ActionManager.class.getName());
	
	public void getEvents() {
		
	}
	
	/*
	 * Apply actions resulting fron a change to managed forms
	 */
	public void applyManagedFormActions(TableColumn tc) {
		for(int i = 0; i < tc.actions.size(); i++) {
			Action a = tc.actions.get(i);
			System.out.println("Action: " + a.action + " : " + a.notify_type + " : " + a.notify_person);
		}
	}

}


