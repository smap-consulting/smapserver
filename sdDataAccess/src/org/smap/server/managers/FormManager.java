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

import java.util.ArrayList;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.smap.server.entities.Form;
import org.smap.server.entities.Question;
import org.smap.server.entities.Survey;

public class FormManager {

	//private EntityManager em = null;
	private PersistenceContext pc = null;

	public FormManager(PersistenceContext pc) {
		// em = pc.getEntityManager();
		this.pc = pc;
	}

	@SuppressWarnings("unchecked")
	public List<Form> getAll() {
		EntityManager em = pc.getEntityManager();
		Query query = em.createQuery("SELECT form FROM FORM f");
		List<Form> formList = query.getResultList();
		return formList;
	}

	public Form getById(int formId) {
		EntityManager em = pc.getEntityManager();
		Form f = em.find(Form.class, formId);
		return f;
	}

	@SuppressWarnings("unchecked")
	public List<Form> getBySurveyId(int surveyId) {
		EntityManager em = pc.getEntityManager();
		System.out.println("Survey ID " + surveyId);
		Query query = em.createQuery(
				"SELECT f FROM FORM f WHERE f.surveyOwner.id = ?1")
				.setParameter(1, surveyId);
		List<Form> formList = query.getResultList();
		return formList;
	}

	public List<Form> getBySurvey(Survey survey) {
		EntityManager em = pc.getEntityManager();
		Query query = em.createQuery(
				"SELECT f FROM FORM f WHERE f.surveyOwner = ?1").setParameter(
				1, survey);
		List<Form> formList = query.getResultList();

		return formList;
	}
	

	@SuppressWarnings("unchecked")
	public Form getFirstFormBySurveyId(int surveyId) throws Exception {

		EntityManager em = pc.getEntityManager();
		Query query = em.createQuery(
				"SELECT form FROM FORM form WHERE form.surveyOwner.id = ?1 "
						+ "AND form.parentForm = null").setParameter(1,
				surveyId);
		List<Form> formList = query.getResultList();

		
		if (formList.isEmpty()) {
			throw new Exception("First form not found for survey ID:"
					+ surveyId);
		}

		return formList.get(0);

	}

	@SuppressWarnings("unchecked")
	public Form loadFormWithQuestion(int questionId) {

		EntityManager em = pc.getEntityManager();
		Query query = em
				.createQuery(
						"SELECT form FROM FORM form, QUESTION question "
								+ "WHERE form.f_id = question.form.id AND question.q_id = ?1")
				.setParameter(1, questionId);
		List<Form> formList = query.getResultList();
		Form f = formList.get(0);

		return f;

	}

	/*
	 * If question has a subForm then return the subForm identifier else return
	 * 0
	 * 
	 * @param parentFormId
	 * 
	 * @param questionId
	 */
	@SuppressWarnings("unchecked")
	public int getSubFormId(int parentForm, int parentQuestion) {
		int subFormId = 0;
		EntityManager em = pc.getEntityManager();

		Query query = em
				.createQuery("SELECT form FROM FORM form WHERE form.parentForm.id = ?1 AND "
						+ "form.parentQuestion.id = ?2");
		query.setParameter(1, parentForm);
		query.setParameter(2, parentQuestion);
		List<Form> formList = query.getResultList();
		if (!formList.isEmpty()) {
			Form f = formList.get(0);
			subFormId = f.getId();
		}


		return subFormId;
	}

	public void persist(Form... formList) {
		EntityManager em = pc.getEntityManager();
		try {

			em.getTransaction().begin();

			for (Form f : formList) {
				System.out.println("Persisting form(" + f.getId() + "): " + f.getName());
				em.persist(f);
			}
			em.getTransaction().commit();
			
		} catch (PersistenceException e) {
			e.printStackTrace();
			em.getTransaction().rollback();
			// TODO handle exceptions, specifically constraint violations
		} finally {

		}
	}

	public void delete(Form... f) {
		EntityManager em = pc.getEntityManager();
		try {
			em.getTransaction().begin();
			
			em.flush();
			for (Form form : f) {
			
				System.out.println("Removing form " + form.getName());
				em.remove(form);
			}
			em.getTransaction().commit();
		} catch (PersistenceException e) {
			e.printStackTrace();
			// TODO handle exceptions, specifically constraint violations
		} finally {

		}

	}

	public void merge(Form f) {
		EntityManager em = pc.getEntityManager();
		try {

			em.getTransaction().begin();
			em.merge(f);
			em.getTransaction().commit();
		} catch (PersistenceException e) {
			// TODO handle exceptions, specifically constraint violations
		} finally {

		}
	}

	/**
	 * Returns a form using a question id
	 * 
	 * @param parseInt
	 *            The QuestionID which is attached to the form
	 * @return The return form. If no form is found, then a null value is
	 *         returned
	 */
	public Form getByQuestionId(int parseInt) {
		EntityManager em = pc.getEntityManager();
		Query query = em
				.createQuery(
						"SELECT form FROM FORM form WHERE form.parentQuestion.id = ?1 ")
				.setParameter(1, parseInt);
		List results = query.getResultList();
		
		Form f = (Form) ((results != null && results.size() >= 1) ? results.get(0) : null);

		return f;
	}

	/**
	 * This method returns a new form object.
	 * 
	 * @param survey
	 *            Survey that this form belongs to
	 * @param parentForm
	 * 
	 * @return
	 */
	public Form newForm(Survey survey, Form parentForm) {
		Form newForm = new Form();

		newForm.setQuestions(new ArrayList<Question>());
		newForm.setSurvey(survey);
		newForm.setParentForm(parentForm);

		return newForm;
	}
}
