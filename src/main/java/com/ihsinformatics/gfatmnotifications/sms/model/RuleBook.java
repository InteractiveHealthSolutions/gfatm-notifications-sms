/* Copyright(C) 2018 Interactive Health Solutions, Pvt. Ltd.

This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as
published by the Free Software Foundation; either version 3 of the License (GPLv3), or any later version.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program; if not, write to the Interactive Health Solutions, info@ihsinformatics.com
You can also access the license on the internet at the address: http://www.gnu.org/licenses/gpl-3.0.html

Interactive Health Solutions, hereby disclaims all copyright interest in this program written by the contributors.
*/
package com.ihsinformatics.gfatmnotifications.sms.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openl.util.Log;

import com.ihsinformatics.gfatmnotifications.common.Context;
import com.ihsinformatics.gfatmnotifications.sms.SmsContext;

/**
 * @author owais.hussain@ihsinformatics.com
 *
 */
public class RuleBook {

	private final Integer typeColumn = Context.getIntegerProperty("rule.type.column");
	private final Integer encounterColumn = Context.getIntegerProperty("rule.encounter.column");
	private final Integer conditionsColumn = Context.getIntegerProperty("rule.conditions.column");
	private final Integer sendToColumn = Context.getIntegerProperty("rule.send_to.column");
	private final Integer scheduleDateColumn = Context.getIntegerProperty("rule.schedule_date.column");
	private final Integer plusMinusColumn = Context.getIntegerProperty("rule.plus_minus.column");
	private final Integer plusMinusUnitColumn = Context.getIntegerProperty("rule.unit.column");
	private final Integer messageCodeColumn = Context.getIntegerProperty("rule.message_code.column");
	private final Integer stopConditionColumn = Context.getIntegerProperty("rule.stop_condition.column");
	private List<Rule> rules;

	public RuleBook() throws IOException {
		Set<File> files = SmsContext.getRuleFiles();
		for (File file : files) {
			if (file.getName().endsWith(Context.getStringProperty("rule.filename"))) {
				FileInputStream fis = new FileInputStream(file);
				// Read Excel document
				Workbook workbook = new XSSFWorkbook(fis);
				// Fetch sheet
				Sheet sheet = workbook.getSheet("Rules");
				setRules(new ArrayList<Rule>());
				for (Row row : sheet) {
					// Skip the header row
					if (row.getRowNum() == 0) {
						continue;
					}
					Rule rule = new Rule();
					rule.setType(row.getCell(typeColumn).getStringCellValue());
					rule.setEncounterType(row.getCell(encounterColumn).getStringCellValue());
					rule.setConditions(row.getCell(conditionsColumn).getStringCellValue());
					rule.setSendTo(row.getCell(sendToColumn).getStringCellValue());
					rule.setScheduleDate(row.getCell(scheduleDateColumn).getStringCellValue());
					rule.setPlusMinus(row.getCell(plusMinusColumn).getNumericCellValue());
					rule.setPlusMinusUnit(row.getCell(plusMinusUnitColumn).getStringCellValue());
					rule.setMessageCode(row.getCell(messageCodeColumn).getStringCellValue());
					try {
						rule.setStopCondition(row.getCell(stopConditionColumn).getStringCellValue());
					} catch (Exception e) {
						Log.warn("Stop condition is undefined.");
					}
					rules.add(rule);
				}
				workbook.close();
			}
		}

	}

	/**
	 * @return the rules
	 */
	public List<Rule> getRules() {
		return rules;
	}

	/**
	 * @param rules the rules to set
	 */
	public void setRules(List<Rule> rules) {
		this.rules = rules;
	}

	public List<Rule> getSmsRules() {
		List<Rule> smsRules = new ArrayList<Rule>();
		for (Rule rule : rules) {
			if (rule.getType().equalsIgnoreCase("SMS")) {
				smsRules.add(rule);
			}
		}
		return smsRules;
	}
}
