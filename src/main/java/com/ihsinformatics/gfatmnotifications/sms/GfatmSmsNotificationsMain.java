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

import com.ihsinformatics.gfatmnotifications.sms.service.ReminderSmsNotificationsJob;
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

		
			// In debug mode, run immediately
			if (DEBUG_MODE) {
				SmsContext.SMS_SCHEDULE_START_TIME = new Date(new Date().getTime() + 150 ); 
			}
			// Create trigger with given interval and start time
			SimpleScheduleBuilder alertScheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
					.withIntervalInMinutes(SmsContext.SMS_SCHEDULE_INTERVAL_IN_HOURS).repeatForever();

			Trigger trigger = TriggerBuilder.newTrigger().withIdentity("smsTrigger", "smsGroup")
					.withSchedule(alertScheduleBuilder).startAt(SmsContext.SMS_SCHEDULE_START_TIME).build();

			// Create SMS Job 
			JobDetail smsJob2 = JobBuilder.newJob(ReminderSmsNotificationsJob.class).withIdentity("smsJob2", "smsGroup")
					.build();
			ReminderSmsNotificationsJob smsJobObj2 = new ReminderSmsNotificationsJob();
			smsJobObj2.setDateFrom(from);
			smsJobObj2.setDateTo(to);
			smsJob2.getJobDataMap().put("smsJob2", smsJobObj2);
			// Create trigger with given interval and start time
			SimpleScheduleBuilder reminderScheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
					.withIntervalInHours(24).repeatForever();
			Trigger trigger2 = TriggerBuilder.newTrigger().withIdentity("smsReminderTrigger", "smsGroup")
					.withSchedule(reminderScheduleBuilder).startAt(SmsContext.SMS_SCHEDULE_START_TIME).build();
			Scheduler smsScheduler = null;
			smsScheduler = StdSchedulerFactory.getDefaultScheduler();
			//smsScheduler.scheduleJob(smsJob, trigger);
			smsScheduler.scheduleJob(smsJob2, trigger2);
			smsScheduler.start();
		} catch (SchedulerException e) {
			log.severe(e.getMessage());
			System.exit(-1);
		}
	}
}
