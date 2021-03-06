/* Copyright(C) 2018 Interactive Health Solutions, Pvt. Ltd.

This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as
published by the Free Software Foundation; either version 3 of the License (GPLv3), or any later version.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program; if not, write to the Interactive Health Solutions, info@ihsinformatics.com
You can also access the license on the internet at the address: http://www.gnu.org/licenses/gpl-3.0.html

Interactive Health Solutions, hereby disclaims all copyright interest in this program written by the contributors.
*/
package com.ihsinformatics.gfatmnotifications.sms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;

import com.ihsinformatics.gfatmnotifications.common.Context;
import com.ihsinformatics.util.ClassLoaderUtil;
import com.ihsinformatics.util.DateTimeUtil;

/**
 * @author owais.hussain@ihsinformatics.com
 *
 */
@SuppressWarnings("deprecation")
public class SmsContext {
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
	public static int SMS_ALERT_SCHEDULE_INTERVAL_IN_HOURS;

	// How often to check for new SMS notifications in DB
	public static int SMS_REMINDER_SCHEDULE_INTERVAL_IN_HOURS;

	// What time to start schedule on
	public static Date SMS_SCHEDULE_START_TIME;

	static {
		try {
			readMessageProperties();
			Context.initialize(true, false, false);
		} catch (IOException e) {
			e.printStackTrace();
		}
		SMS_SERVER_ADDRESS = Context.getProps().getProperty("sms.server.address",
				"https://ihs.trgccms.com/api/send_sms/");
		SMS_API_KEY = Context.getProps().getProperty("sms.api.key");
		SMS_USE_SSL = Context.getProps().getProperty("sms.server.ssl", "true").equals("true");
		SMS_ALERT_SCHEDULE_INTERVAL_IN_HOURS = Integer
				.parseInt(Context.getProps().getProperty("sms.alert.job.interval", "2"));
		SMS_REMINDER_SCHEDULE_INTERVAL_IN_HOURS = Integer
				.parseInt(Context.getProps().getProperty("sms.reminder.job.interval", "24"));
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
		// Context.getRuleBook().
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
		String message = Context.getRuleBook().getMessages().get(code);
		if (message == null) {
			message = "Message unavailable for code: " + code;
		}
		return message;
	}

	/**
	 * Send SSL-enabled message
	 * 
	 * @param url
	 * @param content
	 * @return
	 * @throws Exception
	 */
	public static String postSecure(String url, String content) throws Exception {
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
	public static String postInsecure(String url, String content) throws Exception {
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
}
