/* Copyright(C) 2018 Interactive Health Solutions, Pvt. Ltd.

This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as
published by the Free Software Foundation; either version 3 of the License (GPLv3), or any later version.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program; if not, write to the Interactive Health Solutions, info@ihsinformatics.com
You can also access the license on the internet at the address: http://www.gnu.org/licenses/gpl-3.0.html

Interactive Health Solutions, hereby disclaims all copyright interest in this program written by the contributors.
*/
package com.ihsinformatics.gfatmnotifications.sms.service;

import java.io.File;
import java.util.logging.Logger;

import org.apache.commons.io.FilenameUtils;
import org.openl.rules.runtime.RulesEngineFactory;

import com.ihsinformatics.gfatmnotifications.sms.SmsContext;

/**
 * @author owais.hussain@ihsinformatics.com
 *
 */
public class RuleEngineService {

	private static final Logger log = Logger.getLogger(Class.class.getName());

	public RuleEngineService() {
		SmsContext.loadRuleFiles();
	}

	public void testRun() {
		for (File ruleFile : SmsContext.getRuleFiles()) {
			log.info("Processing: " + ruleFile.getName());
			Class<?> clazz = null;
			try {
				String fileName = FilenameUtils.removeExtension(ruleFile.getName());
				clazz = Class.forName(this.getClass().getPackage().getName() + "." + fileName);
				if (!clazz.isInterface()) {
					System.out.println("Not an interface...");
				}
			} catch (ClassNotFoundException e) {
				log.warning(e.getMessage());
			}
			RulesEngineFactory<?> rulesFactory = new RulesEngineFactory<>(ruleFile.getPath(), clazz);
			RuleExamples ruleAction = (RuleExamples) rulesFactory.newInstance();

			String code = ruleAction.testExactMatch("CAD4TB");
			System.out.println(code + " = " + SmsContext.getMessage(code));

			code = ruleAction.testDecisionTree("XDR-TB");
			System.out.println(code + " = " + SmsContext.getMessage(code));

			code = ruleAction.testRange(12);
			System.out.println(code + " = " + SmsContext.getMessage(code));

		}
	}
}
