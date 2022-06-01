/*
 * Copyright (C) 2022 by Sebastian Hasait (sebastian at hasait dot de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.hasait.cipa.log

import com.cloudbees.groovy.cps.NonCPS
import hudson.model.TaskListener

/**
 * Logger for Pipeline Scripts.
 *
 * <p>Default logLevel can be overridden by defining a Field in the Pipeline Script with name: <code>PLogger_logLevel_<i>topic</i></code></p>
 * <p>Example:<br>
 * <pre>
 * &#64;groovy.transform.Field
 * def PLogger_logLevel_Cipa=de.hasait.cipa.log.PLogLevel.DEBUG
 * </pre></p>
 */
class PLogger {

	private final def rawScript

	final String topic

	private PLogLevel logLevel

	PLogger(def rawScript, String topic, PLogLevel defaultLogLevel = PLogLevel.INFO) {
		this.rawScript = rawScript
		this.topic = topic
		this.logLevel = defaultLogLevel
		String logLevelPropertyForRawScript = PLogger.class.simpleName + '_logLevel_' + topic
		Object logLevelFromRawScript = rawScript.hasProperty(logLevelPropertyForRawScript)?.getProperty(rawScript)
		if (logLevelFromRawScript instanceof PLogLevel) {
			logInternal('PLogger default logLevel overridden by ' + logLevelPropertyForRawScript + ' = ' + logLevelFromRawScript)
			setLogLevel(logLevelFromRawScript)
		}
	}

	@NonCPS
	boolean isLogLevelDebugOrHigher() {
		return logLevel >= PLogLevel.DEBUG
	}

	@NonCPS
	boolean isLogLevelInfoOrHigher() {
		return logLevel >= PLogLevel.INFO
	}

	@NonCPS
	boolean isLogLevelWarnOrHigher() {
		return logLevel >= PLogLevel.WARN
	}

	@NonCPS
	boolean isLogLevelErrorOrHigher() {
		return logLevel >= PLogLevel.ERROR
	}

	@NonCPS
	PLogLevel getLogLevel() {
		return logLevel
	}

	@NonCPS
	void setLogLevel(PLogLevel logLevel) {
		if (logLevel == null) {
			throw new NullPointerException('logLevel')
		}
		this.logLevel = logLevel
	}

	@NonCPS
	void error(String message, Throwable throwable = null) {
		logWithLevel(PLogLevel.ERROR, message, throwable)
	}

	@NonCPS
	void warn(String message, Throwable throwable = null) {
		logWithLevel(PLogLevel.WARN, message, throwable)
	}

	@NonCPS
	void info(String message, Throwable throwable = null) {
		logWithLevel(PLogLevel.INFO, message, throwable)
	}

	@NonCPS
	void debug(String message, Throwable throwable = null) {
		logWithLevel(PLogLevel.DEBUG, message, throwable)
	}

	@NonCPS
	void logWithLevel(PLogLevel messageLogLevel, String message, Throwable throwable = null) {
		if (logLevel >= messageLogLevel) {
			logInternal('[' + messageLogLevel.text + '][' + topic + '] ' + message, throwable)
		}
	}

	/**
	 * Log directly without level filtering.
	 */
	@NonCPS
	void log(String message, Throwable throwable = null) {
		logInternal('[    *][' + topic + '] ' + message, throwable)
	}

	/**
	 * Echo directly without level filtering using step.
	 */
	void echo(String message, Throwable throwable = null) {
		rawScript.echo('[' + topic + '] ' + message + handleThrowable(throwable))
	}

	/**
	 * Log directly without level filtering.
	 */
	@NonCPS
	private void logInternal(String message, Throwable throwable = null) {
		rawScript.getContext(TaskListener.class).getLogger().println(message + handleThrowable(throwable))
	}

	@NonCPS
	private static String handleThrowable(Throwable throwable) {
		return throwable == null ? '' : ': ' + extractStacktrace(throwable)
	}

	@NonCPS
	private static String extractStacktrace(Throwable throwable) {
		StringWriter sw = new StringWriter()
		PrintWriter pw = new PrintWriter(sw)
		throwable.printStackTrace(pw)
		pw.flush()
		return sw.toString()
	}

}
