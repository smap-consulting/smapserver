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

package org.smap.server.managers;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.smap.server.entities.UploadEvent;

/**
 * 
 * @author Neil Penman, Gerard Gigliotti
 * 
 *         This class is used to manage the reading and writing of upload events
 */
public class UploadEventManager {

	private EntityManager em = null;

	public UploadEventManager(PersistenceContext pc) {
		em = pc.getEntityManager();
	}

	@SuppressWarnings("unchecked")
	public List<UploadEvent> getAll() {
		Query query = em.createQuery("SELECT ue FROM UPLOAD_EVENT ue ORDER BY ue.uploadTime desc");
		List<UploadEvent> eventList = query.getResultList();
		return eventList;
	}
	
	/**
	 * This method is used to return upload events for a specified survey template.
	 */
	@SuppressWarnings("unchecked")
	public List<UploadEvent> getBySurveyName(String name) {
		List<UploadEvent> eventList = null;
 		Query query = em.createQuery(
				"SELECT ue FROM UPLOAD_EVENT ue WHERE ue.surveyName =:name ORDER BY ue.uploadTime desc");
		query.setParameter("name", name);
		try {
			eventList = query.getResultList();
		} catch (Exception e) {
			// TODO handle
		}
		return eventList;
	}
	
	/**
	 * This method is used to return complete upload events that have not been submitted 
	 * successfully to the specified subscriber
	 * if sId > 0 then only uploads for that survey are returned
	 */
	@SuppressWarnings("unchecked")
	public List<UploadEvent> getFailedForSubscriber(String name, int sId) {
		List<UploadEvent> eventList = null;

		Query query = null;
		if(sId > 0) {
	 		query = em.createQuery(
					"SELECT ue FROM UPLOAD_EVENT ue " +
					"WHERE ue.status = 'success' " +
					" AND ue.s_id = :sId " +
					" AND ue.incomplete = 'false'" +
					" AND NOT EXISTS (SELECT se FROM SUBSCRIBER_EVENT se WHERE " +
					"se.subscriber =:name AND se.ue = ue)");
			query.setParameter("sId", sId);
		} else {
	 		query = em.createQuery(
					"SELECT ue FROM UPLOAD_EVENT ue " +
					"WHERE ue.status = 'success' " +
					" AND ue.s_id is not null " +
					" AND ue.incomplete = 'false'" +
					" AND NOT EXISTS (SELECT se FROM SUBSCRIBER_EVENT se WHERE " +
					"se.subscriber =:name AND se.ue = ue)");
		}
		
 		query.setParameter("name", name);
		try {
			eventList = query.getResultList();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return eventList;
	}
	
	/**
	 * This method is used to return complete upload events that have not been submitted 
	 * successfully to the specified subscriber
	 * if sId > 0 then only uploads for that survey are returned
	 */
	@SuppressWarnings("unchecked")
	public List<UploadEvent> getIncomplete(String origIdent, String ident) {
		List<UploadEvent> eventList = null;

 		Query query = em.createQuery(
				"SELECT ue FROM UPLOAD_EVENT ue " +
				"WHERE ue.status = 'success' " +
				" AND ue.s_id is not null " +
		//		" AND ue.origSurveyIdent = :origIdent " +
		//		" AND ue.ident = :ident " +
				" AND ue.incomplete = true" +
				" AND NOT EXISTS (SELECT se FROM SUBSCRIBER_EVENT se WHERE se.ue = ue)");
		
 		query.setParameter("origIdent", origIdent);
 		query.setParameter("ident", ident);
		try {
			eventList = query.getResultList();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return eventList;
	}

	public void persist(UploadEvent ue) throws Exception {

		try {
			
			em.getTransaction().begin();
			em.persist(ue);
			em.getTransaction().commit();
			
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception("Error: Failed to write to upload event table");
		}
	}
	
}
