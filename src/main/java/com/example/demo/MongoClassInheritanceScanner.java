package com.example.demo;

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.lang.Nullable;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Calculates subclasses for any mongo entity class. We use string class names instead of classes here, due to different classloader
 * problem with spring boot libraries.
 *
 * @author Lukasz Frankowski (http://lifeinide.com)
 */
public class MongoClassInheritanceScanner {

	protected List<String> classes = Collections.synchronizedList(new ArrayList<>());
	protected Map<String, List<Class>> allClasses = new ConcurrentHashMap<>();
	protected Map<Class, List<String>> aliases = new ConcurrentHashMap<>();

	private static MongoClassInheritanceScanner instance;

	private MongoClassInheritanceScanner() {
		ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
		provider.addIncludeFilter(new AnnotationTypeFilter(TypeAlias.class));
		provider.findCandidateComponents("com.example").forEach(it -> {
			classes.add(it.getBeanClassName());
		});
	}

	public static MongoClassInheritanceScanner getInstance() {
		if (instance==null)
			instance = new MongoClassInheritanceScanner();

		return instance;
	}

	/**
	 * All classes of clazz, together with subclasses.
	 */
	@SuppressWarnings("unchecked")
	public List<Class> getAllClasses(String className) {
		return allClasses.computeIfAbsent(className, clazz1 -> classes.stream()
			.map(it -> {
				try {
					return Class.forName(it);
				} catch (ClassNotFoundException e) {
					throw new RuntimeException(e);
				}
			})
			.filter(it -> {
				try {
					return Class.forName(className).isAssignableFrom(it);
				} catch (ClassNotFoundException e) {
					throw new RuntimeException(e);
				}
			})
			.collect(Collectors.toList()));
	}

	/**
	 * All aliases of clazz, together with subclass aliases.
	 */
	@SuppressWarnings("unchecked")
	public List<String> getAliases(Class clazz) {
		return aliases.computeIfAbsent(clazz, clazz1 -> getAllClasses(clazz1.getName()).stream()
			.map(this::findAlias)
			.filter(Objects::nonNull)
			.collect(Collectors.toList()));
	}

	public String findAlias(Class<?> clazz) {
		return (!Modifier.isAbstract(clazz.getModifiers()) && clazz.isAnnotationPresent(TypeAlias.class))
			? clazz.getAnnotation(TypeAlias.class).value()
			: null;
	}

	@Nullable
	public Criteria createInheritanceCritera(List<String> aliases) {
		if (!aliases.isEmpty()) {
			return new Criteria().orOperator(aliases.stream()
				.map(alias -> where("_class").is(alias))
				.toArray(Criteria[]::new));
		}

		return null;
	}

	@Nullable
	public Criteria createInheritanceCritera(Class<?> clazz) {
		return createInheritanceCritera(getAliases(clazz));
	}

}
