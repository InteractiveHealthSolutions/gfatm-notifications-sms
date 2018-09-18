/* Copyright(C) 2018 Interactive Health Solutions, Pvt. Ltd.

This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as
published by the Free Software Foundation; either version 3 of the License (GPLv3), or any later version.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program; if not, write to the Interactive Health Solutions, info@ihsinformatics.com
You can also access the license on the internet at the address: http://www.gnu.org/licenses/gpl-3.0.html

Interactive Health Solutions, hereby disclaims all copyright interest in this program written by the contributors.
*/
package com.ihsinformatics.gfatmnotifications.sms.model;

/**
 * @author owais.hussain@ihsinformatics.com
 *
 */
public class Rule {

	private String type;
	private String encounterType;
	private String conditions;
	private String sendTo;
	private String scheduleDate;
	private Double plusMinus;
	private String plusMinusUnit;
	private String messageCode;
	private String stopCondition;

	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * @param type the type to set
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * @return the encounter
	 */
	public String getEncounterType() {
		return encounterType;
	}

	/**
	 * @param encounterType the encounterType to set
	 */
	public void setEncounterType(String encounterType) {
		this.encounterType = encounterType;
	}

	/**
	 * @return the conditions
	 */
	public String getConditions() {
		return conditions;
	}

	/**
	 * @param conditions the conditions to set
	 */
	public void setConditions(String conditions) {
		this.conditions = conditions;
	}

	/**
	 * @return the sendTo
	 */
	public String getSendTo() {
		return sendTo;
	}

	/**
	 * @param sendTo the sendTo to set
	 */
	public void setSendTo(String sendTo) {
		this.sendTo = sendTo;
	}

	/**
	 * @return the scheduleDate
	 */
	public String getScheduleDate() {
		return scheduleDate;
	}

	/**
	 * @param scheduleDate the scheduleDate to set
	 */
	public void setScheduleDate(String scheduleDate) {
		this.scheduleDate = scheduleDate;
	}

	/**
	 * @return the plusMinus
	 */
	public Double getPlusMinus() {
		return plusMinus;
	}

	/**
	 * @param plusMinus the plusMinus to set
	 */
	public void setPlusMinus(Double plusMinus) {
		this.plusMinus = plusMinus;
	}

	/**
	 * @return the plusMinusUnit
	 */
	public String getPlusMinusUnit() {
		return plusMinusUnit;
	}

	/**
	 * @param plusMinusUnit the plusMinusUnit to set
	 */
	public void setPlusMinusUnit(String plusMinusUnit) {
		this.plusMinusUnit = plusMinusUnit;
	}

	/**
	 * @return the messageCode
	 */
	public String getMessageCode() {
		return messageCode;
	}

	/**
	 * @param messageCode the messageCode to set
	 */
	public void setMessageCode(String messageCode) {
		this.messageCode = messageCode;
	}

	/**
	 * @return the stopCondition
	 */
	public String getStopCondition() {
		return stopCondition;
	}

	/**
	 * @param stopCondition the stopCondition to set
	 */
	public void setStopCondition(String stopCondition) {
		this.stopCondition = stopCondition;
	}

}
