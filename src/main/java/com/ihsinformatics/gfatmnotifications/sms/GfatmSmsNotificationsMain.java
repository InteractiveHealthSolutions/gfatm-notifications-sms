/* Copyright(C) 2017 Interactive Health Solutions, Pvt. Ltd.

This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as
published by the Free Software Foundation; either version 3 of the License (GPLv3), or any later version.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program; if not, write to the Interactive Health Solutions, info@ihsinformatics.com
You can also access the license on the internet at the address: http://www.gnu.org/licenses/gpl-3.0.html

Interactive Health Solutions, hereby disclaims all copyright interest in this program written by the contributors.
 */
package com.ihsinformatics.gfatmnotifications.sms;

import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import com.ihsinformatics.gfatmnotifications.sms.service.SmsNotificationsJob;

/**
 * @author owais.hussain@ihsinformatics.com
 *
 */

public class GfatmSmsNotificationsMain {

	// Detect whether the app is running in DEBUG mode or not
	private static final boolean DEBUG_MODE = ManagementFactory.getRuntimeMXBean().getInputArguments().toString()
			.indexOf("-agentlib:jdwp") > 0;
	private static final Logger log = Logger.getLogger(Class.class.getName());

	/**
	 * @param args
	 * @throws InputRequiredException
	 * @throws DatabaseUpdateException
	 * @throws ModuleMustStartException
	 */
	public static void main(String[] args) {
		System.out.println("Is this Debug mode? " + DEBUG_MODE);
		try {
			// Set date/time from and to
			DateTime from = new DateTime();
			from = from.minusHours(SmsContext.SMS_SCHEDULE_INTERVAL_IN_HOURS);
			DateTime to = new DateTime();

			// Create SMS Job
			JobDetail smsJob = JobBuilder.newJob(SmsNotificationsJob.class).withIdentity("smsJob", "smsGroup").build();
			SmsNotificationsJob smsJobObj = new SmsNotificationsJob();
			smsJobObj.setDateFrom(from);
			smsJobObj.setDateTo(to);
			smsJob.getJobDataMap().put("smsJob", smsJobObj);

			// Create trigger with given interval and start time
			SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
					.withIntervalInMinutes(SmsContext.SMS_SCHEDULE_INTERVAL_IN_HOURS).repeatForever();
			// In debug mode, run immediately
			if (DEBUG_MODE) {
				SmsContext.SMS_SCHEDULE_START_TIME = new Date(new Date().getTime() + 10);
			}
			Trigger trigger = TriggerBuilder.newTrigger().withIdentity("smsTrigger", "smsGroup")
					.withSchedule(scheduleBuilder).startAt(SmsContext.SMS_SCHEDULE_START_TIME).build();
			Scheduler smsScheduler = null;
			smsScheduler = StdSchedulerFactory.getDefaultScheduler();
			smsScheduler.scheduleJob(smsJob, trigger);
			smsScheduler.start();
		} catch (SchedulerException e) {
			log.severe(e.getMessage());
			System.exit(-1);
		}
	}
}
