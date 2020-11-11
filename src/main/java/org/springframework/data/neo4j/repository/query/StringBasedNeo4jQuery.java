/*
 * Copyright 2011-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.neo4j.repository.query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.neo4j.driver.types.MapAccessor;
import org.neo4j.driver.types.TypeSystem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.neo4j.core.Neo4jOperations;
import org.springframework.data.neo4j.core.PreparedQuery;
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.SpelEvaluator;
import org.springframework.data.repository.query.SpelQueryContext;
import org.springframework.data.repository.query.SpelQueryContext.SpelExtractor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Implementation of {@link RepositoryQuery} for query methods annotated with {@link Query @Query}. The flow to handle
 * queries with SpEL parameters is as follows
 * <ol>
 * <li>Parse template as something that has SpEL-expressions in it</li>
 * <li>Replace the SpEL-expressions with Neo4j Statement template parameters</li>
 * <li>The parameters passed here _and_ the values that might have been computed during SpEL-parsing</li>
 * </ol>
 * The main ingredient is a SpelEvaluator, that parses a template and replaces SpEL expressions with real Neo4j
 * parameters.
 *
 * @author Gerrit Meier
 * @author Michael J. Simons
 * @since 6.0
 */
final class StringBasedNeo4jQuery extends AbstractNeo4jQuery {

	/**
	 * Used for extracting SpEL expressions inside Cypher query templates.
	 */
	static final SpelQueryContext SPEL_QUERY_CONTEXT = SpelQueryContext.of(StringBasedNeo4jQuery::parameterNameSource,
			StringBasedNeo4jQuery::replacementSource);

	/**
	 * Used to evaluate the expression found while parsing the cypher template of this query against the actual parameters
	 * with the help of the formal parameters during the building of the {@link PreparedQuery}.
	 */
	private final SpelEvaluator spelEvaluator;

	/**
	 * The Cypher string used for this query. The cypher query will not be changed after parsed via
	 * {@link #SPEL_QUERY_CONTEXT}. All SpEL expressions will be substituted via "native" parameter placeholders. This
	 * will be done via the {@link #spelEvaluator}.
	 */
	private final String cypherQuery;

	/**
	 * Create a {@link StringBasedNeo4jQuery} for a query method that is annotated with {@link Query @Query}. The
	 * annotation is expected to have a value.
	 *
	 * @param neo4jOperations the Neo4j operations
	 * @param mappingContext a Neo4jMappingContext instance
	 * @param evaluationContextProvider a QueryMethodEvaluationContextProvider instance
	 * @param queryMethod the query method
	 * @return A new instance of a String based Neo4j query.
	 */
	static StringBasedNeo4jQuery create(Neo4jOperations neo4jOperations, Neo4jMappingContext mappingContext,
			QueryMethodEvaluationContextProvider evaluationContextProvider, Neo4jQueryMethod queryMethod) {

		Query queryAnnotation = queryMethod.getQueryAnnotation()
				.orElseThrow(() -> new MappingException("Expected @Query annotation on the query method!"));

		if (queryAnnotation.countQuery().isEmpty() && (queryMethod.isSliceQuery() || queryMethod.isPageQuery())) {
			throw new MappingException("Expected paging query method to have a count query!");
		}

		String cypherTemplate = Optional.ofNullable(queryAnnotation.value()).filter(StringUtils::hasText)
				.orElseThrow(() -> new MappingException("Expected @Query annotation to have a value, but it did not."));

		return new StringBasedNeo4jQuery(neo4jOperations, mappingContext, evaluationContextProvider, queryMethod,
				cypherTemplate, Neo4jQueryType.fromDefinition(queryAnnotation));
	}

	/**
	 * Create a {@link StringBasedNeo4jQuery} based on an explicit Cypher template.
	 *
	 * @param neo4jOperations the Neo4j operations
	 * @param mappingContext a Neo4jMappingContext instance
	 * @param evaluationContextProvider a QueryMethodEvaluationContextProvider instance
	 * @param queryMethod the query method
	 * @param cypherTemplate The template to use.
	 * @return A new instance of a String based Neo4j query.
	 */
	static StringBasedNeo4jQuery create(Neo4jOperations neo4jOperations, Neo4jMappingContext mappingContext,
			QueryMethodEvaluationContextProvider evaluationContextProvider, Neo4jQueryMethod queryMethod,
			String cypherTemplate) {

		Assert.hasText(cypherTemplate, "Cannot create String based Neo4j query without a cypher template.");

		return new StringBasedNeo4jQuery(neo4jOperations, mappingContext, evaluationContextProvider, queryMethod,
				cypherTemplate, Neo4jQueryType.DEFAULT);
	}

	private StringBasedNeo4jQuery(Neo4jOperations neo4jOperations, Neo4jMappingContext mappingContext,
			QueryMethodEvaluationContextProvider evaluationContextProvider, Neo4jQueryMethod queryMethod,
			String cypherTemplate, Neo4jQueryType queryType) {

		super(neo4jOperations, mappingContext, queryMethod, queryType);

		SpelExtractor spelExtractor = SPEL_QUERY_CONTEXT.parse(cypherTemplate);
		this.spelEvaluator = new SpelEvaluator(evaluationContextProvider, queryMethod.getParameters(), spelExtractor);
		this.cypherQuery = spelExtractor.getQueryString();
	}

	@Override
	protected <T extends Object> PreparedQuery<T> prepareQuery(Class<T> returnedType, List<String> includedProperties,
			Neo4jParameterAccessor parameterAccessor, @Nullable Neo4jQueryType queryType,
			@Nullable BiFunction<TypeSystem, MapAccessor, ?> mappingFunction) {

		return PreparedQuery.queryFor(returnedType).withCypherQuery(cypherQuery)
				.withParameters(bindParameters(parameterAccessor)).usingMappingFunction(mappingFunction).build();
	}

	Map<String, Object> bindParameters(Neo4jParameterAccessor parameterAccessor) {

		final Parameters<?, ?> formalParameters = parameterAccessor.getParameters();
		Map<String, Object> resolvedParameters = new HashMap<>();

		// Values from the parameter accessor can only get converted after evaluation
		for (Map.Entry<String, Object> evaluatedParam : spelEvaluator.evaluate(parameterAccessor.getValues()).entrySet()) {
			resolvedParameters.put(evaluatedParam.getKey(), super.convertParameter(evaluatedParam.getValue()));
		}

		formalParameters.getBindableParameters().forEach(parameter -> {

			int parameterIndex = parameter.getIndex();
			Object parameterValue = super.convertParameter(parameterAccessor.getBindableValue(parameterIndex));

			// Add the parameter under its name when possible
			parameter.getName().ifPresent(parameterName -> resolvedParameters.put(parameterName, parameterValue));
			// Always add under its index.
			resolvedParameters.put(Integer.toString(parameterIndex), parameterValue);
		});

		if (formalParameters.hasPageableParameter()) {
			Pageable pageable = parameterAccessor.getPageable();
			resolvedParameters.put("limit", pageable.getPageSize());
			resolvedParameters.put("skip", pageable.getOffset());
		}

		return resolvedParameters;
	}

	@Override
	protected Optional<PreparedQuery<Long>> getCountQuery(Neo4jParameterAccessor parameterAccessor) {

		return queryMethod.getQueryAnnotation().map(queryAnnotation ->
				PreparedQuery.queryFor(Long.class)
						.withCypherQuery(queryAnnotation.countQuery())
						.withParameters(bindParameters(parameterAccessor)).build());
	}

	/**
	 * @param index position of this parameter placeholder
	 * @param originalSpelExpression Not used for configuring parameter names atm.
	 * @return A new parameter name for the given index.
	 */
	private static String parameterNameSource(int index, @SuppressWarnings("unused") String originalSpelExpression) {
		return "__SpEL__" + index;
	}

	/**
	 * @param originalPrefix The prefix passed to the replacement source is either ':' or '?', so that isn't usable for
	 *          Cypher templates and therefore ignored.
	 * @param parameterName name of the parameter
	 * @return The name of the parameter in its native Cypher form.
	 */
	private static String replacementSource(@SuppressWarnings("unused") String originalPrefix, String parameterName) {
		return "$" + parameterName;
	}
}
