/*
 * Copyright (c)  [2011-2016] "Pivotal Software, Inc." / "Neo Technology" / "Graph Aware Ltd."
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with
 * separate copyright notices and license terms. Your use of the source
 * code for these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 *
 */

package org.springframework.data.neo4j.integration.conversion;

import org.springframework.core.convert.converter.Converter;

/**
 * Nonsensical spring converter from a byte[] to an Integer, for test purposes
 * @author Luanne Misquitta
 */
public class SpringByteArrayToIntegerConverter implements Converter<byte[],Integer> {

	@Override
	public Integer convert(byte[] source) {
		return source.length;
	}
}
