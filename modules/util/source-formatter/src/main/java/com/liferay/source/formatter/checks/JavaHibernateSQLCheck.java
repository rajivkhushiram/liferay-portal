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

import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Tuple;

import java.util.Collections;
import java.util.List;

/**
 * @author Hugo Huijser
 */
public class JavaHibernateSQLCheck extends BaseFileCheck {

	public JavaHibernateSQLCheck(List<String> excludes) {
		_excludes = excludes;
	}

	@Override
	public Tuple process(String fileName, String absolutePath, String content)
		throws Exception {

		if (!isExcludedPath(_excludes, absolutePath) &&
			content.contains("= session.createSynchronizedSQLQuery(") &&
			content.contains("com.liferay.portal.kernel.dao.orm.Session")) {

			content = StringUtil.replace(
				content, "= session.createSynchronizedSQLQuery(",
				"= session.createSynchronizedSQLQuery(");
		}

		return new Tuple(content, Collections.emptySet());
	}

	private final List<String> _excludes;

}