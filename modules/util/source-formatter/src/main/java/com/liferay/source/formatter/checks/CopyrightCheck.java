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

package com.liferay.source.formatter.checks;

import com.liferay.portal.kernel.util.CharPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Tuple;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.source.formatter.SourceFormatterMessage;
import com.liferay.source.formatter.util.FileUtil;

import java.io.File;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Hugo Huijser
 */
public class CopyrightCheck extends BaseFileCheck {

	public CopyrightCheck(String copyright) {
		_copyright = copyright;
	}

	@Override
	public Tuple process(String fileName, String absolutePath, String content)
		throws Exception {

		if (fileName.endsWith(".tpl") || fileName.endsWith(".vm") ||
			Validator.isNull(_copyright)) {

			return new Tuple(content, Collections.emptySet());
		}

		Set<SourceFormatterMessage> sourceFormatterMessages = new HashSet<>();

		content = _fixCopyright(
			sourceFormatterMessages, fileName, absolutePath, content);

		return new Tuple(content, sourceFormatterMessages);
	}

	private String _fixCopyright(
			Set<SourceFormatterMessage> sourceFormatterMessages,
			String fileName, String absolutePath, String content)
		throws Exception {

		String customCopyright = _getCustomCopyright(absolutePath);

		if (!content.contains(_copyright) &&
			((customCopyright == null) || !content.contains(_copyright))) {

			addMessage(sourceFormatterMessages, fileName, "Missing copyright");
		}
		else if (!content.startsWith(_copyright) &&
				 !content.startsWith("<%--\n" + _copyright) &&
				 ((customCopyright == null) ||
				  (!content.startsWith(customCopyright) &&
				   !content.startsWith("<%--\n" + customCopyright)))) {

			addMessage(
				sourceFormatterMessages, fileName,
				"File must start with copyright");
		}

		if (fileName.endsWith(".jsp") || fileName.endsWith(".jspf")) {
			content = StringUtil.replace(
				content, "<%\n" + _copyright + "\n%>",
				"<%--\n" + _copyright + "\n--%>");

			content = StringUtil.replace(
				content, "<%\n" + customCopyright + "\n%>",
				"<%--\n" + customCopyright + "\n--%>");
		}

		return content;
	}

	private String _getCustomCopyright(String absolutePath) throws Exception {
		for (int x = absolutePath.length();;) {
			x = absolutePath.lastIndexOf(CharPool.SLASH, x);

			if (x == -1) {
				break;
			}

			String copyright = FileUtil.read(
				new File(absolutePath.substring(0, x + 1) + "copyright.txt"));

			if (Validator.isNotNull(copyright)) {
				return copyright;
			}

			x = x - 1;
		}

		return null;
	}

	private final String _copyright;

}