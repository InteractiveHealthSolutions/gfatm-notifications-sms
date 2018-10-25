/* Copyright(C) 2018 Interactive Health Solutions, Pvt. Ltd.

This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as
published by the Free Software Foundation; either version 3 of the License (GPLv3), or any later version.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program; if not, write to the Interactive Health Solutions, info@ihsinformatics.com
You can also access the license on the internet at the address: http://www.gnu.org/licenses/gpl-3.0.html

Interactive Health Solutions, hereby disclaims all copyright interest in this program written by the contributors.
*/
package com.ihsinformatics.gfatmnotifications.sms.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.joda.time.DateTime;
import org.json.JSONObject;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.ihsinformatics.gfatmnotifications.common.Context;
import com.ihsinformatics.gfatmnotifications.common.model.Encounter;
import com.ihsinformatics.gfatmnotifications.common.model.Location;
import com.ihsinformatics.gfatmnotifications.common.model.Observation;
import com.ihsinformatics.gfatmnotifications.common.model.Patient;
import com.ihsinformatics.gfatmnotifications.common.model.Rule;
import com.ihsinformatics.gfatmnotifications.common.model.User;
import com.ihsinformatics.gfatmnotifications.common.service.NotificationService;
import com.ihsinformatics.gfatmnotifications.common.util.Decision;
import com.ihsinformatics.gfatmnotifications.common.util.FormattedMessageParser;
import com.ihsinformatics.gfatmnotifications.common.util.ValidationUtil;
import com.ihsinformatics.gfatmnotifications.sms.SmsContext;
import com.ihsinformatics.util.DateTimeUtil;
import com.ihsinformatics.util.JsonUtil;
import com.ihsinformatics.util.RegexUtil;

/**
 * @author owais.hussain@ihsinformatics.com
 *
 */
public class SmsNotificationsJob implements NotificationService {

	private static final Logger log = Logger.getLogger(Class.class.getName());

	private DateTime dateFrom;
	private DateTime dateTo;
	private FormattedMessageParser messageParser;

	public SmsNotificationsJob() {
		HostnameVerifier hostNameVerifier = new HostnameVerifier() {
			@Override
			public boolean verify(String hostname, SSLSession session) {
				return hostname.equals(SmsContext.SMS_SERVER_ADDRESS);
			}
		};
		HttpsURLConnection.setDefaultHostnameVerifier(hostNameVerifier);
		messageParser = new FormattedMessageParser(Decision.LEAVE_EMPTY);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.quartz.Job#execute(org.quartz.JobExecutionContext)
	 */
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		JobDataMap dataMap = context.getMergedJobDataMap();
		SmsNotificationsJob smsJob = (SmsNotificationsJob) dataMap.get("smsJob");
		this.setDateFrom(smsJob.getDateFrom());
		this.setDateTo(smsJob.getDateTo());
		try {
			Context.initialize();
			setDateFrom(getDateFrom().minusHours(24));
			log.info(getDateFrom() + " " + getDateTo());

			DateTime from = DateTime.now().minusDays(2);//minusMonths(12);
			DateTime to = DateTime.now().minusMonths(0);

			run(from, to);

		} catch (IOException e) {
			log.warning("Unable to initialize context.");
			throw new JobExecutionException(e.getMessage());
		} catch (ParseException e) {
			log.warning("Unable to parse messages.");
			throw new JobExecutionException(e.getMessage());
		}
	}

	/**
	 * At present, this function can only validate all OR's or all AND's, not their
	 * combinations
	 * 
	 * @param patient   TODO
	 * @param location  TODO
	 * @param encounter
	 * @param rule
	 * 
	 * @return
	 */
	public boolean validateConditions(Patient patient, Location location, Encounter encounter, Rule rule) {
		String conditions = rule.getConditions();
		String orPattern = "(.)+OR(.)+";
		String andPattern = "(.)+AND(.)+";
		if (conditions.matches(orPattern) && conditions.matches(andPattern)) {
			String[] orConditions = conditions.split("( )?OR( )?");
			for (String condition : orConditions) {
				// No need to proceed even if one condition is true
				if (condition.matches(andPattern)) {

					String[] andConditions = conditions.split("( )?AND( )?");
					for (String nestedCondition : andConditions) {
						// No need to proceed even if one condition is false
						if (!validateSingleCondition(conditions, patient, location, encounter,
								encounter.getObservations())) {
							return false;
						}
					}
					return true;
				} else {
					return validateSingleCondition(conditions, patient, location, encounter,
							encounter.getObservations());
				}
			}
			return false;
		}
		if (conditions.matches(orPattern)) {
			String[] orConditions = conditions.split("( )?OR( )?");
			for (String condition : orConditions) {
				// No need to proceed even if one condition is true
				if (validateSingleCondition(condition, patient, location, encounter, encounter.getObservations())) {
					return true;
				}
			}
		} else if (conditions.matches(andPattern)) {
			String[] orConditions = conditions.split("( )?AND( )?");
			for (String condition : orConditions) {
				// No need to proceed even if one condition is false
				if (!validateSingleCondition(condition, patient, location, encounter, encounter.getObservations())) {
					return false;
				}
			}
			return true;
		} else {
			return validateSingleCondition(conditions, patient, location, encounter, encounter.getObservations());
		}
		return false;
	}

	private boolean validateSingleCondition(String condition, Patient patient, Location location, Encounter encounter,
			List<Observation> observations) {
		boolean result = false;
		JSONObject jsonObject = JsonUtil.getJSONObject(condition);
		if (!(jsonObject.has("entity") && jsonObject.has("property") && jsonObject.has("validate")
				&& jsonObject.has("value"))) {
			log.severe("Condition must contain all four required keys: entity, validate and value");
			return false;
		}
		String entity = jsonObject.getString("entity");
		String property = jsonObject.getString("property");
		String validationType = jsonObject.getString("validate");
		String expectedValue = jsonObject.getString("value");
		String actualValue = null;

		try {
			// In case of Encounter, search through observations
			if (entity.equals("Encounter")) {
				// Search for the observation's concept name matching the variable name
				Observation target = null;
				for (Observation observation : observations) {
					if (variableMatchesWithConcept(property, observation)) {
						target = observation;
						break;
					}
				}
				if (target == null) {
					return result;
				}
				actualValue = target.getValue().toString();
			}
			// In case of Patient or Location, search for the defined property
			else if (entity.equals("Patient")) {
				actualValue = getEntityPropertyValue(patient, property);
			} else if (entity.equals("Location")) {
				actualValue = getEntityPropertyValue(location, property);
			}
			// Check the type of validation and call respective method
			if (validationType.equals("VALUE")) {
				return actualValue.equalsIgnoreCase(expectedValue);
			} else if (validationType.equalsIgnoreCase("RANGE")) {
				Double valueDouble = Double.parseDouble(actualValue);
				return ValidationUtil.validateRange(expectedValue, valueDouble);
			} else if (validationType.equalsIgnoreCase("REGEX")) {
				return ValidationUtil.validateRegex(expectedValue, actualValue);
			} else if (validationType.equalsIgnoreCase("QUERY")) {
				return ValidationUtil.validateQuery(expectedValue, actualValue);
			} else if (validationType.equalsIgnoreCase("NOTNULL") || validationType.equalsIgnoreCase("PRESENT")
					|| validationType.equalsIgnoreCase("EXISTS")) {
				if (actualValue != null) {
					return true;
				}
				return false;
			} else if (validationType.equalsIgnoreCase("List")) {
				return ValidationUtil.validateList(expectedValue, actualValue);
			}
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * Checks the entity type of the object and looks for the value in given
	 * property using Reflection
	 * 
	 * @param object
	 * @param property
	 * @return
	 * @throws NoSuchFieldException
	 * @throws IllegalAccessException
	 */
	private String getEntityPropertyValue(Object object, String property)
			throws NoSuchFieldException, IllegalAccessException {
		String actualValue;
		Field field;
		if (object instanceof Patient) {
			field = Patient.class.getDeclaredField(property);
		} else if (object instanceof Location) {
			field = Location.class.getDeclaredField(property);
		} else if (object instanceof Encounter) {
			field = Encounter.class.getDeclaredField(property);
		} else if (object instanceof User) {
			field = User.class.getDeclaredField(property);
		}
		field = Patient.class.getDeclaredField(property);
		boolean flag = field.isAccessible();
		field.setAccessible(true);
		actualValue = field.get(object).toString();
		field.setAccessible(flag);
		return actualValue;
	}

	/**
	 * This function only checks whether the variable is an Integer ID, or a concept
	 * name and matches with the concept in the observation
	 * 
	 * @param variable
	 * @param observation
	 * @return
	 */
	private boolean variableMatchesWithConcept(String variable, Observation observation) {
		// Check if the variable is a concept ID
		if (RegexUtil.isNumeric(variable, false)) {
			return observation.getConceptId().equals(Integer.parseInt(variable));
		} else if (observation.getConceptName().equalsIgnoreCase(variable)) {
			return true;
		} else if (observation.getConceptShortName().equalsIgnoreCase(variable)) {
			return true;
		}
		return false;
	}

	public void run(DateTime from, DateTime to) throws ParseException, IOException {
		List<Rule> rules = Context.getRuleBook().getSmsRules();
		// Read each rule and execute the decision
		for (Rule rule : rules) {
			if (rule.getEncounterType() == null) {
				continue;
			}

			// Fetch all the encounters for this type
			List<Encounter> encounters = Context.getEncounters(from, to,
					Context.getEncounterTypeId(rule.getEncounterType()));

			// patient to whom sms is already sent ?
			Map<Integer, Patient> imformedPatients = new HashMap<Integer, Patient>();
			for (Encounter encounter : encounters) {
				Patient patient = Context.getPatientByIdentifier(encounter.getIdentifier());
				if(patient==null) {
					continue;
				}
				if (imformedPatients.get(patient.getPersonId()) != null) {
					System.out.println("Sms already sent to Patient with identifer =" + patient.getPatientIdentifier());
					continue;
				}

				Location location = Context.getLocationByName(encounter.getLocation());
				List<Observation> observations = Context.getEncounterObservations(encounter);
				encounter.setObservations(observations);
				if (validateConditions(patient, location, encounter, rule)) {
					User user = Context.getUserByUsername(encounter.getUsername());

					String preparedMessage = messageParser.parseFormattedMessage(
							SmsContext.getMessage(rule.getMessageCode()), encounter, patient, user, location);
					System.out.println(preparedMessage);
					Date sendOn = new Date();
					// String dateField = rule.getScheduleDate();
					DateTime referenceDate = null;

					try {
						referenceDate = getReferenceDate(rule.getScheduleDate(), encounter);
						sendOn = calculateScheduleDate(referenceDate, rule.getPlusMinus(), rule.getPlusMinusUnit());
					} catch (Exception e) {
						e.printStackTrace();
					}
					String contactNumber = null;
					boolean isItPatient = false;
					if (rule.getSendTo().equalsIgnoreCase("patient")) {
						contactNumber = patient.getPrimaryContact();
						if (!patient.getConsent().equalsIgnoreCase("1065")) {
							log.info("Patient : " + patient.getPatientIdentifier() + "  doesnot want to receive SMS!");

							continue;
						}

						if (Context.getRuleBook().getBlacklistedPatient().contains(patient.getPatientIdentifier())) {
							log.info("Patient : " + patient.getPatientIdentifier() + "  is in blacklist!");

							continue;
						}
						/*
						 * if(!ValidationUtil.isValidContactNumber(contactNumber)) {
						 * log.info("Patient : "+patient.getPatientIdentifier()
						 * +"  doesnot have an valid number!"); continue; }
						 */
						isItPatient = true;

					} else if (rule.getSendTo().equalsIgnoreCase("doctor")) {
						//TODO  to be decided
					} else if (rule.getSendTo().equalsIgnoreCase("supervisor") || rule.getSendTo().equalsIgnoreCase("facility")) {
						contactNumber=location.getPrimaryContact();

					}
					contactNumber = "03343811112";
					if (sendOn != null) {
						DateTime now = new DateTime();
						DateTime beforeNow = now.minusHours(SmsContext.SMS_SCHEDULE_INTERVAL_IN_HOURS);
						if (sendOn.getTime() > beforeNow.getMillis() && sendOn.getTime() <= now.getMillis()) {
							if (!validateStopConditions(patient, location, encounter, rule)) {
								sendNotification(contactNumber, preparedMessage, Context.PROJECT_NAME, sendOn);
								if (isItPatient) {
									imformedPatients.put(patient.getPersonId(), patient);
								}
							}

						}
					}
				}
			}
		}
	}

	private boolean validateStopConditions(Patient patient, Location location, Encounter encounter, Rule rule) {
		if (rule.getStopCondition() == null || rule.getStopCondition().isEmpty()) {
			return false;
		}
		String conditions = rule.getStopCondition();
		String orPattern = "(.)+OR(.)+";
		String andPattern = "(.)+AND(.)+";
		if (conditions.matches(orPattern) && conditions.matches(andPattern)) {
			String[] orConditions = conditions.split("( )?OR( )?");
			for (String condition : orConditions) {
				// No need to proceed even if one condition is true
				if (condition.matches(andPattern)) {

					String[] andConditions = conditions.split("( )?AND( )?");
					for (String nestedCondition : andConditions) {
						// No need to proceed even if one condition is false
						if (!validateSingleStopCondition(nestedCondition, patient, location)) {
							return false;
						}
					}
					return true;
				} else {
					return validateSingleStopCondition(conditions, patient, location);
				}
			}

			return false;
		}
		if (conditions.matches(orPattern)) {
			String[] orConditions = conditions.split("( )?OR( )?");
			for (String condition : orConditions) {
				// No need to proceed even if one condition is true

				if (validateSingleStopCondition(condition, patient, location)) {
					return true;
				}
			}
		} else if (conditions.matches(andPattern)) {
			String[] orConditions = conditions.split("( )?AND( )?");
			for (String condition : orConditions) {
				// No need to proceed even if one condition is false
				if (!validateSingleStopCondition(condition, patient, location)) {
					return false;
				}
			}
			return true;
		} else {
			return validateSingleStopCondition(conditions, patient, location);
		}

		return false;
	}

	private boolean validateSingleStopCondition(String condition, Patient patient, Location location) {
		boolean result = false;
		JSONObject jsonObject = JsonUtil.getJSONObject(condition);
	/*	if (jsonObject.has("entity") && jsonObject.has("property") && jsonObject.has("validate")) {
			log.severe("Condition must contain all four required keys: entity, validate and value");
			return false;
		} else if(jsonObject.has("entity") && jsonObject.has("encounter") && jsonObject.has("validate")
				&& jsonObject.has("after")) {
			log.severe("Condition must contain all four required keys: entity, validate, after and encounter ");
			return false;
		}*/

		String entity = jsonObject.getString("entity");

		String validationType = jsonObject.getString("validate");

		// Fetch all the encounters for this type
		/*
		 * List<Encounter> encounters = Context.getEncounters(from, to, ));
		 */
		String encounter = jsonObject.getString("encounter");
		Encounter baseEncounter = null;
		if (encounter != null || (!encounter.isEmpty())) {
			try {
			baseEncounter = Context.getEncounterByPatientIdentifier(patient.getPatientIdentifier(),
					Context.getEncounterTypeId(encounter));
			baseEncounter.setObservations(Context.getEncounterObservations(baseEncounter));
			}catch(Exception e) {
				e.printStackTrace();
				return false;
			}
		}
		String property = jsonObject.getString("property");
		String expectedValue = jsonObject.getString("value");
		String actualValue = null;
		try {
			// In case of Encounter, search through observations
			if (entity.equals("Encounter")) {
				// Search for the observation's concept name matching the variable name
				Observation target = null;
				for (Observation observation : baseEncounter.getObservations()) {
					if (variableMatchesWithConcept(property, observation)) {
						target = observation;
						break;
					}
				}
				if (target == null) {
					return result;
				}
				actualValue = target.getValue().toString();
			}
			// In case of Patient or Location, search for the defined property
			else if (entity.equals("Patient")) {
				actualValue = getEntityPropertyValue(patient, property);
			} else if (entity.equals("Location")) {
				actualValue = getEntityPropertyValue(location, property);

			}

			// Check the type of validation and call respective method
			if (validationType.equalsIgnoreCase("Encounter")) {
				String afterEncounterType = jsonObject.getString("after");
				Encounter afterEncounter = Context.getEncounterByPatientIdentifier(patient.getPatientIdentifier(),
						Context.getEncounterTypeId(afterEncounterType));
				if (baseEncounter.getEncounterDate() > afterEncounter.getEncounterDate()) {
					return true;
				}
			} else {

				// Search for the observation's concept name matching the variable name
				Observation target = null;
				for (Observation observation : baseEncounter.getObservations()) {
					if (variableMatchesWithConcept(property, observation)) {
						target = observation;
						break;
					}
				}
				if (target == null) {
					return result;
				}
				actualValue = target.getValue().toString();

				if (validationType.equals("VALUE")) {
					return actualValue.equalsIgnoreCase(expectedValue);
				} else if (validationType.equalsIgnoreCase("RANGE")) {
					Double valueDouble = Double.parseDouble(actualValue);
					return ValidationUtil.validateRange(expectedValue, valueDouble);
				} else if (validationType.equalsIgnoreCase("REGEX")) {
					return ValidationUtil.validateRegex(expectedValue, actualValue);
				} else if (validationType.equalsIgnoreCase("QUERY")) {
					return ValidationUtil.validateQuery(expectedValue, actualValue);
				} else if (validationType.equalsIgnoreCase("NOTNULL") || validationType.equalsIgnoreCase("PRESENT")
						|| validationType.equalsIgnoreCase("EXISTS")) {
					if (actualValue != null) {
						return true;
					}
					return false;
				} else if (validationType.equalsIgnoreCase("List")) {
					return ValidationUtil.validateList(expectedValue, actualValue);

				}

			}
		} catch (InvalidPropertiesFormatException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	private Date calculateScheduleDate(DateTime referenceDate, Double plusMinus, String plusMinusUnit) {
		Date returnDate = null;
		if (referenceDate == null) {
			return returnDate;
		}
		if (plusMinusUnit.equalsIgnoreCase("hours")) {
			if (plusMinus < 0) {
				returnDate = referenceDate.minusHours(plusMinus.intValue()).toDate();
			} else {
				returnDate = referenceDate.plusHours(plusMinus.intValue()).toDate();
			}
		} else if (plusMinusUnit.equalsIgnoreCase("days")) {
			if (plusMinus < 0) {
				returnDate = referenceDate.minusDays(plusMinus.intValue()).toDate();
			} else {
				returnDate = referenceDate.plusDays(plusMinus.intValue()).toDate();
			}
		} else if (plusMinusUnit.equalsIgnoreCase("months")) {
			if (plusMinus < 0) {
				returnDate = referenceDate.minusMonths(plusMinus.intValue()).toDate();
			} else {
				returnDate = referenceDate.plusMonths(plusMinus.intValue()).toDate();
			}
		}
		return returnDate;
	}

	private DateTime getReferenceDate(String scheduleDate, Encounter encounter) throws Exception {

		DateTime referenceDate = null;
		FormattedMessageParser formattedMessageParser = new FormattedMessageParser(Decision.SKIP);

		try {
			Object object;
			object = formattedMessageParser.getPropertyValue(encounter, scheduleDate);
			if (object != null) {
				if (object instanceof Long) {
					return referenceDate = new DateTime((Long) object);
				} else {
					log.severe("Schedule Date must be a Date");
					throw new Exception("Schedule Date Object is not a Date ");
				}
			}
		} catch (SecurityException | IllegalArgumentException | ReflectiveOperationException e) {
			e.printStackTrace();
		}

		Observation target = null;
		for (Observation observation : encounter.getObservations()) {
			if (variableMatchesWithConcept(scheduleDate, observation)) {
				target = observation;
				referenceDate = new DateTime(target.getValueDatetime());
				break;
			}
		}

		return referenceDate;
	}

	/*
	 * @see
	 * com.ihsinformatics.gfatmnotifications.common.service.NotificationService#
	 * sendNotification(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public String sendNotification(String addressTo, String message, String subject, Date sendOn) {
		String response = null;
		try {
			StringBuffer content = new StringBuffer();
			content.append("send_to=" + addressTo + "&");
			content.append("message=" + URLEncoder.encode(message, "UTF-8") + "&");
			content.append(
					"schedule_time=" + URLEncoder.encode(DateTimeUtil.toSqlDateTimeString(sendOn), "UTF-8") + "&");
			content.append("project_id=" + Context.PROJECT_NAME + "&");
			if (SmsContext.SMS_USE_SSL) {
				response = postSecure(SmsContext.SMS_SERVER_ADDRESS, content.toString());
			} else {
				response = postInsecure(SmsContext.SMS_SERVER_ADDRESS, content.toString());
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			log.log(Level.SEVERE, e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			log.log(Level.SEVERE, e.getMessage());
		}
		return response;
	}

	/**
	 * Send SSL-enabled message
	 * 
	 * @param url
	 * @param content
	 * @return
	 * @throws Exception
	 */
	public String postSecure(String url, String content) throws Exception {
		URL obj = new URL(url + "?" + content);
		HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
		con.setRequestMethod("POST");
		con.setRequestProperty("Accept", "application/json");
		con.setRequestProperty("Content-Type", "text/html; charset=UTF-8");
		con.setRequestProperty("Authorization", "Basic " + SmsContext.SMS_API_KEY);
		con.setDoOutput(true);
		int responseCode = con.getResponseCode();
		StringBuilder message = new StringBuilder("\n").append("Sending 'POST' request to URL : ").append(url)
				.append("Post parameters : ").append(content).append("Response Code : ").append(responseCode);
		System.out.println(message.toString());
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		return response.toString();
	}

	/**
	 * Send unencrypted SMS
	 * 
	 * @param url
	 * @param content
	 * @return
	 * @throws Exception
	 */
	@Deprecated
	public String postInsecure(String url, String content) throws Exception {
		URL obj = new URL(url + "?" + content);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setRequestMethod("POST");
		con.setRequestProperty("Accept", "application/json");
		con.setRequestProperty("Content-Encoding", "gzip");
		con.setRequestProperty("Content-Type", "text/html; charset=UTF-8");
		con.setRequestProperty("Authorization", "Basic " + SmsContext.SMS_API_KEY);
		con.setDoOutput(true);
		int responseCode = con.getResponseCode();
		System.out.println("\nSending 'POST' request to URL : " + url);
		System.out.println("Post parameters : " + content);
		System.out.println("Response Code : " + responseCode);
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		return response.toString();
	}

//	public void test(String httpsUrl, boolean printCertificate) {
//		try {
//			URL url = new URL(httpsUrl);
//			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
//			if (printCertificate) {
//				try {
//					System.out.println("Response Code : " + con.getResponseCode());
//					System.out.println("Cipher Suite : " + con.getCipherSuite());
//					System.out.println("\n");
//					Certificate[] certs = con.getServerCertificates();
//					for (Certificate cert : certs) {
//						System.out.println("Cert Type : " + cert.getType());
//						System.out.println("Cert Hash Code : " + cert.hashCode());
//						System.out.println("Cert Public Key Algorithm : " + cert.getPublicKey().getAlgorithm());
//						System.out.println("Cert Public Key Format : " + cert.getPublicKey().getFormat());
//						System.out.println("\n");
//					}
//				} catch (SSLPeerUnverifiedException e) {
//					e.printStackTrace();
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//			}
//		} catch (MalformedURLException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}

	/**
	 * @return the dateFrom
	 */
	public DateTime getDateFrom() {
		return dateFrom;
	}

	/**
	 * @param dateFrom the dateFrom to set
	 */
	public void setDateFrom(DateTime dateFrom) {
		this.dateFrom = dateFrom;
	}

	/**
	 * @return the dateTo
	 */
	public DateTime getDateTo() {
		return dateTo;
	}

	/**
	 * @param dateTo the dateTo to set
	 */
	public void setDateTo(DateTime dateTo) {
		this.dateTo = dateTo;
	}
}