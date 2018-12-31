/* Copyright(C) 2018 Interactive Health Solutions, Pvt. Ltd.

This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as
published by the Free Software Foundation; either version 3 of the License (GPLv3), or any later version.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program; if not, write to the Interactive Health Solutions, info@ihsinformatics.com
You can also access the license on the internet at the address: http://www.gnu.org/licenses/gpl-3.0.html

Interactive Health Solutions, hereby disclaims all copyright interest in this program written by the contributors.
*/
package com.ihsinformatics.gfatmnotifications.sms.service;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.joda.time.DateTime;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.ihsinformatics.gfatmnotifications.common.Context;
import com.ihsinformatics.gfatmnotifications.common.model.Encounter;
import com.ihsinformatics.gfatmnotifications.common.model.Location;
import com.ihsinformatics.gfatmnotifications.common.model.Message;
import com.ihsinformatics.gfatmnotifications.common.model.Observation;
import com.ihsinformatics.gfatmnotifications.common.model.Patient;
import com.ihsinformatics.gfatmnotifications.common.model.Rule;
import com.ihsinformatics.gfatmnotifications.common.model.User;
import com.ihsinformatics.gfatmnotifications.common.util.Decision;
import com.ihsinformatics.gfatmnotifications.common.util.ExcelSheetWriter;
import com.ihsinformatics.gfatmnotifications.common.util.FormattedMessageParser;
import com.ihsinformatics.gfatmnotifications.common.util.ValidationUtil;
import com.ihsinformatics.gfatmnotifications.sms.SmsContext;
import com.ihsinformatics.util.DateTimeUtil;

/**
 * @author owais.hussain@ihsinformatics.com
 *
 */
public class ReminderSmsNotificationsJob extends AbstractSmsNotificationsJob {

	public ReminderSmsNotificationsJob() {
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
		ReminderSmsNotificationsJob smsJob = (ReminderSmsNotificationsJob) dataMap.get("smsReminderJob");
		this.setDateFrom(smsJob.getDateFrom());
		this.setDateTo(smsJob.getDateTo());
		try {
			Context.initialize();
			run(null, null);
			String fileName = Context.getOutputFilePath() + "gfatm-notifications-sms-"
					+ DateTimeUtil.toString(new Date(), DateTimeUtil.SQL_DATE) + ".xlsx";
			ExcelSheetWriter.writeFile(fileName, messages);
			log.info("Message log written on Excel file.");
			System.exit(0);
		} catch (IOException e) {
			log.warning("Unable to initialize context.");
			throw new JobExecutionException(e.getMessage());
		} catch (ParseException | InvalidFormatException e) {
			log.warning("Unable to parse messages.");
			throw new JobExecutionException(e.getMessage());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.ihsinformatics.gfatmnotifications.sms.service.AbstractSmsNotificationsJob
	 * #run(org.joda.time.DateTime, org.joda.time.DateTime)
	 */
	public void run(DateTime from, DateTime to) throws ParseException {
		List<Rule> rules = Context.getRuleBook().getSmsRules();
		// Read each rule and execute the decision
		for (Rule rule : rules) {
			if (rule.getDatabaseConnectionName().toLowerCase().contains("datawarehouse")) {
				dbUtil = Context.getDwDb();
			} else {
				dbUtil = Context.getOpenmrsDb();
			}
			if (rule.getEncounterType() == null) {
				continue;
			}
			// Fetch all the encounters for this type
			List<Encounter> encounters = Context.getEncounters(rule.getFetchDurationDate(),
					new DateTime().minusHours(SmsContext.SMS_REMINDER_SCHEDULE_INTERVAL_IN_HOURS),
					Context.getEncounterTypeId(rule.getEncounterType()), dbUtil);
			log.info("Running rule: " + rule.toString());
			executeRule(encounters, rule);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.ihsinformatics.gfatmnotifications.sms.service.AbstractSmsNotificationsJob
	 * #executeRule(java.util.List,
	 * com.ihsinformatics.gfatmnotifications.common.model.Rule)
	 */
	public void executeRule(List<Encounter> encounters, Rule rule) throws ParseException {
		// patient to whom sms is already sent ?
		Map<Integer, Patient> informedPatients = new HashMap<>();
		for (Encounter encounter : encounters) {
			Patient patient = Context.getPatientByIdentifierOrGeneratedId(encounter.getIdentifier(), null, dbUtil);
			if (patient == null) {
				log.info("Patient does not exits against patient identifier " + encounter.getIdentifier());
				continue;
			}
			if (informedPatients.get(patient.getPersonId()) != null) {
				log.info("SMS already sent to " + patient.getPatientIdentifier());
				continue;
			}
			Location location = Context.getLocationByName(encounter.getLocation(), dbUtil);
			List<Observation> observations = Context.getEncounterObservations(encounter, dbUtil);
			encounter.setObservations(observations);
			boolean isRulePassed = false;
			try {
				isRulePassed = ValidationUtil.validateRule(rule, patient, location, encounter, dbUtil);
			} catch (Exception e1) {
				StringBuilder sb = new StringBuilder().append("Exception thrown while validating rule: ").append(rule)
						.append(" against objects:\r\n").append("Patient:").append(patient.toString()).append("\r\n")
						.append("Encounter:").append(encounter.toString()).append("\r\n").append(e1.getMessage());
				log.warning(sb.toString());
				e1.printStackTrace();
			}
			if (isRulePassed) {
				User user = Context.getUserByUsername(encounter.getUsername(), dbUtil);
				Date sendOn = getSendDate(rule, encounter);
				if (sendOn == null) {
					continue;
				}
				String contact;
				try {
					contact = getContactFromRule(patient, location, encounter, rule);
					if (contact == null) {
						StringBuilder sb = new StringBuilder().append(patient.getPatientIdentifier()).append(" ")
								.append(rule);
						throw new NullPointerException(
								"Contact number is either not available or invalid for transaction " + sb.toString());
					}
				} catch (NullPointerException e) {
					log.warning(e.getMessage());
					continue;
				}
				boolean isPatient = false;
				if (rule.getSendTo().equalsIgnoreCase("patient")) {
					isPatient = true;
					if (Context.getRuleBook().getBlacklistedPatient().contains(patient.getPatientIdentifier())) {
						log.info("Patient : " + patient.getPatientIdentifier() + " is in blocked list.");
						continue;
					}
				}
				if (isPatient) {
					informedPatients.put(patient.getPersonId(), patient);
				}
				String preparedMessage = messageParser.parseFormattedMessage(
						SmsContext.getMessage(rule.getMessageCode()), encounter, patient, user, location);
				messages.add(new Message(preparedMessage, contact, encounter.getEncounterType(),
						DateTimeUtil.toSqlDateTimeString(new Date()), DateTimeUtil.toSqlDateTimeString(sendOn),
						rule.getSendTo(), rule));
				log.info("Sending message: \"" + preparedMessage + "\" to " + contact);
				if (!rule.getRecordOnly()) {
					sendNotification(contact, preparedMessage, Context.PROJECT_NAME, sendOn);
				}
			}
		}
	}
}