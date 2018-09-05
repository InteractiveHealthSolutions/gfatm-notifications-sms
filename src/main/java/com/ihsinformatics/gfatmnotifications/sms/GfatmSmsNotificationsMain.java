/* Copyright(C) 2017 Interactive Health Solutions, Pvt. Ltd.

This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as
published by the Free Software Foundation; either version 3 of the License (GPLv3), or any later version.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program; if not, write to the Interactive Health Solutions, info@ihsinformatics.com
You can also access the license on the internet at the address: http://www.gnu.org/licenses/gpl-3.0.html

Interactive Health Solutions, hereby disclaims all copyright interest in this program written by the contributors.
 */
package com.ihsinformatics.gfatmnotifications.sms;

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

import com.ihsinformatics.gfatmnotifications.sms.controller.SmsController;
import com.ihsinformatics.gfatmnotifications.sms.service.SmsNotificationsJob;

/**
 * @author owais.hussain@ihsinformatics.com
 * @author Shujaat
 *
 */

public class GfatmSmsNotificationsMain {

	private static final Logger log = Logger.getLogger(Class.class.getName());
	private Scheduler smsScheduler;

	/**
	 * @param args
	 * @throws InputRequiredException
	 * @throws DatabaseUpdateException
	 * @throws ModuleMustStartException
	 */
	public static void main(String[] args) {
		try {
			log.info("SMS Notification execution is complete on : " + new Date());
		} catch (Exception e) {
			log.info("Exception : " + e.getMessage());
		}
	}

	public void createSmsJob() throws SchedulerException {
		DateTime from = new DateTime();
		from.minusHours(SmsConstant.SMS_SCHEDULE_INTERVAL_IN_HOURS);
		DateTime to = new DateTime();
		smsScheduler = StdSchedulerFactory.getDefaultScheduler();
		JobDetail smsJob = JobBuilder.newJob(SmsNotificationsJob.class).withIdentity("smsJob", "smsGroup").build();
		SmsNotificationsJob smsJobObj = new SmsNotificationsJob();
		smsJobObj.setLocalDb(SmsConstant.getLocalDb());
		smsJobObj.setOpenmrs(new OpenMrsUtil(UtilityCollection.getInstance().getLocalDb()));
		smsJobObj.setDateFrom(from);
		smsJobObj.setDateTo(to);
		smsJobObj.setSmsController(
				new SmsController(SmsConstant.SMS_SERVER_ADDRESS, SmsConstant.SMS_API_KEY, SmsConstant.SMS_USE_SSL));
		smsJob.getJobDataMap().put("smsJob", smsJobObj);

		SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
				.withIntervalInMinutes(SmsConstant.SMS_SCHEDULE_INTERVAL_IN_HOURS).repeatForever();

		Trigger trigger = TriggerBuilder.newTrigger().withIdentity("smsTrigger", "smsGroup")
				.withSchedule(scheduleBuilder).build();
		smsScheduler.scheduleJob(smsJob, trigger);
		smsScheduler.start();
	}
}
