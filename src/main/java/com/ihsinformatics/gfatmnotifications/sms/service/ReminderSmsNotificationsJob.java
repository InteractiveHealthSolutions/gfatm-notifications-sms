package com.ihsinformatics.gfatmnotifications.sms.service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
import com.ihsinformatics.gfatmnotifications.common.model.Encounter;
import com.ihsinformatics.gfatmnotifications.common.model.Location;
import com.ihsinformatics.gfatmnotifications.common.model.Message;
import com.ihsinformatics.gfatmnotifications.common.model.Observation;
import com.ihsinformatics.gfatmnotifications.common.model.Patient;
import com.ihsinformatics.gfatmnotifications.common.model.Rule;
import com.ihsinformatics.gfatmnotifications.common.model.User;
import com.ihsinformatics.gfatmnotifications.common.service.NotificationService;
import com.ihsinformatics.gfatmnotifications.common.util.CsvFileWriter;
import com.ihsinformatics.gfatmnotifications.common.util.Decision;
import com.ihsinformatics.gfatmnotifications.common.util.FormattedMessageParser;
import com.ihsinformatics.gfatmnotifications.common.util.ValidationUtil;
import com.ihsinformatics.gfatmnotifications.sms.SmsContext;
import com.ihsinformatics.util.DatabaseUtil;
import com.ihsinformatics.util.DateTimeUtil;

public class ReminderSmsNotificationsJob implements NotificationService {

	private static final Logger log = Logger.getLogger(Class.class.getName());

	private DateTime dateFrom;
	private DateTime dateTo;
	private DatabaseUtil dbUtil;
	private static Properties props;
	private FormattedMessageParser messageParser;
	private List<Message> messages=new ArrayList<>();
	 private String fileName = System.getProperty("user.home")+"/reminder.csv";
	private static final boolean DEBUG_MODE = ManagementFactory.getRuntimeMXBean().getInputArguments().toString()
			.indexOf("-agentlib:jdwp") > 0;

	public ReminderSmsNotificationsJob() {
		HostnameVerifier hostNameVerifier = new HostnameVerifier() {
			@Override
			public boolean verify(String hostname, SSLSession session) {
				return hostname.equals(SmsContext.SMS_SERVER_ADDRESS);
			}
		};
		HttpsURLConnection.setDefaultHostnameVerifier(hostNameVerifier);
		messageParser = new FormattedMessageParser(Decision.LEAVE_EMPTY);
		props = Context.getProps();
	}

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		JobDataMap dataMap = context.getMergedJobDataMap();
		ReminderSmsNotificationsJob smsJob = (ReminderSmsNotificationsJob) dataMap.get("smsJob2");
		this.setDateFrom(smsJob.getDateFrom());
		this.setDateTo(smsJob.getDateTo());
		try {
			Context.initialize();
			setDateFrom(getDateFrom().minusHours(24));
			log.info(getDateFrom() + " " + getDateTo());

			DateTime from = DateTime.now().minusDays(2);// minusMonths(12);
			DateTime to = DateTime.now().minusMonths(0);

			run();
			CsvFileWriter.writeCsvFile(fileName,messages);
		} catch (IOException e) {
			log.warning("Unable to initialize context.");
			throw new JobExecutionException(e.getMessage());
		}   catch (ParseException e) { log.warning("Unable to parse messages."); throw
			  new JobExecutionException(e.getMessage()); }
			 

	}

	private void run() throws ParseException {
		List<Rule> rules = Context.getRuleBook().getSmsRules();
		// Read each rule and execute the decision
		for (Rule rule : rules) {
			if(rule.getDatabaseConnectionName().trim().equalsIgnoreCase(props.getProperty("db.connection.openmrs").trim())){
				dbUtil = Context.getOpenmrsDb();
			}else if(rule.getDatabaseConnectionName().trim().equalsIgnoreCase(props.getProperty("db.connection.dwh").trim())){
				dbUtil = Context.getDwDb();
			}
			
			if (rule.getEncounterType() == null) {
				continue;
			}
			if (rule.getPlusMinusUnit().equalsIgnoreCase("hours")) {
				continue;
			}
			
			DateTime from = rule.getFetchDurationDate();
			DateTime to=new DateTime();
			//default value if there is no reference/fetch duration is not defined
			if(from==null) {
				from=to.minusMonths(2);
			}
			// Fetch all the encounters for this type ,the third on your choice 
			List<Encounter> encounters = Context.getEncounters(from, to,
					Context.getEncounterTypeId(rule.getEncounterType()),dbUtil);

			// patient to whom sms is already sent ?
			Map<Integer, Patient> imformedPatients = new HashMap<Integer, Patient>();
			for (Encounter encounter : encounters) {
				Patient patient = Context.getPatientByIdentifier(encounter.getIdentifier(),dbUtil);
				if(patient==null) {
					continue;
				}
				if (imformedPatients.get(patient.getPersonId()) != null) {
					System.out.println("Sms already sent to Patient with identifer =" + patient.getPatientIdentifier());
					continue;
				}

				Location location = Context.getLocationByName(encounter.getLocation(),dbUtil);
				List<Observation> observations = Context.getEncounterObservations(encounter,dbUtil);
				encounter.setObservations(observations);
				if (ValidationUtil.validateConditions(patient, location, encounter, rule)) {
					User user = Context.getUserByUsername(encounter.getUsername(),dbUtil);

					String preparedMessage = messageParser.parseFormattedMessage(
							SmsContext.getMessage(rule.getMessageCode()), encounter, patient, user, location);
					System.out.println(preparedMessage);
					Date sendOn = new Date();
					// String dateField = rule.getScheduleDate();
					DateTime referenceDate = null;

					try {
						referenceDate = Context.getReferenceDate(rule.getScheduleDate(), encounter);
						sendOn = Context.calculateScheduleDate(referenceDate, rule.getPlusMinus(), rule.getPlusMinusUnit());
					} catch (Exception e) {
						e.printStackTrace();
					}
					String contactNumber = null;
					boolean isItPatient = false;
					if (rule.getSendTo().equalsIgnoreCase("patient")) {
						contactNumber = patient.getPrimaryContact();
						if (patient.getConsent().equalsIgnoreCase("1066")) {
							log.info("Patient : " + patient.getPatientIdentifier() + "  doesnot want to receive SMS!");

							continue;
						}

						if (Context.getRuleBook().getBlacklistedPatient().contains(patient.getPatientIdentifier())) {
							log.info("Patient : " + patient.getPatientIdentifier() + "  is in blacklist!");

							continue;
						}
						
						  if(!ValidationUtil.isValidContactNumber(contactNumber)) {
						  log.info("Patient : "+patient.getPatientIdentifier()
						  +"  doesnot have an valid number!"); 
						  continue; 
						  }
						 
						isItPatient = true;

					} else if (rule.getSendTo().equalsIgnoreCase("doctor")) {
						//TODO  to be decided
						log.severe("SMS could not be send because doctor are not decided yet!");
						continue;
					} else if (rule.getSendTo().equalsIgnoreCase("supervisor") || rule.getSendTo().equalsIgnoreCase("facility")) {
						contactNumber=location.getPrimaryContact();

					}
					
					if (sendOn != null) {
						DateTime now = new DateTime().plusMinutes(10);
						//DateTime beforeNow = now.minusHours(SmsContext.);
						//if (sendOn.getTime() > beforeNow.getMillis() && sendOn.getTime() <= now.getMillis()) {
							if (!ValidationUtil.validateStopConditions(patient, location, encounter, rule,dbUtil)) {
								// In debug mode
								if (DEBUG_MODE) {
									
									messages.add(new Message(preparedMessage,contactNumber,Context.PROJECT_NAME, sendOn));
								}else {
							//	sendNotification(contactNumber, preparedMessage, Context.PROJECT_NAME, sendOn);
								}
								if (isItPatient) {
									imformedPatients.put(patient.getPersonId(), patient);
								}
							}

						//}
					}
				}
			}
		}
		
		
	}

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
