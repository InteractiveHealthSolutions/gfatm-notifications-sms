/* Copyright(C) 2018 Interactive Health Solutions, Pvt. Ltd.

This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as
published by the Free Software Foundation; either version 3 of the License (GPLv3), or any later version.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program; if not, write to the Interactive Health Solutions, info@ihsinformatics.com
You can also access the license on the internet at the address: http://www.gnu.org/licenses/gpl-3.0.html

Interactive Health Solutions, hereby disclaims all copyright interest in this program written by the contributors.
*/
package com.ihsinformatics.gfatmnotifications.sms;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import com.ihsinformatics.gfatmnotifications.common.Context;
import com.ihsinformatics.util.ClassLoaderUtil;
import com.ihsinformatics.util.DateTimeUtil;

/**
 * @author owais.hussain@ihsinformatics.com
 *
 */
@SuppressWarnings("deprecation")
public class SmsContext {

	// Path to the directory where all rule files are stored
	public static final String RULE_DIRECTORY = "rules";

	// Message file path
	private static final String MESSAGE_PROP_FILE = "message.properties";

	// Where all codes and their respective messages are mapped
	private static Properties messages;

	// Link to the SMS service API
	public static final String SMS_SERVER_ADDRESS;

	// API Key
	public static final String SMS_API_KEY;

	// Whether or not to use SSL encryption
	public static final Boolean SMS_USE_SSL;

	// How often to check for new SMS notifications in DB
	public static int SMS_SCHEDULE_INTERVAL_IN_HOURS;

	// What time to start schedule on
	public static Date SMS_SCHEDULE_START_TIME;

	static {
		try {
			Context.initialize();
			readMessageProperties();
		} catch (IOException e) {
			e.printStackTrace();
		}
		SMS_SERVER_ADDRESS = Context.getProps().getProperty("sms.server.address",
				"https://ihs.trgccms.com/api/send_sms/");
		SMS_API_KEY = Context.getProps().getProperty("sms.api.key", "aWhzc21zOnVsNjJ6eDM=");
		SMS_USE_SSL = Context.getProps().getProperty("sms.server.ssl", "true").equals("true");
		SMS_SCHEDULE_INTERVAL_IN_HOURS = Integer.parseInt(Context.getProps().getProperty("sms.job.interval", "24"));
		String timeStr = Context.getProps().getProperty("sms.job.start_time", "00:00:00");
		SMS_SCHEDULE_START_TIME = new Date();
		Date scheduleTime = DateTimeUtil.fromString(timeStr, DateTimeUtil.detectDateFormat(timeStr));
		SMS_SCHEDULE_START_TIME.setHours(scheduleTime.getHours());
		SMS_SCHEDULE_START_TIME.setMinutes(scheduleTime.getMinutes());
		SMS_SCHEDULE_START_TIME.setSeconds(scheduleTime.getSeconds());
	}

	/**
	 * Read properties from PROP_FILE
	 * 
	 * @throws IOException
	 */
	public static void readMessageProperties() throws IOException {
		//Context.getRuleBook().
		InputStream inputStream = ClassLoaderUtil.getResourceAsStream(MESSAGE_PROP_FILE, SmsContext.class);
		if (inputStream != null) {
			messages = new Properties();
			messages.load(inputStream);
		}
	}

	/**
	 * Read message text from messages file by code and return
	 * 
	 * @param code
	 * @return
	 */
	public static String getMessage(String code) {
		String message=Context.getRuleBook().getMessages().get(code);
		//String message = messages.getProperty(code);
		if (message == null) {
			message = "Message unavailable for code: " + code;
		}
		return message;
	}
}
