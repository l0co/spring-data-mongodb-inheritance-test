package org.springframework.data.mongodb.repository.query;

import org.springframework.lang.Nullable;

/**
 * This hack is because of stupid {@link MongoQueryMethod} design with package access rules to key fields to make some logic on them.
 *
 * @author Lukasz Frankowski (http://lifeinide.com)
 */
public class MongoQueryMethodExtractor {

	@Nullable
	public static String extractAnnotatatedQuery(MongoQueryMethod method) {
		return method.getAnnotatedQuery();
	}

}
