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
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.smap.server.entities.Option;
import org.smap.server.entities.SubscriberEvent;
import org.smap.server.entities.UploadEvent;

public class SubscriberEventManager {

	private EntityManager em = null;

	public SubscriberEventManager(PersistenceContext pc) {
		em = pc.getEntityManager();
	}
	
	@SuppressWarnings("unchecked")
	public List<String> getSubscribers() {
		Query query = em.createQuery("SELECT DISTINCT se.subscriber FROM SUBSCRIBER_EVENT se ORDER BY se.subscriber desc");
		List<String> subscribers = query.getResultList();
		return subscribers;
	}


	public void persist(SubscriberEvent se) throws Exception {
		try {
			em.getTransaction().begin();
			em.persist(se);
			em.getTransaction().commit();
		} catch (PersistenceException e) {
			e.printStackTrace();
			throw new Exception("Error: Failed to write to upload event table");
		}
	}
}
