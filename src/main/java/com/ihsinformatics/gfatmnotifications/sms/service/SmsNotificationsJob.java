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
				if (ValidationUtil.validateConditions(patient, location, encounter, rule)) {
					User user = Context.getUserByUsername(encounter.getUsername());

					String preparedMessage = messageParser.parseFormattedMessage(
							SmsContext.getMessage(rule.getMessageCode()), encounter, patient, user, location);
					System.out.println(preparedMessage);
					Date sendOn = new Date();
					// String dateField = rule.getScheduleDate();
					DateTime referenceDate = null;

					try {
						referenceDate = Context.getReferenceDate(rule.getScheduleDate(), encounter);
						sendOn = Context.calculateScheduleDate(referenceDate, rule.getPlusMinus(), rule.getPlusMinusUnit());
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
							if (!ValidationUtil.validateStopConditions(patient, location, encounter, rule)) {
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
				response = SmsContext.postSecure(SmsContext.SMS_SERVER_ADDRESS, content.toString());
			} else {
				response = SmsContext.postInsecure(SmsContext.SMS_SERVER_ADDRESS, content.toString());
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