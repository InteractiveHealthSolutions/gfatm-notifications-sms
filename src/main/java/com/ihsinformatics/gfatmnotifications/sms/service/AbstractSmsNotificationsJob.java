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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.DateTime;

import com.ihsinformatics.gfatmnotifications.common.Context;
import com.ihsinformatics.gfatmnotifications.common.model.Encounter;
import com.ihsinformatics.gfatmnotifications.common.model.Location;
import com.ihsinformatics.gfatmnotifications.common.model.Message;
import com.ihsinformatics.gfatmnotifications.common.model.Patient;
import com.ihsinformatics.gfatmnotifications.common.model.Rule;
import com.ihsinformatics.gfatmnotifications.common.model.User;
import com.ihsinformatics.gfatmnotifications.common.service.NotificationService;
import com.ihsinformatics.gfatmnotifications.common.service.SearchService;
import com.ihsinformatics.gfatmnotifications.common.util.FormattedMessageParser;
import com.ihsinformatics.gfatmnotifications.common.util.ValidationUtil;
import com.ihsinformatics.gfatmnotifications.sms.GfatmSmsNotificationsMain;
import com.ihsinformatics.gfatmnotifications.sms.SmsContext;
import com.ihsinformatics.util.DatabaseUtil;
import com.ihsinformatics.util.DateTimeUtil;

/**
 * Super class for all SMS notification service implementations
 * 
 * @author owais.hussain@ihsinformatics.com
 *
 */
public abstract class AbstractSmsNotificationsJob implements NotificationService {
	protected static final Logger log = Logger.getLogger(Class.class.getName());
	protected List<Message> messages = new ArrayList<>();

	protected DatabaseUtil dbUtil;
	protected DateTime dateFrom;
	protected DateTime dateTo;
	protected FormattedMessageParser messageParser;

	/*
	 * @see
	 * com.ihsinformatics.gfatmnotifications.common.service.NotificationService#
	 * sendNotification(java.lang.String, java.lang.String, java.lang.String)
	 */
	@SuppressWarnings("deprecation")
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

	/**
	 * Fetch contact number from the field specified in Rule.sendTo. Also checks if
	 * a number is valid
	 * 
	 * @param patient
	 * @param location
	 * @param encounter
	 * @param rule
	 * @return
	 */
	public String getContactFromRule(Patient patient, Location location, Encounter encounter, Rule rule)
			throws NullPointerException {
		String contact = null;
		switch (rule.getSendTo().toLowerCase()) {
		case "patient":
			contact = patient.getPrimaryContact();
			break;
		case "facility":
		case "location":
		case "supervisor":
			contact = location.getPrimaryContact();
			break;
		case "user":
			User user = Context.getUserByUsername(encounter.getUsername(), dbUtil);
			if (user != null) {
				contact = user.getPrimaryContact();
			}
			break;
		default:
			contact = new SearchService(dbUtil).searchContactFromEntityValuePair(patient, encounter, rule.getSendTo());
		}
		if (contact == null || !ValidationUtil.isValidContactNumber(contact)) {
			return null;
		}
		return contact;
	}

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

	/**
	 * Get date to send message from given Rule and Encounter objects
	 * 
	 * @param rule
	 * @param encounter
	 * @return
	 */
	public Date getSendDate(Rule rule, Encounter encounter) {
		Date sendOn = new Date();
		try {
			DateTime referenceDate = Context.getReferenceDate(rule.getScheduleDate(), encounter);
			sendOn = Context.calculateScheduleDate(referenceDate, rule.getPlusMinus(), rule.getPlusMinusUnit());
			DateTime now = new DateTime();
			DateTime beforeNow = now.minusHours(SmsContext.SMS_ALERT_SCHEDULE_INTERVAL_IN_HOURS);
			if (!(sendOn.getTime() >= beforeNow.getMillis() && sendOn.getTime() <= now.getMillis())) {
				if (!GfatmSmsNotificationsMain.DEBUG_MODE) {
					return null;
				}
			}
		} catch (Exception e) {
			log.warning(e.getMessage());
		}
		return sendOn;
	}

	/**
	 * Runs the notifications job on {@link Encounter} objects fetched from given
	 * date range
	 * 
	 * @param from
	 * @param to
	 * @throws ParseException
	 * @throws IOException
	 */
	public abstract void run(DateTime from, DateTime to) throws ParseException, IOException;

	/**
	 * Sends notifications on each of the {@link Encounter} objects according the
	 * the provided {@link Rule} object
	 * 
	 * @param encounters
	 * @param rule
	 * @throws ParseException
	 */
	public abstract void executeRule(List<Encounter> encounters, Rule rule) throws ParseException;

}
