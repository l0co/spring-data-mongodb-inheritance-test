package com.example.demo;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.query.ConvertingParameterAccessor;
import org.springframework.data.mongodb.repository.query.MongoQueryMethod;
import org.springframework.data.mongodb.repository.query.PartTreeMongoQuery;

public class InheritanceAwarePartTreeMongoQuery extends PartTreeMongoQuery {

    private final Criteria inheritanceCriteria;

    public InheritanceAwarePartTreeMongoQuery(MongoQueryMethod method, MongoOperations mongoOperations) {
        super(method, mongoOperations);

		inheritanceCriteria = MongoClassInheritanceScanner.getInstance().createInheritanceCritera(
			method.getEntityInformation().getJavaType());
    }

    @Override
    protected Query createQuery(ConvertingParameterAccessor accessor) {
        Query query = super.createQuery(accessor);
        if (inheritanceCriteria != null) {
            query.addCriteria(inheritanceCriteria);
        }
        return query;
    }

    @Override
    protected Query createCountQuery(ConvertingParameterAccessor accessor) {
        Query query = super.createCountQuery(accessor);
        if (inheritanceCriteria != null) {
            query.addCriteria(inheritanceCriteria);
        }
        return query;
    }
}
