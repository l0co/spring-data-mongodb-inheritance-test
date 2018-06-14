package com.example.demo;

import org.bson.Document;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.query.MongoEntityInformation;
import org.springframework.data.mongodb.repository.support.SimpleMongoRepository;
import org.springframework.data.repository.support.PageableExecutionUtils;
import org.springframework.data.util.StreamUtils;
import org.springframework.data.util.Streamable;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

public class InheritanceAwareSimpleMongoRepository<T, ID extends Serializable> extends SimpleMongoRepository<T, ID> {

    private final MongoOperations mongoOperations;
    private final MongoEntityInformation<T, ID> entityInformation;
    private final Document classCriteriaDocument;
    private final Criteria classCriteria;

    public InheritanceAwareSimpleMongoRepository(MongoEntityInformation<T, ID> metadata,
            MongoOperations mongoOperations) {
        super(metadata, mongoOperations);
        this.mongoOperations = mongoOperations;
        this.entityInformation = metadata;

		classCriteria = MongoClassInheritanceScanner.getInstance().createInheritanceCritera(entityInformation.getJavaType());
		classCriteriaDocument = classCriteria!=null ? classCriteria.getCriteriaObject() : new Document();
    }

    protected Query getIdQuery(Object id) {
        return new Query(getIdCriteria(id));
    }

    protected Criteria getIdCriteria(Object id) {
        Criteria criteria = where(entityInformation.getIdAttribute()).is(id);
        if (classCriteria!=null)
            criteria.andOperator(classCriteria);
        return criteria;
    }

    protected Query getQuery() {
        return classCriteria!=null ? new Query(classCriteria) : new Query();
    }

    protected List<T> findAll(@Nullable Query query) {
        if (query == null)
            return Collections.emptyList();

        if (classCriteria!=null)
            query.addCriteria(classCriteria);

        return mongoOperations.find(query, entityInformation.getJavaType(), entityInformation.getCollectionName());
    }

    @Override
    public long count() {
        return classCriteria != null
            ? mongoOperations.getCollection(entityInformation.getCollectionName()).count(classCriteriaDocument)
            : mongoOperations.getCollection(entityInformation.getCollectionName()).count();
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.CrudRepository#save(java.lang.Object)
     */
    @Override
    public <S extends T> S save(S entity) {

        Assert.notNull(entity, "Entity must not be null!");

        if (entityInformation.isNew(entity)) {
            mongoOperations.insert(entity, entityInformation.getCollectionName());
        } else {
            mongoOperations.save(entity, entityInformation.getCollectionName());
        }

        return entity;
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.mongodb.repository.MongoRepository#saveAll(java.lang.Iterable)
     */
    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {

        Assert.notNull(entities, "The given Iterable of entities not be null!");

        Streamable<S> source = Streamable.of(entities);
        boolean allNew = source.stream().allMatch(it -> entityInformation.isNew(it));

        if (allNew) {

            List<S> result = source.stream().collect(Collectors.toList());
            mongoOperations.insertAll(result);
            return result;

        } else {
            return source.stream().map(this::save).collect(Collectors.toList());
        }
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.CrudRepository#findById(java.io.Serializable)
     */
    @Override
    public Optional<T> findById(ID id) {

        Assert.notNull(id, "The given id must not be null!");

        return Optional.ofNullable(
            mongoOperations.findOne(getIdQuery(id), entityInformation.getJavaType(), entityInformation.getCollectionName()));
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.CrudRepository#existsById(java.lang.Object)
     */
    @Override
    public boolean existsById(ID id) {

        Assert.notNull(id, "The given id must not be null!");

        return mongoOperations.exists(getIdQuery(id), entityInformation.getJavaType(),
            entityInformation.getCollectionName());
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.CrudRepository#deleteById(java.lang.Object)
     */
    @Override
    public void deleteById(ID id) {

        Assert.notNull(id, "The given id must not be null!");

        mongoOperations.remove(getIdQuery(id), entityInformation.getJavaType(), entityInformation.getCollectionName());
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.CrudRepository#delete(java.lang.Object)
     */
    @Override
    public void delete(T entity) {

        Assert.notNull(entity, "The given entity must not be null!");

        deleteById(entityInformation.getRequiredId(entity));
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.CrudRepository#delete(java.lang.Iterable)
     */
    @Override
    public void deleteAll(Iterable<? extends T> entities) {

        Assert.notNull(entities, "The given Iterable of entities not be null!");

        entities.forEach(this::delete);
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.CrudRepository#deleteAll()
     */
    @Override
    public void deleteAll() {
        mongoOperations.remove(getQuery(), entityInformation.getCollectionName());
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.CrudRepository#findAll()
     */
    @Override
    public List<T> findAll() {
        return findAll(new Query());
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.CrudRepository#findAllById(java.lang.Iterable)
     */
    @Override
    public Iterable<T> findAllById(Iterable<ID> ids) {

        return findAll(getQuery().addCriteria(new Criteria(entityInformation.getIdAttribute())
            .in(Streamable.of(ids).stream().collect(StreamUtils.toUnmodifiableList()))));
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.PagingAndSortingRepository#findAll(org.springframework.data.domain.Pageable)
     */
    @Override
    public Page<T> findAll(Pageable pageable) {

        Assert.notNull(pageable, "Pageable must not be null!");

        Long count = count();
        List<T> list = findAll(new Query().with(pageable));

        return new PageImpl<T>(list, pageable, count);
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.PagingAndSortingRepository#findAll(org.springframework.data.domain.Sort)
     */
    @Override
    public List<T> findAll(Sort sort) {

        Assert.notNull(sort, "Sort must not be null!");

        return findAll(new Query().with(sort));
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.mongodb.repository.MongoRepository#insert(java.lang.Object)
     */
    @Override
    public <S extends T> S insert(S entity) {

        Assert.notNull(entity, "Entity must not be null!");

        mongoOperations.insert(entity, entityInformation.getCollectionName());
        return entity;
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.mongodb.repository.MongoRepository#insert(java.lang.Iterable)
     */
    @Override
    public <S extends T> List<S> insert(Iterable<S> entities) {

        Assert.notNull(entities, "The given Iterable of entities not be null!");

        List<S> list = Streamable.of(entities).stream().collect(StreamUtils.toUnmodifiableList());

        if (list.isEmpty()) {
            return list;
        }

        mongoOperations.insertAll(list);
        return list;
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.mongodb.repository.MongoRepository#findAllByExample(org.springframework.data.domain.Example, org.springframework.data.domain.Pageable)
     */
    @Override
    public <S extends T> Page<S> findAll(final Example<S> example, Pageable pageable) {

        Assert.notNull(example, "Sample must not be null!");
        Assert.notNull(pageable, "Pageable must not be null!");

        Query q = getQuery().addCriteria(new Criteria().alike(example)).with(pageable);
        List<S> list = mongoOperations.find(q, example.getProbeType(), entityInformation.getCollectionName());

        return PageableExecutionUtils.getPage(list, pageable,
            () -> mongoOperations.count(q, example.getProbeType(), entityInformation.getCollectionName()));
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.mongodb.repository.MongoRepository#findAllByExample(org.springframework.data.domain.Example, org.springframework.data.domain.Sort)
     */
    @Override
    public <S extends T> List<S> findAll(Example<S> example, Sort sort) {

        Assert.notNull(example, "Sample must not be null!");
        Assert.notNull(sort, "Sort must not be null!");

        Query q = getQuery().addCriteria(new Criteria().alike(example)).with(sort);

        return mongoOperations.find(q, example.getProbeType(), entityInformation.getCollectionName());
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.mongodb.repository.MongoRepository#findAllByExample(org.springframework.data.domain.Example)
     */
    @Override
    public <S extends T> List<S> findAll(Example<S> example) {
        return findAll(example, Sort.unsorted());
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.query.QueryByExampleExecutor#findOne(org.springframework.data.domain.Example)
     */
    @Override
    public <S extends T> Optional<S> findOne(Example<S> example) {

        Assert.notNull(example, "Sample must not be null!");

        Query q = getQuery().addCriteria(new Criteria().alike(example));
        return Optional
            .ofNullable(mongoOperations.findOne(q, example.getProbeType(), entityInformation.getCollectionName()));
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.query.QueryByExampleExecutor#count(org.springframework.data.domain.Example)
     */
    @Override
    public <S extends T> long count(Example<S> example) {

        Assert.notNull(example, "Sample must not be null!");

        Query q = getQuery().addCriteria(new Criteria().alike(example));
        return mongoOperations.count(q, example.getProbeType(), entityInformation.getCollectionName());
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.query.QueryByExampleExecutor#exists(org.springframework.data.domain.Example)
     */
    @Override
    public <S extends T> boolean exists(Example<S> example) {

        Assert.notNull(example, "Sample must not be null!");

        Query q = getQuery().addCriteria(new Criteria().alike(example));
        return mongoOperations.exists(q, example.getProbeType(), entityInformation.getCollectionName());
    }

}
