# What is this?

This is an extension for @beb4ch idea for Mongo Inheritance. The original README content is below, my one is yet more below.  

This repo is a very simple example of how inheritance support could be added to Spring Data for MongoDB.

**Please note, this code only solves inheritance in my use-case!** This is why inheritance in general is not part of
Spring Data for MongoDB as such. It is difficult to provide a solution that would work for all possible use-cases.

## My use-case

These are the rules I followed in my code:

* All entity classes for which a repository interface is to de declared have a ```@Document``` annotation. 
* All entity classes that share a common superclass **are stored in the same collection**.
* The shared common superclasses are usually abstract classes. They don't have to be, but usually are.
* To support inheritance, all subclasses (concrete classes) use ```@TypeAlias``` annotation to specify a specific 
marker used to identify class type once stored in the database. See below why I use that annotation!

Following these rules, here is the functionality I was looking for:

* Superclass repositories should work on all data within a collection. 
* Subclass repositories should only work on data that are of the subclass type - meaning that a condition involving
the ```_class``` field should be automatically added to all queries before they reach MongoDB.

The code in this repository showcases a simple implementation that achieves this. There is room for improvement, 
but this should be a nice starter for further experimentation.

## Why use @TypeAlias ?

By default Spring Data for MongoDB will put a fully qualified class name into the ```_class``` field of the entity
class (the field is added to the model automatically by Spring). This may seem fine, however if your code goes
through any refactoring where package or class names change, you will be stuck with a lot of documents inside
MongoDB with "legacy" class names. I don't really like that, so I always use the ```@TypeAlias``` annotation 
with some identifier that makes sense to me. This way any refactoring I do will not affect inheritance in any way. And 
I can also use a short identifier, because a full class name can get pretty lengthy sometimes.

## OK, so what's wrong with the original idea?

The idea is great, but for **my use-case** requires some refinements to support more complex model with inheritance.

### `@TextIndexed`

The first thing with the original idea is that `@TextIndexed` doesn't work. This is because Spring Data forcefully creates text indexes for each entity class using class name [here](https://github.com/spring-projects/spring-data-mongodb/blob/2.0.0.RELEASE/spring-data-mongodb/src/main/java/org/springframework/data/mongodb/core/index/MongoPersistentEntityIndexResolver.java#L217). Unfortunately putting name on `@TextIndexed` is [not supported](https://stackoverflow.com/a/39383752) and in a Spring fashion their own classes are finals on privates and you cannot easily change anything. Waiting for `name` support in `@TextIndexed` annotation I currently use an [ugly hack](https://github.com/l0co/spring-data-mongodb-inheritance-test/blob/58877f4f1f7c859798c9f1f2fd7bf1df60d70b85/src/main/java/org/springframework/data/mongodb/core/index/MongoPersistentEntityIndexResolver.java#L204-L211) on `MongoPersistentEntityIndexResolver`.

## Better inheritance handling

In the original idea the base class can't introduce its own entity, is abstract and without `@TypeAlias` annotation:

```
@Document(collection = "things")
public abstract class Thing {}

@Document(collection = "things")
@TypeAlias("car")
public class Car extends Thing {}

@Document(collection = "things")
@TypeAlias("boat")
public class Boat extends Thing {}
```

What I want is to make possible any inheritance hierarchy in my model, including non-abstract base classes with `@TypeAlias` as well: 
 
```
@Document(collection = "things")
@TypeAlias("thing")
public class Thing {}

@Document(collection = "things")
@TypeAlias("car")
public class Car extends Thing {}

@Document(collection = "things")
@TypeAlias("boat")
public class Boat extends Thing {}
```

This is achievable using [entities hierarchy scanning](https://github.com/l0co/spring-data-mongodb-inheritance-test/blob/58877f4f1f7c859798c9f1f2fd7bf1df60d70b85/src/main/java/com/example/demo/MongoClassInheritanceScanner.java#L21) and using all subtypes discriminator criteria with `or` clause. The scanning facility uses `ClassPathScanningCandidateComponentProvider` which is quite fast.

## Inherited repositories

The next thing I need is to have the same inheritance for repositories as for entities, because I want to keep some common queries related to base classes on super repositories and have them reusable on derived repositories. This is now possible due to previous feature. For example [`ThingRepository.findByName()`](https://github.com/l0co/spring-data-mongodb-inheritance-test/blob/58877f4f1f7c859798c9f1f2fd7bf1df60d70b85/src/main/java/com/example/demo/repository/ThingRepository.java#L11) can be used and works on all three repos, while still can be used on `ThingRepository` to [get any type of derived entity](https://github.com/l0co/spring-data-mongodb-inheritance-test/blob/58877f4f1f7c859798c9f1f2fd7bf1df60d70b85/src/test/java/com/example/demo/DemoApplicationTests.java#L66-L68).

## `#{entityName}`

The last thing is that if we already have inherited repositories and discriminators are automatically injected to all auto generated queries, we may want to use explicite `@Query` on some repository methods. If we do it on end repositories we can use explicit alias, for example on `CarRepository` we can create `@Query("{'_class': 'car}")`. However if we want to do it as well on repositories in the middle of hierarchy we can't, because we don't know the exact entity type alias on this level.

In Spring Data JPA there's nice concept to [inject current repository entity name automatically using SPEL](https://docs.spring.io/spring-data/data-jpa/docs/current/reference/html/#jpa.query.spel-expressions) `#{#entityName}` expression, but there's no equivalent for Mongo. I try to simulate this [here](https://github.com/l0co/spring-data-mongodb-inheritance-test/blob/58877f4f1f7c859798c9f1f2fd7bf1df60d70b85/src/main/java/com/example/demo/InheritanceAwareMongoRepositoryFactory.java#L95). With [such construction](https://github.com/l0co/spring-data-mongodb-inheritance-test/blob/58877f4f1f7c859798c9f1f2fd7bf1df60d70b85/src/main/java/com/example/demo/repository/ThingRepository.java#L13) we can now use this expression on [all derived repositories](https://github.com/l0co/spring-data-mongodb-inheritance-test/blob/58877f4f1f7c859798c9f1f2fd7bf1df60d70b85/src/test/java/com/example/demo/DemoApplicationTests.java#L70-L72).

NOTE, that currently Spring tries to go far away than finals on privates and this time to prevent from doing something with their code they **completely unreasonably** made [this field](https://github.com/spring-projects/spring-data-mongodb/blob/2.0.0.RELEASE/spring-data-mongodb/src/main/java/org/springframework/data/mongodb/repository/query/MongoQueryMethod.java#L107) with package visibility, what make it absolutely unaccessible to user code. To overcome this I needed to do another [ugly hack](https://github.com/l0co/spring-data-mongodb-inheritance-test/blob/58877f4f1f7c859798c9f1f2fd7bf1df60d70b85/src/main/java/org/springframework/data/mongodb/repository/query/MongoQueryMethodExtractor.java#L10) waiting for some code cleanup in this module.                                                    