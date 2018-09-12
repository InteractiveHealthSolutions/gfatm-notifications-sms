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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.joda.time.DateTime;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.ihsinformatics.gfatmnotifications.common.Context;
import com.ihsinformatics.gfatmnotifications.common.service.NotificationService;
import com.ihsinformatics.gfatmnotifications.sms.SmsContext;
import com.ihsinformatics.util.DateTimeUtil;

/**
 * @author owais.hussain@ihsinformatics.com
 *
 */
public class SmsNotificationsJob implements NotificationService {

	private static final Logger log = Logger.getLogger(Class.class.getName());

	private DateTime dateFrom;
	private DateTime dateTo;

	public SmsNotificationsJob() {
		HostnameVerifier hostNameVerifier = new HostnameVerifier() {
			@Override
			public boolean verify(String hostname, SSLSession session) {
				return hostname.equals(SmsContext.SMS_SERVER_ADDRESS);
			}
		};
		HttpsURLConnection.setDefaultHostnameVerifier(hostNameVerifier);
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
			System.out.println(getDateFrom() + " " + getDateTo());

			// executeFastSms(dateFrom, dateTo);
		} catch (IOException e) {
			log.warning("Unable to initialize context.");
			throw new JobExecutionException(e.getMessage());
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
				response = postSecure(SmsContext.SMS_SERVER_ADDRESS, content.toString());
			} else {
				response = postInsecure(SmsContext.SMS_SERVER_ADDRESS, content.toString());
			}
		} catch (UnsupportedEncodingException e) {
			log.log(Level.SEVERE, e.getMessage());
		} catch (Exception e) {
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
		URL obj = new URL(url);
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