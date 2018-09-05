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

import com.ihsinformatics.gfatmnotifications.common.Constant;
import com.ihsinformatics.util.ClassLoaderUtil;

/**
 * @author owais.hussain@ihsinformatics.com
 *
 */
public class SmsConstant extends Constant {

	// Link to the SMS service API
	public static final String SMS_SERVER_ADDRESS;
	// API Key
	public static final String SMS_API_KEY;
	// Whether or not to use SSL encryption
	public static final Boolean SMS_USE_SSL;
	// How often to check for new SMS notifications in DB
	public static final int SMS_SCHEDULE_INTERVAL_IN_HOURS;

	static {
		try {
			readProperties();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		} finally {
			SMS_SERVER_ADDRESS = getProps().getProperty("sms.server.address", "https://ihs.trgccms.com/api/send_sms/");
			SMS_API_KEY = getProps().getProperty("sms.api.key", "aWhzc21zOnVsNjJ6eDM=");
			SMS_USE_SSL = getProps().getProperty("sms.server.ssl", "true").equals("true");
			SMS_SCHEDULE_INTERVAL_IN_HOURS = Integer.parseInt(getProps().getProperty("sms.interval", "24"));
		}
	}

	public static void readProperties() throws IOException {
		InputStream inputStream = ClassLoaderUtil.getResourceAsStream(PROP_FILE_NAME, SmsConstant.class);
		if (inputStream != null) {
			getProps().load(inputStream);
		}
	}

}
