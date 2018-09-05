/* Copyright(C) 2017 Interactive Health Solutions, Pvt. Ltd.

This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as
published by the Free Software Foundation; either version 3 of the License (GPLv3), or any later version.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program; if not, write to the Interactive Health Solutions, info@ihsinformatics.com
You can also access the license on the internet at the address: http://www.gnu.org/licenses/gpl-3.0.html

Interactive Health Solutions, hereby disclaims all copyright interest in this program written by the contributors.
 */

package com.ihsinformatics.gfatmnotifications.sms.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.cert.Certificate;
import java.util.Date;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import com.ihsinformatics.util.DateTimeUtil;

/**
 * @author owais.hussain@ihsinformatics.com
 *
 */
public class SmsController {

	private String serverAddress;
	private String apiKey;
	private boolean useSsl;

	public SmsController(final String serverAddress, final String apiKey, boolean useSsl) {
		this.serverAddress = serverAddress;
		this.apiKey = apiKey;
		this.useSsl = useSsl;
		HostnameVerifier hostNameVerifier = new HostnameVerifier() {
			@Override
			public boolean verify(String hostname, SSLSession session) {
				return hostname.equals(serverAddress);
			}
		};
		HttpsURLConnection.setDefaultHostnameVerifier(hostNameVerifier);
	}

	@SuppressWarnings("deprecation")
	public String createSms(String sendTo, String message, Date sendOn, String projectId, String additionalInfo)
			throws Exception {

		StringBuffer content = new StringBuffer();
		content.append("send_to=" + sendTo + "&");
		content.append("message=" + URLEncoder.encode(message, "UTF-8") + "&");
		content.append("schedule_time=" + URLEncoder.encode(DateTimeUtil.getSqlDateTime(sendOn), "UTF-8") + "&");
		content.append("project_id=" + projectId + "&");
		String response = null;
		if (useSsl) {
			response = postSecure(serverAddress, content.toString());
		} else {
			response = postInsecure(serverAddress, content.toString());
		}
		return response;
	}

	public String postSecure(String url, String content) throws Exception {
		URL obj = new URL(url);
		HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
		con.setRequestMethod("POST");
		con.setRequestProperty("Accept", "application/json");
		con.setRequestProperty("Content-Type", "text/html; charset=UTF-8");
		con.setRequestProperty("Authorization", "Basic " + apiKey);
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

	public String postInsecure(String url, String content) throws Exception {
		URL obj = new URL(url + "?" + content);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setRequestMethod("POST");
		con.setRequestProperty("Accept", "application/json");
		con.setRequestProperty("Content-Encoding", "gzip");
		con.setRequestProperty("Content-Type", "text/html; charset=UTF-8");
		con.setRequestProperty("Authorization", "Basic " + apiKey);
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

	public void testMe(String httpsUrl, boolean printCertificate) {
		try {
			URL url = new URL(httpsUrl);
			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
			if (printCertificate) {
				try {
					System.out.println("Response Code : " + con.getResponseCode());
					System.out.println("Cipher Suite : " + con.getCipherSuite());
					System.out.println("\n");
					Certificate[] certs = con.getServerCertificates();
					for (Certificate cert : certs) {
						System.out.println("Cert Type : " + cert.getType());
						System.out.println("Cert Hash Code : " + cert.hashCode());
						System.out.println("Cert Public Key Algorithm : " + cert.getPublicKey().getAlgorithm());
						System.out.println("Cert Public Key Format : " + cert.getPublicKey().getFormat());
						System.out.println("\n");
					}
				} catch (SSLPeerUnverifiedException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
