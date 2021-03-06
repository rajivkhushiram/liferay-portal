/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.source.formatter;

import com.liferay.portal.kernel.io.unsync.UnsyncBufferedReader;
import com.liferay.portal.kernel.io.unsync.UnsyncStringReader;
import com.liferay.portal.kernel.util.CharPool;
import com.liferay.portal.kernel.util.ReflectionUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.tools.ImportsFormatter;
import com.liferay.source.formatter.checks.CopyrightCheck;
import com.liferay.source.formatter.checks.FileCheck;
import com.liferay.source.formatter.checks.JSPEmptyLinesCheck;
import com.liferay.source.formatter.checks.JSPIfStatementCheck;
import com.liferay.source.formatter.checks.JSPLanguageKeysCheck;
import com.liferay.source.formatter.checks.JSPSessionKeysCheck;
import com.liferay.source.formatter.checks.JSPTagAttributesCheck;
import com.liferay.source.formatter.checks.JSPUnusedImportCheck;
import com.liferay.source.formatter.checks.JSPUnusedTaglibCheck;
import com.liferay.source.formatter.checks.JSPUnusedVariableCheck;
import com.liferay.source.formatter.checks.JSPWhitespaceCheck;
import com.liferay.source.formatter.checks.MethodCallsOrderCheck;
import com.liferay.source.formatter.checks.ResourceBundleCheck;
import com.liferay.source.formatter.checks.StringUtilCheck;
import com.liferay.source.formatter.checks.UnparameterizedClassCheck;
import com.liferay.source.formatter.checks.ValidatorEqualsCheck;
import com.liferay.source.formatter.checks.util.JSPSourceUtil;
import com.liferay.source.formatter.util.FileUtil;
import com.liferay.source.formatter.util.ThreadSafeClassLibrary;

import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.DefaultDocletTagFactory;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.parser.ParseException;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dom4j.Document;
import org.dom4j.Element;

/**
 * @author Hugo Huijser
 */
public class JSPSourceProcessor extends BaseSourceProcessor {

	protected void addImportCounts(String content) {
		Matcher matcher = _importsPattern.matcher(content);

		while (matcher.find()) {
			String importName = matcher.group(1);

			int count = 0;

			if (_importCountMap.containsKey(importName)) {
				count = _importCountMap.get(importName);
			}
			else {
				int pos = importName.lastIndexOf(CharPool.PERIOD);

				String importClassName = importName.substring(pos + 1);

				if (_importClassNames.contains(importClassName)) {
					_duplicateImportClassNames.add(importClassName);
				}
				else {
					_importClassNames.add(importClassName);
				}
			}

			_importCountMap.put(importName, count + 1);
		}
	}

	protected void checkDefineObjectsVariables(
		String fileName, String content, String absolutePath) {

		for (String[] defineObject : _LIFERAY_THEME_DEFINE_OBJECTS) {
			checkDefineObjectsVariables(
				fileName, content, defineObject[0], defineObject[1],
				defineObject[2], "liferay-theme");
		}

		for (String[] defineObject : _PORTLET_DEFINE_OBJECTS) {
			checkDefineObjectsVariables(
				fileName, content, defineObject[0], defineObject[1],
				defineObject[2], "portlet");
		}

		if (!portalSource && !subrepository) {
			return;
		}

		try {
			for (String directoryName :
					getPluginsInsideModulesDirectoryNames()) {

				if (absolutePath.contains(directoryName)) {
					return;
				}
			}
		}
		catch (Exception e) {
		}

		for (String[] defineObject : _LIFERAY_FRONTEND_DEFINE_OBJECTS) {
			checkDefineObjectsVariables(
				fileName, content, defineObject[0], defineObject[1],
				defineObject[2], "liferay-frontend");
		}
	}

	protected void checkDefineObjectsVariables(
		String fileName, String content, String objectType, String variableName,
		String value, String tag) {

		int x = -1;

		while (true) {
			x = content.indexOf(
				objectType + " " + variableName + " = " + value + ";", x + 1);

			if (x == -1) {
				return;
			}

			int y = content.lastIndexOf("<%", x);

			if ((y == -1) ||
				(getLevel(content.substring(y, x), "{", "}") > 0)) {

				continue;
			}

			processMessage(
				fileName,
				"Use '" + tag + ":defineObjects' or rename var, see LPS-62493",
				getLineCount(content, x));
		}
	}

	protected void checkSubnames(String fileName, String content) {
		Matcher matcher = _subnamePattern.matcher(content);

		while (matcher.find()) {
			processMessage(
				fileName,
				"'sub' should be followed by a lowercase character for '" +
					matcher.group(1) + "'");
		}
	}

	protected String compressImportsOrTaglibs(
		String fileName, String content, String attributePrefix) {

		if (!fileName.endsWith("init.jsp") && !fileName.endsWith("init.jspf")) {
			return content;
		}

		int x = content.indexOf(attributePrefix);

		int y = content.lastIndexOf(attributePrefix);

		y = content.indexOf("%>", y);

		if ((x == -1) || (y == -1) || (x > y)) {
			return content;
		}

		String importsOrTaglibs = content.substring(x, y);

		importsOrTaglibs = StringUtil.replace(
			importsOrTaglibs, new String[] {"%>\r\n<%@ ", "%>\n<%@ "},
			new String[] {"%><%@\r\n", "%><%@\n"});

		return content.substring(0, x) + importsOrTaglibs +
			content.substring(y);
	}

	@Override
	protected String doFormat(
			File file, String fileName, String absolutePath, String content)
		throws Exception {

		String newContent = formatJSP(fileName, absolutePath, content);

		newContent = StringUtil.replace(
			newContent,
			new String[] {
				"<br/>", "@page import", "\"%>", ")%>", "function (",
				"javascript: ", "){\n", ";;\n"
			},
			new String[] {
				"<br />", "@ page import", "\" %>", ") %>", "function(",
				"javascript:", ") {\n", ";\n"
			});

		newContent = fixRedirectBackURL(newContent);

		newContent = fixCompatClassImports(absolutePath, newContent);

		newContent = fixIncorrectClosingTag(newContent);

		newContent = fixEmptyJavaSourceTag(newContent);

		newContent = formatJSPImportsOrTaglibs(
			fileName, newContent, _compressedJSPImportPattern,
			_uncompressedJSPImportPattern);
		newContent = formatJSPImportsOrTaglibs(
			fileName, newContent, _compressedJSPTaglibPattern,
			_uncompressedJSPTaglibPattern);

		if ((portalSource || subrepository) &&
			content.contains("page import=") &&
			!fileName.contains("init.jsp") &&
			!fileName.contains("init-ext.jsp") &&
			!fileName.contains("/taglib/aui/") &&
			!fileName.endsWith("touch.jsp") &&
			(fileName.endsWith(".jspf") || content.contains("include file="))) {

			processMessage(fileName, "Move imports to init.jsp");
		}

		newContent = StringUtil.replace(
			newContent,
			new String[] {
				"alert('<%= LanguageUtil.", "alert(\"<%= LanguageUtil.",
				"confirm('<%= LanguageUtil.", "confirm(\"<%= LanguageUtil."
			},
			new String[] {
				"alert('<%= UnicodeLanguageUtil.",
				"alert(\"<%= UnicodeLanguageUtil.",
				"confirm('<%= UnicodeLanguageUtil.",
				"confirm(\"<%= UnicodeLanguageUtil."
			});

		newContent = compressImportsOrTaglibs(
			fileName, newContent, "<%@ page import=");
		newContent = compressImportsOrTaglibs(
			fileName, newContent, "<%@ taglib uri=");

		checkSubnames(fileName, newContent);

		newContent = formatStringBundler(fileName, newContent, -1);

		newContent = formatTaglibVariable(fileName, newContent);

		newContent = fixXSSVulnerability(fileName, newContent);

		// LPS-47682

		newContent = fixIncorrectParameterTypeForLanguageUtil(
			newContent, true, fileName);

		// LPS-48156

		newContent = checkPrincipalException(newContent);

		newContent = formatLogFileName(absolutePath, newContent);

		newContent = formatDefineObjects(newContent);

		// LPS-62989

		if ((portalSource || subrepository) && isModulesFile(absolutePath) &&
			newContent.contains("import=\"com.liferay.registry.Registry")) {

			processMessage(
				fileName,
				"Do not use com.liferay.registry.Registry in modules, see " +
					"LPS-62989");
		}

		// LPS-64335

		if ((portalSource || subrepository) && isModulesFile(absolutePath) &&
			newContent.contains("import=\"com.liferay.util.ContentUtil")) {

			processMessage(
				fileName,
				"Do not use com.liferay.util.ContentUtil in modules, see " +
					"LPS-64335");
		}

		// LPS-62786

		checkPropertyUtils(fileName, newContent);

		checkGetterUtilGet(fileName, newContent);

		checkDefineObjectsVariables(fileName, newContent, absolutePath);

		Matcher matcher = _javaClassPattern.matcher(newContent);

		if (matcher.find()) {
			String javaClassContent = matcher.group();

			javaClassContent = javaClassContent.substring(1);

			String javaClassName = matcher.group(2);

			int javaClassLineCount = getLineCount(
				newContent, matcher.start() + 1);

			newContent = formatJavaTerms(
				javaClassName, null, file, fileName, absolutePath, newContent,
				javaClassContent, javaClassLineCount, StringPool.BLANK, null,
				null, null);
		}

		JSPSourceTabCalculator jspSourceTabCalculator =
			new JSPSourceTabCalculator();

		newContent = jspSourceTabCalculator.calculateTabs(
			fileName, newContent, (JSPSourceProcessor)this);

		return newContent;
	}

	@Override
	protected List<String> doGetFileNames() throws Exception {
		String[] excludes = new String[] {"**/null.jsp", "**/tools/**"};

		List<String> fileNames = getFileNames(excludes, getIncludes());

		if (fileNames.isEmpty()) {
			return fileNames;
		}

		_contentsMap = _getContentsMap();

		if (sourceFormatterArgs.isFormatCurrentBranch() ||
			sourceFormatterArgs.isFormatLatestAuthor() ||
			sourceFormatterArgs.isFormatLocalChanges()) {

			return JSPSourceUtil.addIncludedAndReferencedFileNames(
				fileNames, new HashSet<String>(), _contentsMap);
		}

		return fileNames;
	}

	@Override
	protected String[] doGetIncludes() {
		return _INCLUDES;
	}

	protected String fixEmptyJavaSourceTag(String content) {
		Matcher matcher = _emptyJavaSourceTagPattern.matcher(content);

		if (matcher.find()) {
			return StringUtil.replace(
				content, matcher.group(), StringPool.BLANK);
		}

		return content;
	}

	protected String fixIncorrectClosingTag(String content) {
		Matcher matcher = _incorrectClosingTagPattern.matcher(content);

		if (matcher.find()) {
			return StringUtil.replaceFirst(
				content, " />\n", "\n" + matcher.group(1) + "/>\n",
				matcher.end(1));
		}

		return content;
	}

	protected String fixRedirectBackURL(String content) {
		Matcher matcher = _redirectBackURLPattern.matcher(content);

		String newContent = content;

		while (matcher.find()) {
			newContent = StringUtil.replaceFirst(
				newContent, matcher.group(),
				matcher.group(1) + "\n\n" + matcher.group(2), matcher.start());
		}

		return newContent;
	}

	protected String fixXSSVulnerability(String fileName, String content) {
		Matcher matcher1 = _xssPattern.matcher(content);

		String jspVariable = null;
		int vulnerabilityPos = -1;

		while (matcher1.find()) {
			jspVariable = matcher1.group(1);

			String anchorVulnerability = " href=\"<%= " + jspVariable + " %>";
			String inputVulnerability = " value=\"<%= " + jspVariable + " %>";

			vulnerabilityPos = Math.max(
				getTaglibXSSVulnerabilityPos(content, anchorVulnerability),
				getTaglibXSSVulnerabilityPos(content, inputVulnerability));

			if (vulnerabilityPos != -1) {
				break;
			}

			Pattern pattern = Pattern.compile(
				"('|\\(\"| \"|\\.)<%= " + jspVariable + " %>");

			Matcher matcher2 = pattern.matcher(content);

			if (matcher2.find()) {
				vulnerabilityPos = matcher2.start();

				break;
			}
		}

		if (vulnerabilityPos != -1) {
			return StringUtil.replaceFirst(
				content, "<%= " + jspVariable + " %>",
				"<%= HtmlUtil.escape(" + jspVariable + ") %>",
				vulnerabilityPos);
		}

		return content;
	}

	protected String formatDefineObjects(String content) {
		Matcher matcher = _missingEmptyLineBetweenDefineOjbectsPattern.matcher(
			content);

		if (matcher.find()) {
			content = StringUtil.replaceFirst(
				content, "\n", "\n\n", matcher.start());
		}

		String previousDefineObjectsTag = null;

		matcher = _defineObjectsPattern.matcher(content);

		while (matcher.find()) {
			String defineObjectsTag = matcher.group(1);

			if (Validator.isNotNull(previousDefineObjectsTag) &&
				(previousDefineObjectsTag.compareTo(defineObjectsTag) > 0)) {

				content = StringUtil.replaceFirst(
					content, previousDefineObjectsTag, defineObjectsTag);
				content = StringUtil.replaceLast(
					content, defineObjectsTag, previousDefineObjectsTag);

				return content;
			}

			previousDefineObjectsTag = defineObjectsTag;
		}

		return content;
	}

	protected String formatJSP(
			String fileName, String absolutePath, String content)
		throws Exception {

		StringBundler sb = new StringBundler();

		String currentException = null;
		String previousException = null;

		boolean hasUnsortedExceptions = false;

		try (UnsyncBufferedReader unsyncBufferedReader =
				new UnsyncBufferedReader(new UnsyncStringReader(content))) {

			int lineCount = 0;

			String line = null;

			String previousLine = StringPool.BLANK;

			boolean javaSource = false;

			while ((line = unsyncBufferedReader.readLine()) != null) {
				lineCount++;

				if (line.contains("<aui:button ") &&
					line.contains("type=\"button\"")) {

					processMessage(
						fileName, "No need to set 'type=button' for aui:button",
						lineCount);
				}

				if (line.contains("debugger.")) {
					processMessage(fileName, "Do not use debugger", lineCount);
				}

				String trimmedLine = StringUtil.trimLeading(line);

				if (line.matches(".*\\WgetClass\\(\\)\\..+")) {
					processMessage(
						fileName, "Avoid chaining on 'getClass'", lineCount);
				}

				checkEmptyCollection(trimmedLine, fileName, lineCount);

				line = formatEmptyArray(line);

				if (trimmedLine.equals("<%") || trimmedLine.equals("<%!")) {
					javaSource = true;
				}
				else if (trimmedLine.equals("%>")) {
					javaSource = false;
				}

				checkInefficientStringMethods(
					line, fileName, absolutePath, lineCount, javaSource);

				// LPS-47179

				if (line.contains(".sendRedirect(") &&
					!fileName.endsWith("_jsp.jsp")) {

					processMessage(
						fileName,
						"Do not use sendRedirect in jsp, see LPS-47179",
						lineCount);
				}

				// LPS-55341

				if (javaSource) {
					line = StringUtil.replace(
						line, "LanguageUtil.get(locale,",
						"LanguageUtil.get(request,");
				}
				else {
					Matcher matcher = javaSourceInsideJSPLinePattern.matcher(
						line);

					while (matcher.find()) {
						String match = matcher.group(1);

						String replacement = StringUtil.replace(
							match, "LanguageUtil.get(locale,",
							"LanguageUtil.get(request,");

						line = StringUtil.replace(line, match, replacement);
					}
				}

				if (!fileName.endsWith("test.jsp") &&
					line.contains("System.out.print")) {

					processMessage(
						fileName, "Do not call 'System.out.print'", lineCount);
				}

				if (trimmedLine.matches("^\\} ?(catch|else|finally) .*")) {
					processMessage(
						fileName, "There should be a line break after '}'",
						lineCount);
				}

				if (!hasUnsortedExceptions) {
					int x = line.indexOf("<liferay-ui:error exception=\"<%=");

					if (x != -1) {
						int y = line.indexOf(".class %>", x);

						if (y != -1) {
							currentException = line.substring(x, y);

							if (Validator.isNotNull(previousException) &&
								(previousException.compareToIgnoreCase(
									currentException) > 0)) {

								currentException = line;
								previousException = previousLine;

								hasUnsortedExceptions = true;
							}
						}
					}

					if (!hasUnsortedExceptions) {
						previousException = currentException;
						currentException = null;
					}
				}

				if (!fileName.endsWith("/touch.jsp")) {
					int x = line.indexOf("<%@ include file");

					if (x != -1) {
						x = line.indexOf(CharPool.QUOTE, x);

						int y = line.indexOf(CharPool.QUOTE, x + 1);

						if (y != -1) {
							String includeFileName = line.substring(x + 1, y);

							Matcher matcher = _jspIncludeFilePattern.matcher(
								includeFileName);

							if (!matcher.find()) {
								processMessage(
									fileName,
									"Incorrect include '" + includeFileName +
										"'",
									lineCount);
							}
						}
					}
				}

				line = replacePrimitiveWrapperInstantiation(line);

				if (lineCount > 1) {
					sb.append(previousLine);
					sb.append("\n");
				}

				previousLine = line;
			}

			sb.append(previousLine);
		}

		content = sb.toString();

		if (content.endsWith("\n")) {
			content = content.substring(0, content.length() - 1);
		}

		if (hasUnsortedExceptions) {
			if ((StringUtil.count(content, currentException) > 1) ||
				(StringUtil.count(content, previousException) > 1)) {

				processMessage(
					fileName, "Unsorted exception '" + currentException + "'");
			}
			else {
				content = StringUtil.replaceFirst(
					content, previousException, currentException);

				content = StringUtil.replaceLast(
					content, currentException, previousException);
			}
		}

		return content;
	}

	protected String formatJSPImportsOrTaglibs(
			String fileName, String content, Pattern compressedPattern,
			Pattern uncompressedPattern)
		throws IOException {

		if (fileName.endsWith("init-ext.jsp")) {
			return content;
		}

		Matcher matcher = compressedPattern.matcher(content);

		if (!matcher.find()) {
			return content;
		}

		String imports = matcher.group();

		String newImports = StringUtil.replace(
			imports, new String[] {"<%@\r\n", "<%@\n", " %><%@ "},
			new String[] {"\r\n<%@ ", "\n<%@ ", " %>\n<%@ "});

		content = StringUtil.replaceFirst(content, imports, newImports);

		ImportsFormatter importsFormatter = new JSPImportsFormatter();

		return importsFormatter.format(content, uncompressedPattern);
	}

	protected String formatLogFileName(String absolutePath, String content) {
		if (!isModulesFile(absolutePath) &&
			!absolutePath.contains("/portal-web/")) {

			return content;
		}

		Matcher matcher = _logPattern.matcher(content);

		if (!matcher.find()) {
			return content;
		}

		String logFileName = StringUtil.replace(
			absolutePath, CharPool.PERIOD, CharPool.UNDERLINE);

		logFileName = StringUtil.replace(
			logFileName, CharPool.SLASH, CharPool.PERIOD);

		logFileName = StringUtil.replace(
			logFileName, CharPool.DASH, CharPool.UNDERLINE);

		int x = logFileName.lastIndexOf(".portal_web.");

		if (x != -1) {
			logFileName = logFileName.substring(x + 1);
		}
		else {
			x = logFileName.lastIndexOf(".docroot.");

			if (x == -1) {
				x = Math.max(
					logFileName.lastIndexOf(
						".src.main.resources.META_INF.resources."),
					logFileName.lastIndexOf(".src.META_INF.resources."));
			}

			if (x == -1) {
				return content;
			}

			x = logFileName.lastIndexOf(StringPool.PERIOD, x - 1);

			logFileName = "com_liferay_" + logFileName.substring(x + 1);

			logFileName = StringUtil.replace(
				logFileName,
				new String[] {
					".docroot.", ".src.main.resources.META_INF.resources.",
					".src.META_INF.resources."
				},
				new String[] {
					StringPool.PERIOD, StringPool.PERIOD, StringPool.PERIOD
				});
		}

		return StringUtil.replace(
			content, matcher.group(),
			"Log _log = LogFactoryUtil.getLog(\"" + logFileName + "\")");
	}

	protected String formatTaglibVariable(String fileName, String content) {
		Matcher matcher = _taglibVariablePattern.matcher(content);

		while (matcher.find()) {
			String taglibValue = matcher.group(3);

			if (taglibValue.contains("\\\"") ||
				(taglibValue.contains(StringPool.APOSTROPHE) &&
				 taglibValue.contains(StringPool.QUOTE))) {

				continue;
			}

			String taglibName = matcher.group(2);
			String nextTag = matcher.group(4);

			if (!nextTag.contains(taglibName)) {
				processMessage(
					fileName,
					"No need to specify taglib variable '" + taglibName + "'",
					getLineCount(content, matcher.start()));

				continue;
			}

			content = StringUtil.replaceFirst(
				content, taglibName, taglibValue, matcher.start(4));

			return content = StringUtil.replaceFirst(
				content, matcher.group(1), StringPool.BLANK, matcher.start());
		}

		return content;
	}

	@Override
	protected List<FileCheck> getFileChecks() {
		return _fileChecks;
	}

	protected int getTaglibXSSVulnerabilityPos(
		String content, String vulnerability) {

		int x = -1;

		while (true) {
			x = content.indexOf(vulnerability, x + 1);

			if (x == -1) {
				return x;
			}

			String tagContent = null;

			int y = x;

			while (true) {
				y = content.lastIndexOf(CharPool.LESS_THAN, y - 1);

				if (y == -1) {
					return -1;
				}

				if (content.charAt(y + 1) == CharPool.PERCENT) {
					continue;
				}

				tagContent = content.substring(y, x);

				if (getLevel(tagContent, "<", ">") == 1) {
					break;
				}
			}

			if (!tagContent.startsWith("<aui:") &&
				!tagContent.startsWith("<liferay-portlet:") &&
				!tagContent.startsWith("<liferay-util:") &&
				!tagContent.startsWith("<portlet:")) {

				return x;
			}
		}
	}

	protected String getUtilTaglibSrcDirName() {
		if (_utilTaglibSrcDirName != null) {
			return _utilTaglibSrcDirName;
		}

		File utilTaglibDir = getFile("util-taglib/src", PORTAL_MAX_DIR_LEVEL);

		String utilTaglibSrcDirName = null;

		if (utilTaglibDir != null) {
			utilTaglibSrcDirName = utilTaglibDir.getAbsolutePath();

			utilTaglibSrcDirName = StringUtil.replace(
				utilTaglibSrcDirName, CharPool.BACK_SLASH, CharPool.SLASH);

			utilTaglibSrcDirName += StringPool.SLASH;
		}
		else {
			utilTaglibSrcDirName = StringPool.BLANK;
		}

		_utilTaglibSrcDirName = utilTaglibSrcDirName;

		return _utilTaglibSrcDirName;
	}

	@Override
	protected void populateFileChecks() throws Exception {
		_fileChecks.add(new JSPWhitespaceCheck());

		_fileChecks.add(
			new CopyrightCheck(
				getContent(
					sourceFormatterArgs.getCopyrightFileName(),
					PORTAL_MAX_DIR_LEVEL)));
		_fileChecks.add(new JSPEmptyLinesCheck());
		_fileChecks.add(new JSPIfStatementCheck());
		_fileChecks.add(new JSPSessionKeysCheck());
		_fileChecks.add(
			new JSPTagAttributesCheck(
				portalSource, subrepository,
				_getPrimitiveTagAttributeDataTypes(), _getTagJavaClassesMap()));
		_fileChecks.add(new JSPUnusedImportCheck(_contentsMap));
		_fileChecks.add(
			new MethodCallsOrderCheck(getExcludes(METHOD_CALL_SORT_EXCLUDES)));
		_fileChecks.add(new StringUtilCheck());
		_fileChecks.add(new UnparameterizedClassCheck());
		_fileChecks.add(new ValidatorEqualsCheck());

		if (portalSource || subrepository) {
			_fileChecks.add(new JSPUnusedTaglibCheck(_contentsMap));
			_fileChecks.add(
				new JSPUnusedVariableCheck(
					getExcludes(_UNUSED_VARIABLES_EXCLUDES), _contentsMap));
			_fileChecks.add(
				new ResourceBundleCheck(
					getExcludes(RUN_OUTSIDE_PORTAL_EXCLUDES)));
		}

		if (portalSource) {
			_fileChecks.add(
				new JSPLanguageKeysCheck(
					getExcludes(LANGUAGE_KEYS_CHECK_EXCLUDES),
					getPortalLanguageProperties()));
		}
	}

	private Map<String, String> _getContentsMap() throws Exception {
		String[] excludes = new String[] {"**/null.jsp", "**/tools/**"};

		List<String> allFileNames = getFileNames(
			sourceFormatterArgs.getBaseDirName(), null, excludes,
			getIncludes(), true);

		Map<String, String> contentsMap = new HashMap<>();

		try {
			for (String fileName : allFileNames) {
				fileName = StringUtil.replace(
					fileName, CharPool.BACK_SLASH, CharPool.SLASH);

				File file = new File(fileName);

				String content = FileUtil.read(file);

				Matcher matcher = _includeFilePattern.matcher(content);

				String newContent = content;

				while (matcher.find()) {
					newContent = StringUtil.replaceFirst(
						newContent, matcher.group(),
						"@ include file=\"" + matcher.group(1) + "\"",
						matcher.start());
				}

				processFormattedFile(file, fileName, content, newContent);

				contentsMap.put(fileName, newContent);
			}
		}
		catch (Exception e) {
			ReflectionUtil.throwException(e);
		}

		return contentsMap;
	}

	private Set<String> _getPrimitiveTagAttributeDataTypes() {
		return SetUtil.fromArray(
			new String[] {"boolean", "double", "int", "long"});
	}

	private Map<String, JavaClass> _getTagJavaClassesMap() throws Exception {
		Map<String, JavaClass> tagJavaClassesMap = new HashMap<>();

		List<String> tldFileNames = getFileNames(
			sourceFormatterArgs.getBaseDirName(), null,
			new String[] {"**/dependencies/**", "**/util-taglib/**"},
			new String[] {"**/*.tld"}, true);

		outerLoop:
		for (String tldFileName : tldFileNames) {
			tldFileName = StringUtil.replace(
				tldFileName, CharPool.BACK_SLASH, CharPool.SLASH);

			File tldFile = new File(tldFileName);

			String content = FileUtil.read(tldFile);

			Document document = readXML(content);

			Element rootElement = document.getRootElement();

			Element shortNameElement = rootElement.element("short-name");

			String shortName = shortNameElement.getStringValue();

			List<Element> tagElements = rootElement.elements("tag");

			String srcDir = null;

			for (Element tagElement : tagElements) {
				Element tagClassElement = tagElement.element("tag-class");

				String tagClassName = tagClassElement.getStringValue();

				if (!tagClassName.startsWith("com.liferay")) {
					continue;
				}

				if (srcDir == null) {
					if (tldFileName.contains("/src/")) {
						srcDir = tldFile.getAbsolutePath();

						srcDir = StringUtil.replace(
							srcDir, CharPool.BACK_SLASH, CharPool.SLASH);

						srcDir =
							srcDir.substring(0, srcDir.lastIndexOf("/src/")) +
								"/src/main/java/";
					}
					else {
						srcDir = getUtilTaglibSrcDirName();

						if (Validator.isNull(srcDir)) {
							continue outerLoop;
						}
					}
				}

				StringBundler sb = new StringBundler(3);

				sb.append(srcDir);
				sb.append(
					StringUtil.replace(
						tagClassName, CharPool.PERIOD, CharPool.SLASH));
				sb.append(".java");

				File tagJavaFile = new File(sb.toString());

				if (!tagJavaFile.exists()) {
					continue;
				}

				JavaDocBuilder javaDocBuilder = new JavaDocBuilder(
					new DefaultDocletTagFactory(),
					new ThreadSafeClassLibrary());

				try {
					javaDocBuilder.addSource(tagJavaFile);
				}
				catch (ParseException pe) {
					continue;
				}

				JavaClass tagJavaClass = javaDocBuilder.getClassByName(
					tagClassName);

				Element tagNameElement = tagElement.element("name");

				String tagName = tagNameElement.getStringValue();

				tagJavaClassesMap.put(
					shortName + StringPool.COLON + tagName, tagJavaClass);
			}
		}

		return tagJavaClassesMap;
	}

	private static final String[] _INCLUDES =
		new String[] {"**/*.jsp", "**/*.jspf", "**/*.tpl", "**/*.vm"};

	private static final String[][] _LIFERAY_FRONTEND_DEFINE_OBJECTS =
		new String[][] {
			new String[] {"String", "currentURL", "currentURLObj.toString()"},
			new String[] {
				"PortletURL", "currentURLObj",
				"PortletURLUtil.getCurrent(liferayPortletRequest, " +
					"liferayPortletResponse)"
			},
			new String[] {
				"ResourceBundle", "resourceBundle",
				"ResourceBundleUtil.getBundle(\"content.Language\", locale, " +
					"getClass()"
			},
			new String[] {
				"WindowState", "windowState",
				"liferayPortletRequest.getWindowState()"
			}
		};

	private static final String[][] _LIFERAY_THEME_DEFINE_OBJECTS =
		new String[][] {
			new String[] {"Account", "account", "themeDisplay.getAccount()"},
			new String[] {
				"ColorScheme", "colorScheme", "themeDisplay.getColorScheme()"
			},
			new String[] {"Company", "company", "themeDisplay.getCompany()"},
			new String[] {"Contact", "contact", "themeDisplay.getContact()"},
			new String[] {"Layout", "layout", "themeDisplay.getLayout()"},
			new String[] {
				"List<Layout>", "layouts", "themeDisplay.getLayouts()"
			},
			new String[] {
				"LayoutTypePortlet", "layoutTypePortlet",
				"themeDisplay.getLayoutTypePortlet()"
			},
			new String[] {"Locale", "locale", "themeDisplay.getLocale()"},
			new String[] {
				"PermissionChecker", "permissionChecker",
				"themeDisplay.getPermissionChecker()"
			},
			new String[] {"long", "plid", "themeDisplay.getPlid()"},
			new String[] {
				"PortletDisplay", "portletDisplay",
				"themeDisplay.getPortletDisplay()"
			},
			new String[] {"User", "realUser", "themeDisplay.getRealUser()"},
			new String[] {
				"long", "scopeGroupId", "themeDisplay.getScopeGroupId()"
			},
			new String[] {"Theme", "theme", "themeDisplay.getTheme()"},
			new String[] {
				"ThemeDisplay", "themeDisplay",
				"(ThemeDisplay)request.getAttribute(WebKeys.THEME_DISPLAY)"
			},
			new String[] {"TimeZone", "timeZone", "themeDisplay.getTimeZone()"},
			new String[] {"User", "user", "themeDisplay.getUser()"},
			new String[] {
				"long", "portletGroupId", "themeDisplay.getScopeGroupId()"
			}
		};

	private static final String[][] _PORTLET_DEFINE_OBJECTS = new String[][] {
		new String[] {
			"PortletConfig", "portletConfig",
			"(PortletConfig)request.getAttribute(JavaConstants." +
				"JAVAX_PORTLET_CONFIG)"
		},
		new String[] {
			"String", "portletName", "portletConfig.getPortletName()"
		},
		new String[] {
			"LiferayPortletRequest", "liferayPortletRequest",
			"PortalUtil.getLiferayPortletRequest(portletRequest)"
		},
		new String[] {
			"PortletRequest", "actionRequest",
			"(PortletRequest)request.getAttribute(JavaConstants." +
				"JAVAX_PORTLET_REQUEST)"
		},
		new String[] {
			"PortletRequest", "eventRequest",
			"(PortletRequest)request.getAttribute(JavaConstants." +
				"JAVAX_PORTLET_REQUEST)"
		},
		new String[] {
			"PortletRequest", "renderRequest",
			"(PortletRequest)request.getAttribute(JavaConstants." +
				"JAVAX_PORTLET_REQUEST)"
		},
		new String[] {
			"PortletRequest", "resourceRequest",
			"(PortletRequest)request.getAttribute(JavaConstants." +
				"JAVAX_PORTLET_REQUEST)"
		},
		new String[] {
			"PortletPreferences", "portletPreferences",
			"portletRequest.getPreferences()"
		},
		new String[] {
			"Map<String, String[]>", "portletPreferencesValues",
			"portletPreferences.getMap()"
		},
		new String[] {
			"PortletSession", "portletSession",
			"portletRequest.getPortletSession()"
		},
		new String[] {
			"Map<String, Object>", "portletSessionScope",
			"portletSession.getAttributeMap()"
		},
		new String[] {
			"LiferayPortletResponse", "liferayPortletResponse",
			"PortalUtil.getLiferayPortletResponse(portletResponse)"
		},
		new String[] {
			"PortletResponse", "actionResponse",
			"(PortletResponse)request.getAttribute(JavaConstants." +
				"JAVAX_PORTLET_RESPONSE)"
		},
		new String[] {
			"PortletResponse", "eventResponse",
			"(PortletResponse)request.getAttribute(JavaConstants." +
				"JAVAX_PORTLET_RESPONSE)"
		},
		new String[] {
			"PortletResponse", "renderResponse",
			"(PortletResponse)request.getAttribute(JavaConstants." +
				"JAVAX_PORTLET_RESPONSE)"
		},
		new String[] {
			"PortletResponse", "resourceResponse",
			"(PortletResponse)request.getAttribute(JavaConstants." +
				"JAVAX_PORTLET_RESPONSE)"
		}
	};

	private static final String _UNUSED_VARIABLES_EXCLUDES =
		"jsp.unused.variables.excludes";

	private final Pattern _compressedJSPImportPattern = Pattern.compile(
		"(<.*\n*page import=\".*>\n*)+", Pattern.MULTILINE);
	private final Pattern _compressedJSPTaglibPattern = Pattern.compile(
		"(<.*\n*taglib uri=\".*>\n*)+", Pattern.MULTILINE);
	private Map<String, String> _contentsMap;
	private final Pattern _defineObjectsPattern = Pattern.compile(
		"\n\t*(<.*:defineObjects />)(\n|$)");
	private final List<String> _duplicateImportClassNames = new ArrayList<>();
	private final Pattern _emptyJavaSourceTagPattern = Pattern.compile(
		"\n\t*<%\n+\t*%>\n");
	private final List<FileCheck> _fileChecks = new ArrayList<>();
	private final List<String> _importClassNames = new ArrayList<>();
	private final Map<String, Integer> _importCountMap = new HashMap<>();
	private final Pattern _importsPattern = Pattern.compile(
		"page import=\"(.+)\"");
	private final Pattern _includeFilePattern = Pattern.compile(
		"\\s*@\\s*include\\s*file=['\"](.*)['\"]");
	private final Pattern _incorrectClosingTagPattern = Pattern.compile(
		"\n(\t*)\t((?!<\\w).)* />\n");
	private Pattern _javaClassPattern = Pattern.compile(
		"\n(private|protected|public).* class ([A-Za-z0-9]+) " +
			"([\\s\\S]*?)\n\\}\n");
	private final Pattern _jspIncludeFilePattern = Pattern.compile(
		"/.*\\.(jsp[f]?|svg)");
	private final Pattern _logPattern = Pattern.compile(
		"Log _log = LogFactoryUtil\\.getLog\\(\"(.*?)\"\\)");
	private final Pattern _missingEmptyLineBetweenDefineOjbectsPattern =
		Pattern.compile("<.*:defineObjects />\n<.*:defineObjects />\n");
	private final Pattern _redirectBackURLPattern = Pattern.compile(
		"(String redirect = ParamUtil\\.getString\\(request, \"redirect\".*" +
			"\\);)\n(String backURL = ParamUtil\\.getString\\(request, \"" +
				"backURL\", redirect\\);)");
	private final Pattern _subnamePattern = Pattern.compile(
		"\\s(_?sub[A-Z]\\w+)[; ]");
	private final Pattern _taglibVariablePattern = Pattern.compile(
		"(\n\t*String (taglib\\w+) = (.*);)\n\\s*%>\\s+(<[\\S\\s]*?>)\n");
	private final Pattern _uncompressedJSPImportPattern = Pattern.compile(
		"(<.*page import=\".*>\n*)+", Pattern.MULTILINE);
	private final Pattern _uncompressedJSPTaglibPattern = Pattern.compile(
		"(<.*taglib uri=\".*>\n*)+", Pattern.MULTILINE);
	private String _utilTaglibSrcDirName;
	private final Pattern _xssPattern = Pattern.compile(
		"\\s+([^\\s]+)\\s*=\\s*(Bean)?ParamUtil\\.getString\\(");

}