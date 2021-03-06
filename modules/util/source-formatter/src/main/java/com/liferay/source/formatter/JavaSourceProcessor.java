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
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.CharPool;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.tools.ImportsFormatter;
import com.liferay.portal.tools.JavaImportsFormatter;
import com.liferay.portal.tools.ToolsUtil;
import com.liferay.source.formatter.checks.CopyrightCheck;
import com.liferay.source.formatter.checks.FileCheck;
import com.liferay.source.formatter.checks.JavaAnnotationsCheck;
import com.liferay.source.formatter.checks.JavaAssertEqualsCheck;
import com.liferay.source.formatter.checks.JavaBooleanUsageCheck;
import com.liferay.source.formatter.checks.JavaCombineLinesCheck;
import com.liferay.source.formatter.checks.JavaDataAccessConnectionCheck;
import com.liferay.source.formatter.checks.JavaDeprecatedJavadocCheck;
import com.liferay.source.formatter.checks.JavaDeserializationSecurityCheck;
import com.liferay.source.formatter.checks.JavaDiamondOperatorCheck;
import com.liferay.source.formatter.checks.JavaEmptyLinesCheck;
import com.liferay.source.formatter.checks.JavaExceptionCheck;
import com.liferay.source.formatter.checks.JavaFinderCacheCheck;
import com.liferay.source.formatter.checks.JavaHibernateSQLCheck;
import com.liferay.source.formatter.checks.JavaIfStatementCheck;
import com.liferay.source.formatter.checks.JavaIOExceptionCheck;
import com.liferay.source.formatter.checks.JavaLineBreakCheck;
import com.liferay.source.formatter.checks.JavaLogLevelCheck;
import com.liferay.source.formatter.checks.JavaLongLinesCheck;
import com.liferay.source.formatter.checks.JavaModuleExtendedObjectClassDefinitionCheck;
import com.liferay.source.formatter.checks.JavaModuleIllegalImportsCheck;
import com.liferay.source.formatter.checks.JavaModuleInternalImportsCheck;
import com.liferay.source.formatter.checks.JavaModuleServiceProxyFactoryCheck;
import com.liferay.source.formatter.checks.JavaModuleTestCheck;
import com.liferay.source.formatter.checks.JavaOSGiReferenceCheck;
import com.liferay.source.formatter.checks.JavaPackagePathCheck;
import com.liferay.source.formatter.checks.JavaSeeAnnotationCheck;
import com.liferay.source.formatter.checks.JavaStopWatchCheck;
import com.liferay.source.formatter.checks.JavaSystemEventAnnotationCheck;
import com.liferay.source.formatter.checks.JavaSystemExceptionCheck;
import com.liferay.source.formatter.checks.JavaUpgradeClassCheck;
import com.liferay.source.formatter.checks.JavaVerifyUpgradeConnectionCheck;
import com.liferay.source.formatter.checks.JavaWhitespaceCheck;
import com.liferay.source.formatter.checks.JavaXMLSecurityCheck;
import com.liferay.source.formatter.checks.LanguageKeysCheck;
import com.liferay.source.formatter.checks.MethodCallsOrderCheck;
import com.liferay.source.formatter.checks.ResourceBundleCheck;
import com.liferay.source.formatter.checks.SessionKeysCheck;
import com.liferay.source.formatter.checks.StringUtilCheck;
import com.liferay.source.formatter.checks.UnparameterizedClassCheck;
import com.liferay.source.formatter.checks.ValidatorEqualsCheck;
import com.liferay.source.formatter.checkstyle.util.CheckStyleUtil;
import com.liferay.source.formatter.util.FileUtil;

import java.io.File;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hugo Huijser
 */
public class JavaSourceProcessor extends BaseSourceProcessor {

	@Override
	protected String doFormat(
			File file, String fileName, String absolutePath, String content)
		throws Exception {

		if (hasGeneratedTag(content)) {
			return content;
		}

		_ungeneratedFiles.add(file);

		String className = file.getName();

		int pos = className.lastIndexOf(CharPool.PERIOD);

		className = className.substring(0, pos);

		String packagePath = StringPool.BLANK;

		Matcher matcher = _packagePattern.matcher(content);

		if (matcher.find()) {
			packagePath = matcher.group(2);
		}

		if (packagePath.endsWith(".model")) {
			if (content.contains("extends " + className + "Model")) {
				return content;
			}
		}

		String newContent = content;

		if (newContent.contains("$\n */")) {
			processMessage(fileName, "*");

			newContent = StringUtil.replace(newContent, "$\n */", "$\n *\n */");
		}

		if (newContent.contains(className + ".java.html")) {
			processMessage(fileName, "Java2HTML");
		}

		if (newContent.contains(" * @author Raymond Aug") &&
			!newContent.contains(" * @author Raymond Aug\u00e9")) {

			newContent = newContent.replaceFirst(
				"Raymond Aug.++", "Raymond Aug\u00e9");

			processMessage(fileName, "UTF-8");
		}

		newContent = StringUtil.replace(
			newContent,
			new String[] {
				"com.liferay.portal.PortalException",
				"com.liferay.portal.SystemException",
				"com.liferay.util.LocalizationUtil"
			},
			new String[] {
				"com.liferay.portal.kernel.exception.PortalException",
				"com.liferay.portal.kernel.exception.SystemException",
				"com.liferay.portal.kernel.util.LocalizationUtil"
			});

		newContent = StringUtil.replace(
			newContent, " final static ", " static final ");

		newContent = fixCompatClassImports(absolutePath, newContent);

		ImportsFormatter importsFormatter = new JavaImportsFormatter();

		newContent = importsFormatter.format(
			newContent, packagePath, className);

		newContent = StringUtil.replace(
			newContent,
			new String[] {";\n/**", "\t/*\n\t *", ";;\n", "\n/**\n *\n *"},
			new String[] {";\n\n/**", "\t/**\n\t *", ";\n", "\n/**\n *"});

		matcher = _logPattern.matcher(newContent);

		if (matcher.find()) {
			String logClassName = matcher.group(1);

			if (!logClassName.equals(className)) {
				newContent = StringUtil.replaceLast(
					newContent, logClassName + ".class)",
					className + ".class)");
			}
		}

		if (!isExcludedPath(_STATIC_LOG_EXCLUDES, absolutePath)) {
			newContent = StringUtil.replace(
				newContent, "private Log _log",
				"private static final Log _log");
		}

		newContent = StringUtil.replace(
			newContent,
			new String[] {"!Validator.isNotNull(", "!Validator.isNull("},
			new String[] {"Validator.isNull(", "Validator.isNotNull("});

		if (newContent.contains("*/\npackage ")) {
			processMessage(fileName, "package");
		}

		if ((portalSource ||subrepository) &&
			!_allowUseServiceUtilInServiceImpl &&
			!fileName.contains("/wsrp/internal/bind/") &&
			!className.equals("BaseServiceImpl") &&
			className.endsWith("ServiceImpl") &&
			newContent.contains("ServiceUtil.")) {

			processMessage(
				fileName,
				"Do not use *ServiceUtil in *ServiceImpl class, create a " +
					"reference via service.xml instead");
		}

		boolean isRunOutsidePortalExclusion = isExcludedPath(
			RUN_OUTSIDE_PORTAL_EXCLUDES, absolutePath);

		if (!isRunOutsidePortalExclusion &&
			!isExcludedPath(_PROXY_EXCLUDES, absolutePath) &&
			newContent.contains("import java.lang.reflect.Proxy;")) {

			processMessage(
				fileName, "Use ProxyUtil instead of java.lang.reflect.Proxy");
		}

		if (newContent.contains("import edu.emory.mathcs.backport.java")) {
			processMessage(
				fileName, "Illegal import: edu.emory.mathcs.backport.java");
		}

		if (newContent.contains("import jodd.util.StringPool")) {
			processMessage(fileName, "Illegal import: jodd.util.StringPool");
		}

		// LPS-45027

		if (newContent.contains(
				"com.liferay.portal.kernel.util.UnmodifiableList")) {

			processMessage(
				fileName,
				"Use java.util.Collections.unmodifiableList instead of " +
					"com.liferay.portal.kernel.util.UnmodifiableList");
		}

		// LPS-70963

		if (newContent.contains("java.util.WeakHashMap")) {
			processMessage(
				fileName,
				"Do not use java.util.WeakHashMap because it is not " +
					"thread-safe");
		}

		// LPS-28266

		for (int pos1 = -1;;) {
			pos1 = newContent.indexOf(StringPool.TAB + "try {", pos1 + 1);

			if (pos1 == -1) {
				break;
			}

			int pos2 = newContent.indexOf(StringPool.TAB + "try {", pos1 + 1);
			int pos3 = newContent.indexOf("\"select count(", pos1);

			if ((pos2 != -1) && (pos3 != -1) && (pos2 < pos3)) {
				continue;
			}

			int pos4 = newContent.indexOf("rs.getLong(1)", pos1);
			int pos5 = newContent.indexOf(StringPool.TAB + "finally {", pos1);

			if ((pos3 == -1) || (pos4 == -1) || (pos5 == -1)) {
				break;
			}

			if ((pos3 < pos4) && (pos4 < pos5)) {
				processMessage(
					fileName, "Use rs.getInt(1) for count, see LPS-28266");
			}
		}

		// LPS-33070

		matcher = _processCallablePattern.matcher(content);

		if (matcher.find() &&
			!content.contains("private static final long serialVersionUID")) {

			processMessage(
				fileName,
				"Assign ProcessCallable implementation a serialVersionUID");
		}

		newContent = formatStringBundler(fileName, newContent, _maxLineLength);

		newContent = StringUtil.replace(
			newContent, StringPool.TAB + "for (;;) {",
			StringPool.TAB + "while (true) {");

		// LPS-39508

		if (!isRunOutsidePortalExclusion &&
			!isExcludedPath(_SECURE_RANDOM_EXCLUDES, absolutePath) &&
			content.contains("java.security.SecureRandom") &&
			!content.contains("javax.crypto.KeyGenerator")) {

			processMessage(
				fileName,
				"Use SecureRandomUtil or com.liferay.portal.kernel.security." +
					"SecureRandom instead of java.security.SecureRandom");
		}

		// LPS-46017

		newContent = StringUtil.replace(
			newContent, " static interface ", " interface ");

		// LPS-47648

		if ((portalSource || subrepository) &&
			(fileName.contains("/test/integration/") ||
			 fileName.contains("/testIntegration/java"))) {

			newContent = StringUtil.replace(
				newContent, "FinderCacheUtil.clearCache();", StringPool.BLANK);
		}

		// LPS-47682

		newContent = fixIncorrectParameterTypeForLanguageUtil(
			newContent, false, fileName);

		if (portalSource && fileName.contains("/portal-kernel/") &&
			content.contains("import javax.servlet.jsp.")) {

			processMessage(
				fileName,
				"Never import javax.servlet.jsp.* from portal-kernel, see " +
					"LPS-47682");
		}

		// LPS-55690

		if (newContent.contains("org.testng.Assert")) {
			processMessage(
				fileName,
				"Use org.junit.Assert instead of org.testng.Assert, see " +
					"LPS-55690");
		}

		// LPS-48156

		newContent = checkPrincipalException(newContent);

		// LPS-60473

		if (newContent.contains(".supportsBatchUpdates()") &&
			!fileName.endsWith("AutoBatchPreparedStatementUtil.java")) {

			processMessage(
				fileName,
				"Use AutoBatchPreparedStatementUtil instead of " +
					"DatabaseMetaData.supportsBatchUpdates, see LPS-60473");
		}

		// LPS-64056

		if (newContent.contains("Configurable.createConfigurable(") &&
			!fileName.endsWith("ConfigurableUtil.java")) {

			processMessage(
				fileName,
				"Use ConfigurableUtil.createConfigurable instead of " +
					"Configurable.createConfigurable, see LPS-64056");
		}

		// LPS-62786

		checkPropertyUtils(fileName, newContent);

		// LPS-65229

		if (fileName.endsWith("ResourceCommand.java") &&
			newContent.contains("ServletResponseUtil.sendFile(")) {

			processMessage(
				fileName,
				"Use PortletResponseUtil.sendFile instead of " +
					"ServletResponseUtil.sendFile");
		}

		if (!fileName.endsWith("GetterUtilTest.java")) {
			checkGetterUtilGet(fileName, newContent);
		}

		// LPS-69494

		if (!fileName.endsWith("AbstractExtender.java") &&
			newContent.contains(
				"org.apache.felix.utils.extender.AbstractExtender")) {

			StringBundler sb = new StringBundler(4);

			sb.append("Use com.liferay.osgi.felix.util.AbstractExtender ");
			sb.append("instead of ");
			sb.append("org.apache.felix.utils.extender.AbstractExtender, see ");
			sb.append("LPS-69494");

			processMessage(fileName, sb.toString());
		}

		matcher = _incorrectSynchronizedPattern.matcher(newContent);

		newContent = matcher.replaceAll("$1$3 $2");

		pos = newContent.indexOf("\npublic ");

		if (pos != -1) {
			String javaClassContent = newContent.substring(pos + 1);

			int javaClassLineCount = getLineCount(newContent, pos + 1);

			newContent = formatJavaTerms(
				className, packagePath, file, fileName, absolutePath,
				newContent, javaClassContent, javaClassLineCount,
				StringPool.BLANK, _CHECK_JAVA_FIELD_TYPES_EXCLUDES,
				_JAVATERM_SORT_EXCLUDES, _TEST_ANNOTATIONS_EXCLUDES);
		}

		matcher = _anonymousClassPattern.matcher(newContent);

		while (matcher.find()) {
			if (getLevel(matcher.group()) != 0) {
				continue;
			}

			int x = matcher.start() + 1;
			int y = matcher.end();

			while (true) {
				String javaClassContent = newContent.substring(x, y);

				if (getLevel(javaClassContent, "{", "}") != 0) {
					y++;

					continue;
				}

				int javaClassLineCount = getLineCount(
					newContent, matcher.start() + 1);

				newContent = formatJavaTerms(
					StringPool.BLANK, StringPool.BLANK, file, fileName,
					absolutePath, newContent, javaClassContent,
					javaClassLineCount, matcher.group(1),
					_CHECK_JAVA_FIELD_TYPES_EXCLUDES, _JAVATERM_SORT_EXCLUDES,
					_TEST_ANNOTATIONS_EXCLUDES);

				break;
			}
		}

		return formatJava(fileName, absolutePath, newContent);
	}

	@Override
	protected List<String> doGetFileNames() throws Exception {
		String[] includes = getIncludes();

		if (ArrayUtil.isEmpty(includes)) {
			return new ArrayList<>();
		}

		Collection<String> fileNames = null;

		if (portalSource || subrepository) {
			fileNames = getPortalJavaFiles(includes);
		}
		else {
			fileNames = getPluginJavaFiles(includes);
		}

		return new ArrayList<>(fileNames);
	}

	@Override
	protected String[] doGetIncludes() {
		return _INCLUDES;
	}

	protected String formatJava(
			String fileName, String absolutePath, String content)
		throws Exception {

		StringBundler sb = new StringBundler();

		try (UnsyncBufferedReader unsyncBufferedReader =
				new UnsyncBufferedReader(new UnsyncStringReader(content))) {

			String line = null;
			String previousLine = StringPool.BLANK;

			int lineCount = 0;

			String packageName = StringPool.BLANK;

			while ((line = unsyncBufferedReader.readLine()) != null) {
				lineCount++;

				if (line.startsWith("package ")) {
					packageName = line.substring(8, line.length() - 1);
				}

				if (line.startsWith("import ")) {
					int pos = line.lastIndexOf(CharPool.PERIOD);

					if (pos != -1) {
						String importPackageName = line.substring(7, pos);

						if (importPackageName.equals(packageName)) {
							continue;
						}
					}
				}

				String trimmedLine = StringUtil.trimLeading(line);

				line = replacePrimitiveWrapperInstantiation(line);

				checkEmptyCollection(trimmedLine, fileName, lineCount);

				line = formatEmptyArray(line);

				checkInefficientStringMethods(
					line, fileName, absolutePath, lineCount, true);

				int lineLeadingTabCount = getLeadingTabCount(line);
				int previousLineLeadingTabCount = getLeadingTabCount(
					previousLine);

				if (!trimmedLine.startsWith(StringPool.DOUBLE_SLASH) &&
					!trimmedLine.startsWith(StringPool.STAR)) {

					String strippedQuotesLine = stripQuotes(trimmedLine);

					String indent = StringPool.BLANK;

					if (!trimmedLine.startsWith(StringPool.CLOSE_CURLY_BRACE) &&
						strippedQuotesLine.contains(
							StringPool.CLOSE_CURLY_BRACE)) {

						if ((getLevel(strippedQuotesLine, "{", "}") < 0) &&
							(lineLeadingTabCount > 0)) {

							for (int i = 0; i < lineLeadingTabCount - 1; i++) {
								indent += StringPool.TAB;
							}

							int x = line.lastIndexOf(
								CharPool.CLOSE_CURLY_BRACE);

							return StringUtil.replace(
								content, "\n" + line + "\n",
								"\n" + line.substring(0, x) + "\n" + indent +
									line.substring(x) + "\n");
						}
					}

					if (!previousLine.contains("\tthrows ") &&
						!previousLine.contains(" throws ") &&
						(previousLineLeadingTabCount ==
							(lineLeadingTabCount - 1))) {

						int x = -1;

						while (true) {
							x = previousLine.indexOf(", ", x + 1);

							if (x == -1) {
								break;
							}

							if (ToolsUtil.isInsideQuotes(previousLine, x)) {
								continue;
							}

							String linePart = previousLine.substring(0, x);

							linePart = stripQuotes(linePart);

							if ((getLevel(linePart, "(", ")") != 0) ||
								(getLevel(linePart, "<", ">") != 0)) {

								continue;
							}

							linePart = previousLine.substring(x);

							linePart = stripQuotes(linePart, CharPool.QUOTE);

							if ((getLevel(linePart, "(", ")") != 0) ||
								(getLevel(linePart, "<", ">") != 0)) {

								continue;
							}

							if (Validator.isNull(indent)) {
								for (int i = 0; i < lineLeadingTabCount - 1;
										i++) {

									indent += StringPool.TAB;
								}
							}

							return StringUtil.replace(
								content, "\n" + previousLine + "\n",
								"\n" + previousLine.substring(0, x + 1) + "\n" +
									indent + previousLine.substring(x + 2) +
										"\n");
						}
					}
				}

				if (lineCount > 1) {
					sb.append(previousLine);
					sb.append("\n");
				}

				previousLine = line;
			}

			sb.append(previousLine);
		}

		String newContent = sb.toString();

		if (newContent.endsWith("\n")) {
			newContent = newContent.substring(0, newContent.length() - 1);
		}

		return newContent;
	}

	@Override
	protected List<FileCheck> getFileChecks() {
		return _fileChecks;
	}

	protected String[] getPluginExcludes(String pluginDirectoryName) {
		return new String[] {
			pluginDirectoryName + "**/model/*Clp.java",
			pluginDirectoryName + "**/model/impl/*BaseImpl.java",
			pluginDirectoryName + "**/model/impl/*Model.java",
			pluginDirectoryName + "**/model/impl/*ModelImpl.java",
			pluginDirectoryName + "**/service/**/service/*Service.java",
			pluginDirectoryName + "**/service/**/service/*ServiceClp.java",
			pluginDirectoryName + "**/service/**/service/*ServiceFactory.java",
			pluginDirectoryName + "**/service/**/service/*ServiceUtil.java",
			pluginDirectoryName + "**/service/**/service/*ServiceWrapper.java",
			pluginDirectoryName + "**/service/**/service/ClpSerializer.java",
			pluginDirectoryName +
				"**/service/**/service/messaging/*ClpMessageListener.java",
			pluginDirectoryName +
				"**/service/**/service/persistence/*Finder.java",
			pluginDirectoryName +
				"**/service/**/service/persistence/*Util.java",
			pluginDirectoryName + "**/service/base/*ServiceBaseImpl.java",
			pluginDirectoryName + "**/service/base/*ServiceClpInvoker.java",
			pluginDirectoryName + "**/service/http/*JSONSerializer.java",
			pluginDirectoryName + "**/service/http/*ServiceHttp.java",
			pluginDirectoryName + "**/service/http/*ServiceJSON.java",
			pluginDirectoryName + "**/service/http/*ServiceSoap.java",
			pluginDirectoryName + "**/tools/templates/**"
		};
	}

	protected Collection<String> getPluginJavaFiles(String[] includes)
		throws Exception {

		Collection<String> fileNames = new TreeSet<>();

		String[] excludes = getPluginExcludes(StringPool.BLANK);

		fileNames.addAll(getFileNames(excludes, includes));

		return fileNames;
	}

	protected String getPortalCustomSQLContent() throws Exception {
		if (_portalCustomSQLContent != null) {
			return _portalCustomSQLContent;
		}

		File portalCustomSQLFile = getFile(
			"portal-impl/src/custom-sql/default.xml", PORTAL_MAX_DIR_LEVEL);

		String portalCustomSQLContent = FileUtil.read(portalCustomSQLFile);

		Matcher matcher = _customSQLFilePattern.matcher(portalCustomSQLContent);

		while (matcher.find()) {
			File customSQLFile = getFile(
				"portal-impl/src/" + matcher.group(1), PORTAL_MAX_DIR_LEVEL);

			if (customSQLFile != null) {
				portalCustomSQLContent += FileUtil.read(customSQLFile);
			}
		}

		_portalCustomSQLContent = portalCustomSQLContent;

		return _portalCustomSQLContent;
	}

	protected Collection<String> getPortalJavaFiles(String[] includes)
		throws Exception {

		Collection<String> fileNames = new TreeSet<>();

		String[] excludes = new String[] {
			"**/*_IW.java", "**/counter/service/**", "**/jsp/*",
			"**/model/impl/*Model.java", "**/model/impl/*ModelImpl.java",
			"**/portal/service/**", "**/portal-client/**",
			"**/portal-web/test/**/*Test.java", "**/test/*-generated/**"
		};

		for (String directoryName : getPluginsInsideModulesDirectoryNames()) {
			excludes = ArrayUtil.append(
				excludes, getPluginExcludes("**" + directoryName));
		}

		fileNames.addAll(getFileNames(excludes, includes));

		excludes = new String[] {
			"**/portal-client/**", "**/tools/ext_tmpl/**", "**/*_IW.java",
			"**/test/**/*PersistenceTest.java"
		};
		includes = new String[] {
			"**/com/liferay/portal/kernel/service/ServiceContext*.java",
			"**/model/BaseModel.java", "**/model/impl/BaseModelImpl.java",
			"**/portal-test/**/portal/service/**/*.java",
			"**/portal-test-integration/**/portal/service/**/*.java",
			"**/service/Base*.java",
			"**/service/PersistedModelLocalService*.java",
			"**/service/configuration/**/*.java",
			"**/service/http/*HttpTest.java", "**/service/http/*SoapTest.java",
			"**/service/http/TunnelUtil.java", "**/service/impl/*.java",
			"**/service/jms/*.java", "**/service/permission/*.java",
			"**/service/persistence/BasePersistence.java",
			"**/service/persistence/BatchSession*.java",
			"**/service/persistence/*FinderImpl.java",
			"**/service/persistence/*Query.java",
			"**/service/persistence/impl/*.java",
			"**/portal-impl/test/**/*.java", "**/util-bridges/**/*.java"
		};

		fileNames.addAll(getFileNames(excludes, includes));

		return fileNames;
	}

	protected List<File> getSuppressionsFiles() throws Exception {
		String fileName = "checkstyle-suppressions.xml";

		List<File> suppressionsFiles = new ArrayList<>();

		// Find suppressions file in portal-impl/src/

		if (portalSource) {
			File suppressionsFile = getFile(
				"portal-impl/src/" + fileName, PORTAL_MAX_DIR_LEVEL);

			if (suppressionsFile != null) {
				suppressionsFiles.add(suppressionsFile);
			}
		}

		// Find suppressions files in any parent directory

		int maxDirLevel = PLUGINS_MAX_DIR_LEVEL;
		String parentDirName = sourceFormatterArgs.getBaseDirName();

		if (portalSource || subrepository) {
			maxDirLevel = PORTAL_MAX_DIR_LEVEL - 1;
			parentDirName += "../";
		}

		for (int i = 0; i < maxDirLevel; i++) {
			File suppressionsFile = new File(parentDirName + fileName);

			if (suppressionsFile.exists()) {
				suppressionsFiles.add(suppressionsFile);
			}

			parentDirName += "../";
		}

		if (!portalSource && !subrepository) {
			return suppressionsFiles;
		}

		// Find suppressions files in any child directory

		List<String> moduleSuppressionsFileNames = getFileNames(
			sourceFormatterArgs.getBaseDirName(), null, new String[0],
			new String[] {"**/modules/**/" + fileName});

		for (String moduleSuppressionsFileName : moduleSuppressionsFileNames) {
			moduleSuppressionsFileName = StringUtil.replace(
				moduleSuppressionsFileName, CharPool.BACK_SLASH,
				CharPool.SLASH);

			suppressionsFiles.add(new File(moduleSuppressionsFileName));
		}

		return suppressionsFiles;
	}

	protected boolean hasGeneratedTag(String content) {
		if ((content.contains("* @generated") || content.contains("$ANTLR")) &&
			!content.contains("hasGeneratedTag")) {

			return true;
		}
		else {
			return false;
		}
	}

	@Override
	protected void populateFileChecks() throws Exception {
		_fileChecks.add(new JavaWhitespaceCheck());

		_fileChecks.add(
			new CopyrightCheck(
				getContent(
					sourceFormatterArgs.getCopyrightFileName(),
					PORTAL_MAX_DIR_LEVEL)));
		_fileChecks.add(new JavaAnnotationsCheck());
		_fileChecks.add(new JavaAssertEqualsCheck());
		_fileChecks.add(new JavaBooleanUsageCheck());
		_fileChecks.add(
			new JavaCombineLinesCheck(
				getExcludes(_FIT_ON_SINGLE_LINE_EXCLUDES),
				sourceFormatterArgs.getMaxLineLength()));
		_fileChecks.add(new JavaDataAccessConnectionCheck());
		_fileChecks.add(
			new JavaDiamondOperatorCheck(
				getExcludes(_DIAMOND_OPERATOR_EXCLUDES)));
		_fileChecks.add(
			new JavaDeserializationSecurityCheck(
				getExcludes(_SECURE_DESERIALIZATION_EXCLUDES),
				getExcludes(RUN_OUTSIDE_PORTAL_EXCLUDES)));
		_fileChecks.add(new JavaEmptyLinesCheck());
		_fileChecks.add(new JavaExceptionCheck());
		_fileChecks.add(new JavaFinderCacheCheck());
		_fileChecks.add(
			new JavaHibernateSQLCheck(
				getExcludes(_HIBERNATE_SQL_QUERY_EXCLUDES)));
		_fileChecks.add(
			new JavaIfStatementCheck(sourceFormatterArgs.getMaxLineLength()));
		_fileChecks.add(new JavaIOExceptionCheck());
		_fileChecks.add(
			new JavaLineBreakCheck(sourceFormatterArgs.getMaxLineLength()));
		_fileChecks.add(new JavaLogLevelCheck());
		_fileChecks.add(
			new JavaLongLinesCheck(
				getExcludes(_LINE_LENGTH_EXCLUDES),
				sourceFormatterArgs.getMaxLineLength()));
		_fileChecks.add(new JavaPackagePathCheck());
		_fileChecks.add(new JavaSeeAnnotationCheck());
		_fileChecks.add(new JavaStopWatchCheck());
		_fileChecks.add(new JavaSystemExceptionCheck());
		_fileChecks.add(
			new MethodCallsOrderCheck(getExcludes(METHOD_CALL_SORT_EXCLUDES)));
		_fileChecks.add(new SessionKeysCheck());
		_fileChecks.add(new StringUtilCheck());
		_fileChecks.add(new UnparameterizedClassCheck());
		_fileChecks.add(new ValidatorEqualsCheck());

		if (portalSource || subrepository) {
			_fileChecks.add(new JavaSystemEventAnnotationCheck());
			_fileChecks.add(
				new JavaVerifyUpgradeConnectionCheck(
					getExcludes(_UPGRADE_DATA_ACCESS_CONNECTION_EXCLUDES)));
			_fileChecks.add(
				new JavaUpgradeClassCheck(
					getExcludes(_UPGRADE_SERVICE_UTIL_EXCLUDES)));
			_fileChecks.add(
				new JavaXMLSecurityCheck(
					getExcludes(RUN_OUTSIDE_PORTAL_EXCLUDES),
					getExcludes(_SECURE_XML_EXCLUDES)));
			_fileChecks.add(
				new ResourceBundleCheck(
					getExcludes(RUN_OUTSIDE_PORTAL_EXCLUDES)));
		}

		if (portalSource) {
			_fileChecks.add(
				new LanguageKeysCheck(
					getExcludes(LANGUAGE_KEYS_CHECK_EXCLUDES),
					getPortalLanguageProperties()));
		}

		if (GetterUtil.getBoolean(
				getProperty("add.missing.deprecation.release.version"))) {

			_fileChecks.add(
				new JavaDeprecatedJavadocCheck(portalSource, subrepository));
		}
	}

	@Override
	protected void populateModuleFileChecks() throws Exception {
		_fileChecks.add(new JavaModuleExtendedObjectClassDefinitionCheck(subrepository));

		boolean checkRegistryInTestClasses = GetterUtil.getBoolean(
			System.getProperty(
				"source.formatter.check.registry.in.test.classes"));

		_fileChecks.add(
			new JavaModuleIllegalImportsCheck(
				subrepository, checkRegistryInTestClasses));

		_fileChecks.add(new JavaModuleInternalImportsCheck(subrepository));
		_fileChecks.add(new JavaModuleServiceProxyFactoryCheck(subrepository));
		_fileChecks.add(new JavaModuleTestCheck(subrepository));
		_fileChecks.add(
			new JavaOSGiReferenceCheck(
				_getModuleFileNamesMap(), subrepository));
	}

	@Override
	protected void postFormat() throws Exception {
		processCheckStyle();
	}

	@Override
	protected void preFormat() throws Exception {
		_maxLineLength = sourceFormatterArgs.getMaxLineLength();

		_allowUseServiceUtilInServiceImpl = GetterUtil.getBoolean(
			getProperty("allow.use.service.util.in.service.impl"));
	}

	protected void processCheckStyle() throws Exception {
		if (_ungeneratedFiles.isEmpty()) {
			return;
		}

		Set<SourceFormatterMessage> sourceFormatterMessages =
			CheckStyleUtil.process(
				_ungeneratedFiles, getSuppressionsFiles(),
				sourceFormatterArgs.getBaseDirName());

		for (SourceFormatterMessage sourceFormatterMessage :
				sourceFormatterMessages) {

			processMessage(
				sourceFormatterMessage.getFileName(),
				sourceFormatterMessage.getMessage(),
				sourceFormatterMessage.getLineCount());

			printError(
				sourceFormatterMessage.getFileName(),
				sourceFormatterMessage.toString());
		}
	}

	@Override
	protected String processFileChecks(
			String fileName, String absolutePath, String content)
		throws Exception {

		if (hasGeneratedTag(content)) {
			return content;
		}

		return super.processFileChecks(fileName, absolutePath, content);
	}

	private Map<String, String> _getModuleFileNamesMap() throws Exception {
		Map<String, String> moduleFileNamesMap = new HashMap<>();

		List<String> fileNames = new ArrayList<>();

		String moduleRootDirLocation = "modules/";

		for (int i = 0; i < 6; i++) {
			File file = new File(
				sourceFormatterArgs.getBaseDirName() + moduleRootDirLocation);

			if (file.exists()) {
				fileNames = getFileNames(
					sourceFormatterArgs.getBaseDirName() +
						moduleRootDirLocation,
					null, new String[0], getIncludes());

				break;
			}

			moduleRootDirLocation = "../" + moduleRootDirLocation;
		}

		for (String fileName : fileNames) {
			fileName = StringUtil.replace(
				fileName, CharPool.BACK_SLASH, CharPool.SLASH);

			String className = StringUtil.replace(
				fileName, CharPool.SLASH, CharPool.PERIOD);

			int pos = className.lastIndexOf(".com.liferay.");

			className = className.substring(pos + 1, fileName.length() - 5);

			moduleFileNamesMap.put(className, fileName);
		}

		return moduleFileNamesMap;
	}

	private static final String _CHECK_JAVA_FIELD_TYPES_EXCLUDES =
		"check.java.field.types.excludes";

	private static final String _DIAMOND_OPERATOR_EXCLUDES =
		"diamond.operator.excludes";

	private static final String _FIT_ON_SINGLE_LINE_EXCLUDES =
		"fit.on.single.line.excludes";

	private static final String _HIBERNATE_SQL_QUERY_EXCLUDES =
		"hibernate.sql.query.excludes";

	private static final String[] _INCLUDES = new String[] {"**/*.java"};

	private static final String _JAVATERM_SORT_EXCLUDES =
		"javaterm.sort.excludes";

	private static final String _LINE_LENGTH_EXCLUDES = "line.length.excludes";

	private static final String _PROXY_EXCLUDES = "proxy.excludes";

	private static final String _SECURE_DESERIALIZATION_EXCLUDES =
		"secure.deserialization.excludes";

	private static final String _SECURE_RANDOM_EXCLUDES =
		"secure.random.excludes";

	private static final String _SECURE_XML_EXCLUDES = "secure.xml.excludes";

	private static final String _STATIC_LOG_EXCLUDES = "static.log.excludes";

	private static final String _TEST_ANNOTATIONS_EXCLUDES =
		"test.annotations.excludes";

	private static final String _UPGRADE_DATA_ACCESS_CONNECTION_EXCLUDES =
		"upgrade.data.access.connection.excludes";

	private static final String _UPGRADE_SERVICE_UTIL_EXCLUDES =
		"upgrade.service.util.excludes";

	private boolean _allowUseServiceUtilInServiceImpl;
	private final Pattern _anonymousClassPattern = Pattern.compile(
		"\n(\t+)(\\S.* )?new (.|\\(\n)*\\) \\{\n\n");
	private final Pattern _customSQLFilePattern = Pattern.compile(
		"<sql file=\"(.*)\" \\/>");
	private final List<FileCheck> _fileChecks = new ArrayList<>();
	private final Pattern _incorrectSynchronizedPattern = Pattern.compile(
		"([\n\t])(synchronized) (private|public|protected)");
	private final Pattern _logPattern = Pattern.compile(
		"\n\tprivate static final Log _log = LogFactoryUtil.getLog\\(\n*" +
			"\t*(.+)\\.class\\)");
	private int _maxLineLength;
	private final Pattern _packagePattern = Pattern.compile(
		"(\n|^)\\s*package (.*);\n");
	private String _portalCustomSQLContent;
	private final Pattern _processCallablePattern = Pattern.compile(
		"implements ProcessCallable\\b");
	private final Set<File> _ungeneratedFiles = new CopyOnWriteArraySet<>();

}